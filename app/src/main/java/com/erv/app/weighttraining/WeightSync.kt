package com.erv.app.weighttraining

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

    suspend fun publishExercises(
        relayPool: RelayPool,
        signer: EventSigner,
        exercises: List<WeightExercise>
    ): Boolean {
        val payload = WeightExercisesPayload(
            exercises = exercises.map { it.copy(sessionSummaries = emptyList()) }
        )
        val content = json.encodeToString(WeightExercisesPayload.serializer(), payload)
        return publishEvent(relayPool, signer, WEIGHT_EXERCISES_D_TAG, content)
    }

    suspend fun publishRoutines(
        relayPool: RelayPool,
        signer: EventSigner,
        routines: List<WeightRoutine>
    ): Boolean {
        val payload = WeightRoutinesPayload(routines = routines)
        val content = json.encodeToString(WeightRoutinesPayload.serializer(), payload)
        return publishEvent(relayPool, signer, WEIGHT_ROUTINES_D_TAG, content)
    }

    suspend fun publishDayLog(
        relayPool: RelayPool,
        signer: EventSigner,
        log: WeightDayLog
    ): Boolean {
        val content = json.encodeToString(WeightDayLog.serializer(), log)
        return publishEvent(relayPool, signer, dailyTag(log.date), content)
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): WeightLibraryState? = coroutineScope {
        val subId = "erv-weight-${System.currentTimeMillis()}"
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

        WeightLibraryState(
            exercises = exercises,
            routines = routines,
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
            createdAt = System.currentTimeMillis() / 1000,
            kind = 30078,
            tags = listOf(listOf("d", dTag)),
            content = encrypted
        )
        val signed = signer.sign(unsigned)
        return relayPool.publish(signed)
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

    private fun NostrEvent.dTagOrNull(): String? =
        tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1)

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }

    private fun dailyTag(date: String): String = "erv/weight/$date"
}
