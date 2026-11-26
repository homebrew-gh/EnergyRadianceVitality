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
