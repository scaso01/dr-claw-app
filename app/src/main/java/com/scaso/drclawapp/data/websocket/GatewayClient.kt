package com.scaso.drclawapp.data.websocket

import com.scaso.drclawapp.data.approval.CmdApprovalGate
import com.scaso.drclawapp.data.roles.ModelChangedEvent
import kotlin.random.Random
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OkHttp WebSocket client for the Ironjaw Gateway Protocol v4.
 *
 * Handles the 2-step handshake (connect → auth.connect → response),
 * request/response correlation, event routing, and auto-reconnect
 * with exponential backoff.
 *
 * No Android imports — KMP-extractable.
 */

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Authenticating : ConnectionState()
    data class Connected(val serverInfo: HelloOk) : ConnectionState()
    data class Error(val message: String, val retryMs: Long?) : ConnectionState()

    /**
     * Terminal state: the server rejected auth.connect (bad/expired token). Unlike [Error],
     * this does NOT trigger auto-reconnect — retrying with the same bad token would loop
     * forever. Cleared only when the caller explicitly reconnects (connect()/reconnect()/
     * reconnectWith()), e.g. after the user updates the token in Settings.
     */
    data class AuthFailed(val message: String) : ConnectionState()
}

/** Interface for dispatching incoming device commands (no Android imports). */
interface CmdDispatcherInterface {
    suspend fun dispatch(tool: String, params: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonElement
}

open class GatewayClient(
    private var url: String,
    private val token: String,
    private val scope: CoroutineScope,
    private val appVersion: String = "0.9.5",
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        // ponytail: 20s keepalive catches a dead tunnel ~10s sooner than 30s. The DI-provided
        // client (GatewayModule) is what runs in production; this default is the test fallback.
        .pingInterval(20, TimeUnit.SECONDS)
        // Anchor pins — keep in sync with GatewayModule.provideTransportOkHttpClient.
        .certificatePinner(
            CertificatePinner.Builder()
                .add("gateway.example.com", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=") // ISRG Root X1
                .add("gateway.example.com", "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=") // ISRG Root X2
                .add("gateway.example.com", "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=") // ISRG Root YE
                .build()
        )
        .build(),
) {
    /** Optional command dispatcher for handling incoming device commands. */
    var cmdDispatcher: CmdDispatcherInterface? = null

    /**
     * Gate consulted before dispatching an incoming `cmd` frame. Required for any command
     * to execute -- unset gate, denial, or timeout all fail closed (see [handleCmd]).
     */
    var cmdApprovalGate: CmdApprovalGate? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    open val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 256)
    open val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    private val _ccChatEvents = MutableSharedFlow<CcBridgeEvent>(extraBufferCapacity = 256)
    val ccChatEvents: SharedFlow<CcBridgeEvent> = _ccChatEvents.asSharedFlow()

    private val _modelChangedFlow = MutableSharedFlow<ModelChangedEvent>(replay = 1, extraBufferCapacity = 8)
    val modelChangedFlow: SharedFlow<ModelChangedEvent> = _modelChangedFlow.asSharedFlow()

    // Stable per-client device identifier (persists for the lifetime of this GatewayClient instance)
    private val instanceId: String = UUID.randomUUID().toString()

    private var webSocket: WebSocket? = null
    private var sessionKey: String? = null
    private var tickIntervalMs: Long = 30_000L
    private var lastTickTime: Long = 0L

    // Request/response correlation
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ResponseFrame>>()

    // Reconnect state
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var tickWatchdogJob: Job? = null
    private var intentionalDisconnect = false
    private var fastReconnectUntil = 0L
    private val isReconnecting = java.util.concurrent.atomic.AtomicBoolean(false)

    companion object {
        private const val MAX_BACKOFF_MS = 30_000L
        private const val AUTH_TIMEOUT_MS = 10_000L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val TTS_TIMEOUT_MS = 120_000L
        private const val TICK_STALENESS_MULTIPLIER = 3
        /** No user response to an incoming cmd approval within this window = deny (fail closed). */
        private const val CMD_APPROVAL_TIMEOUT_MS = 60_000L
        /** FIX #9: watchdog pings before cancelling; connection is alive if it answers within this. */
        private const val PING_PROBE_TIMEOUT_MS = 5_000L
    }

    fun connect() {
        intentionalDisconnect = false
        reconnectAttempts = 0
        doConnect()
    }

    fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        tickWatchdogJob?.cancel()
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun reconnectWith(newUrl: String) {
        if (newUrl == url) return
        disconnect()
        url = newUrl
        connect()
    }

    /**
     * Force a fresh connection to the same URL. Used when transport config changes (e.g. the
     * Meshnet IP), where reconnectWith() would no-op because the URL is unchanged.
     */
    fun reconnect() {
        disconnect()
        connect()
    }

    /**
     * Called when the network changes (e.g., WiFi ↔ 5G, tower handoff).
     * Cancels the current WebSocket and reconnects immediately with no backoff.
     * Safe to call from any thread. No-op if intentionally disconnected.
     */
    fun onNetworkChanged() {
        if (intentionalDisconnect) return
        val current = _connectionState.value
        if (current is ConnectionState.Disconnected) return
        // Cancel existing connection — triggers onFailure which would scheduleReconnect,
        // but we pre-empt that by resetting attempts and using fast reconnect
        reconnectJob?.cancel()
        tickWatchdogJob?.cancel()
        reconnectAttempts = 0
        enableFastReconnect(10_000L)
        webSocket?.cancel()
        webSocket = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        isReconnecting.set(true)
        scope.launch {
            _connectionState.value = ConnectionState.Error("Network changed, reconnecting...", 500L)
            delay(500L)
            doConnect()
        }
    }

    fun currentUrl(): String = url

    /**
     * Enable fast reconnect for the given duration. During this window,
     * reconnect delay is capped at 1 second instead of exponential backoff.
     * Used when the gateway is expected to restart (e.g., model switch).
     */
    fun enableFastReconnect(durationMs: Long = 15_000L) {
        fastReconnectUntil = System.currentTimeMillis() + durationMs
        reconnectAttempts = 0
    }

    open suspend fun sendMessage(text: String, forceFresh: Boolean = false): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            ChatSendParams(
                message = text,
                idempotencyKey = newId(),
                sessionKey = sessionKey,
                forceFresh = if (forceFresh) true else null,
            )
        )
        return sendRequest(id, "chat.send", params)
    }

    open suspend fun loadHistory(limit: Int? = null): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            ChatHistoryParams(sessionKey = sessionKey, limit = limit)
        )
        return sendRequest(id, "chat.history", params)
    }

    suspend fun abortRun(runId: String? = null): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(ChatAbortParams(runId = runId))
        return sendRequest(id, "chat.abort", params)
    }

    suspend fun abortSession(): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(ChatAbortParams(sessionKey = sessionKey))
        return sendRequest(id, "chat.abort", params)
    }

    suspend fun injectText(text: String): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            ChatInjectParams(message = text, sessionKey = sessionKey)
        )
        return sendRequest(id, "chat.inject", params)
    }

    // --- Session API ---

    open suspend fun listSessions(limit: Int? = null, offset: Int? = null): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            SessionsListParams(limit = limit, offset = offset)
        )
        return sendRequest(id, "sessions.list", params)
    }

    open suspend fun patchSession(sessionKey: String, title: String?): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            SessionRenameParams(sessionId = sessionKey, title = title ?: "")
        )
        return sendRequest(id, "session.rename", params)
    }

    open suspend fun deleteSession(sessionId: String): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            SessionsDeleteParams(sessionId = sessionId)
        )
        return sendRequest(id, "session.delete", params)
    }

    open suspend fun createSession(title: String? = null): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            SessionCreateParams(title = title)
        )
        return sendRequest(id, "session.create", params)
    }

    open suspend fun switchSession(newSessionKey: String) {
        val id = newId()
        val params = buildJsonObject {
            put("session_id", newSessionKey)
        }
        sendRequest(id, "session.switch", params)
        sessionKey = newSessionKey
    }

    /**
     * Sets the working directory for a session via the `session.set_cwd` RPC (Ironjaw C1).
     *
     * @param targetSessionId The Ironjaw session_id to update (NOT the app session key).
     * @param path Absolute path on the server, or null to clear.
     * @return ResponseFrame — caller checks [ResponseFrame.ok] and [ResponseFrame.error].
     */
    open suspend fun sessionSetCwd(targetSessionId: String, path: String?): ResponseFrame {
        val id = newId()
        val params = IronjawProtocol.sessionSetCwdParams(targetSessionId, path)
        return sendRequest(id, IronjawProtocol.SESSION_SET_CWD, params)
    }

    /**
     * Forks a child agent via the `agent.fork` RPC (Ironjaw C8).
     *
     * The caller must show a [PermissionDialog] BEFORE invoking this method.
     *
     * @param parentSessionId The Ironjaw session_id that will own the forked agent.
     * @param task The task description to send to the child agent.
     * @param inheritTools Whether the child agent inherits the parent's tool set (default true).
     * @param inheritBrainContext Whether the child agent inherits the brain context (default false).
     * @return ResponseFrame — caller checks [ResponseFrame.ok] and [ResponseFrame.error].
     *         On success, [ResponseFrame.payload] contains `{ agent_id, task, parent_session_id }`.
     */
    open suspend fun forkAgent(
        parentSessionId: String,
        task: String,
        inheritTools: Boolean = true,
        inheritBrainContext: Boolean = false,
    ): ResponseFrame {
        val id = newId()
        val params = IronjawProtocol.agentForkParams(parentSessionId, task, inheritTools, inheritBrainContext)
        return sendRequest(id, IronjawProtocol.AGENT_FORK, params)
    }

    /** Update local session key without sending an RPC (e.g. after session.create auto-switches). */
    open fun setLocalSessionKey(key: String) {
        sessionKey = key
    }

    fun getCurrentSessionKey(): String? = sessionKey

    // --- Attachment API (Phase 7) ---

    suspend fun sendMessageWithAttachments(
        message: String,
        attachments: List<RpcAttachment>,
    ): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            ChatSendParams(
                message = message,
                idempotencyKey = newId(),
                sessionKey = sessionKey,
                attachments = attachments,
            )
        )
        return sendRequest(id, "chat.send", params)
    }

    open suspend fun convertTextToSpeech(text: String, voice: String? = null): ResponseFrame {
        val id = newId()
        val params = json.encodeToJsonElement(
            TtsConvertParams(text = text, voice = voice)
        )
        return sendRequest(id, "tts.convert", params, timeoutMs = TTS_TIMEOUT_MS)
    }

    // --- Generic RPC (v0.5.0+) ---

    /**
     * Send a generic RPC request to the gateway. Used for extension
     * message types (ccbridge.sessions, infra.status, project.status, file.get).
     */
    open suspend fun sendGenericRequest(
        method: String,
        params: kotlinx.serialization.json.JsonElement? = null,
    ): ResponseFrame {
        val id = newId()
        val frame = RequestFrame(id = id, method = method, params = params)
        val text = json.encodeToString(RequestFrame.serializer(), frame)

        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

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

    // --- B1: Slash commands RPC ---

    suspend fun commandsList(): List<CommandSpec> {
        val response = sendGenericRequest("commands.list")
        if (!response.ok || response.payload == null) return emptyList()
        return try {
            val jsonCfg = Json { ignoreUnknownKeys = true }
            jsonCfg.decodeFromString(
                ListSerializer(CommandSpec.serializer()),
                response.payload.toString(),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun commandsExecute(
        name: String,
        args: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ): CommandResult {
        val params = buildJsonObject {
            put("name", name)
            if (args.isNotEmpty()) {
                put("args", json.encodeToJsonElement(args))
            }
        }
        val response = sendGenericRequest("commands.execute", params)
        if (!response.ok || response.payload == null) {
            return CommandResult(ok = false, output = response.error?.message ?: "Unknown error")
        }
        return try {
            val jsonCfg = Json { ignoreUnknownKeys = true }
            jsonCfg.decodeFromString(CommandResult.serializer(), response.payload.toString())
        } catch (_: Exception) {
            CommandResult(ok = false, output = "Failed to parse response")
        }
    }

    // --- Internal ---

    private fun doConnect() {
        _connectionState.value = ConnectionState.Connecting
        // Origin header required for control-ui client to pass gateway origin check
        val originUrl = url.replace("wss://", "https://").replace("ws://", "http://")
        val request = Request.Builder()
            .url(url)
            .header("Origin", originUrl)
            .build()
        webSocket = okHttpClient.newWebSocket(request, Listener())
    }

    private suspend fun sendRequest(
        id: String,
        method: String,
        params: kotlinx.serialization.json.JsonElement,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
    ): ResponseFrame {
        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[id] = deferred

        val frame = RequestFrame(id = id, method = method, params = params)
        val text = json.encodeToString(RequestFrame.serializer(), frame)
        val sent = webSocket?.send(text) ?: false
        if (!sent) {
            pendingRequests.remove(id)
            return ResponseFrame(id = id, ok = false, error = ErrorShape(code = "NOT_CONNECTED", message = "WebSocket not connected"))
        }

        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pendingRequests.remove(id)
        }
    }

    private fun handleFrame(text: String) {
        val envelope = try {
            json.decodeFromString(FrameEnvelope.serializer(), text)
        } catch (_: Exception) {
            return
        }

        when (envelope.type) {
            "event" -> handleEvent(envelope)
            "res" -> handleResponse(envelope)
            "cmd" -> handleCmd(envelope)
            "ui" -> handleUiFrame(envelope)
        }
    }

    private fun handleEvent(envelope: FrameEnvelope) {
        when (envelope.event) {
            "chat" -> handleChatEvent(envelope)
            "chat.inject" -> handleInjectEvent(envelope)
            "tick" -> handleTick(envelope)
            "permission.request" -> handlePermissionRequest(envelope)
            "cc.chat" -> handleCcChatEvent(envelope)
            "cc.complete" -> handleCcCompleteEvent(envelope)
            "model.changed" -> handleModelChanged(envelope)
            // Phase 13: native-chat tool events
            "tool.start" -> handleNativeToolStart(envelope)
            "tool.result" -> handleNativeToolResult(envelope)
            // chat.history: handled via res frame in ChatRepository.loadHistory()
        }
    }

    private fun handlePermissionRequest(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val requestId = payloadObj["request_id"]?.jsonPrimitive?.contentOrNull ?: return
        val method = (payloadObj["tool"] ?: payloadObj["method"])?.jsonPrimitive?.contentOrNull ?: "unknown"
        val reason = payloadObj["reason"]?.jsonPrimitive?.contentOrNull ?: ""
        val description = reason.ifBlank { "Permission requested for $method" }

        // Auto-approve session management operations (user already confirmed via UI)
        if (method.startsWith("session.")) {
            approvePermission(requestId)
            return
        }

        // Phase 13: if tool_call_id is present this is a native-chat permission request
        val toolCallId = payloadObj["tool_call_id"]?.jsonPrimitive?.contentOrNull
        val allowSessionCache = payloadObj["allow_session_cache"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val consequences = payloadObj["consequences"]?.jsonPrimitive?.contentOrNull

        scope.launch {
            if (toolCallId != null) {
                _events.emit(
                    GatewayEvent.NativePermissionRequest(
                        requestId = requestId,
                        tool = method,
                        description = description,
                        toolCallId = toolCallId,
                        allowSessionCache = allowSessionCache,
                    )
                )
            } else {
                _events.emit(GatewayEvent.PermissionRequest(requestId, method, description, consequences))
            }
        }
    }

    // Phase 13: native-chat tool event handlers

    private fun handleNativeToolStart(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val runId = payloadObj["run_id"]?.jsonPrimitive?.contentOrNull
        val toolCallId = payloadObj["tool_call_id"]?.jsonPrimitive?.contentOrNull ?: return
        val toolName = payloadObj["tool_name"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val input = payloadObj["input"] ?: kotlinx.serialization.json.JsonObject(emptyMap())
        scope.launch {
            _events.emit(
                GatewayEvent.NativeToolStart(
                    runId = runId,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    input = input,
                )
            )
        }
    }

    private fun handleNativeToolResult(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val runId = payloadObj["run_id"]?.jsonPrimitive?.contentOrNull
        val toolCallId = payloadObj["tool_call_id"]?.jsonPrimitive?.contentOrNull ?: return
        val toolName = payloadObj["tool_name"]?.jsonPrimitive?.contentOrNull
        val output = (payloadObj["content"] ?: payloadObj["output"]) ?: kotlinx.serialization.json.JsonObject(emptyMap())
        val error = payloadObj["error"]?.jsonPrimitive?.contentOrNull
        val durationMs = payloadObj["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        scope.launch {
            _events.emit(
                GatewayEvent.NativeToolResult(
                    runId = runId,
                    toolCallId = toolCallId,
                    toolName = toolName,
                    output = output,
                    error = error,
                    durationMs = durationMs,
                )
            )
        }
    }

    fun approvePermission(requestId: String) {
        val frame = CmdResFrame(id = requestId, ok = true)
        val text = json.encodeToString(CmdResFrame.serializer(), frame)
        scope.launch { webSocket?.send(text) }
    }

    fun denyPermission(requestId: String) {
        val frame = CmdResFrame(id = requestId, ok = false)
        val text = json.encodeToString(CmdResFrame.serializer(), frame)
        scope.launch { webSocket?.send(text) }
    }

    /**
     * Phase 16 native-chat HITL: send a cmd.res frame with ok + scope data.
     * Scope is "once" or "session" per the server contract.
     *
     * Approve:  cmd.res { id, ok=true,  data: { scope: "once"|"session" } }
     * Deny:     cmd.res { id, ok=false, data: { scope: "once" } }
     */
    open fun sendNativePermissionResponse(requestId: String, ok: Boolean, scope: String) {
        val data = buildJsonObject { put("scope", scope) }
        val frame = CmdResFrame(id = requestId, ok = ok, data = data)
        val text = json.encodeToString(CmdResFrame.serializer(), frame)
        this.scope.launch { webSocket?.send(text) }
    }

    /**
     * Protocol v4 auth: send auth.connect immediately on WebSocket open.
     * No challenge event needed — client initiates authentication.
     */
    private fun sendAuth() {
        _connectionState.value = ConnectionState.Authenticating

        val connectId = newId()
        val params = buildJsonObject {
            put("token", token)
            // Ironjaw expects "device" as an object with name/platform/app_version
            put("device", buildJsonObject {
                put("name", "Dr. CLAW")
                put("platform", "android")
                put("app_version", appVersion)
            })
            // Resume existing session on reconnect (avoids creating a new session each time)
            sessionKey?.let { put("session_id", it) }
        }
        val frame = RequestFrame(id = connectId, method = "auth.connect", params = params)
        val text = json.encodeToString(RequestFrame.serializer(), frame)

        val deferred = CompletableDeferred<ResponseFrame>()
        pendingRequests[connectId] = deferred
        webSocket?.send(text)

        scope.launch {
            try {
                val response = withTimeout(AUTH_TIMEOUT_MS) { deferred.await() }
                if (response.ok && response.payload != null) {
                    val payloadObj = response.payload.jsonObject
                    // Extract session_id from v4 response payload
                    val sessionId = payloadObj["session_id"]?.jsonPrimitive?.contentOrNull
                    sessionKey = sessionId
                    reconnectAttempts = 0
                    lastTickTime = System.currentTimeMillis()
                    // Parse HelloOk from the actual Ironjaw response payload
                    val hello = try {
                        json.decodeFromJsonElement(HelloOk.serializer(), response.payload)
                    } catch (_: Exception) {
                        HelloOk(
                            protocol = 4,
                            server = ServerInfo(name = "ironjaw", version = "0.1.0"),
                            policy = Policy(),
                        )
                    }
                    tickIntervalMs = hello.policy.tickIntervalMs
                    isReconnecting.set(false)
                    _connectionState.value = ConnectionState.Connected(hello)
                    _events.emit(GatewayEvent.Connected(hello))
                    startTickWatchdog()
                } else {
                    isReconnecting.set(false)
                    val msg = response.error?.message ?: "Authentication failed"
                    // Terminal: server rejected the token. Do NOT scheduleReconnect — the
                    // socket will still receive onClosed, which checks for this state.
                    _connectionState.value = ConnectionState.AuthFailed(msg)
                }
            } catch (_: TimeoutCancellationException) {
                isReconnecting.set(false)
                _connectionState.value = ConnectionState.Error("Auth timeout", null)
                scheduleReconnect()
            } catch (e: Exception) {
                isReconnecting.set(false)
                _connectionState.value = ConnectionState.Error(
                    e.message ?: "Handshake failed", null
                )
                scheduleReconnect()
            } finally {
                pendingRequests.remove(connectId)
            }
        }
    }

    private fun handleChatEvent(envelope: FrameEnvelope) {
        val payload = envelope.payload ?: return
        val chatPayload = try {
            json.decodeFromJsonElement(ChatEventPayload.serializer(), payload)
        } catch (_: Exception) {
            return
        }

        val event = when (chatPayload.state) {
            "delta" -> GatewayEvent.ChatDelta(
                runId = chatPayload.runId,
                text = extractTextFromMessage(chatPayload.message),
                seq = chatPayload.seq,
            )
            "catchup" -> GatewayEvent.ChatCatchup(
                runId = chatPayload.runId,
                content = extractTextFromMessage(chatPayload.message),
                seq = chatPayload.seq,
            )
            "final" -> GatewayEvent.ChatFinal(
                runId = chatPayload.runId,
                text = extractTextFromMessage(chatPayload.message),
                stopReason = chatPayload.stopReason,
                contextPct = chatPayload.contextPct,
                contextWindow = chatPayload.contextWindow,
                contextTokens = chatPayload.contextTokens,
                inputTokens = chatPayload.inputTokens,
                outputTokens = chatPayload.outputTokens,
            )
            "aborted" -> GatewayEvent.ChatAborted(runId = chatPayload.runId)
            "error" -> GatewayEvent.ChatError(
                runId = chatPayload.runId,
                message = extractTextFromMessage(chatPayload.message),
            )
            else -> return
        }

        // Emit tool events from content blocks
        emitToolEvents(chatPayload)

        // Detect inject broadcasts: final events with no runId
        if (chatPayload.state == "final" && chatPayload.runId == null) {
            val text = extractTextFromMessage(chatPayload.message)
            if (text.isNotEmpty()) {
                scope.launch {
                    _events.emit(GatewayEvent.ChatInject(text, chatPayload.sessionKey))
                }
            }
            return
        }

        scope.launch { _events.emit(event) }
    }

    /**
     * Handle cc.chat events from Ironjaw CC session pipeline.
     * Converts cc.chat payloads into CcBridgeEvent subclasses for unified consumption.
     */
    private fun handleCcChatEvent(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val state = payloadObj["state"]?.jsonPrimitive?.contentOrNull ?: return
        val sessionId = payloadObj["sessionId"]?.jsonPrimitive?.contentOrNull ?: return

        val bridgeEvent: CcBridgeEvent = when (state) {
            "delta" -> {
                val text = payloadObj["message"]?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull ?: ""
                CcBridgeEvent.SessionStream(
                    sessionId = sessionId,
                    kind = "text_delta",
                    text = text,
                    data = payloadObj as JsonObject,
                )
            }
            "tool_use" -> {
                val toolName = payloadObj["tool"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                val input = payloadObj["input"]
                CcBridgeEvent.SessionStream(
                    sessionId = sessionId,
                    kind = "tool_use",
                    toolName = toolName,
                    input = input,
                    data = payloadObj as JsonObject,
                )
            }
            "tool_result" -> {
                val toolName = payloadObj["tool"]?.jsonPrimitive?.contentOrNull
                val output = payloadObj["output"]?.jsonPrimitive?.contentOrNull ?: ""
                CcBridgeEvent.SessionStream(
                    sessionId = sessionId,
                    kind = "tool_result",
                    toolName = toolName,
                    text = output,
                    data = payloadObj as JsonObject,
                )
            }
            "final" -> CcBridgeEvent.SessionResult(
                sessionId = sessionId,
                data = payloadObj as JsonObject,
            )
            "error" -> {
                val error = payloadObj["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                CcBridgeEvent.SessionError(
                    sessionId = sessionId,
                    error = error,
                    data = payloadObj as JsonObject,
                )
            }
            "started" -> CcBridgeEvent.SessionInit(
                sessionId = sessionId,
                data = payloadObj as JsonObject,
            )
            "session.resolved" -> {
                val trackingKey = payloadObj["trackingKey"]?.jsonPrimitive?.contentOrNull ?: sessionId
                CcBridgeEvent.SessionResolved(
                    trackingKey = trackingKey,
                    resolvedSessionId = sessionId,
                )
            }
            else -> return
        }

        scope.launch { _ccChatEvents.emit(bridgeEvent) }
    }

    /**
     * Handle cc.complete events — a CC session has finished processing.
     * Emitted by Ironjaw after stream_cc_events() completes.
     */
    private fun handleCcCompleteEvent(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val sessionId = payloadObj["sessionId"]?.jsonPrimitive?.contentOrNull ?: return
        val runId = payloadObj["runId"]?.jsonPrimitive?.contentOrNull

        scope.launch {
            _events.emit(GatewayEvent.CcComplete(sessionId = sessionId, runId = runId))
        }
    }

    private fun handleModelChanged(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val model = payloadObj["model"]?.jsonPrimitive?.contentOrNull ?: return
        val previous = payloadObj["previous"]?.jsonPrimitive?.contentOrNull
        val source = payloadObj["source"]?.jsonPrimitive?.contentOrNull

        scope.launch {
            _modelChangedFlow.emit(ModelChangedEvent(model = model, previous = previous, source = source))
        }
    }

    /**
     * Handle "ui" frames from Ironjaw — agent-generated HTML content.
     * Frame shape: { type: "ui", id, component, schema }
     * The schema.html field contains the HTML string; schema.url is an optional
     * URL to load instead. Routed to ccChatEvents as SessionCanvas for display
     * in the attached session chat.
     */
    private fun handleUiFrame(envelope: FrameEnvelope) {
        val payloadObj = envelope.payload?.jsonObject ?: return
        val schema = payloadObj["schema"]?.jsonObject ?: return
        val html = schema["html"]?.jsonPrimitive?.contentOrNull ?: ""
        val url = schema["url"]?.jsonPrimitive?.contentOrNull
        // Try to extract a session ID; fall back to current session key
        val sessionId = payloadObj["sessionId"]?.jsonPrimitive?.contentOrNull
            ?: sessionKey ?: return

        if (html.isBlank() && url == null) return

        scope.launch {
            _ccChatEvents.emit(
                CcBridgeEvent.SessionCanvas(
                    sessionId = sessionId,
                    html = html,
                    url = url,
                )
            )
        }
    }

    private fun emitToolEvents(chatPayload: ChatEventPayload) {
        val content = chatPayload.message?.content
        if (content !is JsonArray) return
        for (block in content.jsonArray) {
            val obj = block as? JsonObject ?: continue
            val blockType = obj["type"]?.jsonPrimitive?.contentOrNull ?: continue
            when (blockType) {
                "tool_use" -> {
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                    val input = obj["input"]?.toString() ?: "{}"
                    scope.launch {
                        _events.emit(GatewayEvent.ToolUse(chatPayload.runId, name, input))
                    }
                    // Emit Activity event with friendly summary
                    val summary = friendlyToolSummary(name, obj["input"])
                    scope.launch {
                        _events.emit(GatewayEvent.Activity(
                            runId = chatPayload.runId,
                            tool = name,
                            summary = summary,
                            timestamp = System.currentTimeMillis(),
                        ))
                    }
                }
                "tool_result" -> {
                    val resultContent = obj["content"]?.jsonPrimitive?.contentOrNull ?: obj["content"]?.toString() ?: ""
                    scope.launch {
                        _events.emit(GatewayEvent.ToolResult(chatPayload.runId, null, resultContent))
                    }
                }
            }
        }
    }

    private fun handleInjectEvent(envelope: FrameEnvelope) {
        val payload = envelope.payload ?: return
        val text = try {
            val obj = payload.jsonObject
            obj["text"]?.jsonPrimitive?.contentOrNull ?: return
        } catch (_: Exception) {
            return
        }
        val sessionKey = try {
            payload.jsonObject["sessionKey"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
        scope.launch { _events.emit(GatewayEvent.ChatInject(text, sessionKey)) }
    }

    /**
     * Extract plain text from a ChatMessage.
     * The gateway sends: {"role":"assistant","content":[{"type":"text","text":"..."}]}
     * Skips tool_use and tool_result blocks — only extracts text type.
     */
    private fun extractTextFromMessage(message: ChatMessage?): String {
        val content = message?.content ?: return ""
        return when (content) {
            is JsonArray -> content.jsonArray.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val blockType = obj["type"]?.jsonPrimitive?.contentOrNull
                if (blockType == "tool_use" || blockType == "tool_result") return@mapNotNull null
                obj["text"]?.jsonPrimitive?.contentOrNull
            }.joinToString("")
            is JsonObject -> content.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            else -> content.jsonPrimitive.contentOrNull ?: ""
        }
    }

    private fun handleTick(envelope: FrameEnvelope) {
        lastTickTime = System.currentTimeMillis()
        scope.launch { _events.emit(GatewayEvent.Tick(envelope.seq ?: 0)) }
    }



    private fun handleResponse(envelope: FrameEnvelope) {
        val id = envelope.id ?: return
        val response = ResponseFrame(
            id = id,
            ok = envelope.ok ?: false,
            payload = envelope.resolvedPayload,
            error = envelope.error,
        )
        pendingRequests[id]?.complete(response)
    }

    /**
     * Handle incoming "cmd" frames from Ironjaw.
     * Extracts tool + params, requires approval via [cmdApprovalGate] (fail closed if unset,
     * denied, or unanswered within [CMD_APPROVAL_TIMEOUT_MS]), then delegates to [cmdDispatcher]
     * and sends back a cmd.res frame with the result or error.
     */
    private fun handleCmd(envelope: FrameEnvelope) {
        val id = envelope.id ?: return
        val tool = envelope.tool
            ?: envelope.params?.jsonObject?.get("tool")?.jsonPrimitive?.contentOrNull
            ?: run {
                sendCmdError(id, "Missing tool field")
                return
            }
        val params = try {
            envelope.params?.jsonObject ?: JsonObject(emptyMap())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }

        val dispatcher = cmdDispatcher
        if (dispatcher == null) {
            sendCmdError(id, "No command dispatcher registered")
            return
        }

        val gate = cmdApprovalGate
        if (gate == null) {
            // Fail closed: without an approval gate wired up, no incoming command may execute.
            sendCmdError(id, "denied: no approval gate configured")
            return
        }

        scope.launch {
            try {
                val approved = try {
                    withTimeout(CMD_APPROVAL_TIMEOUT_MS) { gate.requestApproval(tool, params) }
                } catch (_: TimeoutCancellationException) {
                    false
                }
                if (!approved) {
                    sendCmdError(id, "denied by user")
                    return@launch
                }
                val result = dispatcher.dispatch(tool, params)
                sendCmdResponse(id, result)
            } catch (e: Exception) {
                sendCmdError(id, e.message ?: "Command execution failed")
            }
        }
    }

    private fun sendCmdResponse(id: String, data: kotlinx.serialization.json.JsonElement) {
        val frame = CmdResFrame(id = id, ok = true, data = data)
        val text = json.encodeToString(CmdResFrame.serializer(), frame)
        webSocket?.send(text)
    }

    private fun sendCmdError(id: String, message: String) {
        val frame = CmdResFrame(id = id, ok = false, error = message)
        val text = json.encodeToString(CmdResFrame.serializer(), frame)
        webSocket?.send(text)
    }

    private fun startTickWatchdog() {
        tickWatchdogJob?.cancel()
        tickWatchdogJob = scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                if (isReconnecting.get()) continue
                val elapsed = System.currentTimeMillis() - lastTickTime
                if (elapsed > tickIntervalMs * TICK_STALENESS_MULTIPLIER) {
                    // FIX #9: don't hard-cancel a possibly-alive-but-slow connection.
                    // A long local-model prefill can delay server ticks well past the
                    // staleness window while the socket is perfectly healthy. Probe with
                    // an app-level ping first; only cancel if it doesn't answer in time.
                    val alive = try {
                        sendRequest(newId(), "ping", buildJsonObject {}, PING_PROBE_TIMEOUT_MS).ok
                    } catch (_: Exception) {
                        false
                    }
                    if (alive) {
                        // Pong counts as liveness — reset the clock and keep watching.
                        lastTickTime = System.currentTimeMillis()
                        continue
                    }
                    webSocket?.cancel()
                    break
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (intentionalDisconnect) return
        // Terminal: the token was rejected. Retrying would loop forever with the same
        // bad token. Only an explicit connect()/reconnect()/reconnectWith() call resets this.
        if (_connectionState.value is ConnectionState.AuthFailed) return

        reconnectJob?.cancel()
        val isFastReconnect = System.currentTimeMillis() < fastReconnectUntil
        val base = if (isFastReconnect) {
            1000L
        } else {
            minOf(
                1000L * (1L shl minOf(reconnectAttempts, 4)),
                MAX_BACKOFF_MS,
            )
        }
        // FIX #10: add jitter (up to +50% of base, min 250ms spread) so simultaneous
        // drops or rapid retries don't reconnect in lockstep and hammer the gateway
        // (4 reconnects within 5s was observed with the fixed schedule).
        val delay = base + Random.nextLong((base / 2).coerceAtLeast(250L))
        reconnectAttempts++

        isReconnecting.set(true)
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.Error("Reconnecting...", delay)
            delay(delay)
            doConnect()
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun friendlyToolSummary(toolName: String, input: kotlinx.serialization.json.JsonElement?): String {
        val inputObj = input as? JsonObject
        return when (toolName) {
            "Read" -> {
                val fp = inputObj?.get("file_path")?.jsonPrimitive?.contentOrNull
                if (fp != null) "Reading ${fp.substringAfterLast('/')}" else "Reading file..."
            }
            "Edit" -> {
                val fp = inputObj?.get("file_path")?.jsonPrimitive?.contentOrNull
                if (fp != null) "Editing ${fp.substringAfterLast('/')}" else "Editing file..."
            }
            "Write" -> {
                val fp = inputObj?.get("file_path")?.jsonPrimitive?.contentOrNull
                if (fp != null) "Writing ${fp.substringAfterLast('/')}" else "Writing file..."
            }
            "Bash" -> {
                val cmd = inputObj?.get("command")?.jsonPrimitive?.contentOrNull
                if (cmd != null) "Running: ${cmd.take(40)}" else "Running command..."
            }
            "Grep" -> "Searching code..."
            "Glob" -> "Finding files..."
            "WebSearch" -> "Searching the web..."
            "WebFetch" -> "Fetching URL..."
            "Task" -> "Spawning sub-agent..."
            else -> "Using $toolName..."
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            lastTickTime = System.currentTimeMillis()
            // Protocol v4: send auth immediately on connection (no challenge wait)
            sendAuth()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleFrame(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            try {
                tickWatchdogJob?.cancel()
                pendingRequests.values.forEach { it.cancel() }
                pendingRequests.clear()
                _connectionState.value = ConnectionState.Error(
                    t.message ?: "Connection failed", null
                )
                scheduleReconnect()
            } catch (e: Exception) {
                // Last-resort: log and attempt reconnection even if cleanup failed
                _connectionState.value = ConnectionState.Error(
                    "onFailure handler error: ${e.message}", null
                )
                scheduleReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            tickWatchdogJob?.cancel()
            // Terminal: auth was rejected — the server closing the socket is expected.
            // Don't overwrite AuthFailed with Disconnected, and don't reconnect.
            if (_connectionState.value is ConnectionState.AuthFailed) return
            _connectionState.value = ConnectionState.Disconnected
            if (!intentionalDisconnect) {
                scheduleReconnect()
            }
        }
    }
}
