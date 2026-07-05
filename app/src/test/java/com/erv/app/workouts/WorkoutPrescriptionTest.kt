package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightSetLoggingStyle
import com.erv.app.weighttraining.repsShadowText
import org.junit.Assert.assertEquals
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
            assertNull(set.targetWeightKg)
        }
    }

    @Test
    fun resolvedSets_seeds_weight_target_for_live_ghost() {
        val sets = WorkoutWeightPrescription(
            setCount = 3,
            targetReps = 8,
            targetWeightKg = 60.0,
        ).resolvedSets(loggingStyle = WeightSetLoggingStyle.REPS)

        assertEquals(3, sets.size)
        sets.forEach { set ->
            assertEquals(0, set.reps)
            assertNull(set.weightKg)
            assertEquals(8, set.targetReps)
            assertEquals(60.0, set.targetWeightKg!!, 0.001)
        }
    }

    @Test
    fun resolvedSets_normalizes_prescription_set_weight_to_ghost() {
        val sets = WorkoutWeightPrescription(
            sets = listOf(
                WeightSet(reps = 8, weightKg = 72.5),
                WeightSet(reps = 8, weightKg = 72.5),
            ),
        ).resolvedSets(loggingStyle = WeightSetLoggingStyle.REPS)

        assertEquals(2, sets.size)
        sets.forEach { set ->
            assertEquals(0, set.reps)
            assertNull(set.weightKg)
            assertEquals(8, set.targetReps)
            assertEquals(72.5, set.targetWeightKg!!, 0.001)
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

    @Test
    fun resolvedSets_seeds_rep_range_label_for_live_ghost() {
        val sets = WorkoutWeightPrescription(
            setCount = 3,
            repRangeMin = 5,
            repRangeMax = 8,
        ).resolvedSets(loggingStyle = WeightSetLoggingStyle.REPS)

        assertEquals(3, sets.size)
        sets.forEach { set ->
            assertEquals(0, set.reps)
            assertNull(set.targetReps)
            assertEquals("5-8", set.targetRepsRangeLabel)
            assertEquals("5-8", set.repsShadowText())
        }
    }

    @Test
    fun repRangeLabel_returns_null_when_min_equals_max() {
        assertNull(
            WorkoutWeightPrescription(repRangeMin = 8, repRangeMax = 8).repRangeLabel(),
        )
    }

    @Test
    fun effectiveTimedPrepSeconds_returns_zero_when_unset() {
        assertEquals(0, WorkoutWeightPrescription(durationSeconds = 45).effectiveTimedPrepSeconds())
        assertEquals(10, WorkoutWeightPrescription(timedPrepSeconds = 10).effectiveTimedPrepSeconds())
    }

    @Test
    fun defaultWorkoutPrescriptionForExercise_timeOnly_includes_prep() {
        val exercise = com.erv.app.weighttraining.WeightExercise(
            name = "Carry",
            muscleGroup = "core",
            pushOrPull = com.erv.app.weighttraining.WeightPushPull.PULL,
            equipment = com.erv.app.weighttraining.WeightEquipment.DUMBBELL,
            timePerSetCapable = true,
            repPerSetCapable = false,
        )
        val prescription = defaultWorkoutPrescriptionForExercise(exercise, WorkoutSegmentKind.STRAIGHT_SETS)
        assertEquals(DEFAULT_TIMED_PREP_SECONDS, prescription.timedPrepSeconds)
    }
}
