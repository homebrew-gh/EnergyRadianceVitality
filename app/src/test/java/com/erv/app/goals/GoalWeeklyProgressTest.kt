package com.erv.app.goals

import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioSession
import com.erv.app.data.createGoalFromTemplate
import com.erv.app.data.goalTemplateOptionForId
import com.erv.app.data.migrateLegacyGoalIds
import com.erv.app.lighttherapy.LightDayLog
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.lighttherapy.LightSession
import com.erv.app.programs.FitnessProgram
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramCompletionMark
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramWeekDay
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.programBlockCompletionKey
import com.erv.app.supplements.SupplementDayLog
import com.erv.app.supplements.SupplementIntake
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementRoutineRun
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.weighttraining.WeightDayLog
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
    fun migrateLegacyGoalIds_mapsBuiltInsToCustomGoals() {
        val migrated = migrateLegacyGoalIds(listOf("supplements_adherence", "cardio_activity"))
        assertEquals(2, migrated.size)
        assertEquals("Supplement routines", migrated[0].title)
        assertEquals(4, migrated[0].target)
        assertEquals("Cardio", migrated[1].title)
        assertEquals(2, migrated[1].target)
    }

    @Test
    fun supplements_goal_countsDistinctDaysWithLog() {
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
        val goal = createGoalFromTemplate(
            goalTemplateOptionForId("supplements_daily")!!,
            target = 2,
        )
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon.plusDays(2),
            goals = listOf(goal),
            supplementState = state,
            lightState = LightLibraryState(),
            cardioState = CardioLibraryState(),
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            programsState = ProgramsLibraryState(),
        )
        assertEquals(1, rows.size)
        assertEquals(2, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun cycling_goal_countsOnlyCyclingSessions() {
        val mon = LocalDate.of(2025, 3, 17)
        val ride = CardioSession(
            activity = CardioActivitySnapshot(
                builtin = CardioBuiltinActivity.BIKE,
                displayLabel = "Bike",
            ),
            durationMinutes = 45,
        )
        val run = CardioSession(
            activity = CardioActivitySnapshot(
                builtin = CardioBuiltinActivity.RUN,
                displayLabel = "Run",
            ),
            durationMinutes = 30,
        )
        val logMon = CardioDayLog(date = mon.toString(), sessions = listOf(ride, run))
        val logTue = CardioDayLog(date = mon.plusDays(1).toString(), sessions = listOf(ride))
        val state = CardioLibraryState(logs = listOf(logMon, logTue))
        val goal = createGoalFromTemplate(
            goalTemplateOptionForId("cycling_sessions")!!,
            target = 2,
        )
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon.plusDays(3),
            goals = listOf(goal),
            supplementState = SupplementLibraryState(),
            lightState = LightLibraryState(),
            cardioState = state,
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            programsState = ProgramsLibraryState(),
        )
        assertEquals(2, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun light_goal_sumsMinutes() {
        val mon = LocalDate.of(2025, 3, 17)
        val log = LightDayLog(
            date = mon.toString(),
            sessions = listOf(LightSession(minutes = 25), LightSession(minutes = 40)),
        )
        val state = LightLibraryState(logs = listOf(log))
        val goal = createGoalFromTemplate(
            goalTemplateOptionForId("light_minutes")!!,
            target = 60,
        )
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon,
            goals = listOf(goal),
            supplementState = SupplementLibraryState(),
            lightState = state,
            cardioState = CardioLibraryState(),
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            programsState = ProgramsLibraryState(),
        )
        assertEquals(65, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun weight_goal_countsWorkouts() {
        val mon = LocalDate.of(2025, 3, 17)
        val w = WeightWorkoutSession(source = WeightWorkoutSource.MANUAL)
        val log = WeightDayLog(date = mon.toString(), workouts = listOf(w, w))
        val state = WeightLibraryState(logs = listOf(log))
        val goal = createGoalFromTemplate(
            goalTemplateOptionForId("strength_sessions")!!,
            target = 2,
        )
        val rows = computeWeeklyGoalProgress(
            asOfDate = mon,
            goals = listOf(goal),
            supplementState = SupplementLibraryState(),
            lightState = LightLibraryState(),
            cardioState = CardioLibraryState(),
            weightState = state,
            stretchState = StretchLibraryState(),
            programsState = ProgramsLibraryState(),
        )
        assertEquals(2, rows.single().current)
        assertTrue(rows.single().met)
    }

    @Test
    fun program_goal_countsFullyCompletedScheduledDays() {
        val mon = LocalDate.of(2025, 3, 17)
        val mondayBlock = ProgramDayBlock(id = "monday", kind = ProgramBlockKind.CARDIO, title = "Intervals")
        val tuesdayBlock = ProgramDayBlock(id = "tuesday", kind = ProgramBlockKind.CARDIO, title = "Ride")
        val program = FitnessProgram(
            id = "program-1",
            name = "Base",
            weeklySchedule = listOf(
                ProgramWeekDay(dayOfWeek = 1, blocks = listOf(mondayBlock)),
                ProgramWeekDay(dayOfWeek = 2, blocks = listOf(tuesdayBlock)),
            ),
        )
        val state = ProgramsLibraryState(
            programs = listOf(program),
            activeProgramId = program.id,
            completionState = mapOf(
                programBlockCompletionKey(program.id, mondayBlock.id, mon) to ProgramCompletionMark(done = true),
                programBlockCompletionKey(program.id, tuesdayBlock.id, mon.plusDays(1)) to ProgramCompletionMark(done = true),
            ),
        )
        val goal = createGoalFromTemplate(
            goalTemplateOptionForId("follow_program")!!,
            target = 2,
        )

        val rows = computeWeeklyGoalProgress(
            asOfDate = mon.plusDays(1),
            goals = listOf(goal),
            supplementState = SupplementLibraryState(),
            lightState = LightLibraryState(),
            cardioState = CardioLibraryState(),
            weightState = WeightLibraryState(),
            stretchState = StretchLibraryState(),
            programsState = state,
        )

        assertEquals(2, rows.single().current)
        assertTrue(rows.single().met)
    }
}
