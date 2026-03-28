package com.erv.app.stretching

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erv.app.unifiedroutines.UnifiedSessionLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

private val Context.stretchingDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_stretching")

class StretchingRepository(context: Context) {

    private val appContext = context.applicationContext

    /** Bundled catalog; not persisted or synced. */
    val catalog: List<StretchCatalogEntry> = StretchCatalogLoader.load(appContext)

    private object Keys {
        val STATE = stringPreferencesKey("stretching_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<StretchLibraryState> = appContext.stretchingDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): StretchLibraryState =
        decodeState(appContext.stretchingDataStore.data.first()[Keys.STATE])

    fun stretchById(id: String): StretchCatalogEntry? = catalog.firstOrNull { it.id == id }

    suspend fun replaceAll(newState: StretchLibraryState) {
        updateState { newState.withStableSessionIds() }
    }

    suspend fun addRoutine(routine: StretchRoutine) {
        updateState { it.copy(routines = it.routines.upsert(routine)) }
    }

    suspend fun updateRoutine(routine: StretchRoutine) {
        updateState { it.copy(routines = it.routines.upsert(routine)) }
    }

    suspend fun deleteRoutine(routineId: String) {
        updateState { it.copy(routines = it.routines.filterNot { it.id == routineId }) }
    }

    suspend fun logSession(
        date: LocalDate,
        routineId: String? = null,
        routineName: String? = null,
        stretchIds: List<String>,
        totalMinutes: Int,
        unifiedLink: UnifiedSessionLink? = null
    ) {
        updateState { current ->
            val log = current.logFor(date) ?: StretchDayLog(date = date.toString())
            val session = StretchSession(
                routineId = routineId,
                routineName = routineName,
                stretchIds = stretchIds,
                totalMinutes = totalMinutes.coerceAtLeast(0),
                loggedAtEpochSeconds = nowEpochSeconds(),
                id = UUID.randomUUID().toString(),
                unifiedLink = unifiedLink
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

    private suspend fun updateState(transform: (StretchLibraryState) -> StretchLibraryState) {
        appContext.stretchingDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(StretchLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): StretchLibraryState {
        if (raw.isNullOrBlank()) return StretchLibraryState()
        return try {
            json.decodeFromString(StretchLibraryState.serializer(), raw).withStableSessionIds()
        } catch (_: SerializationException) {
            StretchLibraryState()
        } catch (_: IllegalArgumentException) {
            StretchLibraryState()
        }
    }
}

private fun List<StretchRoutine>.upsert(entry: StretchRoutine): List<StretchRoutine> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.id == entry.id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}

private fun List<StretchDayLog>.upsert(log: StretchDayLog): List<StretchDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}
