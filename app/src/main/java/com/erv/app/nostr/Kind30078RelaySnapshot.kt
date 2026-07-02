package com.erv.app.nostr

import com.erv.app.cardio.CardioDayLog
import com.erv.app.programs.PROGRAMS_MASTER_D_TAG
import com.erv.app.weighttraining.WeightDayLog
import com.erv.app.workouts.WORKOUTS_LIBRARY_D_TAG
import com.erv.app.workouts.WORKOUTS_LIBRARY_WORKOUT_PREFIX
import android.util.Log
import com.erv.app.workouts.WorkoutSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

internal const val ERV_SYNC_LOG_TAG = "ErvRelaySync"

private const val KIND_30078_FETCH_LIMIT = 2500
private const val KIND_30078_TAG_FETCH_LIMIT = 20
private const val KIND_30078_FILTER_BATCH_SIZE = 6
private const val KIND_30078_LOOKBACK_SECONDS = 400L * 86_400

/** Single-tag masters that must not be dropped when day-log events fill the relay window. */
private val PRIORITY_KIND_30078_MASTER_D_TAGS = listOf(
    WORKOUTS_LIBRARY_D_TAG,
    PROGRAMS_MASTER_D_TAG,
    "erv/weight/routines",
    "erv/stretching/routines",
    "erv/cardio/routines",
    "erv/equipment",
    "erv/training-profile",
)

/**
 * Fetches the latest kind-30078 event per d-tag for one author.
 *
 * Uses a broad subscription plus targeted `#d` fetches for workout/planner masters and per-workout
 * shards so web-published libraries are not lost behind thousands of training day logs.
 */
suspend fun fetchLatestKind30078ByDTag(
    relayPool: RelayPool,
    pubkeyHex: String,
    timeoutMs: Long = 8000,
    limit: Int = KIND_30078_FETCH_LIMIT,
    signer: EventSigner? = null,
): Map<String, NostrEvent> = coroutineScope {
    val since = fetchSinceEpochSeconds()
    val broadEvents = collectKind30078Events(
        relayPool = relayPool,
        pubkeyHex = pubkeyHex,
        subscriptionId = "erv-kind30078-broad-${System.currentTimeMillis()}",
        timeoutMs = timeoutMs,
        filters = arrayOf(
            NostrFilter(
                kinds = listOf(30078),
                authors = listOf(pubkeyHex),
                since = since,
                limit = limit,
            ),
        ),
    )
    var merged = pickLatestDecryptableByDTag(broadEvents, signer)
    Log.i(
        ERV_SYNC_LOG_TAG,
        "fetch: pubkey=${pubkeyHex.take(8)}… relayStates=${relayPool.relayStates.value} " +
            "broadEvents=${broadEvents.size} broadDecryptedTags=${merged.size} signer=${signer != null}",
    )

    val masterSupplement = collectKind30078Events(
        relayPool = relayPool,
        pubkeyHex = pubkeyHex,
        subscriptionId = "erv-kind30078-masters-${System.currentTimeMillis()}",
        timeoutMs = timeoutMs / 2,
        filters = PRIORITY_KIND_30078_MASTER_D_TAGS.map { dTag ->
            NostrFilter(
                kinds = listOf(30078),
                authors = listOf(pubkeyHex),
                dTags = listOf(dTag),
                since = since,
                limit = KIND_30078_TAG_FETCH_LIMIT,
            )
        }.toTypedArray(),
    )
    merged = mergeLatestByDTag(merged, pickLatestDecryptableByDTag(masterSupplement, signer))
    Log.i(
        ERV_SYNC_LOG_TAG,
        "fetch: masterSupplementEvents=${masterSupplement.size} afterMasterTags=${merged.size} " +
            "hasWorkoutLibraryTag=${merged.containsKey(WORKOUTS_LIBRARY_D_TAG)}",
    )

    if (signer != null) {
        merged = mergeLatestByDTag(
            merged,
            fetchWorkoutLibraryShardTags(relayPool, pubkeyHex, merged, signer, since, timeoutMs),
        )
    }

    val workoutShardTags = merged.keys.count {
        it.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX) && !it.contains("/segment/")
    }
    Log.i(
        ERV_SYNC_LOG_TAG,
        "fetch: finalTags=${merged.size} workoutHeadShardTags=$workoutShardTags " +
            "allErvWorkoutTags=${merged.keys.filter { it.startsWith("erv/workouts") }}",
    )

    merged
}

private suspend fun fetchWorkoutLibraryShardTags(
    relayPool: RelayPool,
    pubkeyHex: String,
    merged: Map<String, NostrEvent>,
    signer: EventSigner,
    since: Long,
    timeoutMs: Long,
): Map<String, NostrEvent> {
    val libraryPlain = merged[WORKOUTS_LIBRARY_D_TAG]
        ?.decryptKind30078PayloadOrNull(signer)
        .orEmpty()
    val indexIds = WorkoutSync.workoutIdsFromLibraryMasterPlaintext(libraryPlain)

    val prefixIds = merged.keys
        .filter { it.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX) && !it.contains("/segment/") }
        .map { it.removePrefix(WORKOUTS_LIBRARY_WORKOUT_PREFIX) }

    val workoutIds = (indexIds + prefixIds).distinct()
    Log.i(
        ERV_SYNC_LOG_TAG,
        "shardFetch: libraryPlainLen=${libraryPlain.length} indexIds=${indexIds.size} " +
            "prefixIds=${prefixIds.size} workoutIdsToFetch=${workoutIds.size}",
    )
    if (workoutIds.isEmpty()) return emptyMap()

    val headTags = workoutIds.map { "$WORKOUTS_LIBRARY_WORKOUT_PREFIX$it" }
    val headEvents = collectKind30078EventsForDTags(
        relayPool = relayPool,
        pubkeyHex = pubkeyHex,
        subscriptionPrefix = "erv-kind30078-workouts",
        timeoutMs = timeoutMs,
        since = since,
        dTags = headTags,
    )
    val headByTag = pickLatestDecryptableByDTag(headEvents, signer)
    Log.i(
        ERV_SYNC_LOG_TAG,
        "shardFetch: requestedHeads=${headTags.size} headEventsReceived=${headEvents.size} " +
            "headDecryptedTags=${headByTag.size}",
    )

    val segmentTags = buildList {
        for ((dTag, event) in headByTag) {
            val plain = event.decryptKind30078PayloadOrNull(signer) ?: continue
            val segmentIds = WorkoutSync.segmentShardIdsFromWorkoutHeadPlaintext(plain)
            val workoutId = dTag.removePrefix(WORKOUTS_LIBRARY_WORKOUT_PREFIX)
            for (segmentId in segmentIds) {
                add("$WORKOUTS_LIBRARY_WORKOUT_PREFIX$workoutId/segment/$segmentId")
            }
        }
    }.distinct()

    if (segmentTags.isEmpty()) return headByTag

    val segmentEvents = collectKind30078EventsForDTags(
        relayPool = relayPool,
        pubkeyHex = pubkeyHex,
        subscriptionPrefix = "erv-kind30078-workout-segments",
        timeoutMs = timeoutMs,
        since = since,
        dTags = segmentTags,
    )
    return mergeLatestByDTag(headByTag, pickLatestDecryptableByDTag(segmentEvents, signer))
}

private suspend fun collectKind30078EventsForDTags(
    relayPool: RelayPool,
    pubkeyHex: String,
    subscriptionPrefix: String,
    timeoutMs: Long,
    since: Long,
    dTags: List<String>,
): List<NostrEvent> {
    if (dTags.isEmpty()) return emptyList()
    val perBatchTimeout = (timeoutMs / dTags.chunked(KIND_30078_FILTER_BATCH_SIZE).size.coerceAtLeast(1))
        .coerceIn(2_000L, timeoutMs)
    return dTags.chunked(KIND_30078_FILTER_BATCH_SIZE).flatMapIndexed { batchIndex, batch ->
        collectKind30078Events(
            relayPool = relayPool,
            pubkeyHex = pubkeyHex,
            subscriptionId = "$subscriptionPrefix-$batchIndex-${System.currentTimeMillis()}",
            timeoutMs = perBatchTimeout,
            filters = batch.map { dTag ->
                NostrFilter(
                    kinds = listOf(30078),
                    authors = listOf(pubkeyHex),
                    dTags = listOf(dTag),
                    since = since,
                    limit = KIND_30078_TAG_FETCH_LIMIT,
                )
            }.toTypedArray(),
        )
    }
}

private suspend fun collectKind30078Events(
    relayPool: RelayPool,
    pubkeyHex: String,
    subscriptionId: String,
    timeoutMs: Long,
    filters: Array<NostrFilter>,
): List<NostrEvent> = coroutineScope {
    val events = mutableListOf<NostrEvent>()
    // relayPool.events is a hot SharedFlow with replay = 0. Start collecting and wait until this
    // collector is actually registered (onSubscription) BEFORE sending the REQ, otherwise events
    // that stream back immediately — common for small targeted #d fetches like workout shards —
    // are emitted into the void and lost.
    val collecting = CompletableDeferred<Unit>()
    val job = launch {
        relayPool.events
            .onSubscription { collecting.complete(Unit) }
            .collect { (id, ev) ->
                if (id == subscriptionId && ev.kind == 30078) events.add(ev)
            }
    }
    collecting.await()
    relayPool.subscribe(subscriptionId, *filters)
    delay(timeoutMs)
    job.cancel()
    relayPool.unsubscribe(subscriptionId)
    events
}

private fun fetchSinceEpochSeconds(): Long {
    val now = System.currentTimeMillis() / 1000
    return (now - KIND_30078_LOOKBACK_SECONDS).coerceAtLeast(0)
}

private suspend fun pickLatestDecryptableByDTag(
    events: List<NostrEvent>,
    signer: EventSigner?,
): Map<String, NostrEvent> {
    val grouped = events
        .sortedBy { it.createdAt }
        .groupBy { it.dTagOrNull() ?: "unknown" }

    return grouped.mapNotNull { (dTag, items) ->
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

private fun mergeLatestByDTag(
    base: Map<String, NostrEvent>,
    supplement: Map<String, NostrEvent>,
): Map<String, NostrEvent> {
    if (supplement.isEmpty()) return base
    val merged = base.toMutableMap()
    for ((dTag, event) in supplement) {
        val existing = merged[dTag]
        if (existing == null || event.createdAt >= existing.createdAt) {
            merged[dTag] = event
        }
    }
    return merged
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
