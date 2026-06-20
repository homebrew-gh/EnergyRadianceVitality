package com.erv.app.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRunEngineTest {

    private val circuitWorkout = Workout(
        name = "Circuit test",
        segments = listOf(
            WorkoutSegment(
                kind = WorkoutSegmentKind.CIRCUIT,
                title = "Core",
                rounds = 2,
                items = listOf(
                    WorkoutItem.Weight(exerciseId = "ex-a"),
                    WorkoutItem.Weight(exerciseId = "ex-b"),
                ),
            ),
        ),
    )

    @Test
    fun circuit_advances_items_then_rounds_then_completes() {
        var position = WorkoutRunPosition()
        position = WorkoutRunEngine.advance(circuitWorkout, position)
        assertEquals(0, position.segmentIndex)
        assertEquals(1, position.itemIndex)
        assertEquals(1, position.round)

        position = WorkoutRunEngine.advance(circuitWorkout, position)
        assertEquals(0, position.segmentIndex)
        assertEquals(0, position.itemIndex)
        assertEquals(2, position.round)

        position = WorkoutRunEngine.advance(circuitWorkout, position)
        assertEquals(0, position.segmentIndex)
        assertEquals(1, position.itemIndex)
        assertEquals(2, position.round)

        position = WorkoutRunEngine.advance(circuitWorkout, position)
        assertTrue(WorkoutRunEngine.isWorkoutComplete(circuitWorkout, position))
    }

    @Test
    fun pendingRest_between_exercises_in_circuit() {
        val workout = Workout(
            name = "Circuit",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.CIRCUIT,
                    rounds = 2,
                    restPolicy = WorkoutRestPolicy(
                        restBetweenItemsSeconds = 15,
                        restAfterRoundSeconds = 90,
                    ),
                    items = listOf(
                        WorkoutItem.Weight(exerciseId = "a"),
                        WorkoutItem.Weight(exerciseId = "b"),
                    ),
                ),
            ),
        )
        val position = WorkoutRunPosition(itemIndex = 0)
        val rest = WorkoutRunEngine.pendingRestBeforeAdvance(workout, position)
        assertEquals(15, rest?.seconds)
        assertEquals("Rest · next exercise", rest?.label)
    }

    @Test
    fun pendingRest_after_round_in_circuit() {
        val workout = Workout(
            name = "Circuit",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.CIRCUIT,
                    rounds = 3,
                    restPolicy = WorkoutRestPolicy(
                        restBetweenItemsSeconds = 0,
                        restAfterRoundSeconds = 60,
                    ),
                    items = listOf(
                        WorkoutItem.Weight(exerciseId = "a"),
                        WorkoutItem.Weight(exerciseId = "b"),
                    ),
                ),
            ),
        )
        val position = WorkoutRunPosition(itemIndex = 1, round = 1)
        val rest = WorkoutRunEngine.pendingRestBeforeAdvance(workout, position)
        assertEquals(60, rest?.seconds)
    }

    @Test
    fun pendingRest_after_segment_when_leaving() {
        val workout = Workout(
            name = "Two segments",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    restAfterSeconds = 45,
                    items = listOf(WorkoutItem.Weight(exerciseId = "a")),
                ),
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(WorkoutItem.Weight(exerciseId = "b")),
                ),
            ),
        )
        val position = WorkoutRunPosition(segmentIndex = 0, itemIndex = 0)
        val rest = WorkoutRunEngine.pendingRestBeforeAdvance(workout, position)
        assertEquals(45, rest?.seconds)
        assertEquals("Rest · next segment", rest?.label)
    }

    @Test
    fun pendingRest_after_exercise_in_straight_sets() {
        val workout = Workout(
            name = "Push",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(
                            exerciseId = "a",
                            prescription = WorkoutWeightPrescription(
                                setCount = 3,
                                restAfterExerciseSeconds = 45,
                            ),
                        ),
                        WorkoutItem.Weight(exerciseId = "b"),
                    ),
                ),
            ),
        )
        val rest = WorkoutRunEngine.pendingRestBeforeAdvance(workout, WorkoutRunPosition(itemIndex = 0))
        assertEquals(45, rest?.seconds)
        assertEquals("Rest · next exercise", rest?.label)
    }

    @Test
    fun currentStep_straight_sets_shows_item_progress() {
        val workout = Workout(
            name = "Lift",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    title = "Main",
                    items = listOf(
                        WorkoutItem.Weight(exerciseId = "a"),
                        WorkoutItem.Note(text = "Brace hard"),
                    ),
                ),
            ),
        )
        val step = WorkoutRunEngine.currentStep(workout, WorkoutRunPosition(itemIndex = 1))
        assertEquals("Main · 2/2 · Note", step?.label)
        assertTrue(step?.item is WorkoutItem.Note)
    }

    @Test
    fun superset_advances_like_circuit() {
        val workout = Workout(
            name = "Superset",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.SUPERSET,
                    rounds = 2,
                    items = listOf(
                        WorkoutItem.Weight(exerciseId = "a"),
                        WorkoutItem.Weight(exerciseId = "b"),
                    ),
                ),
            ),
        )
        var position = WorkoutRunPosition()
        position = WorkoutRunEngine.advance(workout, position)
        assertEquals(1, position.itemIndex)
        position = WorkoutRunEngine.advance(workout, position)
        assertEquals(0, position.itemIndex)
        assertEquals(2, position.round)
    }

    @Test
    fun straight_sets_advances_through_items() {
        val workout = Workout(
            name = "Lift",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(exerciseId = "ex-1"),
                        WorkoutItem.Weight(exerciseId = "ex-2"),
                    ),
                ),
            ),
        )
        var position = WorkoutRunPosition()
        assertFalse(WorkoutRunEngine.isWorkoutComplete(workout, position))
        position = WorkoutRunEngine.advance(workout, position)
        assertEquals(1, position.itemIndex)
        position = WorkoutRunEngine.advance(workout, position)
        assertTrue(WorkoutRunEngine.isWorkoutComplete(workout, position))
    }
}
