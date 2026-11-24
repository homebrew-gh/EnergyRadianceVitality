package com.erv.app.workouts

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
    return copy(sessionStepAwaitingStart = first.waitsForStart(null, newItem = true))
}

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
    val waitsForStart = step.waitsForStart(previousStep, newItem)
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
 * legs of one cardio item and a run of stretches flow on by themselves.
 */
internal fun WorkoutSessionStep?.waitsForStart(previousStep: WorkoutSessionStep?, newItem: Boolean): Boolean =
    when (this) {
        is WorkoutSessionStep.CardioLeg -> cardioRoutineId == null && newItem
        is WorkoutSessionStep.StretchHold -> previousStep !is WorkoutSessionStep.StretchHold
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
)

/**
 * Finished sections with lifts done that have no weight log entry yet. A section counts once every
 * step of it is done or skipped, its own rest included, so a section is logged exactly once.
 */
fun WorkoutActiveRun.sessionLiftSectionsToLog(plan: WorkoutSessionPlan = sessionPlan()): List<WorkoutSessionLiftSection> {
    val state = session ?: return emptyList()
    val linked = itemRecaps.filter { !it.skipped && it.linkedEntryId != null }.map { it.segmentId to it.itemId }.toSet()
    return workoutSnapshot.segments.mapIndexedNotNull { segmentIndex, segment ->
        if (!WorkoutSessionEngine.isSectionFinished(plan, state, segmentIndex)) return@mapIndexedNotNull null
        val entries = segment.items.filterIsInstance<WorkoutItem.Weight>()
            .filter { (segment.id to it.id) !in linked }
            .mapNotNull { item ->
                val sets = WorkoutSessionEngine.loggedSets(plan, state, segment.id, item.id)
                    .map { it.copy(done = null) }
                if (sets.isEmpty()) return@mapNotNull null
                WorkoutSessionLiftEntry(
                    itemId = item.id,
                    exerciseId = sessionExerciseSwaps[sessionItemKey(segment.id, item.id)] ?: item.exerciseId,
                    sets = sets,
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
        entries = section.entries.map { WeightWorkoutEntry(exerciseId = it.exerciseId, sets = it.sets) },
        workoutLink = linkFor(section.segmentId, section.entries.first().itemId),
    )
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

/** True once the session has run out of steps. */
fun WorkoutActiveRun.isSessionComplete(plan: WorkoutSessionPlan = sessionPlan()): Boolean =
    session?.let { WorkoutSessionEngine.isComplete(plan, it) } == true

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

/** True when the item on screen can take another set (or its circuit another round). */
fun WorkoutActiveRun.canAddSessionSet(plan: WorkoutSessionPlan = sessionPlan()): Boolean {
    val state = session ?: return false
    val step = WorkoutSessionEngine.currentStep(plan, state) as? WorkoutSessionStep.LiftSet ?: return false
    return step.setCount < SESSION_MAX_SET_COUNT
}

/**
 * Adds a set to the exercise on screen ([delta] 1) or takes its last set away ([delta] -1), for
 * today only. In a superset or circuit a set is a round, so the whole section gains or loses one.
 * The change goes into the run's own copy of the workout; sets already logged keep their records.
 */
fun WorkoutActiveRun.withSessionSetCountChanged(delta: Int): WorkoutActiveRun {
    if (delta == 0) return this
    val plan = sessionPlan()
    val state = session ?: return this
    val step = WorkoutSessionEngine.currentStep(plan, state) as? WorkoutSessionStep.LiftSet ?: return this
    if (delta > 0 && !canAddSessionSet(plan)) return this
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
    return withSessionWorkout(workoutSnapshot.copy(segments = segments))
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
 * Replaces the run's workout copy and moves the session's records onto the plan built from it,
 * step by step (see [stepKeys]). The step on screen stays on screen.
 */
internal fun WorkoutActiveRun.withSessionWorkout(snapshot: Workout): WorkoutActiveRun {
    val state = session ?: return copy(workoutSnapshot = snapshot)
    val oldKeys = sessionPlan().stepKeys()
    val newPlan = WorkoutSessionPlan.from(snapshot)
    val newIndexByKey = newPlan.stepKeys().withIndex().associate { it.value to it.index }
    val records = state.records.mapNotNull { record ->
        oldKeys.getOrNull(record.stepIndex)?.let(newIndexByKey::get)?.let { record.copy(stepIndex = it) }
    }
    val recorded = records.map { it.stepIndex }.toSet()
    val stepIndex = oldKeys.getOrNull(state.stepIndex)?.let(newIndexByKey::get)
        ?: newPlan.steps.indices.firstOrNull { it !in recorded }
        ?: newPlan.size
    // Steps that land behind the one on screen (an exercise added to a circuit already in its
    // second round, say) were never done, so they count as skipped and the section can finish.
    val passed = (0 until stepIndex).filter { it !in recorded }.map { WorkoutStepRecord(it, WorkoutStepOutcome.SKIPPED) }
    return copy(workoutSnapshot = snapshot, session = state.copy(stepIndex = stepIndex, records = records + passed))
}

/** Something added to today's run from the session screen. */
sealed class WorkoutSessionAddition {
    /** [exercise] is the library entry, so a timed exercise gets a hold target instead of reps. */
    data class Lift(
        val exerciseId: String,
        val exercise: com.erv.app.weighttraining.WeightExercise? = null,
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
    val step = WorkoutSessionEngine.currentStep(sessionPlan(), state) ?: return this
    val segmentIndex = workoutSnapshot.segments.indexOfFirst { it.id == step.segmentId }
    val segment = workoutSnapshot.segments.getOrNull(segmentIndex) ?: return this
    val itemIndex = step.itemId?.let { id -> segment.items.indexOfFirst { it.id == id } } ?: -1
    // Into this section only after an item of it: on the rest after the whole section the new
    // steps would land behind the one on screen.
    val intoSection = addition.fitsIn(segment.kind) && itemIndex >= 0
    val nearbyLift = (segment.items.getOrNull(itemIndex) as? WorkoutItem.Weight)
        ?: segment.weightItems().lastOrNull()
    val item: WorkoutItem = when (addition) {
        is WorkoutSessionAddition.Lift -> WorkoutItem.Weight(
            id = newItemId,
            exerciseId = addition.exerciseId,
            prescription = defaultWorkoutPrescriptionForExercise(
                addition.exercise,
                if (intoSection) segment.kind else WorkoutSegmentKind.STRAIGHT_SETS,
            ).copy(
                restBetweenSetsSeconds = nearbyLift?.prescription?.restBetweenSetsSeconds,
                restAfterExerciseSeconds = nearbyLift?.prescription?.restAfterExerciseSeconds,
            ),
        )
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
    val segments = if (intoSection) {
        val items = segment.items.toMutableList().apply { add(itemIndex + 1, item) }
        workoutSnapshot.segments.map { if (it.id == segment.id) it.copy(items = items) else it }
    } else {
        val added = WorkoutSegment(id = newSegmentId, kind = addition.newSegmentKind(), items = listOf(item))
        workoutSnapshot.segments.toMutableList().apply { add(segmentIndex + 1, added) }
    }
    return withSessionWorkout(workoutSnapshot.copy(segments = segments))
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
