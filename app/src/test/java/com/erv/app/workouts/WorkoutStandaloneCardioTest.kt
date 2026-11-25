package com.erv.app.workouts

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioSpeedUnit
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.CardioTimerStyle
import com.erv.app.cardio.CardioTreadmillParams
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStandaloneCardioTest {

    private val now = 1_764_000_000L

    private fun draft(
        builtin: CardioBuiltinActivity? = CardioBuiltinActivity.RUCK,
        style: CardioTimerStyle = CardioTimerStyle.CountUp,
        modality: CardioModality = CardioModality.OUTDOOR,
        treadmill: CardioTreadmillParams? = null,
        ruckLoadKg: Double? = 13.6,
    ) = CardioTimerSessionDraft.fromQuickSnapshot(
        activity = CardioActivitySnapshot(builtin = builtin, displayLabel = "Ruck"),
        modality = modality,
        treadmill = treadmill,
        title = "Morning Ruck",
        timerStyle = style,
        ruckLoadKg = ruckLoadKg,
    )

    @Test
    fun onlySteadyBuiltinTimersMoveToTheSessionScreen() {
        assertNotNull(CardioActiveTimerSession.Single(draft()).sessionScreenCardioDraft())
        assertNotNull(CardioActiveTimerSession.Single(draft(style = CardioTimerStyle.CountDown(1_800))).sessionScreenCardioDraft())
        // Stay on the cardio timer screen for now.
        assertNull(CardioActiveTimerSession.Single(draft(style = CardioTimerStyle.CountDown(90))).sessionScreenCardioDraft())
        assertNull(CardioActiveTimerSession.Single(draft(style = CardioTimerStyle.CountDownDistance(1_609.0))).sessionScreenCardioDraft())
        assertNull(CardioActiveTimerSession.Single(draft(builtin = null)).sessionScreenCardioDraft())
        val custom = draft().copy(activity = CardioActivitySnapshot(customTypeId = "c1", customName = "Sled", displayLabel = "Sled"))
        assertNull(CardioActiveTimerSession.Single(custom).sessionScreenCardioDraft())
        val multi = CardioActiveTimerSession.Multi(
            CardioMultiLegTimerState(
                routineId = null,
                routineName = null,
                legs = emptyList(),
                completedSegments = emptyList(),
                currentLegIndex = 0,
                workoutStartEpoch = now,
                legStartedEpoch = now,
            ),
        )
        assertNull(multi.sessionScreenCardioDraft())
    }

    @Test
    fun countdownBecomesTheItemsTargetMinutes() {
        val workout = standaloneCardioWorkout(draft(style = CardioTimerStyle.CountDown(1_800)), now)
        assertTrue(isStandaloneWorkoutId(workout.id))
        assertEquals("Morning Ruck", workout.name)
        val item = workout.segments.single().items.single() as WorkoutItem.Cardio
        assertEquals("RUCK", item.cardio.activity)
        assertEquals(30, item.cardio.targetMinutes)
        assertNull(item.cardio.cardioRoutineId)
        val run = standaloneCardioRun(workout, draft(), now)
        assertEquals(item.id, run.standalone?.cardioItemId)
        assertNotNull(run.standaloneCardioDraftFor(item.id))
        assertNull(run.standaloneCardioDraftFor("other"))
    }

    private fun finished(d: CardioTimerSessionDraft): Pair<WorkoutActiveRun, WorkoutSessionCardioItem> {
        val workout = standaloneCardioWorkout(d, now)
        val run = standaloneCardioRun(workout, d, now)
            .copy(sessionStepAwaitingStart = false, sessionStepStartedAtEpochSeconds = now)
            .applyingSessionEvent(WorkoutSessionEvent.TimedStepDone(2_400), now + 2_400)
        return run to run.sessionCardioItemsToLog().single()
    }

    @Test
    fun outdoorRuckLogsItsPackAndRoute() {
        val (run, item) = finished(draft())
        assertEquals(2_400, item.elapsedSeconds)
        val points = (0..20).map { CardioGpsPoint(lat = 40.0 + it * 0.0002, lon = -75.0, epochSeconds = now + it * 30L) }
        val session = run.cardioSessionFor(item, CardioLibraryState(), points)!!
        assertEquals(13.6, session.ruckLoadKg!!, 0.001)
        assertEquals(CardioModality.OUTDOOR, session.modality)
        assertNotNull(session.gpsTrack)
    }

    @Test
    fun treadmillLogsIndoorWithoutARoute() {
        val treadmill = CardioTreadmillParams(speed = 3.5, speedUnit = CardioSpeedUnit.MPH, inclinePercent = 5.0, loadKg = 9.0)
        val (run, item) = finished(draft(modality = CardioModality.INDOOR_TREADMILL, treadmill = treadmill, ruckLoadKg = null))
        val points = (0..20).map { CardioGpsPoint(lat = 40.0 + it * 0.0002, lon = -75.0, epochSeconds = now + it * 30L) }
        val session = run.cardioSessionFor(item, CardioLibraryState(), points)!!
        assertEquals(CardioModality.INDOOR_TREADMILL, session.modality)
        assertEquals(treadmill, session.treadmill)
        assertNull(session.gpsTrack)
        // 3.5 mph for 40 minutes.
        assertEquals(3_755.0, session.distanceMeters!!, 15.0)
    }

    @Test
    fun aCardioStartEndsWithItsCardio() {
        val (run, _) = finished(draft())
        assertTrue(run.isSessionComplete())
        assertFalse(run.isSessionWaitingForMore())
        // Something added to it waits for Finish, like any standalone workout.
        val withLift = run.copy(
            workoutSnapshot = run.workoutSnapshot.copy(
                segments = run.workoutSnapshot.segments + WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(WorkoutItem.Weight(exerciseId = "squat", prescription = standaloneLiftPrescription(null))),
                ),
            ),
        )
        assertFalse(withLift.isSessionComplete())
    }

    @Test
    fun theDraftSurvivesARestart() {
        val d = draft()
        val workout = standaloneCardioWorkout(d, now)
        val state = WorkoutLibraryState(activeRun = standaloneCardioRun(workout, d, now))
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val back = json.decodeFromString(WorkoutLibraryState.serializer(), json.encodeToString(WorkoutLibraryState.serializer(), state))
        assertEquals(d, back.activeRun?.standalone?.cardioDraft)
    }
}
