package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRunCompletionTest {

    @Test
    fun active_run_starts_in_pre_start_state() {
        val workout = sampleTwoSegmentWorkout()
        val run = WorkoutActiveRun(
            workoutId = workout.id,
            workoutSnapshot = workout,
        )

        assertNull(run.startedAtEpochSeconds)
        assertFalse(run.isStarted())
        assertTrue(run.copy(startedAtEpochSeconds = 123L).isStarted())
    }

    @Test
    fun activeWorkoutCardioLaunch_requires_cardio_item() {
        val workout = sampleTwoSegmentWorkout()
        val segmentId = workout.segments[0].id
        val itemId = workout.segments[0].items[0].id
        val state = WorkoutLibraryState(
            workouts = listOf(workout),
            activeRun = WorkoutActiveRun(
                workoutId = workout.id,
                workoutSnapshot = workout,
                lastLaunchedSegmentId = segmentId,
                lastLaunchedItemId = itemId,
            ),
        )
        assertNotNull(state.activeWorkoutCardioLaunch())
        assertNull(state.activeWorkoutWeightLaunch())
    }

    @Test
    fun completing_warmup_cardio_advances_to_next_segment() {
        val workout = sampleTwoSegmentWorkout()
        val position = WorkoutRunPosition()
        val next = WorkoutRunEngine.advance(workout, position)
        assertEquals(1, next.segmentIndex)
        assertTrue(WorkoutRunEngine.isWorkoutComplete(workout, next).not())
        assertEquals("Main work", workout.segments[next.segmentIndex].displayTitle())
    }

    @Test
    fun circuit_current_slot_requires_current_round_row_logged() {
        val segment = WorkoutSegment(
            kind = WorkoutSegmentKind.CIRCUIT,
            rounds = 2,
            items = listOf(
                WorkoutItem.Weight(
                    exerciseId = "pushup",
                    prescription = WorkoutWeightPrescription(setCount = 1, targetReps = 10),
                ),
                WorkoutItem.Weight(
                    exerciseId = "plank",
                    prescription = WorkoutWeightPrescription(
                        mode = WorkoutWeightPrescriptionMode.TIME_BASED,
                        durationSeconds = 30,
                    ),
                ),
            ),
        )
        val circuit = buildWorkoutCircuitRun(
            segment = segment,
            workoutId = "workout",
            segmentIndex = 0,
            initialRound = 2,
            initialSlotIndex = 0,
        )!!

        assertFalse(
            circuit.isCurrentSlotLogged(
                setsByExerciseId = mapOf(
                    "pushup" to listOf(
                        WeightSet(reps = 10),
                        WeightSet(reps = 0),
                    ),
                ),
                hiitBlocksByExerciseId = emptyMap(),
            ),
        )
        assertTrue(
            circuit.isCurrentSlotLogged(
                setsByExerciseId = mapOf(
                    "pushup" to listOf(
                        WeightSet(reps = 10),
                        WeightSet(reps = 10),
                    ),
                ),
                hiitBlocksByExerciseId = emptyMap(),
            ),
        )
    }

    private fun sampleTwoSegmentWorkout(): Workout = Workout(
        name = "Two-part",
        segments = listOf(
            WorkoutSegment(
                kind = WorkoutSegmentKind.CARDIO,
                title = "Warm-up",
                items = listOf(
                    WorkoutItem.Cardio(
                        cardio = WorkoutCardioPrescription(activity = "STATIONARY_BIKE"),
                    ),
                ),
            ),
            WorkoutSegment(
                kind = WorkoutSegmentKind.STRAIGHT_SETS,
                title = "Main work",
                items = listOf(WorkoutItem.Weight(exerciseId = "squat")),
            ),
        ),
    )
}
