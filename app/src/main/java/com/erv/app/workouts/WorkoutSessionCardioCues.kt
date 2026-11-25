package com.erv.app.workouts

import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioWorkoutSplit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the session screen says out loud on a cardio leg: "Mile 2, 9 minutes 42, heart rate 152".
 * Pure, so the wording is unit tested; the screen speaks it and buzzes.
 */
object WorkoutSessionCardioCues {

    private const val METERS_PER_MILE = 1609.344

    /** A split from the route, named by its distance when it is a whole mile or kilometer. */
    fun splitCue(split: CardioWorkoutSplit, unit: CardioDistanceUnit, heartRateBpm: Int?): String {
        val number = split.orderIndex + 1
        val length = split.segmentDistanceMeters
        val name = when {
            length != null && unit == CardioDistanceUnit.MILES && abs(length - METERS_PER_MILE) < 1.0 -> "Mile $number"
            length != null && unit == CardioDistanceUnit.KILOMETERS && abs(length - 1000.0) < 1.0 -> "Kilometer $number"
            else -> split.totalDistanceMeters?.let { "Split $number, ${spokenDistance(it, unit)}" } ?: "Split $number"
        }
        return join(name, spokenClock(split.segmentElapsedSeconds), heartRate(heartRateBpm))
    }

    /** A lap tapped on the card or in the notification. */
    fun lapCue(lapNumber: Int, lapSeconds: Int, heartRateBpm: Int?): String =
        join("Lap $lapNumber", spokenClock(lapSeconds), heartRate(heartRateBpm))

    /** A new leg of an interval item starting: "Work, leg 3 of 8, 1 minute" or "Recovery, 30 seconds". */
    fun legStartCue(step: WorkoutSessionStep.CardioLeg): String? {
        if (step.legCount <= 1) return null
        val name = if (step.isRecovery) "Recovery" else step.label?.takeIf { it.isNotBlank() } ?: "Work"
        val position = if (step.isRecovery) null else "leg ${step.legIndex + 1} of ${step.legCount}"
        return join(name, position, step.targetSeconds?.takeIf { it > 0 }?.let(::spokenClock))
    }

    /** Halfway through a timed leg worth marking (two minutes or more). */
    fun halfwaySecond(step: WorkoutSessionStep.CardioLeg): Int? =
        step.targetSeconds?.takeIf { it >= 120 }?.let { it / 2 }

    /** "9 minutes 42", "45 seconds", "1 hour 2 minutes". */
    fun spokenClock(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return when {
            hours > 0 -> listOfNotNull(
                "$hours ${if (hours == 1) "hour" else "hours"}",
                minutes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "minute" else "minutes"}" },
            ).joinToString(" ")
            minutes > 0 && seconds == 0 -> "$minutes ${if (minutes == 1) "minute" else "minutes"}"
            minutes > 0 -> "$minutes ${if (minutes == 1) "minute" else "minutes"} $seconds"
            else -> "$seconds ${if (seconds == 1) "second" else "seconds"}"
        }
    }

    private fun spokenDistance(meters: Double, unit: CardioDistanceUnit): String {
        val value = if (unit == CardioDistanceUnit.MILES) meters / METERS_PER_MILE else meters / 1000.0
        val hundredths = (value * 100).roundToInt()
        val text = if (hundredths % 100 == 0) "${hundredths / 100}" else {
            val whole = hundredths / 100
            val rest = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
            "$whole.$rest"
        }
        val noun = if (unit == CardioDistanceUnit.MILES) "miles" else "kilometers"
        return "$text $noun"
    }

    private fun heartRate(bpm: Int?): String? = bpm?.takeIf { it > 0 }?.let { "heart rate $it" }

    private fun join(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
}
