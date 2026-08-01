package com.scaso.drclawapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scaso.drclawapp.data.local.entity.ToolEventEntity

/**
 * DAO for [ToolEventEntity].
 * All queries are suspend functions — called from coroutines in [ChatRepository].
 *
 * Added in Phase 17.
 */
@Dao
interface ToolEventDao {

    /**
     * Insert or replace a tool event row.
     * REPLACE strategy overwrites an existing row with the same [ToolEventEntity.toolCallId]
     * (enforced by the unique index), so callers can safely call upsert on both start and result.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: ToolEventEntity): Long

    /** Returns all events for a message ordered chronologically. */
    @Query("SELECT * FROM tool_events WHERE message_id = :messageId ORDER BY started_at ASC")
    suspend fun getByMessageId(messageId: String): List<ToolEventEntity>

    /** Lookup a single event by its tool-call ID, or null if not found. */
    @Query("SELECT * FROM tool_events WHERE tool_call_id = :toolCallId LIMIT 1")
    suspend fun getByToolCallId(toolCallId: String): ToolEventEntity?

    /** Patch status, output, error, and duration on an existing row. */
    @Query("""
        UPDATE tool_events
        SET status = :status,
            output_json = :output,
            error = :error,
            duration_ms = :duration
        WHERE tool_call_id = :toolCallId
    """)
    suspend fun updateResult(
        toolCallId: String,
        status: String,
        output: String?,
        error: String?,
        duration: Long?,
    )

    /** Remove all tool events belonging to a message (used when a message is deleted). */
    @Query("DELETE FROM tool_events WHERE message_id = :messageId")
    suspend fun deleteByMessageId(messageId: String)
}
