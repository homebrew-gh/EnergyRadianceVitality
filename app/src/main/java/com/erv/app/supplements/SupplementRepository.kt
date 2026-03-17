package com.erv.app.supplements

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

private val Context.supplementDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_supplements")

class SupplementRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("supplement_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<SupplementLibraryState> = appContext.supplementDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): SupplementLibraryState =
        decodeState(appContext.supplementDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(state: SupplementLibraryState) {
        updateState { state }
    }

    suspend fun upsertSupplement(entry: SupplementEntry) {
        updateState { current ->
            current.copy(supplements = current.supplements.upsert(entry))
        }
    }

    suspend fun deleteSupplement(supplementId: String) {
        updateState { current ->
            current.copy(
                supplements = current.supplements.filterNot { it.id == supplementId },
                routines = current.routines.map { routine ->
                    routine.copy(steps = routine.steps.filterNot { it.supplementId == supplementId })
                }
            )
        }
    }

    suspend fun upsertRoutine(routine: SupplementRoutine) {
        updateState { current ->
            current.copy(routines = current.routines.upsert(routine))
        }
    }

    suspend fun deleteRoutine(routineId: String) {
        updateState { current ->
            current.copy(routines = current.routines.filterNot { it.id == routineId })
        }
    }

    suspend fun logAdHocIntake(
        date: LocalDate,
        supplementId: String,
        dosageTaken: String? = null,
        note: String? = null
    ) {
        updateState { current ->
            val supplement = current.supplementById(supplementId) ?: return@updateState current
            val log = current.logFor(date) ?: SupplementDayLog(date = date.toString())
            val intake = SupplementIntake(
                supplementId = supplement.id,
                supplementName = supplement.name,
                dosageTaken = dosageTaken ?: supplement.dosage,
                takenAtEpochSeconds = nowEpochSeconds(),
                note = note
            )
            current.copy(logs = current.logs.upsert(log.copy(adHocIntakes = log.adHocIntakes + intake)))
        }
    }

    suspend fun logRoutineRun(date: LocalDate, routineId: String) {
        updateState { current ->
            val routine = current.routineById(routineId) ?: return@updateState current
            val log = current.logFor(date) ?: SupplementDayLog(date = date.toString())
            val run = SupplementRoutineRun(
                routineId = routine.id,
                routineName = routine.name,
                takenAtEpochSeconds = nowEpochSeconds(),
                stepIntakes = routine.steps.mapNotNull { step ->
                    val supplement = current.supplementById(step.supplementId) ?: return@mapNotNull null
                    SupplementIntake(
                        supplementId = supplement.id,
                        supplementName = supplement.name,
                        dosageTaken = step.dosageOverride ?: supplement.dosage,
                        takenAtEpochSeconds = nowEpochSeconds(),
                        note = step.note
                    )
                }
            )
            current.copy(logs = current.logs.upsert(log.copy(routineRuns = log.routineRuns + run)))
        }
    }

    suspend fun attachInfo(
        supplementId: String,
        info: SupplementInfo,
        productId: String? = info.productId
    ) {
        updateState { current ->
            val updated = current.supplementById(supplementId)?.copy(
                productId = productId,
                info = info
            ) ?: return@updateState current
            current.copy(supplements = current.supplements.upsert(updated))
        }
    }

    suspend fun renameSupplement(
        supplementId: String,
        name: String,
        dosage: String,
        frequency: String,
        whenToTake: String,
        notes: String = ""
    ) {
        updateState { current ->
            val updated = current.supplementById(supplementId)?.copy(
                name = name,
                dosage = dosage,
                frequency = frequency,
                whenToTake = whenToTake,
                notes = notes
            ) ?: return@updateState current
            current.copy(supplements = current.supplements.upsert(updated))
        }
    }

    suspend fun renameRoutine(
        routineId: String,
        name: String,
        steps: List<SupplementRoutineStep>,
        notes: String = ""
    ) {
        updateState { current ->
            val updated = current.routineById(routineId)?.copy(
                name = name,
                steps = steps,
                notes = notes
            ) ?: return@updateState current
            current.copy(routines = current.routines.upsert(updated))
        }
    }

    suspend fun addSupplement(
        name: String,
        dosage: String,
        frequency: String,
        whenToTake: String,
        notes: String = ""
    ): SupplementEntry {
        val entry = SupplementEntry(
            name = name,
            dosage = dosage,
            frequency = frequency,
            whenToTake = whenToTake,
            notes = notes
        )
        upsertSupplement(entry)
        return entry
    }

    suspend fun addRoutine(
        name: String,
        steps: List<SupplementRoutineStep>,
        notes: String = ""
    ): SupplementRoutine {
        val routine = SupplementRoutine(
            name = name,
            steps = steps,
            notes = notes
        )
        upsertRoutine(routine)
        return routine
    }

    suspend fun summaryFor(date: LocalDate): SupplementDaySummary =
        currentState().summaryFor(date)

    private suspend fun updateState(transform: (SupplementLibraryState) -> SupplementLibraryState) {
        appContext.supplementDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(SupplementLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): SupplementLibraryState {
        if (raw.isNullOrBlank()) return SupplementLibraryState()
        return try {
            json.decodeFromString(SupplementLibraryState.serializer(), raw)
        } catch (_: SerializationException) {
            SupplementLibraryState()
        } catch (_: IllegalArgumentException) {
            SupplementLibraryState()
        }
    }
}

private fun List<SupplementEntry>.upsert(entry: SupplementEntry): List<SupplementEntry> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.id == entry.id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}

private fun List<SupplementRoutine>.upsert(routine: SupplementRoutine): List<SupplementRoutine> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.id == routine.id }
    if (index >= 0) items[index] = routine else items.add(routine)
    return items
}

private fun List<SupplementDayLog>.upsert(log: SupplementDayLog): List<SupplementDayLog> {
    val items = toMutableList()
    val index = items.indexOfFirst { it.date == log.date }
    if (index >= 0) items[index] = log else items.add(log)
    return items.sortedBy { it.date }
}

