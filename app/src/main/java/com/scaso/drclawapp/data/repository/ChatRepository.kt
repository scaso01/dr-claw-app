package com.scaso.drclawapp.data.repository

import com.scaso.drclawapp.data.model.AckState
import com.scaso.drclawapp.data.model.SyncState
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.entity.MessageEntity
import com.scaso.drclawapp.data.local.entity.SyncState as EntitySyncState
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.MessageType
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.websocket.RpcAttachment
import com.scaso.drclawapp.data.websocket.CommandResult
import com.scaso.drclawapp.data.websocket.CommandSpec
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.ChatHistoryResponse
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.HistoryEntry
import com.scaso.drclawapp.data.websocket.PromptStatsResult
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.scaso.drclawapp.data.local.dao.ToolEventDao
import com.scaso.drclawapp.data.local.entity.ToolEventEntity
import com.scaso.drclawapp.data.model.ToolEvent
import com.scaso.drclawapp.data.model.ToolStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Single source of truth for chat state. Translates raw gateway events
 * into a list of [Message] objects for the UI.
 *
 * No Android imports — KMP-extractable.
 */
class ChatRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
    private val messageDao: MessageDao? = null,
    private val toolEventDao: ToolEventDao? = null,
) {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val MAX_MESSAGES = 500

    val connectionState: StateFlow<ConnectionState> = gatewayClient.connectionState

    /** Raw gateway event stream, passed through for ViewModel-level UI state machines. */
    val events: SharedFlow<GatewayEvent> = gatewayClient.events

    /** Phase 13: native-chat permission requests for ViewModel-level HITL dialogs. */
    private val _nativePermissionFlow = MutableSharedFlow<GatewayEvent.NativePermissionRequest>(extraBufferCapacity = 8)
    val nativePermissionFlow: SharedFlow<GatewayEvent.NativePermissionRequest> = _nativePermissionFlow.asSharedFlow()

    private var currentRunId: String? = null
    private var autoContinueCount = 0
    private val MAX_AUTO_CONTINUES = 3

    /** Incremented on every session switch to reject stale streaming events. */
    private var sessionGeneration = 0L

    /** Set by SessionRepository to enable Room persistence. */
    var currentSessionKey: String? = null

    /** Set by ViewModel to tag messages with model name. */
    var currentModelName: String? = null

    init {
        scope.launch {
            gatewayClient.events.collect { event ->
                when (event) {
                    is GatewayEvent.ChatDelta -> handleDelta(event)
                    is GatewayEvent.ChatCatchup -> handleCatchup(event)
                    is GatewayEvent.ChatFinal -> handleFinal(event)
                    is GatewayEvent.ChatAborted -> handleAborted(event)
                    is GatewayEvent.ChatError -> handleError(event)
                    is GatewayEvent.HistoryResult -> handleHistory(event)
                    is GatewayEvent.ToolUse -> handleToolUse(event)
                    is GatewayEvent.ToolResult -> handleToolResult(event)
                    is GatewayEvent.ChatInject -> handleInject(event)
                    is GatewayEvent.Activity -> { /* ViewModel handles activity display */ }
                    is GatewayEvent.Connected -> { /* ViewModel handles connect logic */ }
                    is GatewayEvent.Tick -> { /* Heartbeat — no action */ }
                    is GatewayEvent.PermissionRequest -> { /* ViewModel handles HITL dialog */ }
                    is GatewayEvent.CcComplete -> { /* ViewModel handles completion notification */ }
                    // Phase 13: native-chat tool events
                    is GatewayEvent.NativeToolStart -> handleNativeToolStart(event)
                    is GatewayEvent.NativeToolResult -> handleNativeToolResult(event)
                    is GatewayEvent.NativePermissionRequest -> handleNativePermissionRequest(event)
                    is GatewayEvent.StreamPhaseChange -> { /* Phase 14 will wire up phase transitions */ }
                    is GatewayEvent.StreamDone -> { /* Phase 14 will handle usage reporting */ }
                }
            }
        }
    }

    private fun trimMessages() {
        _messages.update { msgs ->
            if (msgs.size > MAX_MESSAGES) msgs.takeLast(MAX_MESSAGES) else msgs
        }
    }

    fun connect() = gatewayClient.connect()

    fun disconnect() = gatewayClient.disconnect()

    /** Sends `gateway.restart`. Caller (ViewModel) wraps the raw frame into a UI-facing Result. */
    suspend fun restartGateway(): ResponseFrame = gatewayClient.sendGenericRequest("gateway.restart")

    /**
     * Post-reconnect health verification via the `gateway.model` RPC.
     * Returns false (rather than throwing) on timeout or any RPC failure.
     */
    /**
     * Fetches the system-prompt token cost breakdown (`prompt.stats`):
     * per-layer estimated tokens (identity, memories, context vars, …).
     * Returns null on any failure — the sheet simply omits the section.
     */
    suspend fun fetchPromptStats(): PromptStatsResult? {
        return try {
            val response = gatewayClient.sendGenericRequest("prompt.stats")
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement<PromptStatsResult>(response.payload)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun verifyGatewayHealth(timeoutMs: Long = 10_000L): Boolean {
        return try {
            withTimeout(timeoutMs) { gatewayClient.sendGenericRequest("gateway.model") }.ok
        } catch (_: TimeoutCancellationException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /** B1: server-driven slash command list. */
    suspend fun listSlashCommands(): List<CommandSpec> = gatewayClient.commandsList()

    /** B1: execute a server-driven slash command by name (no leading slash). */
    suspend fun executeSlashCommand(name: String): CommandResult = gatewayClient.commandsExecute(name)

    /** Converts text to speech audio via the gateway (used by voice conversation mode). */
    suspend fun convertTextToSpeech(text: String, voice: String?): ResponseFrame =
        gatewayClient.convertTextToSpeech(text, voice)

    suspend fun sendMessage(text: String, replyToId: String? = null, forceFresh: Boolean = false) {
        val msgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val isOnline = connectionState.value is ConnectionState.Connected

        val userMsg = Message(
            id = msgId,
            role = Role.USER,
            content = text,
            timestamp = now,
            ackState = AckState.PENDING,
            replyToId = replyToId,
            syncState = if (isOnline) SyncState.SYNCED else SyncState.PENDING,
        )
        _messages.update { it + userMsg }

        // Always persist to Room first (optimistic write)
        persistUserMessageToRoom(msgId, text, now, if (isOnline) EntitySyncState.SYNCED else EntitySyncState.PENDING)

        if (!isOnline) {
            // Offline: mark as pending, will be synced by MessageSyncWorker on reconnect
            _messages.update { msgs ->
                msgs.map { if (it.id == msgId) it.copy(ackState = AckState.PENDING, syncState = SyncState.PENDING) else it }
            }
            return
        }

        // Online: send via WebSocket
        // Prepend reply context if replying (gateway has no native threading)
        val messageText = if (replyToId != null) {
            val replyMsg = _messages.value.find { it.id == replyToId }
            if (replyMsg != null) {
                "> ${replyMsg.content.take(200)}\n\n$text"
            } else {
                text
            }
        } else {
            text
        }
        try {
            val response = gatewayClient.sendMessage(messageText, forceFresh = forceFresh)
            val newState = if (response.ok) AckState.SENT else AckState.ERROR
            val newSync = if (response.ok) SyncState.SYNCED else SyncState.FAILED
            _messages.update { msgs ->
                msgs.map { if (it.id == msgId) it.copy(ackState = newState, syncState = newSync) else it }
            }
            // Update Room sync state
            scope.launch {
                messageDao?.updateSyncState(msgId, newSync.name)
            }
        } catch (_: Exception) {
            _messages.update { msgs ->
                msgs.map { if (it.id == msgId) it.copy(ackState = AckState.ERROR, syncState = SyncState.FAILED) else it }
            }
            scope.launch {
                messageDao?.updateSyncState(msgId, EntitySyncState.FAILED.name)
            }
        }
    }

    private fun persistUserMessageToRoom(msgId: String, content: String, timestamp: Long, syncState: EntitySyncState) {
        val dao = messageDao ?: return
        val sessionKey = currentSessionKey ?: return
        scope.launch {
            dao.upsert(
                MessageEntity(
                    id = msgId,
                    sessionKey = sessionKey,
                    role = Role.USER.name,
                    content = content,
                    timestamp = timestamp,
                    syncState = syncState.name,
                )
            )
        }
    }

    /** Retry sending a failed message. Called from UI retry button. */
    suspend fun retrySendMessage(messageId: String) {
        val msg = _messages.value.find { it.id == messageId } ?: return
        if (msg.syncState != SyncState.FAILED && msg.syncState != SyncState.PENDING) return

        _messages.update { msgs ->
            msgs.map { if (it.id == messageId) it.copy(ackState = AckState.PENDING, syncState = SyncState.PENDING) else it }
        }

        try {
            val response = gatewayClient.sendMessage(msg.content)
            val newState = if (response.ok) AckState.SENT else AckState.ERROR
            val newSync = if (response.ok) SyncState.SYNCED else SyncState.FAILED
            _messages.update { msgs ->
                msgs.map { if (it.id == messageId) it.copy(ackState = newState, syncState = newSync) else it }
            }
            scope.launch { messageDao?.updateSyncState(messageId, newSync.name) }
        } catch (_: Exception) {
            _messages.update { msgs ->
                msgs.map { if (it.id == messageId) it.copy(ackState = AckState.ERROR, syncState = SyncState.FAILED) else it }
            }
            scope.launch { messageDao?.updateSyncState(messageId, EntitySyncState.FAILED.name) }
        }
    }

    suspend fun sendMessageWithAttachments(text: String, attachments: List<RpcAttachment>) {
        val displayText = text.ifBlank {
            attachments.joinToString(", ") { it.fileName ?: "attachment" }
                .let { "[Sent: $it]" }
        }
        val msgId = UUID.randomUUID().toString()
        val userMsg = Message(
            id = msgId,
            role = Role.USER,
            content = displayText,
            timestamp = System.currentTimeMillis(),
            ackState = AckState.PENDING,
        )
        _messages.update { it + userMsg }

        try {
            val response = gatewayClient.sendMessageWithAttachments(
                message = text.ifBlank { "Describe this image." },
                attachments = attachments,
            )
            val newState = if (response.ok) AckState.SENT else AckState.ERROR
            _messages.update { msgs ->
                msgs.map { if (it.id == msgId) it.copy(ackState = newState) else it }
            }
        } catch (_: Exception) {
            _messages.update { msgs ->
                msgs.map { if (it.id == msgId) it.copy(ackState = AckState.ERROR) else it }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        _messages.update { it.filter { msg -> msg.id != messageId } }
    }

    fun addLocalAssistantMessage(content: String) {
        _messages.update {
            it + Message(
                id = UUID.randomUUID().toString(),
                role = Role.ASSISTANT,
                content = content,
                timestamp = System.currentTimeMillis(),
                isStreaming = false,
            )
        }
        trimMessages()
    }

    fun findLastUserMessageBefore(assistantMessageId: String): Message? {
        val messages = _messages.value
        val idx = messages.indexOfFirst { it.id == assistantMessageId }
        if (idx <= 0) return null
        return messages.subList(0, idx).lastOrNull { it.role == Role.USER }
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadHistory(limit: Int? = null) {
        val response = gatewayClient.loadHistory(limit)
        if (!response.ok || response.payload == null) return
        val payload = response.payload ?: return

        // Gateway sends history in res payload as {messages: [...]}
        val historyResponse = try {
            json.decodeFromJsonElement<ChatHistoryResponse>(payload)
        } catch (_: Exception) {
            return
        }

        val entries = historyResponse.messages.mapNotNull { msg ->
            val role = msg.role ?: return@mapNotNull null
            // Skip tool-related messages (not useful for chat display)
            if (role == "toolResult" || role == "tool") return@mapNotNull null
            val text = extractTextFromContent(msg.content)
            // Skip empty or placeholder-only messages
            if (text.isBlank() || text.contains("CHAT_HISTORY_OVERSIZED_PLACEHOLDER")) return@mapNotNull null
            HistoryEntry(role = role, content = text, timestamp = msg.timestamp)
        }

        if (entries.isNotEmpty()) {
            handleHistory(GatewayEvent.HistoryResult(entries))
        }
    }

    /** Extract plain text from history message content (string or content block array). */
    private fun extractTextFromContent(content: kotlinx.serialization.json.JsonElement?): String {
        if (content == null) return ""
        return when (content) {
            is JsonPrimitive -> content.contentOrNull ?: ""
            is JsonArray -> content.jsonArray.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val blockType = obj["type"]?.jsonPrimitive?.contentOrNull
                if (blockType == "tool_use" || blockType == "tool_result") return@mapNotNull null
                obj["text"]?.jsonPrimitive?.contentOrNull
            }.joinToString("")
            is JsonObject -> content.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            else -> ""
        }
    }

    suspend fun abortCurrentRun() {
        if (currentRunId != null) {
            gatewayClient.abortRun(currentRunId)
        } else {
            gatewayClient.abortSession()
        }
    }

    fun clearMessages() {
        sessionGeneration++
        _messages.value = emptyList()
        currentRunId = null
    }

    fun clearMessagesWithRoom(sessionKey: String) {
        sessionGeneration++
        _messages.value = emptyList()
        currentRunId = null
        scope.launch {
            messageDao?.deleteBySession(sessionKey)
        }
    }

    /**
     * Load persisted messages for [sessionKey] from Room, joining with tool_events rows.
     * Replaces the in-memory message list with the Room snapshot so the UI shows cached
     * content instantly while the gateway history request is in-flight.
     *
     * Called by callers (e.g. SessionRepository) after a session switch.
     */
    suspend fun loadMessagesFromRoom(sessionKey: String) {
        val msgDao = messageDao ?: return
        val evtDao = toolEventDao

        val entities = msgDao.getMessages(sessionKey)
        if (entities.isEmpty()) return

        val messages = entities.map { entity ->
            val toolEvents = evtDao?.getByMessageId(entity.id)?.map { te ->
                ToolEvent(
                    toolCallId = te.toolCallId,
                    toolName = te.toolName,
                    input = try {
                        Json.parseToJsonElement(te.inputJson)
                    } catch (_: Exception) {
                        JsonPrimitive(te.inputJson)
                    },
                    status = try {
                        ToolStatus.valueOf(te.status)
                    } catch (_: Exception) {
                        ToolStatus.Done
                    },
                    output = te.outputJson?.let {
                        try { Json.parseToJsonElement(it) } catch (_: Exception) { null }
                    },
                    error = te.error,
                    durationMs = te.durationMs,
                )
            } ?: emptyList()

            val injectedMemories = entity.injectedMemoryIds?.let { json ->
                try {
                    Json { ignoreUnknownKeys = true }
                        .decodeFromString<List<com.scaso.drclawapp.data.websocket.MemoryRef>>(json)
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            Message(
                id = entity.id,
                role = try { Role.valueOf(entity.role) } catch (_: Exception) { Role.SYSTEM },
                content = entity.content,
                timestamp = entity.timestamp,
                messageType = try {
                    MessageType.valueOf(entity.messageType)
                } catch (_: Exception) {
                    MessageType.TEXT
                },
                modelName = entity.modelName,
                toolEvents = toolEvents,
                injectedMemories = injectedMemories,
            )
        }

        _messages.value = messages
    }

    private fun handleDelta(event: GatewayEvent.ChatDelta) {
        val gen = sessionGeneration

        val isNewRun = currentRunId != event.runId &&
            _messages.value.none { it.runId == event.runId }
        currentRunId = event.runId

        // Check if we have a pending user message (sent from this device)
        val hasPendingUserMsg = _messages.value.any {
            it.role == Role.USER && (it.ackState == AckState.PENDING || it.ackState == AckState.SENT)
        }

        // If this is a new run and we didn't send it, reload history
        // to pick up messages from other clients (dashboard, TUI, etc.)
        // Only if nothing is currently streaming to avoid mid-stream disruption
        val anyStreaming = _messages.value.any { it.isStreaming }
        if (isNewRun && !hasPendingUserMsg && !anyStreaming) {
            scope.launch { loadHistory() }
        }

        // Update user ack state + append/update streaming message in a single atomic update
        _messages.update { messages ->
            // Reject stale deltas from a previous session
            if (sessionGeneration != gen) return@update messages

            val lastUser = messages.lastOrNull { it.role == Role.USER && (it.ackState == AckState.PENDING || it.ackState == AckState.SENT) }
            val ackUpdated = if (lastUser != null) {
                messages.map {
                    if (it.id == lastUser.id) it.copy(ackState = AckState.PROCESSING) else it
                }
            } else {
                messages
            }

            val existing = ackUpdated.findLast { it.runId == event.runId && it.isStreaming }
            if (existing != null) {
                ackUpdated.map {
                    if (it.id == existing.id) it.copy(content = it.content + event.text)
                    else it
                }
            } else {
                ackUpdated + Message(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    content = event.text,
                    timestamp = System.currentTimeMillis(),
                    isStreaming = true,
                    runId = event.runId,
                )
            }
        }
        trimMessages()
    }

    /**
     * Server-side stream resume: after reconnect, the gateway sends ONE catchup event
     * carrying the full accumulated text of a still-generating run. Unlike [handleDelta],
     * this REPLACES the streaming message's content rather than appending to it. Later
     * "delta" frames continue appending as normal since the message stays isStreaming=true.
     */
    private fun handleCatchup(event: GatewayEvent.ChatCatchup) {
        val gen = sessionGeneration
        currentRunId = event.runId
        _messages.update { messages ->
            if (sessionGeneration != gen) return@update messages
            val existing = messages.findLast { it.runId == event.runId && it.isStreaming }
            if (existing != null) {
                messages.map {
                    if (it.id == existing.id) it.copy(content = event.content, isStreaming = true)
                    else it
                }
            } else {
                // App restarted mid-run — no local streaming message to replace, create one.
                messages + Message(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    content = event.content,
                    timestamp = System.currentTimeMillis(),
                    isStreaming = true,
                    runId = event.runId,
                )
            }
        }
        trimMessages()
    }

    private fun handleFinal(event: GatewayEvent.ChatFinal) {
        _messages.update { messages ->
            val existing = messages.findLast { it.runId == event.runId && it.isStreaming }
            if (existing != null) {
                // Normal path: finalize the streaming message
                messages.map {
                    if (it.id == existing.id) {
                        val finalContent = if (!event.text.isNullOrEmpty()) event.text else it.content
                        it.copy(
                            content = finalContent,
                            isStreaming = false,
                            modelName = currentModelName,
                            injectedMemories = event.injectedMemories,
                        )
                    } else if (it.role == Role.USER && it.ackState == AckState.PROCESSING) {
                        it.copy(ackState = AckState.DONE)
                    } else it
                }
            } else if (!event.text.isNullOrEmpty()) {
                // Fallback: no delta ever created a streaming message (e.g. all tokens
                // were thinking tokens stripped by Ironjaw). Create from final text.
                val updated = messages.map {
                    if (it.role == Role.USER && it.ackState == AckState.PROCESSING) {
                        it.copy(ackState = AckState.DONE)
                    } else it
                }
                updated + Message(
                    id = UUID.randomUUID().toString(),
                    role = Role.ASSISTANT,
                    content = event.text,
                    timestamp = System.currentTimeMillis(),
                    isStreaming = false,
                    runId = event.runId,
                    modelName = currentModelName,
                    injectedMemories = event.injectedMemories,
                )
            } else {
                // No existing streaming message and no final text — nothing to do,
                // but still mark user messages as done
                messages.map {
                    if (it.role == Role.USER && it.ackState == AckState.PROCESSING) {
                        it.copy(ackState = AckState.DONE)
                    } else it
                }
            }
        }

        // Persist final assistant message to Room
        persistFinalToRoom(event)

        // Auto-continue on max_tokens truncation (max 3 per chain)
        if (event.stopReason == "max_tokens" && autoContinueCount < MAX_AUTO_CONTINUES) {
            autoContinueCount++
            scope.launch {
                gatewayClient.sendMessage("Continue from where you left off.")
            }
        } else {
            autoContinueCount = 0
            currentRunId = null
        }
    }

    private fun handleAborted(event: GatewayEvent.ChatAborted) {
        _messages.update { messages ->
            messages.map {
                if (it.runId == event.runId && it.isStreaming) {
                    it.copy(isStreaming = false, isAborted = true)
                } else if (it.role == Role.USER && it.ackState == AckState.PROCESSING) {
                    it.copy(ackState = AckState.DONE)
                } else it
            }
        }
        currentRunId = null
    }

    private fun handleError(event: GatewayEvent.ChatError) {
        _messages.update { messages ->
            val existing = messages.findLast { it.runId == event.runId && it.isStreaming }
            if (existing != null) {
                messages.map {
                    if (it.id == existing.id) {
                        it.copy(
                            content = it.content + "\n\n[Error: ${event.message ?: "Unknown error"}]",
                            isStreaming = false,
                        )
                    } else if (it.role == Role.USER && it.ackState == AckState.PROCESSING) {
                        it.copy(ackState = AckState.DONE)
                    } else it
                }
            } else {
                messages + Message(
                    id = UUID.randomUUID().toString(),
                    role = Role.SYSTEM,
                    content = "[Error: ${event.message ?: "Unknown error"}]",
                    timestamp = System.currentTimeMillis(),
                    runId = event.runId,
                )
            }
        }
        currentRunId = null
    }

    private fun handleHistory(event: GatewayEvent.HistoryResult) {
        val historyMessages = event.entries.mapIndexed { index, entry ->
            val isTruncated = entry.content.startsWith("[chat.history omitted:")
            Message(
                id = "history-$index",
                role = when (entry.role) {
                    "assistant" -> Role.ASSISTANT
                    "user" -> Role.USER
                    else -> Role.SYSTEM
                },
                content = entry.content,
                timestamp = entry.timestamp ?: System.currentTimeMillis(),
                isTruncated = isTruncated,
                messageType = if (isTruncated) MessageType.TRUNCATED else MessageType.TEXT,
            )
        }
        _messages.update { existing ->
            // Keep any actively streaming messages and locally sent messages
            val localOnly = existing.filter { it.isStreaming || it.ackState == AckState.SENT }
            // Deduplicate by content+role to avoid doubles from cross-device sync
            val existingContent = localOnly.map { "${it.role}:${it.content.take(100)}" }.toSet()
            val newHistory = historyMessages.filter { msg ->
                "${msg.role}:${msg.content.take(100)}" !in existingContent
            }
            // Sort by timestamp instead of appending localOnly last. A stale localOnly
            // message (e.g. a user message still marked SENT because its ChatFinal never
            // arrived — the device was disconnected when the reply landed) would otherwise
            // be shoved after messages that are chronologically newer, e.g. the very answer
            // that resolved it. In the reversed chat list that buries the real answer above
            // what looks like the conversation's last word.
            (newHistory + localOnly).sortedBy { it.timestamp }
        }
        trimMessages()

        // Persist history to Room
        persistHistoryToRoom(event)
    }

    private fun handleToolUse(event: GatewayEvent.ToolUse) {
        val gen = sessionGeneration
        _messages.update { messages ->
            if (sessionGeneration != gen) return@update messages
            messages + Message(
                id = UUID.randomUUID().toString(),
                role = Role.ASSISTANT,
                content = event.toolName,
                timestamp = System.currentTimeMillis(),
                runId = event.runId,
                messageType = MessageType.TOOL_USE,
                toolName = event.toolName,
                toolInput = event.toolInput,
            )
        }
    }

    private fun handleToolResult(event: GatewayEvent.ToolResult) {
        val gen = sessionGeneration
        _messages.update { messages ->
            if (sessionGeneration != gen) return@update messages
            messages + Message(
                id = UUID.randomUUID().toString(),
                role = Role.ASSISTANT,
                content = event.content,
                timestamp = System.currentTimeMillis(),
                runId = event.runId,
                messageType = MessageType.TOOL_RESULT,
                toolName = event.toolName,
                toolResult = event.content,
            )
        }
    }

    private fun handleInject(event: GatewayEvent.ChatInject) {
        val gen = sessionGeneration
        _messages.update { messages ->
            if (sessionGeneration != gen) return@update messages
            messages + Message(
                id = UUID.randomUUID().toString(),
                role = Role.ASSISTANT,
                content = event.text,
                timestamp = System.currentTimeMillis(),
                messageType = MessageType.INJECT,
                isInjected = true,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Phase 13: native-chat tool event handlers
    // -------------------------------------------------------------------------

    /**
     * Attaches a new [ToolEvent] (status=Running) to the currently streaming assistant message.
     * If no streaming message exists, creates a lightweight placeholder so the tool event
     * is not lost.
     */
    private fun handleNativeToolStart(event: GatewayEvent.NativeToolStart) {
        val toolEvent = ToolEvent(
            toolCallId = event.toolCallId,
            toolName = event.toolName,
            input = event.input,
            status = ToolStatus.Running,
        )
        val gen = sessionGeneration
        _messages.update { messages ->
            if (sessionGeneration != gen) return@update messages
            val idx = messages.indexOfLast { it.isStreaming && it.runId == event.runId }
            if (idx >= 0) {
                val updated = messages[idx].copy(toolEvents = messages[idx].toolEvents + toolEvent)
                messages.toMutableList().also { it[idx] = updated }
            } else {
                // No streaming message yet — attach to last assistant message for this run
                val fallbackIdx = messages.indexOfLast { it.runId == event.runId && it.role == Role.ASSISTANT }
                if (fallbackIdx >= 0) {
                    val updated = messages[fallbackIdx].copy(
                        toolEvents = messages[fallbackIdx].toolEvents + toolEvent
                    )
                    messages.toMutableList().also { it[fallbackIdx] = updated }
                } else {
                    messages
                }
            }
        }

        // Persist to Room: find the parent message ID to use as the foreign key.
        persistToolEventStart(event)
    }

    private fun persistToolEventStart(event: GatewayEvent.NativeToolStart) {
        val dao = toolEventDao ?: return
        val now = System.currentTimeMillis()
        // Capture parentMsgId synchronously on the calling thread before launching the coroutine.
        // Reading _messages.value inside scope.launch risks a race: a concurrent sendMessage call
        // can update _messages between the _messages.update above and coroutine scheduling, causing
        // lastOrNull to see a stale or absent entry and silently drop the telemetry row.
        val parentMsgId = _messages.value.lastOrNull { msg ->
            msg.runId == event.runId &&
                msg.toolEvents.any { it.toolCallId == event.toolCallId }
        }?.id
            ?: _messages.value.lastOrNull { msg ->
                msg.runId == event.runId && msg.role == Role.ASSISTANT
            }?.id
            ?: run {
                android.util.Log.w("ChatRepository", "persistToolEventStart: no parent message for toolCallId=${event.toolCallId}, dropping event")
                return
            }
        scope.launch {
            dao.upsert(
                ToolEventEntity(
                    messageId = parentMsgId,
                    toolCallId = event.toolCallId,
                    toolName = event.toolName,
                    inputJson = Json.encodeToString(event.input),
                    outputJson = null,
                    error = null,
                    status = ToolStatus.Running.name,
                    startedAt = now,
                    durationMs = null,
                )
            )
        }
    }

    /**
     * Finds the matching [ToolEvent] by [toolCallId] on any message in the current run
     * and updates its status, output, error, and durationMs.
     */
    private fun handleNativeToolResult(event: GatewayEvent.NativeToolResult) {
        val gen = sessionGeneration
        _messages.update { messages ->
            if (sessionGeneration != gen) return@update messages
            val idx = messages.indexOfLast { msg ->
                msg.runId == event.runId && msg.toolEvents.any { it.toolCallId == event.toolCallId }
            }
            if (idx < 0) return@update messages
            val msg = messages[idx]
            val updatedTools = msg.toolEvents.map { te ->
                if (te.toolCallId == event.toolCallId) {
                    te.copy(
                        status = if (event.error != null) ToolStatus.Error else ToolStatus.Done,
                        output = event.output,
                        error = event.error,
                        durationMs = event.durationMs,
                    )
                } else te
            }
            val updatedMsg = msg.copy(toolEvents = updatedTools)
            messages.toMutableList().also { it[idx] = updatedMsg }
        }

        // Persist result to Room.
        persistToolEventResult(event)
    }

    private fun persistToolEventResult(event: GatewayEvent.NativeToolResult) {
        val dao = toolEventDao ?: return
        scope.launch {
            val status = if (event.error != null) ToolStatus.Error else ToolStatus.Done
            val outputJson = event.output?.let { Json.encodeToString(it) }
            dao.updateResult(
                toolCallId = event.toolCallId,
                status = status.name,
                output = outputJson,
                error = event.error,
                duration = event.durationMs,
            )
        }
    }

    /**
     * Forwards a native-chat permission request to [nativePermissionFlow] for ViewModel
     * consumption. Does not modify the message list.
     */
    private fun handleNativePermissionRequest(event: GatewayEvent.NativePermissionRequest) {
        scope.launch {
            _nativePermissionFlow.emit(event)
        }
    }

    private fun persistFinalToRoom(event: GatewayEvent.ChatFinal) {
        val dao = messageDao ?: return
        val sessionKey = currentSessionKey ?: return
        scope.launch {
            val msg = _messages.value.findLast { it.runId == event.runId && !it.isStreaming }
                ?: return@launch
            val injectedIdsJson = if (event.injectedMemories.isNotEmpty()) {
                kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(
                        com.scaso.drclawapp.data.websocket.MemoryRef.serializer()
                    ),
                    event.injectedMemories,
                )
            } else null
            dao.upsert(
                MessageEntity(
                    id = msg.id,
                    sessionKey = sessionKey,
                    role = msg.role.name,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    messageType = msg.messageType.name,
                    modelName = currentModelName,
                    injectedMemoryIds = injectedIdsJson,
                )
            )
            // Also persist the most recent user message if not yet saved
            val lastUser = _messages.value.lastOrNull {
                it.role == Role.USER && it.ackState == AckState.DONE
            }
            if (lastUser != null) {
                dao.upsert(
                    MessageEntity(
                        id = lastUser.id,
                        sessionKey = sessionKey,
                        role = lastUser.role.name,
                        content = lastUser.content,
                        timestamp = lastUser.timestamp,
                        messageType = lastUser.messageType.name,
                    )
                )
            }
        }
    }

    private fun persistHistoryToRoom(event: GatewayEvent.HistoryResult) {
        val dao = messageDao ?: return
        val sessionKey = currentSessionKey ?: return
        scope.launch {
            val entities = event.entries.mapIndexed { index, entry ->
                MessageEntity(
                    id = "history-$index",
                    sessionKey = sessionKey,
                    role = when (entry.role) {
                        "assistant" -> Role.ASSISTANT.name
                        "user" -> Role.USER.name
                        else -> Role.SYSTEM.name
                    },
                    content = entry.content,
                    timestamp = entry.timestamp ?: System.currentTimeMillis(),
                    messageType = if (entry.content.startsWith("[chat.history omitted:"))
                        MessageType.TRUNCATED.name else MessageType.TEXT.name,
                )
            }
            // Atomic replace, not a plain upsert: history rows are keyed positionally
            // (id = "history-$index"), so a reload with a different entry count would
            // otherwise strand stale rows or overwrite the wrong row's content. Live
            // (UUID-keyed) rows are untouched -- see MessageDao.replaceHistoryMessages.
            dao.replaceHistoryMessages(sessionKey, entities)
        }
    }
}
