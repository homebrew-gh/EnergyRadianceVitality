package com.erv.app.workouts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.workoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_workouts")

class WorkoutRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("workout_library_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    val state: Flow<WorkoutLibraryState> = appContext.workoutDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): WorkoutLibraryState =
        decodeState(appContext.workoutDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(newState: WorkoutLibraryState) {
        updateState { newState.sanitized() }
    }

    suspend fun clearAllData() {
        replaceAll(WorkoutLibraryState())
    }

    suspend fun mergeImported(envelope: WorkoutImportEnvelope) {
        updateState { current ->
            mergeWorkoutImport(current, envelope).copy(
                libraryUpdatedAtEpochSeconds = nowWorkoutEpochSeconds(),
            ).sanitized()
        }
    }

    suspend fun upsertWorkout(workout: Workout) {
        val now = nowWorkoutEpochSeconds()
        val stamped = workout.copy(lastModifiedEpochSeconds = now)
        updateState { state ->
            state.copy(
                workouts = state.workouts.upsertById(stamped) { it.id },
                libraryUpdatedAtEpochSeconds = now,
            ).sanitized()
        }
    }

    suspend fun deleteWorkout(workoutId: String) {
        val now = nowWorkoutEpochSeconds()
        updateState { state ->
            state.copy(
                workouts = state.workouts.filterNot { it.id == workoutId },
                activeRun = state.activeRun?.takeUnless { it.workoutId == workoutId },
                libraryUpdatedAtEpochSeconds = now,
            ).sanitized()
        }
    }

    suspend fun startRun(workoutId: String) {
        updateState { state ->
            val existing = state.activeRun?.takeIf { it.workoutId == workoutId }
            if (existing != null) return@updateState state
            val workout = state.workoutById(workoutId) ?: return@updateState state
            state.copy(
                activeRun = WorkoutActiveRun(
                    workoutId = workout.id,
                    workoutSnapshot = workout,
                ),
            )
        }
    }

    suspend fun beginRun(workoutId: String) {
        updateState { state ->
            val run = state.activeRun?.takeIf { it.workoutId == workoutId }
            if (run != null) {
                return@updateState state.copy(
                    activeRun = if (run.startedAtEpochSeconds == null) {
                        run.copy(startedAtEpochSeconds = nowWorkoutEpochSeconds())
                    } else {
                        run
                    },
                )
            }
            val workout = state.workoutById(workoutId) ?: return@updateState state
            state.copy(
                activeRun = WorkoutActiveRun(
                    workoutId = workout.id,
                    workoutSnapshot = workout,
                    startedAtEpochSeconds = nowWorkoutEpochSeconds(),
                ),
            )
        }
    }

    suspend fun updateRunPosition(position: WorkoutRunPosition) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.copy(position = position))
        }
    }

    suspend fun markSegmentCompleted(segmentId: String) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            val completed = (run.completedSegmentIds + segmentId).distinct()
            state.copy(activeRun = run.copy(completedSegmentIds = completed))
        }
    }

    suspend fun setLastLaunchedItem(segmentId: String, itemId: String) {
        setLastLaunchedItems(segmentId, listOf(itemId))
    }

    suspend fun setLastLaunchedItems(segmentId: String, itemIds: List<String>) {
        val ids = itemIds.distinct()
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(
                activeRun = run.copy(
                    lastLaunchedSegmentId = segmentId,
                    lastLaunchedItemId = ids.firstOrNull(),
                    lastLaunchedItemIds = ids,
                ),
            )
        }
    }

    suspend fun clearPendingNextSegmentPrompt() {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (run.pendingNextSegmentTitle == null) return@updateState state
            state.copy(activeRun = run.copy(pendingNextSegmentTitle = null))
        }
    }

    suspend fun clearAutoAdvance() {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (!run.autoAdvanceRequested) return@updateState state
            state.copy(activeRun = run.copy(autoAdvanceRequested = false))
        }
    }

    /**
     * Records a silo log entry for the launched storyboard item and advances the run position.
     * Returns null when there is no active launched item.
     */
    suspend fun completeLaunchedItem(
        logDate: String,
        entryId: String,
        kind: WorkoutLoggedItemKind,
    ): WorkoutItemCompletionResult? {
        var result: WorkoutItemCompletionResult? = null
        updateState { state ->
            val run = state.activeRun
            if (run == null) {
                return@updateState state
            }
            val workout = run.workoutSnapshot
            val position = run.position
            // Prefer the explicit launch pointer, but fall back to the current run
            // position so a finished section still advances even if the launch write
            // never persisted (e.g. navigation cancelled the storyboard coroutine).
            val fallbackStep = WorkoutRunEngine.currentStep(workout, position)
            val segmentId = run.lastLaunchedSegmentId
                ?: workout.segments.getOrNull(position.segmentIndex)?.id
                ?: run {
                    return@updateState state
                }
            val itemId = run.lastLaunchedItemId
                ?: fallbackStep?.item?.id
                ?: run {
                    return@updateState state
                }
            val now = nowWorkoutEpochSeconds()
            val recap = WorkoutItemRecap(
                segmentId = segmentId,
                itemId = itemId,
                kind = kind,
                linkedLogDate = logDate,
                linkedEntryId = entryId,
                finishedAtEpochSeconds = now,
            )
            val itemRecaps = run.itemRecaps
                .filterNot { it.segmentId == segmentId && it.itemId == itemId } + recap
            val beforeSegmentIndex = position.segmentIndex
            val batchSize = run.lastLaunchedItemIds.size.takeIf { it > 0 }
                ?: WorkoutRunEngine.consecutiveWeightItemRun(workout, position).size.coerceAtLeast(1)
            val nextPosition = WorkoutRunEngine.advanceBy(workout, position, batchSize)
            val segmentJustCompleted = nextPosition.segmentIndex > beforeSegmentIndex
            val completedSegmentId = if (segmentJustCompleted) {
                workout.segments.getOrNull(beforeSegmentIndex)?.id
            } else {
                null
            }
            val completedSegmentIds = if (completedSegmentId != null) {
                (run.completedSegmentIds + completedSegmentId).distinct()
            } else {
                run.completedSegmentIds
            }
            val workoutComplete = WorkoutRunEngine.isWorkoutComplete(workout, nextPosition)
            val nextSegmentTitle = if (segmentJustCompleted && !workoutComplete) {
                workout.segments.getOrNull(nextPosition.segmentIndex)?.displayTitle()
            } else {
                null
            }
            result = WorkoutItemCompletionResult(
                segmentJustCompleted = segmentJustCompleted,
                completedSegmentId = completedSegmentId,
                workoutComplete = workoutComplete,
                nextSegmentTitle = nextSegmentTitle,
            )
            val autoAdvance = !workoutComplete && workout.stepIsSiloBacked(nextPosition)
            val updatedRun = run.copy(
                position = nextPosition,
                itemRecaps = itemRecaps,
                completedSegmentIds = completedSegmentIds,
                lastLaunchedSegmentId = null,
                lastLaunchedItemId = null,
                lastLaunchedItemIds = emptyList(),
                pendingNextSegmentTitle = nextSegmentTitle,
                autoAdvanceRequested = autoAdvance,
            )
            state.copy(activeRun = updatedRun)
        }
        return result
    }

    suspend fun clearActiveRun() {
        updateState { state -> state.copy(activeRun = null) }
    }

    private suspend fun updateState(transform: (WorkoutLibraryState) -> WorkoutLibraryState) {
        appContext.workoutDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            prefs[Keys.STATE] = json.encodeToString(
                WorkoutLibraryState.serializer(),
                transform(current).sanitized(),
            )
        }
    }

    private fun decodeState(raw: String?): WorkoutLibraryState {
        if (raw.isNullOrBlank()) return WorkoutLibraryState()
        return try {
            json.decodeFromString(WorkoutLibraryState.serializer(), raw).sanitized()
        } catch (_: SerializationException) {
            WorkoutLibraryState()
        }
    }
}

private fun List<Workout>.upsertById(entry: Workout, idSelector: (Workout) -> String): List<Workout> {
    val items = toMutableList()
    val id = idSelector(entry)
    val index = items.indexOfFirst { idSelector(it) == id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}
