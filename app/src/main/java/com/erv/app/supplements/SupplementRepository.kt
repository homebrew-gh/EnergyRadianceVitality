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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val Context.supplementDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_supplements")

/** Tracks log dates we just published so fetch doesn't overwrite with stale relay data. */
private const val PREFER_LOCAL_LOG_MS = 90_000L

class SupplementRepository(context: Context) {

    private val appContext = context.applicationContext
    private val lastPublishedLogDates = ConcurrentHashMap<String, Long>()

    private object Keys {
        val STATE = stringPreferencesKey("supplement_state")
    }

    /** Call after publishing a daily log so the next fetch won't overwrite it with stale relay data. */
    fun markLogPublished(date: String) {
        lastPublishedLogDates[date] = System.currentTimeMillis()
    }

    /** Returns date strings for which we should prefer local log over remote when merging. */
    fun getRecentlyPublishedLogDates(withinMs: Long = PREFER_LOCAL_LOG_MS): Set<String> {
        val now = System.currentTimeMillis()
        val expired = lastPublishedLogDates.filter { now - it.value > withinMs }.keys
        expired.forEach { lastPublishedLogDates.remove(it) }
        return lastPublishedLogDates.filter { now - it.value <= withinMs }.keys.toSet()
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

    suspend fun clearAllData() {
        replaceAll(SupplementLibraryState())
        lastPublishedLogDates.clear()
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
                dosageTaken = dosageTaken ?: supplement.dosagePlan.summary(),
                takenAtEpochSeconds = nowEpochSeconds(),
                note = note,
                id = UUID.randomUUID().toString()
            )
            current.copy(logs = current.logs.upsert(log.copy(adHocIntakes = log.adHocIntakes + intake)))
        }
    }

    suspend fun logRoutineRun(date: LocalDate, routineId: String) {
        val routine = currentState().routineById(routineId) ?: return
        logRoutineRun(date, routine.id, routine.name, routine.steps)
    }

    suspend fun logRoutineRun(
        date: LocalDate,
        routineId: String,
        routineName: String,
        steps: List<SupplementRoutineStep>
    ) {
        updateState { current ->
            val log = current.logFor(date) ?: SupplementDayLog(date = date.toString())
            val run = SupplementRoutineRun(
                routineId = routineId,
                routineName = routineName,
                takenAtEpochSeconds = nowEpochSeconds(),
                stepIntakes = steps.mapNotNull { step ->
                    val supplement = current.supplementById(step.supplementId) ?: return@mapNotNull null
                    SupplementIntake(
                        supplementId = supplement.id,
                        supplementName = supplement.name,
                        dosageTaken = step.dosageOverride ?: step.describe(supplement.name),
                        takenAtEpochSeconds = nowEpochSeconds(),
                        note = step.note,
                        quantity = step.quantity?.coerceAtLeast(1) ?: 1,
                        id = UUID.randomUUID().toString()
                    )
                }
            )
            current.copy(logs = current.logs.upsert(log.copy(routineRuns = log.routineRuns + run)))
        }
    }

    /**
     * Removes one intake from the log for the given date (by intake id).
     * Activity summary and log list both derive from this state, so they update automatically.
     */
    suspend fun removeIntake(date: LocalDate, intakeId: String) {
        updateState { current ->
            val log = current.logFor(date) ?: return@updateState current
            val newRoutineRuns = log.routineRuns.mapNotNull { run ->
                val newSteps = run.stepIntakes.filterNot { it.id == intakeId }
                if (newSteps.isEmpty()) null else run.copy(stepIntakes = newSteps)
            }
            val newAdHoc = log.adHocIntakes.filterNot { it.id == intakeId }
            val newLog = log.copy(
                routineRuns = newRoutineRuns,
                adHocIntakes = newAdHoc
            )
            current.copy(logs = current.logs.upsert(newLog))
        }
    }

    /**
     * Removes one intake from the log by matching fields (for legacy entries that have no id).
     * Removes the first matching intake in routine runs, then ad-hoc if not found.
     */
    suspend fun removeIntakeByMatch(
        date: LocalDate,
        supplementId: String,
        takenAtEpochSeconds: Long,
        sourceLabel: String
    ) {
        updateState { current ->
            val log = current.logFor(date) ?: return@updateState current
            val targetSec = takenAtEpochSeconds
            fun matches(i: SupplementIntake): Boolean =
                i.supplementId == supplementId && (i.takenAtEpochSeconds ?: 0L) == targetSec
            var removed = false
            val newRoutineRuns = log.routineRuns.mapNotNull { run ->
                if (removed) return@mapNotNull run
                val idx = run.stepIntakes.indexOfFirst { matches(it) }
                if (idx < 0) return@mapNotNull run
                removed = true
                val newSteps = run.stepIntakes.toMutableList().apply { removeAt(idx) }
                if (newSteps.isEmpty()) null else run.copy(stepIntakes = newSteps)
            }
            val newAdHoc = if (!removed) {
                val idx = log.adHocIntakes.indexOfFirst { matches(it) }
                if (idx >= 0) log.adHocIntakes.toMutableList().apply { removeAt(idx) } else log.adHocIntakes
            } else {
                log.adHocIntakes
            }
            val newLog = log.copy(
                routineRuns = newRoutineRuns,
                adHocIntakes = newAdHoc
            )
            current.copy(logs = current.logs.upsert(newLog))
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
        brand: String,
        dosagePlan: SupplementDosagePlan,
        notes: String = ""
    ) {
        updateState { current ->
            val updated = current.supplementById(supplementId)?.copy(
                name = name,
                brand = brand,
                dosagePlan = dosagePlan,
                notes = notes
            ) ?: return@updateState current
            current.copy(supplements = current.supplements.upsert(updated))
        }
    }

    suspend fun renameRoutine(
        routineId: String,
        name: String,
        timeOfDay: SupplementTimeOfDay,
        steps: List<SupplementRoutineStep>,
        notes: String = ""
    ) {
        updateState { current ->
            val updated = current.routineById(routineId)?.copy(
                name = name,
                timeOfDay = timeOfDay,
                steps = steps,
                notes = notes
            ) ?: return@updateState current
            current.copy(routines = current.routines.upsert(updated))
        }
    }

    suspend fun addSupplement(
        name: String,
        brand: String,
        dosagePlan: SupplementDosagePlan,
        notes: String = ""
    ): SupplementEntry {
        val entry = SupplementEntry(
            name = name,
            brand = brand,
            dosagePlan = dosagePlan,
            notes = notes
        )
        upsertSupplement(entry)
        return entry
    }

    suspend fun addRoutine(
        name: String,
        timeOfDay: SupplementTimeOfDay,
        steps: List<SupplementRoutineStep>,
        notes: String = ""
    ): SupplementRoutine {
        val routine = SupplementRoutine(
            name = name,
            timeOfDay = timeOfDay,
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

