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
import kotlinx.coroutines.flow.first

data class CurrentRelayDataCoverage(
    val foundPayloadCount: Int,
    val totalPayloadCount: Int,
    val connectedRelayCount: Int,
    val configuredRelayCount: Int,
)

object CurrentRelayDataSync {

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
    ): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += WeightSync.fullOutboxEntries(weightRepository.currentState())
        pairs += CardioSync.fullOutboxEntries(cardioRepository.currentState())
        pairs += StretchingSync.fullOutboxEntries(stretchingRepository.currentState())
        pairs += HeatColdSync.fullOutboxEntries(heatColdRepository.currentState())
        pairs += LightSync.fullOutboxEntries(lightTherapyRepository.currentState())
        pairs += SupplementSync.fullOutboxEntries(supplementRepository.currentState())
        pairs += ProgramSync.fullOutboxEntries(programRepository.currentState())
        pairs += BodyTrackerSync.fullOutboxEntries(bodyTrackerRepository.currentState())

        val gymMembership = userPreferences.gymMembership.first()
        val equipment = userPreferences.ownedEquipment.first()
        if (gymMembership || equipment.isNotEmpty()) {
            pairs += FitnessEquipmentSync.plaintextFor(gymMembership, equipment)
        }

        return pairs.distinctBy { it.first }
    }

    suspend fun probeCoverage(
        signer: EventSigner,
        dataRelayUrls: List<String>,
        localEntries: List<Pair<String, String>>,
        timeoutMs: Long = 5000,
    ): CurrentRelayDataCoverage {
        if (dataRelayUrls.isEmpty()) {
            return CurrentRelayDataCoverage(
                foundPayloadCount = 0,
                totalPayloadCount = localEntries.size,
                connectedRelayCount = 0,
                configuredRelayCount = 0,
            )
        }

        val tempPool = RelayPool(signer)
        try {
            tempPool.setRelays(dataRelayUrls)
            tempPool.awaitAtLeastOneConnected(timeoutMs = timeoutMs)
            val latestByTag = fetchLatestKind30078ByDTag(
                relayPool = tempPool,
                pubkeyHex = signer.publicKey,
                timeoutMs = timeoutMs,
            )
            val localTags = localEntries.map { it.first }.toSet()
            val connectedCount = dataRelayUrls.count { url ->
                tempPool.relayStates.value[url].let { state ->
                    state is ConnectionState.Connected || state is ConnectionState.Authenticated
                }
            }
            return CurrentRelayDataCoverage(
                foundPayloadCount = localTags.count { it in latestByTag },
                totalPayloadCount = localTags.size,
                connectedRelayCount = connectedCount,
                configuredRelayCount = dataRelayUrls.size,
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
        outbox.enqueueAll(localEntries)
        return outbox.kickDrain(relayPool, signer, dataRelayUrls)
    }
}
