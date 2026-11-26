package com.erv.app.stretching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.erv.app.data.StretchGuidanceDetail
import com.erv.app.data.parseStretchGuidanceDetail
import org.junit.Test

class GuidedStretchSessionTest {

    private val neck = StretchCatalogEntry(id = "neck", name = "Neck", requiresBothSides = true)
    private val child = StretchCatalogEntry(id = "child", name = "Child's Pose")
    private val quad = StretchCatalogEntry(id = "quad", name = "Quad", requiresBothSides = true)
    private val entries = listOf(neck, child, quad)
    // neck R, neck L, child, quad R, quad L
    private val steps = entries.toGuidedSessionSteps()

    @Test
    fun backGoesToPreviousEarlyAndRestartsLater() {
        assertEquals(1, guidedBackTarget(2, inHold = true, secondsIntoHold = 2))
        assertEquals(2, guidedBackTarget(2, inHold = true, secondsIntoHold = 10))
        assertEquals(1, guidedBackTarget(2, inHold = false, secondsIntoHold = 0))
        assertEquals(0, guidedBackTarget(0, inHold = true, secondsIntoHold = 20))
    }

    @Test
    fun skipGoesForwardUntilTheEnd() {
        assertEquals(1, guidedSkipTarget(0, steps.size))
        assertNull(guidedSkipTarget(steps.lastIndex, steps.size))
    }

    @Test
    fun nothingHeldLogsNothing() {
        assertNull(guidedStretchResult(steps, emptySet(), entries.map { it.id }, 5, 40))
    }

    @Test
    fun allHeldLogsTheWholeRoutineAndPlannedLength() {
        val r = guidedStretchResult(steps, steps.indices.toSet(), entries.map { it.id }, 5, 999)
        assertEquals(GuidedStretchResult(listOf("neck", "child", "quad"), 5), r)
    }

    @Test
    fun partialLogsHeldStretchesInOrderAndTimeSpent() {
        // Held the neck's right side and the quad's left side, skipped the rest.
        val r = guidedStretchResult(steps, setOf(4, 0), entries.map { it.id }, 5, 61)
        assertEquals(GuidedStretchResult(listOf("neck", "quad"), 2), r)
    }

    @Test
    fun partialNeverLogsZeroMinutes() {
        val r = guidedStretchResult(steps, setOf(2), entries.map { it.id }, 5, 0)
        assertEquals(1, r?.totalMinutes)
    }

    @Test
    fun bothSidesShareOneDot() {
        assertEquals(listOf(0, 0, 1, 2, 2), guidedEntryPositions(steps))
    }

    @Test
    fun repeatedStretchGetsItsOwnDot() {
        val again = listOf(child, child).toGuidedSessionSteps()
        assertEquals(listOf(0, 1), guidedEntryPositions(again))
    }

    @Test
    fun secondsRemainingCountsHoldsAndGaps() {
        // In the first hold with 10 s left; 4 more holds of 30 and 4 gaps of 5.
        assertEquals(10 + 4 * 35, guidedSecondsRemaining(steps, 30, 0, inHold = true, secondsLeftInPhase = 10) { _, _ -> 5 })
        // In the gap before the last step: 3 s of gap plus its whole hold.
        assertEquals(3 + 30, guidedSecondsRemaining(steps, 30, 4, inHold = false, secondsLeftInPhase = 3) { _, _ -> 5 })
    }

    @Test
    fun guidanceDetailRules() {
        // Second side is never re-read.
        assertFalse(guidedReadsProcedure(StretchGuidanceDetail.FULL, steps, 1))
        assertTrue(guidedReadsProcedure(StretchGuidanceDetail.FULL, steps, 2))
        assertFalse(guidedReadsProcedure(StretchGuidanceDetail.NAMES_ONLY, steps, 0))
        val again = listOf(child, neck, child).toGuidedSessionSteps()
        assertTrue(guidedReadsProcedure(StretchGuidanceDetail.FIRST_TIME, again, 0))
        assertFalse(guidedReadsProcedure(StretchGuidanceDetail.FIRST_TIME, again, 3))
        assertTrue(guidedReadsProcedure(StretchGuidanceDetail.FULL, again, 3))
    }

    @Test
    fun guidanceDetailDefaultsToFull() {
        assertEquals(StretchGuidanceDetail.FULL, parseStretchGuidanceDetail(null))
        assertEquals(StretchGuidanceDetail.NAMES_ONLY, parseStretchGuidanceDetail("names_only"))
    }
}
