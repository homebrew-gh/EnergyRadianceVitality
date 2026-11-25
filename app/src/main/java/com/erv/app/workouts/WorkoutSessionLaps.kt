package com.erv.app.workouts

import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioGpsSplits
import com.erv.app.cardio.CardioWorkoutSplit
import kotlinx.serialization.Serializable

/**
 * One Lap tapped on a cardio item run on the session screen. [elapsedSeconds] is the item's time
 * so far (all its legs, pauses left out); [atEpochSeconds] is the wall clock, which places the lap
 * on the GPS route.
 */
@Serializable
data class WorkoutSessionLap(
    val segmentId: String,
    val itemId: String,
    val elapsedSeconds: Int,
    val atEpochSeconds: Long,
)

/** The running cardio leg Lap applies to, or null (waiting for Start, paused, or not cardio). */
fun WorkoutActiveRun.lapableCardioLeg(plan: WorkoutSessionPlan = sessionPlan()): WorkoutSessionStep.CardioLeg? {
    val state = session ?: return null
    val step = WorkoutSessionEngine.currentStep(plan, state) as? WorkoutSessionStep.CardioLeg ?: return null
    if (step.cardioRoutineId != null || sessionStepAwaitingStart || sessionStepStartedAtEpochSeconds == null) return null
    if (activeSessionPause() != null) return null
    return step
}

/** The cardio item's time so far: its finished legs plus the one running now. */
fun WorkoutActiveRun.sessionCardioItemElapsedSeconds(
    step: WorkoutSessionStep.CardioLeg,
    nowEpochSeconds: Long,
    plan: WorkoutSessionPlan = sessionPlan(),
): Int {
    val state = session ?: return 0
    val earlier = plan.steps.indices.filter { index ->
        val other = plan.steps[index]
        index != state.stepIndex && other is WorkoutSessionStep.CardioLeg &&
            other.segmentId == step.segmentId && other.itemId == step.itemId
    }.sumOf { index ->
        state.recordFor(index)?.takeIf { it.outcome == WorkoutStepOutcome.DONE }?.elapsedSeconds ?: 0
    }
    return earlier + sessionStepElapsedSeconds(nowEpochSeconds)
}

/** Laps tapped so far on one cardio item, in order. */
fun WorkoutActiveRun.sessionLapsFor(segmentId: String, itemId: String): List<WorkoutSessionLap> =
    sessionCardioLaps.filter { it.segmentId == segmentId && it.itemId == itemId }.sortedBy { it.elapsedSeconds }

/** Marks a lap on the running cardio leg. A second tap in the same second is ignored. */
fun WorkoutActiveRun.withSessionLap(nowEpochSeconds: Long): WorkoutActiveRun {
    val plan = sessionPlan()
    val step = lapableCardioLeg(plan) ?: return this
    val elapsed = sessionCardioItemElapsedSeconds(step, nowEpochSeconds, plan)
    val last = sessionLapsFor(step.segmentId, step.itemId).lastOrNull()?.elapsedSeconds ?: 0
    if (elapsed <= last) return this
    return copy(sessionCardioLaps = sessionCardioLaps + WorkoutSessionLap(step.segmentId, step.itemId, elapsed, nowEpochSeconds))
}

/**
 * The item's laps as cardio log splits, the last one running to [totalSeconds]. Each split's
 * distance comes from the route up to the lap, when there is one.
 */
fun sessionLapSplits(
    laps: List<WorkoutSessionLap>,
    totalSeconds: Int,
    finishedAtEpochSeconds: Long?,
    gpsPoints: List<CardioGpsPoint> = emptyList(),
): List<CardioWorkoutSplit> {
    if (laps.isEmpty()) return emptyList()
    val marks = laps.map { it.elapsedSeconds to it.atEpochSeconds }.toMutableList()
    val last = marks.last().first
    if (totalSeconds > last) marks += totalSeconds to (finishedAtEpochSeconds ?: Long.MAX_VALUE)
    val useRoute = gpsPoints.size >= 2
    var previousSeconds = 0
    var previousMeters = 0.0
    return marks.mapIndexed { index, (seconds, at) ->
        val meters = if (useRoute) CardioGpsSplits.distanceAtEpoch(gpsPoints, at) else null
        val split = CardioWorkoutSplit(
            orderIndex = index,
            elapsedSeconds = seconds,
            segmentElapsedSeconds = seconds - previousSeconds,
            totalDistanceMeters = meters,
            segmentDistanceMeters = meters?.let { (it - previousMeters).coerceAtLeast(0.0) },
        )
        previousSeconds = seconds
        if (meters != null) previousMeters = meters
        split
    }
}
