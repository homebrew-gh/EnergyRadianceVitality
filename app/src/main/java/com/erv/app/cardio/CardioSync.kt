package com.erv.app.cardio

import android.content.Context
import com.erv.app.data.UserPreferences
import com.erv.app.hr.HeartRateZoneInputs
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.dTagOrNull
import com.erv.app.nostr.fetchLatestKind30078ByDTag
import com.erv.app.nostr.isTrainingDayLogDTag
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import com.erv.app.nostr.TrainingDayLogRelaySync
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first

private const val CARDIO_MASTER_D_TAG = "erv/cardio/routines"
private const val MAX_TRAINING_DAY_PLAINTEXT_BYTES = 40_000

@Serializable
private data class CardioMasterPayload(
    val routines: List<CardioRoutine> = emptyList(),
    val customActivityTypes: List<CardioCustomActivityType> = emptyList(),
    val quickLaunches: List<CardioQuickLaunch> = emptyList()
)

object CardioSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun publishMaster(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: CardioLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val payload = CardioMasterPayload(
            routines = state.routines,
            customActivityTypes = state.customActivityTypes,
            quickLaunches = state.quickLaunches
        )
        val content = json.encodeToString(CardioMasterPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, CARDIO_MASTER_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishDailyLog(
        appContext: Context,
        relayPool: RelayPool?,
        signer: EventSigner?,
        log: CardioDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        queueDayLogForRelay(appContext, log)
        return relayPool != null && signer != null && dataRelayUrls.isNotEmpty()
    }

    /** Always queues a cardio day log for relay upload (does not require an active [RelayPool]). */
    suspend fun queueDayLogForRelay(appContext: Context, log: CardioDayLog) {
        if (log.sessions.isEmpty()) return
        val dayTag = dailyTag(log.date)
        val zoneInputs = UserPreferences(appContext).heartRateZoneInputs.first()
        val entries = outboxEntriesForDayLog(log, zoneInputs)
        if (entries.size == 1 && entries.first().first == dayTag) {
            TrainingDayLogRelaySync.queueTrainingDayLog(appContext, dayTag, entries.first().second)
        } else {
            TrainingDayLogRelaySync.queueTrainingDayLogEntries(
                appContext = appContext,
                replaceDTags = listOf(dayTag),
                entries = entries,
            )
        }
    }

    fun fullOutboxEntries(
        state: CardioLibraryState,
        zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
    ): List<Pair<String, String>> =
        cardioImportOutboxEntries(state, state.logs.map { it.date }, zoneInputs)

    /** Day logs only (for Progress / silo log resync). Skips empty days and the routines master. */
    fun dayLogOutboxEntries(
        state: CardioLibraryState,
        zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
    ): List<Pair<String, String>> =
        cardioImportOutboxEntries(state, state.logs.map { it.date }, zoneInputs)
            .filter { it.first != CARDIO_MASTER_D_TAG }

    fun clearOutboxEntries(state: CardioLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val emptyMaster = CardioMasterPayload()
        pairs += CARDIO_MASTER_D_TAG to json.encodeToString(
            CardioMasterPayload.serializer(),
            emptyMaster
        )
        for (dateIso in state.logs.map { it.date }.distinct().sorted()) {
            pairs += dailyTag(dateIso) to json.encodeToString(
                CardioDayLog.serializer(),
                CardioDayLog(date = dateIso)
            )
        }
        return pairs
    }

    /** Master + day logs for [com.erv.app.nostr.RelayPublishOutbox] after cardio import. */
    fun cardioImportOutboxEntries(
        state: CardioLibraryState,
        affectedDates: List<String>,
        zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
    ): List<Pair<String, String>> {
        val masterPayload = CardioMasterPayload(
            routines = state.routines,
            customActivityTypes = state.customActivityTypes,
            quickLaunches = state.quickLaunches
        )
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += CARDIO_MASTER_D_TAG to json.encodeToString(
            CardioMasterPayload.serializer(),
            masterPayload
        )
        for (dateIso in affectedDates.distinct().sorted()) {
            val log = state.logFor(LocalDate.parse(dateIso)) ?: continue
            if (log.sessions.isEmpty()) continue
            pairs += outboxEntriesForDayLog(log, zoneInputs)
        }
        return pairs
    }

    private fun outboxEntriesForDayLog(
        log: CardioDayLog,
        zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
    ): List<Pair<String, String>> {
        val safe = log.relaySafeForPublish(zoneInputs)
        val dayTag = dailyTag(log.date)
        val dayContent = json.encodeToString(CardioDayLog.serializer(), safe)
        if (dayContent.toByteArray(Charsets.UTF_8).size <= MAX_TRAINING_DAY_PLAINTEXT_BYTES) {
            return listOf(dayTag to dayContent)
        }
        return safe.sessions.map { session ->
            val splitLog = CardioDayLog(date = safe.date, sessions = listOf(session))
            "$dayTag/session/${session.id}" to json.encodeToString(CardioDayLog.serializer(), splitLog)
        }
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): CardioLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs, signer = signer)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): CardioLibraryState {
        val master = latestByTag[CARDIO_MASTER_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeMaster(it) }

        val decodedLogs = latestByTag
            .filterKeys { it.startsWith("erv/cardio/") && it != CARDIO_MASTER_D_TAG }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dateFromCardioDTag(dTag)
                decodeLog(raw, date)
            }

        return CardioLibraryState(
            routines = master?.routines ?: emptyList(),
            customActivityTypes = master?.customActivityTypes ?: emptyList(),
            quickLaunches = master?.quickLaunches ?: emptyList(),
            logs = mergeDecodedLogs(decodedLogs)
        )
    }

    private fun dateFromCardioDTag(dTag: String): String =
        dTag.removePrefix("erv/cardio/").substringBefore("/session/")

    private fun mergeDecodedLogs(logs: List<CardioDayLog>): List<CardioDayLog> =
        logs.groupBy { it.date }
            .map { (date, sameDay) ->
                val sessions = sameDay
                    .flatMap { it.sessions }
                    .distinctBy { it.id }
                CardioDayLog(date = date, sessions = sessions)
            }
            .sortedBy { it.date }

    private suspend fun publishEvent(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dTag: String,
        plaintext: String,
        dataRelayUrls: List<String>,
    ): Boolean {
        if (isTrainingDayLogDTag(dTag)) {
            RelayPayloadDigestStore.get(appContext).clearDigests(listOf(dTag))
        }
        val r = RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            dTag,
            plaintext,
        )
        return r.publishedFail == 0
    }

    private fun decodeMaster(raw: String): CardioMasterPayload? =
        try {
            json.decodeFromString(CardioMasterPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeLog(raw: String, date: String): CardioDayLog? =
        try {
            val parsed = json.decodeFromString(CardioDayLog.serializer(), raw)
            if (parsed.date.isBlank()) parsed.copy(date = date) else parsed
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }

    private fun dailyTag(date: String): String = "erv/cardio/$date"
}
