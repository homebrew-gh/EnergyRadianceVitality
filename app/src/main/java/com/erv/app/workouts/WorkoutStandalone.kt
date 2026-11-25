package com.erv.app.workouts

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.CardioTimerStyle
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

/**
 * The draft for Cardio's Start to run on the session screen, or null when the old cardio screen
 * still has to run it: interval and multi-leg routines, distance countdowns, custom activities and
 * countdowns that aren't whole minutes. Those stay on the cardio screen until the session screen
 * can do them.
 */
fun CardioActiveTimerSession.sessionScreenCardioDraft(): CardioTimerSessionDraft? {
    val draft = (this as? CardioActiveTimerSession.Single)?.draft ?: return null
    if (draft.activity.builtin == null || draft.activity.customTypeId != null) return null
    return when (val style = draft.timerStyle) {
        CardioTimerStyle.CountUp -> draft
        is CardioTimerStyle.CountDown ->
            draft.takeIf { style.totalSeconds > 0 && style.totalSeconds % 60 == 0 }
        is CardioTimerStyle.CountDownDistance -> null
    }
}

/**
 * A standalone workout of one cardio item for [draft] (see [sessionScreenCardioDraft]): a steady
 * effort, open-ended or for the countdown's minutes.
 */
fun standaloneCardioWorkout(
    draft: CardioTimerSessionDraft,
    nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
): Workout {
    val builtin = requireNotNull(draft.activity.builtin) { "Only built-in activities run on the session screen" }
    val minutes = (draft.timerStyle as? CardioTimerStyle.CountDown)?.totalSeconds?.div(60)
    val name = draft.title.trim().ifEmpty { draft.activity.displayLabel }
    val item = WorkoutItem.Cardio(
        title = name,
        cardio = WorkoutCardioPrescription(activity = builtin.name, targetMinutes = minutes),
    )
    return Workout(
        id = "$STANDALONE_WORKOUT_ID_PREFIX${UUID.randomUUID()}",
        name = name,
        segments = listOf(WorkoutSegment(kind = WorkoutSegmentKind.CARDIO, items = listOf(item))),
        createdAtEpochSeconds = nowEpochSeconds,
        lastModifiedEpochSeconds = nowEpochSeconds,
    )
}

/** A started run of [workout] (from [standaloneCardioWorkout]) that keeps [draft] for its log entry. */
fun standaloneCardioRun(
    workout: Workout,
    draft: CardioTimerSessionDraft,
    nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
): WorkoutActiveRun =
    WorkoutActiveRun(
        workoutId = workout.id,
        workoutSnapshot = workout,
        createdAtEpochSeconds = nowEpochSeconds,
        startedAtEpochSeconds = nowEpochSeconds,
        standalone = WorkoutStandaloneStart(
            routineId = draft.routineId,
            routineName = draft.routineName,
            cardioItemId = workout.segments.firstOrNull()?.items?.firstOrNull()?.id,
            cardioDraft = draft,
        ),
    ).withSessionStartedAtPosition(nowEpochSeconds)

/** The cardio draft this run was started from, when [itemId] is its cardio item. */
fun WorkoutActiveRun.standaloneCardioDraftFor(itemId: String): CardioTimerSessionDraft? =
    standalone?.takeIf { it.cardioItemId == itemId }?.cardioDraft

/**
 * Starts Cardio's [draft] as a standalone workout on the session screen. Returns its workout id
 * for the run screen, or null when another workout is already under way.
 */
suspend fun WorkoutRepository.startCardioQuickStart(draft: CardioTimerSessionDraft): String? {
    val workout = standaloneCardioWorkout(draft)
    return if (startStandaloneRun(standaloneCardioRun(workout, draft))) workout.id else null
}
