package com.erv.app.heatcold

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.NostrFilter
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object HeatColdSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun publishSaunaDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: HeatColdDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(HeatColdDayLog.serializer(), log)
        return publishEvent(appContext, relayPool, signer, "erv/sauna/${log.date}", content, dataRelayUrls)
    }

    suspend fun publishColdDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: HeatColdDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(HeatColdDayLog.serializer(), log)
        return publishEvent(appContext, relayPool, signer, "erv/cold/${log.date}", content, dataRelayUrls)
    }

    fun fullOutboxEntries(state: HeatColdLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        for (log in state.saunaLogs) {
            pairs += "erv/sauna/${log.date}" to json.encodeToString(HeatColdDayLog.serializer(), log)
        }
        for (log in state.coldLogs) {
            pairs += "erv/cold/${log.date}" to json.encodeToString(HeatColdDayLog.serializer(), log)
        }
        return pairs
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): HeatColdLibraryState? = coroutineScope {
        val subId = "erv-heatcold-${System.currentTimeMillis()}"
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(30078),
                authors = listOf(pubkeyHex),
                limit = 200
            )
        )

        val events = mutableListOf<NostrEvent>()
        val job = launch {
            relayPool.events.collect { (id, ev) ->
                if (id == subId && ev.kind == 30078) events.add(ev)
            }
        }

        delay(timeoutMs)
        job.cancel()
        relayPool.unsubscribe(subId)

        if (events.isEmpty()) return@coroutineScope null

        val latestByTag = events
            .sortedBy { it.createdAt }
            .groupBy { it.dTagOrNull() ?: "unknown" }
            .mapValues { (_, items) -> items.last() }

        val saunaLogs = latestByTag
            .filterKeys { it.startsWith("erv/sauna/") }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/sauna/")
                decodeLog(raw, date)
            }

        val coldLogs = latestByTag
            .filterKeys { it.startsWith("erv/cold/") }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/cold/")
                decodeLog(raw, date)
            }

        HeatColdLibraryState(
            saunaLogs = saunaLogs.sortedBy { it.date },
            coldLogs = coldLogs.sortedBy { it.date }
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

    private fun decodeLog(raw: String, date: String): HeatColdDayLog? =
        try {
            val parsed = json.decodeFromString(HeatColdDayLog.serializer(), raw)
            if (parsed.date.isBlank()) parsed.copy(date = date) else parsed
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun NostrEvent.dTagOrNull(): String? =
        tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1)

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }
}
