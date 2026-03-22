package com.erv.app.stretching

import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.NostrFilter
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.UnsignedEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val STRETCHING_ROUTINES_D_TAG = "erv/stretching/routines"

@Serializable
private data class StretchRoutinesPayload(
    val routines: List<StretchRoutine> = emptyList()
)

object StretchingSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun publishRoutinesMaster(
        relayPool: RelayPool,
        signer: EventSigner,
        state: StretchLibraryState
    ): Boolean {
        val payload = StretchRoutinesPayload(routines = state.routines)
        val content = json.encodeToString(StretchRoutinesPayload.serializer(), payload)
        return publishEvent(relayPool, signer, STRETCHING_ROUTINES_D_TAG, content)
    }

    suspend fun publishDailyLog(
        relayPool: RelayPool,
        signer: EventSigner,
        log: StretchDayLog
    ): Boolean {
        val content = json.encodeToString(StretchDayLog.serializer(), log)
        return publishEvent(relayPool, signer, dailyTag(log.date), content)
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): StretchLibraryState? = coroutineScope {
        val subId = "erv-stretching-${System.currentTimeMillis()}"
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(30078),
                authors = listOf(pubkeyHex),
                limit = 100
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

        val master = latestByTag[STRETCHING_ROUTINES_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeRoutinesMaster(it) }

        val logs = latestByTag
            .filterKeys { it.startsWith("erv/stretching/") && it != STRETCHING_ROUTINES_D_TAG }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/stretching/")
                decodeLog(raw, date)
            }

        StretchLibraryState(
            routines = master?.routines ?: emptyList(),
            logs = logs.sortedBy { it.date }
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

    private fun decodeRoutinesMaster(raw: String): StretchRoutinesPayload? =
        try {
            json.decodeFromString(StretchRoutinesPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeLog(raw: String, date: String): StretchDayLog? =
        try {
            val parsed = json.decodeFromString(StretchDayLog.serializer(), raw)
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

    private fun dailyTag(date: String): String = "erv/stretching/$date"
}
