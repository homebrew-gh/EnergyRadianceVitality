package com.erv.app.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Authenticated : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

sealed interface ClientPublishResult {
    data object Success : ClientPublishResult
    data object NoSocket : ClientPublishResult
    data object TimedOut : ClientPublishResult
    data class Rejected(val message: String) : ClientPublishResult
    data class SendFailed(val message: String) : ClientPublishResult
}

data class NostrFilter(
    val kinds: List<Int>? = null,
    val authors: List<String>? = null,
    val dTags: List<String>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null
) {
    fun toJson(): String = buildJsonObject {
        kinds?.let { k -> put("kinds", buildJsonArray { k.forEach { add(it) } }) }
        authors?.let { a -> put("authors", buildJsonArray { a.forEach { add(it) } }) }
        dTags?.let { d -> put("#d", buildJsonArray { d.forEach { add(it) } }) }
        since?.let { put("since", it) }
        until?.let { put("until", it) }
        limit?.let { put("limit", it) }
    }.toString()
}

/**
 * Nostr relay WebSocket client with NIP-42 authentication.
 * Manages a single relay connection, handles AUTH challenges,
 * and provides subscribe/publish APIs.
 */
class NostrClient(
    private val signer: EventSigner,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private data class PublishAck(val success: Boolean, val message: String)

    private data class PendingPublish(
        val event: NostrEvent,
        val deferred: CompletableDeferred<PublishAck>,
        var authRetryCount: Int = 0,
    )

    private companion object {
        const val MAX_AUTH_PUBLISH_RETRIES = 1
    }

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var relayUrl: String? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, NostrEvent>>(extraBufferCapacity = 64)
    val events: SharedFlow<Pair<String, NostrEvent>> = _events.asSharedFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val pendingPublishes = mutableMapOf<String, PendingPublish>()
    private var authEventId: String? = null
    private var pendingChallenge: String? = null

    /** Remember REQs so they run after the socket opens (and again after NIP-42 AUTH). */
    private val subscriptionLock = Any()
    private val activeSubscriptions = linkedMapOf<String, List<NostrFilter>>()

    fun connect(url: String) {
        shouldReconnect = true
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        authEventId = null
        pendingChallenge = null
        relayUrl = url
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = ConnectionState.Connecting
        openSocket(url)
    }

    private fun openSocket(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                if (ws !== webSocket) return
                reconnectAttempts = 0
                _connectionState.value = ConnectionState.Connected
                flushSubscriptions()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (ws !== webSocket) return
                scope.launch { handleMessage(text) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (ws !== webSocket) return
                webSocket = null
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                failAllPending()
                scheduleReconnect(url)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (ws !== webSocket) return
                webSocket = null
                _connectionState.value = ConnectionState.Disconnected
                failAllPending()
                scheduleReconnect(url)
            }
        })
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        authEventId = null
        pendingChallenge = null
        relayUrl = null
        webSocket?.cancel()
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        failAllPending()
        synchronized(subscriptionLock) { activeSubscriptions.clear() }
    }

    private fun scheduleReconnect(url: String) {
        if (!shouldReconnect || relayUrl != url) return
        reconnectJob?.cancel()

        val attempt = reconnectAttempts
        reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(6)
        val delayMs = reconnectDelayMs(attempt)

        reconnectJob = scope.launch {
            delay(delayMs)
            if (!shouldReconnect || relayUrl != url || webSocket != null) return@launch
            _connectionState.value = ConnectionState.Connecting
            openSocket(url)
        }
    }

    private fun reconnectDelayMs(attempt: Int): Long {
        // First attempt after 2s to avoid rapid reconnects (and NIP-42 Amber popups) on brief blips
        val cappedAttempt = attempt.coerceIn(0, 5)
        val delayMs = 2_000L * (1L shl cappedAttempt)
        return delayMs.coerceAtMost(30_000L)
    }

    /**
     * Publish a signed event to the relay.
     * Returns true if the relay accepted it (OK true), false otherwise.
     */
    suspend fun publish(event: NostrEvent): Boolean {
        return publishDetailed(event) is ClientPublishResult.Success
    }

    suspend fun publishDetailed(event: NostrEvent): ClientPublishResult {
        val ws = webSocket ?: return ClientPublishResult.NoSocket
        val pending = PendingPublish(
            event = event,
            deferred = CompletableDeferred(),
        )
        pendingPublishes[event.id] = pending
        if (!ws.send("""["EVENT",${event.toJson()}]""")) {
            pendingPublishes.remove(event.id)
            return ClientPublishResult.SendFailed("Relay socket closed before send")
        }
        return try {
            val ack = withTimeout(15_000) { pending.deferred.await() }
            if (ack.success) ClientPublishResult.Success else ClientPublishResult.Rejected(ack.message)
        } catch (_: TimeoutCancellationException) {
            pendingPublishes.remove(event.id)
            ClientPublishResult.TimedOut
        }
    }

    fun subscribe(subscriptionId: String, vararg filters: NostrFilter) {
        val fl = filters.toList()
        synchronized(subscriptionLock) {
            activeSubscriptions[subscriptionId] = fl
        }
        webSocket?.let { sendReq(it, subscriptionId, fl) }
    }

    private fun sendReq(ws: WebSocket, subscriptionId: String, filters: List<NostrFilter>) {
        val filterJson = filters.joinToString(",") { it.toJson() }
        ws.send("""["REQ","$subscriptionId",$filterJson]""")
    }

    private fun flushSubscriptions() {
        val ws = webSocket ?: return
        val snapshot: List<Pair<String, List<NostrFilter>>>
        synchronized(subscriptionLock) {
            if (activeSubscriptions.isEmpty()) return
            snapshot = activeSubscriptions.map { it.key to it.value }
        }
        for ((id, fl) in snapshot) {
            sendReq(ws, id, fl)
        }
    }

    fun unsubscribe(subscriptionId: String) {
        synchronized(subscriptionLock) { activeSubscriptions.remove(subscriptionId) }
        webSocket?.send("""["CLOSE","$subscriptionId"]""")
    }

    /**
     * Publish a test kind-30078 event with d-tag erv/test/... to verify the full pipeline.
     */
    suspend fun publishTestEvent(): Boolean {
        val ts = System.currentTimeMillis()
        val content = """{"test":true,"timestamp":$ts}"""
        val encrypted = signer.encryptToSelf(content)
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = ts / 1000,
            kind = 30078,
            tags = listOf(listOf("d", "erv/test/$ts")),
            content = encrypted
        )
        val signed = signer.sign(unsigned)
        return publish(signed)
    }

    fun destroy() {
        disconnect()
        scope.cancel()
    }

    // --- Internal message handling ---

    private suspend fun handleMessage(text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonArray
            when (json[0].jsonPrimitive.content) {
                "AUTH" -> handleAuth(json[1].jsonPrimitive.content)
                "EVENT" -> {
                    val subId = json[1].jsonPrimitive.content
                    val event = NostrEvent.fromJson(json[2].jsonObject)
                    _events.emit(subId to event)
                }
                "OK" -> {
                    val eventId = json[1].jsonPrimitive.content
                    val success = json[2].jsonPrimitive.boolean
                    val message = if (json.size > 3) json[3].jsonPrimitive.content else ""
                    handleOk(eventId, success, message)
                }
                "EOSE" -> { /* End of stored events for subscription */ }
                "CLOSED" -> {
                    val message = if (json.size > 2) json[2].jsonPrimitive.content else ""
                    if (message.startsWith("auth-required")) {
                        pendingChallenge?.let { scope.launch { handleAuth(it) } }
                    }
                }
                "NOTICE" -> {
                    _notices.emit(json[1].jsonPrimitive.content)
                }
            }
        } catch (_: Exception) {
            // Silently drop malformed relay messages
        }
    }

    private suspend fun handleAuth(challenge: String) {
        pendingChallenge = challenge
        val url = relayUrl ?: return
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 22242,
            tags = listOf(
                listOf("relay", url),
                listOf("challenge", challenge)
            ),
            content = ""
        )
        val signed = signer.sign(unsigned)
        authEventId = signed.id
        webSocket?.send("""["AUTH",${signed.toJson()}]""")
    }

    private fun handleOk(eventId: String, success: Boolean, message: String) {
        if (eventId == authEventId) {
            authEventId = null
            if (success) {
                _connectionState.value = ConnectionState.Authenticated
                flushSubscriptions()
                retryAuthBlockedPublishes()
            } else {
                failAuthBlockedPublishes(if (message.isBlank()) "Relay auth was rejected" else message)
            }
            return
        }

        if (!success && message.startsWith("auth-required")) {
            val pending = pendingPublishes[eventId]
            if (pending == null) return
            if (pending.authRetryCount >= MAX_AUTH_PUBLISH_RETRIES) {
                pendingPublishes.remove(eventId)?.deferred?.complete(
                    PublishAck(
                        success = false,
                        message = "Relay requires authentication; publish retry was exhausted"
                    )
                )
                return
            }
            pending.authRetryCount++
            scope.launch {
                val challenge = awaitPendingChallenge()
                if (challenge == null) {
                    pendingPublishes.remove(eventId)?.deferred?.complete(
                        PublishAck(
                            success = false,
                            message = "Relay requires authentication but did not provide a challenge"
                        )
                    )
                } else {
                    handleAuth(challenge)
                }
            }
            return
        }

        pendingPublishes.remove(eventId)?.deferred?.complete(
            PublishAck(success = success, message = message)
        )
    }

    private fun failAllPending() {
        pendingPublishes.values.forEach {
            it.deferred.complete(PublishAck(success = false, message = "Relay connection closed"))
        }
        pendingPublishes.clear()
    }

    private suspend fun awaitPendingChallenge(timeoutMs: Long = 1_500): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pendingChallenge?.let { return it }
            delay(50)
        }
        return pendingChallenge
    }

    private fun retryAuthBlockedPublishes() {
        val ws = webSocket ?: return
        val blocked = pendingPublishes.values.filter {
            !it.deferred.isCompleted && it.authRetryCount > 0
        }
        for (pending in blocked) {
            if (!ws.send("""["EVENT",${pending.event.toJson()}]""")) {
                pendingPublishes.remove(pending.event.id)?.deferred?.complete(
                    PublishAck(success = false, message = "Relay socket closed before authenticated retry")
                )
            }
        }
    }

    private fun failAuthBlockedPublishes(message: String) {
        val blockedIds = pendingPublishes.values
            .filter { !it.deferred.isCompleted && it.authRetryCount > 0 }
            .map { it.event.id }
        for (eventId in blockedIds) {
            pendingPublishes.remove(eventId)?.deferred?.complete(
                PublishAck(success = false, message = message)
            )
        }
    }
}
