package com.erv.app.workouts

import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.setLoggingStyle

/** Seed for one live weight session that covers several consecutive storyboard lifts. */
data class WorkoutWeightBatch(
    val exerciseOrder: List<String>,
    val setsByExerciseId: Map<String, List<WeightSet>>,
    val restBetweenSetsSecondsByExerciseId: Map<String, Int>,
    val timedPrepSecondsByExerciseId: Map<String, Int>,
)

/**
 * Builds the live session seed for a plain section's consecutive lifts.
 *
 * The live draft holds one entry per exercise, so when the same exercise appears more than once
 * (for example heavy sets, then back-off sets later in the section) its later sets are appended to
 * the first entry instead of being dropped. Rest and timed prep come from the first item that sets
 * them. Items whose exercise is missing from the library are left out. Returns null when nothing is
 * left to start.
 */
fun buildWorkoutWeightBatch(
    items: List<WorkoutItem.Weight>,
    library: WeightLibraryState,
): WorkoutWeightBatch? {
    val order = mutableListOf<String>()
    val setsByExerciseId = mutableMapOf<String, List<WeightSet>>()
    val restByExerciseId = mutableMapOf<String, Int>()
    val prepByExerciseId = mutableMapOf<String, Int>()
    items.forEach { item ->
        val exerciseId = item.exerciseId
        val exercise = library.exerciseById(exerciseId) ?: return@forEach
        val sets = item.prescription.resolvedSets(loggingStyle = exercise.setLoggingStyle())
        if (exerciseId !in setsByExerciseId) order.add(exerciseId)
        setsByExerciseId[exerciseId] = setsByExerciseId[exerciseId].orEmpty() + sets
        item.prescription.restBetweenSetsSeconds?.takeIf { it > 0 }?.let { seconds ->
            restByExerciseId.putIfAbsent(exerciseId, seconds)
        }
        item.prescription.effectiveTimedPrepSeconds().takeIf { it > 0 }?.let { seconds ->
            prepByExerciseId.putIfAbsent(exerciseId, seconds)
        }
    }
    if (order.isEmpty()) return null
    return WorkoutWeightBatch(
        exerciseOrder = order,
        setsByExerciseId = setsByExerciseId,
        restBetweenSetsSecondsByExerciseId = restByExerciseId,
        timedPrepSecondsByExerciseId = prepByExerciseId,
    )
}

/**
 * Index of the first item at or after [from] that offers alternative exercises, or null when no
 * remaining item needs a choice. Used to ask for each choice in turn before launching a section.
 */
fun List<WorkoutItem.Weight>.nextAlternativeChoiceIndex(from: Int): Int? =
    (from.coerceAtLeast(0) until size).firstOrNull { this[it].alternativeExerciseIds.isNotEmpty() }
