package com.erv.app.workouts

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

/** The run bookkeeping around the session: position, recaps and one weight log per lifting section. */
class WorkoutSessionRunTest {

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

    private fun started(id: String, at: Long = 1_000): WorkoutActiveRun =
        WorkoutActiveRun(workoutId = id, workoutSnapshot = examples.getValue(id), createdAtEpochSeconds = 900)
            .withSessionStartedAtPosition(at)

    private fun set(reps: Int, kg: Double? = null) = WeightSet(reps = reps, weightKg = kg)

    /** Applies each event one minute after the last. */
    private fun WorkoutActiveRun.play(from: Long, vararg events: WorkoutSessionEvent): WorkoutActiveRun =
        events.foldIndexed(this) { index, run, event -> run.applyingSessionEvent(event, from + 60L * index) }

    private fun WorkoutActiveRun.step() = WorkoutSessionEngine.currentStep(sessionPlan(), session!!)

    /** Squat 3 sets with rests, both rests before bench, bench 3 sets, row skipped, note read, section rest. */
    private fun WorkoutActiveRun.finishMainSection(): WorkoutActiveRun = play(
        1_060,
        WorkoutSessionEvent.SetLogged(set(5, 102.5)), WorkoutSessionEvent.StepDone,
        WorkoutSessionEvent.SetLogged(set(5, 102.5)), WorkoutSessionEvent.StepDone,
        WorkoutSessionEvent.SetLogged(set(4, 102.5)), WorkoutSessionEvent.StepDone, WorkoutSessionEvent.StepDone,
        WorkoutSessionEvent.SetLogged(set(8, 61.2)), WorkoutSessionEvent.StepDone,
        WorkoutSessionEvent.SetLogged(set(8, 61.2)), WorkoutSessionEvent.StepDone,
        WorkoutSessionEvent.SetLogged(set(6, 65.8)),
        WorkoutSessionEvent.SkipItem,
        WorkoutSessionEvent.StepDone,
        WorkoutSessionEvent.StepDone,
    )

    @Test
    fun the_storyboard_position_follows_the_session() {
        val run = started("example-strength-day")
        assertEquals(WorkoutRunPosition(0, 0, 1), run.position)
        val onBench = run.play(
            1_060,
            WorkoutSessionEvent.SkipItem,
            WorkoutSessionEvent.StepDone,
        )
        assertEquals("strength-bench", onBench.step()?.itemId)
        assertEquals(WorkoutRunPosition(segmentIndex = 0, itemIndex = 2, round = 1), onBench.position)

        val done = run.play(1_060, WorkoutSessionEvent.EndWorkout)
        assertEquals(WorkoutRunPosition(segmentIndex = 2), done.position)
        assertTrue(done.isSessionComplete())
    }

    @Test
    fun a_lifting_section_is_logged_once_its_rest_is_over() {
        val beforeRest = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.EndSection,
        )
        // The section's own rest is still on screen, so nothing is logged yet.
        assertTrue(beforeRest.step() is WorkoutSessionStep.Rest)
        assertTrue(beforeRest.sessionLiftSectionsToLog().isEmpty())

        val run = started("example-strength-day").finishMainSection()
        assertEquals("strength-split-squat", run.step()?.itemId)
        val sections = run.sessionLiftSectionsToLog()
        assertEquals(1, sections.size)
        val section = sections.single()
        assertEquals("strength-main", section.segmentId)
        assertEquals(listOf("strength-squat", "strength-bench"), section.entries.map { it.itemId })
        assertEquals(listOf(5, 5, 4), section.entries[0].sets.map { it.reps })
        assertEquals(listOf(8, 8, 6), section.entries[1].sets.map { it.reps })
        assertEquals(1_000L, section.startedAtEpochSeconds)
        assertEquals(1_060L + 60L * 14, section.finishedAtEpochSeconds)

        val logged = run.withSessionLiftLogged(section, "2026-11-24", "weight-log-1", 2_000)
        assertTrue(logged.sessionLiftSectionsToLog().isEmpty())
        val linked = logged.itemRecaps.filter { it.linkedEntryId == "weight-log-1" }.map { it.itemId }
        assertEquals(listOf("strength-squat", "strength-bench"), linked)
    }

    @Test
    fun the_weight_log_entry_is_linked_and_timed() {
        val run = started("example-strength-day").finishMainSection()
        val section = run.sessionLiftSectionsToLog().single()
        val session = run.weightSessionFor(section)
        assertEquals(listOf("erv-weight-exercise-squat-v1", "erv-weight-exercise-bench-v1"), session.entries.map { it.exerciseId })
        assertEquals(1_000L, session.startedAtEpochSeconds)
        assertEquals(900, session.durationSeconds)
        assertEquals("strength-main", session.workoutLink?.segmentId)
        assertEquals("strength-squat", session.workoutLink?.itemId)
        assertEquals(run.sessionId, session.workoutLink?.sessionId)
        assertTrue(session.entries.flatMap { it.sets }.all { it.done == null })
    }

    @Test
    fun skipped_items_get_a_recap_when_their_section_finishes() {
        val midway = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(5, 102.5)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SkipItem,
        )
        assertTrue(midway.itemRecaps.isEmpty())

        val run = started("example-strength-day").finishMainSection()
        val skipped = run.itemRecaps.filter { it.skipped }
        assertEquals(listOf("strength-row"), skipped.map { it.itemId })
        assertEquals(WorkoutLoggedItemKind.WEIGHT, skipped.single().kind)
        assertTrue("strength-main" in run.completedSegmentIds)
    }

    @Test
    fun a_swapped_exercise_is_what_gets_logged() {
        val swapped = started("example-strength-day").let {
            it.copy(
                sessionExerciseSwaps = mapOf(
                    sessionItemKey("strength-main", "strength-bench") to "erv-weight-exercise-ohp-v1",
                ),
            )
        }
        val lift = swapped.sessionPlan().steps.filterIsInstance<WorkoutSessionStep.LiftSet>()
            .first { it.itemId == "strength-bench" }
        assertEquals("erv-weight-exercise-ohp-v1", swapped.sessionExerciseFor(lift))
        val entry = swapped.finishMainSection().sessionLiftSectionsToLog().single().entries
            .single { it.itemId == "strength-bench" }
        assertEquals("erv-weight-exercise-ohp-v1", entry.exerciseId)
    }

    @Test
    fun ending_early_logs_what_was_done_and_skips_the_rest() {
        val run = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(5, 102.5)),
            WorkoutSessionEvent.EndWorkout,
        )
        assertTrue(run.isSessionComplete())
        val section = run.sessionLiftSectionsToLog().single()
        assertEquals(listOf("strength-squat"), section.entries.map { it.itemId })
        val skipped = run.itemRecaps.filter { it.skipped }.map { it.itemId }.toSet()
        assertEquals(
            setOf("strength-bench", "strength-row", "strength-split-squat", "strength-plank", "strength-dips"),
            skipped,
        )
        assertFalse(run.sessionHasNothingDone())
    }

    @Test
    fun cardio_and_stretch_steps_open_their_own_screens() {
        val run = started("example-cardio-and-mobility")
        // The first step waits for Start; nothing opens by itself.
        assertFalse(run.autoAdvanceRequested)
        val bikeDone = run.copy(lastLaunchedSegmentId = "cardio-warmup", lastLaunchedItemId = "cardio-warmup-bike")
            .withSessionItemLoggedElsewhere(
                segmentId = "cardio-warmup",
                itemId = "cardio-warmup-bike",
                kind = WorkoutLoggedItemKind.CARDIO,
                logDate = "2026-11-24",
                entryId = "cardio-log-1",
                nowEpochSeconds = 1_400,
            )
        assertEquals("cardio-warmup-neck", bikeDone.step()?.itemId)
        assertTrue(bikeDone.autoAdvanceRequested)
        assertNull(bikeDone.lastLaunchedItemId)
        assertEquals(WorkoutRunPosition(segmentIndex = 0, itemIndex = 1, round = 1), bikeDone.position)
        val recap = bikeDone.itemRecaps.single()
        assertEquals("cardio-log-1", recap.linkedEntryId)
        assertFalse(recap.skipped)
        assertEquals(WorkoutStepOutcome.DONE, bikeDone.session!!.recordFor(0)?.outcome)
        assertEquals(1_400L, bikeDone.session!!.recordFor(0)?.atEpochSeconds)
        // Cardio and stretching log themselves; there is no weight entry to write.
        assertTrue(bikeDone.sessionLiftSectionsToLog().isEmpty())
    }

    @Test
    fun a_rest_before_a_cardio_section_holds_it_until_the_rest_is_over() {
        val plan = started("example-cardio-and-mobility").sessionPlan()
        val sectionRest = plan.steps.indexOfFirst {
            it is WorkoutSessionStep.Rest && it.kind == WorkoutSessionRestKind.AFTER_SECTION
        }
        val atRest = started("example-cardio-and-mobility").copy(
            session = WorkoutSessionState(
                stepIndex = sectionRest,
                records = (0 until sectionRest).map { WorkoutStepRecord(it, WorkoutStepOutcome.DONE) },
            ),
        )
        val afterRest = atRest.applyingSessionEvent(WorkoutSessionEvent.StepDone, 3_000)
        assertTrue(afterRest.step() is WorkoutSessionStep.StretchHold)
        assertTrue(afterRest.autoAdvanceRequested)
        // Moving between two holds of the same stretch is not a new screen to open.
        val nextSide = afterRest.copy(autoAdvanceRequested = false)
            .applyingSessionEvent(WorkoutSessionEvent.TimedStepDone(30), 3_030)
        assertEquals(afterRest.step()?.itemId, nextSide.step()?.itemId)
        assertFalse(nextSide.autoAdvanceRequested)
    }

    @Test
    fun a_run_from_before_the_session_picks_up_at_its_section() {
        val old = WorkoutActiveRun(
            workoutId = "example-strength-day",
            workoutSnapshot = examples.getValue("example-strength-day"),
            startedAtEpochSeconds = 500,
            position = WorkoutRunPosition(segmentIndex = 1),
        )
        val run = old.withSessionStartedAtPosition(1_000)
        assertEquals("strength-split-squat", run.step()?.itemId)
        assertEquals(500L, run.startedAtEpochSeconds)
        assertTrue(run.sessionLiftSectionsToLog().isEmpty())
        assertTrue(run.sessionHasNothingDone())

        val midSection = old.copy(position = WorkoutRunPosition(segmentIndex = 0, itemIndex = 2))
            .withSessionStartedAtPosition(1_000)
        assertEquals("strength-bench", midSection.step()?.itemId)
    }

    @Test
    fun late_events_dont_change_a_run_without_a_session() {
        val idle = WorkoutActiveRun(workoutId = "w", workoutSnapshot = examples.getValue("example-strength-day"))
        assertEquals(idle, idle.applyingSessionEvent(WorkoutSessionEvent.EndWorkout, 1_000))
        assertTrue(idle.sessionLiftSectionsToLog().isEmpty())
        assertFalse(idle.isSessionComplete())
    }

    @Test
    fun a_set_can_be_added_or_taken_away_for_today() {
        val onSecond = started("example-strength-day").play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(5, 102.5)),
            WorkoutSessionEvent.StepDone,
        )
        val second = onSecond.step() as WorkoutSessionStep.LiftSet
        assertEquals(1, second.setIndex)
        assertEquals(3, second.setCount)
        assertTrue(onSecond.canAddSessionSet())
        assertTrue(onSecond.canRemoveSessionSet())

        val added = onSecond.withSessionSetCountChanged(1)
        val onScreen = added.step() as WorkoutSessionStep.LiftSet
        assertEquals(1, onScreen.setIndex)
        assertEquals(4, onScreen.setCount)
        val squat = added.workoutSnapshot.segments[0].items[0] as WorkoutItem.Weight
        assertEquals(4, squat.prescription.setCount)
        // The set already logged keeps its record.
        assertEquals(
            listOf(set(5, 102.5)),
            WorkoutSessionEngine.loggedSets(added.sessionPlan(), added.session!!, "strength-main", "strength-squat"),
        )

        val removed = added.withSessionSetCountChanged(-1).withSessionSetCountChanged(-1)
        assertEquals(2, (removed.step() as WorkoutSessionStep.LiftSet).setCount)
        // The set on screen is now the last one: it can be skipped, not taken away.
        assertFalse(removed.canRemoveSessionSet())
        assertEquals(removed, removed.withSessionSetCountChanged(-1))
        val next = removed.play(2_000, WorkoutSessionEvent.SetLogged(set(5, 102.5)))
        assertEquals(WorkoutSessionRestKind.BETWEEN_EXERCISES, (next.step() as WorkoutSessionStep.Rest).kind)

        // Only today's copy changes; the workout in the library keeps its three sets.
        val planned = examples.getValue("example-strength-day").segments[0].items[0] as WorkoutItem.Weight
        assertEquals(3, planned.prescription.setCount)
    }

    @Test
    fun an_added_set_copies_the_last_planned_one() {
        val bench = (examples.getValue("example-strength-day").segments[0].items[2] as WorkoutItem.Weight).prescription
        val more = bench.withSetCountChange(1)
        assertEquals(4, more.sets.size)
        assertEquals(bench.sets.last(), more.sets.last())
        assertEquals(bench.sets.take(2), bench.withSetCountChange(-1).sets)
        assertEquals(1, WorkoutWeightPrescription(setCount = 1).withSetCountChange(-1).setCount)
    }

    @Test
    fun in_a_superset_a_set_is_a_round() {
        val run = started("example-pairs-and-circuit")
        val first = run.step() as WorkoutSessionStep.LiftSet
        assertTrue(first.inRounds)
        assertEquals(3, first.setCount)

        val fewer = run.withSessionSetCountChanged(-1)
        assertEquals(2, fewer.workoutSnapshot.segments[0].rounds)
        assertEquals(2, (fewer.step() as WorkoutSessionStep.LiftSet).setCount)

        // Into round 2 of 2: the last round has started, so it can't be taken away.
        val lastRound = fewer.play(
            1_060,
            WorkoutSessionEvent.SetLogged(set(8)),
            WorkoutSessionEvent.StepDone,
            WorkoutSessionEvent.SetLogged(set(6)),
            WorkoutSessionEvent.StepDone,
        )
        val onLastRound = lastRound.step() as WorkoutSessionStep.LiftSet
        assertEquals("pairs-ohp", onLastRound.itemId)
        assertEquals(2, onLastRound.round)
        assertFalse(lastRound.canRemoveSessionSet())

        val more = lastRound.withSessionSetCountChanged(1)
        val onScreen = more.step() as WorkoutSessionStep.LiftSet
        assertEquals(2, onScreen.round)
        assertEquals(3, onScreen.setCount)
        assertEquals(
            listOf(set(8)),
            WorkoutSessionEngine.loggedSets(more.sessionPlan(), more.session!!, "pairs-superset", "pairs-ohp"),
        )
    }
}
