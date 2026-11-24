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

    @Test
    fun guided_interval_session_totals_exact_seconds() {
        val run = CardioActivitySnapshot(builtin = CardioBuiltinActivity.RUN, displayLabel = "Running")
        val walk = CardioActivitySnapshot(builtin = CardioBuiltinActivity.ACTIVE_RECOVERY, displayLabel = "Active recovery")
        // 4 x 30 s sprints with 90 s walks between: 2 min of sprints and 4.5 min of walking.
        val legs = buildList {
            repeat(4) { round ->
                add(CardioRoutineStep(activity = run, targetDurationMinutes = 1, targetDurationSeconds = 30, orderIndex = size))
                if (round < 3) add(CardioRoutineStep(activity = walk, targetDurationMinutes = 2, targetDurationSeconds = 90, orderIndex = size))
            }
        }
        var state: CardioMultiLegTimerState? = CardioMultiLegTimerState(
            routineId = null,
            routineName = "Sprints",
            legs = legs,
            completedSegments = emptyList(),
            currentLegIndex = 0,
            workoutStartEpoch = 1_000L,
            legStartedEpoch = 1_000L,
        )
        var session: CardioSession? = null
        var now = 1_000L
        while (state != null) {
            val seconds = state.currentLeg.targetLegSeconds()!!
            now += seconds
            val (next, done) = CardioMetEstimator.advanceMultiLegTimer(
                state,
                legDurationMinutes = (seconds + 59) / 60,
                legEndEpoch = now,
                library = CardioLibraryState(),
                weightKg = 80.0,
                legDurationSeconds = seconds,
            )
            state = next
            session = done
        }
        assertNotNull(session)
        assertEquals(listOf(30, 90, 30, 90, 30, 90, 30), session!!.segments.map { it.durationSeconds })
        // 390 s is 6.5 min, rounded to 7; the old sum of rounded-up legs said 10.
        assertEquals(7, session.durationMinutes)
    }
}
