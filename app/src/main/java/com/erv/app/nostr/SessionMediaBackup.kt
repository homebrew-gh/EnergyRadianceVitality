package com.erv.app.nostr

import android.content.Context
import com.erv.app.bodytracker.nowEpochSeconds
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioTrackShareImage
import com.erv.app.hr.HeartRateShareImage
import com.erv.app.hr.HeartRateZoneInputs
import com.erv.app.weighttraining.WeightWorkoutSession

const val MEDIA_SOURCE_CARDIO_ROUTE = "cardio_route"
const val MEDIA_SOURCE_HEART_RATE_GRAPH = "heart_rate_graph"

data class SessionMediaBackupResult(
    val origin: String?,
    val uploaded: Int,
    val reused: Int,
    val failed: Int,
    val manifestQueued: Boolean,
)

object SessionMediaBackup {

    suspend fun backupCardioSession(
        appContext: Context,
        session: CardioSession,
        dateIso: String,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        explicitPrivateBlossomOrigin: String,
        trustSelfSignedLanTls: Boolean,
        zoneInputs: HeartRateZoneInputs,
        routeColorTop: Int,
        routeColorMid: Int,
        routeColorBottom: Int,
    ): SessionMediaBackupResult {
        val origin = BlossomEndpoints.resolvePrivateBackupOrigin(
            explicitPrivateOrigin = explicitPrivateBlossomOrigin,
            dataRelayUrls = dataRelayUrls,
        ) ?: return SessionMediaBackupResult(
            origin = null,
            uploaded = 0,
            reused = 0,
            failed = 0,
            manifestQueued = false,
        )

        val existingManifest = MediaLibraryBackup.fetchManifest(relayPool, signer)
        val nextItems = existingManifest.items.toMutableList()
        var uploaded = 0
        var reused = 0
        var failed = 0

        val routePoints = session.gpsTrack?.points?.takeIf { it.isNotEmpty() }
        if (routePoints != null) {
            when (
                val outcome = upsertEncryptedPng(
                    origin = origin,
                    trustSelfSignedLanTls = trustSelfSignedLanTls,
                    signer = signer,
                    existingItems = nextItems,
                    source = MEDIA_SOURCE_CARDIO_ROUTE,
                    localId = session.id,
                    dateIso = dateIso,
                    idPrefix = "cardio_route",
                    contentType = "image/png",
                    renderBytes = {
                        CardioTrackShareImage.renderRoutePngBytes(
                            appContext,
                            routePoints,
                            routeColorTop,
                            routeColorMid,
                            routeColorBottom,
                        )
                    },
                )
            ) {
                is ItemOutcome.Uploaded -> {
                    nextItems += outcome.item
                    uploaded += 1
                }
                is ItemOutcome.Reused -> {
                    nextItems += outcome.item
                    reused += 1
                }
                ItemOutcome.Failed -> failed += 1
                ItemOutcome.Skipped -> Unit
            }
        }

        val hrSamples = session.heartRate?.samples.orEmpty()
        if (hrSamples.size >= 2) {
            when (
                val outcome = upsertEncryptedPng(
                    origin = origin,
                    trustSelfSignedLanTls = trustSelfSignedLanTls,
                    signer = signer,
                    existingItems = nextItems,
                    source = MEDIA_SOURCE_HEART_RATE_GRAPH,
                    localId = session.id,
                    dateIso = dateIso,
                    idPrefix = "heart_rate_graph",
                    contentType = "image/png",
                    renderBytes = {
                        HeartRateShareImage.renderPngBytes(
                            samples = hrSamples,
                            zoneInputs = zoneInputs,
                            title = "Heart rate",
                            avgBpm = session.heartRate?.avgBpm,
                            maxBpm = session.heartRate?.maxBpm,
                            minBpm = session.heartRate?.minBpm,
                        )
                    },
                )
            ) {
                is ItemOutcome.Uploaded -> {
                    nextItems += outcome.item
                    uploaded += 1
                }
                is ItemOutcome.Reused -> {
                    nextItems += outcome.item
                    reused += 1
                }
                ItemOutcome.Failed -> failed += 1
                ItemOutcome.Skipped -> Unit
            }
        }

        if (uploaded == 0 && reused == 0 && failed == 0) {
            return SessionMediaBackupResult(
                origin = origin,
                uploaded = 0,
                reused = 0,
                failed = 0,
                manifestQueued = false,
            )
        }

        val manifest = MediaLibraryManifest(
            updatedAtEpochSeconds = nowEpochSeconds(),
            items = nextItems.sortedWith(
                compareBy<MediaLibraryItem> { it.source }.thenBy { it.date }.thenBy { it.localId },
            ),
        )
        val publishResult = MediaLibraryBackup.publishManifest(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            manifest,
        )
        return SessionMediaBackupResult(
            origin = origin,
            uploaded = uploaded,
            reused = reused,
            failed = failed,
            manifestQueued = publishResult.publishedFail == 0,
        )
    }

    suspend fun backupWeightSession(
        appContext: Context,
        session: WeightWorkoutSession,
        dateIso: String,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        explicitPrivateBlossomOrigin: String,
        trustSelfSignedLanTls: Boolean,
        zoneInputs: HeartRateZoneInputs,
    ): SessionMediaBackupResult {
        val origin = BlossomEndpoints.resolvePrivateBackupOrigin(
            explicitPrivateOrigin = explicitPrivateBlossomOrigin,
            dataRelayUrls = dataRelayUrls,
        ) ?: return SessionMediaBackupResult(
            origin = null,
            uploaded = 0,
            reused = 0,
            failed = 0,
            manifestQueued = false,
        )

        val hrSamples = session.heartRate?.samples.orEmpty()
        if (hrSamples.size < 2) {
            return SessionMediaBackupResult(
                origin = origin,
                uploaded = 0,
                reused = 0,
                failed = 0,
                manifestQueued = false,
            )
        }

        val existingManifest = MediaLibraryBackup.fetchManifest(relayPool, signer)
        val nextItems = existingManifest.items.toMutableList()
        var uploaded = 0
        var reused = 0
        var failed = 0

        when (
            val outcome = upsertEncryptedPng(
                origin = origin,
                trustSelfSignedLanTls = trustSelfSignedLanTls,
                signer = signer,
                existingItems = nextItems,
                source = MEDIA_SOURCE_HEART_RATE_GRAPH,
                localId = session.id,
                dateIso = dateIso,
                idPrefix = "heart_rate_graph",
                contentType = "image/png",
                renderBytes = {
                    HeartRateShareImage.renderPngBytes(
                        samples = hrSamples,
                        zoneInputs = zoneInputs,
                        title = "Heart rate",
                        avgBpm = session.heartRate?.avgBpm,
                        maxBpm = session.heartRate?.maxBpm,
                        minBpm = session.heartRate?.minBpm,
                    )
                },
            )
        ) {
            is ItemOutcome.Uploaded -> {
                nextItems += outcome.item
                uploaded += 1
            }
            is ItemOutcome.Reused -> {
                nextItems += outcome.item
                reused += 1
            }
            ItemOutcome.Failed -> failed += 1
            ItemOutcome.Skipped -> Unit
        }

        if (uploaded == 0 && reused == 0) {
            return SessionMediaBackupResult(
                origin = origin,
                uploaded = uploaded,
                reused = reused,
                failed = failed,
                manifestQueued = false,
            )
        }

        val manifest = MediaLibraryManifest(
            updatedAtEpochSeconds = nowEpochSeconds(),
            items = nextItems.sortedWith(
                compareBy<MediaLibraryItem> { it.source }.thenBy { it.date }.thenBy { it.localId },
            ),
        )
        val publishResult = MediaLibraryBackup.publishManifest(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            manifest,
        )
        return SessionMediaBackupResult(
            origin = origin,
            uploaded = uploaded,
            reused = reused,
            failed = failed,
            manifestQueued = publishResult.publishedFail == 0,
        )
    }

    private sealed interface ItemOutcome {
        data class Uploaded(val item: MediaLibraryItem) : ItemOutcome
        data class Reused(val item: MediaLibraryItem) : ItemOutcome
        data object Failed : ItemOutcome
        data object Skipped : ItemOutcome
    }

    private suspend fun upsertEncryptedPng(
        origin: String,
        trustSelfSignedLanTls: Boolean,
        signer: EventSigner,
        existingItems: MutableList<MediaLibraryItem>,
        source: String,
        localId: String,
        dateIso: String,
        idPrefix: String,
        contentType: String,
        renderBytes: suspend () -> ByteArray?,
    ): ItemOutcome {
        val bytes = renderBytes() ?: return ItemOutcome.Skipped
        val plaintextSha = Hex.encode(sha256(bytes))
        val prior = existingItems.firstOrNull { it.source == source && it.localId == localId }
        existingItems.removeAll { it.source == source && it.localId == localId }

        if (prior != null && prior.sha256 == plaintextSha && prior.blobUrl.isNotBlank()) {
            return ItemOutcome.Reused(prior.copy(date = dateIso))
        }

        val encrypted = MediaLibraryBackup.encryptBytes(bytes)
        val uploadUrl = BlossomUploader.uploadBlob(
            normalizedOrigin = origin,
            bytes = encrypted.ciphertext,
            contentType = "application/octet-stream",
            signer = signer,
            trustSelfSignedLanTls = trustSelfSignedLanTls,
        ).getOrNull() ?: return ItemOutcome.Failed

        return ItemOutcome.Uploaded(
            MediaLibraryItem(
                id = "$idPrefix:$localId",
                source = source,
                localId = localId,
                date = dateIso,
                blobUrl = uploadUrl,
                blossomOrigin = origin,
                sha256 = plaintextSha,
                encryptedSha256 = Hex.encode(sha256(encrypted.ciphertext)),
                sizeBytes = bytes.size.toLong(),
                contentType = contentType,
                encryption = encrypted.encryption,
                uploadedAtEpochSeconds = nowEpochSeconds(),
            ),
        )
    }
}
