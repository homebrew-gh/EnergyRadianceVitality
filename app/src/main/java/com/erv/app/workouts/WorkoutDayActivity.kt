package com.erv.app.workouts

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import java.time.LocalDate

/** What was logged on one day, for the Training tab's week strip and hero cards. */
data class TrainingDayActivity(
    /** Workouts with at least one section logged on this day. */
    val loggedWorkoutIds: Set<String> = emptySet(),
    /** Any lifting, cardio or stretching session was logged on this day. */
    val trained: Boolean = false,
)

fun trainingDayActivity(
    date: LocalDate,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
): TrainingDayActivity {
    val weight = weightState.logFor(date)?.workouts.orEmpty()
    val cardio = cardioState.logFor(date)?.sessions.orEmpty()
    val stretch = stretchState.logFor(date)?.sessions.orEmpty()
    val workoutIds = buildSet {
        weight.forEach { session -> session.workoutLink?.workoutId?.let(::add) }
        cardio.forEach { session -> session.workoutLink?.workoutId?.let(::add) }
        stretch.forEach { session -> session.workoutLink?.workoutId?.let(::add) }
    }
    return TrainingDayActivity(
        loggedWorkoutIds = workoutIds,
        trained = weight.isNotEmpty() || cardio.isNotEmpty() || stretch.isNotEmpty(),
    )
}

/**
 * Seconds after Start during which backing out of a run that has logged nothing just leaves,
 * the same as before Start used to. Opening a workout now starts it, so a quick look should
 * not need a "Leave this workout?" answer.
 */
const val WORKOUT_RUN_QUICK_EXIT_SECONDS = 60L

/** True when the run started moments ago and no section has been launched or logged yet. */
fun WorkoutActiveRun.isFreshWithNothingLogged(nowEpochSeconds: Long = nowWorkoutEpochSeconds()): Boolean {
    val started = startedAtEpochSeconds ?: return true
    return lastLaunchedSegmentId == null &&
        itemRecaps.isEmpty() &&
        completedSegmentIds.isEmpty() &&
        nowEpochSeconds - started < WORKOUT_RUN_QUICK_EXIT_SECONDS
}
