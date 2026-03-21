package com.erv.app.nostr

import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * [NIP-B7](https://nips.nostr.com/B7): Blossom media — kind **10063** lists preferred Blossom HTTPS origins.
 * Other clients (Primal, Amethyst, etc.) publish `["server", "https://…"]` tags; we can reuse that list.
 */
object NipB7 {

    fun parseBlossomServersFromEvent(event: NostrEvent): List<String> =
        event.tags
            .filter { it.size >= 2 && it[0] == "server" }
            .mapNotNull { tag ->
                val u = tag[1].trim().trimEnd('/')
                if (u.startsWith("https://", ignoreCase = true)) u else null
            }
            .distinct()

    /**
     * Fetches the latest kind **10063** for [pubkeyHex] from the connected [relayPool].
     * Use after [RelayPool.awaitAtLeastOneConnected] so at least one socket is open.
     */
    suspend fun fetchBlossomServersFromNetwork(
        relayPool: RelayPool,
        pubkeyHex: String,
        timeoutMs: Long = 8000
    ): List<String> = coroutineScope {
        val subId = "erv-nipb7-${System.currentTimeMillis()}"
        val events = mutableListOf<NostrEvent>()
        val job = launch {
            relayPool.events.collect { (id, ev) ->
                if (id == subId && ev.kind == 10063) events.add(ev)
            }
        }
        delay(50)
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(10063),
                authors = listOf(pubkeyHex),
                limit = 20
            )
        )
        delay(timeoutMs)
        job.cancel()
        relayPool.unsubscribe(subId)
        val latest = events.maxByOrNull { it.createdAt }
        latest?.let { parseBlossomServersFromEvent(it) } ?: emptyList()
    }
}
