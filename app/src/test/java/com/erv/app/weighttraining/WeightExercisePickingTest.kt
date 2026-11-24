package com.erv.app.weighttraining

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightExercisePickingTest {

    private fun ex(id: String, name: String, muscle: String, equipment: WeightEquipment = WeightEquipment.BARBELL) =
        WeightExercise(id = id, name = name, muscleGroup = muscle, pushOrPull = WeightPushPull.PUSH, equipment = equipment)

    private val bench = ex("bench", "Bench Press", "chest")
    private val dbBench = ex("db-bench", "Dumbbell Bench Press", "chest", WeightEquipment.DUMBBELL)
    private val incline = ex("incline", "Incline Dumbbell Press", "chest", WeightEquipment.DUMBBELL)
    private val pullUp = ex("pull-up", "Pull-Up", "back", WeightEquipment.OTHER)
    private val squat = ex("squat", "Back Squat", "legs")
    private val all = listOf(bench, dbBench, incline, pullUp, squat)

    private fun workout(finishedAt: Long, vararg ids: String) = WeightWorkoutSession(
        source = WeightWorkoutSource.LIVE,
        finishedAtEpochSeconds = finishedAt,
        entries = ids.map { WeightWorkoutEntry(exerciseId = it) },
    )

    @Test
    fun recent_exercises_come_newest_workout_first_each_once() {
        val state = WeightLibraryState(
            exercises = all,
            logs = listOf(
                WeightDayLog("2026-11-20", listOf(workout(100, "squat", "bench"))),
                WeightDayLog("2026-11-23", listOf(workout(300, "pull-up"), workout(400, "incline", "bench"))),
                WeightDayLog("2026-11-22", listOf(workout(200, "deleted-exercise", "db-bench"))),
            ),
        )
        assertEquals(listOf("incline", "bench", "pull-up", "db-bench", "squat"), state.recentExerciseIds())
        assertEquals(listOf("incline", "bench"), state.recentExerciseIds(limit = 2))
    }

    @Test
    fun search_matches_every_word_across_name_muscle_and_equipment() {
        assertEquals(listOf(dbBench, incline), searchExercisesForPicker(all, "dumbbell chest"))
        // Punctuation and case don't matter.
        assertEquals(listOf(pullUp), searchExercisesForPicker(all, "pull up"))
        assertEquals(listOf(pullUp), searchExercisesForPicker(all, "PULL-UP"))
        assertEquals(emptyList<WeightExercise>(), searchExercisesForPicker(all, "deadlift"))
    }

    @Test
    fun search_puts_name_matches_first() {
        // "Back Squat" starts with "back"; "Pull-Up" is only listed under back.
        assertEquals(listOf(squat, pullUp), searchExercisesForPicker(all, "back"))
        // Starts-with, then contains, then the words in any order.
        assertEquals(listOf(bench, dbBench), searchExercisesForPicker(all, "bench press"))
        assertEquals(listOf(incline, dbBench), searchExercisesForPicker(all, "dumbbell press"))
    }

    @Test
    fun blank_search_keeps_the_list() {
        assertEquals(all, searchExercisesForPicker(all, "  "))
    }

    @Test
    fun blank_muscle_group_is_listed_under_other() {
        assertEquals("other", ex("x", "X", " ").pickerMuscleKey())
        assertEquals("chest", ex("y", "Y", " Chest ").pickerMuscleKey())
    }
}
