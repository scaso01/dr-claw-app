package com.scaso.drclawapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.scaso.drclawapp.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

data class SearchResult(
    val id: String,
    val sessionKey: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val messageType: String,
    val modelName: String?,
    val sessionTitle: String?,
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE sessionKey = :sessionKey ORDER BY timestamp ASC")
    fun observeMessages(sessionKey: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionKey = :sessionKey ORDER BY timestamp ASC")
    suspend fun getMessages(sessionKey: String): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE sessionKey = :sessionKey")
    suspend fun deleteBySession(sessionKey: String)

    /** Deletes only the history-sourced rows for a session (id prefix "history-"), leaving
     * live (UUID-keyed) rows untouched. */
    @Query("DELETE FROM messages WHERE sessionKey = :sessionKey AND id LIKE 'history-%'")
    suspend fun deleteHistoryMessages(sessionKey: String)

    /**
     * Atomically replaces the session's history-sourced rows with a fresh set. History
     * entries are keyed positionally (id = "history-$index" per load), so without this the
     * row count could shift between loads and strand/overwrite unrelated rows -- deleting the
     * old history rows first guarantees no stale or duplicate rows survive a reload. Live
     * (UUID-keyed) rows are untouched since they never match the "history-" id prefix.
     */
    @Transaction
    suspend fun replaceHistoryMessages(sessionKey: String, messages: List<MessageEntity>) {
        deleteHistoryMessages(sessionKey)
        upsertAll(messages)
    }

    @Query("""
        SELECT m.id, m.sessionKey, m.role, m.content, m.timestamp, m.messageType, m.modelName,
               s.title AS sessionTitle
        FROM messages m
        JOIN messages_fts ON messages_fts.docid = m.rowid
        JOIN sessions s ON s.sessionKey = m.sessionKey
        WHERE messages_fts MATCH :query
        ORDER BY m.timestamp DESC
    """)
    suspend fun searchGlobal(query: String): List<SearchResult>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionKey = :sessionKey")
    suspend fun countBySession(sessionKey: String): Int

    @Query("SELECT * FROM messages WHERE syncState = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingMessages(): List<MessageEntity>

    @Query("UPDATE messages SET syncState = :syncState WHERE id = :messageId")
    suspend fun updateSyncState(messageId: String, syncState: String)
}
