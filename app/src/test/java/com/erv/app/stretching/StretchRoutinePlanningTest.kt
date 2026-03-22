package com.erv.app.stretching

import org.junit.Assert.assertEquals
import org.junit.Test

class StretchRoutinePlanningTest {

    @Test
    fun toGuidedSessionSteps_expandsBilateralToRightThenLeft() {
        val bilateral = StretchCatalogEntry(id = "a", name = "A", requiresBothSides = true)
        val single = StretchCatalogEntry(id = "b", name = "B", requiresBothSides = false)
        val steps = listOf(bilateral, single).toGuidedSessionSteps()
        assertEquals(3, steps.size)
        assertEquals(StretchSide.RIGHT, steps[0].side)
        assertEquals(StretchSide.LEFT, steps[1].side)
        assertEquals(null, steps[2].side)
    }

    @Test
    fun holdSideSegments_isTwoWhenBilateral() {
        val bilateral = StretchCatalogEntry(
            id = "x",
            name = "Test",
            requiresBothSides = true
        )
        val single = StretchCatalogEntry(
            id = "y",
            name = "Test2",
            requiresBothSides = false
        )
        assertEquals(2, bilateral.holdSideSegments())
        assertEquals(1, single.holdSideSegments())
    }

    @Test
    fun routineHoldSegments_sumsSegments() {
        val catalog = listOf(
            StretchCatalogEntry(id = "a", name = "A", requiresBothSides = true),
            StretchCatalogEntry(id = "b", name = "B", requiresBothSides = false)
        )
        assertEquals(3, routineHoldSegments(listOf("a", "b"), catalog))
        assertEquals(1, routineHoldSegments(listOf("missing"), catalog))
    }
}
