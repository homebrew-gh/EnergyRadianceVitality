package com.erv.app.workouts

import com.erv.app.cardio.CardioPaceGoal
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PaceGoalState { AHEAD, ON_PACE, BEHIND }

/**
 * Where the athlete stands against a pace goal ("10 mi in 45 min") after [elapsedSeconds] and
 * [coveredMeters].
 */
data class PaceGoalStatus(
    val state: PaceGoalState,
    /** Seconds ahead of the goal at this distance; negative when behind. */
    val aheadSeconds: Int,
    /** Metres ahead of where the goal pace would be now; negative when behind. */
    val aheadMeters: Double,
    /** Share of the goal distance covered, 0 to 1. */
    val youFraction: Float,
    /** Share of the goal distance the goal pace has covered by now, 0 to 1. */
    val goalFraction: Float,
    /** Finish time at the average pace so far, or null before any distance. */
    val projectedFinishSeconds: Int?,
)

object WorkoutPaceGoals {

    /** Within this many seconds of the goal counts as on pace. */
    const val ON_PACE_SECONDS = 10

    /** The goal for a cardio item: the one picked at Cardio's Start, else the builder's distance and time. */
    fun goalFor(prescription: WorkoutCardioPrescription, standaloneGoal: CardioPaceGoal?): CardioPaceGoal? {
        standaloneGoal?.takeIf { it.distanceMeters > 0 && it.seconds > 0 }?.let { return it }
        val meters = prescription.targetDistanceMeters?.takeIf { it > 0 } ?: return null
        val minutes = prescription.targetMinutes?.takeIf { it > 0 } ?: return null
        return CardioPaceGoal(distanceMeters = meters, seconds = minutes * 60)
    }

    fun status(goal: CardioPaceGoal, elapsedSeconds: Int, coveredMeters: Double): PaceGoalStatus {
        val metersPerSecond = goal.distanceMeters / goal.seconds
        val covered = coveredMeters.coerceAtLeast(0.0)
        val goalMeters = (metersPerSecond * elapsedSeconds).coerceAtMost(goal.distanceMeters)
        // At the goal pace, this much distance takes this long; less time than the clock is ahead.
        val goalSecondsForCovered = covered / metersPerSecond
        val ahead = (goalSecondsForCovered - elapsedSeconds).roundToInt()
        val state = when {
            abs(ahead) <= ON_PACE_SECONDS -> PaceGoalState.ON_PACE
            ahead > 0 -> PaceGoalState.AHEAD
            else -> PaceGoalState.BEHIND
        }
        val projected = if (covered > 1.0 && elapsedSeconds > 0) {
            (elapsedSeconds * goal.distanceMeters / covered).roundToInt()
        } else {
            null
        }
        return PaceGoalStatus(
            state = state,
            aheadSeconds = ahead,
            aheadMeters = covered - goalMeters,
            youFraction = (covered / goal.distanceMeters).coerceIn(0.0, 1.0).toFloat(),
            goalFraction = (goalMeters / goal.distanceMeters).coerceIn(0.0, 1.0).toFloat(),
            projectedFinishSeconds = projected,
        )
    }
}
