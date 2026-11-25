package com.erv.app.workouts

import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.resolveSnapshot

/**
 * Cardio and stretching done on the session screen, logged to their own silos like the lifting
 * sections are (see [sessionLiftSectionsToLog]): one cardio log entry per cardio item once its
 * legs are over, one stretch log entry per finished section. Pure, so it is unit tested.
 */

/** One cardio item the session finished that has no cardio log entry yet. */
data class WorkoutSessionCardioItem(
    val segmentId: String,
    val itemId: String,
    /** The builder's activity, a [com.erv.app.cardio.CardioBuiltinActivity] name. */
    val activity: String,
    val title: String?,
    /** Work and recovery legs together. */
    val elapsedSeconds: Int,
    val workLegsDone: Int,
    val startedAtEpochSeconds: Long?,
    val finishedAtEpochSeconds: Long?,
)

/** The stretches held in one finished section that have no stretch log entry yet. */
data class WorkoutSessionStretchSection(
    val segmentId: String,
    val entries: List<WorkoutSessionStretchEntry>,
    val heldSeconds: Int,
    val finishedAtEpochSeconds: Long?,
) {
    /** Whole minutes for the stretch log, never zero when something was held. */
    val totalMinutes: Int get() = ((heldSeconds + 59) / 60).coerceAtLeast(1)
}

data class WorkoutSessionStretchEntry(val itemId: String, val catalogId: String)

private fun WorkoutActiveRun.linkedItemKeys(): Set<Pair<String, String>> =
    itemRecaps.filter { !it.skipped && it.linkedEntryId != null }.map { it.segmentId to it.itemId }.toSet()

/**
 * Cardio items whose legs are all done or skipped, with at least one done, that have no cardio log
 * entry yet. An item run on the cardio screen is already linked there, so it never shows up here.
 */
fun WorkoutActiveRun.sessionCardioItemsToLog(plan: WorkoutSessionPlan = sessionPlan()): List<WorkoutSessionCardioItem> {
    val state = session ?: return emptyList()
    val linked = linkedItemKeys()
    return workoutSnapshot.segments.flatMap { segment ->
        segment.items.filterIsInstance<WorkoutItem.Cardio>()
            .filter { (segment.id to it.id) !in linked && it.cardio.cardioRoutineId.isNullOrBlank() }
            .mapNotNull { item ->
                val legs = plan.steps.indices.filter {
                    val step = plan.steps[it]
                    step is WorkoutSessionStep.CardioLeg && step.segmentId == segment.id && step.itemId == item.id
                }
                if (legs.isEmpty()) return@mapNotNull null
                val records = legs.map { state.recordFor(it) ?: return@mapNotNull null }
                val done = legs.zip(records).filter { (_, record) -> record.outcome == WorkoutStepOutcome.DONE }
                if (done.isEmpty()) return@mapNotNull null
                val elapsed = done.sumOf { (_, record) -> record.elapsedSeconds ?: 0 }
                val finished = records.mapNotNull { it.atEpochSeconds }.maxOrNull()
                WorkoutSessionCardioItem(
                    segmentId = segment.id,
                    itemId = item.id,
                    activity = item.cardio.activity,
                    title = item.title?.takeIf { it.isNotBlank() },
                    elapsedSeconds = elapsed,
                    workLegsDone = done.count { (index, _) -> (plan.steps[index] as WorkoutSessionStep.CardioLeg).isRecovery.not() },
                    startedAtEpochSeconds = finished?.minus(elapsed),
                    finishedAtEpochSeconds = finished,
                )
            }
    }
}

/** Links [item] to the cardio log entry just saved for it. */
fun WorkoutActiveRun.withSessionCardioLogged(
    item: WorkoutSessionCardioItem,
    logDate: String,
    entryId: String,
    nowEpochSeconds: Long,
): WorkoutActiveRun {
    val recap = WorkoutItemRecap(
        segmentId = item.segmentId,
        itemId = item.itemId,
        kind = WorkoutLoggedItemKind.CARDIO,
        linkedLogDate = logDate,
        linkedEntryId = entryId,
        finishedAtEpochSeconds = item.finishedAtEpochSeconds ?: nowEpochSeconds,
    )
    return copy(itemRecaps = itemRecaps.filterNot { it.segmentId == item.segmentId && it.itemId == item.itemId } + recap)
}

/**
 * Finished sections with stretches held that have no stretch log entry yet. Like lifting, a
 * section counts once every step of it is done or skipped, its own rest included.
 */
fun WorkoutActiveRun.sessionStretchSectionsToLog(plan: WorkoutSessionPlan = sessionPlan()): List<WorkoutSessionStretchSection> {
    val state = session ?: return emptyList()
    val linked = linkedItemKeys()
    return workoutSnapshot.segments.mapIndexedNotNull { segmentIndex, segment ->
        if (!WorkoutSessionEngine.isSectionFinished(plan, state, segmentIndex)) return@mapIndexedNotNull null
        var held = 0
        var finished: Long? = null
        val entries = segment.items.filterIsInstance<WorkoutItem.Mobility>()
            .filter { (segment.id to it.id) !in linked }
            .mapNotNull { item ->
                val done = plan.steps.indices
                    .filter {
                        val step = plan.steps[it]
                        step is WorkoutSessionStep.StretchHold && step.segmentId == segment.id && step.itemId == item.id
                    }
                    .mapNotNull { state.recordFor(it) }
                    .filter { it.outcome == WorkoutStepOutcome.DONE }
                if (done.isEmpty()) return@mapNotNull null
                held += done.sumOf { it.elapsedSeconds ?: 0 }
                done.mapNotNull { it.atEpochSeconds }.maxOrNull()?.let { at -> finished = maxOf(finished ?: at, at) }
                WorkoutSessionStretchEntry(item.id, item.mobility.catalogId)
            }
        if (entries.isEmpty()) return@mapIndexedNotNull null
        WorkoutSessionStretchSection(
            segmentId = segment.id,
            entries = entries,
            heldSeconds = held,
            finishedAtEpochSeconds = finished,
        )
    }
}

/** Links the section's stretches to the stretch log entry just saved for it. */
fun WorkoutActiveRun.withSessionStretchLogged(
    section: WorkoutSessionStretchSection,
    logDate: String,
    entryId: String,
    nowEpochSeconds: Long,
): WorkoutActiveRun {
    val keys = section.entries.map { section.segmentId to it.itemId }.toSet()
    val recaps = section.entries.map {
        WorkoutItemRecap(
            segmentId = section.segmentId,
            itemId = it.itemId,
            kind = WorkoutLoggedItemKind.MOBILITY,
            linkedLogDate = logDate,
            linkedEntryId = entryId,
            finishedAtEpochSeconds = section.finishedAtEpochSeconds ?: nowEpochSeconds,
        )
    }
    return copy(itemRecaps = itemRecaps.filterNot { (it.segmentId to it.itemId) in keys } + recaps)
}

/** True when the session screen can run and log this cardio activity itself. */
fun isSessionCardioActivity(activity: String): Boolean =
    runCatching { CardioBuiltinActivity.valueOf(activity) }.isSuccess

/**
 * The cardio log entry for [item], built the way the cardio screen's timer builds one, and linked
 * to this run. Null for an activity the cardio silo doesn't know. Heart rate and calories are
 * added by the screen, which holds the monitor and the body weight.
 */
fun WorkoutActiveRun.cardioSessionFor(
    item: WorkoutSessionCardioItem,
    cardioState: CardioLibraryState,
    /** The route recorded on the session screen for this item; gives distance and elevation. */
    gpsPoints: List<CardioGpsPoint> = emptyList(),
): CardioSession? {
    val builtin = runCatching { CardioBuiltinActivity.valueOf(item.activity) }.getOrNull() ?: return null
    val snapshot = cardioState.resolveSnapshot(builtin, null)
    val seconds = item.elapsedSeconds.coerceAtLeast(0)
    val end = item.finishedAtEpochSeconds ?: nowWorkoutEpochSeconds()
    val raw = CardioTimerSessionDraft.fromQuickSnapshot(
        activity = snapshot,
        modality = CardioModality.OUTDOOR,
        treadmill = null,
        title = item.title ?: snapshot.displayLabel,
    ).toSession(
        durationMinutes = ((seconds + 59) / 60).coerceAtLeast(1),
        endEpoch = end,
        elapsedSecondsForDistance = seconds,
        gpsPoints = gpsPoints,
    )
    return raw.copy(
        startEpochSeconds = item.startedAtEpochSeconds ?: (end - seconds),
        routineName = item.title,
        workoutLink = linkFor(item.segmentId, item.itemId),
    )
}
