package com.erv.app.reminders

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

private val Context.routineReminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_routine_reminders")

class RoutineReminderRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("routine_reminder_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<RoutineReminderState> = appContext.routineReminderDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): RoutineReminderState =
        decodeState(appContext.routineReminderDataStore.data.first()[Keys.STATE])

    suspend fun reminderForRoutine(routineId: String): RoutineReminder? =
        currentState().reminderForRoutine(routineId)

    suspend fun upsertReminder(reminder: RoutineReminder): Boolean {
        updateState { current ->
            current.copy(reminders = current.reminders.upsert(reminder))
        }
        return RoutineReminderScheduler.schedule(appContext, reminder)
    }

    suspend fun updateRoutineName(routineId: String, routineName: String): Boolean {
        val reminder = reminderForRoutine(routineId) ?: return false
        return upsertReminder(reminder.copy(routineName = routineName))
    }

    suspend fun deleteReminder(routineId: String) {
        updateState { current ->
            current.copy(reminders = current.reminders.filterNot { it.routineId == routineId })
        }
        RoutineReminderScheduler.cancel(appContext, routineId)
    }

    suspend fun restoreAllSchedules() {
        currentState().reminders.forEach { reminder ->
            RoutineReminderScheduler.schedule(appContext, reminder)
        }
    }

    suspend fun replaceAll(state: RoutineReminderState) {
        currentState().reminders.forEach { reminder ->
            RoutineReminderScheduler.cancel(appContext, reminder.routineId)
        }
        updateState { state }
    }

    suspend fun clearAllData() {
        currentState().reminders.forEach { reminder ->
            RoutineReminderScheduler.cancel(appContext, reminder.routineId)
        }
        updateState { RoutineReminderState() }
    }

    private suspend fun updateState(transform: (RoutineReminderState) -> RoutineReminderState) {
        appContext.routineReminderDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(RoutineReminderState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): RoutineReminderState {
        if (raw.isNullOrBlank()) return RoutineReminderState()
        return try {
            json.decodeFromString(RoutineReminderState.serializer(), raw)
        } catch (_: SerializationException) {
            RoutineReminderState()
        } catch (_: IllegalArgumentException) {
            RoutineReminderState()
        }
    }
}

private fun List<RoutineReminder>.upsert(reminder: RoutineReminder): List<RoutineReminder> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.routineId == reminder.routineId }
    if (index >= 0) items[index] = reminder else items.add(reminder)
    return items.sortedBy { it.routineName.lowercase() }
}
