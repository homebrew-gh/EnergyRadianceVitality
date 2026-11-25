package com.erv.app.workouts

import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioGpsSplits
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioTimerSessionDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionLapsTest {

    private val now = 1_764_000_000L

    private fun running(): WorkoutActiveRun {
        val draft = CardioTimerSessionDraft.fromQuickSnapshot(
            activity = CardioActivitySnapshot(builtin = CardioBuiltinActivity.RUN, displayLabel = "Run"),
            modality = CardioModality.OUTDOOR,
            treadmill = null,
            title = "Run",
        )
        val workout = standaloneCardioWorkout(draft, now)
        return standaloneCardioRun(workout, draft, now)
            .copy(sessionStepAwaitingStart = false, sessionStepStartedAtEpochSeconds = now)
    }

    @Test
    fun lapsMarkTheItemsTimeAndIgnoreRepeatsAndPauses() {
        val run = running()
        assertNull(run.copy(sessionStepAwaitingStart = true).withSessionLap(now + 60).sessionCardioLaps.firstOrNull())
        val one = run.withSessionLap(now + 300)
        val lap = one.sessionCardioLaps.single()
        assertEquals(300, lap.elapsedSeconds)
        assertEquals(now + 300, lap.atEpochSeconds)
        // Same second again: nothing.
        assertEquals(one, one.withSessionLap(now + 300))
        // Paused: no lap, and the pause is left out of the next one.
        val paused = one.withSessionStepPaused(now + 400)
        assertEquals(paused, paused.withSessionLap(now + 450))
        val two = paused.withSessionStepResumed(now + 460).withSessionLap(now + 660)
        assertEquals(listOf(300, 600), two.sessionCardioLaps.map { it.elapsedSeconds })
    }

    @Test
    fun lapsBecomeSplitsWithRouteDistance() {
        // About 22 m between fixes, a fix every 10 s.
        val points = (0..90).map { CardioGpsPoint(lat = 40.0 + it * 0.0002, lon = -75.0, epochSeconds = now + it * 10L) }
        val laps = listOf(
            WorkoutSessionLap("s", "i", 300, now + 300),
            WorkoutSessionLap("s", "i", 600, now + 600),
        )
        val splits = sessionLapSplits(laps, totalSeconds = 900, finishedAtEpochSeconds = now + 900, gpsPoints = points)
        assertEquals(listOf(300, 600, 900), splits.map { it.elapsedSeconds })
        assertEquals(listOf(300, 300, 300), splits.map { it.segmentElapsedSeconds })
        val first = CardioGpsSplits.distanceAtEpoch(points, now + 300)
        assertEquals(first, splits[0].totalDistanceMeters!!, 0.01)
        assertEquals(first, splits[1].segmentDistanceMeters!!, 1.0)
        assertTrue(splits[2].totalDistanceMeters!! > splits[1].totalDistanceMeters!!)
    }

    @Test
    fun lapsWithoutARouteKeepTimesOnly() {
        val laps = listOf(WorkoutSessionLap("s", "i", 120, now + 120))
        val splits = sessionLapSplits(laps, totalSeconds = 120, finishedAtEpochSeconds = now + 120)
        assertEquals(1, splits.size)
        assertNull(splits.single().totalDistanceMeters)
    }

    @Test
    fun routeDistanceAtATimeInterpolates() {
        val points = listOf(
            CardioGpsPoint(40.0, -75.0, now),
            CardioGpsPoint(40.001, -75.0, now + 10),
        )
        val whole = CardioGpsSplits.distanceAtEpoch(points, now + 10)
        assertEquals(whole / 2, CardioGpsSplits.distanceAtEpoch(points, now + 5), 0.01)
        assertEquals(0.0, CardioGpsSplits.distanceAtEpoch(points, now - 5), 0.0)
    }
}
