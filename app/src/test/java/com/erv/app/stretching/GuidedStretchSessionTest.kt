package com.erv.app.stretching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
