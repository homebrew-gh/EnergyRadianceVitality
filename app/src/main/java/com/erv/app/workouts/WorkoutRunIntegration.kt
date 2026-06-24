package com.erv.app.workouts

import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioRepository
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
    val run = activeRun ?: return null
    val segmentId = run.lastLaunchedSegmentId ?: return null
    val itemId = run.lastLaunchedItemId ?: return null
    val segment = run.workoutSnapshot.segments.firstOrNull { it.id == segmentId } ?: return null
    val item = segment.items.firstOrNull { it.id == itemId } ?: return null
    if (item !is T) return null
    return ActiveWorkoutItemLaunch(
        workoutId = run.workoutId,
        segmentId = segmentId,
        itemId = itemId,
    )
}

fun WorkoutSegment.displayTitle(): String =
    title?.takeIf { it.isNotBlank() } ?: kind.defaultTitle()

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
