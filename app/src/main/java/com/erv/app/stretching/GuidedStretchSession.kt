package com.erv.app.stretching

/**
 * Pure rules for the guided stretch player's controls (Pause, Skip, Back, End), so they are unit
 * tested apart from the Compose overlay in `ui/stretching/StretchingScreen.kt`.
 */

/** Seconds into a hold after which Back restarts that hold instead of going to the one before. */
const val GUIDED_BACK_RESTARTS_AFTER_SECONDS = 3

/**
 * Where Back goes from step [index]. Early in a hold (or between holds) it goes to the step before;
 * once a hold has run a few seconds it restarts that hold, like a music player's previous button.
 */
fun guidedBackTarget(index: Int, inHold: Boolean, secondsIntoHold: Int): Int = when {
    index <= 0 -> 0
    inHold && secondsIntoHold > GUIDED_BACK_RESTARTS_AFTER_SECONDS -> index
    else -> index - 1
}

/**
 * Where Skip goes from step [index]: the next step, or null when [index] is the last one, which
 * means the session is over.
 */
fun guidedSkipTarget(index: Int, stepCount: Int): Int? =
    (index + 1).takeIf { it < stepCount }

/** What a guided session logs when it ends. */
data class GuidedStretchResult(
    val stretchIds: List<String>,
    val totalMinutes: Int,
)

/**
 * The log entry for a guided session. When every step was held at least a second, it logs the whole
 * routine and its planned length ([plannedMinutes]), as before. Otherwise (skips, or ended early) it
 * logs only the stretches that were held, in routine order, and the time actually spent
 * ([activeSeconds], pauses excluded), rounded up to whole minutes. Null when nothing was held.
 */
fun guidedStretchResult(
    steps: List<GuidedStretchStep>,
    heldStepIndexes: Set<Int>,
    routineStretchIds: List<String>,
    plannedMinutes: Int,
    activeSeconds: Int,
): GuidedStretchResult? {
    val held = heldStepIndexes.filter { it in steps.indices }.toSet()
    if (held.isEmpty()) return null
    if (held.size == steps.size) {
        return GuidedStretchResult(routineStretchIds, plannedMinutes)
    }
    val ids = held.sorted().map { steps[it].entry.id }.distinct()
    val minutes = ((activeSeconds.coerceAtLeast(0) + 59) / 60).coerceAtLeast(1)
    return GuidedStretchResult(ids, minutes)
}

/** For each guided step, the position of its stretch in the routine (both sides of one stretch share it). */
fun guidedEntryPositions(steps: List<GuidedStretchStep>): List<Int> {
    var pos = -1
    return steps.mapIndexed { i, step ->
        val sameAsBefore = i > 0 && steps[i - 1].entry.id == step.entry.id &&
            steps[i - 1].side == StretchSide.RIGHT && step.side == StretchSide.LEFT
        if (!sameAsBefore) pos++
        pos
    }
}

/**
 * Seconds left in the session from step [focusIndex]: what is left of the current phase
 * ([secondsLeftInPhase]), every later hold, and the gaps between them ([gapSeconds]). During a gap
 * ([inHold] false) the focus step's whole hold is still ahead.
 */
fun guidedSecondsRemaining(
    steps: List<GuidedStretchStep>,
    holdSeconds: Int,
    focusIndex: Int,
    inHold: Boolean,
    secondsLeftInPhase: Int,
    gapSeconds: (GuidedStretchStep, GuidedStretchStep) -> Int,
): Int {
    if (steps.isEmpty() || focusIndex !in steps.indices) return 0
    var total = secondsLeftInPhase.coerceAtLeast(0)
    if (!inHold) total += holdSeconds
    for (k in focusIndex + 1 until steps.size) {
        total += gapSeconds(steps[k - 1], steps[k]) + holdSeconds
    }
    return total
}

/**
 * Whether the voice reads the how-to for step [index] under [detail]. Never for the second side of
 * the same stretch (it was just read); [StretchGuidanceDetail.FIRST_TIME] only reads a stretch the
 * first time it comes up in the session.
 */
fun guidedReadsProcedure(detail: com.erv.app.data.StretchGuidanceDetail, steps: List<GuidedStretchStep>, index: Int): Boolean {
    val step = steps.getOrNull(index) ?: return false
    val prev = steps.getOrNull(index - 1)
    val otherSide = prev != null && prev.entry.id == step.entry.id && step.side == StretchSide.LEFT
    if (otherSide) return false
    return when (detail) {
        com.erv.app.data.StretchGuidanceDetail.FULL -> true
        com.erv.app.data.StretchGuidanceDetail.NAMES_ONLY -> false
        com.erv.app.data.StretchGuidanceDetail.FIRST_TIME ->
            steps.subList(0, index).none { it.entry.id == step.entry.id }
    }
}
