package com.erv.app.workouts

import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRoutineStep
import com.erv.app.cardio.formatCardioLegSeconds
import com.erv.app.cardio.targetLegSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun sprint_intervals_time_each_leg_in_exact_seconds() {
        val item = WorkoutItem.Cardio(
            cardio = WorkoutCardioPrescription(
                activity = "RUN",
                mode = "sprint_intervals",
                rounds = 3,
                workSeconds = 30,
                restSeconds = 90,
            ),
        )
        val legs = (item.resolveCardioLaunch(emptyCardioState) as CardioActiveTimerSession.Multi).state.legs
        assertEquals(listOf(30, 90, 30, 90, 30), legs.map { it.targetLegSeconds() })
        // Readers that only know minutes still see a rounded-up length.
        assertEquals(listOf(1, 2, 1, 2, 1), legs.map { it.targetDurationMinutes })
    }

    @Test
    fun interval_template_legs_keep_their_seconds() {
        val item = WorkoutItem.Cardio(
            cardio = WorkoutCardioPrescription(
                activity = "ROWING",
                mode = "interval_template",
                outerRounds = 1,
                legs = listOf(
                    WorkoutCardioIntervalLeg(workSeconds = 45, restSeconds = 15),
                    WorkoutCardioIntervalLeg(workSeconds = 150, restSeconds = 0),
                ),
            ),
        )
        val legs = (item.resolveCardioLaunch(emptyCardioState) as CardioActiveTimerSession.Multi).state.legs
        assertEquals(listOf(45, 15, 150), legs.map { it.targetLegSeconds() })
    }

    @Test
    fun older_minute_only_legs_still_time_in_minutes() {
        val leg = CardioRoutineStep(
            activity = CardioActivitySnapshot(builtin = CardioBuiltinActivity.RUN, customTypeId = null, customName = null, displayLabel = "Run"),
            targetDurationMinutes = 4,
        )
        assertEquals(240, leg.targetLegSeconds())
        assertNull(leg.copy(targetDurationMinutes = null).targetLegSeconds())
    }

    @Test
    fun leg_lengths_format_for_display() {
        assertEquals("30 s", formatCardioLegSeconds(30))
        assertEquals("1:30", formatCardioLegSeconds(90))
        assertEquals("4 min", formatCardioLegSeconds(240))
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
