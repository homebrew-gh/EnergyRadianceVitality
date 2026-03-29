package com.erv.app.unifiedroutines

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

    suspend fun upsertRoutine(routine: UnifiedRoutine) {
        val stamped = routine.touch()
        updateState { state ->
            state.copy(routines = state.routines.upsertById(stamped) { it.id })
        }
    }

    suspend fun deleteRoutine(routineId: String) {
        updateState { state ->
            state.copy(
                routines = state.routines.filterNot { it.id == routineId },
                sessions = state.sessions.filterNot { it.routineId == routineId },
                activeSession = state.activeSession?.takeUnless { it.routineId == routineId }
            )
        }
    }

    suspend fun startSession(routineId: String) {
        updateState { state ->
            val routine = state.routineById(routineId) ?: return@updateState state
            createStartedSessionState(
                state = state,
                routine = routine,
                persistInRoutines = true,
                startedAsAdHoc = false
            )
        }
    }

    suspend fun startAdHocSession(routine: UnifiedRoutine) {
        updateState { state ->
            createStartedSessionState(
                state = state,
                routine = routine,
                persistInRoutines = false,
                startedAsAdHoc = true
            )
        }
    }

    private fun createStartedSessionState(
        state: UnifiedRoutineLibraryState,
        routine: UnifiedRoutine,
        persistInRoutines: Boolean,
        startedAsAdHoc: Boolean,
    ): UnifiedRoutineLibraryState {
            val session = UnifiedWorkoutSession(
                routineId = routine.id,
                routineName = routine.name,
                startedAtEpochSeconds = nowUnifiedRoutineEpochSeconds(),
                routineSnapshot = routine,
                startedAsAdHoc = startedAsAdHoc,
                blocks = routine.blocks.map { block ->
                    UnifiedWorkoutBlockRecap(
                        blockId = block.id,
                        type = block.type,
                        title = block.title,
                        sourceBlock = block,
                    )
                }
            )
            return state.copy(
                routines = if (persistInRoutines) state.routines else state.routines.filterNot { it.id == routine.id },
                sessions = state.sessions.filterNot { it.id == state.activeSession?.sessionId } + session,
                activeSession = UnifiedRoutineSessionState(
                    sessionId = session.id,
                    routineId = routine.id,
                    routineSnapshot = routine,
                    startedAsAdHoc = startedAsAdHoc,
                    startedAtEpochSeconds = session.startedAtEpochSeconds,
                    completedBlockIds = emptyList(),
                    lastLaunchedBlockId = null
                )
            )
    }

    suspend fun markBlockCompleted(routineId: String, blockId: String, completed: Boolean) {
        updateState { state ->
            val session = state.activeSession ?: return@updateState state
            if (session.routineId != routineId) return@updateState state
            val nextIds = if (completed) {
                (session.completedBlockIds + blockId).distinct()
            } else {
                session.completedBlockIds.filterNot { it == blockId }
            }
            state.copy(
                sessions = state.sessions.updateById(session.sessionId) { existing ->
                    existing.copy(completedBlockIds = nextIds)
                },
                activeSession = session.copy(completedBlockIds = nextIds)
            )
        }
    }

    suspend fun setLastLaunchedBlock(routineId: String, blockId: String?) {
        updateState { state ->
            val session = state.activeSession ?: return@updateState state
            if (session.routineId != routineId) return@updateState state
            state.copy(
                sessions = state.sessions.updateById(session.sessionId) { existing ->
                    existing.copy(
                        lastLaunchedBlockId = blockId,
                        blocks = existing.blocks.map { block ->
                            if (block.blockId != blockId || block.startedAtEpochSeconds != null) block
                            else block.copy(startedAtEpochSeconds = nowUnifiedRoutineEpochSeconds())
                        }
                    )
                },
                activeSession = session.copy(lastLaunchedBlockId = blockId)
            )
        }
    }

    suspend fun attachLoggedBlock(routineId: String, blockId: String, logDate: String, entryId: String) {
        updateState { state ->
            val session = state.activeSession ?: return@updateState state
            if (session.routineId != routineId) return@updateState state
            val nextIds = (session.completedBlockIds + blockId).distinct()
            state.copy(
                sessions = state.sessions.updateById(session.sessionId) { existing ->
                    existing.copy(
                        completedBlockIds = nextIds,
                        lastLaunchedBlockId = blockId,
                        blocks = existing.blocks.map { block ->
                            if (block.blockId != blockId) block
                            else block.copy(
                                startedAtEpochSeconds = block.startedAtEpochSeconds ?: nowUnifiedRoutineEpochSeconds(),
                                finishedAtEpochSeconds = nowUnifiedRoutineEpochSeconds(),
                                linkedLogDate = logDate,
                                linkedEntryId = entryId
                            )
                        }
                    )
                },
                activeSession = session.copy(
                    completedBlockIds = nextIds,
                    lastLaunchedBlockId = blockId
                )
            )
        }
    }

    suspend fun finishSession(routineId: String, heartRate: com.erv.app.cardio.CardioHrScaffolding?) {
        updateState { state ->
            val session = state.activeSession ?: return@updateState state
            if (session.routineId != routineId) return@updateState state
            state.copy(
                sessions = state.sessions.updateById(session.sessionId) { existing ->
                    existing.copy(
                        completedBlockIds = session.completedBlockIds,
                        lastLaunchedBlockId = session.lastLaunchedBlockId,
                        finishedAtEpochSeconds = nowUnifiedRoutineEpochSeconds(),
                        heartRate = heartRate
                    )
                },
                activeSession = null
            )
        }
    }

    suspend fun clearActiveSession() {
        updateState { state ->
            state.copy(
                sessions = state.sessions.filterNot { it.id == state.activeSession?.sessionId },
                activeSession = null
            )
        }
    }

    private suspend fun updateState(transform: (UnifiedRoutineLibraryState) -> UnifiedRoutineLibraryState) {
        appContext.unifiedRoutineDataStore.edit { prefs ->
            val current = decodeState(prefs[Keys.STATE])
            val updated = transform(current)
            prefs[Keys.STATE] = json.encodeToString(UnifiedRoutineLibraryState.serializer(), updated)
        }
    }

    private fun decodeState(raw: String?): UnifiedRoutineLibraryState {
        if (raw.isNullOrBlank()) return UnifiedRoutineLibraryState()
        return try {
            json.decodeFromString(UnifiedRoutineLibraryState.serializer(), raw)
        } catch (_: SerializationException) {
            UnifiedRoutineLibraryState()
        } catch (_: IllegalArgumentException) {
            UnifiedRoutineLibraryState()
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

private fun List<UnifiedWorkoutSession>.updateById(
    sessionId: String,
    transform: (UnifiedWorkoutSession) -> UnifiedWorkoutSession
): List<UnifiedWorkoutSession> = map { session ->
    if (session.id == sessionId) transform(session) else session
}
