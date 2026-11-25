package com.erv.app.workouts

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutCardioDistanceTargetsTest {

    private fun workoutOf(cardio: WorkoutCardioPrescription) = Workout(
        id = "w",
        name = "Run",
        segments = listOf(
            WorkoutSegment(id = "s", kind = WorkoutSegmentKind.CARDIO, items = listOf(WorkoutItem.Cardio(id = "i", cardio = cardio))),
        ),
        createdAtEpochSeconds = 0,
        lastModifiedEpochSeconds = 0,
    )

    private fun legs(cardio: WorkoutCardioPrescription) =
        WorkoutSessionPlan.from(workoutOf(cardio)).steps.filterIsInstance<WorkoutSessionStep.CardioLeg>()

    @Test
    fun aSteadyDistanceTargetCountsUpToTheDistance() {
        val leg = legs(WorkoutCardioPrescription(activity = "RUN", targetMinutes = 30, targetDistanceMeters = 1609.344)).single()
        assertNull(leg.targetSeconds)
        assertEquals(1609.344, leg.targetMeters!!, 0.001)
        val timed = legs(WorkoutCardioPrescription(activity = "RUN", targetMinutes = 30)).single()
        assertEquals(1800, timed.targetSeconds)
        assertNull(timed.targetMeters)
    }

    @Test
    fun distanceLegsRunAlongsideTimedOnes() {
        val cardio = WorkoutCardioPrescription(
            activity = "RUN",
            mode = "interval_template",
            outerRounds = 2,
            legs = listOf(
                WorkoutCardioIntervalLeg(workSeconds = 0, distanceMeters = 400.0, restSeconds = 90),
                WorkoutCardioIntervalLeg(workSeconds = 60, restSeconds = 0),
            ),
        )
        val steps = legs(cardio)
        // 400 m, rest, 1:00, then again without the last rest.
        assertEquals(listOf(null, 90, 60, null, 90, 60), steps.map { it.targetSeconds })
        assertEquals(400.0, steps[0].targetMeters!!, 0.0)
        assertNull(steps[2].targetMeters)
    }

    @Test
    fun webRowsWithTheNewFieldsDecodeAndOldOnesStillDo() {
        val json = Json { ignoreUnknownKeys = true }
        val fromWeb = """{"activity":"RUN","targetDistanceMeters":5000,"paceTargetFastSecondsPerKm":354.2,"paceTargetSlowSecondsPerKm":372.8,
            "legs":[{"workSeconds":0,"distanceMeters":400}]}"""
        val decoded = json.decodeFromString(WorkoutCardioPrescription.serializer(), fromWeb)
        assertEquals(5000.0, decoded.targetDistanceMeters!!, 0.0)
        assertEquals(354.2, decoded.paceTargetFastSecondsPerKm!!, 0.0)
        assertEquals(400.0, decoded.legs.single().distanceMeters!!, 0.0)
        val old = json.decodeFromString(WorkoutCardioPrescription.serializer(), """{"activity":"RUN","targetMinutes":20}""")
        assertNull(old.targetDistanceMeters)
        assertNull(old.paceTargetSlowSecondsPerKm)
    }
}
