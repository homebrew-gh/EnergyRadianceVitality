package com.erv.app.bodytracker

import com.erv.app.nostr.LibraryStateMerge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BodyTrackerTrackingTest {

    @Test
    fun lengthUnit_followsWeightUnitUntilPicked() {
        val fresh = BodyTrackerLibraryState()
        assertEquals(BodyMeasurementLengthUnit.INCHES, fresh.effectiveLengthUnit(weightInKg = false))
        assertEquals(BodyMeasurementLengthUnit.CENTIMETERS, fresh.effectiveLengthUnit(weightInKg = true))
        val picked = fresh.copy(lengthUnit = BodyMeasurementLengthUnit.CENTIMETERS, lengthUnitUpdatedAtEpochSeconds = 10)
        assertEquals(BodyMeasurementLengthUnit.CENTIMETERS, picked.effectiveLengthUnit(weightInKg = false))
    }

    @Test
    fun formKeys_trackedPlusSavedThatDay() {
        val state = BodyTrackerLibraryState(trackedMeasurements = listOf("WAIST", "CHEST"))
        val log = BodyTrackerDayLog(date = "2026-11-20", measurementsCm = mapOf("CALF_LEFT" to 38.0))
        assertEquals(
            listOf(BodyMeasurementKey.CHEST, BodyMeasurementKey.WAIST, BodyMeasurementKey.CALF_LEFT),
            state.measurementKeysForForm(log)
        )
        assertEquals(DEFAULT_TRACKED_MEASUREMENTS, BodyTrackerLibraryState().trackedMeasurementKeys())
    }

    @Test
    fun weightTrend_sortedByDate() {
        val state = BodyTrackerLibraryState(
            logs = listOf(
                BodyTrackerDayLog(date = "2026-11-02", weightKg = 82.0),
                BodyTrackerDayLog(date = "2026-10-01", weightKg = 84.0),
                BodyTrackerDayLog(date = "2026-10-15", note = "no weight"),
            )
        )
        assertEquals(
            listOf(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-02")),
            state.weightTrend().map { it.date }
        )
    }

    @Test
    fun merge_newerTrackedListWins() {
        val local = BodyTrackerLibraryState(trackedMeasurements = listOf("WAIST"), trackedMeasurementsUpdatedAtEpochSeconds = 5)
        val remote = BodyTrackerLibraryState(trackedMeasurements = listOf("NECK", "HIPS"), trackedMeasurementsUpdatedAtEpochSeconds = 9)
        assertEquals(listOf("NECK", "HIPS"), LibraryStateMerge.mergeBodyTracker(local, remote).trackedMeasurements)
        assertEquals(listOf("WAIST"), LibraryStateMerge.mergeBodyTracker(local, remote.copy(trackedMeasurementsUpdatedAtEpochSeconds = 1)).trackedMeasurements)
    }
}
