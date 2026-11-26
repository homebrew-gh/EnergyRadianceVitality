package com.erv.app.fasting

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.decryptSelfPayloadOrNull
import com.erv.app.nostr.publishSelfKind30078
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** `erv/fasting/state`: the running fast, the daily schedule and delete markers. */
const val FASTING_STATE_D_TAG = "erv/fasting/state"

private const val FASTING_DAY_PREFIX = "erv/fasting/"

@Serializable
private data class FastingStatePayload(
    val activeSession: FastingSession? = null,
    val intermittentPlan: IntermittentFastingPlan = IntermittentFastingPlan(),
    val deletedSessionIds: Map<String, Long> = emptyMap(),
)

/**
 * Relay sync for fasting, encrypt-to-self kind 30078 like the other sections:
 * [FASTING_STATE_D_TAG] plus one `erv/fasting/<yyyy-MM-dd>` day log per day with fasts
 * (dated by when the fast ended). Merging is [mergeFastingStates].
 */
object FastingSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun dayTag(dateIso: String): String = "$FASTING_DAY_PREFIX$dateIso"

    private fun statePlaintext(state: FastingLibraryState): String =
        json.encodeToString(
            FastingStatePayload.serializer(),
            FastingStatePayload(state.activeSession, state.intermittentPlan, state.deletedSessionIds),
        )

    private fun dayPlaintext(state: FastingLibraryState, dateIso: String): String =
        json.encodeToString(
            FastingDayLog.serializer(),
            state.dayLogs().firstOrNull { it.date == dateIso } ?: FastingDayLog(dateIso),
        )

    /**
     * Publishes the state tag and the day logs for [dates] (a day left with no fasts goes up
     * empty, so a delete or a move clears it on the relay). Returns true when all went out.
     */
    suspend fun publishAfterChange(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: FastingLibraryState,
        dates: Collection<LocalDate>,
        dataRelayUrls: List<String>,
    ): Boolean {
        var ok = publishSelfKind30078(appContext, relayPool, signer, FASTING_STATE_D_TAG, statePlaintext(state), dataRelayUrls)
        for (date in dates.distinct()) {
            val iso = date.toString()
            ok = publishSelfKind30078(appContext, relayPool, signer, dayTag(iso), dayPlaintext(state, iso), dataRelayUrls) && ok
        }
        return ok
    }

    fun fullOutboxEntries(state: FastingLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += FASTING_STATE_D_TAG to statePlaintext(state)
        for (log in state.dayLogs()) {
            pairs += dayTag(log.date) to json.encodeToString(FastingDayLog.serializer(), log)
        }
        return pairs
    }

    suspend fun fromLatestByTag(latestByTag: Map<String, NostrEvent>, signer: EventSigner): FastingLibraryState {
        val statePayload = latestByTag[FASTING_STATE_D_TAG]
            ?.decryptSelfPayloadOrNull(signer)
            ?.let { raw -> runCatching { json.decodeFromString(FastingStatePayload.serializer(), raw) }.getOrNull() }
        val history = latestByTag
            .filterKeys { it.startsWith(FASTING_DAY_PREFIX) && it != FASTING_STATE_D_TAG }
            .flatMap { (_, event) ->
                val raw = event.decryptSelfPayloadOrNull(signer) ?: return@flatMap emptyList()
                runCatching { json.decodeFromString(FastingDayLog.serializer(), raw).sessions }.getOrElse { emptyList() }
            }
            .distinctBy { it.id }
        return FastingLibraryState(
            activeSession = statePayload?.activeSession,
            history = history,
            intermittentPlan = statePayload?.intermittentPlan ?: IntermittentFastingPlan(),
            deletedSessionIds = statePayload?.deletedSessionIds ?: emptyMap(),
        )
    }
}

/**
 * Publishes the current fasting state and the given days after a local change, when signed in
 * with a relay pool. Safe to call with nulls (local-only use).
 */
suspend fun publishFastingChange(
    appContext: Context,
    relayPool: RelayPool?,
    signer: EventSigner?,
    repository: FastingRepository,
    dates: Collection<LocalDate>,
    dataRelayUrls: List<String>,
) {
    if (relayPool == null || signer == null) return
    FastingSync.publishAfterChange(appContext, relayPool, signer, repository.currentState(), dates, dataRelayUrls)
}
