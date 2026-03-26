package com.erv.app.cardio

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.NostrFilter
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import java.time.LocalDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private const val CARDIO_MASTER_D_TAG = "erv/cardio/routines"

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
        relayPool: RelayPool,
        signer: EventSigner,
        log: CardioDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(CardioDayLog.serializer(), log.withoutGpsTracks())
        return publishEvent(appContext, relayPool, signer, dailyTag(log.date), content, dataRelayUrls)
    }

    fun fullOutboxEntries(state: CardioLibraryState): List<Pair<String, String>> =
        cardioImportOutboxEntries(state, state.logs.map { it.date })

    /** Master + day logs for [com.erv.app.nostr.RelayPublishOutbox] after cardio import. */
    fun cardioImportOutboxEntries(
        state: CardioLibraryState,
        affectedDates: List<String>,
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
            pairs += dailyTag(dateIso) to json.encodeToString(
                CardioDayLog.serializer(),
                log.withoutGpsTracks()
            )
        }
        return pairs
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): CardioLibraryState? = coroutineScope {
        val subId = "erv-cardio-${System.currentTimeMillis()}"
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

        val master = latestByTag[CARDIO_MASTER_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeMaster(it) }

        val logs = latestByTag
            .filterKeys { it.startsWith("erv/cardio/") && it != CARDIO_MASTER_D_TAG }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/cardio/")
                decodeLog(raw, date)
            }

        CardioLibraryState(
            routines = master?.routines ?: emptyList(),
            customActivityTypes = master?.customActivityTypes ?: emptyList(),
            quickLaunches = master?.quickLaunches ?: emptyList(),
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

    private fun NostrEvent.dTagOrNull(): String? =
        tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1)

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }

    private fun dailyTag(date: String): String = "erv/cardio/$date"
}
