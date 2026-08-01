package com.scaso.drclawapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionKey"],
            childColumns = ["sessionKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionKey")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionKey: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val messageType: String = "TEXT",
    val toolName: String? = null,
    val modelName: String? = null,
    val syncState: String = SyncState.SYNCED.name,
    val injectedMemoryIds: String? = null,
)

enum class SyncState {
    SYNCED,
    PENDING,
    FAILED,
}
