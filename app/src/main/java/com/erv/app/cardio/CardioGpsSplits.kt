package com.erv.app.cardio

/**
 * Splits worked out from a GPS route instead of tapped live: one every [splitMeters] along the
 * path, timed by the points' clock. A gap of more than [maxGapSeconds] between two fixes (a pause,
 * or GPS dropping out) adds no time, so a stop at a light or a pause doesn't slow a split.
 */
object CardioGpsSplits {
    const val DEFAULT_MAX_GAP_SECONDS = 20L

    fun fromTrack(
        points: List<CardioGpsPoint>,
        splitMeters: Double,
        maxGapSeconds: Long = DEFAULT_MAX_GAP_SECONDS,
    ): List<CardioWorkoutSplit> {
        if (points.size < 2 || splitMeters <= 0.0 || !splitMeters.isFinite()) return emptyList()
        val splits = mutableListOf<CardioWorkoutSplit>()
        var totalMeters = 0.0
        var movingSeconds = 0.0
        var nextAt = splitMeters
        var lastSplitSeconds = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = CardioGpsMath.haversineMeters(a.lat, a.lon, b.lat, b.lon)
            val dt = (b.epochSeconds - a.epochSeconds).coerceAtLeast(0L)
            val moving = if (dt > maxGapSeconds) 0.0 else dt.toDouble()
            val before = totalMeters
            totalMeters += d
            while (d > 0.0 && totalMeters >= nextAt) {
                val fraction = (nextAt - before) / d
                val at = movingSeconds + moving * fraction
                splits += CardioWorkoutSplit(
                    orderIndex = splits.size,
                    elapsedSeconds = Math.round(at).toInt(),
                    segmentElapsedSeconds = Math.round(at - lastSplitSeconds).toInt(),
                    totalDistanceMeters = nextAt,
                    segmentDistanceMeters = splitMeters,
                )
                lastSplitSeconds = at
                nextAt += splitMeters
            }
            movingSeconds += moving
        }
        return splits
    }

    /** Meters along the route up to [epochSeconds], between two fixes by straight line. */
    fun distanceAtEpoch(points: List<CardioGpsPoint>, epochSeconds: Long): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.epochSeconds >= epochSeconds) break
            val d = CardioGpsMath.haversineMeters(a.lat, a.lon, b.lat, b.lon)
            if (b.epochSeconds <= epochSeconds) {
                total += d
            } else {
                val span = (b.epochSeconds - a.epochSeconds).toDouble()
                total += if (span > 0) d * (epochSeconds - a.epochSeconds) / span else 0.0
                break
            }
        }
        return total
    }

    /** The split length the athlete picked for auto splits, in meters (quarter miles, or km). */
    fun splitMeters(quarterMiles: Int, unit: CardioDistanceUnit): Double = when (unit) {
        CardioDistanceUnit.MILES -> quarterMiles.coerceAtLeast(1) * 1609.344 / 4.0
        CardioDistanceUnit.KILOMETERS -> 1000.0
    }
}
