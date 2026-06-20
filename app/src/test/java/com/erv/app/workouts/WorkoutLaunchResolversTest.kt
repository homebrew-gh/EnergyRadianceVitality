package com.erv.app.workouts

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioLibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLaunchResolversTest {

    private val emptyCardioState = CardioLibraryState()

    @Test
    fun resolveCardioLaunch_steady_countdown() {
        val item = WorkoutItem.Cardio(
            cardio = WorkoutCardioPrescription(
                activity = "WALK",
                mode = "steady",
                targetMinutes = 20,
            ),
        )
        val session = item.resolveCardioLaunch(emptyCardioState)
        assertTrue(session is CardioActiveTimerSession.Single)
        val draft = (session as CardioActiveTimerSession.Single).draft
        assertEquals(20 * 60, (draft.timerStyle as com.erv.app.cardio.CardioTimerStyle.CountDown).totalSeconds)
    }

    @Test
    fun resolveCardioLaunch_sprint_intervals_multi_leg() {
        val item = WorkoutItem.Cardio(
            cardio = WorkoutCardioPrescription(
                activity = "STATIONARY_BIKE",
                mode = "sprint_intervals",
                rounds = 4,
                workSeconds = 60,
                restSeconds = 60,
            ),
        )
        val session = item.resolveCardioLaunch(emptyCardioState)
        assertTrue(session is CardioActiveTimerSession.Multi)
        val legs = (session as CardioActiveTimerSession.Multi).state.legs
        assertEquals(7, legs.size)
    }

    @Test
    fun resolveCardioLaunch_interval_template_outer_rounds() {
        val item = WorkoutItem.Cardio(
            cardio = WorkoutCardioPrescription(
                activity = "STATIONARY_BIKE",
                mode = "interval_template",
                outerRounds = 2,
                legs = listOf(
                    WorkoutCardioIntervalLeg(workSeconds = 240, restSeconds = 120),
                ),
            ),
        )
        val session = item.resolveCardioLaunch(emptyCardioState)
        assertTrue(session is CardioActiveTimerSession.Multi)
        val legs = (session as CardioActiveTimerSession.Multi).state.legs
        assertEquals(3, legs.size)
    }

    @Test
    fun resolveStretchLaunch_catalog_pose() {
        val item = WorkoutItem.Mobility(
            mobility = WorkoutMobilityPrescription(
                catalogId = "builtin_hamstring_stretch",
                holdSeconds = 45,
            ),
        )
        val payload = item.resolveStretchLaunch()
        assertNotNull(payload)
        assertEquals(listOf("builtin_hamstring_stretch"), payload?.stretchIds)
        assertEquals(45, payload?.holdSecondsPerStretch)
    }

    @Test
    fun secondsToGuidedMinutes_rounds_up() {
        assertEquals(1, secondsToGuidedMinutes(30))
        assertEquals(1, secondsToGuidedMinutes(60))
        assertEquals(2, secondsToGuidedMinutes(61))
    }
}
