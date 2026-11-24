package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutEntry
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutWeightWriteBackTest {

    private fun lift(id: String, exerciseId: String, sets: Int, kg: Double?) = WorkoutItem.Weight(
        id = id,
        exerciseId = exerciseId,
        prescription = WorkoutWeightPrescription(setCount = sets, targetReps = 5, targetWeightKg = kg),
    )

    private fun session(vararg entries: Pair<String, List<WeightSet>>) = WeightWorkoutSession(
        source = WeightWorkoutSource.LIVE,
        entries = entries.map { (exerciseId, sets) -> WeightWorkoutEntry(exerciseId = exerciseId, sets = sets) },
    )

    private fun done(kg: Double, reps: Int = 5) = WeightSet(reps = reps, weightKg = kg)

    @Test
    fun single_target_becomes_heaviest_set_lifted() {
        val workout = Workout(
            id = "w",
            name = "Push",
            segments = listOf(
                WorkoutSegment(id = "main", kind = WorkoutSegmentKind.STRAIGHT_SETS, items = listOf(lift("b", "bench", 3, 60.0))),
            ),
        )
        val changes = proposeWorkoutTargetWeightChanges(
            workout,
            mapOf("main" to listOf(session("bench" to listOf(done(62.5), done(65.0), done(65.0))))),
        )
        assertEquals(1, changes.size)
        assertEquals(listOf<Double?>(60.0), changes[0].beforeKg)
        assertEquals(listOf<Double?>(65.0), changes[0].afterKg)

        val updated = workout.withTodaysWeights(changes)
        val item = updated.segments[0].items[0] as WorkoutItem.Weight
        assertEquals(65.0, item.prescription.targetWeightKg!!, 0.001)
        assertEquals(3, item.prescription.setCount)
    }

    @Test
    fun matching_or_unlogged_weights_change_nothing() {
        val workout = Workout(
            id = "w",
            name = "Push",
            segments = listOf(
                WorkoutSegment(
                    id = "main",
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(lift("b", "bench", 2, 60.0), lift("p", "plank", 1, null)),
                ),
            ),
        )
        val changes = proposeWorkoutTargetWeightChanges(
            workout,
            mapOf(
                "main" to listOf(
                    session(
                        "bench" to listOf(done(60.0), done(60.004)),
                        // A bodyweight hold logs no weight, and an untouched set logs no reps.
                        "plank" to listOf(WeightSet(durationSeconds = 60), WeightSet(reps = 0, weightKg = 20.0)),
                    ),
                ),
            ),
        )
        assertTrue(changes.isEmpty())
        assertSame(workout, workout.withTodaysWeights(changes))
    }

    @Test
    fun per_set_targets_take_each_logged_set() {
        val item = WorkoutItem.Weight(
            id = "s",
            exerciseId = "squat",
            prescription = WorkoutWeightPrescription(
                sets = listOf(
                    WeightSet(targetReps = 5, targetWeightKg = 100.0),
                    WeightSet(targetReps = 5, targetWeightKg = 100.0),
                    WeightSet(targetReps = 3, weightKg = 110.0),
                ),
            ),
        )
        val workout = Workout(
            id = "w",
            name = "Legs",
            segments = listOf(WorkoutSegment(id = "main", kind = WorkoutSegmentKind.STRAIGHT_SETS, items = listOf(item))),
        )
        // Third set not done today: it keeps its target.
        val changes = proposeWorkoutTargetWeightChanges(
            workout,
            mapOf("main" to listOf(session("squat" to listOf(done(102.5), done(100.0), WeightSet())))),
        )
        assertEquals(listOf<Double?>(100.0, 100.0, 110.0), changes.single().beforeKg)
        assertEquals(listOf<Double?>(102.5, 100.0, 110.0), changes.single().afterKg)
        val sets = (workout.withTodaysWeights(changes).segments[0].items[0] as WorkoutItem.Weight).prescription.sets
        assertEquals(102.5, sets[0].targetWeightKg!!, 0.001)
        assertEquals(100.0, sets[1].targetWeightKg!!, 0.001)
        assertNull(sets[2].targetWeightKg)
        assertEquals(110.0, sets[2].weightKg!!, 0.001)
    }

    @Test
    fun repeated_exercise_splits_its_sets_between_items_in_order() {
        val workout = Workout(
            id = "w",
            name = "Push",
            segments = listOf(
                WorkoutSegment(
                    id = "main",
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(lift("heavy", "bench", 2, 80.0), lift("row", "row", 2, 50.0), lift("backoff", "bench", 2, 60.0)),
                ),
            ),
        )
        // The live session appends the back-off sets to the first bench entry.
        val changes = proposeWorkoutTargetWeightChanges(
            workout,
            mapOf(
                "main" to listOf(
                    session(
                        "bench" to listOf(done(82.5), done(82.5), done(60.0), done(60.0)),
                        "row" to listOf(done(50.0), done(50.0)),
                    ),
                ),
            ),
        )
        assertEquals(1, changes.size)
        assertEquals("heavy", changes[0].itemId)
        assertEquals(listOf<Double?>(82.5), changes[0].afterKg)
    }

    @Test
    fun skipped_and_swapped_items_are_left_alone() {
        val workout = Workout(
            id = "w",
            name = "Push",
            segments = listOf(
                WorkoutSegment(
                    id = "main",
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        lift("first", "bench", 1, 60.0),
                        lift("skipped", "bench", 1, 40.0),
                        WorkoutItem.Weight(
                            id = "swap",
                            exerciseId = "ohp",
                            alternativeExerciseIds = listOf("db_press"),
                            prescription = WorkoutWeightPrescription(setCount = 1, targetWeightKg = 40.0),
                        ),
                    ),
                ),
            ),
        )
        val changes = proposeWorkoutTargetWeightChanges(
            workout,
            mapOf("main" to listOf(session("bench" to listOf(done(60.0)), "db_press" to listOf(done(25.0))))),
            skippedItemKeys = setOf("main" to "skipped"),
        )
        assertTrue(changes.isEmpty())
    }

    @Test
    fun circuit_items_use_one_set_per_round() {
        val workout = Workout(
            id = "w",
            name = "Circuit",
            segments = listOf(
                WorkoutSegment(
                    id = "c",
                    kind = WorkoutSegmentKind.CIRCUIT,
                    rounds = 3,
                    items = listOf(lift("kb", "swing", 1, 16.0)),
                ),
            ),
        )
        val changes = proposeWorkoutTargetWeightChanges(
            workout,
            mapOf("c" to listOf(session("swing" to listOf(done(16.0, 15), done(20.0, 15), done(20.0, 15))))),
        )
        assertEquals(listOf<Double?>(20.0), changes.single().afterKg)
    }

    @Test
    fun changes_apply_to_an_edited_library_copy_by_id() {
        val snapshot = Workout(
            id = "w",
            name = "Push",
            segments = listOf(
                WorkoutSegment(id = "main", kind = WorkoutSegmentKind.STRAIGHT_SETS, items = listOf(lift("b", "bench", 3, 60.0))),
            ),
        )
        val changes = proposeWorkoutTargetWeightChanges(
            snapshot,
            mapOf("main" to listOf(session("bench" to listOf(done(65.0))))),
        )
        // Edited on the PC during the run: a set was added and a new item came first.
        val edited = snapshot.copy(
            segments = listOf(
                snapshot.segments[0].copy(
                    items = listOf(lift("new", "row", 3, 50.0), lift("b", "bench", 4, 60.0)),
                ),
            ),
        )
        val updated = edited.withTodaysWeights(changes)
        val row = updated.segments[0].items[0] as WorkoutItem.Weight
        val bench = updated.segments[0].items[1] as WorkoutItem.Weight
        assertEquals(50.0, row.prescription.targetWeightKg!!, 0.001)
        assertEquals(65.0, bench.prescription.targetWeightKg!!, 0.001)
        assertEquals(4, bench.prescription.setCount)

        // An item whose exercise changed since the run is not touched.
        val swapped = edited.copy(
            segments = listOf(edited.segments[0].copy(items = listOf(lift("b", "squat", 3, 100.0)))),
        )
        assertSame(swapped, swapped.withTodaysWeights(changes))
    }
}
