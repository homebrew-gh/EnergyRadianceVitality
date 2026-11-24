package com.erv.app.workouts

import com.erv.app.weighttraining.WeightExerciseFocusMark
import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightSet
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The interval timer and heart rate per exercise on the session screen. */
class WorkoutSessionToolsTest {

    private val examples: Map<String, Workout> by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val file = File(dir, "shared/test-cases/workout_examples.json")
            if (file.isFile) {
                val root = Json.parseToJsonElement(file.readText()).jsonObject
                return@lazy root.getValue("workouts").jsonArray
                    .map { WorkoutSync.json.decodeFromJsonElement(Workout.serializer(), it) }
                    .associateBy { it.id }
            }
            dir = dir.parentFile
        }
        error("shared/test-cases/workout_examples.json not found")
    }

    private fun strengthDay(at: Long = 1_000): WorkoutActiveRun =
        WorkoutActiveRun(
            workoutId = "example-strength-day",
            workoutSnapshot = examples.getValue("example-strength-day"),
            createdAtEpochSeconds = 900,
        ).withSessionStartedAtPosition(at)

    private fun WorkoutActiveRun.play(from: Long, vararg events: WorkoutSessionEvent): WorkoutActiveRun =
        events.foldIndexed(this) { index, run, event -> run.applyingSessionEvent(event, from + 60L * index) }

    private fun WorkoutActiveRun.step() = WorkoutSessionEngine.currentStep(sessionPlan(), session!!)

    private val block = WeightHiitBlockLog(intervals = 8, workSeconds = 30, restSeconds = 30, weightKg = 20.0, rpe = 8.0)

    @Test
    fun an_interval_block_stands_in_for_the_sets_and_is_logged() {
        val run = strengthDay()
        assertTrue(run.canLogSessionHiitBlock())
        val done = run.withSessionHiitBlockLogged(block, 1_300)
        // The squat is done, so the rest the builder placed after it comes up.
        assertEquals("strength-rest", done.step()?.itemId)

        val sections = done.play(1_400, WorkoutSessionEvent.EndWorkout).sessionLiftSectionsToLog()
        val squat = sections.first().entries.first()
        assertEquals("strength-squat", squat.itemId)
        assertEquals(block, squat.hiitBlock)
        assertTrue(squat.sets.isEmpty())
        val logged = done.weightSessionFor(sections.first())
        assertEquals(block, logged.entries.first().hiitBlock)
    }

    @Test
    fun no_interval_timer_once_a_set_is_logged() {
        val run = strengthDay().play(1_060, WorkoutSessionEvent.SetLogged(WeightSet(reps = 5)), WorkoutSessionEvent.StepDone)
        assertFalse(run.canLogSessionHiitBlock())
        assertEquals(run, run.withSessionHiitBlockLogged(block, 1_300))
    }

    @Test
    fun each_exercise_begins_when_the_step_before_it_ended() {
        // Squat: three sets and two rests; then its rest after, the planned rest, and bench.
        val run = strengthDay().play(
            1_060,
            WorkoutSessionEvent.SetLogged(WeightSet(reps = 5)), WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(WeightSet(reps = 5)), WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(WeightSet(reps = 5)), WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(WeightSet(reps = 8)),
        )
        assertEquals("strength-bench", run.step()?.itemId)
        assertEquals(
            listOf(
                WeightExerciseFocusMark(run.workoutSnapshot.segments[0].weightItems()[0].exerciseId, 1_000),
                // The planned rest ended at 1_420, so bench began then.
                WeightExerciseFocusMark(run.workoutSnapshot.segments[0].weightItems()[1].exerciseId, 1_420),
            ),
            run.sessionExerciseFocusMarks("strength-main"),
        )
    }
}
