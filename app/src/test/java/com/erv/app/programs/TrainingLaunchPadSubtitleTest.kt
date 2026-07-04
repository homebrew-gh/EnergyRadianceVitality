package com.erv.app.programs

import com.erv.app.workouts.Workout
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingLaunchPadSubtitleTest {

    @Test
    fun trainingLaunchPadSubtitle_restWhenNoWorkoutBlocks() {
        assertEquals("Rest", trainingLaunchPadSubtitle(emptyList(), emptyMap()))
        assertEquals(
            "Rest",
            trainingLaunchPadSubtitle(
                listOf(ProgramDayBlock(kind = ProgramBlockKind.REST)),
                emptyMap(),
            ),
        )
    }

    @Test
    fun trainingLaunchPadSubtitle_usesSavedWorkoutName() {
        val blocks = listOf(
            ProgramDayBlock(kind = ProgramBlockKind.WORKOUT, workoutId = "w1"),
        )
        val workouts = mapOf("w1" to Workout(id = "w1", name = "Upper Body Push"))
        assertEquals("Upper Body Push", trainingLaunchPadSubtitle(blocks, workouts))
    }

    @Test
    fun trainingLaunchPadSubtitle_fallsBackToBlockTitle() {
        val blocks = listOf(
            ProgramDayBlock(kind = ProgramBlockKind.WORKOUT, title = "Leg Day"),
        )
        assertEquals("Leg Day", trainingLaunchPadSubtitle(blocks, emptyMap()))
    }
}
