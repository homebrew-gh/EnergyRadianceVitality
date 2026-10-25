package com.erv.app.workouts

import com.erv.app.nostr.LibraryStateMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun fromLatestByTag_skipsSegmentShardedWorkout_untilEverySegmentArrives() {
        val largeNote = "x".repeat(22_000)
        val workout = Workout(
            id = "w1",
            name = "Heavy session",
            segments = listOf(
                WorkoutSegment(kind = WorkoutSegmentKind.COMPOSITE, items = listOf(WorkoutItem.Note(text = largeNote))),
                WorkoutSegment(kind = WorkoutSegmentKind.COMPOSITE, items = listOf(WorkoutItem.Note(text = largeNote))),
            ),
        )
        val entries = WorkoutSync.fullOutboxEntries(WorkoutLibraryState(workouts = listOf(workout))).toMap()
        val segmentTags = entries.keys.filter { it.contains("/segment/") }
        assertEquals(2, segmentTags.size)

        val partial = WorkoutSync.fromLatestByTagFromPlaintextForTest(entries - segmentTags.last())
        assertTrue(partial.workouts.isEmpty())

        val complete = WorkoutSync.fromLatestByTagFromPlaintextForTest(entries)
        assertEquals(2, complete.workoutById("w1")?.segments?.size)
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

    @Test
    fun fullOutboxEntries_afterDeletingLastWorkout_publishesIndexWithDeleteMarker() {
        val state = WorkoutLibraryState(
            libraryUpdatedAtEpochSeconds = 300L,
            deletedWorkouts = mapOf("w1" to 300L),
        )
        val entries = WorkoutSync.fullOutboxEntries(state)
        assertEquals(listOf(WORKOUTS_LIBRARY_D_TAG), entries.map { it.first })
        val master = WorkoutSync.decodeLibraryPayloadForTest(entries.single().second)
        assertEquals(true, master?.sharded)
        assertEquals(emptyList<String>(), master?.workoutIds)
        assertEquals(mapOf("w1" to 300L), master?.deletedWorkouts)
    }

    @Test
    fun fromLatestByTagFromPlaintext_ignoresOlderUnlistedShard_keepsNewerUnlistedShard() {
        val deleted = Workout(id = "gone", name = "Deleted", lastModifiedEpochSeconds = 100L)
        val pending = Workout(id = "new", name = "Index never landed", lastModifiedEpochSeconds = 400L)
        val deletedTag = WorkoutSync.workoutShardTagForTest("gone")
        val pendingTag = WorkoutSync.workoutShardTagForTest("new")
        val shards = WorkoutSync.fullOutboxEntries(WorkoutLibraryState(workouts = listOf(deleted, pending)))
            .toMap()
            .filterKeys { it != WORKOUTS_LIBRARY_D_TAG }
        val state = WorkoutSync.fromLatestByTagFromPlaintextForTest(
            latestByTag = shards + (
                WORKOUTS_LIBRARY_D_TAG to """{"sharded":true,"workoutIds":[],"libraryUpdatedAtEpochSeconds":300}"""
                ),
            createdAtByTag = mapOf(deletedTag to 100L, WORKOUTS_LIBRARY_D_TAG to 300L, pendingTag to 400L),
        )
        assertEquals(listOf("new"), state.workouts.map { it.id })
    }

    @Test
    fun fromLatestByTagFromPlaintext_dropsWorkoutsCoveredByDeleteMarker() {
        val stale = Workout(id = "w1", name = "Deleted elsewhere", lastModifiedEpochSeconds = 100L)
        val entries = WorkoutSync.fullOutboxEntries(WorkoutLibraryState(workouts = listOf(stale))).toMap()
        val state = WorkoutSync.fromLatestByTagFromPlaintextForTest(
            entries + (
                WORKOUTS_LIBRARY_D_TAG to
                    """{"sharded":true,"workoutIds":["w1"],"libraryUpdatedAtEpochSeconds":200,"deletedWorkouts":{"w1":200}}"""
                ),
        )
        assertTrue(state.workouts.isEmpty())
        assertEquals(mapOf("w1" to 200L), state.deletedWorkouts)
    }

    @Test
    fun mergeWorkouts_remoteDeleteMarker_dropsLocalCopy_unlessEditedAfterDelete() {
        val local = WorkoutLibraryState(
            workouts = listOf(
                Workout(id = "w1", name = "Untouched", lastModifiedEpochSeconds = 100L),
                Workout(id = "w2", name = "Edited after delete", lastModifiedEpochSeconds = 500L),
            ),
        )
        val remote = WorkoutLibraryState(
            libraryUpdatedAtEpochSeconds = 300L,
            deletedWorkouts = mapOf("w1" to 300L, "w2" to 300L),
        )
        val merged = LibraryStateMerge.mergeWorkouts(local, remote)
        assertNull(merged.workoutById("w1"))
        assertEquals("Edited after delete", merged.workoutById("w2")?.name)
        assertEquals(mapOf("w1" to 300L, "w2" to 300L), merged.deletedWorkouts)
    }

    @Test
    fun mergeWorkouts_localDeleteMarker_keepsRemoteCopyFromComingBack() {
        val local = WorkoutLibraryState(libraryUpdatedAtEpochSeconds = 300L, deletedWorkouts = mapOf("w1" to 300L))
        val remote = WorkoutLibraryState(
            workouts = listOf(Workout(id = "w1", name = "Still on relay", lastModifiedEpochSeconds = 100L)),
        )
        val merged = LibraryStateMerge.mergeWorkouts(local, remote)
        assertTrue(merged.workouts.isEmpty())
    }

    @Test
    fun deleteOnOneDevice_propagatesToAnotherDeviceThroughRelayIndex() {
        val workout = Workout(id = "w1", name = "Push day", lastModifiedEpochSeconds = 100L)
        val otherDevice = WorkoutLibraryState(workouts = listOf(workout), libraryUpdatedAtEpochSeconds = 100L)
        val shardsOnRelay = WorkoutSync.fullOutboxEntries(otherDevice).toMap()

        val afterDelete = WorkoutLibraryState(
            libraryUpdatedAtEpochSeconds = 300L,
            deletedWorkouts = mapOf("w1" to 300L),
        ).sanitized()
        val relay = shardsOnRelay + WorkoutSync.fullOutboxEntries(afterDelete).toMap()

        val pulled = WorkoutSync.fromLatestByTagFromPlaintextForTest(relay)
        val merged = LibraryStateMerge.mergeWorkouts(otherDevice, pulled)
        assertTrue(merged.workouts.isEmpty())
        assertFalse(WorkoutSync.fullOutboxEntries(merged).any { it.first.startsWith(WORKOUTS_LIBRARY_WORKOUT_PREFIX) })
    }

    @Test
    fun sanitized_dropsDeleteMarkersPastRetention() {
        val now = 10L * WORKOUT_DELETE_MARKER_RETENTION_SECONDS
        val state = WorkoutLibraryState(
            libraryUpdatedAtEpochSeconds = now,
            deletedWorkouts = mapOf(
                "old" to now - WORKOUT_DELETE_MARKER_RETENTION_SECONDS - 1,
                "recent" to now - 60,
            ),
        ).sanitized()
        assertEquals(setOf("recent"), state.deletedWorkouts.keys)
    }
}
