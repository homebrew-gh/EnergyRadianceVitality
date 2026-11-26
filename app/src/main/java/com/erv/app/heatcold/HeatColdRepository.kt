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
        val LAST_SETTINGS = stringPreferencesKey("heat_cold_last_settings")
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

    /** The last durations and temperatures used on this phone (not synced). */
    val lastSettings: Flow<HeatColdLastSettings> = appContext.heatColdDataStore.data.map { prefs ->
        decodeLastSettings(prefs[Keys.LAST_SETTINGS])
    }

    suspend fun currentLastSettings(): HeatColdLastSettings =
        decodeLastSettings(appContext.heatColdDataStore.data.first()[Keys.LAST_SETTINGS])

    suspend fun saveLastSettings(settings: HeatColdLastSettings) {
        appContext.heatColdDataStore.edit { prefs ->
            prefs[Keys.LAST_SETTINGS] = json.encodeToString(HeatColdLastSettings.serializer(), settings)
        }
    }

    private fun decodeLastSettings(raw: String?): HeatColdLastSettings {
        if (raw.isNullOrBlank()) return HeatColdLastSettings()
        return runCatching { json.decodeFromString(HeatColdLastSettings.serializer(), raw) }
            .getOrDefault(HeatColdLastSettings())
    }

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
        tempUnit: TemperatureUnit? = null,
        loggedAtEpochSeconds: Long = nowEpochSeconds(),
        contrastId: String? = null
    ) {
        updateState { current ->
            val log = current.logFor(mode, date) ?: HeatColdDayLog(date = date.toString())
            val session = HeatColdSession(
                id = UUID.randomUUID().toString(),
                durationSeconds = durationSeconds,
                tempValue = tempValue,
                tempUnit = tempUnit,
                loggedAtEpochSeconds = loggedAtEpochSeconds,
                lastModifiedEpochSeconds = nowEpochSeconds(),
                contrastId = contrastId
            )
            current.withLogs(mode, current.logsFor(mode).upsertLog(log.copy(sessions = log.sessions + session)))
        }
    }

    /**
     * Changes a saved session. A new [newMode] or [newDate] moves it, leaving a delete marker on the
     * old day so a synced copy elsewhere does not bring it back there.
     */
    suspend fun updateSession(
        mode: HeatColdMode,
        date: LocalDate,
        sessionId: String,
        newMode: HeatColdMode,
        newDate: LocalDate,
        durationSeconds: Int,
        tempValue: Double?,
        tempUnit: TemperatureUnit?,
        loggedAtEpochSeconds: Long
    ) {
        updateState { current ->
            val oldLog = current.logFor(mode, date) ?: return@updateState current
            val old = oldLog.sessions.firstOrNull { it.id == sessionId } ?: return@updateState current
            val now = nowEpochSeconds()
            val updated = old.copy(
                durationSeconds = durationSeconds,
                tempValue = tempValue,
                tempUnit = tempUnit,
                loggedAtEpochSeconds = loggedAtEpochSeconds,
                lastModifiedEpochSeconds = now
            )
            if (newMode == mode && newDate == date) {
                val sessions = oldLog.sessions.map { if (it.id == sessionId) updated else it }
                return@updateState current.withLogs(mode, current.logsFor(mode).upsertLog(oldLog.copy(sessions = sessions)))
            }
            val removed = current.withLogs(mode, current.logsFor(mode).upsertLog(oldLog.removing(sessionId, now)))
            val target = removed.logFor(newMode, newDate) ?: HeatColdDayLog(date = newDate.toString())
            val targetLog = target.copy(
                sessions = target.sessions.filterNot { it.id == sessionId } + updated,
                deletedSessionIds = target.deletedSessionIds - sessionId
            )
            removed.withLogs(newMode, removed.logsFor(newMode).upsertLog(targetLog))
        }
    }

    suspend fun deleteSession(mode: HeatColdMode, date: LocalDate, sessionId: String) {
        updateState { current ->
            val log = current.logFor(mode, date) ?: return@updateState current
            if (log.sessions.none { it.id == sessionId }) return@updateState current
            current.withLogs(mode, current.logsFor(mode).upsertLog(log.removing(sessionId, nowEpochSeconds())))
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

private fun HeatColdDayLog.removing(sessionId: String, at: Long): HeatColdDayLog =
    copy(
        sessions = sessions.filterNot { it.id == sessionId },
        deletedSessionIds = deletedSessionIds + (sessionId to at)
    )

private fun List<HeatColdDayLog>.upsertLog(log: HeatColdDayLog): List<HeatColdDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}
