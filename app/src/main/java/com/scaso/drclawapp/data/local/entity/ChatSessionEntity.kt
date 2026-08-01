package com.scaso.drclawapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class ChatSessionEntity(
    @PrimaryKey val sessionKey: String,
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
    val forkedFromSessionKey: String? = null,
    val forkedFromMessageId: String? = null,
    val contextTokens: Int? = null,
    val compactionCount: Int? = null,
    val contextPct: Int? = null,
    val contextWindow: Int? = null,
    val mode: String = "Default",
)
