package com.erv.app.weighttraining

import java.time.LocalDate

/**
 * One occurrence of an exercise inside a logged workout on [logDate].
 */
data class WeightExerciseHistoryRow(
    val logDate: LocalDate,
    val workout: WeightWorkoutSession,
    val entry: WeightWorkoutEntry
)

/** Exercise IDs that appear in at least one saved workout entry across all day logs. */
fun WeightLibraryState.exerciseIdsUsedInAnyLog(): Set<String> {
    val ids = mutableSetOf<String>()
    for (dayLog in logs) {
        for (workout in dayLog.workouts) {
            for (entry in workout.entries) {
                ids.add(entry.exerciseId)
            }
        }
    }
    return ids
}

/**
 * Everything [exerciseId] got in this workout as one entry. An exercise done twice (a second block
 * of the same lift) has its sets joined in order; a lone interval block keeps its block.
 */
fun WeightWorkoutSession.entryForExercise(exerciseId: String): WeightWorkoutEntry? {
    val matches = entries.filter { it.exerciseId == exerciseId }
    if (matches.size <= 1) return matches.firstOrNull()
    val withSets = matches.filter { it.sets.isNotEmpty() }
    if (withSets.isEmpty()) return matches.first()
    return withSets.first().copy(sets = withSets.flatMap { it.sets }, hiitBlock = null)
}

/**
 * All log occurrences for [exerciseId], newest calendar day first; within a day, later session timestamps first.
 */
fun WeightLibraryState.historyForExercise(exerciseId: String): List<WeightExerciseHistoryRow> {
    val rows = mutableListOf<WeightExerciseHistoryRow>()
    for (dayLog in logs) {
        val date = runCatching { LocalDate.parse(dayLog.date) }.getOrNull() ?: continue
        for (workout in dayLog.workouts) {
            val entry = workout.entryForExercise(exerciseId) ?: continue
            rows.add(WeightExerciseHistoryRow(date, workout, entry))
        }
    }
    return rows.sortedWith(
        compareByDescending<WeightExerciseHistoryRow> { it.logDate }
            .thenByDescending { it.workout.startedAtEpochSeconds ?: it.workout.finishedAtEpochSeconds ?: 0L }
    )
}

private val repBucketLabels: List<String> =
    (1..10).map { n -> if (n == 1) "1 rep" else "$n reps" } + "10+ reps"

/**
 * For reps 1–10 and 11+ ("10+"), the maximum [WeightSet.weightKg] ever logged for [exerciseId]
 * among working sets (warm-ups left out) with positive reps and weight. Buckets use exact rep count for 1–10; sets with 11+
 * reps contribute only to the last bucket.
 */
fun WeightLibraryState.maxWeightByRepBucketKg(exerciseId: String): List<Pair<String, Double?>> {
    val max = arrayOfNulls<Double>(11)
    for (dayLog in logs) {
        for (workout in dayLog.workouts) {
            val entry = workout.entryForExercise(exerciseId) ?: continue
            for (set in entry.sets) {
                if (set.isWarmup()) continue
                val reps = set.reps
                val w = set.weightKg ?: continue
                if (reps <= 0 || w <= 0.0) continue
                val idx = if (reps <= 10) reps - 1 else 10
                val prev = max[idx]
                if (prev == null || w > prev) max[idx] = w
            }
        }
    }
    return repBucketLabels.zip(max.toList())
}
