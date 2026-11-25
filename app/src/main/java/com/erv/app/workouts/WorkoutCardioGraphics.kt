package com.erv.app.workouts

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioPaceGoal
import com.erv.app.cardio.CardioSession

/** One leg of an interval set, for the strip of work and recovery blocks on the live card. */
data class IntervalStripBlock(
    /** Share of the strip's width; legs with a time are as wide as their time. */
    val weight: Float,
    val isRecovery: Boolean,
)

data class IntervalStrip(
    val blocks: List<IntervalStripBlock>,
    /** Which block is running now. */
    val currentIndex: Int,
)

object WorkoutCardioGraphics {

    /** A previous session counts as "last ride" only with at least this much distance. */
    private const val MIN_GHOST_METERS = 100.0
    private const val MIN_GHOST_SECONDS = 60

    /**
     * The legs of [current]'s cardio item in this round, in order, or null for a single steady leg.
     * Legs with a time get widths by time; distance legs (or a mix) get equal widths.
     */
    fun intervalStrip(steps: List<WorkoutSessionStep>, current: WorkoutSessionStep.CardioLeg): IntervalStrip? {
        if (current.legCount <= 1) return null
        val legs = steps.filterIsInstance<WorkoutSessionStep.CardioLeg>().filter {
            it.segmentId == current.segmentId && it.itemId == current.itemId && it.round == current.round
        }
        if (legs.size <= 1) return null
        val index = legs.indexOfFirst { it.legIndex == current.legIndex }.takeIf { it >= 0 } ?: return null
        val allTimed = legs.all { (it.targetSeconds ?: 0) > 0 }
        return IntervalStrip(
            blocks = legs.map { leg ->
                IntervalStripBlock(
                    weight = if (allTimed) (leg.targetSeconds ?: 1).toFloat() else 1f,
                    isRecovery = leg.isRecovery,
                )
            },
            currentIndex = index,
        )
    }

    /**
     * The most recent logged session of [activity] (a built-in activity's name) that finished
     * before [beforeEpochSeconds], as a distance in a time to race against. Null when there is none
     * with a distance and a time.
     */
    fun lastSessionGhost(state: CardioLibraryState, activity: String, beforeEpochSeconds: Long): CardioPaceGoal? =
        state.logs.asSequence()
            .flatMap { it.sessions.asSequence() }
            .filter { it.activity.builtin?.name == activity && it.segments.isEmpty() }
            .filter { it.finishedAt() < beforeEpochSeconds }
            .mapNotNull { session ->
                val meters = session.distanceMeters?.takeIf { it >= MIN_GHOST_METERS } ?: return@mapNotNull null
                val seconds = session.seconds().takeIf { it >= MIN_GHOST_SECONDS } ?: return@mapNotNull null
                session.finishedAt() to CardioPaceGoal(distanceMeters = meters, seconds = seconds)
            }
            .maxByOrNull { it.first }
            ?.second

    private fun CardioSession.finishedAt(): Long = endEpochSeconds ?: startEpochSeconds ?: loggedAtEpochSeconds

    private fun CardioSession.seconds(): Int {
        val start = startEpochSeconds
        val end = endEpochSeconds
        if (start != null && end != null && end > start) return (end - start).toInt()
        return durationMinutes * 60
    }
}
