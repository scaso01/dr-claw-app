package com.scaso.drclawapp.data.ccbridge

import com.scaso.drclawapp.data.websocket.CcBridgeClient
import com.scaso.drclawapp.data.websocket.CcBridgeConnectionState
import com.scaso.drclawapp.data.websocket.CcBridgeEvent
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ErrorShape
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Dual-source repository for Claude Code Bridge sessions.
 *
 * - **v1 (historical):** fetches session lists via GatewayClient proxy
 *   (ccbridge.sessions RPC).
 * - **v2 (daemon):** connects directly to cc-bridge v2 via CcBridgeClient
 *   WebSocket for active session management, streaming, and control.
 *
 * No Android imports -- KMP-extractable.
 */
class CcBridgeRepository(
    private val gatewayClient: GatewayClient,
    private val localBridge: CcBridgeClient,
    private val phoneBridge: CcBridgeClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Maps each active session id → the bridge that owns it (built in [loadSessions]). */
    private val sessionOwner = ConcurrentHashMap<String, CcBridgeClient>()

    /** The owning bridge for a session id (default workstation), or null if it isn't connected. */
    private fun bridgeFor(id: String): CcBridgeClient? {
        val owner = sessionOwner[id] ?: localBridge
        return owner.takeIf { it.connectionState.value is CcBridgeConnectionState.Connected }
    }

    // ── v1: Historical sessions (via gateway proxy) ──────────────────

    private val _sessions = MutableStateFlow<List<CcSession>>(emptyList())
    val sessions: StateFlow<List<CcSession>> = _sessions

    // ── v2: Active daemon sessions (via direct WS) ──────────────────

    private val _activeSessions = MutableStateFlow<List<CcActiveSession>>(emptyList())
    val activeSessions: StateFlow<List<CcActiveSession>> = _activeSessions

    // ── Shared state ─────────────────────────────────────────────────

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * Combined connection state of both direct cc-bridge WebSockets.
     * Reports Connected if EITHER bridge is connected (so the UI shows "LIVE"
     * when at least one daemon is reachable); otherwise reflects workstation's state.
     */
    val bridgeConnectionState: StateFlow<CcBridgeConnectionState> = combine(
        localBridge.connectionState,
        phoneBridge.connectionState,
    ) { workstation, phone ->
        when {
            workstation is CcBridgeConnectionState.Connected -> workstation
            phone is CcBridgeConnectionState.Connected -> phone
            else -> workstation
        }
    }.stateIn(scope, SharingStarted.Eagerly, CcBridgeConnectionState.Disconnected)

    /** Raw events from BOTH cc-bridge daemons + Ironjaw cc.chat events (merged). */
    val bridgeEvents: SharedFlow<CcBridgeEvent> = merge(
        localBridge.events,
        phoneBridge.events,
        gatewayClient.ccChatEvents,
    ).shareIn(scope, SharingStarted.Eagerly)

    /** CC session completion events (fires when a cc.session.create or cc.chat finishes). */
    val ccCompleteEvents: SharedFlow<GatewayEvent.CcComplete> = gatewayClient.events
        .filterIsInstance<GatewayEvent.CcComplete>()
        .shareIn(scope, SharingStarted.Eagerly)

    // ── Bridge lifecycle ─────────────────────────────────────────────

    fun connectBridge() {
        localBridge.connect()
        phoneBridge.connect()
    }

    fun disconnectBridge() {
        localBridge.disconnect()
        phoneBridge.disconnect()
    }

    /** Whether EITHER direct cc-bridge WebSocket is connected. */
    fun isBridgeConnected(): Boolean =
        localBridge.connectionState.value is CcBridgeConnectionState.Connected ||
            phoneBridge.connectionState.value is CcBridgeConnectionState.Connected

    // ── Load sessions (both sources) ─────────────────────────────────

    /**
     * Fetch CC sessions from both sources:
     * 1. v1 historical sessions via gateway proxy (ccbridge.sessions)
     * 2. v2 active sessions via direct CcBridgeClient (sessions.list)
     */
    fun loadSessions() {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            // Fetch v1 historical sessions
            try {
                val response = gatewayClient.sendGenericRequest("ccbridge.sessions")
                if (response.ok && response.payload != null) {
                    val payloadStr = response.payload.toString()
                    // Guard against HTML error pages or non-JSON responses
                    if (payloadStr.trimStart().startsWith("<")) {
                        // HTML response — cc-bridge may be down or returning an error page
                    } else {
                        val result = json.decodeFromString(
                            CcSessionsResponse.serializer(),
                            payloadStr,
                        )
                        _sessions.value = result.sessions.sortedByDescending { it.lastActivity }
                    }
                }
            } catch (_: Exception) {
                // v1 failure is non-fatal; v2 may still succeed
            }

            // Fetch v2 active sessions from BOTH bridges (workstation + phone-local),
            // tagging each with its origin and recording the owning bridge for routing.
            val merged = mutableListOf<CcActiveSession>()
            sessionOwner.clear()
            collectActive(localBridge, Machine.LOCAL, merged)
            collectActive(phoneBridge, Machine.PHONE, merged)
            _activeSessions.value = merged

            // Only report error if both failed
            if (_sessions.value.isEmpty() && _activeSessions.value.isEmpty()) {
                _error.value = "Failed to load CC sessions from both sources"
            }

            _isLoading.value = false
        }
    }

    /**
     * Query one bridge's active sessions, tag them with [source], append to [into],
     * and record each id's owning bridge in [sessionOwner]. Disconnected bridges
     * return NOT_CONNECTED immediately and contribute nothing (graceful).
     */
    private suspend fun collectActive(
        bridge: CcBridgeClient,
        source: Machine,
        into: MutableList<CcActiveSession>,
    ) {
        try {
            val response = bridge.listSessions()
            if (response.ok && response.payload != null) {
                val result = json.decodeFromString(
                    CcActiveSessionsResponse.serializer(),
                    response.payload.toString(),
                )
                result.sessions.forEach { s ->
                    into += s.copy(source = source)
                    sessionOwner[s.id] = bridge
                }
            }
        } catch (_: Exception) {
            // Non-fatal — the other bridge may still succeed
        }
    }

    // ── v1: Gateway-proxied message send ─────────────────────────────

    /**
     * Send a message to a specific CC session via the gateway proxy.
     * Used for v1 (historical) sessions where the machine is known.
     */
    suspend fun sendMessageViaGateway(
        sessionId: String,
        machine: String,
        message: String,
    ): Boolean {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("machine", machine)
            put("message", message)
        }
        val response = gatewayClient.sendGenericRequest("ccbridge.chat", params)
        return response.ok
    }

    // ── v2: Session daemon operations ────────────────────────────────

    /** Create a new daemon-managed session on [target] (workstation or Phone). Requires that bridge connected. */
    suspend fun createSession(
        cwd: String,
        project: String? = null,
        backend: String = "cloud",
        model: String? = null,
        permissionMode: String = "bypassPermissions",
        target: Machine = Machine.LOCAL,
    ): ResponseFrame {
        val bridge = if (target == Machine.PHONE) phoneBridge else localBridge
        return if (bridge.connectionState.value is CcBridgeConnectionState.Connected) {
            bridge.createSession(cwd, project, backend, model, permissionMode)
        } else {
            ResponseFrame(
                id = "",
                ok = false,
                error = ErrorShape(
                    code = "NO_BRIDGE",
                    message = "${target.displayName} bridge not connected",
                ),
            )
        }
    }

    /**
     * Resume an existing CC session as a live daemon session.
     * When CcBridgeClient is disconnected, returns a synthetic success
     * with `"fallback": "ironjaw"` so the caller navigates to the
     * attached session view with backend="ironjaw". The user can then
     * send a message which starts an Ironjaw subprocess via cc.chat.
     */
    suspend fun resumeSession(
        sessionId: String,
        cwd: String,
        project: String? = null,
    ): ResponseFrame {
        // Historical sessions are workstation/docker-host (enumerated via the gateway proxy),
        // so resume routes to the workstation daemon when it is connected.
        if (localBridge.connectionState.value is CcBridgeConnectionState.Connected) {
            return localBridge.resumeSession(
                sessionId = sessionId,
                cwd = cwd,
                project = project,
            )
        }
        // Ironjaw fallback: navigate directly to attached session view.
        // No subprocess needed — user sends a message to start one via cc.chat.
        return ResponseFrame(
            id = "",
            ok = true,
            payload = buildJsonObject {
                put("id", sessionId)
                put("fallback", "ironjaw")
            },
        )
    }

    /** Attach to an active daemon session, routed to its owning bridge. Falls back to Ironjaw RPC. */
    suspend fun attachSession(id: String): ResponseFrame {
        return bridgeFor(id)?.attachSession(id) ?: attachIronjawSession(id)
    }

    /** Detach from a daemon session, routed to its owning bridge. Falls back to Ironjaw RPC. */
    suspend fun detachSession(id: String): ResponseFrame {
        return bridgeFor(id)?.detachSession(id) ?: detachIronjawSession(id)
    }

    /** Send a message to a daemon session, routed to its owning bridge. Falls back to Ironjaw RPC. */
    suspend fun sendMessage(sessionId: String, message: String): ResponseFrame {
        return bridgeFor(sessionId)?.sendMessage(sessionId, message)
            ?: sendToIronjawSession(sessionId, message)
    }

    /** Interrupt an active daemon session, routed to its owning bridge. Falls back to Ironjaw RPC. */
    suspend fun interruptSession(id: String): ResponseFrame {
        return bridgeFor(id)?.interruptSession(id) ?: destroyIronjawSession(id)
    }

    /** Destroy a daemon session, routed to its owning bridge. Falls back to Ironjaw RPC. */
    suspend fun destroySession(id: String, reason: String? = null): ResponseFrame {
        return bridgeFor(id)?.destroySession(id, reason) ?: destroyIronjawSession(id)
    }

    /** List local models available on the workstation daemon host. */
    suspend fun listLocalModels(): ResponseFrame {
        return localBridge.listLocalModels()
    }

    /**
     * Respond to a permission request, routed to the session's owning bridge.
     *
     * Only the cc-bridge daemon backend surfaces tool permissions. Ironjaw-managed
     * Claude CLI sessions spawn with `--dangerously-skip-permissions` and one-shot
     * stdin, so they auto-approve every tool call and never emit a permission
     * prompt — there is no Ironjaw permission-response path by design. When the
     * session has no live daemon bridge, fail loudly instead of silently no-oping.
     */
    suspend fun respondPermission(
        sessionId: String,
        requestId: String,
        allow: Boolean,
    ): ResponseFrame {
        return bridgeFor(sessionId)?.respondPermission(sessionId, requestId, allow)
            ?: ResponseFrame(
                id = requestId,
                ok = false,
                error = ErrorShape(
                    code = "NOT_DELIVERABLE",
                    message = "Permission response not deliverable: Ironjaw-managed CC sessions " +
                        "auto-approve (run with --dangerously-skip-permissions) and never raise " +
                        "permission prompts.",
                ),
            )
    }

    // ── CC History (via Ironjaw cc.history RPC) ─────────────────────────

    /** Load conversation history for a CC session via Ironjaw gateway. */
    suspend fun loadSessionHistory(sessionId: String, limit: Int = 9999): List<HistoryMessage> {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("limit", limit)
        }
        val response = gatewayClient.sendGenericRequest("cc.history", params)
        if (!response.ok || response.payload == null) return emptyList()
        return try {
            val payloadObj = response.payload.jsonObject
            val messages = payloadObj["messages"]?.jsonArray ?: return emptyList()
            messages.mapNotNull { elem ->
                val obj = elem.jsonObject
                HistoryMessage(
                    role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
                    entryType = obj["entry_type"]?.jsonPrimitive?.contentOrNull,
                    toolName = obj["tool_name"]?.jsonPrimitive?.contentOrNull,
                    entrypoint = obj["entrypoint"]?.jsonPrimitive?.contentOrNull,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetch only history messages added since [knownCount] via cc.history.since RPC.
     * Returns (newMessages, newTotalCount). Empty list if no new messages.
     */
    suspend fun checkHistoryUpdates(
        sessionId: String,
        knownCount: Int,
    ): Pair<List<HistoryMessage>, Int> {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("knownCount", knownCount)
        }
        val response = gatewayClient.sendGenericRequest("cc.history.since", params)
        if (!response.ok || response.payload == null) return Pair(emptyList(), knownCount)
        return try {
            val payloadObj = response.payload.jsonObject
            val totalCount = payloadObj["totalCount"]?.jsonPrimitive?.contentOrNull
                ?.toIntOrNull() ?: knownCount
            val messages = payloadObj["messages"]?.jsonArray ?: return Pair(emptyList(), totalCount)
            val parsed = messages.mapNotNull { elem ->
                val obj = elem.jsonObject
                HistoryMessage(
                    role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    content = obj["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis(),
                    entryType = obj["entry_type"]?.jsonPrimitive?.contentOrNull,
                    toolName = obj["tool_name"]?.jsonPrimitive?.contentOrNull,
                    entrypoint = obj["entrypoint"]?.jsonPrimitive?.contentOrNull,
                )
            }
            Pair(parsed, totalCount)
        } catch (_: Exception) {
            Pair(emptyList(), knownCount)
        }
    }

    // ── Mailbox (via Ironjaw agent.mailbox RPC) ──────────────────────

    private val _mailMessages = MutableStateFlow<List<MailMessage>>(emptyList())
    val mailMessages: StateFlow<List<MailMessage>> = _mailMessages

    /** Load mailbox messages via agent.mailbox.list RPC. */
    fun loadMailMessages() {
        scope.launch {
            try {
                val response = gatewayClient.sendGenericRequest("agent.mailbox.list")
                if (response.ok && response.payload != null) {
                    val messages = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(MailMessage.serializer()),
                        response.payload.toString(),
                    )
                    _mailMessages.value = messages.sortedByDescending { it.createdAt }
                }
            } catch (_: Exception) {
                // Non-fatal
            }
        }
    }

    // ── Ironjaw Claude session operations (gateway proxy) ──────────────

    private val _ironjawSessions = MutableStateFlow<List<ClaudeSession>>(emptyList())
    val ironjawSessions: StateFlow<List<ClaudeSession>> = _ironjawSessions

    /** List Ironjaw-managed Claude sessions via gateway cc.sessions RPC. */
    fun loadIronjawSessions() {
        scope.launch {
            try {
                val response = gatewayClient.sendGenericRequest("cc.sessions")
                if (response.ok && response.payload != null) {
                    val sessions = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(ClaudeSession.serializer()),
                        response.payload.toString(),
                    )
                    _ironjawSessions.value = sessions.sortedByDescending { it.lastActivity }
                }
            } catch (_: Exception) {
                // Non-fatal -- other sources may still succeed
            }
        }
    }

    /** Create a new Ironjaw-managed Claude session. */
    suspend fun createIronjawSession(
        message: String,
        cwd: String = ".",
    ): ResponseFrame {
        val params = buildJsonObject {
            put("message", message)
            put("cwd", cwd)
        }
        return gatewayClient.sendGenericRequest("cc.session.create", params)
    }

    /** Send a message to an Ironjaw-managed Claude session. */
    suspend fun sendToIronjawSession(
        sessionId: String,
        message: String,
        thinkingLevel: String? = null,
    ): ResponseFrame {
        val params = buildJsonObject {
            put("sessionId", sessionId)
            put("message", message)
            thinkingLevel?.let { put("thinkingLevel", it) }
        }
        return gatewayClient.sendGenericRequest("cc.chat", params)
    }

    /** Attach to an Ironjaw-managed Claude session. */
    suspend fun attachIronjawSession(sessionId: String): ResponseFrame {
        val params = buildJsonObject {
            put("sessionId", sessionId)
        }
        return gatewayClient.sendGenericRequest("cc.attach", params)
    }

    /** Detach from an Ironjaw-managed Claude session. */
    suspend fun detachIronjawSession(sessionId: String): ResponseFrame {
        val params = buildJsonObject {
            put("sessionId", sessionId)
        }
        return gatewayClient.sendGenericRequest("cc.detach", params)
    }

    /** Destroy/abort an Ironjaw-managed Claude session. */
    suspend fun destroyIronjawSession(sessionId: String): ResponseFrame {
        val params = buildJsonObject {
            put("sessionId", sessionId)
        }
        return gatewayClient.sendGenericRequest("cc.chat.abort", params)
    }

    /** Claim a mailbox message via Ironjaw agent.mailbox.claim RPC. */
    suspend fun claimMailMessage(messageId: String): ResponseFrame {
        val params = buildJsonObject {
            put("messageId", messageId)
        }
        return gatewayClient.sendGenericRequest("agent.mailbox.claim", params)
    }

    /** Fetch file snapshots for a given path via file.snapshots RPC. */
    suspend fun fetchSnapshots(path: String): ResponseFrame {
        val params = buildJsonObject {
            put("path", path)
        }
        return gatewayClient.sendGenericRequest("file.snapshots", params)
    }

    /** Restore a file to a specific snapshot via file.restore RPC. */
    suspend fun restoreSnapshot(path: String, timestamp: String): ResponseFrame {
        val params = buildJsonObject {
            put("path", path)
            put("timestamp", timestamp)
        }
        return gatewayClient.sendGenericRequest("file.restore", params)
    }
}

// ── Response models ──────────────────────────────────────────────────

@Serializable
data class CcActiveSessionsResponse(
    val sessions: List<CcActiveSession> = emptyList(),
)

@Serializable
data class LocalModelInfo(
    val id: String,
    val name: String? = null,
    val backend: String? = null,
    val loaded: Boolean = false,
)

@Serializable
data class LocalModelsResponse(
    val models: List<LocalModelInfo> = emptyList(),
)
