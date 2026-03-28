package com.erv.app.weighttraining

import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.WorkoutModality

enum class WeightExercisePickerFilter {
    ALL,
    HOME_READY,
}

private val builtinNoEquipmentExerciseIds: Set<String> = setOf(
    "erv-weight-exercise-bw-pushup-v1",
    "erv-weight-exercise-bw-pike-pushup-v1",
    "erv-weight-exercise-bw-air-squat-v1",
    "erv-weight-exercise-bw-reverse-lunge-v1",
    "erv-weight-exercise-bw-glute-bridge-v1",
    "erv-weight-exercise-bw-situp-v1",
    "erv-weight-exercise-bw-crunch-v1",
    "erv-weight-exercise-bw-plank-v1",
    "erv-weight-exercise-bw-side-plank-v1",
    "erv-weight-exercise-bw-superman-v1",
    "erv-weight-exercise-bw-mountain-climber-v1",
    "erv-weight-exercise-bw-burpee-v1",
)

fun filterWeightExercisesForPicker(
    exercises: List<WeightExercise>,
    filter: WeightExercisePickerFilter,
    ownedEquipment: List<OwnedEquipmentItem>,
): List<WeightExercise> =
    when (filter) {
        WeightExercisePickerFilter.ALL -> exercises
        WeightExercisePickerFilter.HOME_READY -> exercises.filter { it.isHomeReadyFor(ownedEquipment) }
    }

fun WeightExercise.isHomeReadyFor(ownedEquipment: List<OwnedEquipmentItem>): Boolean {
    if (id in builtinNoEquipmentExerciseIds) return true
    val kinds = ownedEquipment.map { it.catalogKind }.toSet()
    val hasManualWeightTool = ownedEquipment.any { item ->
        item.catalogKind == EquipmentCatalogKind.MANUAL &&
            (item.modalities.isEmpty() ||
                WorkoutModality.WEIGHT_TRAINING in item.modalities ||
                WorkoutModality.HIIT in item.modalities)
    }
    return when (equipment) {
        WeightEquipment.BARBELL -> EquipmentCatalogKind.BARBELL in kinds
        WeightEquipment.DUMBBELL -> EquipmentCatalogKind.DUMBBELLS in kinds
        WeightEquipment.KETTLEBELL -> EquipmentCatalogKind.KETTLEBELLS in kinds
        WeightEquipment.MACHINE -> false
        WeightEquipment.OTHER ->
            hasManualWeightTool ||
                EquipmentCatalogKind.PULL_UP_DIP in kinds ||
                EquipmentCatalogKind.MOBILITY_TOOLS in kinds ||
                EquipmentCatalogKind.PARALLETTE_RINGS in kinds ||
                EquipmentCatalogKind.SUSPENSION_TRAINER in kinds
    }
}
