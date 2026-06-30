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

    @Test
    fun consecutive_weight_run_batches_a_plain_section() {
        val workout = sampleBatchWorkout()
        val run = WorkoutRunEngine.consecutiveWeightItemRun(workout, WorkoutRunPosition())
        assertEquals(3, run.size)
    }

    @Test
    fun consecutive_weight_run_is_empty_for_circuit_segment() {
        val workout = Workout(
            name = "circuit",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.CIRCUIT,
                    rounds = 2,
                    items = listOf(WorkoutItem.Weight(exerciseId = "a")),
                ),
            ),
        )
        assertTrue(WorkoutRunEngine.consecutiveWeightItemRun(workout, WorkoutRunPosition()).isEmpty())
    }

    @Test
    fun consecutive_weight_run_stops_at_non_weight_item() {
        val workout = Workout(
            name = "mixed",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(exerciseId = "a"),
                        WorkoutItem.Weight(exerciseId = "b"),
                        WorkoutItem.Cardio(cardio = WorkoutCardioPrescription(activity = "RUN")),
                        WorkoutItem.Weight(exerciseId = "c"),
                    ),
                ),
            ),
        )
        assertEquals(2, WorkoutRunEngine.consecutiveWeightItemRun(workout, WorkoutRunPosition()).size)
    }

    @Test
    fun advanceBy_skips_whole_weight_batch_to_next_segment() {
        val workout = sampleBatchWorkout()
        val next = WorkoutRunEngine.advanceBy(workout, WorkoutRunPosition(), 3)
        assertEquals(1, next.segmentIndex)
        assertEquals(0, next.itemIndex)
    }

    @Test
    fun batched_section_completion_lands_on_next_section_and_is_not_final() {
        val workout = sampleBatchWorkout()
        val run = WorkoutActiveRun(
            workoutId = workout.id,
            workoutSnapshot = workout,
            position = WorkoutRunPosition(),
            lastLaunchedSegmentId = workout.segments[0].id,
            lastLaunchedItemId = workout.segments[0].items[0].id,
            lastLaunchedItemIds = workout.segments[0].items.map { it.id },
        )
        val after = run.positionAfterLaunchedSection()
        assertEquals(1, after.segmentIndex)
        assertFalse(run.isFinalLoggableStep())
        // The next step (cardio) is silo-backed, so the storyboard should auto-advance into it.
        assertTrue(workout.stepIsSiloBacked(after))
    }

    @Test
    fun final_section_completion_is_final_and_not_silo_backed() {
        val workout = sampleBatchWorkout()
        val run = WorkoutActiveRun(
            workoutId = workout.id,
            workoutSnapshot = workout,
            position = WorkoutRunPosition(segmentIndex = 1),
            lastLaunchedSegmentId = workout.segments[1].id,
            lastLaunchedItemId = workout.segments[1].items[0].id,
            lastLaunchedItemIds = listOf(workout.segments[1].items[0].id),
        )
        val after = run.positionAfterLaunchedSection()
        assertTrue(WorkoutRunEngine.isWorkoutComplete(workout, after))
        assertTrue(run.isFinalLoggableStep())
        assertFalse(workout.stepIsSiloBacked(after))
    }

    @Test
    fun weightLaunch_falls_back_to_run_position_when_pointer_missing() {
        val workout = sampleBatchWorkout()
        // Simulate the navigation race: the launch pointer never persisted, but the
        // run position points at the warm-up weight section.
        val state = WorkoutLibraryState(
            workouts = listOf(workout),
            activeRun = WorkoutActiveRun(
                workoutId = workout.id,
                workoutSnapshot = workout,
                position = WorkoutRunPosition(),
                lastLaunchedSegmentId = null,
                lastLaunchedItemId = null,
                lastLaunchedItemIds = emptyList(),
            ),
        )
        val launch = state.activeWorkoutWeightLaunch()
        assertNotNull(launch)
        assertEquals(workout.segments[0].id, launch!!.segmentId)
        assertEquals(workout.segments[0].items[0].id, launch.itemId)
        // And there is no spurious cardio launch for the same position.
        assertNull(state.activeWorkoutCardioLaunch())
    }

    @Test
    fun cardioLaunch_falls_back_to_run_position_when_pointer_missing() {
        val workout = sampleBatchWorkout()
        val state = WorkoutLibraryState(
            workouts = listOf(workout),
            activeRun = WorkoutActiveRun(
                workoutId = workout.id,
                workoutSnapshot = workout,
                position = WorkoutRunPosition(segmentIndex = 1),
                lastLaunchedSegmentId = null,
                lastLaunchedItemId = null,
            ),
        )
        val launch = state.activeWorkoutCardioLaunch()
        assertNotNull(launch)
        assertEquals(workout.segments[1].id, launch!!.segmentId)
        assertEquals(workout.segments[1].items[0].id, launch.itemId)
        assertNull(state.activeWorkoutWeightLaunch())
    }

    @Test
    fun note_and_rest_steps_are_not_silo_backed() {
        val workout = Workout(
            name = "passive",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Note(text = "go"),
                        WorkoutItem.Rest(durationSeconds = 30),
                    ),
                ),
            ),
        )
        assertFalse(workout.stepIsSiloBacked(WorkoutRunPosition()))
        assertFalse(workout.stepIsSiloBacked(WorkoutRunPosition(itemIndex = 1)))
    }

    private fun sampleBatchWorkout(): Workout = Workout(
        name = "Batch",
        segments = listOf(
            WorkoutSegment(
                kind = WorkoutSegmentKind.STRAIGHT_SETS,
                title = "Warm-up",
                items = listOf(
                    WorkoutItem.Weight(exerciseId = "a"),
                    WorkoutItem.Weight(exerciseId = "b"),
                    WorkoutItem.Weight(exerciseId = "c"),
                ),
            ),
            WorkoutSegment(
                kind = WorkoutSegmentKind.CARDIO,
                title = "Run",
                items = listOf(
                    WorkoutItem.Cardio(cardio = WorkoutCardioPrescription(activity = "RUN")),
                ),
            ),
        ),
    )

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
