package com.erv.app.ui.wellness

import org.junit.Assert.assertEquals
import org.junit.Test

class WellnessTimerFormatTest {

    @Test
    fun clockShowsMinutesAndSeconds() {
        assertEquals("0:00", formatTimerClock(0))
        assertEquals("0:45", formatTimerClock(45))
        assertEquals("15:00", formatTimerClock(15 * 60))
        assertEquals("1:05:09", formatTimerClock(3600 + 5 * 60 + 9))
        assertEquals("0:00", formatTimerClock(-3))
    }

    @Test
    fun lightMinutesRoundAndNeverZero() {
        assertEquals(1, lightMinutesForSeconds(10))
        assertEquals(1, lightMinutesForSeconds(89))
        assertEquals(2, lightMinutesForSeconds(90))
        assertEquals(10, lightMinutesForSeconds(600))
    }
}
