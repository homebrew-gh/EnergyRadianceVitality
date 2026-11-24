package com.erv.app.workouts

import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.weightLoadUnitSuffix

/**
 * The session engine as the saved run sees it: starting a session, applying events with the time
 * each step began, and what the ongoing notification shows. Pure, so the foreground service and
 * the session screen agree on one set of rules that are unit tested.
 */

/** The plan for this run, built from the workout as it was when the run started. */
fun WorkoutActiveRun.sessionPlan(): WorkoutSessionPlan = WorkoutSessionPlan.from(workoutSnapshot, sessionRestOptions)

/** Starts the single-screen session at its first step, or keeps one already under way. */
fun WorkoutActiveRun.withSessionStarted(nowEpochSeconds: Long): WorkoutActiveRun =
    if (session != null) {
        this
    } else {
        copy(
            startedAtEpochSeconds = startedAtEpochSeconds ?: nowEpochSeconds,
            session = WorkoutSessionState(),
            sessionStepStartedAtEpochSeconds = nowEpochSeconds,
        )
    }

/**
 * This run after [event]. The step clock restarts whenever the session moves to another step, so
 * a rest or a timed hold counts down from when it came on screen.
 */
fun WorkoutActiveRun.withSessionEvent(event: WorkoutSessionEvent, nowEpochSeconds: Long): WorkoutActiveRun {
    val current = session ?: return this
    val reduced = WorkoutSessionEngine.reduce(sessionPlan(), current, event)
    if (reduced == current) return this
    // Stamp each newly finished step, so a section's log gets its start and end times.
    val next = reduced.copy(
        records = reduced.records.map { record ->
            if (record.atEpochSeconds == null && current.recordFor(record.stepIndex) == null) {
                record.copy(atEpochSeconds = nowEpochSeconds)
            } else {
                record
            }
        },
    )
    return copy(
        session = next,
        sessionStepStartedAtEpochSeconds = if (next.stepIndex != current.stepIndex) {
            nowEpochSeconds
        } else {
            sessionStepStartedAtEpochSeconds
        },
    )
}

/** How long the step runs on its own: a rest, a cardio leg with a target, or a stretch hold. */
fun WorkoutSessionStep.countdownSeconds(): Int? = when (this) {
    is WorkoutSessionStep.Rest -> seconds
    is WorkoutSessionStep.CardioLeg -> targetSeconds
    is WorkoutSessionStep.StretchHold -> holdSeconds
    is WorkoutSessionStep.LiftSet, is WorkoutSessionStep.Note -> null
}

/** When the current step's countdown ends, or null when the step waits for the athlete. */
fun WorkoutActiveRun.sessionCountdownEndsAtEpochSeconds(plan: WorkoutSessionPlan = sessionPlan()): Long? {
    val state = session ?: return null
    val step = WorkoutSessionEngine.currentStep(plan, state) ?: return null
    val seconds = step.countdownSeconds() ?: return null
    if (sessionStepAwaitingStart) return null
    val started = sessionStepStartedAtEpochSeconds ?: return null
    return started + seconds
}

/** Names the notification needs; the screen looks them up in the weight and stretch catalogs. */
fun interface WorkoutSessionNames {
    /** Display name for a weight exercise or a stretch catalog id, or null to fall back. */
    fun nameFor(step: WorkoutSessionStep): String?
}

data class WorkoutSessionNotificationContent(
    val title: String,
    val text: String,
    /** "Step 4 of 18", counting the steps the athlete does (lifts, legs, holds), not rests or notes. */
    val progress: String?,
    /**
     * The chronometer: counts down to this time when [countDown], otherwise counts up from it
     * (the start of the workout).
     */
    val chronometerEpochSeconds: Long,
    val countDown: Boolean,
    val complete: Boolean,
    /** The workout clock's start, which the notification falls back to when a countdown ends. */
    val workoutStartedAtEpochSeconds: Long,
)

/**
 * What the ongoing notification shows for this run: the workout name, the step on screen (or
 * the step after a rest) and a clock. [heartRateBpm] is the live reading, when a strap is on.
 */
fun WorkoutActiveRun.sessionNotificationContent(
    names: WorkoutSessionNames,
    loadUnit: BodyWeightUnit,
    nowEpochSeconds: Long,
    heartRateBpm: Int? = null,
): WorkoutSessionNotificationContent? {
    val state = session ?: return null
    val plan = sessionPlan()
    val title = workoutSnapshot.name.ifBlank { "Workout" }
    val workoutStart = startedAtEpochSeconds ?: nowEpochSeconds
    val step = WorkoutSessionEngine.currentStep(plan, state)
    if (step == null && isSessionWaitingForMore(plan)) {
        return WorkoutSessionNotificationContent(
            title = title,
            text = if (plan.size == 0) "Add an exercise to start" else "Add an exercise or finish",
            progress = sessionProgressLabel(plan, state),
            chronometerEpochSeconds = workoutStart,
            countDown = false,
            complete = false,
            workoutStartedAtEpochSeconds = workoutStart,
        )
    }
    step
        ?: return WorkoutSessionNotificationContent(
            title = title,
            text = "Workout complete",
            progress = null,
            chronometerEpochSeconds = workoutStart,
            countDown = false,
            complete = true,
            workoutStartedAtEpochSeconds = workoutStart,
        )
    val stepText = when (step) {
        is WorkoutSessionStep.Rest -> {
            val label = when (step.kind) {
                WorkoutSessionRestKind.AFTER_SECTION -> "Rest before the next section"
                else -> "Rest"
            }
            val after = sessionUpNextLabel(names, loadUnit, plan)
            if (after == null) label else "$label · Next: $after"
        }
        else -> describe(step, names, loadUnit)
    }
    val text = heartRateBpm?.takeIf { it > 0 }?.let { "$stepText · $it bpm" } ?: stepText
    val countdownEnd = sessionCountdownEndsAtEpochSeconds(plan)?.takeIf { it > nowEpochSeconds }
    return WorkoutSessionNotificationContent(
        title = title,
        text = text,
        progress = sessionProgressLabel(plan, state),
        chronometerEpochSeconds = countdownEnd ?: workoutStart,
        countDown = countdownEnd != null,
        complete = false,
        workoutStartedAtEpochSeconds = workoutStart,
    )
}

/** The next step still to do after the one on screen, rests left out: "Bench Press · Set 1 of 3 · 8 reps at 135 lb". */
fun WorkoutActiveRun.sessionUpNextLabel(
    names: WorkoutSessionNames,
    loadUnit: BodyWeightUnit,
    plan: WorkoutSessionPlan = sessionPlan(),
): String? {
    val state = session ?: return null
    val next = ((state.stepIndex + 1) until plan.size)
        .firstOrNull { plan.steps[it] !is WorkoutSessionStep.Rest && state.recordFor(it) == null }
        ?: return null
    return describe(plan.steps[next], names, loadUnit)
}

private fun WorkoutSessionStep.isWork(): Boolean =
    this is WorkoutSessionStep.LiftSet || this is WorkoutSessionStep.CardioLeg || this is WorkoutSessionStep.StretchHold

/** "Step 4 of 18" over lifts, legs and holds; the step on screen counts as the one you're on. */
fun sessionProgressLabel(plan: WorkoutSessionPlan, state: WorkoutSessionState): String? {
    val work = plan.steps.indices.filter { plan.steps[it].isWork() }
    if (work.isEmpty()) return null
    val current = work.count { it < state.stepIndex } + 1
    return "Step ${current.coerceAtMost(work.size)} of ${work.size}"
}

private fun describe(step: WorkoutSessionStep, names: WorkoutSessionNames, loadUnit: BodyWeightUnit): String =
    when (step) {
        is WorkoutSessionStep.LiftSet -> {
            val name = names.nameFor(step) ?: step.exerciseId
            val count = when {
                step.setCount <= 1 -> null
                step.inRounds -> "Round ${step.round} of ${step.setCount}"
                else -> "Set ${step.setIndex + 1} of ${step.setCount}"
            }
            val target = liftTarget(step, loadUnit)
            listOfNotNull(name, count, target).joinToString(" · ")
        }
        is WorkoutSessionStep.CardioLeg -> {
            val name = if (step.isRecovery) "Recovery" else names.nameFor(step) ?: step.label ?: "Cardio"
            val length = step.targetSeconds?.let(::formatStepSeconds)
            val leg = if (step.legCount > 1) "Leg ${step.legIndex + 1} of ${step.legCount}" else null
            listOfNotNull(name, length, leg).joinToString(" · ")
        }
        is WorkoutSessionStep.StretchHold -> {
            val name = names.nameFor(step) ?: step.catalogId
            val side = when (step.side) {
                WorkoutStretchSide.LEFT -> "Left side"
                WorkoutStretchSide.RIGHT -> "Right side"
                null -> null
            }
            listOfNotNull(name, side, formatStepSeconds(step.holdSeconds)).joinToString(" · ")
        }
        is WorkoutSessionStep.Note -> step.text.lineSequence().firstOrNull()?.trim().orEmpty().ifBlank { "Note" }
        is WorkoutSessionStep.Rest -> "Rest"
    }

private fun liftTarget(step: WorkoutSessionStep.LiftSet, loadUnit: BodyWeightUnit): String? {
    val target = step.target
    val reps = target.targetReps?.takeIf { it > 0 }
    val seconds = target.targetDurationSeconds?.takeIf { it > 0 }
    val load = target.targetWeightKg?.takeIf { it > 0 }
        ?.let { "${formatWeightLoadNumber(it, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}" }
    val amount = when {
        reps != null -> "$reps reps"
        seconds != null -> formatStepSeconds(seconds)
        else -> null
    }
    return when {
        amount != null && load != null -> "$amount at $load"
        else -> amount ?: load
    }
}

/** "30 s", "1:30" or "5 min". */
fun formatStepSeconds(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "%d:%02d".format(seconds / 60, seconds % 60)
}
