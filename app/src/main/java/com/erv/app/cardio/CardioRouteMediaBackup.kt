package com.erv.app.cardio

import android.content.Context
import com.erv.app.bodytracker.nowEpochSeconds
import com.erv.app.nostr.BlossomEndpoints
import com.erv.app.nostr.BlossomUploader
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.Hex
import com.erv.app.nostr.MediaLibraryBackup
import com.erv.app.nostr.MediaLibraryItem
import com.erv.app.nostr.MediaLibraryManifest
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.sha256

data class CardioRouteMediaBackupResult(
    val origin: String?,
    val uploaded: Boolean,
    val reused: Boolean,
    val failed: Boolean,
    val manifestQueued: Boolean,
)

object CardioRouteMediaBackup {
    private const val SOURCE_CARDIO_ROUTE = "cardio_route"

    suspend fun backupRouteImage(
        appContext: Context,
        session: CardioSession,
        dateIso: String,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        explicitPrivateBlossomOrigin: String,
        trustSelfSignedLanTls: Boolean,
        colorTop: Int,
        colorMid: Int,
        colorBottom: Int,
    ): CardioRouteMediaBackupResult {
        val points = session.gpsTrack?.points?.takeIf { it.isNotEmpty() }
            ?: return CardioRouteMediaBackupResult(
                origin = null,
                uploaded = false,
                reused = false,
                failed = true,
                manifestQueued = false,
            )
        val origin = BlossomEndpoints.resolvePrivateBackupOrigin(
            explicitPrivateOrigin = explicitPrivateBlossomOrigin,
            dataRelayUrls = dataRelayUrls,
        ) ?: return CardioRouteMediaBackupResult(
            origin = null,
            uploaded = false,
            reused = false,
            failed = true,
            manifestQueued = false,
        )
        val bytes = CardioTrackShareImage.renderRoutePngBytes(
            appContext,
            points,
            colorTop,
            colorMid,
            colorBottom,
        ) ?: return CardioRouteMediaBackupResult(
            origin = origin,
            uploaded = false,
            reused = false,
            failed = true,
            manifestQueued = false,
        )

        val localId = session.id
        val plaintextSha = Hex.encode(sha256(bytes))
        val existingManifest = MediaLibraryBackup.fetchManifest(relayPool, signer)
        val reusable = existingManifest.items
            .filter { it.source == SOURCE_CARDIO_ROUTE }
            .associateBy { it.localId }
        val nextItems = existingManifest.items
            .filterNot { it.source == SOURCE_CARDIO_ROUTE && it.localId == localId }
            .toMutableList()

        val prior = reusable[localId]
        val item = if (prior != null && prior.sha256 == plaintextSha && prior.blobUrl.isNotBlank()) {
            prior.copy(date = dateIso)
        } else {
            val encrypted = MediaLibraryBackup.encryptBytes(bytes)
            val uploadUrl = BlossomUploader.uploadBlob(
                normalizedOrigin = origin,
                bytes = encrypted.ciphertext,
                contentType = "application/octet-stream",
                signer = signer,
                trustSelfSignedLanTls = trustSelfSignedLanTls,
            ).getOrNull() ?: return CardioRouteMediaBackupResult(
                origin = origin,
                uploaded = false,
                reused = false,
                failed = true,
                manifestQueued = false,
            )
            MediaLibraryItem(
                id = "cardio_route:$localId",
                source = SOURCE_CARDIO_ROUTE,
                localId = localId,
                date = dateIso,
                blobUrl = uploadUrl,
                blossomOrigin = origin,
                sha256 = plaintextSha,
                encryptedSha256 = Hex.encode(sha256(encrypted.ciphertext)),
                sizeBytes = bytes.size.toLong(),
                contentType = "image/png",
                encryption = encrypted.encryption,
                uploadedAtEpochSeconds = nowEpochSeconds(),
            )
        }
        nextItems += item
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
        return CardioRouteMediaBackupResult(
            origin = origin,
            uploaded = prior == null || prior.sha256 != plaintextSha,
            reused = prior != null && prior.sha256 == plaintextSha,
            failed = false,
            manifestQueued = publishResult.publishedFail == 0,
        )
    }
}
