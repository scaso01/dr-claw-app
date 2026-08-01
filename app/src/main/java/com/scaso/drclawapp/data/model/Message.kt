package com.scaso.drclawapp.data.model

import com.scaso.drclawapp.data.websocket.MemoryRef

/**
 * Domain model for a chat message.
 * No Android imports -- KMP-extractable.
 */
data class Message(
    val id: String,
    val role: Role,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false,
    val runId: String? = null,
    val ackState: AckState = AckState.NONE,
    val suggestedActions: List<String> = emptyList(),
    val replyToId: String? = null,
    val messageType: MessageType = MessageType.TEXT,
    val isAborted: Boolean = false,
    val isTruncated: Boolean = false,
    val isInjected: Boolean = false,
    val toolName: String? = null,
    val toolInput: String? = null,
    val toolResult: String? = null,
    val modelName: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    /**
     * In-memory tool events attached to this message during a native-chat agentic turn.
     * Not persisted to Room in this phase — Phase 17 adds the schema migration.
     * Excluded from Room entity via @Ignore on MessageEntity.
     */
    val toolEvents: List<ToolEvent> = emptyList(),
    val injectedMemories: List<MemoryRef> = emptyList(),
)

enum class SyncState {
    SYNCED,
    PENDING,
    FAILED,
}

enum class MessageType {
    TEXT,
    TOOL_USE,
    TOOL_RESULT,
    TRUNCATED,
    INJECT,
}

enum class Role {
    USER,
    ASSISTANT,
    SYSTEM,
    DENIAL,
}

enum class AckState {
    NONE,
    PENDING,
    SENT,
    PROCESSING,
    DONE,
    ERROR,
}
