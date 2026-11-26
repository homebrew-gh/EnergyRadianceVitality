package com.erv.app.heatcold

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.dTagOrNull
import com.erv.app.nostr.fetchLatestKind30078ByDTag
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.decryptSelfPayloadOrNull
import com.erv.app.nostr.publishSelfKind30078
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

object HeatColdSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun dTagFor(mode: HeatColdMode, dateIso: String): String = when (mode) {
        HeatColdMode.SAUNA -> "erv/sauna/$dateIso"
        HeatColdMode.COLD_PLUNGE -> "erv/cold/$dateIso"
    }

    suspend fun publishDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        mode: HeatColdMode,
        log: HeatColdDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(HeatColdDayLog.serializer(), log)
        return publishSelfKind30078(appContext, relayPool, signer, dTagFor(mode, log.date), content, dataRelayUrls)
    }

    /** Publishes the sauna and cold plunge day logs for [date] (whichever exist). */
    suspend fun publishDay(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: HeatColdLibraryState,
        date: LocalDate,
        dataRelayUrls: List<String>,
    ) {
        for (mode in HeatColdMode.entries) {
            state.logFor(mode, date)?.let { log ->
                publishDailyLog(appContext, relayPool, signer, mode, log, dataRelayUrls)
            }
        }
    }

    fun fullOutboxEntries(state: HeatColdLibraryState): List<Pair<String, String>> =
        HeatColdMode.entries.flatMap { mode ->
            state.logsFor(mode).map { log ->
                dTagFor(mode, log.date) to json.encodeToString(HeatColdDayLog.serializer(), log)
            }
        }

    fun clearOutboxEntries(state: HeatColdLibraryState): List<Pair<String, String>> =
        HeatColdMode.entries.flatMap { mode ->
            state.logsFor(mode).map { it.date }.distinct().sorted().map { dateIso ->
                dTagFor(mode, dateIso) to json.encodeToString(
                    HeatColdDayLog.serializer(),
                    HeatColdDayLog(date = dateIso)
                )
            }
        }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): HeatColdLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): HeatColdLibraryState {
        val saunaLogs = latestByTag
            .filterKeys { it.startsWith("erv/sauna/") }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptSelfPayloadOrNull(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/sauna/")
                decodeLog(raw, date)
            }

        val coldLogs = latestByTag
            .filterKeys { it.startsWith("erv/cold/") }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptSelfPayloadOrNull(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/cold/")
                decodeLog(raw, date)
            }

        return HeatColdLibraryState(
            saunaLogs = saunaLogs.sortedBy { it.date },
            coldLogs = coldLogs.sortedBy { it.date }
        )
    }

    private fun decodeLog(raw: String, date: String): HeatColdDayLog? =
        try {
            val parsed = json.decodeFromString(HeatColdDayLog.serializer(), raw)
            if (parsed.date.isBlank()) parsed.copy(date = date) else parsed
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
