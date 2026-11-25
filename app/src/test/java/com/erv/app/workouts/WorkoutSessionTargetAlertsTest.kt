package com.erv.app.workouts

import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioGpsPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutSessionTargetAlertsTest {

    private val band = WorkoutTargetBand(130.0, 145.0)

    @Test
    fun alertsAfterFifteenSecondsOutThenEveryMinute() {
        val watcher = WorkoutTargetWatcher()
        assertNull(watcher.update(0, 150.0, band))
        assertNull(watcher.update(14, 150.0, band))
        assertEquals(WorkoutTargetDrift.TOO_HIGH, watcher.update(15, 150.0, band))
        assertNull(watcher.update(30, 152.0, band))
        assertEquals(WorkoutTargetDrift.TOO_HIGH, watcher.update(75, 152.0, band))
    }

    @Test
    fun comingBackOrSwitchingSidesStartsOver() {
        val watcher = WorkoutTargetWatcher()
        watcher.update(0, 150.0, band)
        assertNull(watcher.update(10, 140.0, band))
        assertNull(watcher.update(20, 150.0, band))
        assertEquals(WorkoutTargetDrift.TOO_HIGH, watcher.update(35, 150.0, band))
        assertNull(watcher.update(40, 120.0, band))
        assertEquals(WorkoutTargetDrift.TOO_LOW, watcher.update(55, 120.0, band))
        // No reading, no alert, and the count starts again.
        assertNull(watcher.update(60, null, band))
        assertNull(watcher.update(61, 120.0, band))
    }

    @Test
    fun bandsFromTheBuilder() {
        val range = WorkoutSessionTargets.heartRateBand(WorkoutCardioPrescription("RUN", hrTargetMinBpm = 130, hrTargetMaxBpm = 145), null)
        assertEquals(WorkoutTargetBand(130.0, 145.0), range)
        val single = WorkoutSessionTargets.heartRateBand(WorkoutCardioPrescription("RUN", hrTargetBpm = 150), 160)
        assertEquals(WorkoutTargetBand(155.0, 165.0), single)
        assertNull(WorkoutSessionTargets.heartRateBand(WorkoutCardioPrescription("RUN"), null))
        val pace = WorkoutSessionTargets.paceBand(
            WorkoutCardioPrescription("RUN", paceTargetFastSecondsPerKm = 354.0, paceTargetSlowSecondsPerKm = 373.0),
        )
        assertEquals(WorkoutTargetBand(354.0, 373.0), pace)
        assertNull(WorkoutSessionTargets.paceBand(WorkoutCardioPrescription("RUN")))
    }

    @Test
    fun recentPaceFromTheRoute() {
        // About 22.2 m every 5 s: 4.45 m/s, about 225 s per km.
        val now = 1_000L
        val points = (0..12).map { CardioGpsPoint(40.0 + it * 0.0002, -75.0, now - 60 + it * 5L) }
        val pace = WorkoutSessionTargets.recentPaceSecondsPerKm(points, now)!!
        assertEquals(225.0, pace, 3.0)
        // Nothing recent: no pace.
        assertNull(WorkoutSessionTargets.recentPaceSecondsPerKm(points, now + 120))
    }

    @Test
    fun alertWording() {
        assertEquals("Pace 10 42 a mile, speed up", WorkoutSessionTargets.paceAlert(WorkoutTargetDrift.TOO_HIGH, 642.0 / 1.609344, CardioDistanceUnit.MILES))
        assertEquals("Pace 4 30 a kilometer, ease off", WorkoutSessionTargets.paceAlert(WorkoutTargetDrift.TOO_LOW, 270.0, CardioDistanceUnit.KILOMETERS))
        assertEquals("Heart rate 168, ease off", WorkoutSessionTargets.heartRateAlert(WorkoutTargetDrift.TOO_HIGH, 168))
        assertEquals("Heart rate 120, pick it up", WorkoutSessionTargets.heartRateAlert(WorkoutTargetDrift.TOO_LOW, 120))
    }
}
