package com.erv.app.workouts

import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioRepository
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.UnsignedEvent
import com.erv.app.nostr.buildWorkoutShareHashtagContentLineFromTopics
import com.erv.app.nostr.parseWorkoutShareTopics
import com.erv.app.nostr.workoutShareKind1TopicTagsFromTopics
import com.erv.app.stretching.StretchingRepository
import com.erv.app.weighttraining.WeightRepository
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

/** Section progress label like "Section 2 of 5". */
fun WorkoutActiveRun.sectionProgressLabel(): String {
    val total = workoutSnapshot.segments.size.coerceAtLeast(1)
    val current = (position.segmentIndex + 1).coerceIn(1, total)
    return "Section $current of $total"
}

/** One section of a finished composed workout, with its own per-section HR snapshot if any. */
data class ComposedWorkoutHrSection(
    val title: String,
    val kind: WorkoutLoggedItemKind,
    val heartRate: CardioHrScaffolding?,
)

/** Heart-rate recap for a finished composed workout: the continuous whole-run trace plus per section. */
data class ComposedWorkoutHrSummary(
    val workoutName: String,
    val wholeRun: CardioHrScaffolding?,
    val sections: List<ComposedWorkoutHrSection>,
    val totalElapsedSeconds: Int? = null,
    val sectionCount: Int = 0,
) {
    val hasAnyHeartRate: Boolean
        get() = wholeRun != null || sections.any { it.heartRate != null }
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
    val sections = run.itemRecaps
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
                        cardioRepository.currentState().logFor(logDate)
                            ?.sessions?.firstOrNull { it.id == entryId }?.heartRate
                    WorkoutLoggedItemKind.WEIGHT ->
                        weightRepository.currentState().logFor(logDate)
                            ?.workouts?.firstOrNull { it.id == entryId }?.heartRate
                    WorkoutLoggedItemKind.MOBILITY -> null
                }
            } else {
                null
            }
            ComposedWorkoutHrSection(title = title, kind = recap.kind, heartRate = heartRate)
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
    )
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
            append("\n")
        }
    }
    hashtagLine.trim().takeIf { it.isNotEmpty() }?.let { line ->
        append("\n")
        append(line)
    }
}

/** Publish a finished composed workout as a Nostr kind-1 note to the athlete's relays. */
suspend fun publishComposedWorkoutNote(
    relayPool: RelayPool,
    signer: EventSigner,
    summary: ComposedWorkoutHrSummary,
    personalMessage: String = "",
    hashtagsInput: String = "",
): Boolean {
    val topics = parseWorkoutShareTopics(hashtagsInput)
    val content = buildComposedWorkoutNoteContent(
        summary = summary,
        personalMessage = personalMessage,
        hashtagLine = buildWorkoutShareHashtagContentLineFromTopics(topics),
    )
    val unsigned = UnsignedEvent(
        pubkey = signer.publicKey,
        createdAt = System.currentTimeMillis() / 1000,
        kind = 1,
        tags = workoutShareKind1TopicTagsFromTopics(topics),
        content = content,
    )
    val signed = signer.sign(unsigned)
    return relayPool.publish(signed)
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
