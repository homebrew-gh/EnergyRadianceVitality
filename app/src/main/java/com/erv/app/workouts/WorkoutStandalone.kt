package com.erv.app.workouts

import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSetLoggingStyle
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.weighttraining.setLoggingStyle
import java.util.UUID

/**
 * Workouts started on the phone instead of from the library: an empty one built as it goes, or
 * one made from a weight routine. They run on the session screen like any workout, but only the
 * run holds them (see [WorkoutActiveRun.standalone]).
 */

/** Ids of standalone workouts start with this, so their logs can be told apart. */
const val STANDALONE_WORKOUT_ID_PREFIX = "standalone-"

fun isStandaloneWorkoutId(workoutId: String): Boolean = workoutId.startsWith(STANDALONE_WORKOUT_ID_PREFIX)

/** Sets for a lift in a standalone workout when nothing else says how many. */
const val STANDALONE_DEFAULT_SET_COUNT = 3

/** The name an empty standalone workout goes by, in the notification and history. */
const val STANDALONE_EMPTY_WORKOUT_NAME = "Weight Training"

/**
 * Plain sets for [exercise] in a standalone workout: as many as last time (else three), with no
 * rep or load targets, since a routine is only a list of exercises. A timed exercise keeps its
 * hold target.
 */
fun standaloneLiftPrescription(exercise: WeightExercise?, lastSetCount: Int? = null): WorkoutWeightPrescription {
    val base = defaultWorkoutPrescriptionForExercise(exercise, WorkoutSegmentKind.STRAIGHT_SETS)
    val count = lastSetCount?.takeIf { it > 0 }?.coerceAtMost(SESSION_MAX_SET_COUNT) ?: STANDALONE_DEFAULT_SET_COUNT
    return when (exercise?.setLoggingStyle()) {
        WeightSetLoggingStyle.TIME_ONLY -> base.copy(setCount = count)
        else -> base.copy(setCount = count, targetReps = null, repRangeMin = null, repRangeMax = null)
    }
}

/**
 * A standalone workout: [routine]'s exercises as one section of plain sets, or nothing at all
 * for an empty workout. Exercises no longer in the library are left out, as the weight screen did.
 */
fun standaloneWorkout(
    routine: WeightRoutine?,
    exerciseById: (String) -> WeightExercise?,
    lastSetCount: (String) -> Int? = { null },
    nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
): Workout {
    val items = routine?.exerciseIds.orEmpty()
        .distinct()
        .mapNotNull { id ->
            val exercise = exerciseById(id) ?: return@mapNotNull null
            WorkoutItem.Weight(exerciseId = id, prescription = standaloneLiftPrescription(exercise, lastSetCount(id)))
        }
    return Workout(
        id = "$STANDALONE_WORKOUT_ID_PREFIX${UUID.randomUUID()}",
        name = routine?.name?.trim()?.takeIf { it.isNotEmpty() } ?: STANDALONE_EMPTY_WORKOUT_NAME,
        segments = if (items.isEmpty()) {
            emptyList()
        } else {
            listOf(WorkoutSegment(kind = WorkoutSegmentKind.STRAIGHT_SETS, items = items))
        },
        createdAtEpochSeconds = nowEpochSeconds,
        lastModifiedEpochSeconds = nowEpochSeconds,
    )
}

/** A started run of [workout] (from [standaloneWorkout]) with its session under way. */
fun standaloneRun(
    workout: Workout,
    routine: WeightRoutine?,
    restOptions: WorkoutSessionRestOptions?,
    nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
): WorkoutActiveRun =
    WorkoutActiveRun(
        workoutId = workout.id,
        workoutSnapshot = workout,
        createdAtEpochSeconds = nowEpochSeconds,
        startedAtEpochSeconds = nowEpochSeconds,
        sessionRestOptions = restOptions,
        standalone = WorkoutStandaloneStart(routineId = routine?.id, routineName = routine?.name),
    ).withSessionStartedAtPosition(nowEpochSeconds)

/**
 * Starts a standalone workout (empty, or from [routine]) as the active run, with each exercise
 * set to as many sets as last time. Returns its workout id for the run screen, or null when
 * another workout is already under way.
 */
suspend fun WorkoutRepository.startQuickStart(
    routine: WeightRoutine?,
    weightState: com.erv.app.weighttraining.WeightLibraryState,
): String? {
    val workout = standaloneWorkout(
        routine = routine,
        exerciseById = weightState::exerciseById,
        lastSetCount = { id -> weightState.historyForExercise(id).firstOrNull()?.entry?.sets?.size },
    )
    return if (startStandaloneRun(standaloneRun(workout, routine, restOptions = null))) workout.id else null
}
