package com.erv.app.stretching

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuildForTodayTest {

    private val catalog: List<StretchCatalogEntry> by lazy {
        val file = listOf(
            File("src/main/assets/stretch_catalog.json"),
            File("app/src/main/assets/stretch_catalog.json"),
        ).first { it.exists() }
        Json { ignoreUnknownKeys = true }.decodeFromString(StretchCatalogRoot.serializer(), file.readText()).stretches
    }

    private val groupOf = mapOf("squat" to "legs", "row" to "back", "bench" to "chest", "curl" to "biceps")

    private fun training(logged: TrainingPiece = TrainingPiece(), running: TrainingPiece = TrainingPiece(), planned: TrainingPiece = TrainingPiece()) =
        todayTraining(logged, running, planned) { groupOf[it] }

    @Test
    fun loggedAndRunningCountTogether() {
        val t = training(
            logged = TrainingPiece(weightExerciseIds = listOf("squat", "squat", "row")),
            running = TrainingPiece(weightExerciseIds = listOf("bench")),
            planned = TrainingPiece(weightExerciseIds = listOf("curl")),
        )
        assertEquals(TodayTrainingSource.LOGGED, t.source)
        assertEquals(mapOf("legs" to 2, "back" to 1, "chest" to 1), t.muscleGroups)
        assertEquals("legs", t.rankedGroups.first())
    }

    @Test
    fun runningOnly() {
        assertEquals(TodayTrainingSource.RUNNING, training(running = TrainingPiece(listOf("row"))).source)
    }

    @Test
    fun planIsUsedWhenNothingIsDone() {
        val t = training(planned = TrainingPiece(cardioActivities = listOf("ROWING")))
        assertEquals(TodayTrainingSource.PLANNED, t.source)
        assertEquals(setOf("back", "legs"), t.muscleGroups.keys)
    }

    @Test
    fun nothingIsARestDay() {
        assertTrue(training().isRestDay)
        // Unknown exercises and activities add nothing.
        assertTrue(training(logged = TrainingPiece(listOf("mystery"), listOf("OTHER"))).isRestDay)
    }

    @Test
    fun cardioWorksLegs() {
        assertEquals(listOf("legs"), cardioActivityMuscleGroups("run"))
        assertTrue(cardioActivityMuscleGroups("RUCK").containsAll(listOf("legs", "back")))
    }

    @Test
    fun legDayPicksLegStretchesThatFitTheTime() {
        val today = TodayTraining(TodayTrainingSource.LOGGED, mapOf("legs" to 5))
        for (minutes in BUILD_FOR_TODAY_MINUTES) {
            val built = buildStretchesForToday(catalog, today, minutes)
            assertNotNull(built)
            built!!
            assertTrue("$minutes min: ${built.totalSeconds}", built.totalSeconds <= minutes * 60)
            assertTrue("$minutes min too short: ${built.totalSeconds}", built.totalSeconds >= minutes * 60 * 3 / 4)
            assertTrue(built.entries.all { "legs" in it.muscleGroups })
            assertEquals(built.entries.size, built.entries.map { it.id }.toSet().size)
            assertTrue(built.holds.all { it in 20..300 })
            assertFalse(built.settleAndRest)
        }
    }

    @Test
    fun shortSessionsHaveNoRestorativeLongOnesEndOnOne() {
        val today = TodayTraining(TodayTrainingSource.LOGGED, mapOf("legs" to 5))
        val ten = buildStretchesForToday(catalog, today, 10)!!
        assertTrue(ten.entries.none { it.isRestorative })
        val twenty = buildStretchesForToday(catalog, today, 20)!!
        assertTrue(twenty.entries.last().isRestorative)
        assertTrue(twenty.entries.dropLast(1).none { it.isRestorative })
    }

    @Test
    fun everyWorkedGroupGetsAStretch() {
        val today = TodayTraining(TodayTrainingSource.LOGGED, mapOf("chest" to 4, "triceps" to 3, "shoulders" to 3))
        val built = buildStretchesForToday(catalog, today, 10)!!
        for (g in listOf("chest", "triceps", "shoulders")) {
            assertTrue("$g missing", built.entries.any { g in it.muscleGroups })
        }
        assertTrue(built.name.startsWith("For Today: Chest"))
    }

    @Test
    fun restDayIsRestorativeWithSettleFromTenMinutes() {
        val rest = TodayTraining(TodayTrainingSource.REST_DAY)
        val fifteen = buildStretchesForToday(catalog, rest, 15)!!
        assertTrue(fifteen.entries.all { it.isRestorative })
        assertTrue(fifteen.settleAndRest)
        assertTrue(fifteen.entries.none { it.id == "builtin_savasana" })
        assertTrue(fifteen.totalSeconds <= 15 * 60)
        val five = buildStretchesForToday(catalog, rest, 5)!!
        assertFalse(five.settleAndRest)
        assertTrue(five.totalSeconds <= 5 * 60)
    }

    @Test
    fun savedRoutineKeepsEachHold() {
        val built = buildStretchesForToday(catalog, TodayTraining(TodayTrainingSource.LOGGED, mapOf("legs" to 3)), 20)!!
        val routine = built.toRoutine(id = "x")
        assertEquals(built.entries.map { it.id }, routine.stretchIds)
        assertEquals(built.holds, routine.entryHoldSeconds())
        // The player's own planned length agrees with the builder's.
        val minutes = (built.totalSeconds + 59) / 60
        assertTrue(minutes <= 20)
    }
}
