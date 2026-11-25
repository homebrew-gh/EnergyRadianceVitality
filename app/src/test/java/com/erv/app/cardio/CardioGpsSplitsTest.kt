package com.erv.app.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioGpsSplitsTest {

    /** Points due north, [metersPerStep] apart, [secondsPerStep] apart in time. */
    private fun route(steps: Int, metersPerStep: Double, secondsPerStep: Long, start: Long = 1_000L): List<CardioGpsPoint> {
        val degPerMeter = 1.0 / 111_195.0
        return (0..steps).map { i ->
            CardioGpsPoint(lat = 40.0 + i * metersPerStep * degPerMeter, lon = -74.0, epochSeconds = start + i * secondsPerStep)
        }
    }

    @Test
    fun oneSplitPerMileAtAnEvenPace() {
        // 2.5 miles at 10 m every 4 s (about 10:44 per mile).
        val points = route(steps = 403, metersPerStep = 10.0, secondsPerStep = 4)
        val mile = CardioGpsSplits.splitMeters(4, CardioDistanceUnit.MILES)

        val splits = CardioGpsSplits.fromTrack(points, mile)

        assertEquals(2, splits.size)
        assertEquals(644.0, splits[0].segmentElapsedSeconds.toDouble(), 2.0)
        assertEquals(644.0, splits[1].segmentElapsedSeconds.toDouble(), 2.0)
        assertEquals(mile * 2, splits[1].totalDistanceMeters!!, 0.001)
        assertEquals(listOf(0, 1), splits.map { it.orderIndex })
    }

    @Test
    fun aLongGapBetweenFixesAddsNoTime() {
        val first = route(steps = 100, metersPerStep = 10.0, secondsPerStep = 4)
        // A five-minute pause, then the second half.
        val second = route(steps = 100, metersPerStep = 10.0, secondsPerStep = 4, start = first.last().epochSeconds + 300)
            .map { it.copy(lat = it.lat + (first.last().lat - 40.0) + 10.0 / 111_195.0) }
        val splits = CardioGpsSplits.fromTrack(first + second, 1_609.344)

        assertEquals(1, splits.size)
        assertTrue(splits[0].segmentElapsedSeconds < 700)
    }

    @Test
    fun kilometreSplitsAndEdgeCases() {
        assertEquals(1000.0, CardioGpsSplits.splitMeters(4, CardioDistanceUnit.KILOMETERS), 0.0)
        assertEquals(402.336, CardioGpsSplits.splitMeters(1, CardioDistanceUnit.MILES), 0.001)
        assertTrue(CardioGpsSplits.fromTrack(emptyList(), 1000.0).isEmpty())
        assertTrue(CardioGpsSplits.fromTrack(route(10, 10.0, 4), 0.0).isEmpty())
    }
}
