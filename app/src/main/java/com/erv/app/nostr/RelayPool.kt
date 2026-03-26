package com.erv.app.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages connections to multiple Nostr relays simultaneously.
 * Each relay gets its own [NostrClient]; events, notices, and publishes
 * are fanned out / merged across all of them.
 */
class RelayPool(
    private val signer: EventSigner,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val clients = mutableMapOf<String, NostrClient>()

    private val _relayStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val relayStates: StateFlow<Map<String, ConnectionState>> = _relayStates.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, NostrEvent>>(extraBufferCapacity = 64)
    val events: SharedFlow<Pair<String, NostrEvent>> = _events.asSharedFlow()

    private val _notices = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val notices: SharedFlow<Pair<String, String>> = _notices.asSharedFlow()

    private val collectorJobs = mutableMapOf<String, Job>()

    /**
     * Diff current connections against [urls]. Connect new relays, disconnect removed ones.
     */
    fun setRelays(urls: List<String>) {
        val desired = urls.toSet()
        val current = clients.keys.toSet()

        (current - desired).forEach { removeClient(it) }
        (desired - current).forEach { addClient(it) }
    }

    private fun addClient(url: String) {
        val client = NostrClient(signer)
        clients[url] = client
        client.connect(url)

        collectorJobs[url] = scope.launch {
            launch {
                client.connectionState.collect { state ->
                    _relayStates.update { it + (url to state) }
                }
            }
            launch {
                client.events.collect { _events.emit(it) }
            }
            launch {
                client.notices.collect { _notices.emit(url to it) }
            }
        }
    }

    private fun removeClient(url: String) {
        collectorJobs.remove(url)?.cancel()
        clients.remove(url)?.disconnect()
        _relayStates.update { it - url }
    }

    suspend fun publish(event: NostrEvent): Boolean {
        return coroutineScope {
            clients.values.map { client ->
                async { client.publish(event) }
            }.awaitAll().any { it }
        }
    }

    /**
     * Publishes only to relays whose URLs appear in [urls] and are connected in this pool.
     * Used for kind **30078** so encrypted backups stay off social-only relays.
     */
    suspend fun publishToRelayUrls(event: NostrEvent, urls: Collection<String>): Boolean {
        if (urls.isEmpty()) return false
        return coroutineScope {
            urls.distinct().mapNotNull { clients[it] }.map { client ->
                async { client.publish(event) }
            }.awaitAll().any { it }
        }
    }

    fun subscribe(subscriptionId: String, vararg filters: NostrFilter) {
        clients.values.forEach { it.subscribe(subscriptionId, *filters) }
    }

    /**
     * Waits until at least one relay has an open socket (or auth completed). Avoids NIP-65 / REQ
     * running while every [NostrClient] still has `webSocket == null`.
     */
    suspend fun awaitAtLeastOneConnected(timeoutMs: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (relayStates.value.values.any {
                it is ConnectionState.Connected || it is ConnectionState.Authenticated
            }) return true
            delay(50)
        }
        return relayStates.value.values.any {
            it is ConnectionState.Connected || it is ConnectionState.Authenticated
        }
    }

    fun unsubscribe(subscriptionId: String) {
        clients.values.forEach { it.unsubscribe(subscriptionId) }
    }

    fun disconnect() {
        collectorJobs.values.forEach { it.cancel() }
        collectorJobs.clear()
        clients.values.forEach { it.disconnect() }
        clients.clear()
        _relayStates.value = emptyMap()
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }
}
