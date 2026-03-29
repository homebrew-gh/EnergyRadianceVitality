package com.erv.app.lighttherapy

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

private val Context.lightTherapyDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_light_therapy")

class LightTherapyRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("light_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<LightLibraryState> = appContext.lightTherapyDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): LightLibraryState =
        decodeState(appContext.lightTherapyDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(newState: LightLibraryState) {
        updateState { newState.withStableSessionIds() }
    }

    suspend fun clearAllData() {
        replaceAll(LightLibraryState())
    }

    suspend fun addDevice(device: LightDevice) {
        updateState { it.copy(devices = it.devices.upsert(device)) }
    }

    suspend fun updateDevice(device: LightDevice) {
        updateState { it.copy(devices = it.devices.upsert(device)) }
    }

    suspend fun deleteDevice(deviceId: String) {
        updateState { current ->
            current.copy(
                devices = current.devices.filterNot { it.id == deviceId },
                routines = current.routines.map { r ->
                    if (r.deviceId == deviceId) r.copy(deviceId = null) else r
                }
            )
        }
    }

    suspend fun addRoutine(routine: LightRoutine) {
        updateState { it.copy(routines = it.routines.upsert(routine)) }
    }

    suspend fun updateRoutine(routine: LightRoutine) {
        updateState { it.copy(routines = it.routines.upsert(routine)) }
    }

    suspend fun deleteRoutine(routineId: String) {
        updateState { it.copy(routines = it.routines.filterNot { it.id == routineId }) }
    }

    /** Log a session (from timer completion or manual). */
    suspend fun logSession(
        date: LocalDate,
        minutes: Int,
        deviceId: String? = null,
        deviceName: String? = null,
        routineId: String? = null,
        routineName: String? = null
    ) {
        updateState { current ->
            val device = deviceId?.let { current.deviceById(it) }
            val name = deviceName ?: device?.name
            val log = current.logFor(date) ?: LightDayLog(date = date.toString())
            val session = LightSession(
                minutes = minutes,
                deviceId = deviceId,
                deviceName = name,
                routineId = routineId,
                routineName = routineName,
                loggedAtEpochSeconds = nowEpochSeconds(),
                id = UUID.randomUUID().toString()
            )
            current.copy(
                logs = current.logs.upsert(
                    log.copy(sessions = log.sessions + session)
                )
            )
        }
    }

    suspend fun deleteSession(date: LocalDate, sessionId: String) {
        updateState { current ->
            val log = current.logFor(date) ?: return@updateState current
            val newSessions = log.sessions.filterNot { it.id == sessionId }
            if (newSessions.size == log.sessions.size) return@updateState current
            current.copy(logs = current.logs.upsert(log.copy(sessions = newSessions)))
        }
    }

    private suspend fun updateState(transform: (LightLibraryState) -> LightLibraryState) {
        appContext.lightTherapyDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(LightLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): LightLibraryState {
        if (raw.isNullOrBlank()) return LightLibraryState()
        return try {
            json.decodeFromString(LightLibraryState.serializer(), raw).withStableSessionIds()
        } catch (_: SerializationException) {
            LightLibraryState()
        } catch (_: IllegalArgumentException) {
            LightLibraryState()
        }
    }
}

private fun List<LightDevice>.upsert(entry: LightDevice): List<LightDevice> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.id == entry.id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}

private fun List<LightRoutine>.upsert(entry: LightRoutine): List<LightRoutine> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.id == entry.id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}

private fun List<LightDayLog>.upsert(log: LightDayLog): List<LightDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}
