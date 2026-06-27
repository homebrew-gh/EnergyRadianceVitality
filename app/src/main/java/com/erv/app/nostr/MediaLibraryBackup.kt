package com.erv.app.nostr

import android.content.Context
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val MEDIA_LIBRARY_D_TAG = "erv/media/library"

private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

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

    suspend fun fetchManifest(
        relayPool: RelayPool,
        signer: EventSigner,
        timeoutMs: Long = 5000,
    ): MediaLibraryManifest {
        val event = fetchLatestKind30078ByDTag(
            relayPool = relayPool,
            pubkeyHex = signer.publicKey,
            timeoutMs = timeoutMs,
            signer = signer,
        )[MEDIA_LIBRARY_D_TAG] ?: return MediaLibraryManifest()
        val raw = event.decryptKind30078PayloadOrNull(signer) ?: return MediaLibraryManifest()
        return try {
            json.decodeFromString(MediaLibraryManifest.serializer(), raw)
        } catch (_: SerializationException) {
            MediaLibraryManifest()
        } catch (_: IllegalArgumentException) {
            MediaLibraryManifest()
        }
    }

    suspend fun publishManifest(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        manifest: MediaLibraryManifest,
    ): RelayPublishOutbox.KickDrainResult {
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
