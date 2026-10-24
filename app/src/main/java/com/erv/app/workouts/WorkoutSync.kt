package com.erv.app.workouts

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

const val WORKOUTS_LIBRARY_D_TAG = "erv/workouts/library"
const val WORKOUTS_LIBRARY_WORKOUT_PREFIX = "erv/workouts/library/workout/"
private const val WORKOUT_SEGMENT_SHARD_MARKER = "/segment/"

/** NIP-44 plaintext cap is 65535 bytes; stay under for encryption + relay headroom. */
private const val MAX_WORKOUT_LIBRARY_PLAINTEXT_BYTES = 40_000

@Serializable
internal data class WorkoutLibraryPayload(
    val workouts: List<Workout> = emptyList(),
    val libraryUpdatedAtEpochSeconds: Long = 0L,
    val sharded: Boolean = false,
    val workoutIds: List<String> = emptyList(),
    /** Workout id to the epoch second it was deleted; readers drop workouts not edited since. */
    val deletedWorkouts: Map<String, Long> = emptyMap(),
)

@Serializable
internal data class WorkoutLibraryShardPayload(
    val workout: Workout,
    val segmentShards: Boolean = false,
    val segmentIds: List<String> = emptyList(),
)

@Serializable
internal data class WorkoutSegmentShardPayload(
    val segment: WorkoutSegment,
)

object WorkoutSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    suspend fun publishLibrary(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: WorkoutLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val outbox = RelayPublishOutbox.get(appContext)
        outbox.enqueueAllDigestsAware(appContext, fullOutboxEntries(state))
        val result = outbox.kickDrain(relayPool, signer, dataRelayUrls)
        return result.publishedFail == 0 && result.remaining == 0
    }

    suspend fun publishLibraryIfSignedIn(
        appContext: Context,
        relayPool: RelayPool?,
        signer: EventSigner?,
        state: WorkoutLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        if (relayPool == null || signer == null || dataRelayUrls.isEmpty()) return false
        return publishLibrary(appContext, relayPool, signer, state, dataRelayUrls)
    }

    fun fullOutboxEntries(state: WorkoutLibraryState): List<Pair<String, String>> {
        val payload = toPayload(state)
        // Never auto-publish a blank library master: kind 30078 is last-write-wins, so an empty
        // local library (e.g. right after a reinstall, before the relay pull completes) would
        // otherwise clobber a non-empty library published by the web companion. Deleting the last
        // workout leaves a delete marker, so that index (no ids, markers only) still publishes.
        if (payload.workouts.isEmpty() && payload.deletedWorkouts.isEmpty()) {
            return emptyList()
        }

        val pairs = mutableListOf<Pair<String, String>>()
        pairs += WORKOUTS_LIBRARY_D_TAG to json.encodeToString(
            WorkoutLibraryPayload.serializer(),
            WorkoutLibraryPayload(
                workouts = emptyList(),
                libraryUpdatedAtEpochSeconds = payload.libraryUpdatedAtEpochSeconds,
                sharded = true,
                workoutIds = payload.workouts.map { it.id },
                deletedWorkouts = payload.deletedWorkouts,
            ),
        )
        for (workout in payload.workouts) {
            pairs += outboxEntriesForWorkout(workout)
        }
        return pairs
    }

    fun clearOutboxEntries(): List<Pair<String, String>> =
        listOf(
            WORKOUTS_LIBRARY_D_TAG to json.encodeToString(
                WorkoutLibraryPayload.serializer(),
                WorkoutLibraryPayload(),
            ),
        )

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): WorkoutLibraryState {
        val records = latestByTag
            .filterKeys { it == WORKOUTS_LIBRARY_D_TAG || it.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX) }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                dTag to LibraryRecord(plaintext = raw, createdAt = event.createdAt)
            }
            .toMap()
        val state = assembleLibraryState(records)
        android.util.Log.i(
            "ErvRelaySync",
            "WorkoutSync.fromLatestByTag: masterRawLen=${records[WORKOUTS_LIBRARY_D_TAG]?.plaintext?.length ?: -1} " +
                "finalWorkouts=${state.workouts.size} deleteMarkers=${state.deletedWorkouts.size} " +
                "workoutTagsInMap=${latestByTag.keys.count { it.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX) }}",
        )
        return state
    }

    fun workoutIdsFromLibraryMasterPlaintext(raw: String): List<String> {
        val master = decodeLibraryPayload(raw) ?: return emptyList()
        if (master.sharded) return master.workoutIds
        return master.workouts.map { it.id }
    }

    fun segmentShardIdsFromWorkoutHeadPlaintext(raw: String): List<String> {
        val shard = decodeWorkoutShard(raw) ?: return emptyList()
        if (!shard.segmentShards) return emptyList()
        return shard.segmentIds
    }

    internal fun encodeLibraryPayloadForTest(state: WorkoutLibraryState): String =
        json.encodeToString(WorkoutLibraryPayload.serializer(), toPayload(state))

    internal fun decodeLibraryPayloadForTest(raw: String): WorkoutLibraryPayload? =
        decodeLibraryPayload(raw)

    internal fun decodeWorkoutShardWorkoutForTest(raw: String): Workout? =
        decodeWorkoutShard(raw)?.workout

    internal fun workoutShardTagForTest(workoutId: String): String = workoutShardTag(workoutId)

    internal fun workoutSegmentShardTagForTest(workoutId: String, segmentId: String): String =
        workoutSegmentShardTag(workoutId, segmentId)

    private fun outboxEntriesForWorkout(workout: Workout): List<Pair<String, String>> {
        val fullPayload = workoutShardPlaintext(workout)
        if (plaintextUtf8ByteLength(fullPayload) <= MAX_WORKOUT_LIBRARY_PLAINTEXT_BYTES) {
            return listOf(workoutShardTag(workout.id) to fullPayload)
        }

        val segmentIds = workout.segments.mapIndexed { index, segment ->
            segment.id.takeIf { it.isNotBlank() } ?: "segment-$index"
        }
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += workoutShardTag(workout.id) to json.encodeToString(
            WorkoutLibraryShardPayload.serializer(),
            WorkoutLibraryShardPayload(
                workout = workout.copy(segments = emptyList()),
                segmentShards = true,
                segmentIds = segmentIds,
            ),
        )
        for ((index, segment) in workout.segments.withIndex()) {
            val segmentId = segmentIds[index]
            val segmentPayload = json.encodeToString(
                WorkoutSegmentShardPayload.serializer(),
                WorkoutSegmentShardPayload(segment.copy(id = segmentId)),
            )
            val bytes = plaintextUtf8ByteLength(segmentPayload)
            require(bytes <= MAX_WORKOUT_LIBRARY_PLAINTEXT_BYTES) {
                "Segment \"${segment.title ?: segment.kind}\" in \"${workout.name}\" is too large to sync ($bytes bytes). " +
                    "Shorten coach notes in that segment."
            }
            pairs += workoutSegmentShardTag(workout.id, segmentId) to segmentPayload
        }
        return pairs
    }

    private fun workoutShardTag(workoutId: String): String =
        "$WORKOUTS_LIBRARY_WORKOUT_PREFIX$workoutId"

    private fun workoutSegmentShardTag(workoutId: String, segmentId: String): String =
        "$WORKOUTS_LIBRARY_WORKOUT_PREFIX$workoutId$WORKOUT_SEGMENT_SHARD_MARKER$segmentId"

    private fun workoutShardPlaintext(workout: Workout): String =
        json.encodeToString(
            WorkoutLibraryShardPayload.serializer(),
            WorkoutLibraryShardPayload(workout),
        )

    /** One decrypted relay row; [createdAt] is the event's `created_at` when known. */
    private data class LibraryRecord(val plaintext: String, val createdAt: Long? = null)

    /**
     * Rebuilds the library from decrypted rows, treating the index as authoritative like the web
     * companion: listed ids come first, in index order. A head shard missing from the index still
     * counts when its event is newer than the index (the index update listing it never landed);
     * an older unlisted shard is a leftover from a delete. Delete markers then drop any workout not
     * edited since it was deleted.
     */
    private fun assembleLibraryState(records: Map<String, LibraryRecord>): WorkoutLibraryState {
        val masterRecord = records[WORKOUTS_LIBRARY_D_TAG]
        val master = masterRecord?.plaintext?.let(::decodeLibraryPayload)

        val segmentByWorkoutAndId = records
            .filterKeys { isWorkoutSegmentShardTag(it) }
            .mapNotNull { (dTag, record) ->
                val ref = parseWorkoutSegmentShardTag(dTag) ?: return@mapNotNull null
                val segment = decodeWorkoutSegmentShard(record.plaintext)?.segment ?: return@mapNotNull null
                ref to segment
            }
            .groupBy({ it.first.workoutId }) { it.first.segmentId to it.second }
        val shardCreatedAt = mutableMapOf<String, Long?>()
        val shardById = records
            .filterKeys { isWorkoutHeadShardTag(it) }
            .mapNotNull { (_, record) ->
                assembleWorkoutFromShard(record.plaintext, segmentByWorkoutAndId)
                    ?.also { shardCreatedAt[it.id] = record.createdAt }
            }
            .associateBy { it.id }

        val monolithic = if (master != null && !master.sharded) master.workouts else emptyList()
        val workouts = when {
            monolithic.isNotEmpty() -> monolithic
            master == null -> shardById.keys.sorted().mapNotNull { shardById[it] }
            else -> {
                val listed = if (master.sharded) master.workoutIds else emptyList()
                val listedSet = listed.toSet()
                val indexCreatedAt = masterRecord.createdAt
                val unlistedNewer = if (indexCreatedAt == null) {
                    emptyList()
                } else {
                    shardById.values
                        .filter { it.id !in listedSet && (shardCreatedAt[it.id] ?: 0L) > indexCreatedAt }
                        .sortedBy { it.name.lowercase() }
                }
                listed.mapNotNull { shardById[it] } + unlistedNewer
            }
        }
        val deletedWorkouts = master?.deletedWorkouts.orEmpty()
        return WorkoutLibraryState(
            workouts = workouts.withoutDeletedWorkouts(deletedWorkouts),
            activeRun = null,
            libraryUpdatedAtEpochSeconds = master?.libraryUpdatedAtEpochSeconds ?: 0L,
            deletedWorkouts = deletedWorkouts,
        ).sanitized()
    }

    internal fun fromLatestByTagFromPlaintextForTest(
        latestByTag: Map<String, String>,
        createdAtByTag: Map<String, Long> = emptyMap(),
    ): WorkoutLibraryState =
        assembleLibraryState(
            latestByTag.mapValues { (dTag, raw) -> LibraryRecord(raw, createdAtByTag[dTag]) },
        )

    private fun assembleWorkoutFromShard(
        raw: String,
        segmentByWorkoutAndId: Map<String, List<Pair<String, WorkoutSegment>>>,
    ): Workout? {
        val shard = decodeWorkoutShard(raw) ?: return null
        val workout = shard.workout
        if (!shard.segmentShards) return workout

        val segmentsForWorkout = segmentByWorkoutAndId[workout.id].orEmpty().toMap()
        val orderedIds = shard.segmentIds.ifEmpty { segmentsForWorkout.keys.sorted() }
        // A recent-window or live update can carry the head before (or without) every segment.
        // Merging that would replace the local workout with a newer but truncated copy.
        if (orderedIds.any { it !in segmentsForWorkout }) return null
        val segments = orderedIds.mapNotNull { segmentsForWorkout[it] }
        return workout.copy(segments = segments)
    }

    private fun isWorkoutHeadShardTag(dTag: String): Boolean {
        if (!dTag.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX)) return false
        val suffix = dTag.removePrefix(WORKOUTS_LIBRARY_WORKOUT_PREFIX)
        return suffix.isNotEmpty() && WORKOUT_SEGMENT_SHARD_MARKER !in suffix
    }

    private fun isWorkoutSegmentShardTag(dTag: String): Boolean =
        parseWorkoutSegmentShardTag(dTag) != null

    private fun parseWorkoutSegmentShardTag(dTag: String): WorkoutSegmentShardRef? {
        if (!dTag.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX)) return null
        val suffix = dTag.removePrefix(WORKOUTS_LIBRARY_WORKOUT_PREFIX)
        if (WORKOUT_SEGMENT_SHARD_MARKER !in suffix) return null
        val parts = suffix.split(WORKOUT_SEGMENT_SHARD_MARKER, limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return WorkoutSegmentShardRef(workoutId = parts[0], segmentId = parts[1])
    }

    private data class WorkoutSegmentShardRef(
        val workoutId: String,
        val segmentId: String,
    )

    private fun toPayload(state: WorkoutLibraryState): WorkoutLibraryPayload {
        val sanitized = state.sanitized()
        return WorkoutLibraryPayload(
            workouts = sanitized.workouts,
            libraryUpdatedAtEpochSeconds = sanitized.libraryUpdatedAtEpochSeconds,
            deletedWorkouts = sanitized.deletedWorkouts,
        )
    }

    private fun plaintextUtf8ByteLength(text: String): Int =
        text.toByteArray(StandardCharsets.UTF_8).size

    private fun decodeLibraryPayload(raw: String): WorkoutLibraryPayload? =
        try {
            json.decodeFromString(WorkoutLibraryPayload.serializer(), normalizeRelayWorkoutJson(raw))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeWorkoutShard(raw: String): WorkoutLibraryShardPayload? {
        val normalized = normalizeRelayWorkoutJson(raw)
        try {
            return json.decodeFromString(WorkoutLibraryShardPayload.serializer(), normalized)
        } catch (_: SerializationException) {
        } catch (_: IllegalArgumentException) {
        }
        return try {
            val workout = json.decodeFromString(Workout.serializer(), normalized)
            WorkoutLibraryShardPayload(workout = workout)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun decodeWorkoutSegmentShard(raw: String): WorkoutSegmentShardPayload? =
        try {
            json.decodeFromString(WorkoutSegmentShardPayload.serializer(), normalizeRelayWorkoutJson(raw))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /** Normalize web/Android relay JSON before decode (null collections, etc.). */
    private fun normalizeRelayWorkoutJson(raw: String): String =
        raw
            .replace("\"items\":null", "\"items\":[]")
            .replace("\"segments\":null", "\"segments\":[]")
            .replace("\"workoutIds\":null", "\"workoutIds\":[]")
            .replace("\"deletedWorkouts\":null", "\"deletedWorkouts\":{}")
            .replace("\"sets\":null", "\"sets\":[]")

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }
}
