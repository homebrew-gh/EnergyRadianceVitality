package com.erv.app.nostr

import com.erv.app.cardio.CardioDayLog
import com.erv.app.weighttraining.WeightDayLog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Fetches the latest kind-30078 event per d-tag for one author using a single relay subscription.
 * This is intentionally shared across sections to avoid a burst of overlapping REQs at startup.
 *
 * When [signer] is provided, skips NIP-33 tombstones (blank content or NIP-44 payload decrypting
 * to empty) and prefers the newest decryptable event per d-tag — matches web server merge logic.
 */
suspend fun fetchLatestKind30078ByDTag(
    relayPool: RelayPool,
    pubkeyHex: String,
    timeoutMs: Long = 8000,
    limit: Int = 2500,
    signer: EventSigner? = null,
): Map<String, NostrEvent> = coroutineScope {
    val subId = "erv-kind30078-${System.currentTimeMillis()}"
    relayPool.subscribe(
        subId,
        NostrFilter(
            kinds = listOf(30078),
            authors = listOf(pubkeyHex),
            limit = limit,
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

    val grouped = events
        .sortedBy { it.createdAt }
        .groupBy { it.dTagOrNull() ?: "unknown" }

    grouped.mapNotNull { (dTag, items) ->
        if (dTag == "unknown") return@mapNotNull null
        if (signer != null) {
            val picked = items.asReversed().firstOrNull { ev ->
                ev.decryptKind30078TrainingDayPayloadOrNull(signer, dTag) != null
            } ?: return@mapNotNull null
            dTag to picked
        } else {
            dTag to items.last()
        }
    }.toMap()
}

suspend fun NostrEvent.decryptKind30078PayloadOrNull(signer: EventSigner): String? {
    if (content.isBlank()) return null
    return try {
        val plain = signer.decryptFromSelf(content)
        plain.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/** Decrypt and accept only when payload has sessions/workouts (skip cleared day logs on relay). */
suspend fun NostrEvent.decryptKind30078TrainingDayPayloadOrNull(
    signer: EventSigner,
    dTag: String,
): String? {
    val plain = decryptKind30078PayloadOrNull(signer) ?: return null
    return plain.takeIf { trainingDayLogHasContent(dTag, plain) }
}

private val trainingDayLogJson = Json { ignoreUnknownKeys = true }

internal fun trainingDayLogHasContent(dTag: String, plain: String): Boolean {
    if (!isTrainingDayLogDTag(dTag)) return true
    return try {
        when {
            dTag.startsWith("erv/weight/") ->
                trainingDayLogJson.decodeFromString(WeightDayLog.serializer(), plain).workouts.isNotEmpty()
            else ->
                trainingDayLogJson.decodeFromString(CardioDayLog.serializer(), plain).sessions.isNotEmpty()
        }
    } catch (_: Exception) {
        false
    }
}

/** Whether a local outbox plaintext should be uploaded and counted in relay coverage. */
internal fun localKind30078PayloadHasRelayContent(dTag: String, plain: String): Boolean =
    trainingDayLogHasContent(dTag, plain)

internal fun isTrainingDayLogDTag(dTag: String): Boolean {
    if (dTag == "erv/weight/exercises" || dTag == "erv/weight/routines") return false
    if (dTag == "erv/cardio/routines") return false
    val isoDate = Regex("""^\d{4}-\d{2}-\d{2}$""")
    val splitSession = Regex("""^\d{4}-\d{2}-\d{2}/session/.+""")
    if (dTag.startsWith("erv/weight/")) {
        val suffix = dTag.removePrefix("erv/weight/")
        return isoDate.matches(suffix) || splitSession.matches(suffix)
    }
    if (dTag.startsWith("erv/cardio/")) {
        val suffix = dTag.removePrefix("erv/cardio/")
        return isoDate.matches(suffix) || splitSession.matches(suffix)
    }
    return false
}

fun NostrEvent.dTagOrNull(): String? =
    tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1)
