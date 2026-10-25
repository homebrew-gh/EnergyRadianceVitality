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
        val PAUSE_JSON = stringPreferencesKey("outbox_pause_v1")
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
     * Last relay-wide upload pause (rate limit, refused, offline) for Settings. Does not re-emit when
     * it expires; compare [OutboxPause.untilEpochMs] with the clock.
     */
    fun pauseFlow(): Flow<OutboxPause?> =
        appContext.relayPublishOutboxDataStore.data.map { prefs -> decodePause(prefs[Keys.PAUSE_JSON]) }

    /** Observes pending item metadata for debugging stuck relay uploads in Settings. */
    fun pendingItemsFlow(): Flow<List<PendingItemStatus>> =
        appContext.relayPublishOutboxDataStore.data.map { prefs ->
            decodeQueue(prefs[Keys.QUEUE_JSON]).map { item ->
                PendingItemStatus(
                    dTag = item.dTag,
                    attempts = item.attempts,
                    nextAttemptAtEpochMs = item.nextAttemptAtEpochMs,
                )
            }
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
        require(plaintextPayload.isNotBlank()) {
            "refusing to enqueue empty kind-30078 payload for $dTag"
        }
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
     * Drops [removeDTags] plus any incoming tags, then appends [entries] in order.
     * Useful when one logical payload falls back from a day tag to split session tags.
     */
    suspend fun replaceMany(removeDTags: Collection<String>, entries: List<Pair<String, String>>) {
        queueMutex.withLock {
            val remove = removeDTags.toSet() + entries.map { it.first }
            var cur = loadQueueUnlocked().filterNot { it.dTag in remove }
            val base = System.currentTimeMillis()
            for ((i, pair) in entries.withIndex()) {
                val (dTag, payload) = pair
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

    suspend fun clear() {
        queueMutex.withLock {
            saveQueueUnlocked(emptyList())
        }
    }

    /** Drops queued rows for [dTags] (e.g. the relay already holds the same payload). */
    suspend fun removeDTags(dTags: Collection<String>) {
        if (dTags.isEmpty()) return
        val remove = dTags.toSet()
        queueMutex.withLock {
            saveQueueUnlocked(loadQueueUnlocked().filterNot { it.dTag in remove })
        }
    }

    /**
     * Sends due items until the queue is empty, items are waiting on backoff, or [maxPublishesThisCall] is hit.
     * Call after enqueue and when the app has a live [RelayPool].
     *
     * When every queued item is still inside its backoff window this returns immediately with
     * [KickDrainResult.retryAfterMs] set. Sleeping here would hold [drainMutex] and block a new
     * day-log publish (supplement, light, workout) behind an unrelated failed upload.
     *
     * A rate-limit, refusal, or offline result pauses the whole queue for these relays instead of
     * backing off one item: every other item would fail the same way and burn its retries.
     * Due items go out by [outboxSendComparator] (libraries before day logs).
     */
    suspend fun kickDrain(
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        maxPublishesThisCall: Int = 20_000,
        interPublishDelayMs: Long = 150L,
    ): KickDrainResult = drainMutex.withLock {
        val relayKey = relayPauseKey(dataRelayUrls)
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
                val pause = loadPauseUnlocked()
                val offlinePauseOver = pause?.kind == RelayRejectionKind.OFFLINE &&
                    relayPool.connectedRelayUrls().any { it in dataRelayUrls }
                if (
                    pause != null && pause.relayKey == relayKey && pause.untilEpochMs > now &&
                    !offlinePauseOver
                ) {
                    return@queueStep DrainStep.Wait(pause.untilEpochMs - now)
                }
                val next = pickNextDueOutboxItem(queue, now)
                if (next == null) {
                    val waitMs =
                        (queue.minOf { it.nextAttemptAtEpochMs } - now).coerceIn(250L, 120_000L)
                    DrainStep.Wait(waitMs)
                } else {
                    DrainStep.Publish(next)
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
                is DrainStep.Wait -> {
                    val remaining = queueMutex.withLock { loadQueueUnlocked().size }
                    return@withLock KickDrainResult(
                        remaining = remaining,
                        publishedOk = publishedOk,
                        publishedFail = publishedFail,
                        stoppedBecauseQueueEmpty = false,
                        retryAfterMs = step.ms,
                    )
                }
                is DrainStep.Publish -> {
                    if (publishesThisCall > 0) delay(interPublishDelayMs)
                    val item = step.item
                    val publish = EncryptedKind30078Publish.publishDetailed(
                        relayPool,
                        signer,
                        item.dTag,
                        item.plaintextPayload,
                        dataRelayUrls,
                    )
                    val ok = publish.ok
                    val kind = if (ok) null else publish.failureKind ?: classifyRelayRejection(publish.message)
                    val relayWide = kind != null && kind.pausesWholeQueue()
                    var pauseMs: Long? = null
                    queueMutex.withLock {
                        val queue = loadQueueUnlocked()
                        val currentIdx = queue.indexOfFirst { it.id == item.id }
                        if (currentIdx < 0) {
                            // Superseded by a newer enqueue for the same d tag while we published.
                        } else if (!relayWide) {
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
                        val prior = loadPauseUnlocked()?.takeIf { it.relayKey == relayKey }
                        val nextPause = nextOutboxPause(
                            prior = prior,
                            kind = kind,
                            message = publish.message,
                            relayKey = relayKey,
                            nowEpochMs = System.currentTimeMillis(),
                        )
                        if (nextPause != prior) savePauseUnlocked(nextPause)
                        if (relayWide) pauseMs = nextPause?.let { it.untilEpochMs - System.currentTimeMillis() }
                    }
                    if (ok) {
                        RelayOutboxStatusStore.get(appContext).recordSuccess(item.dTag)
                        RelayPayloadDigestStore.get(appContext)
                            .recordPublishedPlaintext(item.dTag, item.plaintextPayload)
                    } else if (kind != RelayRejectionKind.RATE_LIMITED && kind != RelayRejectionKind.OFFLINE) {
                        RelayOutboxStatusStore.get(appContext)
                            .recordFailure(item.dTag, publish.message)
                    }
                    publishesThisCall++
                    if (ok) publishedOk++ else publishedFail++
                    if (relayWide) {
                        val remaining = queueMutex.withLock { loadQueueUnlocked().size }
                        return@withLock KickDrainResult(
                            remaining = remaining,
                            publishedOk = publishedOk,
                            publishedFail = publishedFail,
                            stoppedBecauseQueueEmpty = false,
                            retryAfterMs = (pauseMs ?: RELAY_OFFLINE_PAUSE_MS).coerceAtLeast(1_000L),
                        )
                    }
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

    private suspend fun loadPauseUnlocked(): OutboxPause? =
        appContext.relayPublishOutboxDataStore.data.map { prefs ->
            decodePause(prefs[Keys.PAUSE_JSON])
        }.first()

    private suspend fun savePauseUnlocked(pause: OutboxPause?) {
        appContext.relayPublishOutboxDataStore.edit { prefs ->
            if (pause == null) {
                prefs.remove(Keys.PAUSE_JSON)
            } else {
                prefs[Keys.PAUSE_JSON] = json.encodeToString(OutboxPause.serializer(), pause)
            }
        }
    }

    private fun decodePause(raw: String?): OutboxPause? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(OutboxPause.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
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
        /** Set when the drain stopped because the next item is not due yet. */
        val retryAfterMs: Long? = null,
    )

    data class PendingItemStatus(
        val dTag: String,
        val attempts: Int,
        val nextAttemptAtEpochMs: Long,
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

/**
 * Relay-wide upload pause. [untilEpochMs] of 0 means the last pause ended with a successful send;
 * [strikes] is kept so a relay that refills slowly (Haven's outbox: 10 events/hour) settles on a
 * long cool-down instead of re-climbing from one minute every cycle.
 */
@Serializable
data class OutboxPause(
    val untilEpochMs: Long = 0L,
    val strikes: Int = 0,
    val kindName: String = "",
    val message: String = "",
    val relayKey: String = "",
) {
    val kind: RelayRejectionKind?
        get() = runCatching { RelayRejectionKind.valueOf(kindName) }.getOrNull()
}

internal fun RelayRejectionKind.pausesWholeQueue(): Boolean =
    this == RelayRejectionKind.RATE_LIMITED ||
        this == RelayRejectionKind.AUTH ||
        this == RelayRejectionKind.RESTRICTED ||
        this == RelayRejectionKind.OFFLINE

internal fun relayPauseKey(dataRelayUrls: List<String>): String =
    dataRelayUrls.map { it.trim().trimEnd('/') }.distinct().sorted().joinToString("\n")

/**
 * Pause after one publish result. [kind] null means the relay accepted the event: an active pause
 * ends and eases off one strike. Per-event failures leave the pause alone.
 */
internal fun nextOutboxPause(
    prior: OutboxPause?,
    kind: RelayRejectionKind?,
    message: String,
    relayKey: String,
    nowEpochMs: Long,
): OutboxPause? {
    val strikes = prior?.strikes ?: 0
    return when (kind) {
        null -> when {
            prior == null -> null
            prior.untilEpochMs == 0L -> prior
            strikes <= 1 -> null
            else -> prior.copy(untilEpochMs = 0L, strikes = strikes - 1)
        }
        RelayRejectionKind.RATE_LIMITED -> OutboxPause(
            untilEpochMs = nowEpochMs + rateLimitCooldownMs(strikes + 1),
            strikes = strikes + 1,
            kindName = kind.name,
            message = message,
            relayKey = relayKey,
        )
        RelayRejectionKind.AUTH, RelayRejectionKind.RESTRICTED -> OutboxPause(
            untilEpochMs = nowEpochMs + RELAY_REFUSED_PAUSE_MS,
            strikes = strikes,
            kindName = kind.name,
            message = message,
            relayKey = relayKey,
        )
        RelayRejectionKind.OFFLINE -> OutboxPause(
            untilEpochMs = nowEpochMs + RELAY_OFFLINE_PAUSE_MS,
            strikes = strikes,
            kindName = kind.name,
            message = message,
            relayKey = relayKey,
        )
        RelayRejectionKind.INVALID, RelayRejectionKind.TRANSIENT -> prior
    }
}

internal fun pickNextDueOutboxItem(
    queue: List<RelayPublishOutbox.OutboxItem>,
    nowEpochMs: Long,
    todayEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
): RelayPublishOutbox.OutboxItem? =
    queue.filter { it.nextAttemptAtEpochMs <= nowEpochMs }
        .minWithOrNull(
            outboxSendComparator(todayEpochDay, { it.dTag }, { it.createdAtEpochMs }),
        )
