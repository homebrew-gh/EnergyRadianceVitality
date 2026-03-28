package com.erv.app.weighttraining

import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.WorkoutModality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightExerciseAvailabilityTest {

    @Test
    fun filterWeightExercisesForPicker_all_returnsOriginalList() {
        val exercises = listOf(
            WeightExercise(name = "Bench", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
            WeightExercise(name = "Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL)
        )

        val filtered = filterWeightExercisesForPicker(
            exercises = exercises,
            filter = WeightExercisePickerFilter.ALL,
            ownedEquipment = emptyList(),
        )

        assertEquals(exercises, filtered)
    }

    @Test
    fun isHomeReadyFor_matchesBroadOwnedEquipmentCategories() {
        val owned = listOf(
            OwnedEquipmentItem(
                id = "bb",
                name = "Olympic bar",
                catalogKind = EquipmentCatalogKind.BARBELL,
                modalities = setOf(WorkoutModality.WEIGHT_TRAINING)
            ),
            OwnedEquipmentItem(
                id = "db",
                name = "Adjustable dumbbells",
                catalogKind = EquipmentCatalogKind.DUMBBELLS,
                modalities = setOf(WorkoutModality.WEIGHT_TRAINING)
            ),
            OwnedEquipmentItem(
                id = "pu",
                name = "Pull-up station",
                catalogKind = EquipmentCatalogKind.PULL_UP_DIP,
                modalities = setOf(WorkoutModality.WEIGHT_TRAINING)
            )
        )

        assertTrue(
            WeightExercise(
                name = "Bench Press",
                muscleGroup = "chest",
                pushOrPull = WeightPushPull.PUSH,
                equipment = WeightEquipment.BARBELL
            ).isHomeReadyFor(owned)
        )
        assertTrue(
            WeightExercise(
                name = "Hammer Curl",
                muscleGroup = "biceps",
                pushOrPull = WeightPushPull.PULL,
                equipment = WeightEquipment.DUMBBELL
            ).isHomeReadyFor(owned)
        )
        assertTrue(
            WeightExercise(
                name = "Pull-Up",
                muscleGroup = "back",
                pushOrPull = WeightPushPull.PULL,
                equipment = WeightEquipment.OTHER
            ).isHomeReadyFor(owned)
        )
        assertFalse(
            WeightExercise(
                name = "Leg Press",
                muscleGroup = "legs",
                pushOrPull = WeightPushPull.PUSH,
                equipment = WeightEquipment.MACHINE
            ).isHomeReadyFor(owned)
        )
    }

    @Test
    fun isHomeReadyFor_acceptsManualWeightTools_forOtherExercises() {
        val owned = listOf(
            OwnedEquipmentItem(
                id = "manual",
                name = "Ab wheel",
                catalogKind = EquipmentCatalogKind.MANUAL,
                modalities = setOf(WorkoutModality.WEIGHT_TRAINING)
            )
        )

        assertTrue(
            WeightExercise(
                name = "Ab Wheel Rollout",
                muscleGroup = "core",
                pushOrPull = WeightPushPull.PUSH,
                equipment = WeightEquipment.OTHER
            ).isHomeReadyFor(owned)
        )
    }
}
