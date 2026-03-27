package com.erv.app.nostr

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fetches the latest kind-30078 event per d-tag for one author using a single relay subscription.
 * This is intentionally shared across sections to avoid a burst of overlapping REQs at startup.
 */
suspend fun fetchLatestKind30078ByDTag(
    relayPool: RelayPool,
    pubkeyHex: String,
    timeoutMs: Long = 8000,
    limit: Int = 2500,
): Map<String, NostrEvent> = coroutineScope {
    val subId = "erv-kind30078-${System.currentTimeMillis()}"
    relayPool.subscribe(
        subId,
        NostrFilter(
            kinds = listOf(30078),
            authors = listOf(pubkeyHex),
            limit = limit,
        )
    )

    val events = mutableListOf<NostrEvent>()
    val job = launch {
        relayPool.events.collect { (id, ev) ->
            if (id == subId && ev.kind == 30078) events.add(ev)
        }
    }

    delay(timeoutMs)
    job.cancel()
    relayPool.unsubscribe(subId)

    events
        .sortedBy { it.createdAt }
        .groupBy { it.dTagOrNull() ?: "unknown" }
        .mapValues { (_, items) -> items.last() }
}

fun NostrEvent.dTagOrNull(): String? =
    tags.firstOrNull { it.size >= 2 && it[0] == "d" }?.getOrNull(1)
