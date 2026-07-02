package com.erv.app.workouts

import com.erv.app.nostr.LibraryStateMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSyncTest {

    @Test
    fun encodeDecode_library_payload_round_trip() {
        val state = WorkoutLibraryState(
            workouts = listOf(
                Workout(
                    id = "w1",
                    name = "Push day",
                    lastModifiedEpochSeconds = 100L,
                ),
            ),
            libraryUpdatedAtEpochSeconds = 100L,
        )
        val encoded = WorkoutSync.encodeLibraryPayloadForTest(state)
        val decoded = WorkoutSync.decodeLibraryPayloadForTest(encoded)
        assertEquals(1, decoded?.workouts?.size)
        assertEquals("Push day", decoded?.workouts?.first()?.name)
        assertEquals(100L, decoded?.libraryUpdatedAtEpochSeconds)
    }

    @Test
    fun fullOutboxEntries_emptyLibrary_producesNoEntries_toAvoidClobberingRelay() {
        val entries = WorkoutSync.fullOutboxEntries(WorkoutLibraryState())
        assertTrue(entries.isEmpty())
    }

    @Test
    fun clearOutboxEntries_stillPublishesEmptyMaster_forIntentionalDeletion() {
        val entries = WorkoutSync.clearOutboxEntries()
        assertEquals(1, entries.size)
        assertEquals(WORKOUTS_LIBRARY_D_TAG, entries.first().first)
    }

    @Test
    fun fullOutboxEntries_uses_per_workout_shards() {
        val entries = WorkoutSync.fullOutboxEntries(
            WorkoutLibraryState(workouts = listOf(Workout(name = "Test"))),
        )
        assertEquals(2, entries.size)
        assertEquals(WORKOUTS_LIBRARY_D_TAG, entries[0].first)
        assertTrue(entries[1].first.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX))
    }

    @Test
    fun fullOutboxEntries_segment_shards_when_single_workout_exceeds_relay_limit() {
        val largeNote = "x".repeat(22_000)
        val workout = Workout(
            id = "w1",
            name = "Heavy session",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.COMPOSITE,
                    items = listOf(WorkoutItem.Note(text = largeNote)),
                ),
                WorkoutSegment(
                    kind = WorkoutSegmentKind.COMPOSITE,
                    items = listOf(WorkoutItem.Note(text = largeNote)),
                ),
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(
                            exerciseId = "erv-weight-exercise-bench-v1",
                        ),
                    ),
                ),
            ),
        )
        val entries = WorkoutSync.fullOutboxEntries(WorkoutLibraryState(workouts = listOf(workout)))
        assertTrue(entries.size >= 4)
        assertEquals(
            WorkoutSync.workoutShardTagForTest("w1"),
            entries[1].first,
        )
        assertTrue(
            entries.any { (tag, _) ->
                tag.startsWith("${WorkoutSync.workoutShardTagForTest("w1")}/segment/")
            },
        )
    }

    @Test
    fun mergeWorkouts_prefers_newer_lastModified() {
        val local = WorkoutLibraryState(
            workouts = listOf(
                Workout(id = "w1", name = "Local", lastModifiedEpochSeconds = 200L),
            ),
        )
        val remote = WorkoutLibraryState(
            workouts = listOf(
                Workout(id = "w1", name = "Remote", lastModifiedEpochSeconds = 100L),
                Workout(id = "w2", name = "Other", lastModifiedEpochSeconds = 50L),
            ),
        )
        val merged = LibraryStateMerge.mergeWorkouts(local, remote)
        assertEquals("Local", merged.workoutById("w1")?.name)
        assertEquals("Other", merged.workoutById("w2")?.name)
    }

    @Test
    fun decodeLibraryPayload_shardedIndex() {
        val json = """
            {
              "sharded": true,
              "workoutIds": ["w1", "w2"],
              "libraryUpdatedAtEpochSeconds": 200
            }
        """.trimIndent()
        val decoded = WorkoutSync.decodeLibraryPayloadForTest(json)
        assertEquals(true, decoded?.sharded)
        assertEquals(listOf("w1", "w2"), decoded?.workoutIds)
        assertEquals(listOf("w1", "w2"), WorkoutSync.workoutIdsFromLibraryMasterPlaintext(json))
    }

    @Test
    fun decodeWorkoutShard_webWrappedShape() {
        val json = """
            {
              "workout": {
                "id": "w1",
                "name": "Full session",
                "segments": [{
                  "kind": "composite",
                  "title": "Warm-up",
                  "items": [{ "type": "note", "text": "Easy bike" }]
                }, {
                  "kind": "superset",
                  "title": "Main",
                  "items": [{
                    "type": "weight",
                    "exerciseId": "erv-weight-exercise-bench-v1",
                    "prescription": { "mode": "straight", "setCount": 3 }
                  }]
                }]
              }
            }
        """.trimIndent()
        val decoded = WorkoutSync.decodeWorkoutShardWorkoutForTest(json)
        assertEquals("Full session", decoded?.name)
        assertEquals(2, decoded?.segments?.size)
        assertEquals(emptyList<String>(), WorkoutSync.segmentShardIdsFromWorkoutHeadPlaintext(json))
    }

    @Test
    fun fromLatestByTagFromPlaintext_mergesShardedIndexAndWorkoutShards() {
        val workoutId = "550e8400-e29b-41d4-a716-446655440000"
        val headTag = "${WORKOUTS_LIBRARY_WORKOUT_PREFIX}$workoutId"
        val headJson = """
            {
              "workout": {
                "id": "$workoutId",
                "name": "Web push day",
                "segments": [{
                  "kind": "straight_sets",
                  "items": [{
                    "type": "weight",
                    "exerciseId": "erv-weight-exercise-bench-v1",
                    "prescription": { "mode": "straight", "setCount": 3 }
                  }]
                }],
                "lastModifiedEpochSeconds": 999
              }
            }
        """.trimIndent()
        val state = WorkoutSync.fromLatestByTagFromPlaintextForTest(
            mapOf(
                WORKOUTS_LIBRARY_D_TAG to """
                    {"sharded":true,"workoutIds":["$workoutId"],"libraryUpdatedAtEpochSeconds":999}
                """.trimIndent(),
                headTag to headJson,
            ),
        )
        assertEquals(1, state.workouts.size)
        assertEquals("Web push day", state.workouts.first().name)
    }

    @Test
    fun decodeLibraryPayload_webStart9Shape() {
        val json = """
            {
              "workouts": [{
                "id": "550e8400-e29b-41d4-a716-446655440000",
                "name": "Morning flow",
                "segments": [{
                  "kind": "mobility",
                  "title": "Cooldown",
                  "items": [{
                    "type": "mobility",
                    "id": "item-1",
                    "mobility": { "catalogId": "builtin_hamstring_stretch", "holdSeconds": 30 }
                  }]
                }, {
                  "kind": "straight_sets",
                  "items": [{
                    "type": "weight",
                    "id": "item-2",
                    "exerciseId": "erv-weight-exercise-bench-v1",
                    "prescription": {
                      "mode": "time_based",
                      "setCount": 1,
                      "durationSeconds": 45,
                      "sets": [{ "durationSeconds": 45, "targetDurationSeconds": 45 }]
                    }
                  }]
                }],
                "sourceLabel": "Start9",
                "lastModifiedEpochSeconds": 1718888888,
                "createdAtEpochSeconds": 1718888888
              }],
              "libraryUpdatedAtEpochSeconds": 1718888888
            }
        """.trimIndent()
        val decoded = WorkoutSync.decodeLibraryPayloadForTest(json)
        assertEquals(1, decoded?.workouts?.size)
        assertEquals("Morning flow", decoded?.workouts?.first()?.name)
        assertEquals(2, decoded?.workouts?.first()?.segments?.size)
    }
}
