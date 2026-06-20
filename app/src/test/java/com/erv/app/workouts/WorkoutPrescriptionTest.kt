package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSetLoggingStyle
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutPrescriptionTest {

    @Test
    fun resolvedSets_seeds_empty_logged_values_with_rep_target() {
        val sets = WorkoutWeightPrescription(
            setCount = 3,
            targetReps = 10,
        ).resolvedSets(loggingStyle = WeightSetLoggingStyle.REPS)

        assertEquals(3, sets.size)
        sets.forEach { set ->
            assertEquals(0, set.reps)
            assertNull(set.durationSeconds)
            assertEquals(10, set.targetReps)
            assertNull(set.targetDurationSeconds)
        }
    }

    @Test
    fun resolvedSets_seeds_duration_target_for_timed_exercises() {
        val sets = WorkoutWeightPrescription(
            setCount = 2,
            durationSeconds = 45,
            mode = WorkoutWeightPrescriptionMode.TIME_BASED,
        ).resolvedSets(loggingStyle = WeightSetLoggingStyle.TIME_ONLY)

        assertEquals(2, sets.size)
        sets.forEach { set ->
            assertEquals(0, set.reps)
            assertNull(set.durationSeconds)
            assertNull(set.targetReps)
            assertEquals(45, set.targetDurationSeconds)
        }
    }

    @Test
    fun resolvedSets_no_targets_when_unset() {
        val sets = WorkoutWeightPrescription(setCount = 1).resolvedSets()
        assertNull(sets.single().targetReps)
        assertNull(sets.single().targetDurationSeconds)
    }

    @Test
    fun effectiveTargetReps_prefers_explicit_target() {
        val reps = WorkoutWeightPrescription(
            targetReps = 8,
            repRangeMin = 5,
            repRangeMax = 12,
        ).effectiveTargetReps()
        assertEquals(8, reps)
    }
}
