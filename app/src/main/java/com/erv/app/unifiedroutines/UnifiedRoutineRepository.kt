package com.erv.app.unifiedroutines

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

private val Context.unifiedRoutineDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_unified_routines")

/**
 * Saved data from the old Unified Workouts feature, which the phone no longer runs. It stays so
 * backups keep it, data deletion clears it and heart rate trends still count those sessions. The
 * cardio, weight and stretch entries those sessions logged live in their own logs.
 */
class UnifiedRoutineRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("unified_routine_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<UnifiedRoutineLibraryState> = appContext.unifiedRoutineDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): UnifiedRoutineLibraryState =
        decodeState(appContext.unifiedRoutineDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(newState: UnifiedRoutineLibraryState) {
        updateState { newState }
    }

    suspend fun clearAllData() {
        replaceAll(UnifiedRoutineLibraryState())
    }

    private suspend fun updateState(transform: (UnifiedRoutineLibraryState) -> UnifiedRoutineLibraryState) {
        appContext.unifiedRoutineDataStore.edit { prefs ->
            val raw = prefs[Keys.STATE]
            val current = decodeState(raw) { prefs.preserveUnreadableState(Keys.STATE, raw) }
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(UnifiedRoutineLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?, onUnreadable: () -> Unit = {}): UnifiedRoutineLibraryState {
        if (raw.isNullOrBlank()) return UnifiedRoutineLibraryState()
        return try {
            json.decodeFromString(UnifiedRoutineLibraryState.serializer(), raw)
        } catch (_: SerializationException) {
            onUnreadable()
            UnifiedRoutineLibraryState()
        } catch (_: IllegalArgumentException) {
            onUnreadable()
            UnifiedRoutineLibraryState()
        }
    }
}
