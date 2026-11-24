package com.erv.app.workouts

import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightPushPull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutWeightBatchTest {

    private val library = WeightLibraryState(
        exercises = listOf(
            WeightExercise(id = "bench", name = "Bench Press", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
            WeightExercise(id = "row", name = "Barbell Row", muscleGroup = "back", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.BARBELL),
        ),
    )

    private fun lift(exerciseId: String, sets: Int, reps: Int, rest: Int? = null, alternatives: List<String> = emptyList()) =
        WorkoutItem.Weight(
            exerciseId = exerciseId,
            alternativeExerciseIds = alternatives,
            prescription = WorkoutWeightPrescription(setCount = sets, targetReps = reps, restBetweenSetsSeconds = rest),
        )

    @Test
    fun repeated_exercise_keeps_every_set_in_order() {
        val batch = buildWorkoutWeightBatch(
            listOf(lift("bench", sets = 3, reps = 5, rest = 180), lift("row", sets = 3, reps = 8), lift("bench", sets = 2, reps = 10, rest = 90)),
            library,
        )!!

        assertEquals(listOf("bench", "row"), batch.exerciseOrder)
        assertEquals(5, batch.setsByExerciseId.getValue("bench").size)
        assertEquals(3, batch.setsByExerciseId.getValue("row").size)
        // The first item that sets rest wins for the merged exercise.
        assertEquals(180, batch.restBetweenSetsSecondsByExerciseId["bench"])
    }

    @Test
    fun missing_exercises_are_left_out() {
        val batch = buildWorkoutWeightBatch(listOf(lift("gone", 3, 5), lift("row", 3, 8)), library)!!
        assertEquals(listOf("row"), batch.exerciseOrder)
        assertNull(buildWorkoutWeightBatch(listOf(lift("gone", 3, 5)), library))
    }

    @Test
    fun next_alternative_choice_walks_items_that_offer_alternatives() {
        val items = listOf(lift("bench", 3, 5, alternatives = listOf("row")), lift("row", 3, 8), lift("row", 3, 8, alternatives = listOf("bench")))
        assertEquals(0, items.nextAlternativeChoiceIndex(0))
        assertEquals(2, items.nextAlternativeChoiceIndex(1))
        assertNull(items.nextAlternativeChoiceIndex(3))
    }
}
