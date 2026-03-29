package com.erv.app.weighttraining

import com.erv.app.cardio.CardioHrScaffolding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightCalorieEstimatorTest {

    @Test
    fun estimateKcal_prefersHeartRateWhenAvailable() {
        val session = WeightWorkoutSession(
            source = WeightWorkoutSource.LIVE,
            durationSeconds = 45 * 60,
            entries = listOf(
                WeightWorkoutEntry(
                    exerciseId = "bench",
                    sets = List(4) { WeightSet(reps = 10, weightKg = 60.0) }
                ),
                WeightWorkoutEntry(
                    exerciseId = "row",
                    sets = List(4) { WeightSet(reps = 10, weightKg = 55.0) }
                ),
                WeightWorkoutEntry(
                    exerciseId = "squat",
                    sets = List(4) { WeightSet(reps = 10, weightKg = 80.0) }
                )
            ),
            heartRate = CardioHrScaffolding(avgBpm = 145)
        )

        val kcal = WeightCalorieEstimator.estimateKcal(session, bodyWeightKg = 80.0)

        assertEquals(368.4, kcal ?: 0.0, 0.2)
    }

    @Test
    fun estimateKcal_fallsBackToWorkoutStructureWithoutHeartRate() {
        val session = WeightWorkoutSession(
            source = WeightWorkoutSource.LIVE,
            durationSeconds = 30 * 60,
            entries = listOf(
                WeightWorkoutEntry(
                    exerciseId = "swing",
                    hiitBlock = WeightHiitBlockLog(intervals = 12, workSeconds = 40, restSeconds = 20, weightKg = 24.0)
                )
            )
        )

        val kcal = WeightCalorieEstimator.estimateKcal(session, bodyWeightKg = 82.0)

        assertEquals(282.9, kcal ?: 0.0, 0.2)
    }

    @Test
    fun estimateKcal_requiresBodyWeightAndDuration() {
        val session = WeightWorkoutSession(
            source = WeightWorkoutSource.MANUAL,
            entries = listOf(
                WeightWorkoutEntry(
                    exerciseId = "bench",
                    sets = listOf(WeightSet(reps = 5, weightKg = 100.0))
                )
            )
        )

        assertNull(WeightCalorieEstimator.estimateKcal(session, bodyWeightKg = 80.0))
        assertNull(
            WeightCalorieEstimator.estimateKcal(
                session.copy(durationSeconds = 20 * 60),
                bodyWeightKg = null
            )
        )
    }
}
