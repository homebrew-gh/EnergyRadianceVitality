package com.erv.app.workouts

import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.poundsToKg
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutPrescriptionSummaryTest {
    private val prescription = WorkoutWeightPrescription(
        setCount = 3,
        targetReps = 5,
        targetWeightKg = poundsToKg(135.0),
    )

    @Test
    fun targetWeightShowsInPoundsForPoundLifters() {
        assertEquals("3 sets · 5 reps · 135 lb", prescription.displaySummary(BodyWeightUnit.LB))
    }

    @Test
    fun targetWeightKeepsKilogramsWhenChosen() {
        assertEquals("3 sets · 5 reps · 61.23 kg", prescription.displaySummary(BodyWeightUnit.KG))
    }
}
