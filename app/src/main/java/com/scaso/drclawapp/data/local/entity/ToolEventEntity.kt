package com.scaso.drclawapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity that persists a [com.scaso.drclawapp.data.model.ToolEvent] across restarts.
 *
 * Foreign key references [MessageEntity.id] (TEXT primary key).
 * CASCADE delete ensures tool events are cleaned up when the parent message is deleted.
 *
 * Added in Phase 17 — DB migration 4 → 5.
 */
@Entity(
    tableName = "tool_events",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("message_id"),
        Index("tool_call_id", unique = true),
    ],
)
data class ToolEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "tool_call_id") val toolCallId: String,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "input_json") val inputJson: String,
    @ColumnInfo(name = "output_json") val outputJson: String?,
    @ColumnInfo(name = "error") val error: String?,
    /** Stores [com.scaso.drclawapp.data.model.ToolStatus.name]. */
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
)
