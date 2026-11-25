package com.erv.app.workouts

import com.erv.app.weighttraining.WeightExerciseFocusMark
import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutEntry
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource

/**
 * The bookkeeping around the session engine that the rest of the app still reads: the storyboard
 * position, the per-item recaps the summary and history use, and one weight log entry per lifting
 * section. Pure, so it is unit tested; [WorkoutRepository] applies it.
 */

/** Key for one storyboard item in [WorkoutActiveRun.sessionExerciseSwaps]. */
fun sessionItemKey(segmentId: String, itemId: String): String = "$segmentId/$itemId"

/** The exercise the athlete is doing for this lift step today (a swap, or the planned one). */
fun WorkoutActiveRun.sessionExerciseFor(step: WorkoutSessionStep.LiftSet): String =
    sessionExerciseSwaps[sessionItemKey(step.segmentId, step.itemId)] ?: step.exerciseId

/**
 * Starts the session for a run, or keeps one already under way. A run begun on an older version
 * of the app, before the session existed, picks up at its storyboard position: earlier sections
 * (and earlier items of the current plain section) count as already handled. Their logs and
 * recaps are already saved, so they are marked skipped here and never logged twice.
 */
fun WorkoutActiveRun.withSessionStartedAtPosition(nowEpochSeconds: Long): WorkoutActiveRun {
    if (session != null) return this
    val started = withSessionStarted(nowEpochSeconds)
    if (startedAtEpochSeconds == null || position == WorkoutRunPosition()) return started.withStartGate()
    val plan = sessionPlan()
    val segments = workoutSnapshot.segments
    val current = segments.getOrNull(position.segmentIndex)
    val currentIsRounds = current?.kind == WorkoutSegmentKind.SUPERSET || current?.kind == WorkoutSegmentKind.CIRCUIT
    val itemIndexById = current?.items?.withIndex()?.associate { it.value.id to it.index }.orEmpty()
    val handled = plan.steps.indices.filter { index ->
        val step = plan.steps[index]
        when {
            step.segmentIndex < position.segmentIndex -> true
            step.segmentIndex > position.segmentIndex || currentIsRounds -> false
            else -> {
                val itemIndex = step.itemId?.let { itemIndexById[it] } ?: return@filter false
                itemIndex < position.itemIndex
            }
        }
    }
    if (handled.isEmpty()) return started.withStartGate()
    val records = handled.map { WorkoutStepRecord(it, WorkoutStepOutcome.SKIPPED) }
    var next = 0
    val recorded = handled.toSet()
    while (next < plan.size && next in recorded) next++
    return started.copy(session = WorkoutSessionState(stepIndex = next, records = records)).withStartGate()
}

/** The step the session opens on waits for Start when it is cardio or a stretch. */
private fun WorkoutActiveRun.withStartGate(): WorkoutActiveRun {
    val first = session?.let { WorkoutSessionEngine.currentStep(sessionPlan(), it) }
    return copy(sessionStepAwaitingStart = first.waitsForStart(null, newItem = true, manualRest = manualRests()))
}

/** True when the rest timer is set to Manual, so rests wait for Start Rest. */
private fun WorkoutActiveRun.manualRests(): Boolean = sessionRestOptions?.mode == WorkoutSessionRestMode.MANUAL

/**
 * [event] applied to this run, with the bookkeeping kept in step: the storyboard position follows
 * the session, items skipped in a finished section get a "skipped" recap, finished sections are
 * listed, a new cardio item or stretch waits for Start, and moving onto a saved cardio routine
 * asks the run screen to open the cardio screen, which still runs those.
 */
fun WorkoutActiveRun.applyingSessionEvent(event: WorkoutSessionEvent, nowEpochSeconds: Long): WorkoutActiveRun {
    val before = session ?: return this
    val moved = withSessionEvent(event, nowEpochSeconds)
    if (moved === this) return this
    return moved.withSessionBookkeeping(before, nowEpochSeconds)
}

internal fun WorkoutActiveRun.withSessionBookkeeping(
    before: WorkoutSessionState,
    nowEpochSeconds: Long,
): WorkoutActiveRun {
    val state = session ?: return this
    val plan = sessionPlan()
    val workout = workoutSnapshot
    val finishedSegments = workout.segments.indices.filter { index ->
        plan.steps.any { it.segmentIndex == index } && WorkoutSessionEngine.isSectionFinished(plan, state, index)
    }
    val recapKeys = itemRecaps.map { it.segmentId to it.itemId }.toSet()
    val skippedRecaps = finishedSegments.flatMap { segmentIndex ->
        val segment = workout.segments[segmentIndex]
        segment.items.filter { item ->
            (item is WorkoutItem.Weight || item is WorkoutItem.Cardio || item is WorkoutItem.Mobility) &&
                (segment.id to item.id) !in recapKeys &&
                plan.itemRecords(state, segment.id, item.id).let { records ->
                    records.isNotEmpty() && records.all { it?.outcome == WorkoutStepOutcome.SKIPPED }
                }
        }.map { skippedRecap(segment.id, it, nowEpochSeconds) }
    }
    val step = WorkoutSessionEngine.currentStep(plan, state)
    val previousStep = WorkoutSessionEngine.currentStep(plan, before)
    val stepChanged = state.stepIndex != before.stepIndex
    val newItem = step?.itemId != previousStep?.itemId || step?.segmentId != previousStep?.segmentId
    // A saved cardio routine still runs on the cardio screen; everything else stays here.
    val opensOwnScreen = step is WorkoutSessionStep.CardioLeg && step.cardioRoutineId != null
    val waitsForStart = step.waitsForStart(previousStep, newItem, manualRests())
    return copy(
        position = plan.positionFor(workout, state),
        itemRecaps = itemRecaps + skippedRecaps,
        completedSegmentIds = (completedSegmentIds + finishedSegments.map { workout.segments[it].id }).distinct(),
        lastLaunchedSegmentId = if (stepChanged) null else lastLaunchedSegmentId,
        lastLaunchedItemId = if (stepChanged) null else lastLaunchedItemId,
        lastLaunchedItemIds = if (stepChanged) emptyList() else lastLaunchedItemIds,
        // The session's own section rest replaces the old between-section banner.
        pendingNextSegmentTitle = null,
        pendingCompletedSegmentTitle = null,
        autoAdvanceRequested = if (stepChanged) opensOwnScreen && newItem else autoAdvanceRequested,
        sessionStepAwaitingStart = if (stepChanged) waitsForStart else sessionStepAwaitingStart,
    )
}

/**
 * A new cardio item waits for Start, and so does a stretch that doesn't follow another hold; the
 * legs of one cardio item and a run of stretches flow on by themselves. With the rest timer on
 * Manual, a rest between sets or exercises waits for Start Rest.
 */
internal fun WorkoutSessionStep?.waitsForStart(
    previousStep: WorkoutSessionStep?,
    newItem: Boolean,
    manualRest: Boolean = false,
): Boolean =
    when (this) {
        is WorkoutSessionStep.CardioLeg -> cardioRoutineId == null && newItem
        is WorkoutSessionStep.StretchHold -> previousStep !is WorkoutSessionStep.StretchHold
        is WorkoutSessionStep.Rest -> manualRest && isTimerRest()
        else -> false
    }

/** The record (or null while open) of every step of one item. */
private fun WorkoutSessionPlan.itemRecords(
    state: WorkoutSessionState,
    segmentId: String,
    itemId: String,
): List<WorkoutStepRecord?> =
    steps.indices
        .filter { steps[it].segmentId == segmentId && steps[it].itemId == itemId }
        .map { state.recordFor(it) }

/**
 * The storyboard position of the session's current step, in [WorkoutRunEngine]'s terms, for the
 * screens that still read it. A rest after a whole section sits on that section's last item.
 */
internal fun WorkoutSessionPlan.positionFor(workout: Workout, state: WorkoutSessionState): WorkoutRunPosition {
    val step = WorkoutSessionEngine.currentStep(this, state)
        ?: return WorkoutRunPosition(segmentIndex = workout.segments.size)
    val segment = workout.segments.getOrNull(step.segmentIndex)
        ?: return WorkoutRunPosition(segmentIndex = step.segmentIndex)
    val isRounds = segment.kind == WorkoutSegmentKind.SUPERSET || segment.kind == WorkoutSegmentKind.CIRCUIT
    val items = if (isRounds) segment.weightItems() else segment.items
    val itemIndex = step.itemId?.let { id -> items.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        ?: items.lastIndex.coerceAtLeast(0)
    return WorkoutRunPosition(segmentIndex = step.segmentIndex, itemIndex = itemIndex, round = step.round)
}

/** One lifting section the session finished whose sets are not in the weight log yet. */
data class WorkoutSessionLiftSection(
    val segmentId: String,
    val segmentIndex: Int,
    val entries: List<WorkoutSessionLiftEntry>,
    val startedAtEpochSeconds: Long?,
    val finishedAtEpochSeconds: Long?,
)

/** The sets done for one weight item, under the exercise actually done. */
data class WorkoutSessionLiftEntry(
    val itemId: String,
    val exerciseId: String,
    val sets: List<WeightSet>,
    /** Set when the exercise was done on the interval timer; [sets] is then empty. */
    val hiitBlock: WeightHiitBlockLog? = null,
)

/**
 * Finished sections with lifts done that have no weight log entry yet. A section counts once every
 * step of it is done or skipped, its own rest included, so a section is logged exactly once.
 */
fun WorkoutActiveRun.sessionLiftSectionsToLog(plan: WorkoutSessionPlan = sessionPlan()): List<WorkoutSessionLiftSection> {
    val state = session ?: return emptyList()
    // A standalone workout can grow until Finish, so it goes into the log once, at the end.
    if (standalone != null && !sessionFinishRequested) return emptyList()
    val linked = itemRecaps.filter { !it.skipped && it.linkedEntryId != null }.map { it.segmentId to it.itemId }.toSet()
    return workoutSnapshot.segments.mapIndexedNotNull { segmentIndex, segment ->
        if (!WorkoutSessionEngine.isSectionFinished(plan, state, segmentIndex)) return@mapIndexedNotNull null
        val entries = segment.items.filterIsInstance<WorkoutItem.Weight>()
            .filter { (segment.id to it.id) !in linked }
            .mapNotNull { item ->
                val hiitBlock = sessionHiitBlocks[sessionItemKey(segment.id, item.id)]
                val sets = if (hiitBlock != null) {
                    emptyList()
                } else {
                    WorkoutSessionEngine.loggedSets(plan, state, segment.id, item.id).map { it.copy(done = null) }
                }
                if (sets.isEmpty() && hiitBlock == null) return@mapNotNull null
                WorkoutSessionLiftEntry(
                    itemId = item.id,
                    exerciseId = sessionExerciseSwaps[sessionItemKey(segment.id, item.id)] ?: item.exerciseId,
                    sets = sets,
                    hiitBlock = hiitBlock,
                )
            }
        if (entries.isEmpty()) return@mapIndexedNotNull null
        val segmentSteps = plan.steps.indices.filter { plan.steps[it].segmentIndex == segmentIndex }.toSet()
        val finishedAt = state.records.filter { it.stepIndex in segmentSteps }.mapNotNull { it.atEpochSeconds }.maxOrNull()
        val firstStep = segmentSteps.minOrNull() ?: 0
        // The section began when the step before it finished, or when the workout began.
        val startedAt = state.records.filter { it.stepIndex < firstStep }.mapNotNull { it.atEpochSeconds }.maxOrNull()
            ?: startedAtEpochSeconds
        WorkoutSessionLiftSection(
            segmentId = segment.id,
            segmentIndex = segmentIndex,
            entries = entries,
            startedAtEpochSeconds = startedAt,
            finishedAtEpochSeconds = finishedAt,
        )
    }
}

/** The weight log entry for [section], linked to this run like a weight screen section was. */
fun WorkoutActiveRun.weightSessionFor(section: WorkoutSessionLiftSection): WeightWorkoutSession {
    val started = section.startedAtEpochSeconds
    val finished = section.finishedAtEpochSeconds
    return WeightWorkoutSession(
        source = WeightWorkoutSource.LIVE,
        startedAtEpochSeconds = started,
        finishedAtEpochSeconds = finished,
        durationSeconds = if (started != null && finished != null && finished >= started) {
            (finished - started).toInt()
        } else {
            null
        },
        // A workout started from a weight routine logs like the weight screen did, under the routine.
        routineId = standalone?.routineId,
        routineName = standalone?.routineName,
        entries = section.entries.map { WeightWorkoutEntry(exerciseId = it.exerciseId, sets = it.sets, hiitBlock = it.hiitBlock) },
        workoutLink = linkFor(section.segmentId, section.entries.first().itemId),
    )
}

/**
 * When each exercise of a lifting section began, for heart rate per exercise as the weight
 * screen kept it: an exercise begins when the step before its first one was finished (or when
 * the workout began). Exercises with nothing logged are left out.
 */
fun WorkoutActiveRun.sessionExerciseFocusMarks(
    segmentId: String,
    plan: WorkoutSessionPlan = sessionPlan(),
): List<WeightExerciseFocusMark> {
    val state = session ?: return emptyList()
    val segment = workoutSnapshot.segments.firstOrNull { it.id == segmentId } ?: return emptyList()
    return segment.weightItems().mapNotNull { item ->
        val steps = plan.steps.indices.filter {
            plan.steps[it] is WorkoutSessionStep.LiftSet && plan.steps[it].segmentId == segmentId && plan.steps[it].itemId == item.id
        }
        val first = steps.minOrNull() ?: return@mapNotNull null
        if (steps.none { state.recordFor(it)?.outcome == WorkoutStepOutcome.DONE }) return@mapNotNull null
        val began = state.records.filter { it.stepIndex < first }.mapNotNull { it.atEpochSeconds }.maxOrNull()
            ?: startedAtEpochSeconds
            ?: return@mapNotNull null
        WeightExerciseFocusMark(
            exerciseId = sessionExerciseSwaps[sessionItemKey(segmentId, item.id)] ?: item.exerciseId,
            epochSeconds = began,
        )
    }.sortedBy { it.epochSeconds }
}

/** Links the section's items to the weight log entry just saved for it. */
fun WorkoutActiveRun.withSessionLiftLogged(
    section: WorkoutSessionLiftSection,
    logDate: String,
    entryId: String,
    nowEpochSeconds: Long,
): WorkoutActiveRun {
    val keys = section.entries.map { section.segmentId to it.itemId }.toSet()
    val recaps = section.entries.map {
        WorkoutItemRecap(
            segmentId = section.segmentId,
            itemId = it.itemId,
            kind = WorkoutLoggedItemKind.WEIGHT,
            linkedLogDate = logDate,
            linkedEntryId = entryId,
            finishedAtEpochSeconds = section.finishedAtEpochSeconds ?: nowEpochSeconds,
        )
    }
    return copy(itemRecaps = itemRecaps.filterNot { (it.segmentId to it.itemId) in keys } + recaps)
}

/**
 * A cardio or stretch section logged on its own screen: its session steps are done, the item is
 * linked to its log, and the run moves on.
 */
fun WorkoutActiveRun.withSessionItemLoggedElsewhere(
    segmentId: String,
    itemId: String,
    kind: WorkoutLoggedItemKind,
    logDate: String,
    entryId: String,
    nowEpochSeconds: Long,
): WorkoutActiveRun {
    val before = session ?: return this
    val recap = WorkoutItemRecap(
        segmentId = segmentId,
        itemId = itemId,
        kind = kind,
        linkedLogDate = logDate,
        linkedEntryId = entryId,
        finishedAtEpochSeconds = nowEpochSeconds,
    )
    val linked = copy(
        itemRecaps = itemRecaps.filterNot { it.segmentId == segmentId && it.itemId == itemId } + recap,
        lastLaunchedSegmentId = null,
        lastLaunchedItemId = null,
        lastLaunchedItemIds = emptyList(),
    )
    val moved = linked.withSessionEvent(WorkoutSessionEvent.ItemLoggedElsewhere(segmentId, itemId), nowEpochSeconds)
    return moved.withSessionBookkeeping(before, nowEpochSeconds)
}

/**
 * True when the exercise on screen can be done on the interval timer instead: a plain (not
 * superset or circuit) exercise with no set logged yet.
 */
fun WorkoutActiveRun.canLogSessionHiitBlock(plan: WorkoutSessionPlan = sessionPlan()): Boolean {
    val state = session ?: return false
    val step = WorkoutSessionEngine.currentStep(plan, state) as? WorkoutSessionStep.LiftSet ?: return false
    if (step.inRounds) return false
    return WorkoutSessionEngine.loggedSets(plan, state, step.segmentId, step.itemId).isEmpty()
}

/**
 * The exercise on screen done on the interval timer: [block] stands in for its sets, every step
 * of it is done, and the run moves on.
 */
fun WorkoutActiveRun.withSessionHiitBlockLogged(block: WeightHiitBlockLog, nowEpochSeconds: Long): WorkoutActiveRun {
    val plan = sessionPlan()
    if (!canLogSessionHiitBlock(plan)) return this
    val before = session ?: return this
    val step = WorkoutSessionEngine.currentStep(plan, before) as WorkoutSessionStep.LiftSet
    val logged = copy(sessionHiitBlocks = sessionHiitBlocks + (sessionItemKey(step.segmentId, step.itemId) to block))
    val moved = logged.withSessionEvent(WorkoutSessionEvent.ItemLoggedElsewhere(step.segmentId, step.itemId), nowEpochSeconds)
    return moved.withSessionBookkeeping(before, nowEpochSeconds)
}

/**
 * True once the session has run out of steps. A standalone workout also needs Finish, since the
 * athlete may add another exercise when the last one is done.
 */
fun WorkoutActiveRun.isSessionComplete(plan: WorkoutSessionPlan = sessionPlan()): Boolean =
    session?.let { WorkoutSessionEngine.isComplete(plan, it) } == true &&
        (!waitsForFinish() || sessionFinishRequested)

/** A standalone workout with nothing left to do but not finished: it waits for an exercise or Finish. */
fun WorkoutActiveRun.isSessionWaitingForMore(plan: WorkoutSessionPlan = sessionPlan()): Boolean =
    waitsForFinish() && !sessionFinishRequested &&
        session?.let { WorkoutSessionEngine.isComplete(plan, it) } == true

/**
 * A standalone workout waits for Finish when its steps run out. One started from Cardio ends with
 * its cardio, unless the athlete added something else to it.
 */
private fun WorkoutActiveRun.waitsForFinish(): Boolean {
    val start = standalone ?: return false
    if (start.cardioDraft == null) return true
    return workoutSnapshot.segments.any { segment -> segment.items.any { it !is WorkoutItem.Cardio } }
}

/** True when nothing has been done in the session yet (skips don't count). */
fun WorkoutActiveRun.sessionHasNothingDone(): Boolean =
    session?.records.orEmpty().none { it.outcome == WorkoutStepOutcome.DONE }

/** Most sets (or rounds) one item can be taken to during a run. */
const val SESSION_MAX_SET_COUNT = 20

/**
 * A name for each step that stays the same when a set or round is added or taken away, so the
 * records of steps already done can follow them into the new plan. Steps that share a name (the
 * rests between sets of one exercise) are numbered in order.
 */
internal fun WorkoutSessionPlan.stepKeys(): List<String> {
    val seen = HashMap<String, Int>()
    return steps.map { step ->
        val base = when (step) {
            is WorkoutSessionStep.LiftSet -> "lift/${step.segmentId}/${step.itemId}/${step.round}/${step.setIndex}"
            // The rest after a section carries the round count, which a new round changes.
            is WorkoutSessionStep.Rest -> if (step.kind == WorkoutSessionRestKind.AFTER_SECTION) {
                "rest-after/${step.segmentId}"
            } else {
                "rest/${step.segmentId}/${step.itemId}/${step.round}/${step.kind}"
            }
            is WorkoutSessionStep.CardioLeg -> "cardio/${step.segmentId}/${step.itemId}/${step.round}"
            is WorkoutSessionStep.StretchHold -> "stretch/${step.segmentId}/${step.itemId}/${step.round}"
            is WorkoutSessionStep.Note -> "note/${step.segmentId}/${step.itemId}"
        }
        val occurrence = seen.getOrDefault(base, 0)
        seen[base] = occurrence + 1
        "$base#$occurrence"
    }
}

/**
 * True when the item on screen can lose its last set (or its circuit its last round): more than
 * one is left and nothing of the last one has been logged or skipped yet.
 */
fun WorkoutActiveRun.canRemoveSessionSet(plan: WorkoutSessionPlan = sessionPlan()): Boolean {
    val state = session ?: return false
    val step = WorkoutSessionEngine.currentStep(plan, state) as? WorkoutSessionStep.LiftSet ?: return false
    if (step.setCount <= 1) return false
    return if (step.inRounds) {
        if (step.round >= step.setCount) return false
        plan.steps.indices
            .filter { plan.steps[it].segmentId == step.segmentId && plan.steps[it].round == step.setCount }
            .filterNot { (plan.steps[it] as? WorkoutSessionStep.Rest)?.kind == WorkoutSessionRestKind.AFTER_SECTION }
            .all { it > state.stepIndex && state.recordFor(it) == null }
    } else {
        val lastSet = plan.steps.indices.lastOrNull {
            val other = plan.steps[it]
            other is WorkoutSessionStep.LiftSet && other.segmentId == step.segmentId && other.itemId == step.itemId
        } ?: return false
        lastSet > state.stepIndex && state.recordFor(lastSet) == null
    }
}

/**
 * The lift set whose exercise the screen shows: the set on screen, or during a rest (and while a
 * standalone workout waits for more) the set just before it, so a rest never changes the screen.
 * Null when the step on screen is cardio, a stretch or a note.
 */
fun WorkoutActiveRun.sessionFocusLiftIndex(plan: WorkoutSessionPlan = sessionPlan()): Int? {
    val state = session ?: return null
    val current = WorkoutSessionEngine.currentStep(plan, state)
    if (current is WorkoutSessionStep.LiftSet) return state.stepIndex
    if (current != null && current !is WorkoutSessionStep.Rest) return null
    for (index in (state.stepIndex - 1).coerceAtMost(plan.size - 1) downTo 0) {
        when (plan.steps[index]) {
            is WorkoutSessionStep.LiftSet -> return index
            is WorkoutSessionStep.Rest -> continue
            else -> return null
        }
    }
    return null
}

/**
 * True when the item of the lift set at [liftStepIndex] (the one on screen by default) can take
 * another set (or its circuit another round): it has room, and it isn't in the weight log yet.
 */
fun WorkoutActiveRun.canAddSessionSet(
    plan: WorkoutSessionPlan = sessionPlan(),
    liftStepIndex: Int? = session?.stepIndex,
): Boolean {
    if (session == null) return false
    val step = liftStepIndex?.let { plan.steps.getOrNull(it) } as? WorkoutSessionStep.LiftSet ?: return false
    if (itemRecaps.any { it.segmentId == step.segmentId && it.itemId == step.itemId && it.linkedEntryId != null }) {
        return false
    }
    return step.setCount < SESSION_MAX_SET_COUNT
}

/**
 * Adds a set to the exercise on screen ([delta] 1) or takes its last set away ([delta] -1), for
 * today only. In a superset or circuit a set is a round, so the whole section gains or loses one.
 * The change goes into the run's own copy of the workout; sets already logged keep their records.
 */
fun WorkoutActiveRun.withSessionSetCountChanged(delta: Int, liftStepIndex: Int? = null): WorkoutActiveRun {
    if (delta == 0) return this
    val plan = sessionPlan()
    val state = session ?: return this
    // A set can be added to the exercise shown during a rest too; taking one away stays with the step on screen.
    val focusIndex = if (delta > 0) liftStepIndex ?: state.stepIndex else state.stepIndex
    val step = plan.steps.getOrNull(focusIndex) as? WorkoutSessionStep.LiftSet ?: return this
    if (delta > 0 && !canAddSessionSet(plan, focusIndex)) return this
    if (delta < 0 && !canRemoveSessionSet(plan)) return this
    val change = delta.coerceIn(-1, 1)
    val segments = workoutSnapshot.segments.map { segment ->
        when {
            segment.id != step.segmentId -> segment
            step.inRounds -> segment.copy(rounds = (segment.effectiveRounds() + change).coerceAtLeast(1))
            else -> segment.copy(
                items = segment.items.map { item ->
                    if (item is WorkoutItem.Weight && item.id == step.itemId) {
                        item.copy(prescription = item.prescription.withSetCountChange(change))
                    } else {
                        item
                    }
                },
            )
        }
    }
    val changed = withSessionWorkout(workoutSnapshot.copy(segments = segments))
    return if (change > 0) changed.withSessionBackOnNewSet(this) else changed
}

/**
 * After a set (or round) was added to an exercise already behind the step on screen (added in
 * the rest after its last set, or while a standalone workout waits for more), the run goes back
 * to the new set instead of counting it as skipped. Steps passed over to get there are skipped.
 */
private fun WorkoutActiveRun.withSessionBackOnNewSet(before: WorkoutActiveRun): WorkoutActiveRun {
    val state = session ?: return this
    val oldKeys = before.sessionPlan().stepKeys().toSet()
    val plan = sessionPlan()
    val keys = plan.stepKeys()
    val isNew = { index: Int -> keys.getOrNull(index)?.let { it !in oldKeys } == true }
    val added = plan.steps.indices.firstOrNull { isNew(it) && plan.steps[it] is WorkoutSessionStep.LiftSet } ?: return this
    // A new rest that landed on screen (a standalone workout waiting for more) gives way to the set too.
    val newRestOnScreen = isNew(state.stepIndex) && plan.steps.getOrNull(state.stepIndex) is WorkoutSessionStep.Rest
    if (added == state.stepIndex || (added > state.stepIndex && !newRestOnScreen)) return this
    val kept = state.records.filterNot { it.stepIndex >= added && isNew(it.stepIndex) }
    val recorded = kept.map { it.stepIndex }.toSet()
    val passed = (0 until added).filter { it !in recorded }.map { WorkoutStepRecord(it, WorkoutStepOutcome.SKIPPED) }
    return copy(
        session = state.copy(stepIndex = added, records = (kept + passed).sortedBy { it.stepIndex }),
        sessionStepStartedAtEpochSeconds = nowWorkoutEpochSeconds(),
        sessionStepAwaitingStart = false,
    )
}

/**
 * Logs [set] for the lift set right after the rest on screen: the rest is cut short and the set
 * recorded, as Skip Rest then Log Set would. Does nothing unless a rest is on screen with a lift
 * set after it.
 */
fun WorkoutActiveRun.withSessionRestCutForSet(set: WeightSet, nowEpochSeconds: Long): WorkoutActiveRun {
    val plan = sessionPlan()
    val state = session ?: return this
    if (WorkoutSessionEngine.currentStep(plan, state) !is WorkoutSessionStep.Rest) return this
    if (plan.steps.getOrNull(state.stepIndex + 1) !is WorkoutSessionStep.LiftSet) return this
    val rested = applyingSessionEvent(WorkoutSessionEvent.StepDone, nowEpochSeconds)
    if (rested.session?.stepIndex != state.stepIndex + 1) return this
    return rested.applyingSessionEvent(WorkoutSessionEvent.SetLogged(set), nowEpochSeconds)
}

/** One more set copied from the last, or the last one taken away; never fewer than one. */
internal fun WorkoutWeightPrescription.withSetCountChange(delta: Int): WorkoutWeightPrescription {
    if (sets.isNotEmpty()) {
        val updated = if (delta > 0) sets + sets.last() else sets.dropLast(1).ifEmpty { sets.take(1) }
        return copy(sets = updated, setCount = setCount?.let { updated.size })
    }
    return copy(setCount = ((setCount ?: 1) + delta).coerceAtLeast(1))
}

/**
 * Replaces the run's workout copy (and its rest timer setting) and moves the session's records
 * onto the plan built from it, step by step (see [stepKeys]). [renameKey] turns an old step's key
 * into its key in the new plan, or null for a step that is gone. The step on screen stays on
 * screen; if it is gone, the first step still to do takes its place.
 */
internal fun WorkoutActiveRun.withSessionWorkout(
    snapshot: Workout,
    restOptions: WorkoutSessionRestOptions? = sessionRestOptions,
    renameKey: (String) -> String? = { it },
): WorkoutActiveRun {
    val state = session ?: return copy(workoutSnapshot = snapshot, sessionRestOptions = restOptions)
    val oldKeys = sessionPlan().stepKeys().map(renameKey)
    val newPlan = WorkoutSessionPlan.from(snapshot, restOptions)
    val newIndexByKey = newPlan.stepKeys().withIndex().associate { it.value to it.index }
    val records = state.records.mapNotNull { record ->
        oldKeys.getOrNull(record.stepIndex)?.let(newIndexByKey::get)?.let { record.copy(stepIndex = it) }
    }
    val recorded = records.map { it.stepIndex }.toSet()
    val onScreen = oldKeys.getOrNull(state.stepIndex)?.let(newIndexByKey::get)
    val stepIndex = onScreen
        ?: newPlan.steps.indices.firstOrNull { it !in recorded }
        ?: newPlan.size
    // Steps that land behind the one on screen (an exercise added to a circuit already in its
    // second round, say) were never done, so they count as skipped and the section can finish.
    val passed = (0 until stepIndex).filter { it !in recorded }.map { WorkoutStepRecord(it, WorkoutStepOutcome.SKIPPED) }
    val moved = copy(
        workoutSnapshot = snapshot,
        sessionRestOptions = restOptions,
        session = state.copy(stepIndex = stepIndex, records = records + passed),
    )
    if (onScreen != null) return moved
    // A different step is on screen now: its clock starts, and it may wait for Start.
    val step = WorkoutSessionEngine.currentStep(newPlan, moved.session!!)
    return moved.copy(
        sessionStepStartedAtEpochSeconds = nowWorkoutEpochSeconds(),
        sessionStepAwaitingStart = step.waitsForStart(null, newItem = true, manualRest = moved.manualRests()),
    )
}

/**
 * Today's run with a new rest timer setting. The rests on screen and still to come follow it;
 * rests already done keep their records. Changing it on a rest that goes away moves on.
 */
fun WorkoutActiveRun.withSessionRestOptions(options: WorkoutSessionRestOptions): WorkoutActiveRun {
    if (options == sessionRestOptions) return this
    val updated = withSessionWorkout(workoutSnapshot, restOptions = options)
    val state = updated.session ?: return updated
    val step = WorkoutSessionEngine.currentStep(updated.sessionPlan(), state)
    // Switched to Manual on a rest already counting down: let it run. Switched away from Manual:
    // a rest waiting for Start begins now.
    return if (updated.sessionStepAwaitingStart && step is WorkoutSessionStep.Rest && options.mode != WorkoutSessionRestMode.MANUAL) {
        updated.copy(sessionStepAwaitingStart = false, sessionStepStartedAtEpochSeconds = nowWorkoutEpochSeconds())
    } else {
        updated
    }
}

/**
 * True when the lift set at [stepIndex] can come out of today's run: a set of a plain (not
 * superset or circuit) exercise in the section on screen, logged or still to come, while the
 * exercise has another set and its section isn't in the weight log yet.
 */
fun WorkoutActiveRun.canRemoveSessionSetAt(stepIndex: Int, plan: WorkoutSessionPlan = sessionPlan()): Boolean {
    val state = session ?: return false
    val step = plan.steps.getOrNull(stepIndex) as? WorkoutSessionStep.LiftSet ?: return false
    if (step.inRounds || step.setCount <= 1) return false
    val current = WorkoutSessionEngine.currentStep(plan, state)
    // A standalone workout is only logged at Finish, so any of its exercises can lose a set,
    // also while it waits for more. A library workout's sections are logged as they finish.
    if (standalone == null && (current == null || current.segmentId != step.segmentId)) return false
    if (state.recordFor(stepIndex)?.outcome == WorkoutStepOutcome.SKIPPED) return false
    return itemRecaps.none { it.segmentId == step.segmentId && it.itemId == step.itemId && it.linkedEntryId != null }
}

/**
 * The last set of the exercise on screen that is still to come (at the latest, the one on
 * screen): what Remove Set takes away. Null in a superset or circuit, where a set is a round.
 */
fun WorkoutActiveRun.sessionLastOpenSetIndex(plan: WorkoutSessionPlan = sessionPlan()): Int? {
    val state = session ?: return null
    val step = WorkoutSessionEngine.currentStep(plan, state) as? WorkoutSessionStep.LiftSet ?: return null
    if (step.inRounds) return null
    return plan.steps.indices.lastOrNull {
        val other = plan.steps[it]
        other is WorkoutSessionStep.LiftSet && other.segmentId == step.segmentId && other.itemId == step.itemId &&
            it >= state.stepIndex && state.recordFor(it) == null
    }
}

/**
 * Takes one set out of today's run, logged or still to come (see [canRemoveSessionSetAt]): the
 * exercise loses that set and one rest between its sets, and the sets after it move up.
 */
fun WorkoutActiveRun.withSessionSetRemoved(stepIndex: Int): WorkoutActiveRun {
    val plan = sessionPlan()
    if (!canRemoveSessionSetAt(stepIndex, plan)) return this
    val step = plan.steps[stepIndex] as WorkoutSessionStep.LiftSet
    val removed = step.setIndex
    val lastSet = step.setCount - 1
    val segments = workoutSnapshot.segments.map { segment ->
        if (segment.id != step.segmentId) return@map segment
        segment.copy(
            items = segment.items.map { item ->
                if (item is WorkoutItem.Weight && item.id == step.itemId) {
                    item.copy(prescription = item.prescription.withSetRemoved(removed))
                } else {
                    item
                }
            },
        )
    }
    val liftPrefix = "lift/${step.segmentId}/${step.itemId}/1/"
    val restPrefix = "rest/${step.segmentId}/${step.itemId}/1/${WorkoutSessionRestKind.BETWEEN_SETS}#"
    // The rest after the removed set goes with it (the one before it, for the last set).
    val removedRest = if (removed < lastSet) removed else removed - 1
    return withSessionWorkout(workoutSnapshot.copy(segments = segments)) { key ->
        when {
            key.startsWith(liftPrefix) -> {
                val index = key.removePrefix(liftPrefix).substringBefore('#').toIntOrNull() ?: return@withSessionWorkout key
                when {
                    index == removed -> null
                    index > removed -> "$liftPrefix${index - 1}#0"
                    else -> key
                }
            }
            key.startsWith(restPrefix) -> {
                val occurrence = key.removePrefix(restPrefix).toIntOrNull() ?: return@withSessionWorkout key
                when {
                    occurrence == removedRest -> null
                    occurrence > removedRest -> "$restPrefix${occurrence - 1}"
                    else -> key
                }
            }
            else -> key
        }
    }
}

/** This prescription without set [index]; never fewer than one set. */
internal fun WorkoutWeightPrescription.withSetRemoved(index: Int): WorkoutWeightPrescription {
    if (sets.isNotEmpty()) {
        if (sets.size <= 1 || index !in sets.indices) return this
        val updated = sets.toMutableList().apply { removeAt(index) }
        return copy(sets = updated, setCount = setCount?.let { updated.size })
    }
    return copy(setCount = ((setCount ?: 1) - 1).coerceAtLeast(1))
}

/** Something added to today's run from the session screen. */
sealed class WorkoutSessionAddition {
    /** [exercise] is the library entry, so a timed exercise gets a hold target instead of reps. */
    data class Lift(
        val exerciseId: String,
        val exercise: com.erv.app.weighttraining.WeightExercise? = null,
        /** Sets logged for it last time; a standalone workout starts with that many. */
        val lastSetCount: Int? = null,
    ) : WorkoutSessionAddition()

    /** A steady effort; [minutes] null runs open-ended until Finish. */
    data class Cardio(val activity: String, val minutes: Int?) : WorkoutSessionAddition()

    /** [perSide] holds each side for [holdSeconds], as a both-sides stretch does in its routine. */
    data class Stretch(val catalogId: String, val holdSeconds: Int, val perSide: Boolean) : WorkoutSessionAddition()
}

private fun WorkoutSessionAddition.fitsIn(kind: WorkoutSegmentKind): Boolean = when (this) {
    is WorkoutSessionAddition.Lift -> kind == WorkoutSegmentKind.STRAIGHT_SETS ||
        kind == WorkoutSegmentKind.SUPERSET || kind == WorkoutSegmentKind.CIRCUIT
    is WorkoutSessionAddition.Cardio -> kind == WorkoutSegmentKind.CARDIO || kind == WorkoutSegmentKind.COMPOSITE
    is WorkoutSessionAddition.Stretch -> kind == WorkoutSegmentKind.MOBILITY || kind == WorkoutSegmentKind.COMPOSITE
}

private fun WorkoutSessionAddition.newSegmentKind(): WorkoutSegmentKind = when (this) {
    is WorkoutSessionAddition.Lift -> WorkoutSegmentKind.STRAIGHT_SETS
    is WorkoutSessionAddition.Cardio -> WorkoutSegmentKind.CARDIO
    is WorkoutSessionAddition.Stretch -> WorkoutSegmentKind.MOBILITY
}

/**
 * Adds several items after the one on screen, in the order given, as if each were added in turn
 * right after the one before it (see [withSessionItemAdded]).
 */
fun WorkoutActiveRun.withSessionItemsAdded(
    additions: List<WorkoutSessionAddition>,
    newItemIds: List<String> = additions.map { java.util.UUID.randomUUID().toString() },
    newSegmentIds: List<String> = additions.map { java.util.UUID.randomUUID().toString() },
): WorkoutActiveRun {
    val state = session ?: return this
    if (additions.isEmpty()) return this
    // Each one goes in right after the item on screen, so adding them last to first keeps the
    // order. With nothing on screen (an empty or used-up standalone workout) the first goes at
    // the end and comes on screen, and the rest follow it the same way.
    val nothingOnScreen = WorkoutSessionEngine.currentStep(sessionPlan(), state) == null
    val first = if (nothingOnScreen) withSessionItemAdded(additions[0], newItemIds[0], newSegmentIds[0]) else this
    val rest = if (nothingOnScreen) 1 until additions.size else additions.indices
    return rest.reversed().fold(first) { run, i -> run.withSessionItemAdded(additions[i], newItemIds[i], newSegmentIds[i]) }
}

/**
 * Adds [addition] to today's run right after the item on screen: into the same section when it
 * fits there (a lift into a lifting section, cardio into a cardio section), otherwise as a new
 * section right after the current one. In a superset or circuit a lift joins the rounds from the
 * current round on. A new lift gets the builder's default sets and the rests of the lift nearby.
 */
fun WorkoutActiveRun.withSessionItemAdded(
    addition: WorkoutSessionAddition,
    newItemId: String = java.util.UUID.randomUUID().toString(),
    newSegmentId: String = java.util.UUID.randomUUID().toString(),
): WorkoutActiveRun {
    val state = session ?: return this
    val step = WorkoutSessionEngine.currentStep(sessionPlan(), state)
    if (step == null && standalone == null) return this
    // Nothing on screen in a standalone workout: the new item goes at the end, into the last
    // section when it is plain sets of the same sort.
    val segmentIndex = if (step != null) {
        workoutSnapshot.segments.indexOfFirst { it.id == step.segmentId }
    } else {
        workoutSnapshot.segments.lastIndex
    }
    val segment = workoutSnapshot.segments.getOrNull(segmentIndex)
    if (step != null && segment == null) return this
    val itemIndex = when {
        segment == null -> -1
        step != null -> step.itemId?.let { id -> segment.items.indexOfFirst { it.id == id } } ?: -1
        segment.kind == WorkoutSegmentKind.SUPERSET || segment.kind == WorkoutSegmentKind.CIRCUIT -> -1
        else -> segment.items.lastIndex
    }
    // Into this section only after an item of it: on the rest after the whole section the new
    // steps would land behind the one on screen.
    val intoSection = segment != null && addition.fitsIn(segment.kind) && itemIndex >= 0
    val nearbyLift = (segment?.items?.getOrNull(itemIndex) as? WorkoutItem.Weight)
        ?: segment?.weightItems()?.lastOrNull()
    val item: WorkoutItem = when (addition) {
        is WorkoutSessionAddition.Lift -> {
            val kind = if (intoSection) segment!!.kind else WorkoutSegmentKind.STRAIGHT_SETS
            // A standalone workout has no targets to copy, so its plain sets start like a routine's.
            val base = if (standalone != null && kind == WorkoutSegmentKind.STRAIGHT_SETS) {
                standaloneLiftPrescription(addition.exercise, addition.lastSetCount)
            } else {
                defaultWorkoutPrescriptionForExercise(addition.exercise, kind)
            }
            WorkoutItem.Weight(
                id = newItemId,
                exerciseId = addition.exerciseId,
                prescription = base.copy(
                    restBetweenSetsSeconds = nearbyLift?.prescription?.restBetweenSetsSeconds,
                    restAfterExerciseSeconds = nearbyLift?.prescription?.restAfterExerciseSeconds,
                ),
            )
        }
        is WorkoutSessionAddition.Cardio -> WorkoutItem.Cardio(
            id = newItemId,
            cardio = WorkoutCardioPrescription(activity = addition.activity, targetMinutes = addition.minutes?.takeIf { it > 0 }),
        )
        is WorkoutSessionAddition.Stretch -> WorkoutItem.Mobility(
            id = newItemId,
            mobility = if (addition.perSide) {
                WorkoutMobilityPrescription(catalogId = addition.catalogId, holdSecondsPerSide = addition.holdSeconds)
            } else {
                WorkoutMobilityPrescription(catalogId = addition.catalogId, holdSeconds = addition.holdSeconds)
            },
        )
    }
    val segments = if (intoSection && segment != null) {
        val items = segment.items.toMutableList().apply { add(itemIndex + 1, item) }
        workoutSnapshot.segments.map { if (it.id == segment.id) it.copy(items = items) else it }
    } else {
        val added = WorkoutSegment(id = newSegmentId, kind = addition.newSegmentKind(), items = listOf(item))
        workoutSnapshot.segments.toMutableList().apply { add(segmentIndex + 1, added) }
    }
    val updated = withSessionWorkout(workoutSnapshot.copy(segments = segments))
    // A finished section that takes a new exercise is open again.
    return if (intoSection && segment != null) {
        updated.copy(completedSegmentIds = updated.completedSegmentIds - segment.id)
    } else {
        updated
    }
}

private fun WorkoutItem.isWork(): Boolean =
    this is WorkoutItem.Weight || this is WorkoutItem.Cardio || this is WorkoutItem.Mobility

/** True when no step of [itemId] (or of the whole section, for null) is on screen, done or skipped. */
private fun WorkoutActiveRun.isUntouched(segmentId: String, itemId: String?, plan: WorkoutSessionPlan): Boolean {
    val state = session ?: return false
    return plan.steps.indices
        .filter { plan.steps[it].segmentId == segmentId && (itemId == null || plan.steps[it].itemId == itemId) }
        .all { it > state.stepIndex && state.recordFor(it) == null }
}

/**
 * True when [itemId] can be taken out of today's run: a lift, cardio or stretch item none of
 * whose steps is on screen, done or skipped yet. The one on screen is skipped instead.
 */
fun WorkoutActiveRun.canRemoveSessionItem(
    segmentId: String,
    itemId: String,
    plan: WorkoutSessionPlan = sessionPlan(),
): Boolean {
    val item = workoutSnapshot.segments.firstOrNull { it.id == segmentId }?.items?.firstOrNull { it.id == itemId }
    if (item == null || !item.isWork()) return false
    val hasSteps = plan.steps.any { it.segmentId == segmentId && it.itemId == itemId }
    return hasSteps && isUntouched(segmentId, itemId, plan)
}

/** Takes [itemId] out of today's run; a section left with nothing to do goes too. */
fun WorkoutActiveRun.withSessionItemRemoved(segmentId: String, itemId: String): WorkoutActiveRun {
    if (!canRemoveSessionItem(segmentId, itemId)) return this
    val segments = workoutSnapshot.segments.mapNotNull { segment ->
        if (segment.id != segmentId) return@mapNotNull segment
        val items = segment.items.filterNot { it.id == itemId }
        if (items.any { it.isWork() }) segment.copy(items = items) else null
    }
    return withSessionWorkout(workoutSnapshot.copy(segments = segments))
}

/**
 * True when [itemId] can trade places with its neighbour in the section ([delta] -1 for up, 1
 * for down): both are still to come, so nothing done moves.
 */
fun WorkoutActiveRun.canMoveSessionItem(
    segmentId: String,
    itemId: String,
    delta: Int,
    plan: WorkoutSessionPlan = sessionPlan(),
): Boolean {
    val segment = workoutSnapshot.segments.firstOrNull { it.id == segmentId } ?: return false
    val index = segment.items.indexOfFirst { it.id == itemId }
    val item = segment.items.getOrNull(index) ?: return false
    val other = segment.items.getOrNull(index + delta) ?: return false
    return item.isWork() && isUntouched(segmentId, item.id, plan) && isUntouched(segmentId, other.id, plan)
}

fun WorkoutActiveRun.withSessionItemMoved(segmentId: String, itemId: String, delta: Int): WorkoutActiveRun {
    if (!canMoveSessionItem(segmentId, itemId, delta)) return this
    val segments = workoutSnapshot.segments.map { segment ->
        if (segment.id != segmentId) return@map segment
        val index = segment.items.indexOfFirst { it.id == itemId }
        val items = segment.items.toMutableList()
        java.util.Collections.swap(items, index, index + delta)
        segment.copy(items = items)
    }
    return withSessionWorkout(workoutSnapshot.copy(segments = segments))
}

/** True when the whole section can trade places with the one before (-1) or after (1): neither has started. */
fun WorkoutActiveRun.canMoveSessionSection(
    segmentId: String,
    delta: Int,
    plan: WorkoutSessionPlan = sessionPlan(),
): Boolean {
    val index = workoutSnapshot.segments.indexOfFirst { it.id == segmentId }
    if (index < 0) return false
    val other = workoutSnapshot.segments.getOrNull(index + delta) ?: return false
    return isUntouched(segmentId, null, plan) && isUntouched(other.id, null, plan)
}

fun WorkoutActiveRun.withSessionSectionMoved(segmentId: String, delta: Int): WorkoutActiveRun {
    if (!canMoveSessionSection(segmentId, delta)) return this
    val index = workoutSnapshot.segments.indexOfFirst { it.id == segmentId }
    val segments = workoutSnapshot.segments.toMutableList()
    java.util.Collections.swap(segments, index, index + delta)
    return withSessionWorkout(workoutSnapshot.copy(segments = segments))
}

/**
 * The workout as it was run today, for "Save Changes to Workout": the run's own copy with each
 * swap made permanent. A swapped item keeps the exercise it replaced as an alternative, and drops
 * a custom title that named the old exercise.
 */
fun WorkoutActiveRun.adjustedWorkout(): Workout {
    val segments = workoutSnapshot.segments.map { segment ->
        segment.copy(
            items = segment.items.map { item ->
                if (item !is WorkoutItem.Weight) return@map item
                val swapped = sessionExerciseSwaps[sessionItemKey(segment.id, item.id)]
                if (swapped == null || swapped == item.exerciseId) return@map item
                item.copy(
                    title = null,
                    exerciseId = swapped,
                    alternativeExerciseIds = (listOf(item.exerciseId) + item.alternativeExerciseIds)
                        .filter { it != swapped }
                        .distinct(),
                )
            },
        )
    }
    return workoutSnapshot.copy(segments = segments)
}

/**
 * What today's run changed compared with [original], one line each: "Added Lat pulldown",
 * "Removed Plank", "Bench press: 3 → 4 sets", "Order changed". Empty when nothing changed.
 */
fun Workout.describeAdjustmentsFrom(original: Workout, nameOf: (WorkoutItem) -> String): List<String> {
    fun items(workout: Workout) = workout.segments.flatMap { segment -> segment.items.filter { it.isWork() } }
    val before = items(original)
    val after = items(this)
    val beforeById = before.associateBy { it.id }
    val afterIds = after.map { it.id }.toSet()
    val lines = mutableListOf<String>()
    after.forEach { item ->
        val old = beforeById[item.id]
        when {
            old == null -> lines += "Added ${nameOf(item)}"
            item is WorkoutItem.Weight && old is WorkoutItem.Weight -> {
                if (item.exerciseId != old.exerciseId) lines += "Swapped ${nameOf(old)} for ${nameOf(item)}"
                val oldSets = old.prescription.resolvedSets().size
                val newSets = item.prescription.resolvedSets().size
                if (oldSets != newSets) lines += "${nameOf(item)}: $oldSets → $newSets sets"
            }
        }
    }
    before.filter { it.id !in afterIds }.forEach { lines += "Removed ${nameOf(it)}" }
    // Items that are in both, in the order each copy runs them.
    val kept = after.map { it.id }.filter { it in beforeById }
    val keptBefore = before.map { it.id }.filter { it in afterIds }
    if (kept != keptBefore) lines += "Order changed"
    return lines
}
