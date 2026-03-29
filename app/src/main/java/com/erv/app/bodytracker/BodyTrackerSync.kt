package com.erv.app.bodytracker

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.dTagOrNull
import com.erv.app.nostr.fetchLatestKind30078ByDTag
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

private const val SETTINGS_D_TAG = "erv/body_tracker/settings"

@Serializable
private data class BodyTrackerDayNostrPayload(
    val date: String,
    val weightKg: Double? = null,
    val measurementsCm: Map<String, Double> = emptyMap(),
    val note: String = "",
    val updatedAtEpochSeconds: Long = 0L
)

@Serializable
private data class BodyTrackerSettingsNostrPayload(
    val lengthUnit: BodyMeasurementLengthUnit = BodyMeasurementLengthUnit.CENTIMETERS,
    val updatedAtEpochSeconds: Long = 0L
)

object BodyTrackerSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun dailyTag(dateIso: String): String = "erv/body_tracker/$dateIso"

    private fun BodyTrackerDayLog.toNostrPayload(): BodyTrackerDayNostrPayload =
        BodyTrackerDayNostrPayload(
            date = date,
            weightKg = weightKg,
            measurementsCm = measurementsCm,
            note = note,
            updatedAtEpochSeconds = updatedAtEpochSeconds
        )

    private fun BodyTrackerDayNostrPayload.toDayLogWithoutPhotos(): BodyTrackerDayLog =
        BodyTrackerDayLog(
            date = date,
            weightKg = weightKg,
            measurementsCm = measurementsCm,
            note = note,
            photos = emptyList(),
            updatedAtEpochSeconds = updatedAtEpochSeconds
        )

    private fun encodeDayPlaintext(log: BodyTrackerDayLog): String =
        json.encodeToString(BodyTrackerDayNostrPayload.serializer(), log.toNostrPayload())

    private fun encodeEmptyDayPlaintext(dateIso: String, updatedAtEpochSeconds: Long): String {
        val payload = BodyTrackerDayNostrPayload(
            date = dateIso,
            updatedAtEpochSeconds = updatedAtEpochSeconds
        )
        return json.encodeToString(BodyTrackerDayNostrPayload.serializer(), payload)
    }

    private fun encodeSettingsPlaintext(state: BodyTrackerLibraryState): String {
        val payload = BodyTrackerSettingsNostrPayload(
            lengthUnit = state.lengthUnit,
            updatedAtEpochSeconds = state.lengthUnitUpdatedAtEpochSeconds
        )
        return json.encodeToString(BodyTrackerSettingsNostrPayload.serializer(), payload)
    }

    /**
     * Publishes weight / measurements / note for [log] (photos are never included).
     * Call only when [BodyTrackerDayLog.isNostrEmpty] is false.
     */
    suspend fun publishDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: BodyTrackerDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        if (log.isNostrEmpty()) return false
        return publishEvent(
            appContext,
            relayPool,
            signer,
            dailyTag(log.date),
            encodeDayPlaintext(log),
            dataRelayUrls
        )
    }

    /**
     * Replaceable empty day on relays (user removed the entry locally). Does not include photos.
     */
    suspend fun publishClearedDay(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dateIso: String,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = encodeEmptyDayPlaintext(dateIso, nowEpochSeconds())
        return publishEvent(appContext, relayPool, signer, dailyTag(dateIso), content, dataRelayUrls)
    }

    suspend fun publishSettings(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: BodyTrackerLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = encodeSettingsPlaintext(state)
        return publishEvent(appContext, relayPool, signer, SETTINGS_D_TAG, content, dataRelayUrls)
    }

    /**
     * After a local change, push the correct replaceable event for [date]: cleared day, full payload, or nothing
     * when the row is photos-only (relay payload unchanged).
     */
    suspend fun publishDayAfterLocalChange(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        repository: BodyTrackerRepository,
        date: LocalDate,
        dataRelayUrls: List<String>,
    ) {
        val log = repository.currentState().logFor(date)
        if (log == null) {
            publishClearedDay(appContext, relayPool, signer, date.toString(), dataRelayUrls)
        } else if (!log.isNostrEmpty()) {
            publishDailyLog(appContext, relayPool, signer, log, dataRelayUrls)
        } else if (log.updatedAtEpochSeconds > 0L) {
            val content = encodeEmptyDayPlaintext(log.date, log.updatedAtEpochSeconds)
            publishEvent(appContext, relayPool, signer, dailyTag(log.date), content, dataRelayUrls)
        }
    }

    fun fullOutboxEntries(state: BodyTrackerLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += SETTINGS_D_TAG to encodeSettingsPlaintext(state)
        for (log in state.logs) {
            when {
                !log.isNostrEmpty() -> pairs += dailyTag(log.date) to encodeDayPlaintext(log)
                log.updatedAtEpochSeconds > 0L ->
                    pairs += dailyTag(log.date) to encodeEmptyDayPlaintext(log.date, log.updatedAtEpochSeconds)
            }
        }
        return pairs
    }

    fun clearOutboxEntries(state: BodyTrackerLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += SETTINGS_D_TAG to encodeSettingsPlaintext(BodyTrackerLibraryState())
        for (dateIso in state.logs.map { it.date }.distinct().sorted()) {
            pairs += dailyTag(dateIso) to encodeEmptyDayPlaintext(dateIso, nowEpochSeconds())
        }
        return pairs
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 8000
    ): BodyTrackerLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): BodyTrackerLibraryState {
        val settingsRaw = latestByTag[SETTINGS_D_TAG]?.decryptPayload(signer)
        val settings = settingsRaw?.let { decodeSettings(it) }

        val logs = latestByTag
            .filterKeys { it.startsWith("erv/body_tracker/") && it != SETTINGS_D_TAG }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val dateIso = dTag.removePrefix("erv/body_tracker/")
                decodeDay(raw, dateIso)
            }

        return BodyTrackerLibraryState(
            lengthUnit = settings?.lengthUnit ?: BodyMeasurementLengthUnit.CENTIMETERS,
            lengthUnitUpdatedAtEpochSeconds = settings?.updatedAtEpochSeconds ?: 0L,
            logs = logs.sortedBy { it.date }
        )
    }

    private suspend fun publishEvent(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dTag: String,
        plaintext: String,
        dataRelayUrls: List<String>,
    ): Boolean {
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

    private fun decodeSettings(raw: String): BodyTrackerSettingsNostrPayload? =
        try {
            json.decodeFromString(BodyTrackerSettingsNostrPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeDay(raw: String, date: String): BodyTrackerDayLog? =
        try {
            val parsed = json.decodeFromString(BodyTrackerDayNostrPayload.serializer(), raw)
            val fixedDate = if (parsed.date.isBlank()) date else parsed.date
            parsed.copy(date = fixedDate).toDayLogWithoutPhotos()
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
}
