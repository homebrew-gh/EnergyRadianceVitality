package com.erv.app.nostr

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val MEDIA_LIBRARY_D_TAG = "erv/media/library"

private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

private const val MEDIA_LIBRARY_LOG_TAG = "ErvMediaBackup"
private const val MEDIA_LIBRARY_CACHE_FILE = "media_library_manifest.json"

/**
 * Union of two manifests' items, one per (source, localId), keeping the most recently uploaded.
 * The relay copy and this phone's last published copy can each hold items the other lacks.
 */
internal fun mergeMediaLibraryItems(
    primary: List<MediaLibraryItem>,
    secondary: List<MediaLibraryItem>,
): List<MediaLibraryItem> {
    val byKey = LinkedHashMap<Pair<String, String>, MediaLibraryItem>()
    for (item in primary + secondary) {
        val key = item.source to item.localId
        val existing = byKey[key]
        if (existing == null || item.uploadedAtEpochSeconds > existing.uploadedAtEpochSeconds) {
            byKey[key] = item
        }
    }
    return byKey.values.toList()
}

/**
 * The base an update builds on, or null when it is unknown: the relay copy could not be read and
 * this phone has no copy of its own. Publishing over an unknown base would drop every saved item.
 */
internal fun mediaLibraryUpdateBase(
    remote: MediaLibraryManifest?,
    cached: MediaLibraryManifest?,
): MediaLibraryManifest? = when {
    remote == null && cached == null -> null
    remote == null -> cached
    cached == null -> remote
    else -> remote.copy(items = mergeMediaLibraryItems(remote.items, cached.items))
}

@Serializable
data class MediaLibraryManifest(
    val version: Int = 1,
    val updatedAtEpochSeconds: Long = nowEpochSeconds(),
    val items: List<MediaLibraryItem> = emptyList(),
)

@Serializable
data class MediaLibraryItem(
    val id: String,
    val source: String,
    val localId: String,
    val date: String? = null,
    val blobUrl: String,
    val blossomOrigin: String,
    val sha256: String,
    val encryptedSha256: String,
    val sizeBytes: Long,
    val contentType: String,
    val encryptedContentType: String = "application/octet-stream",
    val encryption: MediaBlobEncryption,
    val uploadedAtEpochSeconds: Long = nowEpochSeconds(),
)

@Serializable
data class MediaBlobEncryption(
    val algorithm: String = "AES-256-GCM",
    val keyBase64: String,
    val nonceBase64: String,
)

data class EncryptedMediaBlob(
    val ciphertext: ByteArray,
    val encryption: MediaBlobEncryption,
)

object MediaLibraryBackup {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @PublishedApi
    internal val manifestLock = Mutex()

    /**
     * Runs one read-change-publish of the manifest at a time. Several backups start together when
     * a workout finishes; without this each reads the same old copy and the last publish drops
     * the others' graphs.
     */
    suspend inline fun <T> withManifestLock(block: () -> T): T = manifestLock.withLock(action = block)

    /**
     * Current manifest to add items to, merged with this phone's last published copy.
     * Returns null when the relay copy could not be read (timeout, decrypt or parse failure) and
     * there is no local copy: the caller must not publish then.
     */
    suspend fun fetchManifestForUpdate(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        timeoutMs: Long = 8000,
    ): MediaLibraryManifest? {
        val cached = readCachedManifest(appContext)
        val fetch = runCatching {
            fetchLatestKind30078ForDTags(
                relayPool = relayPool,
                pubkeyHex = signer.publicKey,
                dTags = listOf(MEDIA_LIBRARY_D_TAG),
                signer = signer,
                timeoutMs = timeoutMs,
            )
        }.getOrNull()
        val event = fetch?.latestByTag?.get(MEDIA_LIBRARY_D_TAG)
        val remote: MediaLibraryManifest? = when {
            event != null -> event.decryptKind30078PayloadOrNull(signer)?.let(::decodeManifestOrNull)
            fetch?.complete == true -> MediaLibraryManifest(items = emptyList())
            else -> null
        }
        if (remote == null) {
            Log.w(
                MEDIA_LIBRARY_LOG_TAG,
                "manifest read failed (event=${event != null} complete=${fetch?.complete}); " +
                    "cached=${cached?.items?.size}",
            )
        }
        return mediaLibraryUpdateBase(remote, cached)
    }

    fun clearCachedManifest(appContext: Context) {
        runCatching { cacheFile(appContext).delete() }
    }

    private fun decodeManifestOrNull(raw: String): MediaLibraryManifest? = try {
        json.decodeFromString(MediaLibraryManifest.serializer(), raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun cacheFile(appContext: Context) = File(appContext.filesDir, MEDIA_LIBRARY_CACHE_FILE)

    private suspend fun readCachedManifest(appContext: Context): MediaLibraryManifest? =
        withContext(Dispatchers.IO) {
            val file = cacheFile(appContext)
            if (!file.isFile) return@withContext null
            runCatching { file.readText() }.getOrNull()?.let(::decodeManifestOrNull)
        }

    private suspend fun writeCachedManifest(appContext: Context, manifest: MediaLibraryManifest) {
        withContext(Dispatchers.IO) {
            runCatching {
                val file = cacheFile(appContext)
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(MediaLibraryManifest.serializer(), manifest))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { Log.w(MEDIA_LIBRARY_LOG_TAG, "could not save local manifest copy", it) }
        }
    }

    suspend fun publishManifest(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        manifest: MediaLibraryManifest,
    ): RelayPublishOutbox.KickDrainResult {
        writeCachedManifest(appContext, manifest)
        return RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
            appContext = appContext,
            relayPool = relayPool,
            signer = signer,
            dataRelayUrls = dataRelayUrls,
            dTag = MEDIA_LIBRARY_D_TAG,
            plaintextPayload = json.encodeToString(MediaLibraryManifest.serializer(), manifest),
        )
    }

    fun encryptBytes(bytes: ByteArray): EncryptedMediaBlob {
        val key = ByteArray(32)
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(key)
        SecureRandom().nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(bytes)
        return EncryptedMediaBlob(
            ciphertext = ciphertext,
            encryption = MediaBlobEncryption(
                keyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
                nonceBase64 = Base64.encodeToString(nonce, Base64.NO_WRAP),
            ),
        )
    }
}
