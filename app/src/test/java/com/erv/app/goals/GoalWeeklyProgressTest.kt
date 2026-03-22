package com.erv.app.goals

import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioSession
import com.erv.app.lighttherapy.LightDayLog
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.lighttherapy.LightSession
import com.erv.app.supplements.SupplementDayLog
import com.erv.app.supplements.SupplementIntake
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementRoutineRun
import com.erv.app.weighttraining.WeightDayLog
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalWeeklyProgressTest {

    @Test
    fun isoWeekDaysContaining_isSevenDaysStartingMonday() {
        // Wednesday 2025-03-19 -> week Mon 17 .. Sun 23
        val wed = LocalDate.of(2025, 3, 19)
        val days = isoWeekDaysContaining(wed)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2025, 3, 17), days.first())
        assertEquals(LocalDate.of(2025, 3, 23), days.last())
    }

    @Test
    fun supplements_countsDistinctDaysWithLog() {
        val mon = LocalDate.of(2025, 3, 17)
        val logs = listOf(
            SupplementDayLog(
                date = mon.toString(),
                routineRuns = listOf(
                    SupplementRoutineRun(routineId = "r", routineName = "AM", stepIntakes = emptyList())
                ),
            ),
            SupplementDayLog(
                date = mon.plusDays(1).toString(),
                adHocIntakes = listOf(
                    SupplementIntake(supplementId = "s", supplementName = "D", id = "1"),
                ),
            ),
        )
        val state = SupplementLibraryState(logs = logs)
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon.plusDays(2),
            selectedGoalIds = setOf("supplements_adherence"),
            supplementState = state,
            lightState = LightLibraryState(),
            cardioState = CardioLibraryState(),
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            defaults = WeeklyGoalDefaults(supplementActiveDays = 2),
        )
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun cardio_sumsSessionsInWeek() {
        val mon = LocalDate.of(2025, 3, 17)
        val snap = CardioActivitySnapshot(displayLabel = "Run")
        val session = CardioSession(activity = snap, durationMinutes = 30)
        val logMon = CardioDayLog(date = mon.toString(), sessions = listOf(session))
        val logTue = CardioDayLog(date = mon.plusDays(1).toString(), sessions = listOf(session, session))
        val state = CardioLibraryState(logs = listOf(logMon, logTue))
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon.plusDays(3),
            selectedGoalIds = setOf("cardio_activity"),
            supplementState = SupplementLibraryState(),
            lightState = LightLibraryState(),
            cardioState = state,
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            defaults = WeeklyGoalDefaults(cardioSessions = 3),
        )
        assertEquals(3, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun light_sumsMinutes() {
        val mon = LocalDate.of(2025, 3, 17)
        val log = LightDayLog(
            date = mon.toString(),
            sessions = listOf(LightSession(minutes = 25), LightSession(minutes = 40)),
        )
        val state = LightLibraryState(logs = listOf(log))
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon,
            selectedGoalIds = setOf("light_therapy_time"),
            supplementState = SupplementLibraryState(),
            lightState = state,
            cardioState = CardioLibraryState(),
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            defaults = WeeklyGoalDefaults(lightMinutesTotal = 60),
        )
        assertEquals(65, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun weight_countsWorkouts() {
        val mon = LocalDate.of(2025, 3, 17)
        val w = WeightWorkoutSession(source = WeightWorkoutSource.MANUAL)
        val log = WeightDayLog(date = mon.toString(), workouts = listOf(w, w))
        val state = WeightLibraryState(logs = listOf(log))
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon,
            selectedGoalIds = setOf("weight_training"),
            supplementState = SupplementLibraryState(),
            lightState = LightLibraryState(),
            cardioState = CardioLibraryState(),
            weightState = state,
            stretchState = StretchLibraryState(),
            defaults = WeeklyGoalDefaults(weightWorkouts = 2),
        )
        assertEquals(2, rows.single().current)
        assertTrue(rows.single().met)
    }
}
