package com.erv.app.nostr

import com.erv.app.data.UserPreferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * NIP-65: Relay List Metadata (kind 10002).
 * Fetches the user's advertised relay list from the network so we can offer
 * to add them as social relays (e.g. relays they use in Damus/Primal).
 */
object Nip65 {

    /** Well-known relays used to bootstrap NIP-65 lookups. */
    val bootstrapRelays = KeyManager.DEFAULT_RELAYS

    /**
     * Parse relay URLs from a kind 10002 event. NIP-65 uses "r" tags:
     * ["r", "wss://...", "write"] or ["r", "wss://...", "read"] or ["r", "wss://..."].
     */
    fun parseRelayListFromEvent(event: NostrEvent): List<String> =
        event.tags
            .filter { it.size >= 2 && it[0] == "r" }
            .mapNotNull { tag ->
                val url = tag[1].trim()
                if (url.startsWith("wss://") || url.startsWith("ws://")) url else null
            }
            .distinct()

    /**
     * Fetch the user's relay list (kind 10002) from the network.
     * [relayPool] should already be connected (e.g. to user's relays and/or [bootstrapRelays]).
     * Returns the list of relay URLs from the latest kind 10002 event, or empty if none found.
     */
    suspend fun fetchRelayListFromNetwork(
        relayPool: RelayPool,
        pubkeyHex: String,
        timeoutMs: Long = 8000
    ): List<String> = coroutineScope {
        val subId = "nip65-fetch-${System.currentTimeMillis()}"
        val events = mutableListOf<NostrEvent>()
        val job = launch {
            relayPool.events.collect { (id, ev) ->
                if (id == subId && ev.kind == 10002) events.add(ev)
            }
        }
        delay(50)
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(10002),
                authors = listOf(pubkeyHex),
                limit = 10
            )
        )
        delay(timeoutMs)
        job.cancel()
        relayPool.unsubscribe(subId)
        val latest = events.maxByOrNull { it.createdAt }
        latest?.let { parseRelayListFromEvent(it) } ?: emptyList()
    }

    /**
     * Publishes kind **10002** (NIP-65) with current [KeyManager] relay roles when
     * [UserPreferences.neverPublishNip65RelayList] is false. When that preference is true, returns **true**
     * without publishing.
     *
     * @return false only if publishing was attempted and all target relays rejected the event; true if skipped or any relay accepted.
     */
    suspend fun publishRelayListIfAllowed(
        userPreferences: UserPreferences,
        relayPool: RelayPool,
        signer: EventSigner,
        keyManager: KeyManager,
    ): Boolean {
        if (userPreferences.neverPublishNip65RelayList.first()) return true
        val urls = keyManager.allRelayUrls()
        if (urls.isEmpty()) return true
        val tags = urls.map { url ->
            val data = keyManager.isDataRelay(url)
            val social = keyManager.isSocialRelay(url)
            when {
                data && social -> listOf("r", url)
                data -> listOf("r", url, "write")
                social -> listOf("r", url, "read")
                else -> listOf("r", url)
            }
        }
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 10002,
            tags = tags,
            content = "",
        )
        val signed = signer.sign(unsigned)
        val dest = keyManager.relayUrlsForKind0Publish()
        if (dest.isEmpty()) return false
        return relayPool.publishToRelayUrls(signed, dest)
    }
}
