package com.erv.app.nostr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/** Covers clock drift between this device, the other device, and the relay. */
internal const val KIND_30078_LIVE_SINCE_SKEW_SECONDS = 120L

private const val LIVE_BATCH_WINDOW_MS = 750L
private const val LIVE_SEEN_EVENT_ID_CAP = 2_000

/**
 * Keeps one kind-30078 `REQ` open for this author so the relay pushes new app data as it is
 * published (web companion or another device). Suspends until cancelled.
 *
 * [NostrClient] re-sends the `REQ` after every reconnect with the original `since`, so events
 * published while the socket was down are delivered as stored events when it comes back. Bursts
 * (a web publish of a workout and its shards) are batched for [LIVE_BATCH_WINDOW_MS] and handed to
 * [onBatch] as the latest decryptable event per `d` tag.
 */
suspend fun runLiveKind30078Subscription(
    appContext: Context,
    relayPool: RelayPool,
    pubkeyHex: String,
    signer: EventSigner,
    onBatch: suspend (Map<String, NostrEvent>) -> Unit,
) {
    coroutineScope {
        val subscriptionId = "erv-kind30078-live-${System.currentTimeMillis()}"
        val incoming = Channel<NostrEvent>(Channel.UNLIMITED)
        val registered = CompletableDeferred<Unit>()
        val collector = launch {
            relayPool.inbound
                .onSubscription { registered.complete(Unit) }
                .collect { frame ->
                    if (frame is PoolInbound.Event &&
                        frame.subscriptionId == subscriptionId &&
                        frame.event.kind == 30078 &&
                        frame.event.pubkey == pubkeyHex
                    ) {
                        incoming.trySend(frame.event)
                    }
                }
        }
        registered.await()
        relayPool.subscribe(
            subscriptionId,
            NostrFilter(
                kinds = listOf(30078),
                authors = listOf(pubkeyHex),
                since = liveSubscriptionSinceEpochSeconds(),
            ),
        )
        val seenEventIds = LinkedHashSet<String>()
        try {
            while (true) {
                val batch = mutableListOf(incoming.receive())
                delay(LIVE_BATCH_WINDOW_MS)
                while (true) {
                    batch += incoming.tryReceive().getOrNull() ?: break
                }
                val fresh = batch.filter { seenEventIds.add(it.id) }
                while (seenEventIds.size > LIVE_SEEN_EVENT_ID_CAP) {
                    seenEventIds.remove(seenEventIds.first())
                }
                if (fresh.isEmpty()) continue
                val latest = dropPayloadsAlreadyOnDevice(
                    appContext,
                    pickLatestDecryptableByDTag(fresh, signer),
                    signer,
                )
                Log.i(
                    ERV_SYNC_LOG_TAG,
                    "live: events=${fresh.size} newTags=${latest.size} tags=${latest.keys}",
                )
                if (latest.isNotEmpty()) onBatch(latest)
            }
        } finally {
            collector.cancel()
            relayPool.unsubscribe(subscriptionId)
        }
    }
}

internal fun liveSubscriptionSinceEpochSeconds(
    nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
): Long = (nowEpochSeconds - KIND_30078_LIVE_SINCE_SKEW_SECONDS).coerceAtLeast(0)

/**
 * The relay echoes this device's own publishes back on the live subscription. Their plaintext
 * matches the digest recorded on publish, so they carry nothing new to merge.
 */
private suspend fun dropPayloadsAlreadyOnDevice(
    appContext: Context,
    latestByTag: Map<String, NostrEvent>,
    signer: EventSigner,
): Map<String, NostrEvent> {
    if (latestByTag.isEmpty()) return latestByTag
    val digests = RelayPayloadDigestStore.get(appContext)
    return latestByTag.filter { (dTag, event) ->
        val plain = event.decryptKind30078PayloadOrNull(signer) ?: return@filter false
        digests.getDigestHex(dTag) != sha256HexUtf8(plain)
    }
}

/**
 * Workout library shards and catalogs need the targeted full pull (shard fetch, catalog
 * bootstrap rules). Everything else merges directly from the live event.
 */
internal fun liveTagNeedsFullPull(dTag: String): Boolean =
    dTag.startsWith("erv/workouts/") || dTag.startsWith("erv/catalog/")
