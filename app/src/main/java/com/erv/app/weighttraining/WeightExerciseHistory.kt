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

/**
 * All log occurrences for [exerciseId], newest calendar day first; within a day, later session timestamps first.
 */
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

fun WeightLibraryState.historyForExercise(exerciseId: String): List<WeightExerciseHistoryRow> {
    val rows = mutableListOf<WeightExerciseHistoryRow>()
    for (dayLog in logs) {
        val date = runCatching { LocalDate.parse(dayLog.date) }.getOrNull() ?: continue
        for (workout in dayLog.workouts) {
            val entry = workout.entries.firstOrNull { it.exerciseId == exerciseId } ?: continue
            rows.add(WeightExerciseHistoryRow(date, workout, entry))
        }
    }
    return rows.sortedWith(
        compareByDescending<WeightExerciseHistoryRow> { it.logDate }
            .thenByDescending { it.workout.startedAtEpochSeconds ?: it.workout.finishedAtEpochSeconds ?: 0L }
    )
}
