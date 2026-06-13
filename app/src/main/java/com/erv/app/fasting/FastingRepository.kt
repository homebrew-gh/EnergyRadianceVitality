package com.erv.app.fasting

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.fastingDataStore: DataStore<Preferences> by preferencesDataStore(name = "erv_fasting")

class FastingRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val ACTIVE_SESSION_JSON_V1 = stringPreferencesKey("active_session_json_v1")
        val HISTORY_JSON_V1 = stringPreferencesKey("history_json_v1")
        val INTERMITTENT_PLAN_JSON_V1 = stringPreferencesKey("intermittent_plan_json_v1")
    }

    val state: Flow<FastingLibraryState> = context.fastingDataStore.data.map { prefs ->
        FastingLibraryState(
            activeSession = decodeSession(prefs[Keys.ACTIVE_SESSION_JSON_V1]),
            history = decodeHistory(prefs[Keys.HISTORY_JSON_V1]),
            intermittentPlan = decodeIntermittentPlan(prefs[Keys.INTERMITTENT_PLAN_JSON_V1]),
        )
    }

    suspend fun currentState(): FastingLibraryState = state.first()

    suspend fun replaceAll(state: FastingLibraryState) {
        context.fastingDataStore.edit { prefs ->
            val active = state.activeSession
            if (active == null) {
                prefs.remove(Keys.ACTIVE_SESSION_JSON_V1)
            } else {
                prefs[Keys.ACTIVE_SESSION_JSON_V1] = encodeSession(active)
            }
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(state.history)
            prefs[Keys.INTERMITTENT_PLAN_JSON_V1] = encodeIntermittentPlan(state.intermittentPlan)
        }
    }

    suspend fun clearAllData() {
        replaceAll(FastingLibraryState())
    }

    suspend fun startFast(targetDays: Int, nowEpochSeconds: Long = fastingNowEpochSeconds()): FastingSession {
        val days = targetDays.coerceIn(1, 3)
        val session = FastingSession(
            targetDays = days,
            startedAtEpochSeconds = nowEpochSeconds,
            targetEndEpochSeconds = nowEpochSeconds + fastingTargetSeconds(days),
        )
        context.fastingDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SESSION_JSON_V1] = encodeSession(session)
        }
        return session
    }

    suspend fun completeActive(
        mood: FastingMood?,
        weight: String,
        notes: String,
        endedAtEpochSeconds: Long = fastingNowEpochSeconds(),
    ): FastingSession? {
        var saved: FastingSession? = null
        context.fastingDataStore.edit { prefs ->
            val active = decodeSession(prefs[Keys.ACTIVE_SESSION_JSON_V1]) ?: return@edit
            val history = decodeHistory(prefs[Keys.HISTORY_JSON_V1])
            val completed = active.copy(
                status = FastingStatus.COMPLETED,
                endedAtEpochSeconds = endedAtEpochSeconds,
                mood = mood,
                weight = weight.trim(),
                notes = notes.trim(),
            )
            prefs.remove(Keys.ACTIVE_SESSION_JSON_V1)
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(listOf(completed) + history)
            saved = completed
        }
        return saved
    }

    suspend fun setIntermittentPlan(plan: IntermittentFastingPlan) {
        context.fastingDataStore.edit { prefs ->
            prefs[Keys.INTERMITTENT_PLAN_JSON_V1] = encodeIntermittentPlan(plan.normalized())
        }
    }

    suspend fun logCompletedIntermittentWindow(
        plan: IntermittentFastingPlan,
        status: IntermittentFastingStatus,
        nowEpochSeconds: Long = fastingNowEpochSeconds(),
    ): FastingSession? {
        val fastStart = status.completedFastStartEpochSeconds ?: return null
        val fastEnd = status.completedFastEndEpochSeconds ?: return null
        val normalized = plan.normalized()
        val session = FastingSession(
            id = "if-$fastStart-$fastEnd",
            targetDays = 0,
            startedAtEpochSeconds = fastStart,
            targetEndEpochSeconds = fastEnd,
            status = FastingStatus.COMPLETED,
            endedAtEpochSeconds = fastEnd.coerceAtMost(nowEpochSeconds),
            kind = FastingSessionKind.INTERMITTENT,
            protocolLabel = normalized.protocolLabel,
            fastingHours = normalized.fastingHours,
            eatingHours = normalized.eatingHours,
        )
        var saved: FastingSession? = null
        context.fastingDataStore.edit { prefs ->
            val history = decodeHistory(prefs[Keys.HISTORY_JSON_V1])
            val alreadyLogged = history.any {
                it.kind == FastingSessionKind.INTERMITTENT &&
                    it.startedAtEpochSeconds == fastStart &&
                    it.targetEndEpochSeconds == fastEnd
            }
            if (alreadyLogged) return@edit
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(listOf(session) + history)
            saved = session
        }
        return saved
    }

    suspend fun cancelActive(
        notes: String = "",
        endedAtEpochSeconds: Long = fastingNowEpochSeconds(),
    ): FastingSession? {
        var saved: FastingSession? = null
        context.fastingDataStore.edit { prefs ->
            val active = decodeSession(prefs[Keys.ACTIVE_SESSION_JSON_V1]) ?: return@edit
            val history = decodeHistory(prefs[Keys.HISTORY_JSON_V1])
            val cancelled = active.copy(
                status = FastingStatus.CANCELLED,
                endedAtEpochSeconds = endedAtEpochSeconds,
                notes = notes.trim(),
            )
            prefs.remove(Keys.ACTIVE_SESSION_JSON_V1)
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(listOf(cancelled) + history)
            saved = cancelled
        }
        return saved
    }

    fun decodeSession(raw: String?): FastingSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(FastingSession.serializer(), raw) }.getOrNull()
    }

    private fun encodeSession(session: FastingSession): String =
        json.encodeToString(FastingSession.serializer(), session)

    private fun decodeIntermittentPlan(raw: String?): IntermittentFastingPlan {
        if (raw.isNullOrBlank()) return IntermittentFastingPlan()
        return runCatching {
            json.decodeFromString(IntermittentFastingPlan.serializer(), raw).normalized()
        }.getOrElse { IntermittentFastingPlan() }
    }

    private fun encodeIntermittentPlan(plan: IntermittentFastingPlan): String =
        json.encodeToString(IntermittentFastingPlan.serializer(), plan.normalized())

    private fun decodeHistory(raw: String?): List<FastingSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(FastingSession.serializer()), raw)
                .sortedByDescending { it.endedAtEpochSeconds ?: it.startedAtEpochSeconds }
                .take(HISTORY_LIMIT)
        }.getOrElse { emptyList() }
    }

    private fun encodeHistory(history: List<FastingSession>): String =
        json.encodeToString(
            ListSerializer(FastingSession.serializer()),
            history.sortedByDescending { it.endedAtEpochSeconds ?: it.startedAtEpochSeconds }.take(HISTORY_LIMIT),
        )

    companion object {
        private const val HISTORY_LIMIT = 50
    }
}
