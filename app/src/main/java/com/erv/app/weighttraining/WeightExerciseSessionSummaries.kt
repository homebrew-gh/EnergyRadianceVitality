package com.erv.app.weighttraining

import kotlin.math.max
import kotlinx.serialization.Serializable

/**
 * One row per calendar day + workout for this exercise: volume and best estimated 1RM among logged
 * sets (Epley vs Brzycki max). Built from day logs only — safe to rebuild any time.
 */
@Serializable
data class WeightExerciseSessionSummary(
    val date: String,
    val workoutId: String,
    /** Sum of reps × weightKg over all sets for this exercise in this workout (kg-based). */
    val volumeKg: Double = 0.0,
    val bestEstOneRmKg: Double? = null,
    val workingSetCount: Int = 0
)

/** Epley and Brzycki; returns the larger when both apply. */
fun estimatedOneRmKg(weightKg: Double, reps: Int): Double? {
    if (reps <= 0 || weightKg <= 0.0) return null
    val epley = weightKg * (1.0 + reps / 30.0)
    val brzycki = if (reps < 37) weightKg * 36.0 / (37.0 - reps) else null
    return if (brzycki != null) max(epley, brzycki) else epley
}

fun WeightWorkoutEntry.volumeKgForEntry(): Double {
    hiitBlock?.let { b ->
        return (b.weightKg ?: 0.0) * b.intervals.coerceAtLeast(0)
    }
    return sets.sumOf { s -> (s.weightKg ?: 0.0) * s.reps }
}

fun WeightWorkoutEntry.bestEstOneRmKgForEntry(): Double? {
    if (hiitBlock != null) return null
    return sets.mapNotNull { s ->
        val w = s.weightKg ?: return@mapNotNull null
        estimatedOneRmKg(w, s.reps)
    }.maxOrNull()
}

fun WeightWorkoutEntry.workingSetCount(): Int {
    hiitBlock?.let { return it.intervals.coerceAtLeast(0) }
    return sets.count { it.reps > 0 }
}

/**
 * Recomputes [WeightExercise.sessionSummaries] for every exercise in the library from [logs].
 * Entries for exercise IDs not in the library are ignored.
 */
fun WeightLibraryState.withRebuiltExerciseSessionSummaries(): WeightLibraryState {
    val ids = exercises.map { it.id }.toSet()
    val built = exercises.associate { it.id to mutableListOf<WeightExerciseSessionSummary>() }

    for (dayLog in logs) {
        val date = dayLog.date
        for (workout in dayLog.workouts) {
            val byExercise = workout.entries.groupBy { it.exerciseId }
            for ((exerciseId, entries) in byExercise) {
                if (exerciseId !in ids) continue
                val volumeKg = entries.sumOf { it.volumeKgForEntry() }
                val bestEst = entries.mapNotNull { it.bestEstOneRmKgForEntry() }.maxOrNull()
                val setCount = entries.sumOf { it.workingSetCount() }
                if (volumeKg <= 0.0 && bestEst == null && setCount == 0) continue
                built.getValue(exerciseId).add(
                    WeightExerciseSessionSummary(
                        date = date,
                        workoutId = workout.id,
                        volumeKg = volumeKg,
                        bestEstOneRmKg = bestEst,
                        workingSetCount = setCount
                    )
                )
            }
        }
    }

    return copy(
        exercises = exercises.map { ex ->
            ex.copy(
                sessionSummaries = built.getValue(ex.id)
                    .sortedWith(compareBy({ it.date }, { it.workoutId }))
            )
        }
    )
}
