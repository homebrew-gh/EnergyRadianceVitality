package com.erv.app.ui.stretching

import com.erv.app.stretching.GuidedStretchResult
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.toGuidedSessionSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidedStretchSessionStateTest {

    private val neck = StretchCatalogEntry(id = "neck", name = "Neck", requiresBothSides = true)
    private val child = StretchCatalogEntry(id = "child", name = "Child's Pose")
    private val entries = listOf(neck, child)

    private fun state() = GuidedStretchSessionState(
        routineId = "r",
        routineName = "Evening",
        entries = entries,
        steps = entries.toGuidedSessionSteps(),
        stepHoldSeconds = listOf(30, 30, 30),
    )

    @Test
    fun settlingInShowsTheFirstStretch() {
        val s = state()
        assertEquals(0, s.focusIndex)
        assertEquals("neck", s.shownStep?.entry?.id)
    }

    @Test
    fun aGapShowsTheNextStretch() {
        val s = state().copy(firstPrepActive = false, phaseHold = false, index = 1)
        assertEquals(2, s.focusIndex)
        assertEquals("child", s.shownStep?.entry?.id)
    }

    @Test
    fun aHoldShowsItself() {
        val s = state().copy(firstPrepActive = false, phaseHold = true, index = 1)
        assertEquals(1, s.focusIndex)
        assertEquals("neck", s.shownStep?.entry?.id)
    }

    @Test
    fun plannedMinutesCountHoldsGapsAndPrep() {
        // 3 holds of 30 s, a 5 s side switch, a 10 s gap and 10 s of settling in: 115 s, rounded up to 2 min.
        assertEquals(2, state().plannedMinutes)
    }

    @Test
    fun plannedMinutesUseEachHoldAndTheGap() {
        // Neck 45 a side, child 120, a 5 s side switch, a 30 s gap and 10 s settling in: 255 s → 5 min.
        val s = state().copy(stepHoldSeconds = listOf(45, 45, 120), gapSeconds = 30)
        assertEquals(5, s.plannedMinutes)
        assertEquals(120, s.holdAt(2))
        assertEquals(30, s.copy(index = 1).gapAfterCurrent)
        assertEquals(5, s.copy(index = 0).gapAfterCurrent)
    }

    @Test
    fun resultFollowsHeldSteps() {
        assertNull(state().result())
        val all = state().copy(heldStepIndexes = setOf(0, 1, 2))
        assertEquals(GuidedStretchResult(listOf("neck", "child"), 2), all.result())
    }
}
