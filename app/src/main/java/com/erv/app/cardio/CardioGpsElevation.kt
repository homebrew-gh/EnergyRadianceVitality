package com.erv.app.cardio

import kotlin.math.max
import kotlin.math.min

/**
 * Cumulative gain / loss from GPS altitude samples. Uses light smoothing and a minimum step
 * so barometer/GPS noise does not dominate.
 */
object CardioGpsElevation {

    private const val MIN_STEP_M = 4.0
    private const val SMOOTH_WINDOW = 3

    /** Gain and loss in meters (may be small on flat routes), or null if fewer than two altitude samples. */
    fun computeGainLossMeters(points: List<CardioGpsPoint>): Pair<Double, Double>? {
        val ordered = points.sortedBy { it.epochSeconds }
        val alts = ordered.mapNotNull { p -> p.altitudeMeters }
        if (alts.size < 2) return null
        val smoothed = movingAverage(alts, SMOOTH_WINDOW)
        var gain = 0.0
        var loss = 0.0
        for (i in 1 until smoothed.size) {
            val d = smoothed[i] - smoothed[i - 1]
            when {
                d >= MIN_STEP_M -> gain += d
                d <= -MIN_STEP_M -> loss += -d
            }
        }
        return gain to loss
    }

    private fun movingAverage(data: List<Double>, window: Int): List<Double> {
        if (data.isEmpty()) return data
        val half = window / 2
        return data.indices.map { i ->
            val from = max(0, i - half)
            val to = min(data.lastIndex, i + half)
            (from..to).sumOf { data[it] } / (to - from + 1)
        }
    }
}

fun formatCardioElevationGainLoss(
    gainMeters: Double,
    lossMeters: Double,
    distanceUnit: CardioDistanceUnit
): String {
    return when (distanceUnit) {
        CardioDistanceUnit.MILES -> {
            val g = gainMeters * 3.28084
            val l = lossMeters * 3.28084
            "Elevation: +%.0f / −%.0f ft".format(g, l)
        }
        CardioDistanceUnit.KILOMETERS ->
            "Elevation: +%.0f / −%.0f m".format(gainMeters, lossMeters)
    }
}

/** Compact suffix for log rows, e.g. ` • +120/−115 ft`. */
fun formatCardioElevationSummarySuffix(
    gainMeters: Double,
    lossMeters: Double,
    distanceUnit: CardioDistanceUnit
): String {
    return when (distanceUnit) {
        CardioDistanceUnit.MILES -> {
            val g = gainMeters * 3.28084
            val l = lossMeters * 3.28084
            " • +%.0f/−%.0f ft".format(g, l)
        }
        CardioDistanceUnit.KILOMETERS ->
            " • +%.0f/−%.0f m".format(gainMeters, lossMeters)
    }
}
