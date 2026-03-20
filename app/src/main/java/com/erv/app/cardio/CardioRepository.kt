package com.erv.app.cardio

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

private val Context.cardioDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_cardio")

class CardioRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("cardio_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<CardioLibraryState> = appContext.cardioDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): CardioLibraryState =
        decodeState(appContext.cardioDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(state: CardioLibraryState) {
        updateState { state }
    }

    suspend fun addRoutine(routine: CardioRoutine) {
        updateState { it.copy(routines = it.routines.upsertById(routine)) }
    }

    suspend fun updateRoutine(routine: CardioRoutine) {
        updateState { it.copy(routines = it.routines.upsertById(routine)) }
    }

    suspend fun deleteRoutine(routineId: String) {
        updateState { it.copy(routines = it.routines.filterNot { r -> r.id == routineId }) }
    }

    suspend fun addCustomType(type: CardioCustomActivityType) {
        updateState { it.copy(customActivityTypes = it.customActivityTypes.upsertById(type)) }
    }

    suspend fun updateCustomType(type: CardioCustomActivityType) {
        updateState { it.copy(customActivityTypes = it.customActivityTypes.upsertById(type)) }
    }

    suspend fun deleteCustomType(typeId: String) {
        updateState { it.copy(customActivityTypes = it.customActivityTypes.filterNot { t -> t.id == typeId }) }
    }

    suspend fun addSession(date: LocalDate, session: CardioSession) {
        updateState { current ->
            val log = current.logFor(date) ?: CardioDayLog(date = date.toString())
            current.copy(
                logs = current.logs.upsertLog(
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
            current.copy(logs = current.logs.upsertLog(log.copy(sessions = newSessions)))
        }
    }

    private suspend fun updateState(transform: (CardioLibraryState) -> CardioLibraryState) {
        appContext.cardioDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(CardioLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): CardioLibraryState {
        if (raw.isNullOrBlank()) return CardioLibraryState()
        return try {
            json.decodeFromString(CardioLibraryState.serializer(), raw)
        } catch (_: SerializationException) {
            CardioLibraryState()
        } catch (_: IllegalArgumentException) {
            CardioLibraryState()
        }
    }
}

private fun <T : Any> List<T>.upsertById(entry: T, idSelector: (T) -> String): List<T> {
    val items = toMutableList()
    val id = idSelector(entry)
    val index = items.indexOfFirst { idSelector(it) == id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}

private fun List<CardioRoutine>.upsertById(routine: CardioRoutine): List<CardioRoutine> =
    upsertById(routine) { it.id }

private fun List<CardioCustomActivityType>.upsertById(type: CardioCustomActivityType): List<CardioCustomActivityType> =
    upsertById(type) { it.id }

private fun List<CardioDayLog>.upsertLog(log: CardioDayLog): List<CardioDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}
