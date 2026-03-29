package com.erv.app.stretching

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
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: StretchLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val payload = StretchRoutinesPayload(routines = state.routines)
        val content = json.encodeToString(StretchRoutinesPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, STRETCHING_ROUTINES_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: StretchDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(StretchDayLog.serializer(), log)
        return publishEvent(appContext, relayPool, signer, dailyTag(log.date), content, dataRelayUrls)
    }

    fun fullOutboxEntries(state: StretchLibraryState): List<Pair<String, String>> {
        val masterPayload = StretchRoutinesPayload(routines = state.routines)
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += STRETCHING_ROUTINES_D_TAG to json.encodeToString(StretchRoutinesPayload.serializer(), masterPayload)
        for (log in state.logs) {
            pairs += dailyTag(log.date) to json.encodeToString(StretchDayLog.serializer(), log)
        }
        return pairs
    }

    fun clearOutboxEntries(state: StretchLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += STRETCHING_ROUTINES_D_TAG to json.encodeToString(
            StretchRoutinesPayload.serializer(),
            StretchRoutinesPayload()
        )
        for (dateIso in state.logs.map { it.date }.distinct().sorted()) {
            pairs += dailyTag(dateIso) to json.encodeToString(
                StretchDayLog.serializer(),
                StretchDayLog(date = dateIso)
            )
        }
        return pairs
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): StretchLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): StretchLibraryState {
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

        return StretchLibraryState(
            routines = master?.routines ?: emptyList(),
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

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }

    private fun dailyTag(date: String): String = "erv/stretching/$date"
}
