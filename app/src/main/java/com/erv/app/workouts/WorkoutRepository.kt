package com.erv.app.workouts

import com.erv.app.data.preserveUnreadableState
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
                deletedWorkouts = state.deletedWorkouts - stamped.id,
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
                deletedWorkouts = state.deletedWorkouts + (workoutId to now),
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

    /** Starts the single-screen session on the active run (see [withSessionStarted]). */
    suspend fun startSession(workoutId: String) {
        updateState { state ->
            val run = state.activeRun?.takeIf { it.workoutId == workoutId } ?: return@updateState state
            state.copy(activeRun = run.withSessionStartedAtPosition(nowWorkoutEpochSeconds()))
        }
    }

    /**
     * Applies [event] to the active run's session; does nothing when no session is running. With
     * [atStepIndex], only while that step is still on screen, so a late or doubled tap (a rest
     * ending as Skip Rest is pressed) can't act on the step after it.
     */
    suspend fun applySessionEvent(event: WorkoutSessionEvent, atStepIndex: Int? = null) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (atStepIndex != null && run.session?.stepIndex != atStepIndex) return@updateState state
            state.copy(activeRun = run.applyingSessionEvent(event, nowWorkoutEpochSeconds()))
        }
    }

    /** Links a lifting section to the weight log entry saved for it (see [sessionLiftSectionsToLog]). */
    suspend fun recordSessionLiftLog(section: WorkoutSessionLiftSection, logDate: String, entryId: String) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.withSessionLiftLogged(section, logDate, entryId, nowWorkoutEpochSeconds()))
        }
    }

    /** Does [exerciseId] in place of the planned exercise for this item, for today only. */
    suspend fun swapSessionExercise(segmentId: String, itemId: String, exerciseId: String) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            val key = sessionItemKey(segmentId, itemId)
            state.copy(activeRun = run.copy(sessionExerciseSwaps = run.sessionExerciseSwaps + (key to exerciseId)))
        }
    }

    /**
     * Adds a set to the exercise on screen ([delta] 1) or takes one away ([delta] -1), for today
     * only (see [withSessionSetCountChanged]). Ignored once [atStepIndex] has left the screen.
     */
    suspend fun changeSessionSetCount(delta: Int, atStepIndex: Int? = null) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (atStepIndex != null && run.session?.stepIndex != atStepIndex) return@updateState state
            state.copy(activeRun = run.withSessionSetCountChanged(delta))
        }
    }

    /** Adds a lift, cardio or stretch after the item on screen, for today only (see [withSessionItemAdded]). */
    suspend fun addSessionItem(addition: WorkoutSessionAddition, atStepIndex: Int? = null) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (atStepIndex != null && run.session?.stepIndex != atStepIndex) return@updateState state
            state.copy(activeRun = run.withSessionItemAdded(addition))
        }
    }

    /** Takes an item that hasn't started out of today's run (see [withSessionItemRemoved]). */
    suspend fun removeSessionItem(segmentId: String, itemId: String) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.withSessionItemRemoved(segmentId, itemId))
        }
    }

    /** Moves an item that hasn't started up (-1) or down (1) in its section, for today. */
    suspend fun moveSessionItem(segmentId: String, itemId: String, delta: Int) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.withSessionItemMoved(segmentId, itemId, delta))
        }
    }

    /** Moves a section that hasn't started up (-1) or down (1), for today. */
    suspend fun moveSessionSection(segmentId: String, delta: Int) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.withSessionSectionMoved(segmentId, delta))
        }
    }

    /** Starts the clock of the cardio or stretch step waiting on screen for Start. */
    suspend fun startSessionStep(atStepIndex: Int? = null) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (!run.sessionStepAwaitingStart) return@updateState state
            if (atStepIndex != null && run.session?.stepIndex != atStepIndex) return@updateState state
            state.copy(
                activeRun = run.copy(
                    sessionStepAwaitingStart = false,
                    sessionStepStartedAtEpochSeconds = nowWorkoutEpochSeconds(),
                ),
            )
        }
    }

    /** Links a cardio item the session finished to the cardio log entry saved for it. */
    suspend fun recordSessionCardioLog(item: WorkoutSessionCardioItem, logDate: String, entryId: String) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.withSessionCardioLogged(item, logDate, entryId, nowWorkoutEpochSeconds()))
        }
    }

    /** Links a stretch section the session finished to the stretch log entry saved for it. */
    suspend fun recordSessionStretchLog(section: WorkoutSessionStretchSection, logDate: String, entryId: String) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            state.copy(activeRun = run.withSessionStretchLogged(section, logDate, entryId, nowWorkoutEpochSeconds()))
        }
    }

    /** Adds [seconds] to the rest or timed step on screen. */
    suspend fun extendSessionCountdown(seconds: Int) {
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            val started = run.sessionStepStartedAtEpochSeconds ?: return@updateState state
            state.copy(activeRun = run.copy(sessionStepStartedAtEpochSeconds = started + seconds))
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
            if (run.pendingNextSegmentTitle == null && run.pendingCompletedSegmentTitle == null) {
                return@updateState state
            }
            state.copy(
                activeRun = run.copy(
                    pendingNextSegmentTitle = null,
                    pendingCompletedSegmentTitle = null,
                ),
            )
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
        /** Weight exercises the athlete skipped inside the batched silo session; their storyboard items are recapped as skipped. */
        skippedExerciseIds: Collection<String> = emptyList(),
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
            if (run.session != null) {
                // The single-screen session: mark the item's steps done and follow the session.
                val updated = run.withSessionItemLoggedElsewhere(segmentId, itemId, kind, logDate, entryId, now)
                val plan = updated.sessionPlan()
                val segmentDone = WorkoutSessionEngine.isSectionFinished(plan, updated.session!!, run.position.segmentIndex)
                result = WorkoutItemCompletionResult(
                    segmentJustCompleted = segmentDone,
                    completedSegmentId = if (segmentDone) workout.segments.getOrNull(run.position.segmentIndex)?.id else null,
                    workoutComplete = updated.isSessionComplete(plan),
                    nextSegmentTitle = null,
                )
                return@updateState state.copy(activeRun = updated)
            }
            val recap = WorkoutItemRecap(
                segmentId = segmentId,
                itemId = itemId,
                kind = kind,
                linkedLogDate = logDate,
                linkedEntryId = entryId,
                finishedAtEpochSeconds = now,
            )
            val segment = workout.segments.firstOrNull { it.id == segmentId }
            val skippedRecaps = if (skippedExerciseIds.isEmpty() || segment == null) {
                emptyList()
            } else {
                val batchIds = run.lastLaunchedItemIds.toSet()
                segment.items
                    .filterIsInstance<WorkoutItem.Weight>()
                    .filter { it.id != itemId && (batchIds.isEmpty() || it.id in batchIds) && it.exerciseId in skippedExerciseIds }
                    .map {
                        WorkoutItemRecap(
                            segmentId = segmentId,
                            itemId = it.id,
                            kind = WorkoutLoggedItemKind.WEIGHT,
                            finishedAtEpochSeconds = now,
                            skipped = true,
                        )
                    }
            }
            val batchSize = run.lastLaunchedItemIds.size.takeIf { it > 0 }
                ?: WorkoutRunEngine.consecutiveWeightItemRun(workout, position).size.coerceAtLeast(1)
            val nextPosition = WorkoutRunEngine.advanceBy(workout, position, batchSize)
            val outcome = run.advancedTo(nextPosition, listOf(recap) + skippedRecaps)
            result = outcome.result
            state.copy(activeRun = outcome.run)
        }
        return result
    }

    /**
     * Skip the current storyboard step (whole circuit for circuit/superset segments) without
     * logging anything. Returns null when there is no active run or it is already complete.
     */
    suspend fun skipCurrentStep(): WorkoutItemCompletionResult? {
        var result: WorkoutItemCompletionResult? = null
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            val outcome = run.skippingCurrentStep() ?: return@updateState state
            result = outcome.result
            state.copy(activeRun = outcome.run)
        }
        return result
    }

    /**
     * Skip the launched (batched) section — the silo session ended with nothing logged.
     * Returns null when there is no active run or it is already complete.
     */
    suspend fun skipLaunchedSection(): WorkoutItemCompletionResult? {
        var result: WorkoutItemCompletionResult? = null
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            val outcome = run.skippingLaunchedSection() ?: return@updateState state
            result = outcome.result
            state.copy(activeRun = outcome.run)
        }
        return result
    }

    /**
     * End the active run now, recapping every remaining step as skipped, so the finish summary
     * shows what was logged. Returns null when there is no active run or it is already complete.
     */
    suspend fun endRunEarly(): WorkoutItemCompletionResult? {
        var result: WorkoutItemCompletionResult? = null
        updateState { state ->
            val run = state.activeRun ?: return@updateState state
            if (run.session != null) {
                val plan = run.sessionPlan()
                if (run.isSessionComplete(plan)) return@updateState state
                val ended = run.applyingSessionEvent(WorkoutSessionEvent.EndWorkout, nowWorkoutEpochSeconds())
                result = WorkoutItemCompletionResult(
                    segmentJustCompleted = true,
                    completedSegmentId = null,
                    workoutComplete = true,
                    nextSegmentTitle = null,
                )
                return@updateState state.copy(activeRun = ended)
            }
            val outcome = run.endingEarly() ?: return@updateState state
            result = outcome.result
            state.copy(activeRun = outcome.run)
        }
        return result
    }

    suspend fun clearActiveRun() {
        updateState { state -> state.copy(activeRun = null) }
        // Whichever screen ended the run, its notification and rest alarm go with it.
        if (WorkoutSessionForegroundService.isRunning) WorkoutSessionNotifier.stop(appContext)
    }

    private suspend fun updateState(transform: (WorkoutLibraryState) -> WorkoutLibraryState) {
        appContext.workoutDataStore.edit { prefs ->
            val raw = prefs[Keys.STATE]
            val current = decodeState(raw) { prefs.preserveUnreadableState(Keys.STATE, raw) }
            prefs[Keys.STATE] = json.encodeToString(
                WorkoutLibraryState.serializer(),
                transform(current).sanitized(),
            )
        }
    }

    private fun decodeState(raw: String?, onUnreadable: () -> Unit = {}): WorkoutLibraryState {
        if (raw.isNullOrBlank()) return WorkoutLibraryState()
        return try {
            json.decodeFromString(WorkoutLibraryState.serializer(), raw).sanitized()
        } catch (_: SerializationException) {
            onUnreadable()
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
