package com.erv.app.hr

import com.erv.app.cardio.CardioHrSample
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateAnalyticsTest {

    @Test
    fun estimateMaxHrFromAge_uses220MinusAge() {
        assertEquals(190, estimateMaxHrFromAge(30))
        assertEquals(160, estimateMaxHrFromAge(60))
    }

    @Test
    fun heartRateZoneIndex_percentMax_mapsZones() {
        assertEquals(1, heartRateZoneIndex(100, 200, method = HeartRateZoneMethod.PERCENT_MAX_HR))
        assertEquals(2, heartRateZoneIndex(130, 200, method = HeartRateZoneMethod.PERCENT_MAX_HR))
        assertEquals(5, heartRateZoneIndex(190, 200, method = HeartRateZoneMethod.PERCENT_MAX_HR))
    }

    @Test
    fun resolvedMaxHr_prefersManualOverAge() {
        val samples = listOf(CardioHrSample(1L, 150))
        assertEquals(185, resolvedMaxHrForZones(185, samples, 40))
        assertEquals(180, resolvedMaxHrForZones(null, samples, 40))
    }

    @Test
    fun zoneDurationsSeconds_assignsIntervals() {
        val samples = listOf(
            CardioHrSample(0L, 100),
            CardioHrSample(60L, 140),
            CardioHrSample(120L, 180),
        )
        val zones = zoneDurationsSeconds(samples, 200)
        assertEquals(60, zones[0])
        assertEquals(60, zones[2])
        assertEquals(60, zones[4])
    }

    @Test
    fun karvonenZone_usesRestingHr() {
        val zone = heartRateZoneIndex(
            bpm = 130,
            maxHr = 200,
            restingBpm = 60,
            method = HeartRateZoneMethod.KARVONEN_HRR,
        )
        assertEquals(2, zone)
    }

    @Test
    fun heartRateWithinZoneFraction_placesTheReadingInsideItsZone() {
        // Max 200: zone 2 is 120-139 bpm.
        assertEquals(0f, heartRateWithinZoneFraction(120, 2, 200), 0.001f)
        assertEquals(10f / 19, heartRateWithinZoneFraction(130, 2, 200), 0.001f)
        assertEquals(1f, heartRateWithinZoneFraction(139, 2, 200), 0.001f)
        // The open top zone (180 and up) counts as 20 bpm wide.
        assertEquals(0.5f, heartRateWithinZoneFraction(190, 5, 200), 0.001f)
    }
}
