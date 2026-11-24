package com.erv.app.workouts

import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.WeightSet
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the saved run and the ongoing notification show, against the shared examples. */
class WorkoutSessionProgressTest {

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

    private val names = WorkoutSessionNames { step ->
        mapOf(
            "strength-squat" to "Back Squat",
            "strength-bench" to "Bench Press",
            "pairs-ohp" to "Overhead Press",
            "pairs-pullup" to "Pull-Up",
            "cardio-warmup-bike" to "Bike",
            "cardio-sprints" to "Sprints",
            "cardio-stretch-side" to "Side Stretch",
        )[step.itemId]
    }

    private fun run(id: String) = WorkoutActiveRun(
        workoutId = id,
        workoutSnapshot = examples.getValue(id),
        createdAtEpochSeconds = 900,
    )

    private fun started(id: String, at: Long = 1_000) = run(id).withSessionStarted(at)

    private fun WorkoutActiveRun.after(vararg events: Pair<Long, WorkoutSessionEvent>): WorkoutActiveRun =
        events.fold(this) { run, (at, event) -> run.withSessionEvent(event, at) }

    private fun WorkoutActiveRun.content(now: Long, bpm: Int? = null) =
        sessionNotificationContent(names, BodyWeightUnit.LB, now, bpm)!!

    private fun WorkoutActiveRun.textAfterSkipping(steps: Int): String =
        (1..steps).fold(this) { run, _ -> run.withSessionEvent(WorkoutSessionEvent.SkipStep, 2_000) }
            .content(2_000).text

    @Test
    fun starting_sets_the_clocks_once() {
        val first = started("example-strength-day", at = 1_000)
        assertEquals(1_000L, first.startedAtEpochSeconds)
        assertEquals(1_000L, first.sessionStepStartedAtEpochSeconds)
        assertEquals(0, first.session!!.stepIndex)
        assertSame(first, first.withSessionStarted(5_000))

        val alreadyRunning = run("example-strength-day").copy(startedAtEpochSeconds = 800).withSessionStarted(1_000)
        assertEquals(800L, alreadyRunning.startedAtEpochSeconds)
    }

    @Test
    fun events_without_a_session_or_that_dont_fit_change_nothing() {
        val idle = run("example-strength-day")
        assertSame(idle, idle.withSessionEvent(WorkoutSessionEvent.StepDone, 1_100))
        assertNull(idle.sessionNotificationContent(names, BodyWeightUnit.LB, 1_100))

        val live = started("example-strength-day")
        assertSame(live, live.withSessionEvent(WorkoutSessionEvent.TimedStepDone(30), 1_100))
    }

    @Test
    fun the_step_clock_restarts_only_when_the_step_changes() {
        val rest = started("example-strength-day").after(1_100L to WorkoutSessionEvent.SetLogged(WeightSet(reps = 5, weightKg = 102.0582)))
        assertEquals(1, rest.session!!.stepIndex)
        assertEquals(1_100L, rest.sessionStepStartedAtEpochSeconds)

        val corrected = rest.after(1_150L to WorkoutSessionEvent.EditSet(0, WeightSet(reps = 4, weightKg = 102.0582)))
        assertEquals(1, corrected.session!!.stepIndex)
        assertEquals(1_100L, corrected.sessionStepStartedAtEpochSeconds)
    }

    @Test
    fun a_rest_counts_down_then_falls_back_to_the_workout_clock() {
        val rest = started("example-strength-day").after(1_100L to WorkoutSessionEvent.SetLogged(WeightSet(reps = 5, weightKg = 102.0582)))
        assertEquals(1_280L, rest.sessionCountdownEndsAtEpochSeconds())

        val during = rest.content(now = 1_200)
        assertTrue(during.countDown)
        assertEquals(1_280L, during.chronometerEpochSeconds)
        assertEquals(1_000L, during.workoutStartedAtEpochSeconds)

        val over = rest.content(now = 1_300)
        assertFalse(over.countDown)
        assertEquals(1_000L, over.chronometerEpochSeconds)
    }

    @Test
    fun a_lift_set_waits_for_the_athlete() {
        val live = started("example-strength-day")
        assertNull(live.sessionCountdownEndsAtEpochSeconds())
        val content = live.content(now = 1_050)
        assertEquals("Strength Day", content.title)
        assertEquals("Back Squat · Set 1 of 3 · 5 reps at 225 lb", content.text)
        assertEquals("Step 1 of 14", content.progress)
        assertFalse(content.countDown)
        assertEquals(1_000L, content.chronometerEpochSeconds)
        assertFalse(content.complete)
    }

    @Test
    fun a_rest_names_what_comes_next_and_live_heart_rate_is_added() {
        val rest = started("example-strength-day").after(1_100L to WorkoutSessionEvent.SetLogged(WeightSet(reps = 5, weightKg = 102.0582)))
        val content = rest.content(now = 1_120, bpm = 142)
        assertTrue(content.text, content.text.startsWith("Rest · Next: Back Squat · Set 2 of 3"))
        assertTrue(content.text, content.text.endsWith(" · 142 bpm"))
        assertEquals("Step 2 of 14", content.progress)

        // Between exercises the next thing is the bench, not the planned rest after it.
        val betweenExercises = started("example-strength-day").textAfterSkipping(5)
        assertEquals("Rest · Next: Bench Press · Set 1 of 3 · 8 reps at 135 lb", betweenExercises)
    }

    @Test
    fun rounds_are_counted_as_rounds() {
        val live = started("example-pairs-and-circuit")
        assertTrue(live.content(1_000).text, live.content(1_000).text.startsWith("Overhead Press · Round 1 of 3"))
        assertTrue(live.textAfterSkipping(2), live.textAfterSkipping(2).startsWith("Pull-Up · Round 1 of 3"))
    }

    @Test
    fun cardio_legs_and_stretch_sides_read_plainly() {
        val live = started("example-cardio-and-mobility")
        assertEquals("Bike · 5 min", live.content(1_000).text)
        assertEquals(1_300L, live.content(1_000).chronometerEpochSeconds)
        assertTrue(live.content(1_000).countDown)

        assertEquals("Sprints · 30 s · Leg 1 of 15", live.textAfterSkipping(3))
        assertEquals("Recovery · 1:30 · Leg 2 of 15", live.textAfterSkipping(4))

        val plan = live.sessionPlan()
        val leftSide = plan.steps.indexOfFirst { it is WorkoutSessionStep.StretchHold && it.side == WorkoutStretchSide.LEFT }
        assertEquals("Side Stretch · Left side · 30 s", live.textAfterSkipping(leftSide))
        assertEquals("Rest before the next section · Next: Side Stretch · Left side · 30 s", live.textAfterSkipping(leftSide - 1))
    }

    @Test
    fun the_end_of_the_session_reads_complete() {
        val live = started("example-strength-day")
        val done = live.copy(session = WorkoutSessionState(stepIndex = live.sessionPlan().size))
        val content = done.content(now = 4_000)
        assertTrue(content.complete)
        assertEquals("Workout complete", content.text)
        assertNull(content.progress)
        assertNull(done.sessionCountdownEndsAtEpochSeconds())
    }

    @Test
    fun step_lengths_read_as_seconds_minutes_or_both() {
        assertEquals("45 s", formatStepSeconds(45))
        assertEquals("1:30", formatStepSeconds(90))
        assertEquals("30 min", formatStepSeconds(1_800))
    }
}
