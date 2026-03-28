package com.erv.app.weighttraining

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.dTagOrNull
import com.erv.app.nostr.fetchLatestKind30078ByDTag
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal const val WEIGHT_EXERCISES_D_TAG = "erv/weight/exercises"
internal const val WEIGHT_ROUTINES_D_TAG = "erv/weight/routines"

@Serializable
private data class WeightExercisesPayload(
    val exercises: List<WeightExercise> = emptyList()
)

@Serializable
private data class WeightRoutinesPayload(
    val routines: List<WeightRoutine> = emptyList()
)

object WeightSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun relaySafeDayLog(log: WeightDayLog): WeightDayLog =
        log.copy(
            workouts = log.workouts.map { workout ->
                workout.copy(heartRateExerciseSegments = emptyList())
            }
        )

    suspend fun publishExercises(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        exercises: List<WeightExercise>,
        dataRelayUrls: List<String>,
    ): Boolean {
        val payload = WeightExercisesPayload(
            exercises = exercises.map { it.copy(sessionSummaries = emptyList()) }
        )
        val content = json.encodeToString(WeightExercisesPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, WEIGHT_EXERCISES_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishRoutines(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        routines: List<WeightRoutine>,
        dataRelayUrls: List<String>,
    ): Boolean {
        val payload = WeightRoutinesPayload(routines = routines)
        val content = json.encodeToString(WeightRoutinesPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, WEIGHT_ROUTINES_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishDayLog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        log: WeightDayLog,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(WeightDayLog.serializer(), relaySafeDayLog(log))
        return publishEvent(appContext, relayPool, signer, dailyTag(log.date), content, dataRelayUrls)
    }

    fun fullOutboxEntries(state: WeightLibraryState): List<Pair<String, String>> =
        weightImportOutboxEntries(state, state.logs.map { it.date })

    /**
     * Ordered payloads for [com.erv.app.nostr.RelayPublishOutbox] after a weight import
     * (exercises master, routines master, then each day).
     */
    fun weightImportOutboxEntries(
        state: WeightLibraryState,
        affectedDates: List<String>,
    ): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += masterOutboxEntries(state)
        for (dateIso in affectedDates.distinct().sorted()) {
            val log = state.logFor(LocalDate.parse(dateIso)) ?: continue
            pairs += dailyTag(dateIso) to json.encodeToString(
                WeightDayLog.serializer(),
                relaySafeDayLog(log)
            )
        }
        return pairs
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): WeightLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): WeightLibraryState {
        val exercises = latestByTag[WEIGHT_EXERCISES_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeExercises(it) }
            ?: emptyList()

        val routines = latestByTag[WEIGHT_ROUTINES_D_TAG]
            ?.decryptPayload(signer)
            ?.let { decodeRoutines(it) }
            ?: emptyList()

        val logs = latestByTag
            .filterKeys { tag ->
                tag.startsWith("erv/weight/") &&
                    tag != WEIGHT_EXERCISES_D_TAG &&
                    tag != WEIGHT_ROUTINES_D_TAG
            }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                val date = dTag.removePrefix("erv/weight/")
                decodeDayLog(raw, date)
            }

        return WeightLibraryState(
            exercises = exercises,
            routines = routines,
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

    private fun masterOutboxEntries(state: WeightLibraryState): List<Pair<String, String>> {
        val exercisesPayload = WeightExercisesPayload(
            exercises = state.exercises.map { it.copy(sessionSummaries = emptyList()) }
        )
        val routinesPayload = WeightRoutinesPayload(routines = state.routines)
        return listOf(
            WEIGHT_EXERCISES_D_TAG to json.encodeToString(
                WeightExercisesPayload.serializer(),
                exercisesPayload
            ),
            WEIGHT_ROUTINES_D_TAG to json.encodeToString(
                WeightRoutinesPayload.serializer(),
                routinesPayload
            ),
        )
    }

    private fun decodeExercises(raw: String): List<WeightExercise>? =
        try {
            json.decodeFromString(WeightExercisesPayload.serializer(), raw).exercises
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeRoutines(raw: String): List<WeightRoutine>? =
        try {
            json.decodeFromString(WeightRoutinesPayload.serializer(), raw).routines
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeDayLog(raw: String, date: String): WeightDayLog? =
        try {
            val parsed = json.decodeFromString(WeightDayLog.serializer(), raw)
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

    private fun dailyTag(date: String): String = "erv/weight/$date"
}
