package com.erv.app.workouts

import com.erv.app.nostr.LibraryStateMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun fullOutboxEntries_uses_library_d_tag() {
        val entries = WorkoutSync.fullOutboxEntries(
            WorkoutLibraryState(workouts = listOf(Workout(name = "Test"))),
        )
        assertEquals(WORKOUTS_LIBRARY_D_TAG, entries.single().first)
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
