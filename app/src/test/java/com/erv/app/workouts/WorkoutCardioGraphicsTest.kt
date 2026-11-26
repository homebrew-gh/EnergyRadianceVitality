package com.erv.app.workouts

import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioPaceGoal
import com.erv.app.cardio.CardioSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutCardioGraphicsTest {

    private fun leg(index: Int, count: Int, seconds: Int?, recovery: Boolean, itemId: String = "c1", round: Int = 0) =
        WorkoutSessionStep.CardioLeg(
            segmentIndex = 0,
            segmentId = "s1",
            itemId = itemId,
            round = round,
            activity = "STATIONARY_BIKE",
            legIndex = index,
            legCount = count,
            targetSeconds = seconds,
            isRecovery = recovery,
        )

    @Test
    fun intervalStrip_weighsTimedLegsByTime() {
        val steps = listOf(
            leg(0, 4, 60, false),
            leg(1, 4, 30, true),
            leg(2, 4, 60, false),
            leg(3, 4, 30, true),
            leg(0, 1, 600, false, itemId = "other"),
        )
        val strip = WorkoutCardioGraphics.intervalStrip(steps, steps[2])!!
        assertEquals(4, strip.blocks.size)
        assertEquals(2, strip.currentIndex)
        assertEquals(listOf(60f, 30f, 60f, 30f), strip.blocks.map { it.weight })
        assertEquals(listOf(false, true, false, true), strip.blocks.map { it.isRecovery })
    }

    @Test
    fun intervalStrip_onlyThisRound_andNoneForASteadyLeg() {
        val steps = listOf(leg(0, 2, 60, false), leg(1, 2, null, true), leg(0, 2, 60, false, round = 1), leg(1, 2, 60, true, round = 1))
        val strip = WorkoutCardioGraphics.intervalStrip(steps, steps[1])!!
        assertEquals(2, strip.blocks.size)
        assertEquals(1, strip.currentIndex)
        // A leg without a time makes every block the same width.
        assertEquals(listOf(1f, 1f), strip.blocks.map { it.weight })
        assertNull(WorkoutCardioGraphics.intervalStrip(steps, leg(0, 1, 600, false)))
    }

    private fun session(activity: CardioBuiltinActivity, meters: Double?, start: Long, end: Long, minutes: Int = 0) =
        CardioSession(
            activity = CardioActivitySnapshot(builtin = activity, displayLabel = activity.name),
            durationMinutes = minutes,
            distanceMeters = meters,
            startEpochSeconds = start,
            endEpochSeconds = end,
            loggedAtEpochSeconds = end,
        )

    @Test
    fun lastSessionGhost_isTheLatestOfTheSameActivityWithADistance() {
        val state = CardioLibraryState(
            logs = listOf(
                CardioDayLog("2026-11-20", listOf(session(CardioBuiltinActivity.STATIONARY_BIKE, 20_000.0, 1_000, 3_700))),
                CardioDayLog(
                    "2026-11-22",
                    listOf(
                        session(CardioBuiltinActivity.STATIONARY_BIKE, 16_000.0, 5_000, 7_700),
                        // Later, but a run.
                        session(CardioBuiltinActivity.RUN, 5_000.0, 8_000, 9_500),
                        // Later, but no distance.
                        session(CardioBuiltinActivity.STATIONARY_BIKE, null, 10_000, 11_000),
                    ),
                ),
            ),
        )
        assertEquals(
            CardioPaceGoal(distanceMeters = 16_000.0, seconds = 2_700),
            WorkoutCardioGraphics.lastSessionGhost(state, "STATIONARY_BIKE", beforeEpochSeconds = 20_000),
        )
        // Only sessions that finished before this run started.
        assertEquals(
            CardioPaceGoal(distanceMeters = 20_000.0, seconds = 2_700),
            WorkoutCardioGraphics.lastSessionGhost(state, "STATIONARY_BIKE", beforeEpochSeconds = 7_000),
        )
        assertNull(WorkoutCardioGraphics.lastSessionGhost(state, "ROWING", beforeEpochSeconds = 20_000))
    }

    @Test
    fun lastSessionGhost_fallsBackToMinutesWithoutStartAndEnd() {
        val logged = CardioSession(
            activity = CardioActivitySnapshot(builtin = CardioBuiltinActivity.ROWING, displayLabel = "Rowing"),
            durationMinutes = 20,
            distanceMeters = 5_000.0,
            loggedAtEpochSeconds = 1_000,
        )
        val state = CardioLibraryState(logs = listOf(CardioDayLog("2026-11-20", listOf(logged))))
        assertEquals(
            CardioPaceGoal(distanceMeters = 5_000.0, seconds = 1_200),
            WorkoutCardioGraphics.lastSessionGhost(state, "ROWING", beforeEpochSeconds = 2_000),
        )
    }
}
