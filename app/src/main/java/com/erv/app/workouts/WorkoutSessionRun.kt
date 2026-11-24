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
    if (startedAtEpochSeconds == null || position == WorkoutRunPosition()) return started
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
    if (handled.isEmpty()) return started
    val records = handled.map { WorkoutStepRecord(it, WorkoutStepOutcome.SKIPPED) }
    var next = 0
    val recorded = handled.toSet()
    while (next < plan.size && next in recorded) next++
    return started.copy(session = WorkoutSessionState(stepIndex = next, records = records))
}

/**
 * [event] applied to this run, with the bookkeeping kept in step: the storyboard position follows
 * the session, items skipped in a finished section get a "skipped" recap, finished sections are
 * listed, and moving onto a cardio or stretch step asks the run screen to open it (those still run
 * on their own screens).
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
    val opensOwnScreen = step is WorkoutSessionStep.CardioLeg || step is WorkoutSessionStep.StretchHold
    val newItem = step?.itemId != previousStep?.itemId || step?.segmentId != previousStep?.segmentId
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
    )
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
