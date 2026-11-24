package com.erv.app.workouts

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

/** The session engine against `shared/test-cases/workout_examples.json`. */
class WorkoutSessionEngineTest {

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

    private fun plan(id: String) = WorkoutSessionPlan.from(examples.getValue(id))

    private fun signature(step: WorkoutSessionStep): String = when (step) {
        is WorkoutSessionStep.LiftSet -> "lift ${step.itemId} ${step.setIndex + 1}/${step.setCount}"
        is WorkoutSessionStep.Rest -> "rest ${step.kind.name.lowercase()} ${step.seconds}"
        is WorkoutSessionStep.CardioLeg ->
            "${if (step.isRecovery) "recover" else "work"} ${step.itemId} ${step.targetSeconds}"
        is WorkoutSessionStep.StretchHold ->
            "hold ${step.itemId} ${step.holdSeconds}" + (step.side?.let { " ${it.name.lowercase()}" } ?: "")
        is WorkoutSessionStep.Note -> "note ${step.itemId}"
    }

    private fun WorkoutSessionPlan.indexOf(predicate: (WorkoutSessionStep) -> Boolean): Int =
        steps.indexOfFirst(predicate).also { check(it >= 0) }

    private fun done(reps: Int, kg: Double? = null) = WeightSet(reps = reps, weightKg = kg)

    private fun WorkoutSessionPlan.play(start: WorkoutSessionState, vararg events: WorkoutSessionEvent) =
        events.fold(start) { state, event -> WorkoutSessionEngine.reduce(this, state, event) }

    @Test
    fun strength_day_runs_sets_and_rests_in_order() {
        val p = plan("example-strength-day")
        assertEquals(
            listOf(
                "lift strength-squat 1/3", "rest between_sets 180",
                "lift strength-squat 2/3", "rest between_sets 180",
                "lift strength-squat 3/3", "rest between_exercises 120",
                "rest planned 90",
                "lift strength-bench 1/3", "rest between_sets 120",
                "lift strength-bench 2/3", "rest between_sets 120",
                "lift strength-bench 3/3",
                "lift strength-row 1/3", "lift strength-row 2/3", "lift strength-row 3/3",
                "note strength-note",
                "rest after_section 120",
                "lift strength-split-squat 1/2", "lift strength-split-squat 2/2",
                "lift strength-plank 1/2", "lift strength-plank 2/2",
                "lift strength-dips 1/1",
            ),
            p.steps.map(::signature),
        )
    }

    @Test
    fun lift_sets_carry_the_builder_targets() {
        val p = plan("example-strength-day")
        val squat = p.steps.first() as WorkoutSessionStep.LiftSet
        assertEquals(5, squat.target.targetReps)
        assertEquals(102.0582, squat.target.targetWeightKg!!, 1e-9)
        assertEquals(0, squat.target.reps)
        assertNull(squat.target.weightKg)

        val heavyBench = p.steps.filterIsInstance<WorkoutSessionStep.LiftSet>()
            .single { it.itemId == "strength-bench" && it.setIndex == 2 }
        assertEquals(6, heavyBench.target.targetReps)
        assertEquals(65.7709, heavyBench.target.targetWeightKg!!, 1e-9)

        val plank = p.steps.filterIsInstance<WorkoutSessionStep.LiftSet>().first { it.itemId == "strength-plank" }
        assertEquals(45, plank.target.targetDurationSeconds)
        assertNull(plank.target.targetReps)

        val dips = p.steps.filterIsInstance<WorkoutSessionStep.LiftSet>().single { it.itemId == "strength-dips" }
        assertNull(dips.target.targetReps)
    }

    @Test
    fun superset_and_circuit_take_one_set_of_each_exercise_per_round() {
        val p = plan("example-pairs-and-circuit")
        assertEquals(
            listOf(
                "lift pairs-ohp 1/3", "rest between_exercises 30", "lift pairs-pullup 1/3", "rest after_round 90",
                "lift pairs-ohp 2/3", "rest between_exercises 30", "lift pairs-pullup 2/3", "rest after_round 90",
                "lift pairs-ohp 3/3", "rest between_exercises 30", "lift pairs-pullup 3/3",
                "lift pairs-curl 1/2", "lift pairs-lunge 1/2", "rest after_round 60",
                "lift pairs-curl 2/2", "lift pairs-lunge 2/2",
            ),
            p.steps.map(::signature),
        )
        val rounds = p.steps.filterIsInstance<WorkoutSessionStep.LiftSet>()
            .filter { it.itemId == "pairs-pullup" }
            .map { it.round }
        assertEquals(listOf(1, 2, 3), rounds)
    }

    @Test
    fun cardio_and_mobility_become_legs_and_holds() {
        val p = plan("example-cardio-and-mobility")
        val sprints = buildList {
            repeat(8) { round ->
                add("work cardio-sprints 30")
                if (round < 7) add("recover cardio-sprints 90")
            }
        }
        assertEquals(
            listOf("work cardio-warmup-bike 300", "hold cardio-warmup-neck 30", "work cardio-steady-run 1800") +
                sprints +
                listOf(
                    "work cardio-ladder 240", "recover cardio-ladder 120",
                    "work cardio-ladder 45", "recover cardio-ladder 15",
                    "work cardio-ladder 240", "recover cardio-ladder 120",
                    "work cardio-ladder 45",
                    "rest after_section 180",
                    "hold cardio-stretch-side 30 left", "hold cardio-stretch-side 30 right",
                    "hold cardio-stretch-chin 30",
                ),
            p.steps.map(::signature),
        )
        val sprintLegs = p.steps.filterIsInstance<WorkoutSessionStep.CardioLeg>().filter { it.itemId == "cardio-sprints" }
        assertEquals(15, sprintLegs.first().legCount)
        assertEquals((0 until 15).toList(), sprintLegs.map { it.legIndex })
        assertTrue(sprintLegs.filterNot { it.isRecovery }.all { it.hrTargetBpm == 170 })
        val hardLeg = p.steps.filterIsInstance<WorkoutSessionStep.CardioLeg>().first { it.itemId == "cardio-ladder" }
        assertEquals("Hard", hardLeg.label)
        assertEquals(165, hardLeg.hrTargetBpm)
        assertEquals("ROWING", hardLeg.activity)
    }

    @Test
    fun each_event_only_fits_its_own_step() {
        val p = plan("example-strength-day")
        val start = WorkoutSessionState()
        // Not a lift: a timed event or a "rest over" changes nothing.
        assertSame(start, WorkoutSessionEngine.reduce(p, start, WorkoutSessionEvent.TimedStepDone(30)))
        assertSame(start, WorkoutSessionEngine.reduce(p, start, WorkoutSessionEvent.StepDone))

        val afterSet = WorkoutSessionEngine.reduce(p, start, WorkoutSessionEvent.SetLogged(done(5, 102.5)))
        assertEquals(1, afterSet.stepIndex)
        // A late second tap on the set while the rest is up must not log the next set.
        assertSame(afterSet, WorkoutSessionEngine.reduce(p, afterSet, WorkoutSessionEvent.SetLogged(done(5, 102.5))))

        val afterRest = WorkoutSessionEngine.reduce(p, afterSet, WorkoutSessionEvent.TimedStepDone(150))
        assertEquals(2, afterRest.stepIndex)
        assertEquals(150, afterRest.recordFor(1)?.elapsedSeconds)
    }

    @Test
    fun skipping_an_exercise_skips_its_sets_and_rests() {
        val p = plan("example-strength-day")
        val state = p.play(
            WorkoutSessionState(),
            WorkoutSessionEvent.SetLogged(done(5, 102.5)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SkipItem,
        )
        assertEquals(p.indexOf { it.itemId == "strength-rest" }, state.stepIndex)
        val squatRecords = p.steps.indices
            .filter { p.steps[it].itemId == "strength-squat" }
            .map { state.recordFor(it)?.outcome }
        assertEquals(
            listOf(
                WorkoutStepOutcome.DONE, WorkoutStepOutcome.DONE,
                WorkoutStepOutcome.SKIPPED, WorkoutStepOutcome.SKIPPED,
                WorkoutStepOutcome.SKIPPED, WorkoutStepOutcome.SKIPPED,
            ),
            squatRecords,
        )
    }

    @Test
    fun skipping_a_superset_exercise_skips_it_in_every_round() {
        val p = plan("example-pairs-and-circuit")
        val state = p.play(WorkoutSessionState(), WorkoutSessionEvent.SkipItem)
        val remaining = p.steps.indices.filter { state.recordFor(it) == null }.map { signature(p.steps[it]) }
        assertEquals(
            listOf(
                "lift pairs-pullup 1/3", "rest after_round 90",
                "lift pairs-pullup 2/3", "rest after_round 90",
                "lift pairs-pullup 3/3",
                "lift pairs-curl 1/2", "lift pairs-lunge 1/2", "rest after_round 60",
                "lift pairs-curl 2/2", "lift pairs-lunge 2/2",
            ),
            remaining,
        )
        assertEquals("lift pairs-pullup 1/3", signature(WorkoutSessionEngine.currentStep(p, state)!!))
    }

    @Test
    fun ending_a_section_early_keeps_the_rest_before_the_next_one() {
        val p = plan("example-strength-day")
        val ended = p.play(
            WorkoutSessionState(),
            WorkoutSessionEvent.SetLogged(done(5, 102.5)),
            WorkoutSessionEvent.EndSection,
        )
        val sectionRest = p.indexOf { it is WorkoutSessionStep.Rest && it.kind == WorkoutSessionRestKind.AFTER_SECTION }
        assertEquals(sectionRest, ended.stepIndex)
        assertTrue(WorkoutSessionEngine.isSectionFinished(p, ended, 0).not())

        val next = WorkoutSessionEngine.reduce(p, ended, WorkoutSessionEvent.StepDone)
        assertTrue(WorkoutSessionEngine.isSectionFinished(p, next, 0))
        assertEquals("lift strength-split-squat 1/2", signature(WorkoutSessionEngine.currentStep(p, next)!!))
    }

    @Test
    fun logged_sets_come_back_in_order_and_can_be_corrected() {
        val p = plan("example-strength-day")
        var state = p.play(
            WorkoutSessionState(),
            WorkoutSessionEvent.SetLogged(done(5, 100.0)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(done(5, 102.5)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(done(4, 102.5)),
        )
        assertEquals(
            listOf(100.0, 102.5, 102.5),
            WorkoutSessionEngine.loggedSets(p, state, "strength-main", "strength-squat").map { it.weightKg },
        )
        val position = state.stepIndex
        state = WorkoutSessionEngine.reduce(p, state, WorkoutSessionEvent.EditSet(2, done(5, 105.0)))
        assertEquals(position, state.stepIndex)
        assertEquals(
            listOf(5, 5, 4),
            WorkoutSessionEngine.loggedSets(p, state, "strength-main", "strength-squat").map { it.reps },
        )
        assertEquals(105.0, WorkoutSessionEngine.loggedSets(p, state, "strength-main", "strength-squat")[1].weightKg!!, 1e-9)

        // A rest, or a set not logged yet, can't be edited.
        assertSame(state, WorkoutSessionEngine.reduce(p, state, WorkoutSessionEvent.EditSet(1, done(1))))
        assertSame(state, WorkoutSessionEngine.reduce(p, state, WorkoutSessionEvent.EditSet(12, done(1))))
    }

    @Test
    fun every_example_runs_to_the_end() {
        examples.values.forEach { workout ->
            val p = WorkoutSessionPlan.from(workout)
            var state = WorkoutSessionState()
            var guard = 0
            while (!WorkoutSessionEngine.isComplete(p, state)) {
                val event = when (val step = WorkoutSessionEngine.currentStep(p, state)!!) {
                    is WorkoutSessionStep.LiftSet -> WorkoutSessionEvent.SetLogged(
                        step.target.copy(reps = step.target.targetReps ?: 1, weightKg = step.target.targetWeightKg),
                    )
                    is WorkoutSessionStep.Rest -> WorkoutSessionEvent.StepDone
                    is WorkoutSessionStep.Note -> WorkoutSessionEvent.StepDone
                    is WorkoutSessionStep.CardioLeg -> WorkoutSessionEvent.TimedStepDone(step.targetSeconds ?: 600)
                    is WorkoutSessionStep.StretchHold -> WorkoutSessionEvent.TimedStepDone(step.holdSeconds)
                }
                val next = WorkoutSessionEngine.reduce(p, state, event)
                assertTrue("${workout.id} stuck at step ${state.stepIndex}", next.stepIndex > state.stepIndex)
                state = next
                check(++guard <= p.size)
            }
            assertEquals(p.size, state.records.size)
            assertTrue(state.records.all { it.outcome == WorkoutStepOutcome.DONE })
            assertNull(WorkoutSessionEngine.currentStep(p, state))
            assertSame(state, WorkoutSessionEngine.reduce(p, state, WorkoutSessionEvent.SkipStep))
        }
    }

    @Test
    fun state_survives_being_saved_and_read_back() {
        val p = plan("example-pairs-and-circuit")
        val state = p.play(
            WorkoutSessionState(),
            WorkoutSessionEvent.SetLogged(done(8, 40.8233)),
            WorkoutSessionEvent.SkipStep,
            WorkoutSessionEvent.SkipItem,
        )
        val text = WorkoutSync.json.encodeToString(WorkoutSessionState.serializer(), state)
        assertEquals(state, WorkoutSync.json.decodeFromString(WorkoutSessionState.serializer(), text))
        assertFalse(WorkoutSessionEngine.isComplete(p, state))
    }

    @Test
    fun empty_workout_is_already_complete() {
        val p = WorkoutSessionPlan.from(Workout(id = "empty", name = "Empty"))
        assertTrue(p.steps.isEmpty())
        assertTrue(WorkoutSessionEngine.isComplete(p, WorkoutSessionState()))
    }
}
