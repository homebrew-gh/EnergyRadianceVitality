package com.erv.app.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ErvMediaSeekTest {
    @Test
    fun clampsBelowZero() {
        assertEquals(0L, ErvMediaControlHelper.clampSeekTarget(-12_000L, 600_000L))
    }

    @Test
    fun clampsPastDuration() {
        assertEquals(600_000L, ErvMediaControlHelper.clampSeekTarget(615_000L, 600_000L))
    }

    @Test
    fun unknownDurationOnlyClampsFloor() {
        assertEquals(915_000L, ErvMediaControlHelper.clampSeekTarget(915_000L, 0L))
    }
}
