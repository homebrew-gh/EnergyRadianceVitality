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
    data class PublishReport(
        val ok: Boolean,
        val message: String,
    )

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
        return publishToRelayUrlsDetailed(event, urls).ok
    }

    suspend fun publishToRelayUrlsDetailed(event: NostrEvent, urls: Collection<String>): PublishReport {
        if (urls.isEmpty()) {
            return PublishReport(ok = false, message = "No data relays configured")
        }
        val targetUrls = urls.distinct()
        val targets = targetUrls.mapNotNull { url -> clients[url]?.let { url to it } }
        if (targets.isEmpty()) {
            return PublishReport(
                ok = false,
                message = summarizeMissingTargets(targetUrls)
            )
        }
        val results = coroutineScope {
            targets.map { (url, client) ->
                async { url to client.publishDetailed(event) }
            }.awaitAll()
        }
        if (results.any { (_, result) -> result is ClientPublishResult.Success }) {
            return PublishReport(ok = true, message = "")
        }
        return PublishReport(
            ok = false,
            message = summarizePublishFailure(targetUrls, results)
        )
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

    private fun summarizeMissingTargets(targetUrls: List<String>): String {
        val anyConnected = targetUrls.any { url ->
            relayStates.value[url].let { it is ConnectionState.Connected || it is ConnectionState.Authenticated }
        }
        return if (anyConnected) {
            "Data relay clients are not ready yet"
        } else {
            "No connected data relays"
        }
    }

    private fun summarizePublishFailure(
        targetUrls: List<String>,
        results: List<Pair<String, ClientPublishResult>>,
    ): String {
        val connectedCount = targetUrls.count { url ->
            relayStates.value[url].let { it is ConnectionState.Connected || it is ConnectionState.Authenticated }
        }
        val firstRejected = results.firstNotNullOfOrNull { (_, result) ->
            (result as? ClientPublishResult.Rejected)?.message?.takeIf { it.isNotBlank() }
        }
        return when {
            results.any { (_, result) -> result is ClientPublishResult.TimedOut } ->
                "Timed out waiting for a data relay acknowledgement"
            results.any { (_, result) -> result is ClientPublishResult.NoSocket } && connectedCount == 0 ->
                "No connected data relays"
            firstRejected?.startsWith("Relay requires authentication") == true ->
                firstRejected
            firstRejected?.startsWith("auth-required") == true ->
                "Data relay requires authentication"
            firstRejected != null ->
                "Data relay rejected publish: $firstRejected"
            results.any { (_, result) -> result is ClientPublishResult.SendFailed } ->
                "Data relay socket closed before send"
            connectedCount == 0 ->
                "No connected data relays"
            else ->
                "Data relay publish failed"
        }
    }
}
