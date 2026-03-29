package com.erv.app.datadeletion

import android.content.Context
import com.erv.app.bodytracker.BodyTrackerRepository
import com.erv.app.bodytracker.BodyTrackerSync
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.data.UserPreferences
import com.erv.app.dataexport.UserDataSection
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.heatcold.HeatColdSync
import com.erv.app.lighttherapy.LightSync
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.FitnessEquipmentSync
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayOutboxStatusStore
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import com.erv.app.nostr.SettingsSync
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.ProgramSync
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.StretchingSync
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementSync
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataDeletionManager(
    context: Context,
    private val keyManager: KeyManager,
    private val userPreferences: UserPreferences,
    private val weightRepository: WeightRepository,
    private val cardioRepository: CardioRepository,
    private val stretchingRepository: StretchingRepository,
    private val heatColdRepository: HeatColdRepository,
    private val lightTherapyRepository: LightTherapyRepository,
    private val supplementRepository: SupplementRepository,
    private val programRepository: ProgramRepository,
    private val unifiedRoutineRepository: UnifiedRoutineRepository,
    private val bodyTrackerRepository: BodyTrackerRepository,
    private val reminderRepository: RoutineReminderRepository,
) {
    private val appContext = context.applicationContext

    private data class RelayCleanupOutcome(
        val message: String? = null,
        val remainingQueued: Int = 0,
    )

    data class Result(
        val message: String,
        val requiresAppLogout: Boolean = false,
    )

    suspend fun deleteSection(
        section: UserDataSection,
        relayPool: RelayPool?,
        signer: EventSigner?,
    ): Result = withContext(Dispatchers.IO) {
        val relayEntries = buildRelayClearEntriesForSection(section)
        clearLocalSection(section)
        val relayOutcome = relaySummary(
            entries = relayEntries,
            relayPool = relayPool,
            signer = signer,
        )
        Result(
            message = buildString {
                append("Deleted ${section.label.lowercase()} from this device.")
                relayOutcome.message?.let {
                    append(' ')
                    append(it)
                }
            }
        )
    }

    suspend fun deleteAllData(
        relayPool: RelayPool?,
        signer: EventSigner?,
    ): Result = withContext(Dispatchers.IO) {
        val relayEntries = buildRelayClearEntriesForAllData()
        clearAllLocalData()
        val relayOutcome = relaySummary(
            entries = relayEntries,
            relayPool = relayPool,
            signer = signer,
        )
        RelayPublishOutbox.get(appContext).clear()
        RelayPayloadDigestStore.get(appContext).clear()
        RelayOutboxStatusStore.get(appContext).clear()
        keyManager.logout()
        Result(
            message = buildString {
                append("Deleted all local data from this device and signed out.")
                relayOutcome.message?.let {
                    append(' ')
                    append(it)
                }
                if (relayOutcome.remainingQueued > 0) {
                    append(" Remaining cleanup updates were cleared with the local reset, so some relays may keep older copies.")
                }
            },
            requiresAppLogout = true,
        )
    }

    private suspend fun clearLocalSection(section: UserDataSection) {
        when (section) {
            UserDataSection.WEIGHT_TRAINING -> weightRepository.clearAllData()
            UserDataSection.CARDIO -> cardioRepository.clearAllData()
            UserDataSection.STRETCHING -> stretchingRepository.clearAllData()
            UserDataSection.HEAT_COLD -> heatColdRepository.clearAllData()
            UserDataSection.LIGHT_THERAPY -> lightTherapyRepository.clearAllData()
            UserDataSection.SUPPLEMENTS -> supplementRepository.clearAllData()
            UserDataSection.PROGRAMS -> {
                programRepository.clearAllData()
                userPreferences.clearProgramLaunchTargets()
            }
            UserDataSection.UNIFIED_ROUTINES -> {
                unifiedRoutineRepository.clearAllData()
                reminderRepository.clearAllData()
                userPreferences.clearProgramLaunchTargets()
            }
            UserDataSection.BODY_TRACKER -> bodyTrackerRepository.clearAllData()
            UserDataSection.REMINDERS -> reminderRepository.clearAllData()
            UserDataSection.PERSONAL_DATA -> userPreferences.clearPersonalData()
        }
    }

    private suspend fun clearAllLocalData() {
        weightRepository.clearAllData()
        cardioRepository.clearAllData()
        stretchingRepository.clearAllData()
        heatColdRepository.clearAllData()
        lightTherapyRepository.clearAllData()
        supplementRepository.clearAllData()
        programRepository.clearAllData()
        unifiedRoutineRepository.clearAllData()
        bodyTrackerRepository.clearAllData()
        reminderRepository.clearAllData()
        userPreferences.clearAllData()
    }

    private suspend fun buildRelayClearEntriesForSection(section: UserDataSection): List<Pair<String, String>> =
        when (section) {
            UserDataSection.WEIGHT_TRAINING ->
                WeightSync.clearOutboxEntries(weightRepository.currentState())
            UserDataSection.CARDIO ->
                CardioSync.clearOutboxEntries(cardioRepository.currentState())
            UserDataSection.STRETCHING ->
                StretchingSync.clearOutboxEntries(stretchingRepository.currentState())
            UserDataSection.HEAT_COLD ->
                HeatColdSync.clearOutboxEntries(heatColdRepository.currentState())
            UserDataSection.LIGHT_THERAPY ->
                LightSync.clearOutboxEntries(lightTherapyRepository.currentState())
            UserDataSection.SUPPLEMENTS ->
                SupplementSync.clearOutboxEntries(supplementRepository.currentState())
            UserDataSection.PROGRAMS ->
                ProgramSync.clearOutboxEntries(programRepository.currentState())
            UserDataSection.BODY_TRACKER ->
                BodyTrackerSync.clearOutboxEntries(bodyTrackerRepository.currentState())
            UserDataSection.PERSONAL_DATA ->
                listOf(FitnessEquipmentSync.plaintextFor(gymMembership = false, equipment = emptyList()))
            UserDataSection.UNIFIED_ROUTINES,
            UserDataSection.REMINDERS -> emptyList()
        }

    private suspend fun buildRelayClearEntriesForAllData(): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += buildRelayClearEntriesForSection(UserDataSection.WEIGHT_TRAINING)
        pairs += buildRelayClearEntriesForSection(UserDataSection.CARDIO)
        pairs += buildRelayClearEntriesForSection(UserDataSection.STRETCHING)
        pairs += buildRelayClearEntriesForSection(UserDataSection.HEAT_COLD)
        pairs += buildRelayClearEntriesForSection(UserDataSection.LIGHT_THERAPY)
        pairs += buildRelayClearEntriesForSection(UserDataSection.SUPPLEMENTS)
        pairs += buildRelayClearEntriesForSection(UserDataSection.PROGRAMS)
        pairs += buildRelayClearEntriesForSection(UserDataSection.BODY_TRACKER)
        pairs += buildRelayClearEntriesForSection(UserDataSection.PERSONAL_DATA)
        pairs += SettingsSync.plaintextFor(dataRelays = emptyList(), socialRelays = emptyList())
        return pairs.distinctBy { it.first }
    }

    private suspend fun relaySummary(
        entries: List<Pair<String, String>>,
        relayPool: RelayPool?,
        signer: EventSigner?,
    ): RelayCleanupOutcome {
        val uniqueEntries = entries.distinctBy { it.first }
        if (uniqueEntries.isEmpty()) return RelayCleanupOutcome()
        val hasUsedNostrBefore = userPreferences.hasUsedNostrIdentityNow()
        if (relayPool == null || signer == null || !keyManager.isLoggedIn) {
            return RelayCleanupOutcome(
                message = if (hasUsedNostrBefore) {
                    "Relay cleanup was skipped because you are not signed in. Sign in again with the same Nostr identity if you want ERV to try relay cleanup."
                } else {
                    null
                }
            )
        }
        val relayUrls = keyManager.relayUrlsForKind30078Publish()
        if (relayUrls.isEmpty()) {
            return RelayCleanupOutcome(
                message = "Relay cleanup was skipped because no data relays are configured. Previously synced encrypted data may still remain on relays."
            )
        }
        val outbox = RelayPublishOutbox.get(appContext)
        outbox.enqueueAllDigestsAware(appContext, uniqueEntries)
        val drain = outbox.kickDrain(relayPool, signer, relayUrls)
        return RelayCleanupOutcome(
            message = buildString {
                append("Best-effort relay cleanup queued ${uniqueEntries.size} replacement update(s).")
                if (drain.publishedOk > 0 || drain.publishedFail > 0) {
                    append(" Sent ${drain.publishedOk} now")
                    if (drain.publishedFail > 0) {
                        append(", ${drain.publishedFail} failed and will retry")
                    }
                    append('.')
                }
                if (drain.remaining > 0) {
                    append(" ${drain.remaining} update(s) are still queued for retry.")
                }
                append(" ERV cannot guarantee every relay permanently deletes retained copies.")
            },
            remainingQueued = drain.remaining,
        )
    }
}
