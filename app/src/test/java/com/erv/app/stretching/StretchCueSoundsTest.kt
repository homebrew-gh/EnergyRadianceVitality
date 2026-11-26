package com.erv.app.stretching

import com.erv.app.data.StretchSoundStyle
import com.erv.app.data.parseStretchSoundStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StretchCueSoundsTest {

    private fun cuesOverHold(style: StretchSoundStyle, hold: Int): List<Pair<Int, StretchCue>> =
        (hold downTo 1).mapNotNull { left -> stretchCueForSecondLeft(style, hold, left)?.let { left to it } }

    @Test
    fun classicKeepsFiveTicks() {
        val cues = cuesOverHold(StretchSoundStyle.CLASSIC, 30)
        assertEquals((5 downTo 1).map { it to StretchCue.COUNTDOWN_TICK }, cues)
    }

    @Test
    fun classicShortHoldTicksOnlyItsLength() {
        assertEquals(3, cuesOverHold(StretchSoundStyle.CLASSIC, 3).size)
    }

    @Test
    fun calmStylesGiveOneHeadsUp() {
        for (style in listOf(
            StretchSoundStyle.SOFT_CHIME,
            StretchSoundStyle.SINGING_BOWL,
            StretchSoundStyle.WOOD_BLOCK,
            StretchSoundStyle.VIBRATE_ONLY,
        )) {
            assertEquals(listOf(5 to StretchCue.HOLD_ALMOST_DONE), cuesOverHold(style, 30))
        }
    }

    @Test
    fun shortHoldsGetNoHeadsUp() {
        assertTrue(cuesOverHold(StretchSoundStyle.SOFT_CHIME, 10).isEmpty())
        assertEquals(1, cuesOverHold(StretchSoundStyle.SOFT_CHIME, 15).size)
    }

    @Test
    fun noCueAtZero() {
        assertNull(stretchCueForSecondLeft(StretchSoundStyle.CLASSIC, 30, 0))
    }

    @Test
    fun synthesizedCuesAreQuietAndEndInSilence() {
        for (style in StretchSoundStyle.entries.filter { it.isSynthesized }) {
            for (cue in StretchCue.entries) {
                val pcm = synthesizeStretchCue(style, cue)
                assertTrue("$style $cue empty", pcm.isNotEmpty())
                val peak = pcm.maxOf { abs(it.toInt()) }
                // Never louder than the cap (about a third of full scale).
                assertTrue("$style $cue too loud: $peak", peak <= (STRETCH_CUE_PEAK * Short.MAX_VALUE).toInt() + 1)
                assertTrue("$style $cue silent", peak > 1_000)
                assertEquals("$style $cue starts with a click", 0, pcm.first().toInt())
                assertEquals("$style $cue ends with a click", 0, pcm.last().toInt())
                assertTrue("$style $cue too long", pcm.size < STRETCH_CUE_SAMPLE_RATE * 12)
            }
        }
    }

    @Test
    fun volumeScalesThePeak() {
        val start = synthesizeStretchCue(StretchSoundStyle.SOFT_CHIME, StretchCue.HOLD_START, volume = 0.5)
        val peak = start.maxOf { abs(it.toInt()) }
        assertTrue(peak <= (STRETCH_CUE_PEAK * 0.5 * Short.MAX_VALUE).toInt() + 1)
    }

    @Test
    fun classicAndVibrateHaveNoSamples() {
        assertTrue(synthesizeStretchCue(StretchSoundStyle.CLASSIC, StretchCue.HOLD_END).isEmpty())
        assertTrue(synthesizeStretchCue(StretchSoundStyle.VIBRATE_ONLY, StretchCue.HOLD_END).isEmpty())
    }

    @Test
    fun vibrationPatternsStartWithPause() {
        for (cue in StretchCue.entries) {
            val p = stretchCueVibrationPattern(cue)
            assertEquals(0L, p.first())
            assertTrue(p.size % 2 == 0)
        }
    }

    @Test
    fun parseDefaultsToSoftChime() {
        assertEquals(StretchSoundStyle.SOFT_CHIME, parseStretchSoundStyle(null))
        assertEquals(StretchSoundStyle.SOFT_CHIME, parseStretchSoundStyle("nope"))
        assertEquals(StretchSoundStyle.WOOD_BLOCK, parseStretchSoundStyle("wood_block"))
    }
}
