package com.erv.app.heatcold

import com.erv.app.data.TemperatureUnit
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
import java.time.LocalDate
import java.util.UUID

private val Context.heatColdDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_heat_cold")

class HeatColdRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("heat_cold_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<HeatColdLibraryState> = appContext.heatColdDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): HeatColdLibraryState =
        decodeState(appContext.heatColdDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(newState: HeatColdLibraryState) {
        updateState { newState.withStableSessionIds() }
    }

    suspend fun clearAllData() {
        replaceAll(HeatColdLibraryState())
    }

    suspend fun logSession(
        mode: HeatColdMode,
        date: LocalDate,
        durationSeconds: Int,
        tempValue: Double? = null,
        tempUnit: TemperatureUnit? = null
    ) {
        updateState { current ->
            val log = current.logFor(mode, date) ?: HeatColdDayLog(date = date.toString())
            val session = HeatColdSession(
                id = UUID.randomUUID().toString(),
                durationSeconds = durationSeconds,
                tempValue = tempValue,
                tempUnit = tempUnit,
                loggedAtEpochSeconds = nowEpochSeconds()
            )
            current.withLogs(mode, current.logsFor(mode).upsertLog(log.copy(sessions = log.sessions + session)))
        }
    }

    suspend fun deleteSession(mode: HeatColdMode, date: LocalDate, sessionId: String) {
        updateState { current ->
            val log = current.logFor(mode, date) ?: return@updateState current
            val newSessions = log.sessions.filterNot { it.id == sessionId }
            if (newSessions.size == log.sessions.size) return@updateState current
            current.withLogs(mode, current.logsFor(mode).upsertLog(log.copy(sessions = newSessions)))
        }
    }

    private suspend fun updateState(transform: (HeatColdLibraryState) -> HeatColdLibraryState) {
        appContext.heatColdDataStore.edit { prefs ->
            val raw = prefs[Keys.STATE]
            val current = decodeState(raw) { prefs.preserveUnreadableState(Keys.STATE, raw) }
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(HeatColdLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?, onUnreadable: () -> Unit = {}): HeatColdLibraryState {
        if (raw.isNullOrBlank()) return HeatColdLibraryState()
        return try {
            json.decodeFromString(HeatColdLibraryState.serializer(), raw).withStableSessionIds()
        } catch (_: SerializationException) {
            onUnreadable()
            HeatColdLibraryState()
        } catch (_: IllegalArgumentException) {
            onUnreadable()
            HeatColdLibraryState()
        }
    }
}

private fun List<HeatColdDayLog>.upsertLog(log: HeatColdDayLog): List<HeatColdDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}
