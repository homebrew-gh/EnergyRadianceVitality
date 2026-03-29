package com.erv.app.nostr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.relayOutboxStatusDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "erv_relay_outbox_status_v1"
)

class RelayOutboxStatusStore private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: RelayOutboxStatusStore? = null

        fun get(context: Context): RelayOutboxStatusStore {
            return instance ?: synchronized(this) {
                instance ?: RelayOutboxStatusStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private object Keys {
        val STATUS_JSON = stringPreferencesKey("relay_outbox_status_json_v1")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun statusFlow(): Flow<RelayOutboxStatus> =
        appContext.relayOutboxStatusDataStore.data.map { prefs ->
            decodeStatus(prefs[Keys.STATUS_JSON])
        }

    suspend fun recordSuccess(dTag: String) {
        updateStatus { current ->
            current.copy(
                lastSuccessAtEpochMs = System.currentTimeMillis(),
                consecutiveFailureCount = 0,
                lastFailureMessage = "",
                lastFailureDTag = "",
                failuresByDTag = current.failuresByDTag - dTag,
            )
        }
    }

    suspend fun recordFailure(dTag: String, message: String) {
        updateStatus { current ->
            val prior = current.failuresByDTag[dTag]
            current.copy(
                lastFailureAtEpochMs = System.currentTimeMillis(),
                lastFailureMessage = message,
                lastFailureDTag = dTag,
                consecutiveFailureCount = current.consecutiveFailureCount + 1,
                failuresByDTag = current.failuresByDTag + (
                    dTag to RelayOutboxItemFailure(
                        message = message,
                        lastFailureAtEpochMs = System.currentTimeMillis(),
                        failureCount = (prior?.failureCount ?: 0) + 1,
                    )
                ),
            )
        }
    }

    suspend fun clear() {
        appContext.relayOutboxStatusDataStore.edit { prefs ->
            prefs.remove(Keys.STATUS_JSON)
        }
    }

    private suspend fun updateStatus(transform: (RelayOutboxStatus) -> RelayOutboxStatus) {
        appContext.relayOutboxStatusDataStore.edit { prefs ->
            val current = decodeStatus(prefs[Keys.STATUS_JSON])
            prefs[Keys.STATUS_JSON] = json.encodeToString(
                RelayOutboxStatus.serializer(),
                transform(current)
            )
        }
    }

    private fun decodeStatus(raw: String?): RelayOutboxStatus {
        if (raw.isNullOrBlank()) return RelayOutboxStatus()
        return try {
            json.decodeFromString(RelayOutboxStatus.serializer(), raw)
        } catch (_: SerializationException) {
            RelayOutboxStatus()
        } catch (_: IllegalArgumentException) {
            RelayOutboxStatus()
        }
    }
}

@Serializable
data class RelayOutboxStatus(
    val lastFailureAtEpochMs: Long = 0L,
    val lastFailureMessage: String = "",
    val lastFailureDTag: String = "",
    val lastSuccessAtEpochMs: Long = 0L,
    val consecutiveFailureCount: Int = 0,
    val failuresByDTag: Map<String, RelayOutboxItemFailure> = emptyMap(),
)

@Serializable
data class RelayOutboxItemFailure(
    val message: String = "",
    val lastFailureAtEpochMs: Long = 0L,
    val failureCount: Int = 0,
)
