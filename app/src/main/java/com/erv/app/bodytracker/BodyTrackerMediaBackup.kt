package com.erv.app.bodytracker

import android.content.Context
import com.erv.app.nostr.BlossomEndpoints
import com.erv.app.nostr.BlossomUploader
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.Hex
import com.erv.app.nostr.MediaLibraryBackup
import com.erv.app.nostr.MediaLibraryItem
import com.erv.app.nostr.MediaLibraryManifest
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BodyTrackerMediaBackupResult(
    val origin: String?,
    val totalPhotos: Int,
    val uploaded: Int,
    val reused: Int,
    val failed: Int,
    val manifestQueued: Boolean,
)

object BodyTrackerMediaBackup {
    private const val SOURCE_BODY_TRACKER = "body_tracker"

    suspend fun backupProgressPhotos(
        appContext: Context,
        repository: BodyTrackerRepository,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        explicitPrivateBlossomOrigin: String,
        trustSelfSignedLanTls: Boolean,
    ): BodyTrackerMediaBackupResult {
        val origin = BlossomEndpoints.resolvePrivateBackupOrigin(
            explicitPrivateOrigin = explicitPrivateBlossomOrigin,
            dataRelayUrls = dataRelayUrls,
        ) ?: return BodyTrackerMediaBackupResult(
            origin = null,
            totalPhotos = 0,
            uploaded = 0,
            reused = 0,
            failed = 0,
            manifestQueued = false,
        )

        val state = repository.currentState()
        val photoRefs = state.logs.flatMap { log ->
            log.photos.map { photo -> log.date to photo }
        }
        val existingManifest = MediaLibraryBackup.fetchManifest(relayPool, signer)
        val reusable = existingManifest.items
            .filter { it.source == SOURCE_BODY_TRACKER }
            .associateBy { it.localId }
        val nextItems = existingManifest.items
            .filterNot { it.source == SOURCE_BODY_TRACKER }
            .toMutableList()

        var uploaded = 0
        var reused = 0
        var failed = 0

        for ((date, photo) in photoRefs) {
            val file = repository.photoFile(photo.id)
            val bytes = withContext(Dispatchers.IO) {
                if (file.exists() && file.isFile) file.readBytes() else null
            }
            if (bytes == null) {
                failed += 1
                continue
            }
            val plaintextSha = Hex.encode(sha256(bytes))
            val prior = reusable[photo.id]
            if (prior != null && prior.sha256 == plaintextSha && prior.blobUrl.isNotBlank()) {
                nextItems += prior.copy(date = date)
                reused += 1
                continue
            }

            val encrypted = MediaLibraryBackup.encryptBytes(bytes)
            val uploadUrl = BlossomUploader.uploadBlob(
                normalizedOrigin = origin,
                bytes = encrypted.ciphertext,
                contentType = "application/octet-stream",
                signer = signer,
                trustSelfSignedLanTls = trustSelfSignedLanTls,
            ).getOrNull()
            if (uploadUrl == null) {
                failed += 1
                continue
            }

            nextItems += MediaLibraryItem(
                id = "body_tracker:${photo.id}",
                source = SOURCE_BODY_TRACKER,
                localId = photo.id,
                date = date,
                blobUrl = uploadUrl,
                blossomOrigin = origin,
                sha256 = plaintextSha,
                encryptedSha256 = Hex.encode(sha256(encrypted.ciphertext)),
                sizeBytes = bytes.size.toLong(),
                contentType = "image/jpeg",
                encryption = encrypted.encryption,
                uploadedAtEpochSeconds = nowEpochSeconds(),
            )
            uploaded += 1
        }

        val manifest = MediaLibraryManifest(
            updatedAtEpochSeconds = nowEpochSeconds(),
            items = nextItems.sortedWith(compareBy<MediaLibraryItem> { it.source }.thenBy { it.date }.thenBy { it.localId }),
        )
        val publishResult = MediaLibraryBackup.publishManifest(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            manifest,
        )

        return BodyTrackerMediaBackupResult(
            origin = origin,
            totalPhotos = photoRefs.size,
            uploaded = uploaded,
            reused = reused,
            failed = failed,
            manifestQueued = publishResult.publishedFail == 0,
        )
    }

}
