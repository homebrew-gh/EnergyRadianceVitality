package com.erv.app.heatcold

import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.NostrFilter
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.UnsignedEvent
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
        relayPool: RelayPool,
        signer: EventSigner,
        log: HeatColdDayLog
    ): Boolean {
        val content = json.encodeToString(HeatColdDayLog.serializer(), log)
        return publishEvent(relayPool, signer, "erv/sauna/${log.date}", content)
    }

    suspend fun publishColdDailyLog(
        relayPool: RelayPool,
        signer: EventSigner,
        log: HeatColdDayLog
    ): Boolean {
        val content = json.encodeToString(HeatColdDayLog.serializer(), log)
        return publishEvent(relayPool, signer, "erv/cold/${log.date}", content)
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
        relayPool: RelayPool,
        signer: EventSigner,
        dTag: String,
        plaintext: String
    ): Boolean {
        val encrypted = signer.encryptToSelf(plaintext)
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = nowEpochSeconds(),
            kind = 30078,
            tags = listOf(listOf("d", dTag)),
            content = encrypted
        )
        val signed = signer.sign(unsigned)
        return relayPool.publish(signed)
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
