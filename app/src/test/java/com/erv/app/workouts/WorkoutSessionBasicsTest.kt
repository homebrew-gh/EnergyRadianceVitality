package com.erv.app.workouts

import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightSet
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rest timer modes, removing any set, and standalone (Quick Start) workouts on the session screen. */
class WorkoutSessionBasicsTest {

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

    private val strengthDay get() = examples.getValue("example-strength-day")

    private fun started(id: String, at: Long = 1_000): WorkoutActiveRun =
        WorkoutActiveRun(workoutId = id, workoutSnapshot = examples.getValue(id), createdAtEpochSeconds = 900)
            .withSessionStartedAtPosition(at)

    private fun set(reps: Int) = WeightSet(reps = reps)

    private fun WorkoutActiveRun.play(from: Long, vararg events: WorkoutSessionEvent): WorkoutActiveRun =
        events.foldIndexed(this) { index, run, event -> run.applyingSessionEvent(event, from + 60L * index) }

    private fun WorkoutActiveRun.step() = WorkoutSessionEngine.currentStep(sessionPlan(), session!!)

    private fun WorkoutActiveRun.logged(segmentId: String, itemId: String): List<Int> =
        WorkoutSessionEngine.loggedSets(sessionPlan(), session!!, segmentId, itemId).map { it.reps }

    private fun restsOf(plan: WorkoutSessionPlan, itemId: String) =
        plan.steps.filterIsInstance<WorkoutSessionStep.Rest>().filter { it.itemId == itemId }.map { it.kind to it.seconds }

    // Rest timer

    @Test
    fun off_drops_the_rests_between_sets_and_exercises_but_keeps_planned_and_section_rests() {
        val plan = WorkoutSessionPlan.from(strengthDay, WorkoutSessionRestOptions(WorkoutSessionRestMode.OFF, 90))
        val rests = plan.steps.filterIsInstance<WorkoutSessionStep.Rest>()
        assertTrue(rests.none { it.isTimerRest() })
        assertTrue(rests.any { it.kind == WorkoutSessionRestKind.PLANNED })
        assertTrue(rests.any { it.kind == WorkoutSessionRestKind.AFTER_SECTION })
    }

    @Test
    fun the_default_rest_fills_in_only_where_the_workout_gives_none() {
        val plan = WorkoutSessionPlan.from(strengthDay, WorkoutSessionRestOptions(WorkoutSessionRestMode.AUTO, 90))
        // The squat's own rests stand.
        assertEquals(
            listOf(
                WorkoutSessionRestKind.BETWEEN_SETS to 180,
                WorkoutSessionRestKind.BETWEEN_SETS to 180,
                WorkoutSessionRestKind.BETWEEN_EXERCISES to 120,
            ),
            restsOf(plan, "strength-squat"),
        )
        // The row has none, so it rests the default between sets and before the note.
        assertEquals(
            listOf(
                WorkoutSessionRestKind.BETWEEN_SETS to 90,
                WorkoutSessionRestKind.BETWEEN_SETS to 90,
                WorkoutSessionRestKind.BETWEEN_EXERCISES to 90,
            ),
            restsOf(plan, "strength-row"),
        )
        // As planned, without a setting.
        assertEquals(emptyList<Pair<WorkoutSessionRestKind, Int>>(), restsOf(WorkoutSessionPlan.from(strengthDay), "strength-row"))
    }

    @Test
    fun no_default_rest_right_before_a_rest_the_builder_placed() {
        val workout = Workout(
            id = "w",
            name = "W",
            segments = listOf(
                WorkoutSegment(
                    id = "s",
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(id = "a", exerciseId = "x", prescription = WorkoutWeightPrescription(setCount = 2)),
                        WorkoutItem.Rest(id = "r", durationSeconds = 60),
                        WorkoutItem.Weight(id = "b", exerciseId = "y", prescription = WorkoutWeightPrescription(setCount = 1)),
                    ),
                ),
            ),
        )
        val plan = WorkoutSessionPlan.from(workout, WorkoutSessionRestOptions(WorkoutSessionRestMode.AUTO, 90))
        assertEquals(listOf(WorkoutSessionRestKind.BETWEEN_SETS to 90), restsOf(plan, "a"))
    }

    @Test
    fun manual_rests_wait_for_start_and_auto_starts_one_left_waiting() {
        val manual = started("example-strength-day")
            .withSessionRestOptions(WorkoutSessionRestOptions(WorkoutSessionRestMode.MANUAL, 90))
            .play(1_060, WorkoutSessionEvent.SetLogged(set(5)))
        assertEquals(WorkoutSessionRestKind.BETWEEN_SETS, (manual.step() as WorkoutSessionStep.Rest).kind)
        assertTrue(manual.sessionStepAwaitingStart)
        assertNull(manual.sessionCountdownEndsAtEpochSeconds())

        val auto = manual.withSessionRestOptions(WorkoutSessionRestOptions(WorkoutSessionRestMode.AUTO, 90))
        assertFalse(auto.sessionStepAwaitingStart)
        assertTrue(auto.sessionCountdownEndsAtEpochSeconds() != null)
    }

    @Test
    fun changing_the_rest_timer_mid_run_keeps_the_sets_already_logged() {
        val onSecondRest = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(5)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(set(4)),
        )
        assertTrue(onSecondRest.step() is WorkoutSessionStep.Rest)
        val off = onSecondRest.withSessionRestOptions(WorkoutSessionRestOptions(WorkoutSessionRestMode.OFF, 90))
        // The rest on screen is gone, so the last squat set comes up.
        val step = off.step() as WorkoutSessionStep.LiftSet
        assertEquals("strength-squat", step.itemId)
        assertEquals(2, step.setIndex)
        assertEquals(listOf(5, 4), off.logged("strength-main", "strength-squat"))
    }

    // Remove any set

    @Test
    fun a_logged_set_can_come_out_and_the_rest_move_up() {
        val onThirdSet = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(5)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(set(6)),
            WorkoutSessionEvent.StepDone,
        )
        assertTrue(onThirdSet.canRemoveSessionSetAt(0))
        val removed = onThirdSet.withSessionSetRemoved(0)
        val step = removed.step() as WorkoutSessionStep.LiftSet
        assertEquals("strength-squat", step.itemId)
        assertEquals(1, step.setIndex)
        assertEquals(2, step.setCount)
        assertEquals(listOf(6), removed.logged("strength-main", "strength-squat"))
        // Only one rest between the two sets left.
        assertEquals(1, restsOf(removed.sessionPlan(), "strength-squat").count { it.first == WorkoutSessionRestKind.BETWEEN_SETS })
    }

    @Test
    fun remove_set_on_the_last_set_ends_the_exercise_with_what_was_logged() {
        val onThirdSet = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(5)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(set(5)),
            WorkoutSessionEvent.StepDone,
        )
        val last = onThirdSet.sessionLastOpenSetIndex()!!
        assertEquals(onThirdSet.session!!.stepIndex, last)
        val removed = onThirdSet.withSessionSetRemoved(last)
        assertEquals(listOf(5, 5), removed.logged("strength-main", "strength-squat"))
        assertEquals(WorkoutSessionRestKind.BETWEEN_EXERCISES, (removed.step() as WorkoutSessionStep.Rest).kind)
    }

    @Test
    fun the_last_remaining_set_and_sets_of_other_sections_stay() {
        val run = started("example-strength-day")
        val plan = run.sessionPlan()
        val accessorySet = plan.steps.indexOfFirst { it is WorkoutSessionStep.LiftSet && it.segmentId == "strength-accessories" }
        assertFalse(run.canRemoveSessionSetAt(accessorySet))
        val oneSet = run.withSessionSetRemoved(run.sessionLastOpenSetIndex()!!)
            .let { it.withSessionSetRemoved(it.sessionLastOpenSetIndex()!!) }
        assertEquals(1, (oneSet.step() as WorkoutSessionStep.LiftSet).setCount)
        assertFalse(oneSet.canRemoveSessionSetAt(oneSet.session!!.stepIndex))
    }

    // Standalone workouts

    private val bench = WeightExercise(id = "bench", name = "Bench", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL)
    private val ohp = WeightExercise(id = "ohp", name = "Press", muscleGroup = "shoulders", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL)
    private val library = mapOf(bench.id to bench, ohp.id to ohp)

    @Test
    fun an_empty_workout_grows_as_exercises_are_added_and_ends_only_on_finish() {
        val empty = standaloneRun(standaloneWorkout(null, { null }), routine = null, restOptions = null, nowEpochSeconds = 1_000)
        assertEquals(STANDALONE_EMPTY_WORKOUT_NAME, empty.workoutSnapshot.name)
        assertTrue(isStandaloneWorkoutId(empty.workoutId))
        assertEquals(0, empty.sessionPlan().size)
        assertTrue(empty.isSessionWaitingForMore())
        assertFalse(empty.isSessionComplete())

        val added = empty.withSessionItemsAdded(
            listOf(
                WorkoutSessionAddition.Lift("bench", bench, lastSetCount = 2),
                WorkoutSessionAddition.Lift("ohp", ohp),
                WorkoutSessionAddition.Lift("bench", bench, lastSetCount = 1),
            ),
            newItemIds = listOf("a", "b", "c"),
            newSegmentIds = listOf("s1", "s2", "s3"),
        )
        // One section, in the order picked, with plain sets: as many as last time, else three.
        assertEquals(listOf("s1"), added.workoutSnapshot.segments.map { it.id })
        assertEquals(listOf("a", "b", "c"), added.workoutSnapshot.segments.single().items.map { it.id })
        val lifts = added.sessionPlan().steps.filterIsInstance<WorkoutSessionStep.LiftSet>()
        assertEquals(listOf(2, 3, 1), listOf("a", "b", "c").map { id -> lifts.first { it.itemId == id }.setCount })
        assertTrue(added.workoutSnapshot.segments.single().weightItems().all { it.prescription.targetReps == null && it.prescription.repRangeMin == null })
        assertEquals("a", added.step()?.itemId)

        val allDone = added.play(1_060, *Array(6) { WorkoutSessionEvent.SetLogged(set(8)) })
        assertTrue(allDone.isSessionWaitingForMore())
        assertFalse(allDone.isSessionComplete())
        // Nothing goes into the log until Finish, so the workout is one weight entry.
        assertTrue(allDone.sessionLiftSectionsToLog().isEmpty())

        // More can still be added at the end.
        val more = allDone.withSessionItemsAdded(listOf(WorkoutSessionAddition.Lift("ohp", ohp)), listOf("d"), listOf("s4"))
        assertEquals("d", more.step()?.itemId)

        val finished = allDone.play(2_000, WorkoutSessionEvent.EndWorkout).copy(sessionFinishRequested = true)
        assertTrue(finished.isSessionComplete())
        val sections = finished.sessionLiftSectionsToLog()
        assertEquals(1, sections.size)
        assertEquals(3, sections.single().entries.size)
    }

    @Test
    fun a_routine_starts_as_plain_sets_and_logs_under_its_name() {
        val routine = WeightRoutine(id = "push", name = "Push Day", exerciseIds = listOf("bench", "gone", "bench", "ohp"))
        val workout = standaloneWorkout(routine, library::get, lastSetCount = { if (it == "bench") 4 else null })
        assertEquals("Push Day", workout.name)
        val items = workout.segments.single().items.filterIsInstance<WorkoutItem.Weight>()
        assertEquals(listOf("bench", "ohp"), items.map { it.exerciseId })
        assertEquals(listOf(4, 3), items.map { it.prescription.setCount })

        val run = standaloneRun(workout, routine, restOptions = null, nowEpochSeconds = 1_000)
        assertEquals("push", run.standalone?.routineId)
        val finished = run.play(1_060, *Array(7) { WorkoutSessionEvent.SetLogged(set(5)) })
            .play(2_000, WorkoutSessionEvent.EndWorkout)
            .copy(sessionFinishRequested = true)
        val logged = finished.weightSessionFor(finished.sessionLiftSectionsToLog().single())
        assertEquals("push", logged.routineId)
        assertEquals("Push Day", logged.routineName)
        assertEquals(run.sessionId, logged.workoutLink?.sessionId)
    }

    @Test
    fun a_standalone_run_survives_without_a_library_copy() {
        val run = standaloneRun(standaloneWorkout(null, { null }), routine = null, restOptions = null)
        assertEquals(run, WorkoutLibraryState(activeRun = run).sanitized().activeRun)
        val libraryRun = run.copy(standalone = null)
        assertNull(WorkoutLibraryState(activeRun = libraryRun).sanitized().activeRun)
    }
}
