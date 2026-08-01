package com.scaso.drclawapp.data.websocket

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OkHttp WebSocket client for the cc-bridge v2 session daemon.
 *
 * Connects to the cc-bridge WebSocket endpoint (gateway URL + "/cc/ws"),
 * handles challenge-based auth, request/response correlation via
 * CompletableDeferred, event routing via SharedFlow, and auto-reconnect
 * with exponential backoff.
 *
 * No Android imports -- KMP-extractable.
 */
class CcBridgeClient(
    private val url: String,
    private val token: String,
    private val scope: CoroutineScope,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _connectionState = MutableStateFlow<CcBridgeConnectionState>(
        CcBridgeConnectionState.Disconnected,
    )
    val connectionState: StateFlow<CcBridgeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<CcBridgeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<CcBridgeEvent> = _events.asSharedFlow()

    private var webSocket: WebSocket? = null

    // Request/response correlation
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ResponseFrame>>()

    // Reconnect state
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var intentionalDisconnect = false

    companion object {
        private const val MAX_BACKOFF_MS = 30_000L
        private const val AUTH_TIMEOUT_MS = 10_000L
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    fun connect() {
        intentionalDisconnect = false
        reconnectAttempts = 0
        doConnect()
    }

    fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        _connectionState.value = CcBridgeConnectionState.Disconnected
    }

    /**
     * Called when the network changes (e.g., WiFi ↔ 5G, tower handoff).
     * Cancels the current WebSocket and reconnects immediately with no backoff.
     */
    fun onNetworkChanged() {
        if (intentionalDisconnect) return
        if (_connectionState.value is CcBridgeConnectionState.Disconnected) return
        reconnectJob?.cancel()
        reconnectAttempts = 0
        webSocket?.cancel()
        webSocket = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        scope.launch {
            _connectionState.value = CcBridgeConnectionState.Connecting
            delay(500L)
            doConnect()
        }
    }

    // ── Session API Methods ──────────────────────────────────────────

    /** List active daemon sessions. */
    suspend fun listSessions(): ResponseFrame {
        return sendRequest(newId(), "sessions.list")
    }

    /** Create a new daemon session. */
    suspend fun createSession(
        cwd: String,
        project: String? = null,
        backend: String = "cloud",
        model: String? = null,
        permissionMode: String = "bypassPermissions",
    ): ResponseFrame {
        val params = buildJsonObject {
            put("cwd", cwd)
            if (project != null) put("project", project)
            put("backend", backend)
            if (model != null) put("model", model)
            put("permissionMode", permissionMode)
        }
        return sendRequest(newId(), "session.create", params)
    }

    /** Resume an existing CC session as a live daemon session. */
    suspend fun resumeSession(
        sessionId: String,
        cwd: String,
        project: String? = null,
        backend: String? = null,
        model: String? = null,
        permissionMode: String? = null,
    ): ResponseFrame {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("cwd", cwd)
            project?.let { put("project", it) }
            backend?.let { put("backend", it) }
            model?.let { put("model", it) }
            permissionMode?.let { put("permissionMode", it) }
        }
        return sendRequest(newId(), "session.resume", params)
    }

    /** Attach to an existing daemon session (subscribe to its events). */
    suspend fun attachSession(id: String): ResponseFrame {
        val params = buildJsonObject { put("id", id) }
        return sendRequest(newId(), "session.attach", params)
    }

    /** Detach from a daemon session (stop receiving events). */
    suspend fun detachSession(id: String): ResponseFrame {
        val params = buildJsonObject { put("id", id) }
        return sendRequest(newId(), "session.detach", params)
    }

    /** Send a chat message to a daemon session. */
    suspend fun sendMessage(sessionId: String, message: String): ResponseFrame {
        val params = buildJsonObject {
            put("id", sessionId)
            put("message", message)
        }
        return sendRequest(newId(), "session.send", params)
    }

    /** Interrupt (abort) an active daemon session. */
    suspend fun interruptSession(id: String): ResponseFrame {
        val params = buildJsonObject { put("id", id) }
        return sendRequest(newId(), "session.interrupt", params)
    }

    /** Destroy a daemon session with optional reason. */
    suspend fun destroySession(id: String, reason: String? = null): ResponseFrame {
        val params = buildJsonObject {
            put("id", id)
            if (reason != null) put("reason", reason)
        }
        return sendRequest(newId(), "session.destroy", params)
    }

    /** List local models available on the daemon host. */
    suspend fun listLocalModels(): ResponseFrame {
        return sendRequest(newId(), "models.local.list")
    }

    /** Respond to a permission request (allow or deny a tool use). */
    suspend fun respondPermission(
        sessionId: String,
        requestId: String,
        allow: Boolean,
    ): ResponseFrame {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("requestId", requestId)
            put("allow", allow)
        }
        return sendRequest(newId(), "sessions.permission.respond", params)
    }

    // ── Internal: Connection ─────────────────────────────────────────

    private fun doConnect() {
        if (url.isBlank()) {
            _connectionState.value = CcBridgeConnectionState.Disconnected
            return
        }
        _connectionState.value = CcBridgeConnectionState.Connecting
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = okHttpClient.newWebSocket(request, Listener())
    }

    // ── Internal: Request/Response ───────────────────────────────────

    private suspend fun sendRequest(
        id: String,
        method: String,
        params: kotlinx.serialization.json.JsonElement? = null,
    ): ResponseFrame {
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        val frame = RequestFrame(id = id, method = method, params = params)
        val text = json.encodeToString(RequestFrame.serializer(), frame)
        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            pendingRequests.remove(id)
            return ResponseFrame(
                id = id,
                ok = false,
                error = ErrorShape(code = "NOT_CONNECTED", message = "WebSocket not connected"),
            )
        }

        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } finally {
            pendingRequests.remove(id)
        }
    }

    // ── Internal: Frame Handling ─────────────────────────────────────

    private fun handleFrame(text: String) {
        val envelope = try {
            json.decodeFromString(FrameEnvelope.serializer(), text)
        } catch (_: Exception) {
            return
        }

        when (envelope.type) {
            "event" -> handleEvent(envelope, text)
            "res" -> handleResponse(envelope)
        }
    }

    private fun handleEvent(envelope: FrameEnvelope, rawText: String) {
        when (envelope.event) {
            "auth.challenge" -> handleAuthChallenge()
            "session.init" -> handleSessionInit(rawText)
            "session.stream" -> handleSessionStream(rawText)
            "session.result" -> handleSessionResult(rawText)
            "session.status" -> handleSessionStatus(rawText)
            "session.assistant" -> handleSessionAssistant(rawText)
            "session.error" -> handleSessionError(rawText)
            "session.closed" -> handleSessionClosed(rawText)
            "session.permission" -> handleSessionPermission(rawText)
        }
    }

    private fun handleAuthChallenge() {
        _connectionState.value = CcBridgeConnectionState.Authenticating

        val authId = newId()
        val params = buildJsonObject { put("token", token) }
        val frame = RequestFrame(id = authId, method = "auth", params = params)
        val text = json.encodeToString(RequestFrame.serializer(), frame)

        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[authId] = deferred
        webSocket?.send(text)

        scope.launch {
            try {
                val response = withTimeout(AUTH_TIMEOUT_MS) { deferred.await() }
                if (response.ok) {
                    reconnectAttempts = 0
                    _connectionState.value = CcBridgeConnectionState.Connected
                    _events.emit(CcBridgeEvent.ConnectionChanged(CcBridgeConnectionState.Connected))
                } else {
                    val msg = response.error?.message ?: "Authentication failed"
                    _connectionState.value = CcBridgeConnectionState.Error(msg)
                }
            } catch (_: TimeoutCancellationException) {
                _connectionState.value = CcBridgeConnectionState.Error("Auth timeout")
                scheduleReconnect()
            } catch (e: Exception) {
                _connectionState.value = CcBridgeConnectionState.Error(
                    e.message ?: "Handshake failed",
                )
                scheduleReconnect()
            } finally {
                pendingRequests.remove(authId)
            }
        }
    }

    // ── Event Parsers ────────────────────────────────────────────────

    /**
     * Parse the raw JSON to extract sessionId and data object.
     * Server format: { "type": "event", "event": "...", "sessionId": "s1", "data": { ... } }
     */
    private fun parseEventJson(rawText: String): Pair<String, JsonObject>? {
        return try {
            val obj = json.parseToJsonElement(rawText).jsonObject
            val sessionId = obj["sessionId"]?.jsonPrimitive?.contentOrNull ?: return null
            val data = obj["data"]?.jsonObject ?: JsonObject(emptyMap())
            sessionId to data
        } catch (_: Exception) {
            null
        }
    }

    private fun handleSessionInit(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        scope.launch { _events.emit(CcBridgeEvent.SessionInit(sessionId, data)) }
    }

    private fun handleSessionStream(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return

        // Normalize raw Anthropic API stream events into ViewModel-friendly kinds.
        // Server sends raw SDK events: { streamEvent: { type: "content_block_delta",
        //   delta: { type: "text_delta", text: "..." } }, meta: { ... } }
        // ViewModel expects: kind="text_delta" with text, or kind="tool_use" with toolName.
        val streamEvent = data["streamEvent"]?.jsonObject
        val rawType = streamEvent?.get("type")?.jsonPrimitive?.contentOrNull ?: "unknown"
        val delta = streamEvent?.get("delta")?.jsonObject
        val contentBlock = streamEvent?.get("content_block")?.jsonObject

        val kind: String
        val text: String?
        val toolName: String?
        val input = streamEvent?.get("input") ?: data["input"]

        when (rawType) {
            "content_block_delta" -> {
                val deltaType = delta?.get("type")?.jsonPrimitive?.contentOrNull
                when (deltaType) {
                    "text_delta" -> {
                        kind = "text_delta"
                        text = delta?.get("text")?.jsonPrimitive?.contentOrNull
                        toolName = null
                    }
                    "thinking_delta" -> {
                        kind = "thinking"
                        text = delta?.get("thinking")?.jsonPrimitive?.contentOrNull
                        toolName = null
                    }
                    else -> return // input_json_delta etc. — skip
                }
            }
            "content_block_start" -> {
                val blockType = contentBlock?.get("type")?.jsonPrimitive?.contentOrNull
                when (blockType) {
                    "tool_use" -> {
                        kind = "tool_use"
                        text = null
                        toolName = contentBlock?.get("name")?.jsonPrimitive?.contentOrNull
                    }
                    else -> return // text/thinking block start — no actionable content yet
                }
            }
            "content_block_stop" -> {
                kind = "tool_result"
                text = null
                toolName = null
            }
            else -> return // message_start, message_delta, message_stop — skip
        }

        scope.launch {
            _events.emit(
                CcBridgeEvent.SessionStream(
                    sessionId = sessionId,
                    kind = kind,
                    text = text,
                    toolName = toolName,
                    input = input,
                    data = data,
                ),
            )
        }
    }

    private fun handleSessionResult(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        scope.launch { _events.emit(CcBridgeEvent.SessionResult(sessionId, data)) }
    }

    private fun handleSessionStatus(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        val status = data["status"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        scope.launch { _events.emit(CcBridgeEvent.SessionStatus(sessionId, status)) }
    }

    private fun handleSessionAssistant(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        scope.launch { _events.emit(CcBridgeEvent.SessionAssistant(sessionId, data)) }
    }

    private fun handleSessionError(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        val message = data["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
        scope.launch { _events.emit(CcBridgeEvent.SessionError(sessionId, message, data)) }
    }

    private fun handleSessionClosed(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        val reason = data["reason"]?.jsonPrimitive?.contentOrNull
        scope.launch { _events.emit(CcBridgeEvent.SessionClosed(sessionId, reason)) }
    }

    private fun handleSessionPermission(rawText: String) {
        val (sessionId, data) = parseEventJson(rawText) ?: return
        val requestId = data["requestId"]?.jsonPrimitive?.contentOrNull ?: return
        val toolName = data["toolName"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val input = data["input"]

        scope.launch {
            _events.emit(
                CcBridgeEvent.SessionPermission(
                    sessionId = sessionId,
                    requestId = requestId,
                    toolName = toolName,
                    input = input,
                    data = data,
                ),
            )
        }
    }

    // ── Response Handling ────────────────────────────────────────────

    private fun handleResponse(envelope: FrameEnvelope) {
        val id = envelope.id ?: return
        val response = ResponseFrame(
            id = id,
            ok = envelope.ok ?: false,
            payload = envelope.payload,
            error = envelope.error,
        )
        pendingRequests[id]?.complete(response)
    }

    // ── Reconnect ────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        if (intentionalDisconnect) return

        reconnectJob?.cancel()
        val delay = minOf(
            1000L * (1L shl minOf(reconnectAttempts, 4)),
            MAX_BACKOFF_MS,
        )
        reconnectAttempts++

        reconnectJob = scope.launch {
            _connectionState.value = CcBridgeConnectionState.Error("Reconnecting...", delay)
            _events.emit(
                CcBridgeEvent.ConnectionChanged(
                    CcBridgeConnectionState.Error("Reconnecting...", delay),
                ),
            )
            delay(delay)
            doConnect()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    // ── WebSocket Listener ───────────────────────────────────────────

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Wait for auth.challenge event from server
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            pendingRequests.values.forEach { it.cancel() }
            pendingRequests.clear()
            _connectionState.value = CcBridgeConnectionState.Error(
                t.message ?: "Connection failed",
            )
            scope.launch {
                _events.emit(
                    CcBridgeEvent.ConnectionChanged(
                        CcBridgeConnectionState.Error(t.message ?: "Connection failed"),
                    ),
                )
            }
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = CcBridgeConnectionState.Disconnected
            scope.launch {
                _events.emit(
                    CcBridgeEvent.ConnectionChanged(CcBridgeConnectionState.Disconnected),
                )
            }
            if (!intentionalDisconnect) {
                scheduleReconnect()
            }
        }
    }
}
