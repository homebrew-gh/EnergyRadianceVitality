package com.erv.app.weighttraining

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
import java.time.LocalDate

private val Context.weightTrainingDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_weight_training")

class WeightRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("weight_training_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<WeightLibraryState> = appContext.weightTrainingDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): WeightLibraryState =
        decodeState(appContext.weightTrainingDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(state: WeightLibraryState) {
        updateState { state }
    }

    suspend fun upsertExercise(exercise: WeightExercise) {
        updateState { it.copy(exercises = it.exercises.upsertById(exercise) { e -> e.id }) }
    }

    suspend fun deleteExercise(exerciseId: String) {
        updateState { it.copy(exercises = it.exercises.filterNot { e -> e.id == exerciseId }) }
    }

    suspend fun upsertRoutine(routine: WeightRoutine) {
        updateState { it.copy(routines = it.routines.upsertById(routine) { r -> r.id }) }
    }

    suspend fun deleteRoutine(routineId: String) {
        updateState { it.copy(routines = it.routines.filterNot { r -> r.id == routineId }) }
    }

    suspend fun addWorkout(date: LocalDate, session: WeightWorkoutSession) {
        updateState { current ->
            val log = current.logFor(date) ?: WeightDayLog(date = date.toString())
            current.copy(
                logs = current.logs.upsertLog(
                    log.copy(workouts = log.workouts + session)
                )
            )
        }
    }

    suspend fun updateWorkout(date: LocalDate, session: WeightWorkoutSession) {
        updateState { current ->
            val log = current.logFor(date) ?: return@updateState current
            val idx = log.workouts.indexOfFirst { it.id == session.id }
            if (idx < 0) return@updateState current
            val updated = log.workouts.toMutableList().also { it[idx] = session }
            current.copy(logs = current.logs.upsertLog(log.copy(workouts = updated)))
        }
    }

    suspend fun deleteWorkout(date: LocalDate, workoutId: String) {
        updateState { current ->
            val log = current.logFor(date) ?: return@updateState current
            val newWorkouts = log.workouts.filterNot { it.id == workoutId }
            if (newWorkouts.size == log.workouts.size) return@updateState current
            current.copy(logs = current.logs.upsertLog(log.copy(workouts = newWorkouts)))
        }
    }

    private suspend fun updateState(transform: (WeightLibraryState) -> WeightLibraryState) {
        appContext.weightTrainingDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current).withRebuiltExerciseSessionSummaries()
            prefs[Keys.STATE] = json.encodeToString(WeightLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): WeightLibraryState {
        val base = if (raw.isNullOrBlank()) {
            WeightLibraryState()
        } else {
            try {
                json.decodeFromString(WeightLibraryState.serializer(), raw)
            } catch (_: SerializationException) {
                WeightLibraryState()
            } catch (_: IllegalArgumentException) {
                WeightLibraryState()
            }
        }
        val merged = when {
            base.exercises.isEmpty() -> base.copy(exercises = defaultCatalogExercises())
            else -> base.mergeMissingCatalogExercises()
        }
        return merged
            .copy(exercises = merged.exercises.map { it.withMigratedArmsMuscleGroup() })
            .withRebuiltExerciseSessionSummaries()
    }
}

private fun WeightLibraryState.mergeMissingCatalogExercises(): WeightLibraryState {
    val existingIds = exercises.map { it.id }.toSet()
    val toAdd = defaultCatalogExercises().filter { it.id !in existingIds }
    return if (toAdd.isEmpty()) this else copy(exercises = exercises + toAdd)
}

private fun <T : Any> List<T>.upsertById(entry: T, idSelector: (T) -> String): List<T> {
    val items = toMutableList()
    val id = idSelector(entry)
    val index = items.indexOfFirst { idSelector(it) == id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}

private fun List<WeightDayLog>.upsertLog(log: WeightDayLog): List<WeightDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}
