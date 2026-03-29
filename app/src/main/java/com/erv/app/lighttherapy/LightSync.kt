package com.erv.app.lighttherapy

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
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: LightLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val payload = LightMasterPayload(
            devices = state.devices,
            routines = state.routines
        )
        val content = json.encodeToString(LightMasterPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, LIGHT_MASTER_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishDailyLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: LightDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(LightDayLog.serializer(), log)
        return publishEvent(appContext, relayPool, signer, dailyTag(log.date), content, dataRelayUrls)
    }

    fun fullOutboxEntries(state: LightLibraryState): List<Pair<String, String>> {
        val masterPayload = LightMasterPayload(
            devices = state.devices,
            routines = state.routines
        )
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += LIGHT_MASTER_D_TAG to json.encodeToString(LightMasterPayload.serializer(), masterPayload)
        for (log in state.logs) {
            pairs += dailyTag(log.date) to json.encodeToString(LightDayLog.serializer(), log)
        }
        return pairs
    }

    fun clearOutboxEntries(state: LightLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += LIGHT_MASTER_D_TAG to json.encodeToString(
            LightMasterPayload.serializer(),
            LightMasterPayload()
        )
        for (dateIso in state.logs.map { it.date }.distinct().sorted()) {
            pairs += dailyTag(dateIso) to json.encodeToString(
                LightDayLog.serializer(),
                LightDayLog(date = dateIso)
            )
        }
        return pairs
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): LightLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): LightLibraryState {
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

        return LightLibraryState(
            devices = master?.devices ?: emptyList(),
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

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }

    private fun dailyTag(date: String): String = "erv/light/$date"
}
