package com.erv.app.supplements

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.NostrFilter
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val SUPPLEMENT_MASTER_D_TAG = "erv/supplements/list"

@Serializable
private data class SupplementMasterPayload(
    val supplements: List<SupplementEntry> = emptyList(),
    val routines: List<SupplementRoutine> = emptyList()
)

object SupplementSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun publishAll(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: SupplementLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val outbox = RelayPublishOutbox.get(appContext)
        outbox.enqueueAllDigestsAware(appContext, fullOutboxEntries(state))
        val r = outbox.kickDrain(relayPool, signer, dataRelayUrls)
        return r.publishedFail == 0 && r.remaining == 0
    }

    fun fullOutboxEntries(state: SupplementLibraryState): List<Pair<String, String>> {
        val masterPayload = SupplementMasterPayload(
            supplements = state.supplements,
            routines = state.routines
        )
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += SUPPLEMENT_MASTER_D_TAG to json.encodeToString(
            SupplementMasterPayload.serializer(),
            masterPayload
        )
        for (log in state.logs) {
            pairs += dailyTag(log.date) to json.encodeToString(SupplementDayLog.serializer(), log)
        }
        return pairs
    }

    suspend fun publishMaster(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: SupplementLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val payload = SupplementMasterPayload(
            supplements = state.supplements,
            routines = state.routines
        )
        val content = json.encodeToString(SupplementMasterPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, SUPPLEMENT_MASTER_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: SupplementDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(SupplementDayLog.serializer(), log)
        return publishEvent(appContext, relayPool, signer, dailyTag(log.date), content, dataRelayUrls)
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): SupplementLibraryState? = coroutineScope {
        val subId = "erv-supplements-${System.currentTimeMillis()}"
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

        val master = latestByTag[SUPPLEMENT_MASTER_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeMaster(it) }

        val logs = latestByTag
            .filterKeys { it.startsWith("erv/supplements/") && it != SUPPLEMENT_MASTER_D_TAG }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                decodeLog(raw, dTag.removePrefix("erv/supplements/"))
            }

        val combined = SupplementLibraryState(
            supplements = master?.supplements ?: emptyList(),
            routines = master?.routines ?: emptyList(),
            logs = logs.sortedBy { it.date }
        )
        combined
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

    private fun decodeMaster(raw: String): SupplementMasterPayload? =
        try {
            json.decodeFromString(SupplementMasterPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeLog(raw: String, date: String): SupplementDayLog? =
        try {
            val parsed = json.decodeFromString(SupplementDayLog.serializer(), raw)
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

    private fun dailyTag(date: String): String = "erv/supplements/$date"
}
