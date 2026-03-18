package com.erv.app.lighttherapy

import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.NostrFilter
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.UnsignedEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val LIGHT_MASTER_D_TAG = "erv/light/list"

@Serializable
private data class LightMasterPayload(
    val devices: List<LightDevice> = emptyList(),
    val routines: List<LightRoutine> = emptyList()
)

object LightSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun publishMaster(
        relayPool: RelayPool,
        signer: EventSigner,
        state: LightLibraryState
    ): Boolean {
        val payload = LightMasterPayload(
            devices = state.devices,
            routines = state.routines
        )
        val content = json.encodeToString(LightMasterPayload.serializer(), payload)
        return publishEvent(relayPool, signer, LIGHT_MASTER_D_TAG, content)
    }

    suspend fun publishDailyLog(
        relayPool: RelayPool,
        signer: EventSigner,
        log: LightDayLog
    ): Boolean {
        val content = json.encodeToString(LightDayLog.serializer(), log)
        return publishEvent(relayPool, signer, dailyTag(log.date), content)
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): LightLibraryState? = coroutineScope {
        val subId = "erv-light-${System.currentTimeMillis()}"
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

        val master = latestByTag[LIGHT_MASTER_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeMaster(it) }

        val logs = latestByTag
            .filterKeys { it.startsWith("erv/light/") && it != LIGHT_MASTER_D_TAG }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/light/")
                decodeLog(raw, date)
            }

        LightLibraryState(
            devices = master?.devices ?: emptyList(),
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

    private fun decodeMaster(raw: String): LightMasterPayload? =
        try {
            json.decodeFromString(LightMasterPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeLog(raw: String, date: String): LightDayLog? =
        try {
            val parsed = json.decodeFromString(LightDayLog.serializer(), raw)
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

    private fun dailyTag(date: String): String = "erv/light/$date"
}
