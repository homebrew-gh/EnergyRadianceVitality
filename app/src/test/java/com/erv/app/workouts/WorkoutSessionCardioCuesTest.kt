package com.erv.app.workouts

import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioWorkoutSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSessionCardioCuesTest {

    private val mile = 1609.344

    private fun leg(index: Int, count: Int, recovery: Boolean = false, seconds: Int? = 60, label: String? = null) =
        WorkoutSessionStep.CardioLeg(
            segmentIndex = 0,
            segmentId = "s",
            itemId = "i",
            round = 1,
            activity = "RUN",
            legIndex = index,
            legCount = count,
            targetSeconds = seconds,
            isRecovery = recovery,
            label = label,
        )

    @Test
    fun mileSplitsAreNamedByTheMile() {
        val split = CardioWorkoutSplit(1, 1164, 582, totalDistanceMeters = 2 * mile, segmentDistanceMeters = mile)
        assertEquals("Mile 2, 9 minutes 42, heart rate 152", WorkoutSessionCardioCues.splitCue(split, CardioDistanceUnit.MILES, 152))
        val km = CardioWorkoutSplit(0, 300, 300, totalDistanceMeters = 1000.0, segmentDistanceMeters = 1000.0)
        assertEquals("Kilometer 1, 5 minutes", WorkoutSessionCardioCues.splitCue(km, CardioDistanceUnit.KILOMETERS, null))
    }

    @Test
    fun otherSplitsSayTheDistanceSoFar() {
        val quarter = CardioWorkoutSplit(2, 360, 120, totalDistanceMeters = 0.75 * mile, segmentDistanceMeters = 0.25 * mile)
        assertEquals("Split 3, 0.75 miles, 2 minutes", WorkoutSessionCardioCues.splitCue(quarter, CardioDistanceUnit.MILES, 0))
    }

    @Test
    fun lapsAndLegs() {
        assertEquals("Lap 3, 45 seconds, heart rate 160", WorkoutSessionCardioCues.lapCue(3, 45, 160))
        assertNull(WorkoutSessionCardioCues.legStartCue(leg(0, 1)))
        assertEquals("Work, leg 3 of 8, 1 minute", WorkoutSessionCardioCues.legStartCue(leg(2, 8)))
        assertEquals("Recovery, 30 seconds", WorkoutSessionCardioCues.legStartCue(leg(3, 8, recovery = true, seconds = 30)))
        assertEquals("Hill, leg 1 of 4", WorkoutSessionCardioCues.legStartCue(leg(0, 4, seconds = null, label = "Hill")))
    }

    @Test
    fun halfwayOnlyOnLongTimedLegs() {
        assertEquals(300, WorkoutSessionCardioCues.halfwaySecond(leg(0, 1, seconds = 600)))
        assertNull(WorkoutSessionCardioCues.halfwaySecond(leg(0, 1, seconds = 90)))
        assertNull(WorkoutSessionCardioCues.halfwaySecond(leg(0, 1, seconds = null)))
    }

    @Test
    fun spokenClock() {
        assertEquals("1 hour 2 minutes", WorkoutSessionCardioCues.spokenClock(3_725))
        assertEquals("2 hours", WorkoutSessionCardioCues.spokenClock(7_200))
        assertEquals("1 minute 5", WorkoutSessionCardioCues.spokenClock(65))
        assertEquals("1 second", WorkoutSessionCardioCues.spokenClock(1))
    }
}
