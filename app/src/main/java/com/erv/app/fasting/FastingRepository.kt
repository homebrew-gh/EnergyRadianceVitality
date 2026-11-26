package com.erv.app.fasting

import android.content.Context
import com.erv.app.data.preserveUnreadableState
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
        val DELETED_SESSIONS_JSON_V1 = stringPreferencesKey("deleted_sessions_json_v1")
    }

    private val deletedSerializer = MapSerializer(String.serializer(), Long.serializer())

    val state: Flow<FastingLibraryState> = context.fastingDataStore.data.map { prefs ->
        FastingLibraryState(
            activeSession = decodeSession(prefs[Keys.ACTIVE_SESSION_JSON_V1]),
            history = decodeHistory(prefs[Keys.HISTORY_JSON_V1]),
            intermittentPlan = decodeIntermittentPlan(prefs[Keys.INTERMITTENT_PLAN_JSON_V1]),
            deletedSessionIds = decodeDeleted(prefs[Keys.DELETED_SESSIONS_JSON_V1]),
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
            prefs.historyForWrite() // keeps a copy if the stored history can't be read
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(state.history)
            prefs[Keys.INTERMITTENT_PLAN_JSON_V1] = encodeIntermittentPlan(state.intermittentPlan)
            prefs[Keys.DELETED_SESSIONS_JSON_V1] = json.encodeToString(deletedSerializer, state.deletedSessionIds)
        }
    }

    suspend fun clearAllData() {
        replaceAll(FastingLibraryState())
    }

    /**
     * Starts a fast of [targetHours] (24, 48, a custom number) that began at [startedAtEpochSeconds],
     * which may be in the past ("I started at 7 PM last night").
     */
    suspend fun startFast(
        targetHours: Int,
        startedAtEpochSeconds: Long = fastingNowEpochSeconds(),
    ): FastingSession {
        val hours = targetHours.coerceIn(1, FASTING_MAX_TARGET_HOURS)
        val now = fastingNowEpochSeconds()
        val start = startedAtEpochSeconds.coerceAtMost(now)
        val session = FastingSession(
            targetDays = targetDaysForHours(hours),
            targetHours = hours,
            startedAtEpochSeconds = start,
            targetEndEpochSeconds = start + hours * 3600L,
            lastModifiedEpochSeconds = now,
        )
        context.fastingDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SESSION_JSON_V1] = encodeSession(session)
        }
        return session
    }

    /** Changes the running fast's start time and target. Returns the updated fast. */
    suspend fun editActive(startedAtEpochSeconds: Long, targetHours: Int): FastingSession? {
        var saved: FastingSession? = null
        context.fastingDataStore.edit { prefs ->
            val active = decodeSession(prefs[Keys.ACTIVE_SESSION_JSON_V1]) ?: return@edit
            val now = fastingNowEpochSeconds()
            val hours = targetHours.coerceIn(1, FASTING_MAX_TARGET_HOURS)
            val start = startedAtEpochSeconds.coerceAtMost(now)
            val updated = active.copy(
                startedAtEpochSeconds = start,
                targetHours = hours,
                targetDays = targetDaysForHours(hours),
                targetEndEpochSeconds = start + hours * 3600L,
                lastModifiedEpochSeconds = now,
            )
            prefs[Keys.ACTIVE_SESSION_JSON_V1] = encodeSession(updated)
            saved = updated
        }
        return saved
    }

    /** Adds or replaces a finished fast in history (a past fast, or an edit). */
    suspend fun saveHistorySession(session: FastingSession) {
        context.fastingDataStore.edit { prefs ->
            val history = prefs.historyForWrite().filterNot { it.id == session.id }
            val stamped = session.copy(lastModifiedEpochSeconds = fastingNowEpochSeconds())
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(listOf(stamped) + history)
            // Saving again undoes an earlier delete of the same id.
            val deleted = decodeDeleted(prefs[Keys.DELETED_SESSIONS_JSON_V1])
            if (session.id in deleted) {
                prefs[Keys.DELETED_SESSIONS_JSON_V1] = json.encodeToString(deletedSerializer, deleted - session.id)
            }
        }
    }

    /** Removes a fast from history and remembers the delete (for sync and auto-logging). */
    suspend fun deleteHistorySession(sessionId: String): FastingSession? {
        var removed: FastingSession? = null
        context.fastingDataStore.edit { prefs ->
            val history = prefs.historyForWrite()
            removed = history.firstOrNull { it.id == sessionId }
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(history.filterNot { it.id == sessionId })
            val deleted = decodeDeleted(prefs[Keys.DELETED_SESSIONS_JSON_V1])
            prefs[Keys.DELETED_SESSIONS_JSON_V1] =
                json.encodeToString(deletedSerializer, deleted + (sessionId to fastingNowEpochSeconds()))
        }
        return removed
    }

    /**
     * Logs every finished window of the intermittent plan since auto-log was turned on (at most the
     * last two weeks), skipping ones already logged or deleted. Returns what was added.
     */
    suspend fun autoLogIntermittentWindows(nowEpochSeconds: Long = fastingNowEpochSeconds()): List<FastingSession> {
        val added = mutableListOf<FastingSession>()
        context.fastingDataStore.edit { prefs ->
            val plan = decodeIntermittentPlan(prefs[Keys.INTERMITTENT_PLAN_JSON_V1])
            if (!plan.autoLog) return@edit
            val since = maxOf(plan.autoLogSinceEpochSeconds, nowEpochSeconds - AUTO_LOG_LOOKBACK_SECONDS)
            val history = prefs.historyForWrite()
            val deleted = decodeDeleted(prefs[Keys.DELETED_SESSIONS_JSON_V1])
            val known = history.mapTo(mutableSetOf()) { it.id }
            val knownWindows = history
                .filter { it.kind == FastingSessionKind.INTERMITTENT }
                .mapTo(mutableSetOf()) { it.startedAtEpochSeconds to it.targetEndEpochSeconds }
            for ((start, end) in plan.finishedFastWindows(since, nowEpochSeconds)) {
                val id = intermittentWindowId(start, end)
                if (id in known || id in deleted || (start to end) in knownWindows) continue
                added += intermittentSession(plan, start, end, nowEpochSeconds)
            }
            if (added.isNotEmpty()) prefs[Keys.HISTORY_JSON_V1] = encodeHistory(added + history)
        }
        return added
    }

    private fun intermittentSession(
        plan: IntermittentFastingPlan,
        start: Long,
        end: Long,
        nowEpochSeconds: Long,
    ): FastingSession {
        val normalized = plan.normalized()
        return FastingSession(
            id = intermittentWindowId(start, end),
            targetDays = 0,
            startedAtEpochSeconds = start,
            targetEndEpochSeconds = end,
            status = FastingStatus.COMPLETED,
            endedAtEpochSeconds = end.coerceAtMost(nowEpochSeconds),
            kind = FastingSessionKind.INTERMITTENT,
            protocolLabel = normalized.protocolLabel,
            fastingHours = normalized.fastingHours,
            eatingHours = normalized.eatingHours,
            targetHours = ((end - start + 1800) / 3600).toInt(),
            lastModifiedEpochSeconds = nowEpochSeconds,
        )
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
            val history = prefs.historyForWrite()
            val completed = active.copy(
                status = FastingStatus.COMPLETED,
                endedAtEpochSeconds = endedAtEpochSeconds,
                mood = mood,
                weight = weight.trim(),
                notes = notes.trim(),
                lastModifiedEpochSeconds = fastingNowEpochSeconds(),
            )
            prefs.remove(Keys.ACTIVE_SESSION_JSON_V1)
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(listOf(completed) + history)
            saved = completed
        }
        return saved
    }

    suspend fun setIntermittentPlan(plan: IntermittentFastingPlan) {
        context.fastingDataStore.edit { prefs ->
            val previous = decodeIntermittentPlan(prefs[Keys.INTERMITTENT_PLAN_JSON_V1])
            val now = fastingNowEpochSeconds()
            val since = when {
                plan.autoLog && !previous.autoLog -> now
                plan.autoLog -> previous.autoLogSinceEpochSeconds.takeIf { it > 0 } ?: now
                else -> 0L
            }
            prefs[Keys.INTERMITTENT_PLAN_JSON_V1] = encodeIntermittentPlan(
                plan.normalized().copy(autoLogSinceEpochSeconds = since, lastModifiedEpochSeconds = now)
            )
        }
    }

    suspend fun logCompletedIntermittentWindow(
        plan: IntermittentFastingPlan,
        status: IntermittentFastingStatus,
        nowEpochSeconds: Long = fastingNowEpochSeconds(),
    ): FastingSession? {
        val fastStart = status.completedFastStartEpochSeconds ?: return null
        val fastEnd = status.completedFastEndEpochSeconds ?: return null
        val session = intermittentSession(plan, fastStart, fastEnd, nowEpochSeconds)
        var saved: FastingSession? = null
        context.fastingDataStore.edit { prefs ->
            val history = prefs.historyForWrite()
            val alreadyLogged = history.any {
                it.kind == FastingSessionKind.INTERMITTENT &&
                    it.startedAtEpochSeconds == fastStart &&
                    it.targetEndEpochSeconds == fastEnd
            }
            if (alreadyLogged) return@edit
            prefs[Keys.HISTORY_JSON_V1] = encodeHistory(listOf(session) + history)
            val deleted = decodeDeleted(prefs[Keys.DELETED_SESSIONS_JSON_V1])
            if (session.id in deleted) {
                prefs[Keys.DELETED_SESSIONS_JSON_V1] = json.encodeToString(deletedSerializer, deleted - session.id)
            }
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
            val history = prefs.historyForWrite()
            val cancelled = active.copy(
                status = FastingStatus.CANCELLED,
                endedAtEpochSeconds = endedAtEpochSeconds,
                notes = notes.trim(),
                lastModifiedEpochSeconds = fastingNowEpochSeconds(),
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

    private fun decodeDeleted(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(deletedSerializer, raw) }.getOrElse { emptyMap() }
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

    private fun decodeHistory(raw: String?, onUnreadable: () -> Unit = {}): List<FastingSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(FastingSession.serializer()), raw)
                .sortedByDescending { it.endedAtEpochSeconds ?: it.startedAtEpochSeconds }
                .take(HISTORY_LIMIT)
        }.getOrElse {
            onUnreadable()
            emptyList()
        }
    }

    /** History to build a write on; keeps a copy of the stored text first if it can't be decoded. */
    private fun MutablePreferences.historyForWrite(): List<FastingSession> {
        val raw = this[Keys.HISTORY_JSON_V1]
        return decodeHistory(raw) { preserveUnreadableState(Keys.HISTORY_JSON_V1, raw) }
    }

    private fun encodeHistory(history: List<FastingSession>): String =
        json.encodeToString(
            ListSerializer(FastingSession.serializer()),
            history.sortedByDescending { it.endedAtEpochSeconds ?: it.startedAtEpochSeconds }.take(HISTORY_LIMIT),
        )

    companion object {
        /** Daily intermittent logging adds one a day; keep several years. */
        private const val HISTORY_LIMIT = 2000
        private const val AUTO_LOG_LOOKBACK_SECONDS = 14L * 24 * 60 * 60
    }
}
