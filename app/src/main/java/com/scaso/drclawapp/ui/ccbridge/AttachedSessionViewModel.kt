package com.scaso.drclawapp.ui.ccbridge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.ccbridge.CcBridgeRepository
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.websocket.CcBridgeConnectionState
import com.scaso.drclawapp.data.websocket.CcBridgeEvent
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import com.scaso.drclawapp.data.ccbridge.ClaudeSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

// ── Message types for the attached session chat ──────────────────────

sealed class SessionMessage {
    abstract val id: String
    abstract val timestamp: Long
    /** Source: "cli" (terminal), "sdk-cli" (Dr. CLAW), null (live/unknown). */
    open val source: String? = null

    data class UserMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        override val source: String? = null,
    ) : SessionMessage()

    data class AssistantMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val isStreaming: Boolean = false,
        override val source: String? = null,
    ) : SessionMessage()

    data class ToolUseMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val toolName: String,
        val input: JsonElement? = null,
    ) : SessionMessage()

    data class ToolResultMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val toolName: String,
        val output: String,
    ) : SessionMessage()

    data class ThinkingMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val isStreaming: Boolean = false,
    ) : SessionMessage()

    data class ErrorMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val error: String,
    ) : SessionMessage()

    data class CanvasMessage(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val html: String,
        val url: String? = null,
    ) : SessionMessage()
}

// ── Permission request model ─────────────────────────────────────────

data class PermissionRequest(
    val sessionId: String,
    val requestId: String,
    val toolName: String,
    val input: JsonElement? = null,
)

// ── Session activity status ──────────────────────────────────────────

enum class SessionStatus {
    IDLE,
    STREAMING,
    TOOL_EXECUTING,
    SPAWNING,
    WAITING_PERMISSION,
    ERROR,
    CLOSED,
    ;

    companion object {
        fun fromString(value: String): SessionStatus = when (value.lowercase()) {
            "idle" -> IDLE
            "streaming" -> STREAMING
            "tool_executing" -> TOOL_EXECUTING
            "spawning" -> SPAWNING
            "waiting_permission" -> WAITING_PERMISSION
            "error" -> ERROR
            "closed" -> CLOSED
            else -> IDLE
        }
    }
}

@HiltViewModel
class AttachedSessionViewModel @Inject constructor(
    private val repository: CcBridgeRepository,
    private val appPreferences: AppPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "AttachedSessionVM"
        private const val MAX_RAW_EVENTS = 500
    }

    private var _sessionId: String = savedStateHandle["sessionId"] ?: ""
    val sessionId: String get() = _sessionId
    private val backend: String = savedStateHandle.get<String>("backend") ?: "daemon"

    private val _messages = MutableStateFlow<List<SessionMessage>>(emptyList())
    val messages: StateFlow<List<SessionMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow(SessionStatus.IDLE)
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    val isStreaming: StateFlow<Boolean> = _status
        .map { it == SessionStatus.STREAMING || it == SessionStatus.TOOL_EXECUTING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _isAttached = MutableStateFlow(false)
    val isAttached: StateFlow<Boolean> = _isAttached.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _rawEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    val rawEvents: StateFlow<List<RawEvent>> = _rawEvents.asStateFlow()

    private val _permissionRequest = MutableStateFlow<PermissionRequest?>(null)
    val permissionRequest: StateFlow<PermissionRequest?> = _permissionRequest.asStateFlow()

    // Active-in-terminal detection (CC is running this session interactively)
    private val _activeInTerminal = MutableStateFlow(false)
    val activeInTerminal: StateFlow<Boolean> = _activeInTerminal.asStateFlow()

    // Cost/token tracking for Ironjaw sessions
    private val _totalCostUsd = MutableStateFlow(0.0)
    val totalCostUsd: StateFlow<Double> = _totalCostUsd.asStateFlow()

    private val _totalTokens = MutableStateFlow(0L)
    val totalTokens: StateFlow<Long> = _totalTokens.asStateFlow()

    // Context % tracking for daemon sessions
    private val _contextPct = MutableStateFlow<Float?>(null)
    val contextPct: StateFlow<Float?> = _contextPct.asStateFlow()

    private var costPollJob: Job? = null
    private var historySyncJob: Job? = null
    private val costJson = Json { ignoreUnknownKeys = true }

    // History loading state
    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _historicalMessageCount = MutableStateFlow(0)
    val historicalMessageCount: StateFlow<Int> = _historicalMessageCount.asStateFlow()

    // Reconnection state
    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()
    private var wasAttached = false

    // Global collapse toggle for tool outputs (default: collapsed)
    private val _toolOutputsCollapsed = MutableStateFlow(true)
    val toolOutputsCollapsed: StateFlow<Boolean> = _toolOutputsCollapsed.asStateFlow()

    fun toggleToolOutputsCollapsed() {
        _toolOutputsCollapsed.value = !_toolOutputsCollapsed.value
    }

    // Streaming text buffer -- accumulated text_delta fragments
    private val textBuffer = StringBuilder()

    // ID of the currently streaming assistant message (for in-place updates)
    private var streamingMessageId: String? = null

    // Thinking buffer for extended thinking display
    private val thinkingBuffer = StringBuilder()
    private var thinkingMessageId: String? = null

    // Content-hash dedup: tracks messages added via live streaming/send to prevent sync re-adding them
    private val liveMessageContentKeys = mutableSetOf<String>()

    // Post-stream cooldown: sync poll skips for 10s after streaming ends
    private var lastStreamEndTime = 0L

    init {
        attachToSession()
        collectBridgeEvents()
        startCostPolling()
        startHistorySync()
    }

    /**
     * Periodically polls session info to update cost/token/context data.
     * Ironjaw sessions: polls ironjawSessions for cost/tokens.
     * Daemon sessions: polls sessions list for contextPct.
     */
    private fun startCostPolling() {
        costPollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    // Ironjaw sessions: cost/tokens (works on phone + LAN)
                    repository.ironjawSessions.value
                        .find { it.ccBridgeId == sessionId }
                        ?.let {
                            _totalCostUsd.value = it.totalCostUsd
                            _totalTokens.value = it.totalTokens
                        }
                    // Daemon sessions: contextPct (LAN only, no-op on phone)
                    repository.sessions.value
                        .find { it.sessionId == sessionId }
                        ?.let { _contextPct.value = it.contextPct }
                } catch (_: Exception) {
                    // Non-fatal
                }
                delay(10_000L)
            }
        }
    }

    /**
     * Polls cc.history.since every 5s to pick up messages written by the
     * CC terminal session (bidirectional sync: CC → Dr. CLAW).
     * Only active while attached and idle (pauses during streaming).
     */
    private fun startHistorySync() {
        historySyncJob = viewModelScope.launch {
            // Wait for initial history load to complete
            while (isActive && _isLoadingHistory.value) delay(500L)
            while (isActive) {
                delay(5_000L)
                if (!_isAttached.value) continue
                if (_status.value != SessionStatus.IDLE) continue
                // Post-stream cooldown: skip sync for 10s after streaming ends
                if (System.currentTimeMillis() - lastStreamEndTime < 10_000L) continue
                try {
                    val known = _historicalMessageCount.value
                    val (newMessages, newTotal) = repository.checkHistoryUpdates(sessionId, known)
                    // Filter: skip own messages (sdk-cli), empty content, and content already shown live
                    val externalMessages = newMessages.filter { msg ->
                        val contentKey = "${msg.role}:${msg.content.trim().hashCode()}"
                        msg.content.isNotBlank() &&
                            msg.entrypoint != "sdk-cli" &&
                            contentKey !in liveMessageContentKeys
                    }
                    if (externalMessages.isNotEmpty()) {
                        val converted = externalMessages.mapIndexed { i, msg ->
                            val idx = known + i
                            when {
                                msg.role == "user" -> SessionMessage.UserMessage(
                                    id = "sync-$idx",
                                    timestamp = msg.timestamp,
                                    text = msg.content,
                                    source = msg.entrypoint,
                                )
                                msg.entryType == "tool_use" -> SessionMessage.ToolUseMessage(
                                    id = "sync-$idx",
                                    timestamp = msg.timestamp,
                                    toolName = msg.toolName ?: "unknown",
                                    input = null,
                                )
                                msg.entryType == "tool_result" -> SessionMessage.ToolResultMessage(
                                    id = "sync-$idx",
                                    timestamp = msg.timestamp,
                                    toolName = msg.toolName ?: "unknown",
                                    output = msg.content,
                                )
                                else -> SessionMessage.AssistantMessage(
                                    id = "sync-$idx",
                                    timestamp = msg.timestamp,
                                    text = msg.content,
                                    isStreaming = false,
                                    source = msg.entrypoint,
                                )
                            }
                        }
                        _messages.value = (_messages.value + converted).sortedBy { it.timestamp }
                        _historicalMessageCount.value = newTotal
                        Log.d(TAG, "History sync: +${externalMessages.size} external of ${newMessages.size} new (total=$newTotal)")
                    } else if (newMessages.isNotEmpty()) {
                        // Still advance the count past sdk-cli entries we skipped
                        _historicalMessageCount.value = newTotal
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "History sync failed", e)
                }
            }
        }
    }

    /** Force-refresh full history (e.g., on screen resume). */
    fun refreshHistory() {
        viewModelScope.launch {
            if (_isLoadingHistory.value) return@launch
            _messages.value = emptyList()
            _historicalMessageCount.value = 0
            liveMessageContentKeys.clear()
            loadHistory()
        }
    }

    // ── Public actions ────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        // Add user message to list and track for dedup
        val userMsg = SessionMessage.UserMessage(text = text.trim(), source = "sdk-cli")
        _messages.value = _messages.value + userMsg
        liveMessageContentKeys.add("user:${text.trim().hashCode()}")

        // Send via repository -- route based on backend
        viewModelScope.launch {
            try {
                val response = if (backend == "ironjaw") {
                    val thinkingLevel = appPreferences.thinkingLevel.first()
                    repository.sendToIronjawSession(sessionId, text.trim(), thinkingLevel)
                } else {
                    repository.sendMessage(sessionId, text.trim())
                }
                // Check if CC is active in terminal for this session
                try {
                    val active = response.payload?.jsonObject
                        ?.get("activeInTerminal")?.jsonPrimitive?.contentOrNull
                    if (active == "true") _activeInTerminal.value = true
                } catch (_: Exception) { /* non-fatal */ }

                if (!response.ok) {
                    val errorText = try {
                        response.payload?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                        ?: response.error?.message
                        ?: "Failed to send message"
                    _messages.value = _messages.value +
                        SessionMessage.ErrorMessage(error = errorText)
                }
            } catch (e: Exception) {
                _messages.value = _messages.value +
                    SessionMessage.ErrorMessage(error = e.message ?: "Send failed")
            }
        }
    }

    fun interrupt() {
        viewModelScope.launch {
            try {
                if (backend == "ironjaw") {
                    repository.destroyIronjawSession(sessionId)
                } else {
                    repository.interruptSession(sessionId)
                }
            } catch (_: Exception) {
                // Best-effort interrupt
            }
        }
    }

    fun detach() {
        viewModelScope.launch {
            try {
                if (backend == "ironjaw") {
                    repository.detachIronjawSession(sessionId)
                } else {
                    repository.detachSession(sessionId)
                }
                _isAttached.value = false
            } catch (_: Exception) {
                // Best-effort detach
            }
        }
    }

    fun respondPermission(requestId: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                // Ironjaw CC sessions auto-approve (--dangerously-skip-permissions) — no
                // permission response is deliverable for them; the repository fails loudly.
                val response = repository.respondPermission(sessionId, requestId, allow)
                if (!response.ok) {
                    val msg = try {
                        response.payload?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                        ?: response.error?.message
                        ?: "Permission response failed"
                    _error.value = msg
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Permission response failed"
            }
            _permissionRequest.value = null
        }
    }

    private fun reattachAfterReconnect() {
        viewModelScope.launch {
            try {
                val response = if (backend == "ironjaw") {
                    repository.attachIronjawSession(sessionId)
                } else {
                    repository.attachSession(sessionId)
                }
                if (response.ok) {
                    _isAttached.value = true
                    _isReconnecting.value = false
                    _status.value = SessionStatus.IDLE
                    Log.i(TAG, "Re-attached after reconnect")
                } else {
                    _isReconnecting.value = false
                    val msg = try {
                        response.payload?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                        ?: response.error?.message
                        ?: "Session ended while disconnected"
                    _messages.value = _messages.value +
                        SessionMessage.ErrorMessage(error = msg)
                    _status.value = SessionStatus.CLOSED
                }
            } catch (e: Exception) {
                _isReconnecting.value = false
                _messages.value = _messages.value +
                    SessionMessage.ErrorMessage(error = "Reconnect failed: ${e.message}")
                _status.value = SessionStatus.ERROR
            }
        }
    }

    // ── File snapshots ──────────────────────────────────────────────────

    private val _snapshots = MutableStateFlow<List<com.scaso.drclawapp.data.filedownload.FileSnapshot>>(emptyList())
    val snapshots: StateFlow<List<com.scaso.drclawapp.data.filedownload.FileSnapshot>> = _snapshots.asStateFlow()

    private val _snapshotPath = MutableStateFlow<String?>(null)
    val snapshotPath: StateFlow<String?> = _snapshotPath.asStateFlow()

    fun loadSnapshots(path: String) {
        _snapshotPath.value = path
        viewModelScope.launch {
            try {
                val response = repository.fetchSnapshots(path)
                if (response.ok && response.payload != null) {
                    val result = costJson.decodeFromJsonElement(
                        com.scaso.drclawapp.data.filedownload.FileSnapshotListResult.serializer(),
                        response.payload!!,
                    )
                    _snapshots.value = result.snapshots
                } else {
                    _snapshots.value = emptyList()
                }
            } catch (_: Exception) {
                _snapshots.value = emptyList()
            }
        }
    }

    fun restoreSnapshot(snapshot: com.scaso.drclawapp.data.filedownload.FileSnapshot) {
        val path = _snapshotPath.value ?: return
        viewModelScope.launch {
            try {
                repository.restoreSnapshot(path, snapshot.timestamp)
            } catch (_: Exception) {
                // best-effort
            }
        }
    }

    fun dismissSnapshots() {
        _snapshotPath.value = null
        _snapshots.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        costPollJob?.cancel()
        historySyncJob?.cancel()
        // Detach when ViewModel is destroyed (navigating away)
        viewModelScope.launch {
            try {
                if (backend == "ironjaw") {
                    repository.detachIronjawSession(sessionId)
                } else {
                    repository.detachSession(sessionId)
                }
            } catch (_: Exception) {
                // Ignore -- ViewModel is being destroyed
            }
        }
    }

    // ── Private: Attach to session ────────────────────────────────────

    private fun attachToSession() {
        viewModelScope.launch {
            _status.value = SessionStatus.SPAWNING
            try {
                val response = if (backend == "ironjaw") {
                    repository.attachIronjawSession(sessionId)
                } else {
                    repository.attachSession(sessionId)
                }
                if (response.ok) {
                    _isAttached.value = true
                    _status.value = SessionStatus.IDLE
                    loadHistory()
                } else {
                    val msg = try {
                        response.payload?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                        ?: response.error?.message
                        ?: "Failed to attach"

                    // "session not found" means no running subprocess — not a fatal error.
                    // Show history and enable input so the user can send a message
                    // to start a subprocess via cc.chat.
                    if (msg.contains("not found", ignoreCase = true)) {
                        _isAttached.value = true
                        _status.value = SessionStatus.IDLE
                        loadHistory()
                    } else {
                        _error.value = msg
                        _status.value = SessionStatus.ERROR
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Attach failed"
                _status.value = SessionStatus.ERROR
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _isLoadingHistory.value = true
            try {
                val history = repository.loadSessionHistory(sessionId)
                val historyMessages = history
                    .filter { it.content.isNotBlank() || it.entryType in listOf("tool_use", "tool_result") }
                    .mapIndexed { i, msg ->
                    when {
                        msg.role == "user" -> SessionMessage.UserMessage(
                            id = "hist-$i",
                            timestamp = msg.timestamp,
                            text = msg.content,
                            source = msg.entrypoint,
                        )
                        msg.entryType == "tool_use" -> SessionMessage.ToolUseMessage(
                            id = "hist-$i",
                            timestamp = msg.timestamp,
                            toolName = msg.toolName ?: "unknown",
                            input = null,
                        )
                        msg.entryType == "tool_result" -> SessionMessage.ToolResultMessage(
                            id = "hist-$i",
                            timestamp = msg.timestamp,
                            toolName = msg.toolName ?: "unknown",
                            output = msg.content,
                        )
                        else -> SessionMessage.AssistantMessage(
                            id = "hist-$i",
                            timestamp = msg.timestamp,
                            text = msg.content,
                            isStreaming = false,
                            source = msg.entrypoint,
                        )
                    }
                }
                _historicalMessageCount.value = historyMessages.size
                _messages.value = historyMessages + _messages.value
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load history", e)
            } finally {
                _isLoadingHistory.value = false
            }
        }
    }

    // ── Private: Event collection ─────────────────────────────────────

    private fun collectBridgeEvents() {
        viewModelScope.launch {
            repository.bridgeEvents.collect { event ->
                // Record raw event for debugging view
                recordRawEvent(event)

                // Only process events for our session
                when (event) {
                    is CcBridgeEvent.SessionStream -> {
                        if (event.sessionId != sessionId) return@collect
                        handleStream(event)
                    }

                    is CcBridgeEvent.SessionResult -> {
                        if (event.sessionId != sessionId) return@collect
                        handleResult()
                    }

                    is CcBridgeEvent.SessionStatus -> {
                        if (event.sessionId != sessionId) return@collect
                        handleStatusChange(event.status)
                    }

                    is CcBridgeEvent.SessionError -> {
                        if (event.sessionId != sessionId) return@collect
                        handleError(event.error)
                    }

                    is CcBridgeEvent.SessionClosed -> {
                        if (event.sessionId != sessionId) return@collect
                        handleClosed(event.reason)
                    }

                    is CcBridgeEvent.SessionInit -> {
                        if (event.sessionId != sessionId) return@collect
                        _status.value = SessionStatus.IDLE
                    }

                    is CcBridgeEvent.SessionAssistant -> {
                        // Full assistant block -- typically handled via stream+result
                        if (event.sessionId != sessionId) return@collect
                    }

                    is CcBridgeEvent.SessionPermission -> {
                        if (event.sessionId != sessionId) return@collect
                        _status.value = SessionStatus.WAITING_PERMISSION
                        _permissionRequest.value = PermissionRequest(
                            sessionId = event.sessionId,
                            requestId = event.requestId,
                            toolName = event.toolName,
                            input = event.input,
                        )
                    }

                    is CcBridgeEvent.SessionCanvas -> {
                        if (event.sessionId != sessionId) return@collect
                        val canvasMsg = SessionMessage.CanvasMessage(
                            html = event.html,
                            url = event.url,
                        )
                        _messages.value = _messages.value + canvasMsg
                    }

                    is CcBridgeEvent.SessionResolved -> {
                        if (event.trackingKey != sessionId) return@collect
                        Log.i(TAG, "Session resolved: ${event.trackingKey} → ${event.resolvedSessionId}")
                        _sessionId = event.resolvedSessionId
                    }

                    is CcBridgeEvent.ConnectionChanged -> {
                        when (event.state) {
                            is CcBridgeConnectionState.Disconnected,
                            is CcBridgeConnectionState.Error -> {
                                if (_isAttached.value) {
                                    wasAttached = true
                                    _isReconnecting.value = true
                                }
                            }
                            is CcBridgeConnectionState.Connected -> {
                                if (wasAttached) {
                                    wasAttached = false
                                    Log.i(TAG, "Reconnected — re-attaching to session $sessionId")
                                    reattachAfterReconnect()
                                }
                            }
                            else -> { /* Connecting/Authenticating — no action */ }
                        }
                    }
                }
            }
        }
    }

    /**
     * Append a bridge event to the raw events list for debugging.
     * Capped at 500 entries -- oldest events are dropped.
     */
    private fun recordRawEvent(event: CcBridgeEvent) {
        val (type, sid, data) = when (event) {
            is CcBridgeEvent.SessionStream ->
                Triple("stream.${event.kind}", event.sessionId, event.text ?: event.toolName ?: "")
            is CcBridgeEvent.SessionResult ->
                Triple("result", event.sessionId, "")
            is CcBridgeEvent.SessionStatus ->
                Triple("status", event.sessionId, event.status)
            is CcBridgeEvent.SessionError ->
                Triple("error", event.sessionId, event.error)
            is CcBridgeEvent.SessionClosed ->
                Triple("closed", event.sessionId, event.reason ?: "")
            is CcBridgeEvent.SessionInit ->
                Triple("init", event.sessionId, "")
            is CcBridgeEvent.SessionAssistant ->
                Triple("assistant", event.sessionId, "")
            is CcBridgeEvent.SessionPermission ->
                Triple("permission", event.sessionId, event.toolName)
            is CcBridgeEvent.SessionCanvas ->
                Triple("canvas", event.sessionId, event.url ?: "inline html (${event.html.length} chars)")
            is CcBridgeEvent.SessionResolved ->
                Triple("resolved", event.trackingKey, "${event.trackingKey} → ${event.resolvedSessionId}")
            is CcBridgeEvent.ConnectionChanged ->
                Triple("connection", "", event.state.toString())
        }

        val rawEvent = RawEvent(
            timestamp = System.currentTimeMillis(),
            type = type,
            sessionId = sid,
            data = data,
        )

        val current = _rawEvents.value
        _rawEvents.value = if (current.size >= MAX_RAW_EVENTS) {
            current.drop(1) + rawEvent
        } else {
            current + rawEvent
        }
    }

    // ── Private: Event handlers ───────────────────────────────────────

    private fun handleStream(event: CcBridgeEvent.SessionStream) {
        when (event.kind) {
            "text_delta" -> {
                val delta = event.text ?: return

                // Finalize any in-progress thinking message before text
                finalizeThinkingMessage()

                textBuffer.append(delta)

                val currentId = streamingMessageId
                if (currentId != null) {
                    // Update existing streaming message in-place
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == currentId && msg is SessionMessage.AssistantMessage) {
                            msg.copy(text = textBuffer.toString(), isStreaming = true)
                        } else {
                            msg
                        }
                    }
                } else {
                    // Create new streaming message
                    val newMsg = SessionMessage.AssistantMessage(
                        text = textBuffer.toString(),
                        isStreaming = true,
                        source = "sdk-cli",
                    )
                    streamingMessageId = newMsg.id
                    _messages.value = _messages.value + newMsg
                }
                _status.value = SessionStatus.STREAMING
            }

            "tool_use" -> {
                // Finalize any in-progress text or thinking before showing tool use
                finalizeStreamingMessage()
                finalizeThinkingMessage()

                val toolMsg = SessionMessage.ToolUseMessage(
                    toolName = event.toolName ?: "unknown",
                    input = event.input,
                )
                _messages.value = _messages.value + toolMsg
                _status.value = SessionStatus.TOOL_EXECUTING
            }

            "tool_result" -> {
                // Tool finished -- create a ToolResultMessage with the output
                val toolMsg = SessionMessage.ToolResultMessage(
                    toolName = event.toolName ?: "unknown",
                    output = event.text ?: "",
                )
                _messages.value = _messages.value + toolMsg
                _status.value = SessionStatus.STREAMING
            }

            "thinking" -> {
                // Thinking deltas -- display as a separate ThinkingMessage
                val delta = event.text ?: return
                thinkingBuffer.append(delta)

                val currentId = thinkingMessageId
                if (currentId != null) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == currentId && msg is SessionMessage.ThinkingMessage) {
                            msg.copy(text = thinkingBuffer.toString(), isStreaming = true)
                        } else {
                            msg
                        }
                    }
                } else {
                    val newMsg = SessionMessage.ThinkingMessage(
                        text = thinkingBuffer.toString(),
                        isStreaming = true,
                    )
                    thinkingMessageId = newMsg.id
                    _messages.value = _messages.value + newMsg
                }
            }
        }
    }

    private fun handleResult() {
        finalizeThinkingMessage()
        // Track assistant response content for dedup before finalizing
        if (textBuffer.isNotEmpty()) {
            liveMessageContentKeys.add("assistant:${textBuffer.toString().trim().hashCode()}")
        }
        finalizeStreamingMessage()
        _status.value = SessionStatus.IDLE
        lastStreamEndTime = System.currentTimeMillis()
    }

    private fun handleStatusChange(status: String) {
        _status.value = SessionStatus.fromString(status)
    }

    /**
     * Finalize the current thinking message -- mark it as
     * no longer streaming and reset the thinking buffer.
     */
    private fun finalizeThinkingMessage() {
        val currentId = thinkingMessageId ?: return
        if (thinkingBuffer.isNotEmpty()) {
            _messages.value = _messages.value.map { msg ->
                if (msg.id == currentId && msg is SessionMessage.ThinkingMessage) {
                    msg.copy(text = thinkingBuffer.toString(), isStreaming = false)
                } else {
                    msg
                }
            }
        }
        thinkingBuffer.clear()
        thinkingMessageId = null
    }

    private fun handleError(error: String) {
        finalizeThinkingMessage()
        finalizeStreamingMessage()
        _messages.value = _messages.value + SessionMessage.ErrorMessage(error = error)
        _status.value = SessionStatus.ERROR
    }

    private fun handleClosed(reason: String?) {
        finalizeThinkingMessage()
        finalizeStreamingMessage()
        _isAttached.value = false
        _status.value = SessionStatus.CLOSED
        if (reason != null) {
            _messages.value = _messages.value +
                SessionMessage.ErrorMessage(error = "Session closed: $reason")
        }
    }

    /**
     * Finalize the current streaming assistant message -- mark it as
     * no longer streaming and reset the text buffer for the next turn.
     */
    private fun finalizeStreamingMessage() {
        val currentId = streamingMessageId ?: return
        if (textBuffer.isNotEmpty()) {
            _messages.value = _messages.value.map { msg ->
                if (msg.id == currentId && msg is SessionMessage.AssistantMessage) {
                    msg.copy(text = textBuffer.toString(), isStreaming = false)
                } else {
                    msg
                }
            }
        }
        textBuffer.clear()
        streamingMessageId = null
    }
}
