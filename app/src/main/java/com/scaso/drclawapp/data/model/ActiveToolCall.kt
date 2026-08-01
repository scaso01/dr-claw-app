package com.scaso.drclawapp.data.model

/**
 * Lightweight tracker for a tool call that is currently in-flight during a native-chat
 * agentic turn. Held in [com.scaso.drclawapp.ui.chat.ChatUiState.activeTools].
 *
 * Not persisted to Room in this phase — Phase 17 will add schema migration.
 * No Android imports — KMP-extractable.
 */
data class ActiveToolCall(
    val toolCallId: String,
    val toolName: String,
    val startedAt: Long,
)
