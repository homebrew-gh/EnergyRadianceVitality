package com.erv.app.workouts

import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioGpsMath
import com.erv.app.cardio.CardioGpsPoint
import kotlin.math.roundToInt

/** A target band: a pace in seconds per km (lower is faster) or a heart rate in bpm. */
data class WorkoutTargetBand(val low: Double, val high: Double) {
    fun contains(value: Double): Boolean = value in low..high
}

enum class WorkoutTargetDrift { TOO_LOW, TOO_HIGH }

/**
 * Watches one live number against its band and says when to alert: once it has been out of the
 * band for [holdSeconds] in a row, then at most every [repeatSeconds] while it stays out. Coming
 * back into the band starts over. Pure, so it is unit tested; the session screen feeds it each
 * second and speaks what it returns.
 */
class WorkoutTargetWatcher(
    private val holdSeconds: Long = 15,
    private val repeatSeconds: Long = 60,
) {
    private var outSince: Long? = null
    private var outSide: WorkoutTargetDrift? = null
    private var lastAlertAt: Long? = null

    fun update(nowEpochSeconds: Long, value: Double?, band: WorkoutTargetBand?): WorkoutTargetDrift? {
        if (value == null || band == null) {
            reset()
            return null
        }
        val side = when {
            value < band.low -> WorkoutTargetDrift.TOO_LOW
            value > band.high -> WorkoutTargetDrift.TOO_HIGH
            else -> null
        }
        if (side == null) {
            reset()
            return null
        }
        if (side != outSide) {
            outSide = side
            outSince = nowEpochSeconds
            lastAlertAt = null
        }
        val since = outSince ?: nowEpochSeconds
        if (nowEpochSeconds - since < holdSeconds) return null
        val last = lastAlertAt
        if (last != null && nowEpochSeconds - last < repeatSeconds) return null
        lastAlertAt = nowEpochSeconds
        return side
    }

    fun reset() {
        outSince = null
        outSide = null
        lastAlertAt = null
    }
}

object WorkoutSessionTargets {

    /** The builder's pace band, or null when it set none. */
    fun paceBand(cardio: WorkoutCardioPrescription): WorkoutTargetBand? {
        val fast = cardio.paceTargetFastSecondsPerKm?.takeIf { it > 0 }
        val slow = cardio.paceTargetSlowSecondsPerKm?.takeIf { it > 0 }
        val low = fast ?: slow ?: return null
        val high = slow ?: fast ?: return null
        // A single pace gets a little room either side, so it isn't a buzz every step.
        return if (low == high) WorkoutTargetBand(low * 0.97, high * 1.03) else WorkoutTargetBand(minOf(low, high), maxOf(low, high))
    }

    /** The heart rate band: the builder's range, or a single target with 5 bpm either side. */
    fun heartRateBand(cardio: WorkoutCardioPrescription, legTargetBpm: Int?): WorkoutTargetBand? {
        val min = cardio.hrTargetMinBpm
        val max = cardio.hrTargetMaxBpm
        if (min != null && max != null && min > 0 && max > 0) return WorkoutTargetBand(min.toDouble(), max.toDouble())
        val target = (legTargetBpm ?: cardio.hrTargetBpm)?.takeIf { it > 0 } ?: return null
        return WorkoutTargetBand(target - 5.0, target + 5.0)
    }

    /**
     * Pace over the last [windowSeconds] of the route, seconds per km; null until there is
     * enough of it (a stop or a GPS gap gives null rather than a crawl).
     */
    fun recentPaceSecondsPerKm(points: List<CardioGpsPoint>, nowEpochSeconds: Long, windowSeconds: Long = 30): Double? {
        val recent = points.filter { it.epochSeconds >= nowEpochSeconds - windowSeconds }
        if (recent.size < 3) return null
        val seconds = (recent.last().epochSeconds - recent.first().epochSeconds).toDouble()
        if (seconds < windowSeconds / 2.0) return null
        val meters = CardioGpsMath.pathLengthMeters(recent)
        if (meters < 20.0) return null
        return seconds / (meters / 1000.0)
    }

    /** "Pace 10 42 a mile, speed up" or "Heart rate 168, ease off". */
    fun paceAlert(drift: WorkoutTargetDrift, secondsPerKm: Double, unit: CardioDistanceUnit): String {
        val perUnit = if (unit == CardioDistanceUnit.MILES) secondsPerKm * 1.609344 else secondsPerKm
        val s = perUnit.roundToInt()
        val clock = "${s / 60} ${(s % 60).toString().padStart(2, '0')}"
        val per = if (unit == CardioDistanceUnit.MILES) "a mile" else "a kilometer"
        // A lower pace number is faster.
        val advice = if (drift == WorkoutTargetDrift.TOO_LOW) "ease off" else "speed up"
        return "Pace $clock $per, $advice"
    }

    fun heartRateAlert(drift: WorkoutTargetDrift, bpm: Int): String =
        "Heart rate $bpm, ${if (drift == WorkoutTargetDrift.TOO_HIGH) "ease off" else "pick it up"}"
}
