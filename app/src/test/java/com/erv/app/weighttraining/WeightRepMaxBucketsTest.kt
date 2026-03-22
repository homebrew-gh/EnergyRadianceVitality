package com.erv.app.weighttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightRepMaxBucketsTest {

    @Test
    fun maxWeightByRepBucket_picksMaxPerBucket_andGroups11Plus() {
        val exId = "ex1"
        val log = WeightDayLog(
            date = "2025-01-01",
            workouts = listOf(
                WeightWorkoutSession(
                    entries = listOf(
                        WeightWorkoutEntry(
                            exerciseId = exId,
                            sets = listOf(
                                WeightSet(reps = 1, weightKg = 90.0),
                                WeightSet(reps = 1, weightKg = 100.0),
                                WeightSet(reps = 5, weightKg = 80.0),
                                WeightSet(reps = 10, weightKg = 60.0),
                                WeightSet(reps = 12, weightKg = 55.0),
                                WeightSet(reps = 15, weightKg = 50.0)
                            )
                        )
                    )
                )
            )
        )
        val state = WeightLibraryState(logs = listOf(log))
        val rows = state.maxWeightByRepBucketKg(exId)
        assertEquals(11, rows.size)
        assertEquals(100.0, rows[0].second!!, 0.001)
        assertNull(rows[1].second)
        assertNull(rows[2].second)
        assertNull(rows[3].second)
        assertEquals(80.0, rows[4].second!!, 0.001)
        assertEquals(60.0, rows[9].second!!, 0.001)
        assertEquals(55.0, rows[10].second!!, 0.001)
    }

    @Test
    fun maxWeightByRepBucket_ignoresZeroRepsOrMissingWeight() {
        val exId = "ex1"
        val log = WeightDayLog(
            date = "2025-01-02",
            workouts = listOf(
                WeightWorkoutSession(
                    entries = listOf(
                        WeightWorkoutEntry(
                            exerciseId = exId,
                            sets = listOf(
                                WeightSet(reps = 3, weightKg = null),
                                WeightSet(reps = 0, weightKg = 100.0)
                            )
                        )
                    )
                )
            )
        )
        val state = WeightLibraryState(logs = listOf(log))
        val rows = state.maxWeightByRepBucketKg(exId)
        assertEquals(true, rows.all { it.second == null })
    }
}
