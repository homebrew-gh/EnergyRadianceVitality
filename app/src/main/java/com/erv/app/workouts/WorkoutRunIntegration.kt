package com.erv.app.workouts

import android.content.Context
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSession
import com.erv.app.nostr.SessionMediaBackupRuntime
import com.erv.app.hr.HeartRateChartSectionMarker
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.Kind1PublishResult
import com.erv.app.nostr.Kind1ShareDraft
import com.erv.app.nostr.Kind1SocialShare
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.buildWorkoutShareHashtagContentLineFromTopics
import com.erv.app.nostr.parseWorkoutShareTopics
import com.erv.app.nostr.workoutShareKind1TopicTagsFromTopics
import com.erv.app.stretching.StretchSession
import com.erv.app.stretching.StretchingRepository
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.totalSetCount
import java.time.LocalDate

/**
 * Active storyboard item launched from [WorkoutLiveRunScreen] into a silo (cardio / weight).
 */
data class ActiveWorkoutItemLaunch(
    val workoutId: String,
    val segmentId: String,
    val itemId: String,
)

data class WorkoutItemCompletionResult(
    val segmentJustCompleted: Boolean,
    val completedSegmentId: String?,
    val workoutComplete: Boolean,
    val nextSegmentTitle: String?,
)

fun WorkoutLibraryState.activeWorkoutCardioLaunch(): ActiveWorkoutItemLaunch? =
    activeWorkoutItemLaunchOfType<WorkoutItem.Cardio>()

fun WorkoutLibraryState.activeWorkoutWeightLaunch(): ActiveWorkoutItemLaunch? =
    activeWorkoutItemLaunchOfType<WorkoutItem.Weight>()

fun WorkoutLibraryState.activeWorkoutMobilityLaunch(): ActiveWorkoutItemLaunch? =
    activeWorkoutItemLaunchOfType<WorkoutItem.Mobility>()

private inline fun <reified T : WorkoutItem> WorkoutLibraryState.activeWorkoutItemLaunchOfType(): ActiveWorkoutItemLaunch? {
    val run = activeRun
    if (run == null) {
        return null
    }
    val snapshot = run.workoutSnapshot
    // Primary: the explicit launch pointer recorded when the section opened.
    val explicitSegmentId = run.lastLaunchedSegmentId
    val explicitItemId = run.lastLaunchedItemId
    if (explicitSegmentId != null && explicitItemId != null) {
        val segment = snapshot.segments.firstOrNull { it.id == explicitSegmentId }
        val item = segment?.items?.firstOrNull { it.id == explicitItemId }
        if (item is T) {
            return ActiveWorkoutItemLaunch(
                workoutId = run.workoutId,
                segmentId = explicitSegmentId,
                itemId = explicitItemId,
            )
        }
    }
    // Fallback: derive from the current run position so finishing still links to the
    // composed run even if the launch pointer was not persisted (navigation race).
    val step = WorkoutRunEngine.currentStep(snapshot, run.position)
    if (step == null || step.isComplete) {
        return null
    }
    val item = step.item
    if (item !is T) {
        return null
    }
    val segment = snapshot.segments.getOrNull(run.position.segmentIndex) ?: return null
    return ActiveWorkoutItemLaunch(
        workoutId = run.workoutId,
        segmentId = segment.id,
        itemId = item.id,
    )
}

fun WorkoutSegment.displayTitle(): String =
    title?.takeIf { it.isNotBlank() } ?: kind.defaultTitle()

/** True when the step at [position] launches a silo (weight / cardio / mobility / circuit) screen. */
fun Workout.stepIsSiloBacked(position: WorkoutRunPosition): Boolean {
    if (WorkoutRunEngine.isWorkoutComplete(this, position)) return false
    val step = WorkoutRunEngine.currentStep(this, position) ?: return false
    if (step.isComplete) return false
    return when (step.item) {
        is WorkoutItem.Weight, is WorkoutItem.Cardio, is WorkoutItem.Mobility -> true
        else -> false
    }
}

/** True when any silo-backed step remains at or after [position]. */
fun Workout.hasSiloStepAtOrAfter(position: WorkoutRunPosition): Boolean {
    var current = position
    var guard = 0
    while (!WorkoutRunEngine.isWorkoutComplete(this, current) && guard < 10_000) {
        if (stepIsSiloBacked(current)) return true
        val next = WorkoutRunEngine.advance(this, current)
        if (next == current) break
        current = next
        guard++
    }
    return false
}

/** Number of storyboard items batched into the active silo session (>= 1). */
fun WorkoutActiveRun.launchedBatchSize(): Int = lastLaunchedItemIds.size.coerceAtLeast(1)

/** Run position once the active launched section is completed. */
fun WorkoutActiveRun.positionAfterLaunchedSection(): WorkoutRunPosition =
    WorkoutRunEngine.advanceBy(workoutSnapshot, position, launchedBatchSize())

/** True when finishing the active launched section ends the workout (no further silo steps). */
fun WorkoutActiveRun.isFinalLoggableStep(): Boolean {
    val next = positionAfterLaunchedSection()
    if (WorkoutRunEngine.isWorkoutComplete(workoutSnapshot, next)) return true
    return !workoutSnapshot.hasSiloStepAtOrAfter(next)
}

/** Updated run plus the completion result for one storyboard advance. */
data class WorkoutRunAdvanceOutcome(
    val run: WorkoutActiveRun,
    val result: WorkoutItemCompletionResult,
)

/**
 * Pure bookkeeping shared by "item logged" and "item skipped": merge [newRecaps] (replacing any
 * earlier recap for the same step), move to [nextPosition], and derive the segment-transition /
 * auto-advance flags the storyboard reads. Clears the launch pointers.
 */
fun WorkoutActiveRun.advancedTo(
    nextPosition: WorkoutRunPosition,
    newRecaps: List<WorkoutItemRecap>,
): WorkoutRunAdvanceOutcome {
    val workout = workoutSnapshot
    val replacedKeys = newRecaps.map { it.segmentId to it.itemId }.toSet()
    val mergedRecaps = itemRecaps.filterNot { (it.segmentId to it.itemId) in replacedKeys } + newRecaps
    val beforeSegmentIndex = position.segmentIndex
    val segmentJustCompleted = nextPosition.segmentIndex > beforeSegmentIndex
    val completedSegmentId = if (segmentJustCompleted) workout.segments.getOrNull(beforeSegmentIndex)?.id else null
    val workoutComplete = WorkoutRunEngine.isWorkoutComplete(workout, nextPosition)
    val nextSegmentTitle = if (segmentJustCompleted && !workoutComplete) {
        workout.segments.getOrNull(nextPosition.segmentIndex)?.displayTitle()
    } else {
        null
    }
    val completedSegmentTitle = if (segmentJustCompleted && !workoutComplete) {
        workout.segments.getOrNull(beforeSegmentIndex)?.displayTitle()
    } else {
        null
    }
    val updated = copy(
        position = nextPosition,
        itemRecaps = mergedRecaps,
        completedSegmentIds = if (completedSegmentId != null) (completedSegmentIds + completedSegmentId).distinct() else completedSegmentIds,
        lastLaunchedSegmentId = null,
        lastLaunchedItemId = null,
        lastLaunchedItemIds = emptyList(),
        pendingNextSegmentTitle = nextSegmentTitle,
        pendingCompletedSegmentTitle = completedSegmentTitle,
        autoAdvanceRequested = !workoutComplete && workout.stepIsSiloBacked(nextPosition),
    )
    return WorkoutRunAdvanceOutcome(
        run = updated,
        result = WorkoutItemCompletionResult(
            segmentJustCompleted = segmentJustCompleted,
            completedSegmentId = completedSegmentId,
            workoutComplete = workoutComplete,
            nextSegmentTitle = nextSegmentTitle,
        ),
    )
}

private fun WorkoutItem.loggedKind(): WorkoutLoggedItemKind = when (this) {
    is WorkoutItem.Cardio -> WorkoutLoggedItemKind.CARDIO
    is WorkoutItem.Mobility -> WorkoutLoggedItemKind.MOBILITY
    else -> WorkoutLoggedItemKind.WEIGHT
}

private fun skippedRecap(segmentId: String, item: WorkoutItem, now: Long): WorkoutItemRecap =
    WorkoutItemRecap(
        segmentId = segmentId,
        itemId = item.id,
        kind = item.loggedKind(),
        finishedAtEpochSeconds = now,
        skipped = true,
    )

/**
 * Skip the step at the current position without logging anything. Circuit / superset segments are
 * skipped as a whole (rounds are not partially skippable from the storyboard). Returns null when
 * the run is already complete or the position is invalid.
 */
fun WorkoutActiveRun.skippingCurrentStep(
    nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
): WorkoutRunAdvanceOutcome? {
    val workout = workoutSnapshot
    if (WorkoutRunEngine.isWorkoutComplete(workout, position)) return null
    val segment = workout.segments.getOrNull(position.segmentIndex) ?: return null
    val isCircuit = segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET
    if (isCircuit) {
        val recaps = segment.weightItems().map { skippedRecap(segment.id, it, nowEpochSeconds) }
        val next = WorkoutRunPosition(segmentIndex = position.segmentIndex + 1)
        return advancedTo(next, recaps)
    }
    val item = segment.items.getOrNull(position.itemIndex) ?: return null
    val next = WorkoutRunEngine.advance(workout, position)
    return advancedTo(next, listOf(skippedRecap(segment.id, item, nowEpochSeconds)))
}

/**
 * Skip every item in the launched (batched) section — used when the athlete ends a silo session
 * that belongs to a composed run without logging anything. Falls back to the consecutive weight
 * batch at the current position when no launch pointer was persisted.
 */
fun WorkoutActiveRun.skippingLaunchedSection(
    nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
): WorkoutRunAdvanceOutcome? {
    val workout = workoutSnapshot
    if (WorkoutRunEngine.isWorkoutComplete(workout, position)) return null
    val segment = workout.segments.getOrNull(position.segmentIndex) ?: return null
    val isCircuit = segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET
    if (isCircuit) return skippingCurrentStep(nowEpochSeconds)
    val launchedIds = lastLaunchedItemIds.toSet()
    val batch = if (launchedIds.isNotEmpty()) {
        segment.items.filter { it.id in launchedIds }
    } else {
        WorkoutRunEngine.consecutiveWeightItemRun(workout, position)
            .ifEmpty { listOfNotNull(segment.items.getOrNull(position.itemIndex)) }
    }
    if (batch.isEmpty()) return null
    val next = WorkoutRunEngine.advanceBy(workout, position, batch.size)
    return advancedTo(next, batch.map { skippedRecap(segment.id, it, nowEpochSeconds) })
}

/** Section progress label like "Section 2 of 5". */
fun WorkoutActiveRun.sectionProgressLabel(): String {
    val total = workoutSnapshot.segments.size.coerceAtLeast(1)
    val current = (position.segmentIndex + 1).coerceIn(1, total)
    return "Section $current of $total"
}

/** One section of a finished composed workout, with linked silo log data when available. */
data class ComposedWorkoutHrSection(
    val title: String,
    val kind: WorkoutLoggedItemKind,
    val heartRate: CardioHrScaffolding?,
    val startedAtEpochSeconds: Long? = null,
    val finishedAtEpochSeconds: Long? = null,
    val weightSession: WeightWorkoutSession? = null,
    val cardioSession: CardioSession? = null,
    val stretchSession: StretchSession? = null,
)

/** Heart-rate recap for a finished composed workout: the continuous whole-run trace plus per section. */
data class ComposedWorkoutHrSummary(
    val workoutName: String,
    val wholeRun: CardioHrScaffolding?,
    val sections: List<ComposedWorkoutHrSection>,
    val totalElapsedSeconds: Int? = null,
    val sectionCount: Int = 0,
    /** Human-readable labels ("Main work · Bench press") for steps the athlete skipped. */
    val skippedItems: List<String> = emptyList(),
) {
    val hasAnyHeartRate: Boolean
        get() = wholeRun != null || sections.any { it.heartRate != null }

    fun sectionChartMarkers(): List<HeartRateChartSectionMarker> =
        sections.mapNotNull { section ->
            section.startedAtEpochSeconds?.let { started ->
                HeartRateChartSectionMarker(epochSeconds = started, label = section.title)
            }
        }
}

/**
 * Build the finish-screen HR recap for a composed run: the continuous [wholeRun] trace plus each
 * linked section's own per-section snapshot (read back from the silo logs).
 */
suspend fun buildComposedWorkoutHrSummary(
    run: WorkoutActiveRun,
    wholeRun: CardioHrScaffolding?,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
): ComposedWorkoutHrSummary {
    val weightState = weightRepository.currentState()
    val cardioState = cardioRepository.currentState()
    val stretchState = stretchingRepository.currentState()
    var sectionStart = run.startedAtEpochSeconds
    val sections = run.itemRecaps
        .filterNot { it.skipped }
        .distinctBy { it.linkedEntryId ?: (it.segmentId + it.itemId) }
        .map { recap ->
            val title = run.workoutSnapshot.segments
                .firstOrNull { it.id == recap.segmentId }
                ?.displayTitle()
                ?: "Section"
            val logDate = recap.linkedLogDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val entryId = recap.linkedEntryId
            val heartRate = if (logDate != null && entryId != null) {
                when (recap.kind) {
                    WorkoutLoggedItemKind.CARDIO ->
                        cardioState.logFor(logDate)
                            ?.sessions?.firstOrNull { it.id == entryId }?.heartRate
                    WorkoutLoggedItemKind.WEIGHT ->
                        weightState.logFor(logDate)
                            ?.workouts?.firstOrNull { it.id == entryId }?.heartRate
                    WorkoutLoggedItemKind.MOBILITY -> null
                }
            } else {
                null
            }
            val weightSession = if (recap.kind == WorkoutLoggedItemKind.WEIGHT && logDate != null && entryId != null) {
                weightState.logFor(logDate)?.workouts?.firstOrNull { it.id == entryId }
            } else {
                null
            }
            val cardioSession = if (recap.kind == WorkoutLoggedItemKind.CARDIO && logDate != null && entryId != null) {
                cardioState.logFor(logDate)?.sessions?.firstOrNull { it.id == entryId }
            } else {
                null
            }
            val stretchSession = if (recap.kind == WorkoutLoggedItemKind.MOBILITY && logDate != null && entryId != null) {
                stretchState.logFor(logDate)?.sessions?.firstOrNull { it.id == entryId }
            } else {
                null
            }
            val section = ComposedWorkoutHrSection(
                title = title,
                kind = recap.kind,
                heartRate = heartRate,
                startedAtEpochSeconds = sectionStart,
                finishedAtEpochSeconds = recap.finishedAtEpochSeconds,
                weightSession = weightSession,
                cardioSession = cardioSession,
                stretchSession = stretchSession,
            )
            sectionStart = recap.finishedAtEpochSeconds ?: sectionStart
            section
        }
    val totalElapsedSeconds = run.startedAtEpochSeconds?.let { start ->
        val end = run.itemRecaps.mapNotNull { it.finishedAtEpochSeconds }.maxOrNull()
            ?: nowWorkoutEpochSeconds()
        (end - start).coerceAtLeast(0).toInt()
    }
    return ComposedWorkoutHrSummary(
        workoutName = run.workoutSnapshot.name,
        wholeRun = wholeRun,
        sections = sections,
        totalElapsedSeconds = totalElapsedSeconds,
        sectionCount = run.workoutSnapshot.segments.size,
        skippedItems = run.skippedItemLabels { exerciseId -> weightState.exerciseById(exerciseId)?.name },
    )
}

/** "Section · step" labels for every recap marked skipped, in storyboard order. */
fun WorkoutActiveRun.skippedItemLabels(exerciseNameFor: (String) -> String?): List<String> {
    val skippedKeys = itemRecaps.filter { it.skipped }.map { it.segmentId to it.itemId }.toSet()
    if (skippedKeys.isEmpty()) return emptyList()
    return workoutSnapshot.segments.flatMap { segment ->
        segment.items
            .filter { (segment.id to it.id) in skippedKeys }
            .map { item ->
                val stepLabel = when (item) {
                    is WorkoutItem.Weight -> item.displayExerciseName(exerciseNameFor(item.exerciseId))
                    is WorkoutItem.Cardio -> item.title?.takeIf { it.isNotBlank() } ?: item.displaySummary()
                    is WorkoutItem.Mobility -> item.title?.takeIf { it.isNotBlank() } ?: item.displaySummary()
                    is WorkoutItem.Rest -> "Rest ${item.durationSeconds}s"
                    is WorkoutItem.Note -> "Note"
                }
                "${segment.displayTitle()} · $stepLabel"
            }
    }
}

/** Human-readable kind label for a logged section, used in summary + share text. */
fun WorkoutLoggedItemKind.summaryLabel(): String = when (this) {
    WorkoutLoggedItemKind.WEIGHT -> "Weights"
    WorkoutLoggedItemKind.CARDIO -> "Cardio"
    WorkoutLoggedItemKind.MOBILITY -> "Mobility"
}

/** Build the Nostr kind-1 share text for a finished composed workout. */
fun buildComposedWorkoutNoteContent(
    summary: ComposedWorkoutHrSummary,
    personalMessage: String = "",
    hashtagLine: String = "",
): String = buildString {
    append("\uD83C\uDFCB\uFE0F ${summary.workoutName.ifBlank { "Workout" }}\n")
    personalMessage.trim().takeIf { it.isNotEmpty() }?.let { message ->
        append(message)
        append("\n\n")
    }
    summary.totalElapsedSeconds?.takeIf { it > 0 }?.let { seconds ->
        append("Duration: %d:%02d\n".format(seconds / 60, seconds % 60))
    }
    if (summary.sectionCount > 0) {
        append("Sections: ${summary.sectionCount}\n")
    }
    summary.wholeRun?.let { hr ->
        append("Heart rate: avg ${hr.avgBpm} bpm · max ${hr.maxBpm} bpm\n")
    }
    if (summary.sections.isNotEmpty()) {
        append("\n")
        summary.sections.forEach { section ->
            append("• ${section.title} (${section.kind.summaryLabel()})")
            section.heartRate?.let { hr -> append(" — avg ${hr.avgBpm} bpm") }
            section.weightSession?.let { session ->
                append(" — ${session.entries.size} exercise(s), ${session.totalSetCount()} sets")
            }
            section.cardioSession?.let { cardio ->
                append(" — ${cardio.activity.displayLabel}, ${cardio.durationMinutes} min")
            }
            section.stretchSession?.let { stretch ->
                append(" — ${stretch.totalMinutes} min mobility")
            }
            append("\n")
        }
    }
    hashtagLine.trim().takeIf { it.isNotEmpty() }?.let { line ->
        append("\n")
        append(line)
    }
}

/** Build the kind-1 draft for preview before publishing a composed workout share. */
fun buildComposedWorkoutKind1Draft(
    summary: ComposedWorkoutHrSummary,
    personalMessage: String = "",
    hashtagsInput: String = "",
): Kind1ShareDraft {
    val topics = parseWorkoutShareTopics(hashtagsInput)
    return Kind1ShareDraft(
        content = buildComposedWorkoutNoteContent(
            summary = summary,
            personalMessage = personalMessage,
            hashtagLine = buildWorkoutShareHashtagContentLineFromTopics(topics),
        ),
        tags = workoutShareKind1TopicTagsFromTopics(topics),
    )
}

/** Publish a finished composed workout as a Nostr kind-1 note to social relays only. */
suspend fun publishComposedWorkoutNote(
    relayPool: RelayPool,
    keyManager: KeyManager,
    signer: EventSigner,
    summary: ComposedWorkoutHrSummary,
    personalMessage: String = "",
    hashtagsInput: String = "",
    successMessage: String = "Shared to your social relays.",
    noSocialRelaysMessage: String =
        "No social relays configured. Open Settings → Relays and enable Social on at least one relay.",
    failureMessage: String = "Failed to share — check social relay connections.",
): Kind1PublishResult {
    val draft = buildComposedWorkoutKind1Draft(
        summary = summary,
        personalMessage = personalMessage,
        hashtagsInput = hashtagsInput,
    )
    return Kind1SocialShare.publish(
        relayPool = relayPool,
        keyManager = keyManager,
        signer = signer,
        draft = draft,
        successMessage = successMessage,
        noSocialRelaysMessage = noSocialRelaysMessage,
        failureMessage = failureMessage,
    )
}

/** Stamp the full-workout HR summary onto every silo log entry linked to this run. */
suspend fun attachComposedWorkoutHeartRateToLinkedLogs(
    run: WorkoutActiveRun,
    heartRate: CardioHrScaffolding,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
) {
    for (recap in run.itemRecaps) {
        val logDate = recap.linkedLogDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
        val entryId = recap.linkedEntryId ?: continue
        when (recap.kind) {
            WorkoutLoggedItemKind.CARDIO -> {
                cardioRepository.updateSession(logDate, entryId) { session ->
                    val link = session.workoutLink ?: return@updateSession session
                    session.copy(workoutLink = link.copy(sessionHeartRate = heartRate))
                }
            }
            WorkoutLoggedItemKind.WEIGHT -> {
                val session = weightRepository.currentState().logFor(logDate)
                    ?.workouts?.firstOrNull { it.id == entryId } ?: continue
                val link = session.workoutLink ?: continue
                weightRepository.updateWorkout(
                    logDate,
                    session.copy(workoutLink = link.copy(sessionHeartRate = heartRate)),
                )
            }
            WorkoutLoggedItemKind.MOBILITY -> {
                stretchingRepository.updateSession(logDate, entryId) { session ->
                    val link = session.workoutLink ?: return@updateSession session
                    session.copy(workoutLink = link.copy(sessionHeartRate = heartRate))
                }
            }
        }
    }
}

/** Re-backup linked cardio and weight logs after the continuous heart-rate trace is attached. */
suspend fun backupComposedWorkoutSessionMedia(
    appContext: Context,
    run: WorkoutActiveRun,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
) {
    val cardioState = cardioRepository.currentState()
    val weightState = weightRepository.currentState()
    for (recap in run.itemRecaps) {
        if (recap.skipped) continue
        val dateIso = recap.linkedLogDate ?: continue
        val entryId = recap.linkedEntryId ?: continue
        val logDate = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: continue
        when (recap.kind) {
            WorkoutLoggedItemKind.CARDIO -> {
                val session = cardioState.logFor(logDate)?.sessions?.firstOrNull { it.id == entryId } ?: continue
                SessionMediaBackupRuntime.scheduleCardioBackup(appContext, session, dateIso)
            }
            WorkoutLoggedItemKind.WEIGHT -> {
                val session = weightState.logFor(logDate)?.workouts?.firstOrNull { it.id == entryId } ?: continue
                SessionMediaBackupRuntime.scheduleWeightBackup(appContext, session, dateIso)
            }
            WorkoutLoggedItemKind.MOBILITY -> Unit
        }
    }
}
