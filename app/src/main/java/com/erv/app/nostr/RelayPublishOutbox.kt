package com.erv.app.nostr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
 * Durable queue of kind-30078 payloads (plaintext JSON before encrypt). All interactive sync and import
 * enqueue here so relay failures do not drop work; [kickDrain] retries with exponential backoff.
 *
 * Queue is keyed by `d` tag: at most one pending entry per tag. New payloads for the same tag replace
 * older queued entries (preserving order among distinct tags). [enqueueAll] applies entries in order
 * so import batches keep master-before-days ordering without duplicate tags.
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

    /** Serializes queue read/write (enqueue + apply publish result). */
    private val queueMutex = Mutex()

    /** Only one [kickDrain] at a time so two callers cannot publish the same row. */
    private val drainMutex = Mutex()

    private object Keys {
        val QUEUE_JSON = stringPreferencesKey("outbox_queue_v1")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private sealed class DrainStep {
        data class Wait(val ms: Long) : DrainStep()
        data class Publish(val item: OutboxItem) : DrainStep()
    }

    suspend fun pendingCount(): Int = queueMutex.withLock { loadQueueUnlocked().size }

    /** Observes the durable queue size for UI (e.g. settings). Emits when enqueue/drain updates DataStore. */
    fun pendingCountFlow(): Flow<Int> =
        appContext.relayPublishOutboxDataStore.data.map { prefs ->
            decodeQueue(prefs[Keys.QUEUE_JSON]).size
        }

    /**
     * Enqueues only when plaintext differs from [RelayPayloadDigestStore] for [dTag], then [kickDrain].
     */
    suspend fun enqueueReplaceByDTagAndKickDrain(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        dTag: String,
        plaintextPayload: String,
    ): KickDrainResult {
        maybeEnqueueReplaceByDTag(appContext, dTag, plaintextPayload)
        return kickDrain(relayPool, signer, dataRelayUrls)
    }

    /**
     * Returns false when the stored digest already matches this plaintext (no relay upload needed).
     */
    suspend fun maybeEnqueueReplaceByDTag(
        appContext: Context,
        dTag: String,
        plaintextPayload: String,
    ): Boolean {
        val h = sha256HexUtf8(plaintextPayload)
        if (RelayPayloadDigestStore.get(appContext).getDigestHex(dTag) == h) return false
        enqueueReplaceByDTag(dTag, plaintextPayload)
        return true
    }

    /**
     * Batch enqueue like [enqueueAll], skipping rows whose digest already matches (import / bulk sync).
     */
    suspend fun enqueueAllDigestsAware(appContext: Context, entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        queueMutex.withLock {
            var cur = loadQueueUnlocked()
            val base = System.currentTimeMillis()
            var idx = 0
            val store = RelayPayloadDigestStore.get(appContext)
            for ((dTag, payload) in entries) {
                if (store.getDigestHex(dTag) == sha256HexUtf8(payload)) continue
                cur = cur.filter { it.dTag != dTag }
                cur += OutboxItem(
                    id = UUID.randomUUID().toString(),
                    dTag = dTag,
                    plaintextPayload = payload,
                    createdAtEpochMs = base + idx,
                    attempts = 0,
                    nextAttemptAtEpochMs = 0L,
                )
                idx++
            }
            saveQueueUnlocked(cur)
        }
    }

    /**
     * Replace pending rows sharing [dTag], append one fresh row (stable relative order for other tags).
     */
    suspend fun enqueueReplaceByDTag(dTag: String, plaintextPayload: String) {
        queueMutex.withLock {
            val cur = loadQueueUnlocked()
            val filtered = cur.filter { it.dTag != dTag }
            val item = OutboxItem(
                id = UUID.randomUUID().toString(),
                dTag = dTag,
                plaintextPayload = plaintextPayload,
                createdAtEpochMs = System.currentTimeMillis(),
                attempts = 0,
                nextAttemptAtEpochMs = 0L,
            )
            saveQueueUnlocked(filtered + item)
        }
    }

    /**
     * For each `(dTag, payload)` in order: drop any queued row with that tag, then append a new row.
     * Staggered timestamps keep batch ordering when values collide.
     */
    suspend fun enqueueAll(entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        queueMutex.withLock {
            var cur = loadQueueUnlocked()
            val base = System.currentTimeMillis()
            for ((i, pair) in entries.withIndex()) {
                val (dTag, payload) = pair
                cur = cur.filter { it.dTag != dTag }
                cur += OutboxItem(
                    id = UUID.randomUUID().toString(),
                    dTag = dTag,
                    plaintextPayload = payload,
                    createdAtEpochMs = base + i,
                    attempts = 0,
                    nextAttemptAtEpochMs = 0L,
                )
            }
            saveQueueUnlocked(cur)
        }
    }

    /**
     * Sends due items until the queue is empty, items are waiting on backoff, or [maxPublishesThisCall] is hit.
     * Call after enqueue and when the app has a live [RelayPool].
     */
    suspend fun kickDrain(
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        maxPublishesThisCall: Int = 20_000,
        interPublishDelayMs: Long = 150L,
    ): KickDrainResult = drainMutex.withLock {
        var publishedOk = 0
        var publishedFail = 0
        var publishesThisCall = 0
        while (publishesThisCall < maxPublishesThisCall) {
            val step = queueMutex.withLock queueStep@{
                val queue = loadQueueUnlocked()
                if (queue.isEmpty()) {
                    return@queueStep null
                }
                val now = System.currentTimeMillis()
                val idx = queue.indexOfFirst { it.nextAttemptAtEpochMs <= now }
                if (idx < 0) {
                    val waitMs =
                        (queue.minOf { it.nextAttemptAtEpochMs } - now).coerceIn(250L, 120_000L)
                    DrainStep.Wait(waitMs)
                } else {
                    DrainStep.Publish(queue[idx])
                }
            }
            if (step == null) {
                return@withLock KickDrainResult(
                    remaining = 0,
                    publishedOk = publishedOk,
                    publishedFail = publishedFail,
                    stoppedBecauseQueueEmpty = true,
                )
            }
            when (step) {
                is DrainStep.Wait -> delay(step.ms)
                is DrainStep.Publish -> {
                    if (publishesThisCall > 0) delay(interPublishDelayMs)
                    val item = step.item
                    val ok = EncryptedKind30078Publish.publish(
                        relayPool,
                        signer,
                        item.dTag,
                        item.plaintextPayload,
                        dataRelayUrls,
                    )
                    queueMutex.withLock {
                        val queue = loadQueueUnlocked()
                        val currentIdx = queue.indexOfFirst { it.id == item.id }
                        if (currentIdx < 0) {
                            // Superseded by a newer enqueue for the same d tag while we published.
                        } else {
                            val rest = queue.toMutableList()
                            rest.removeAt(currentIdx)
                            if (!ok) {
                                val now = System.currentTimeMillis()
                                val nextAttempts = item.attempts + 1
                                rest += item.copy(
                                    attempts = nextAttempts,
                                    nextAttemptAtEpochMs = now + backoffDelayMsAfterFailure(nextAttempts),
                                )
                            }
                            rest.sortBy { it.createdAtEpochMs }
                            saveQueueUnlocked(rest)
                        }
                    }
                    if (ok) {
                        RelayPayloadDigestStore.get(appContext)
                            .recordPublishedPlaintext(item.dTag, item.plaintextPayload)
                    }
                    publishesThisCall++
                    if (ok) publishedOk++ else publishedFail++
                }
            }
        }
        val remaining = queueMutex.withLock { loadQueueUnlocked().size }
        KickDrainResult(
            remaining = remaining,
            publishedOk = publishedOk,
            publishedFail = publishedFail,
            stoppedBecauseQueueEmpty = false,
        )
    }

    private suspend fun loadQueueUnlocked(): List<OutboxItem> =
        appContext.relayPublishOutboxDataStore.data.map { prefs ->
            decodeQueue(prefs[Keys.QUEUE_JSON])
        }.first()

    private suspend fun saveQueueUnlocked(items: List<OutboxItem>) {
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
