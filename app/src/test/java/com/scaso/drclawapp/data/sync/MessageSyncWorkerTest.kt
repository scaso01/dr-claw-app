package com.scaso.drclawapp.data.sync

import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.entity.MessageEntity
import com.scaso.drclawapp.data.local.entity.SyncState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSyncWorkerTest {

    @Test
    fun `pending messages are sent and marked SYNCED on success`() = runTest {
        val dao = FakeMessageDao(
            pendingMessages = mutableListOf(
                createPendingMessage("msg-1", "Hello"),
                createPendingMessage("msg-2", "World"),
            )
        )
        val client = FakeSyncGatewayClient(shouldSucceed = true)

        val syncer = MessageSyncer(dao, client)
        val result = syncer.syncPendingMessages()

        assertEquals(0, result.failCount)
        assertEquals(2, result.syncedCount)
        assertEquals(SyncState.SYNCED.name, dao.syncStates["msg-1"])
        assertEquals(SyncState.SYNCED.name, dao.syncStates["msg-2"])
    }

    @Test
    fun `failed sends are marked FAILED`() = runTest {
        val dao = FakeMessageDao(
            pendingMessages = mutableListOf(
                createPendingMessage("msg-1", "Hello"),
            )
        )
        val client = FakeSyncGatewayClient(shouldSucceed = false)

        val syncer = MessageSyncer(dao, client)
        val result = syncer.syncPendingMessages()

        assertEquals(1, result.failCount)
        assertEquals(0, result.syncedCount)
        assertEquals(SyncState.FAILED.name, dao.syncStates["msg-1"])
    }

    @Test
    fun `empty pending list returns zero counts`() = runTest {
        val dao = FakeMessageDao(pendingMessages = mutableListOf())
        val client = FakeSyncGatewayClient(shouldSucceed = true)

        val syncer = MessageSyncer(dao, client)
        val result = syncer.syncPendingMessages()

        assertEquals(0, result.failCount)
        assertEquals(0, result.syncedCount)
    }

    @Test
    fun `partial failures are counted correctly`() = runTest {
        val dao = FakeMessageDao(
            pendingMessages = mutableListOf(
                createPendingMessage("msg-1", "Good"),
                createPendingMessage("msg-2", "FAIL"),
                createPendingMessage("msg-3", "Good"),
            )
        )
        val client = FakeSyncGatewayClient(shouldSucceed = true, failOnContent = "FAIL")

        val syncer = MessageSyncer(dao, client)
        val result = syncer.syncPendingMessages()

        assertEquals(1, result.failCount)
        assertEquals(2, result.syncedCount)
        assertEquals(SyncState.SYNCED.name, dao.syncStates["msg-1"])
        assertEquals(SyncState.FAILED.name, dao.syncStates["msg-2"])
        assertEquals(SyncState.SYNCED.name, dao.syncStates["msg-3"])
    }

    private fun createPendingMessage(id: String, content: String) = MessageEntity(
        id = id,
        sessionKey = "session-1",
        role = "USER",
        content = content,
        timestamp = System.currentTimeMillis(),
        syncState = SyncState.PENDING.name,
    )
}

/**
 * Testable sync logic extracted from the Worker (which requires Android context).
 */
class MessageSyncer(
    private val messageDao: FakeMessageDao,
    private val gatewayClient: FakeSyncGatewayClient,
) {
    data class SyncResult(val syncedCount: Int, val failCount: Int)

    suspend fun syncPendingMessages(): SyncResult {
        val pending = messageDao.getPendingMessages()
        var synced = 0
        var failed = 0

        for (message in pending) {
            try {
                val response = gatewayClient.sendMessage(message.content)
                if (response.ok) {
                    messageDao.updateSyncState(message.id, SyncState.SYNCED.name)
                    synced++
                } else {
                    messageDao.updateSyncState(message.id, SyncState.FAILED.name)
                    failed++
                }
            } catch (_: Exception) {
                messageDao.updateSyncState(message.id, SyncState.FAILED.name)
                failed++
            }
        }

        return SyncResult(synced, failed)
    }
}

class FakeMessageDao(
    private val pendingMessages: MutableList<MessageEntity>,
) {
    val syncStates = mutableMapOf<String, String>()

    suspend fun getPendingMessages(): List<MessageEntity> = pendingMessages.toList()

    suspend fun updateSyncState(messageId: String, syncState: String) {
        syncStates[messageId] = syncState
    }
}

class FakeSyncGatewayClient(
    private val shouldSucceed: Boolean,
    private val failOnContent: String? = null,
) {
    suspend fun sendMessage(text: String): ResponseFrame {
        if (failOnContent != null && text == failOnContent) {
            return ResponseFrame(id = "fake", ok = false)
        }
        return ResponseFrame(id = "fake", ok = shouldSucceed)
    }
}
