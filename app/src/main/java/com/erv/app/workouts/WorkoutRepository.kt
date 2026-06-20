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
            val workout = state.workoutById(workoutId) ?: return@updateState state
            state.copy(
                activeRun = WorkoutActiveRun(
                    workoutId = workout.id,
                    workoutSnapshot = workout,
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
