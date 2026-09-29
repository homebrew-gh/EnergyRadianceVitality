package com.erv.app.nostr

import android.content.Context
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.bodytracker.BodyTrackerSync
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.data.UserPreferences
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.heatcold.HeatColdSync
import com.erv.app.lighttherapy.LightSync
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.ProgramSync
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.StretchingSync
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementSync
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.WorkoutSync
import kotlinx.coroutines.flow.first

data class CurrentRelayDataCoverage(
    val foundPayloadCount: Int,
    val totalPayloadCount: Int,
    val connectedRelayCount: Int,
    val configuredRelayCount: Int,
    /** Local `#d` tags with readable content that are not yet readable back from relay. */
    val missingTags: List<String> = emptyList(),
    /** Local day-log tags skipped because sessions/workouts are empty (not uploaded). */
    val skippedEmptyLocalTags: Int = 0,
)

/** What a resync found and queued. */
data class ResyncPlan(
    /** Local payloads with relay-worthy content that were compared. */
    val checkedCount: Int,
    /** Already on the relay with identical content; nothing sent. */
    val alreadyOnRelayCount: Int,
    /** Missing from or different on the relay; queued for upload. */
    val queuedCount: Int,
    /** Absent from the relay answer but the relay did not finish answering; not queued. */
    val unverifiedCount: Int,
    val connectedRelayCount: Int,
    val configuredRelayCount: Int,
    val skippedEmptyLocalTags: Int = 0,
)

internal data class ResyncDiff(
    val matched: List<Pair<String, String>>,
    val toUpload: List<Pair<String, String>>,
    val unverifiedTags: List<String>,
)

/**
 * Compares local payloads with what the relay returned. [relayPlainByTag] holds decrypted relay
 * plaintext for every tag the relay returned (null when it would not decrypt). A tag the relay did
 * not return is only uploaded when [relayAbsenceVerified]: a timed-out fetch must not turn into a
 * full re-upload against a rate-limited relay.
 */
internal fun diffLocalAgainstRelay(
    local: List<Pair<String, String>>,
    relayPlainByTag: Map<String, String?>,
    relayAbsenceVerified: Boolean,
): ResyncDiff {
    val matched = mutableListOf<Pair<String, String>>()
    val toUpload = mutableListOf<Pair<String, String>>()
    val unverified = mutableListOf<String>()
    for (entry in local) {
        val (dTag, plain) = entry
        when {
            relayPlainByTag.containsKey(dTag) ->
                if (relayPlainByTag[dTag] == plain) matched += entry else toUpload += entry
            relayAbsenceVerified -> toUpload += entry
            else -> unverified += dTag
        }
    }
    return ResyncDiff(matched, toUpload, unverified)
}

object CurrentRelayDataSync {

    /**
     * Resync that only uploads what the data relays lack. Fetches the relay copy of every local
     * payload, records a digest for identical ones (and drops their stale queued rows), and queues
     * the rest. Does not drain; the outbox scheduler sends at whatever rate the relay allows.
     */
    suspend fun reconcileAndQueue(
        appContext: Context,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        localEntries: List<Pair<String, String>>,
        trustSelfSignedLanTls: Boolean = false,
        timeoutMs: Long = 12_000,
    ): ResyncPlan {
        val syncable = localEntries.filter { (dTag, plain) ->
            localKind30078PayloadHasRelayContent(dTag, plain)
        }
        val skipped = localEntries.size - syncable.size
        fun plan(
            alreadyOnRelay: Int = 0,
            queued: Int = 0,
            unverified: Int = 0,
            connected: Int = 0,
        ) = ResyncPlan(
            checkedCount = syncable.size,
            alreadyOnRelayCount = alreadyOnRelay,
            queuedCount = queued,
            unverifiedCount = unverified,
            connectedRelayCount = connected,
            configuredRelayCount = dataRelayUrls.size,
            skippedEmptyLocalTags = skipped,
        )
        if (dataRelayUrls.isEmpty() || syncable.isEmpty()) return plan(unverified = syncable.size)

        val tempPool = RelayPool(signer, RelayOkHttpClient.create(trustSelfSignedLanTls), trustSelfSignedLanTls)
        try {
            tempPool.setRelays(dataRelayUrls)
            tempPool.awaitAtLeastOneConnected(timeoutMs = timeoutMs)
            val connectedCount = tempPool.connectedRelayUrls().count { it in dataRelayUrls }
            if (connectedCount == 0) return plan(unverified = syncable.size)

            val snapshot = fetchLatestKind30078Snapshot(
                relayPool = tempPool,
                pubkeyHex = signer.publicKey,
                timeoutMs = timeoutMs,
                signer = signer,
            )
            val notInSnapshot = syncable.map { it.first }.filter { it !in snapshot.latestByTag }
            val targeted = fetchLatestKind30078ForDTags(
                relayPool = tempPool,
                pubkeyHex = signer.publicKey,
                dTags = notInSnapshot,
                signer = signer,
                timeoutMs = timeoutMs,
            )
            val relayLatest = snapshot.latestByTag + targeted.latestByTag
            val relayPlainByTag = buildMap {
                for ((dTag, _) in syncable) {
                    val event = relayLatest[dTag] ?: continue
                    put(dTag, event.decryptKind30078PayloadOrNull(signer))
                }
            }
            val diff = diffLocalAgainstRelay(syncable, relayPlainByTag, relayAbsenceVerified = targeted.complete)

            val digests = RelayPayloadDigestStore.get(appContext)
            for ((dTag, plain) in diff.matched) {
                digests.putDigest(dTag, sha256HexUtf8(plain))
            }
            val outbox = RelayPublishOutbox.get(appContext)
            outbox.removeDTags(diff.matched.map { it.first })
            if (diff.toUpload.isNotEmpty()) {
                val uploadTags = diff.toUpload.map { it.first }
                digests.clearDigests(uploadTags)
                val staleBaseDayTags = uploadTags
                    .filter { isTrainingDayLogDTag(it) }
                    .map { baseTrainingDayDTag(it) }
                    .filter { it !in uploadTags }
                outbox.replaceMany(staleBaseDayTags, diff.toUpload)
            }
            return plan(
                alreadyOnRelay = diff.matched.size,
                queued = diff.toUpload.size,
                unverified = diff.unverifiedTags.size,
                connected = connectedCount,
            )
        } finally {
            tempPool.destroy()
        }
    }

    suspend fun buildCurrentEntries(
        userPreferences: UserPreferences,
        weightRepository: WeightRepository,
        cardioRepository: CardioRepository,
        stretchingRepository: StretchingRepository,
        heatColdRepository: HeatColdRepository,
        lightTherapyRepository: LightTherapyRepository,
        supplementRepository: SupplementRepository,
        programRepository: ProgramRepository,
        bodyTrackerRepository: BodyTrackerRepository,
        workoutRepository: WorkoutRepository,
    ): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val heartRateZoneInputs = userPreferences.heartRateZoneInputs.first()
        pairs += WeightSync.fullOutboxEntries(weightRepository.currentState(), heartRateZoneInputs)
        pairs += CardioSync.fullOutboxEntries(cardioRepository.currentState(), heartRateZoneInputs)
        pairs += StretchingSync.fullOutboxEntries(stretchingRepository.currentState())
        pairs += HeatColdSync.fullOutboxEntries(heatColdRepository.currentState())
        pairs += LightSync.fullOutboxEntries(lightTherapyRepository.currentState())
        pairs += SupplementSync.fullOutboxEntries(supplementRepository.currentState())
        pairs += ProgramSync.fullOutboxEntries(programRepository.currentState())
        pairs += WorkoutSync.fullOutboxEntries(workoutRepository.currentState())
        pairs += BodyTrackerSync.fullOutboxEntries(bodyTrackerRepository.currentState())

        val gymMembership = userPreferences.gymMembership.first()
        val equipment = userPreferences.ownedEquipment.first()
        val enabledPackIds = userPreferences.enabledWeightExercisePackIds.first()
        if (gymMembership || equipment.isNotEmpty() || enabledPackIds.isNotEmpty()) {
            pairs += FitnessEquipmentSync.plaintextFor(
                gymMembership,
                equipment,
                enabledPackIds.toList(),
            )
        }

        val trainingProfile = userPreferences.trainingProfile.first()
        if (TrainingProfileSync.shouldPublish(trainingProfile)) {
            pairs += TrainingProfileSync.plaintextFor(trainingProfile)
        }

        return pairs.distinctBy { it.first }
    }

    suspend fun probeCoverage(
        signer: EventSigner,
        dataRelayUrls: List<String>,
        localEntries: List<Pair<String, String>>,
        trustSelfSignedLanTls: Boolean = false,
        timeoutMs: Long = 5000,
    ): CurrentRelayDataCoverage {
        val syncable = localEntries.filter { (dTag, plain) ->
            localKind30078PayloadHasRelayContent(dTag, plain)
        }
        val skippedEmptyLocalTags = localEntries.size - syncable.size
        if (dataRelayUrls.isEmpty()) {
            return CurrentRelayDataCoverage(
                foundPayloadCount = 0,
                totalPayloadCount = syncable.size,
                connectedRelayCount = 0,
                configuredRelayCount = 0,
                skippedEmptyLocalTags = skippedEmptyLocalTags,
            )
        }

        val tempPool = RelayPool(signer, RelayOkHttpClient.create(trustSelfSignedLanTls), trustSelfSignedLanTls)
        try {
            tempPool.setRelays(dataRelayUrls)
            tempPool.awaitAtLeastOneConnected(timeoutMs = timeoutMs)
            val latestByTag = fetchLatestKind30078ByDTag(
                relayPool = tempPool,
                pubkeyHex = signer.publicKey,
                timeoutMs = timeoutMs,
                signer = signer,
            )
            val localTags = syncable.map { it.first }.toSet()
            val missingTags = localTags.filter { it !in latestByTag }.sorted()
            val connectedCount = dataRelayUrls.count { url ->
                tempPool.relayStates.value[url].let { state ->
                    state is ConnectionState.Connected || state is ConnectionState.Authenticated
                }
            }
            return CurrentRelayDataCoverage(
                foundPayloadCount = localTags.size - missingTags.size,
                totalPayloadCount = localTags.size,
                connectedRelayCount = connectedCount,
                configuredRelayCount = dataRelayUrls.size,
                missingTags = missingTags,
                skippedEmptyLocalTags = skippedEmptyLocalTags,
            )
        } finally {
            tempPool.destroy()
        }
    }

    suspend fun forceResync(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        localEntries: List<Pair<String, String>>,
    ): RelayPublishOutbox.KickDrainResult {
        val outbox = RelayPublishOutbox.get(appContext)
        val trainingDayTags = localEntries.map { it.first }.filter { isTrainingDayLogDTag(it) }
        val replacementTags = localEntries.map { it.first } + trainingDayTags.map { baseTrainingDayDTag(it) }
        RelayPayloadDigestStore.get(appContext).clearDigests(replacementTags)
        outbox.replaceMany(replacementTags, localEntries)
        var publishedOk = 0
        var publishedFail = 0
        var remaining = localEntries.size
        var stoppedBecauseQueueEmpty = false
        var passes = 0
        while (remaining > 0 && passes < 50) {
            val drain = outbox.kickDrain(relayPool, signer, dataRelayUrls)
            publishedOk += drain.publishedOk
            publishedFail += drain.publishedFail
            remaining = drain.remaining
            stoppedBecauseQueueEmpty = drain.stoppedBecauseQueueEmpty
            passes++
            if (drain.stoppedBecauseQueueEmpty) break
            if (drain.publishedOk == 0 && drain.publishedFail == 0 && drain.remaining > 0) break
        }
        return RelayPublishOutbox.KickDrainResult(
            remaining = remaining,
            publishedOk = publishedOk,
            publishedFail = publishedFail,
            stoppedBecauseQueueEmpty = stoppedBecauseQueueEmpty,
        )
    }

    private fun baseTrainingDayDTag(dTag: String): String =
        dTag.substringBefore("/session/")

    /** Clears digests and republishes only training day-log payloads (e.g. cardio or weight log resync). */
    suspend fun forceResyncDayLogs(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        dayLogEntries: List<Pair<String, String>>,
    ): RelayPublishOutbox.KickDrainResult {
        if (dayLogEntries.isEmpty()) {
            return RelayPublishOutbox.KickDrainResult(
                remaining = 0,
                publishedOk = 0,
                publishedFail = 0,
                stoppedBecauseQueueEmpty = true,
            )
        }
        return forceResync(appContext, relayPool, signer, dataRelayUrls, dayLogEntries)
    }

    fun formatDayLogResyncMessage(
        dayLogCount: Int,
        drain: RelayPublishOutbox.KickDrainResult,
    ): String = buildString {
        if (dayLogCount == 0) {
            append("No logged days to sync.")
            return@buildString
        }
        append("Queued $dayLogCount day log(s) for relay.")
        if (drain.publishedOk > 0 || drain.publishedFail > 0) {
            append(" Sent ${drain.publishedOk} now")
            if (drain.publishedFail > 0) {
                append(", ${drain.publishedFail} failed and will retry")
            }
            append('.')
        }
        if (drain.remaining > 0) {
            append(" ${drain.remaining} still queued.")
        }
    }
}
