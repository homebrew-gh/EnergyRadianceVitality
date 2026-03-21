package com.erv.app.cardio

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Minimum path length / point count before GPS replaces pace-based distance on save. */
object CardioGpsDistanceRules {
    const val MIN_PATH_METERS = 25.0
    const val MIN_POINTS = 2
}

object CardioGpsMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Sum of great-circle segment lengths; [points] sorted by time. */
    fun pathLengthMeters(points: List<CardioGpsPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += haversineMeters(
                points[i - 1].lat,
                points[i - 1].lon,
                points[i].lat,
                points[i].lon
            )
        }
        return sum
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r1 = Math.toRadians(lat1)
        val r2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(r1) * cos(r2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }
}
