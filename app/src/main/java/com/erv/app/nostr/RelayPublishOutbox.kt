package com.erv.app.nostr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.min

private val Context.relayPublishOutboxDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "erv_relay_publish_outbox"
)

/**
 * Durable FIFO queue of kind-30078 payloads (plaintext JSON before encrypt). Used after **weight/cardio import**
 * so relay failures do not lose uploads; [kickDrain] retries with exponential backoff.
 */
class RelayPublishOutbox private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: RelayPublishOutbox? = null

        fun get(context: Context): RelayPublishOutbox {
            return instance ?: synchronized(this) {
                instance ?: RelayPublishOutbox(context.applicationContext).also { instance = it }
            }
        }

        internal fun backoffDelayMsAfterFailure(attemptsAfterIncrement: Int): Long {
            val a = attemptsAfterIncrement.coerceAtLeast(1)
            val exp = min(18, a - 1)
            val ms = 2000L shl exp
            return ms.coerceAtMost(600_000L)
        }
    }

    private val mutex = Mutex()

    private object Keys {
        val QUEUE_JSON = stringPreferencesKey("outbox_queue_v1")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun pendingCount(): Int = loadQueue().size

    /** Appends items in order (exercises master, then day logs, etc.). */
    suspend fun enqueueAll(entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        appContext.relayPublishOutboxDataStore.edit { prefs ->
            val cur = decodeQueue(prefs[Keys.QUEUE_JSON])
            val now = System.currentTimeMillis()
            val appended = entries.map { (dTag, payload) ->
                OutboxItem(
                    id = UUID.randomUUID().toString(),
                    dTag = dTag,
                    plaintextPayload = payload,
                    createdAtEpochMs = now,
                    attempts = 0,
                    nextAttemptAtEpochMs = 0L,
                )
            }
            prefs[Keys.QUEUE_JSON] = json.encodeToString(OutboxQueue.serializer(), OutboxQueue(cur + appended))
        }
    }

    /**
     * Sends due items until the queue is empty, items are waiting on backoff, or [maxPublishesThisCall] is hit.
     * Call after enqueue and when the app has a live [RelayPool].
     */
    suspend fun kickDrain(
        relayPool: RelayPool,
        signer: EventSigner,
        maxPublishesThisCall: Int = 20_000,
        interPublishDelayMs: Long = 150L,
    ): KickDrainResult {
        return mutex.withLock {
            doKickDrain(relayPool, signer, maxPublishesThisCall, interPublishDelayMs)
        }
    }

    private suspend fun doKickDrain(
        relayPool: RelayPool,
        signer: EventSigner,
        maxPublishesThisCall: Int,
        interPublishDelayMs: Long,
    ): KickDrainResult {
        var publishedOk = 0
        var publishedFail = 0
        var publishesThisCall = 0
        while (publishesThisCall < maxPublishesThisCall) {
            var queue = loadQueue()
            if (queue.isEmpty()) {
                return KickDrainResult(
                    remaining = 0,
                    publishedOk = publishedOk,
                    publishedFail = publishedFail,
                    stoppedBecauseQueueEmpty = true,
                )
            }
            var now = System.currentTimeMillis()
            var idx = queue.indexOfFirst { it.nextAttemptAtEpochMs <= now }
            while (idx < 0) {
                val wait = (queue.minOf { it.nextAttemptAtEpochMs } - now).coerceIn(250L, 120_000L)
                delay(wait)
                queue = loadQueue()
                if (queue.isEmpty()) {
                    return KickDrainResult(
                        remaining = 0,
                        publishedOk = publishedOk,
                        publishedFail = publishedFail,
                        stoppedBecauseQueueEmpty = true,
                    )
                }
                now = System.currentTimeMillis()
                idx = queue.indexOfFirst { it.nextAttemptAtEpochMs <= now }
            }
            if (publishesThisCall > 0) delay(interPublishDelayMs)
            val item = queue[idx]
            val ok = EncryptedKind30078Publish.publish(relayPool, signer, item.dTag, item.plaintextPayload)
            val rest = queue.toMutableList()
            rest.removeAt(idx)
            if (!ok) {
                val nextAttempts = item.attempts + 1
                rest += item.copy(
                    attempts = nextAttempts,
                    nextAttemptAtEpochMs = now + backoffDelayMsAfterFailure(nextAttempts),
                )
            }
            rest.sortBy { it.createdAtEpochMs }
            saveQueue(rest)
            publishesThisCall++
            if (ok) publishedOk++ else publishedFail++
        }
        return KickDrainResult(
            remaining = loadQueue().size,
            publishedOk = publishedOk,
            publishedFail = publishedFail,
            stoppedBecauseQueueEmpty = false,
        )
    }

    private suspend fun loadQueue(): List<OutboxItem> =
        appContext.relayPublishOutboxDataStore.data.map { prefs ->
            decodeQueue(prefs[Keys.QUEUE_JSON])
        }.first()

    private suspend fun saveQueue(items: List<OutboxItem>) {
        appContext.relayPublishOutboxDataStore.edit { prefs ->
            prefs[Keys.QUEUE_JSON] = json.encodeToString(OutboxQueue.serializer(), OutboxQueue(items))
        }
    }

    private fun decodeQueue(raw: String?): List<OutboxItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(OutboxQueue.serializer(), raw).items
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    data class KickDrainResult(
        val remaining: Int,
        val publishedOk: Int,
        val publishedFail: Int,
        val stoppedBecauseQueueEmpty: Boolean,
    )

    @Serializable
    private data class OutboxQueue(val items: List<OutboxItem> = emptyList())

    @Serializable
    data class OutboxItem(
        val id: String,
        val dTag: String,
        val plaintextPayload: String,
        val createdAtEpochMs: Long = System.currentTimeMillis(),
        val attempts: Int = 0,
        val nextAttemptAtEpochMs: Long = 0L,
    )
}
