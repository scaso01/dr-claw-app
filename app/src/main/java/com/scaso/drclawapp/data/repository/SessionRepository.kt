package com.scaso.drclawapp.data.repository

import com.scaso.drclawapp.data.model.ChatSession
import com.scaso.drclawapp.data.local.dao.SessionDao
import com.scaso.drclawapp.data.local.entity.ChatSessionEntity
import com.scaso.drclawapp.data.local.entity.MessageEntity
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.IronjawSessionsListResponse
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Manages session lifecycle: list, switch, rename, delete, reset.
 * No Android imports — KMP-extractable.
 */
class SessionRepository(
    private val gatewayClient: GatewayClient,
    private val chatRepository: ChatRepository,
    private val scope: CoroutineScope,
    private val sessionDao: SessionDao? = null,
    private val messageDao: MessageDao? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionKey = MutableStateFlow<String?>(null)
    val currentSessionKey: StateFlow<String?> = _currentSessionKey.asStateFlow()

    val archivedSessions: Flow<List<ChatSession>> =
        sessionDao?.observeArchivedSessions()?.map { entities ->
            entities.map { it.toDomain() }
        } ?: MutableStateFlow(emptyList())

    fun setCurrentSessionKey(key: String?) {
        _currentSessionKey.value = key
        chatRepository.currentSessionKey = key
    }

    suspend fun loadSessions(limit: Int? = 50) {
        val response = gatewayClient.listSessions(limit = limit)
        if (response.ok && response.payload != null) {
            val listResponse = try {
                json.decodeFromJsonElement<IronjawSessionsListResponse>(response.payload)
            } catch (_: Exception) {
                return
            }
            val mapped = listResponse.sessions.map { entry ->
                ChatSession(
                    sessionKey = entry.id,
                    title = entry.title?.takeIf { it.isNotBlank() },
                    createdAt = parseIsoToEpochMs(entry.createdAt),
                    updatedAt = entry.updatedAt?.let { parseIsoToEpochMs(it) },
                    messageCount = entry.messageCount ?: 0,
                    contextTokens = entry.contextTokens,
                    compactionCount = entry.compactionCount,
                    contextPct = entry.contextPct,
                    contextWindow = entry.contextWindow,
                    inputTokens = entry.inputTokens,
                    outputTokens = entry.outputTokens,
                )
            }
            _sessions.value = mapped

            sessionDao?.let { dao ->
                val entities = mapped.map { session ->
                    val existing = dao.getByKey(session.sessionKey)
                    session.toEntity(
                        isPinned = existing?.isPinned ?: false,
                        isArchived = existing?.isArchived ?: false,
                    )
                }
                dao.upsertAll(entities)
            }

            // Auto-initialize currentSessionKey on first load
            if (_currentSessionKey.value == null) {
                val defaultKey = mapped.maxByOrNull { it.updatedAt ?: 0L }?.sessionKey
                if (defaultKey != null) {
                    _currentSessionKey.value = defaultKey
                    chatRepository.currentSessionKey = defaultKey
                    gatewayClient.switchSession(defaultKey)
                }
            }
        }
    }

    private fun parseIsoToEpochMs(iso: String): Long {
        return try {
            Instant.parse(iso).toEpochMilliseconds()
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun switchSession(sessionKey: String) {
        gatewayClient.switchSession(sessionKey)
        _currentSessionKey.value = sessionKey
        chatRepository.currentSessionKey = sessionKey
        chatRepository.clearMessages()
        gatewayClient.loadHistory()
    }

    suspend fun createNewSession() {
        val response = gatewayClient.createSession()
        if (response.ok && response.payload != null) {
            // Ironjaw returns { "session": { "id": "...", ... }, "switched": true }
            // Legacy OpenClaw returned { "session_id": "..." } or { "id": "..." }
            val payload = response.payload.jsonObject
            val sessionId = payload["session"]
                ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                ?: payload["session_id"]?.jsonPrimitive?.contentOrNull
                ?: payload["id"]?.jsonPrimitive?.contentOrNull

            if (sessionId != null) {
                // Ironjaw auto-switches on create — just update local state (no redundant RPC)
                gatewayClient.setLocalSessionKey(sessionId)
                _currentSessionKey.value = sessionId
                chatRepository.currentSessionKey = sessionId
                chatRepository.clearMessages()

                val now = System.currentTimeMillis()
                _sessions.update { existing ->
                    listOf(
                        ChatSession(
                            sessionKey = sessionId,
                            title = "New Chat",
                            createdAt = now,
                            updatedAt = now,
                            messageCount = 0,
                        )
                    ) + existing
                }
            }
        }
    }

    suspend fun setMode(sessionKey: String, mode: String) {
        val params = buildJsonObject {
            put("session_key", sessionKey)
            put("mode", mode)
        }
        val response = gatewayClient.sendGenericRequest("session.set_mode", params)
        if (response.ok) {
            _sessions.update { sessions ->
                sessions.map {
                    if (it.sessionKey == sessionKey) it.copy(mode = mode) else it
                }
            }
            sessionDao?.updateMode(sessionKey, mode)
        }
    }

    suspend fun renameSession(sessionKey: String, title: String) {
        val response = gatewayClient.patchSession(sessionKey, title)
        if (response.ok) {
            _sessions.update { sessions ->
                sessions.map {
                    if (it.sessionKey == sessionKey) it.copy(title = title) else it
                }
            }
            sessionDao?.updateTitle(sessionKey, title)
        }
    }

    /** Returns the active Ironjaw session ID (null if not yet connected). */
    fun getActiveSessionId(): String? = gatewayClient.getCurrentSessionKey()

    /**
     * Fetches the gateway's default model name via the `gateway.model` RPC.
     * Returns null on failure or if the response has no name/defaultModel field.
     */
    suspend fun fetchDefaultModelName(): String? {
        return try {
            val response = gatewayClient.sendGenericRequest("gateway.model")
            if (response.ok && response.payload != null) {
                val obj = response.payload.jsonObject
                // Ironjaw returns { id, name, provider, status }; legacy OpenClaw returned { defaultModel }
                (obj["name"] ?: obj["defaultModel"])?.jsonPrimitive?.contentOrNull
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Forks a child agent via `agent.fork` (Ironjaw C8).
     * The caller must show a PermissionDialog BEFORE invoking this method.
     */
    suspend fun forkAgent(parentSessionId: String, task: String): ResponseFrame =
        gatewayClient.forkAgent(parentSessionId = parentSessionId, task = task)

    suspend fun deleteSession(sessionKey: String) {
        // If deleting the active session, create a new one first
        // (Ironjaw rejects deleting the active session)
        if (_currentSessionKey.value == sessionKey) {
            createNewSession() // session.create auto-switches on gateway
        }

        var response = gatewayClient.deleteSession(sessionKey)

        // Retry: if gateway still thinks this is active, force-switch then retry
        if (!response.ok && response.error?.code == "CANNOT_DELETE_ACTIVE") {
            val other = _sessions.value.firstOrNull { it.sessionKey != sessionKey }
            if (other != null) {
                gatewayClient.switchSession(other.sessionKey)
                gatewayClient.setLocalSessionKey(other.sessionKey)
                _currentSessionKey.value = other.sessionKey
                chatRepository.currentSessionKey = other.sessionKey
            }
            response = gatewayClient.deleteSession(sessionKey)
        }

        if (response.ok) {
            _sessions.update { sessions ->
                sessions.filter { it.sessionKey != sessionKey }
            }
            sessionDao?.delete(sessionKey)
        } else {
            val msg = response.error?.message ?: "Unknown error"
            error("Delete failed: $msg")
        }
    }

    suspend fun pinSession(sessionKey: String, pinned: Boolean) {
        sessionDao?.setPinned(sessionKey, pinned)
    }

    suspend fun archiveSession(sessionKey: String, archived: Boolean) {
        sessionDao?.setArchived(sessionKey, archived)
        if (archived) {
            _sessions.update { sessions ->
                sessions.filter { it.sessionKey != sessionKey }
            }
        }
    }

    /**
     * Update session context metrics from a ChatFinal event.
     * Updates both in-memory session list and Room entity.
     */
    suspend fun updateSessionContext(
        sessionId: String,
        contextPct: Int?,
        contextWindow: Int?,
        contextTokens: Long?,
    ) {
        _sessions.update { sessions ->
            sessions.map {
                if (it.sessionKey == sessionId) it.copy(
                    contextPct = contextPct ?: it.contextPct,
                    contextWindow = contextWindow ?: it.contextWindow,
                    contextTokens = contextTokens?.toInt() ?: it.contextTokens,
                ) else it
            }
        }
        sessionDao?.updateSessionContext(
            key = sessionId,
            contextPct = contextPct,
            contextWindow = contextWindow,
            contextTokens = contextTokens?.toInt(),
        )
    }

    /**
     * Fork conversation from a specific message: creates a new session
     * with messages up to (and including) the given message copied to Room.
     */
    suspend fun forkFromMessage(
        message: com.scaso.drclawapp.data.model.Message,
        currentMessages: List<com.scaso.drclawapp.data.model.Message>,
    ) {
        val sourceSessionKey = _currentSessionKey.value ?: return
        val sourceTitle = _sessions.value
            .firstOrNull { it.sessionKey == sourceSessionKey }?.title

        // Create forked session key (local-only UUID, not on gateway)
        val forkKey = "fork-${UUID.randomUUID()}"
        val now = System.currentTimeMillis()

        // Find messages up to and including the target
        val messageIndex = currentMessages.indexOfFirst { it.id == message.id }
        if (messageIndex < 0) return
        val forkedMessages = currentMessages.subList(0, messageIndex + 1)

        // Persist forked messages to Room
        messageDao?.let { dao ->
            val entities = forkedMessages.mapIndexed { index, msg ->
                MessageEntity(
                    id = "fork-${forkKey}-$index",
                    sessionKey = forkKey,
                    role = msg.role.name,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    messageType = msg.messageType.name,
                    modelName = msg.modelName,
                )
            }
            dao.upsertAll(entities)
        }

        // Create session in Room
        val forkTitle = "Fork: ${sourceTitle ?: "Chat"}"
        sessionDao?.upsert(
            ChatSessionEntity(
                sessionKey = forkKey,
                title = forkTitle,
                createdAt = now,
                updatedAt = now,
                messageCount = forkedMessages.size,
                lastMessage = forkedMessages.lastOrNull()?.content?.take(100),
                forkedFromSessionKey = sourceSessionKey,
                forkedFromMessageId = message.id,
            )
        )

        // Switch to forked session on gateway
        gatewayClient.switchSession(forkKey)
        _currentSessionKey.value = forkKey
        chatRepository.currentSessionKey = forkKey
        chatRepository.clearMessages()
        gatewayClient.loadHistory()

        // Add to local sessions list
        _sessions.update { existing ->
            listOf(
                ChatSession(
                    sessionKey = forkKey,
                    title = forkTitle,
                    createdAt = now,
                    updatedAt = now,
                    messageCount = forkedMessages.size,
                )
            ) + existing
        }
    }

}

private fun ChatSession.toEntity(
    isPinned: Boolean = false,
    isArchived: Boolean = false,
) = ChatSessionEntity(
    sessionKey = sessionKey,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messageCount = messageCount,
    lastMessage = lastMessage,
    isSubAgent = isSubAgent,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    modelName = modelName,
    isPinned = isPinned,
    isArchived = isArchived,
    contextTokens = contextTokens,
    compactionCount = compactionCount,
    contextPct = contextPct,
    contextWindow = contextWindow,
    mode = mode,
)

private fun ChatSessionEntity.toDomain() = ChatSession(
    sessionKey = sessionKey,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    messageCount = messageCount,
    lastMessage = lastMessage,
    isSubAgent = isSubAgent,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    modelName = modelName,
    isPinned = isPinned,
    isArchived = isArchived,
    contextTokens = contextTokens,
    compactionCount = compactionCount,
    contextPct = contextPct,
    contextWindow = contextWindow,
    mode = mode,
)
