package com.erv.app.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Authenticated : ConnectionState()
    data class Error(val message: String) : ConnectionState()
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
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var relayUrl: String? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, NostrEvent>>(extraBufferCapacity = 64)
    val events: SharedFlow<Pair<String, NostrEvent>> = _events.asSharedFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val pendingPublishes = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private var authEventId: String? = null
    private var pendingChallenge: String? = null

    fun connect(url: String) {
        disconnect()
        relayUrl = url
        _connectionState.value = ConnectionState.Connecting

        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.Connected
            }

            override fun onMessage(ws: WebSocket, text: String) {
                scope.launch { handleMessage(text) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
                failAllPending()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.Disconnected
                failAllPending()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        failAllPending()
    }

    /**
     * Publish a signed event to the relay.
     * Returns true if the relay accepted it (OK true), false otherwise.
     */
    suspend fun publish(event: NostrEvent): Boolean {
        val ws = webSocket ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pendingPublishes[event.id] = deferred
        ws.send("""["EVENT",${event.toJson()}]""")
        return try {
            withTimeout(15_000) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            pendingPublishes.remove(event.id)
            false
        }
    }

    fun subscribe(subscriptionId: String, vararg filters: NostrFilter) {
        val ws = webSocket ?: return
        val filterJson = filters.joinToString(",") { it.toJson() }
        ws.send("""["REQ","$subscriptionId",$filterJson]""")
    }

    fun unsubscribe(subscriptionId: String) {
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
            }
            return
        }

        if (!success && message.startsWith("auth-required")) {
            scope.launch { pendingChallenge?.let { handleAuth(it) } }
            return
        }

        pendingPublishes.remove(eventId)?.complete(success)
    }

    private fun failAllPending() {
        pendingPublishes.values.forEach { it.complete(false) }
        pendingPublishes.clear()
    }
}
