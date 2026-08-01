package com.scaso.drclawapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.scaso.drclawapp.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM sessions WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActiveSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM sessions WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM sessions WHERE sessionKey = :key")
    suspend fun getByKey(key: String): ChatSessionEntity?

    @Upsert
    suspend fun upsert(session: ChatSessionEntity)

    @Upsert
    suspend fun upsertAll(sessions: List<ChatSessionEntity>)

    @Query("UPDATE sessions SET isPinned = :pinned WHERE sessionKey = :key")
    suspend fun setPinned(key: String, pinned: Boolean)

    @Query("UPDATE sessions SET isArchived = :archived WHERE sessionKey = :key")
    suspend fun setArchived(key: String, archived: Boolean)

    @Query("UPDATE sessions SET title = :title WHERE sessionKey = :key")
    suspend fun updateTitle(key: String, title: String)

    @Query("UPDATE sessions SET mode = :mode WHERE sessionKey = :key")
    suspend fun updateMode(key: String, mode: String)

    @Query("DELETE FROM sessions WHERE sessionKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    @Query("UPDATE sessions SET contextPct = :contextPct, contextWindow = :contextWindow, contextTokens = :contextTokens WHERE sessionKey = :key")
    suspend fun updateSessionContext(key: String, contextPct: Int?, contextWindow: Int?, contextTokens: Int?)
}
