package com.scaso.drclawapp.data.model

/**
 * Represents a chat session with the Ironjaw Gateway.
 * No Android imports — KMP-extractable.
 */
data class ChatSession(
    val sessionKey: String,
    val agentId: String? = null,
    val title: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val messageCount: Int = 0,
    val lastMessage: String? = null,
    val isSubAgent: Boolean = false,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val modelName: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val contextTokens: Int? = null,
    val compactionCount: Int? = null,
    val contextPct: Int? = null,
    val contextWindow: Int? = null,
    val mode: String = "Default",
)
