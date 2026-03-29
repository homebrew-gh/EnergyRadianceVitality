package com.erv.app.programs

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

private val Context.programsDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_programs")

class ProgramRepository(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val STATE = stringPreferencesKey("programs_library_state")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val state: Flow<ProgramsLibraryState> = appContext.programsDataStore.data.map { prefs ->
        decodeState(prefs[Keys.STATE])
    }

    suspend fun currentState(): ProgramsLibraryState =
        decodeState(appContext.programsDataStore.data.first()[Keys.STATE])

    suspend fun replaceAll(state: ProgramsLibraryState) {
        persist(state)
    }

    suspend fun clearAllData() {
        replaceAll(ProgramsLibraryState())
    }

    suspend fun upsertProgram(program: FitnessProgram) {
        val now = System.currentTimeMillis() / 1000
        val stamped = program.copy(lastModifiedEpochSeconds = now)
        updateState { lib ->
            lib.copy(
                programs = lib.programs.upsertById(stamped) { it.id },
                activeProgramId = lib.activeProgramId,
                masterUpdatedAtEpochSeconds = now
            )
        }
    }

    suspend fun deleteProgram(programId: String) {
        val now = System.currentTimeMillis() / 1000
        updateState { lib ->
            lib.copy(
                programs = lib.programs.filterNot { it.id == programId },
                activeProgramId = if (lib.activeProgramId == programId) null else lib.activeProgramId,
                masterUpdatedAtEpochSeconds = now,
                completionState = lib.syncedCompletionState().filterKeys { key ->
                    !key.startsWith("$programId|")
                },
                checklistCompletion = lib.checklistCompletion.filterKeys { key ->
                    !key.startsWith("$programId|")
                }
            )
        }
    }

    suspend fun setProgramChecklistItemDone(
        programId: String,
        blockId: String,
        itemIndex: Int,
        date: LocalDate,
        done: Boolean
    ) {
        val key = programChecklistCompletionKey(programId, blockId, itemIndex, date)
        val now = System.currentTimeMillis() / 1000
        updateState { lib ->
            lib.copy(
                completionState = lib.syncedCompletionState() + (
                    key to ProgramCompletionMark(done = done, updatedAtEpochSeconds = now)
                ),
                checklistCompletion = lib.checklistCompletion - key
            )
        }
    }

    suspend fun setProgramBlockDone(
        programId: String,
        blockId: String,
        date: LocalDate,
        done: Boolean
    ) {
        val key = programBlockCompletionKey(programId, blockId, date)
        val now = System.currentTimeMillis() / 1000
        updateState { lib ->
            lib.copy(
                completionState = lib.syncedCompletionState() + (
                    key to ProgramCompletionMark(done = done, updatedAtEpochSeconds = now)
                ),
                checklistCompletion = lib.checklistCompletion - key
            )
        }
    }

    suspend fun setActiveProgram(programId: String?) {
        val now = System.currentTimeMillis() / 1000
        updateState { lib ->
            lib.copy(
                activeProgramId = programId,
                masterUpdatedAtEpochSeconds = now
            )
        }
    }

    suspend fun activateProgramForDate(
        programId: String,
        activationDate: LocalDate = LocalDate.now(),
    ): ProgramStrategy? {
        val now = System.currentTimeMillis() / 1000
        var appliedStrategy: ProgramStrategy? = null
        updateState { lib ->
            appliedStrategy = lib.programById(programId)?.autoStrategyWhenActivated(activationDate)
            lib.copy(
                activeProgramId = programId,
                strategy = appliedStrategy ?: lib.strategy,
                masterUpdatedAtEpochSeconds = now
            )
        }
        return appliedStrategy
    }

    suspend fun setProgramStrategy(strategy: ProgramStrategy) {
        val now = System.currentTimeMillis() / 1000
        updateState { lib ->
            lib.copy(
                strategy = strategy,
                masterUpdatedAtEpochSeconds = now
            )
        }
    }

    suspend fun mergeImportedPrograms(
        imported: List<FitnessProgram>,
        setActiveFromImport: String?,
        strategyFromImport: ProgramStrategy? = null,
    ) {
        val now = System.currentTimeMillis() / 1000
        updateState { lib ->
            var next = lib
            for (p in imported) {
                next = next.copy(programs = next.programs.upsertById(p) { it.id })
            }
            if (setActiveFromImport != null && next.programs.any { it.id == setActiveFromImport }) {
                next = next.copy(activeProgramId = setActiveFromImport)
            }
            if (strategyFromImport != null) {
                next = next.copy(strategy = strategyFromImport)
            }
            next.copy(masterUpdatedAtEpochSeconds = now)
        }
    }

    private suspend fun persist(state: ProgramsLibraryState) {
        appContext.programsDataStore.edit { prefs ->
            prefs[Keys.STATE] = json.encodeToString(ProgramsLibraryState.serializer(), state.sanitized())
        }
    }

    private suspend fun updateState(transform: (ProgramsLibraryState) -> ProgramsLibraryState) {
        appContext.programsDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            prefs[Keys.STATE] = json.encodeToString(ProgramsLibraryState.serializer(), transform(current).sanitized())
        }
    }

    private fun decodeState(raw: String?): ProgramsLibraryState {
        if (raw.isNullOrBlank()) return ProgramsLibraryState()
        return try {
            json.decodeFromString(ProgramsLibraryState.serializer(), raw).sanitized()
        } catch (_: SerializationException) {
            ProgramsLibraryState()
        } catch (_: IllegalArgumentException) {
            ProgramsLibraryState()
        }
    }
}

fun ProgramsLibraryState.sanitized(): ProgramsLibraryState {
    val validActive = activeProgramId?.takeIf { id -> programs.any { it.id == id } }
    val validIds = programs.map { it.id }.toSet()
    val completion = syncedCompletionState()
    val fallbackMasterUpdatedAt = programs.maxOfOrNull { it.lastModifiedEpochSeconds } ?: 0L
    return copy(
        activeProgramId = validActive,
        strategy = strategy.sanitized(validIds),
        masterUpdatedAtEpochSeconds = maxOf(masterUpdatedAtEpochSeconds, fallbackMasterUpdatedAt),
        completionState = completion,
        checklistCompletion = emptyMap()
    )
}

fun ProgramsLibraryState.syncedCompletionState(): Map<String, ProgramCompletionMark> =
    if (completionState.isNotEmpty()) {
        completionState
    } else {
        checklistCompletion.mapValues { (_, done) ->
            ProgramCompletionMark(done = done, updatedAtEpochSeconds = 0L)
        }
    }

private fun <T : Any> List<T>.upsertById(entry: T, idSelector: (T) -> String): List<T> {
    val items = toMutableList()
    val id = idSelector(entry)
    val index = items.indexOfFirst { idSelector(it) == id }
    if (index >= 0) items[index] = entry else items.add(entry)
    return items
}
