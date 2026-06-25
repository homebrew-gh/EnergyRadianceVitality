package com.erv.app.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardioGpsElevationTest {

    @Test
    fun computeGainLossMeters_accumulatesGradualClimbsAndDescents() {
        val points = listOf(100.0, 102.0, 104.0, 106.0, 108.0, 106.0, 104.0, 102.0, 100.0)
            .mapIndexed { index, altitude ->
                CardioGpsPoint(
                    lat = 0.0,
                    lon = 0.0,
                    epochSeconds = index.toLong(),
                    altitudeMeters = altitude
                )
            }

        val result = CardioGpsElevation.computeGainLossMeters(points)

        // The 3-sample moving average blunts this synthetic triangular profile: the raw
        // 8 m climb/descent reads as 17/3 ≈ 5.67 m (peak 108 -> 106.67, base 100 -> 101).
        // Real GPS tracks are far denser, so the smoothing has a negligible effect there.
        val gain = result?.first ?: 0.0
        val loss = result?.second ?: 0.0
        assertEquals(17.0 / 3.0, gain, 0.001)
        assertEquals(gain, loss, 0.001)
    }

    @Test
    fun computeGainLossMeters_ignoresSmallNoise() {
        val points = listOf(100.0, 101.0, 100.5, 101.5, 100.8, 101.2)
            .mapIndexed { index, altitude ->
                CardioGpsPoint(
                    lat = 0.0,
                    lon = 0.0,
                    epochSeconds = index.toLong(),
                    altitudeMeters = altitude
                )
            }

        val result = CardioGpsElevation.computeGainLossMeters(points)

        assertEquals(0.0, result?.first ?: 0.0, 0.001)
        assertEquals(0.0, result?.second ?: 0.0, 0.001)
    }

    @Test
    fun computeGainLossMeters_returnsNullWithoutEnoughAltitudeSamples() {
        val points = listOf(
            CardioGpsPoint(lat = 0.0, lon = 0.0, epochSeconds = 1L, altitudeMeters = null),
            CardioGpsPoint(lat = 0.0, lon = 0.0, epochSeconds = 2L, altitudeMeters = 100.0)
        )

        assertNull(CardioGpsElevation.computeGainLossMeters(points))
    }
}
