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
    fun sanitized_drops_active_run_for_missing_workout() {
        val state = WorkoutLibraryState(
            workouts = emptyList(),
            activeRun = WorkoutActiveRun(
                workoutId = "missing",
                workoutSnapshot = Workout(id = "missing", name = "Gone"),
            ),
        )
        assertNull(state.sanitized().activeRun)
    }
}
