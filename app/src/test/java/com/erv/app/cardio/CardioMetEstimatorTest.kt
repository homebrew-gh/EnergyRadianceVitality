package com.erv.app.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CardioMetEstimatorTest {

    @Test
    fun estimateKcal_withoutHeartRate_matchesMetBaseline() {
        val session = CardioSession(
            activity = CardioActivitySnapshot(
                builtin = CardioBuiltinActivity.RUN,
                displayLabel = "Running"
            ),
            modality = CardioModality.OUTDOOR,
            durationMinutes = 60
        )

        val kcal = CardioMetEstimator.estimateKcal(session, customMet = null, weightKg = 80.0)

        assertEquals(720.0, kcal ?: 0.0, 0.001)
    }

    @Test
    fun estimateKcal_withHeartRate_blendsMetAndHrEstimate() {
        val session = CardioSession(
            activity = CardioActivitySnapshot(
                builtin = CardioBuiltinActivity.RUN,
                displayLabel = "Running"
            ),
            modality = CardioModality.OUTDOOR,
            durationMinutes = 60,
            heartRate = CardioHrScaffolding(avgBpm = 150)
        )

        val kcal = CardioMetEstimator.estimateKcal(session, customMet = null, weightKg = 80.0)

        assertEquals(734.4, kcal ?: 0.0, 0.001)
    }

    @Test
    fun applyEstimatedKcal_blendsRollupTotalForMultiLegSession() {
        val session = CardioSession(
            activity = CardioActivitySnapshot(displayLabel = "Walk -> Run"),
            modality = CardioModality.OUTDOOR,
            durationMinutes = 60,
            heartRate = CardioHrScaffolding(avgBpm = 150),
            segments = listOf(
                CardioSessionSegment(
                    activity = CardioActivitySnapshot(
                        builtin = CardioBuiltinActivity.WALK,
                        displayLabel = "Walking"
                    ),
                    modality = CardioModality.OUTDOOR,
                    durationMinutes = 30,
                    orderIndex = 0
                ),
                CardioSessionSegment(
                    activity = CardioActivitySnapshot(
                        builtin = CardioBuiltinActivity.RUN,
                        displayLabel = "Running"
                    ),
                    modality = CardioModality.OUTDOOR,
                    durationMinutes = 30,
                    orderIndex = 1
                )
            )
        )

        val estimated = CardioMetEstimator.applyEstimatedKcal(
            session,
            library = CardioLibraryState(),
            weightKg = 80.0
        )

        assertNotNull(estimated.segments[0].estimatedKcal)
        assertNotNull(estimated.segments[1].estimatedKcal)
        assertEquals(140.0, estimated.segments[0].estimatedKcal ?: 0.0, 0.001)
        assertEquals(360.0, estimated.segments[1].estimatedKcal ?: 0.0, 0.001)
        assertEquals(580.4, estimated.estimatedKcal ?: 0.0, 0.001)
    }
}
