package com.scaso.drclawapp.data.repository

import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.dao.SearchResult
import com.scaso.drclawapp.data.local.dao.ToolEventDao
import com.scaso.drclawapp.data.local.entity.MessageEntity
import com.scaso.drclawapp.data.local.entity.ToolEventEntity
import com.scaso.drclawapp.data.model.ToolStatus
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.HistoryEntry
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ChatRepository] persistence behaviour added in Phase 17.
 *
 * Test 3: [loadMessagesFromRoom] populates [ChatRepository.messages] with
 *         the associated [com.scaso.drclawapp.data.model.ToolEvent] entries
 *         pulled from [ToolEventDao].
 *
 * Uses pure-JVM fakes — no Room runtime, no Robolectric.
 */
class ChatRepositoryPersistenceTest {

    private lateinit var fakeMessageDao: FakePersistenceMessageDao
    private lateinit var fakeToolEventDao: FakePersistenceToolEventDao
    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var repository: ChatRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        fakeMessageDao = FakePersistenceMessageDao()
        fakeToolEventDao = FakePersistenceToolEventDao()

        fakeEvents = MutableSharedFlow(replay = 64, extraBufferCapacity = 64)
        val fakeConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val fakeClient = PersistenceFakeGatewayClient(fakeEvents, fakeConnectionState)

        repository = ChatRepository(
            gatewayClient = fakeClient,
            scope = scope,
            messageDao = fakeMessageDao,
            toolEventDao = fakeToolEventDao,
        )
    }

    // -------------------------------------------------------------------------
    // Test 3: loadMessagesFromRoom populates toolEvents from DAO
    // -------------------------------------------------------------------------

    @Test
    fun `loadMessagesFromRoom_populates_tool_events_for_each_message`() = runTest {
        val sessionKey = "sess-test"
        val messageId = "msg-abc"

        // Seed one MessageEntity into the fake MessageDao
        fakeMessageDao.seed(
            MessageEntity(
                id = messageId,
                sessionKey = sessionKey,
                role = "ASSISTANT",
                content = "Here is the result",
                timestamp = 1_000L,
                messageType = "TEXT",
                toolName = null,
                modelName = "claude-opus-4-5",
            )
        )

        // Seed two ToolEventEntities for that message
        fakeToolEventDao.upsert(
            ToolEventEntity(
                messageId = messageId,
                toolCallId = "call-1",
                toolName = "bash",
                inputJson = """{"command":"ls"}""",
                outputJson = """{"output":"file.txt"}""",
                error = null,
                status = ToolStatus.Done.name,
                startedAt = 900L,
                durationMs = 45L,
            )
        )
        fakeToolEventDao.upsert(
            ToolEventEntity(
                messageId = messageId,
                toolCallId = "call-2",
                toolName = "read_file",
                inputJson = """{"path":"/tmp/file.txt"}""",
                outputJson = """{"content":"hello"}""",
                error = null,
                status = ToolStatus.Done.name,
                startedAt = 950L,
                durationMs = 12L,
            )
        )

        // Act
        repository.loadMessagesFromRoom(sessionKey)

        // Assert — messages populated with 2 tool events
        val messages = repository.messages.value
        assertEquals(1, messages.size)

        val toolEvents = messages[0].toolEvents
        assertEquals(2, toolEvents.size)

        // Tool events must be ordered by started_at ASC (call-1 before call-2)
        assertEquals("call-1", toolEvents[0].toolCallId)
        assertEquals("bash", toolEvents[0].toolName)
        assertEquals(ToolStatus.Done, toolEvents[0].status)

        assertEquals("call-2", toolEvents[1].toolCallId)
        assertEquals("read_file", toolEvents[1].toolName)
    }

    @Test
    fun `loadMessagesFromRoom_empty_dao_leaves_messages_unchanged`() = runTest {
        // No messages seeded — repository should remain empty
        repository.loadMessagesFromRoom("sess-empty")

        assertEquals(emptyList<Any>(), repository.messages.value)
    }

    @Test
    fun `loadMessagesFromRoom_message_with_no_tool_events_gets_empty_list`() = runTest {
        val sessionKey = "sess-no-tools"
        val messageId = "msg-plain"

        fakeMessageDao.seed(
            MessageEntity(
                id = messageId,
                sessionKey = sessionKey,
                role = "USER",
                content = "Hello",
                timestamp = 1_000L,
                messageType = "TEXT",
            )
        )
        // No ToolEventEntities seeded for this message

        repository.loadMessagesFromRoom(sessionKey)

        val messages = repository.messages.value
        assertEquals(1, messages.size)
        assertEquals(emptyList<Any>(), messages[0].toolEvents)
    }

    // -------------------------------------------------------------------------
    // History reload: positional "history-$index" ids must not strand or
    // duplicate rows in Room when a later reload has a different entry count.
    // -------------------------------------------------------------------------

    @Test
    fun `history reload with fewer entries replaces stale rows instead of stranding them`() = runTest {
        val sessionKey = "sess-reload"
        repository.currentSessionKey = sessionKey

        fakeEvents.emit(
            GatewayEvent.HistoryResult(
                entries = listOf(
                    HistoryEntry(role = "user", content = "one", timestamp = 1L),
                    HistoryEntry(role = "assistant", content = "two", timestamp = 2L),
                    HistoryEntry(role = "user", content = "three", timestamp = 3L),
                )
            )
        )
        val firstLoadRows = awaitHistoryRowCount(sessionKey, expected = 3)
        assertEquals(3, firstLoadRows.size)

        // Reload with fewer entries -- the old "history-1"/"history-2" rows must be
        // gone, not stranded, and the surviving row must not be duplicated.
        fakeEvents.emit(
            GatewayEvent.HistoryResult(
                entries = listOf(
                    HistoryEntry(role = "user", content = "one", timestamp = 1L),
                )
            )
        )
        val secondLoadRows = awaitHistoryRowCount(sessionKey, expected = 1)

        assertEquals(1, secondLoadRows.size)
        assertEquals("one", secondLoadRows[0].content)
    }

    @Test
    fun `history reload with the same entries does not duplicate rows`() = runTest {
        val sessionKey = "sess-reload-same"
        repository.currentSessionKey = sessionKey

        val entries = listOf(
            HistoryEntry(role = "user", content = "hello", timestamp = 1L),
            HistoryEntry(role = "assistant", content = "hi there", timestamp = 2L),
        )
        fakeEvents.emit(GatewayEvent.HistoryResult(entries))
        awaitHistoryReplaceCount(1)

        // Same history delivered again (e.g. a second loadHistory() call) must not
        // grow the row count. Wait for this second replace to actually complete
        // (not just for the row count to incidentally already match) before asserting.
        fakeEvents.emit(GatewayEvent.HistoryResult(entries))
        awaitHistoryReplaceCount(2)

        val rows = fakeMessageDao.allRows().filter { it.sessionKey == sessionKey }
        assertEquals(2, rows.size)
    }

    /**
     * Polls the fake DAO until the session's history-sourced row count matches [expected].
     * Uses a real (non-virtual) Thread.sleep: persistence runs on ChatRepository's own
     * Dispatchers.Default scope, not runTest's TestDispatcher, so delay() here would be
     * skipped virtually instead of actually waiting for that other dispatcher to progress
     * (same hazard noted in GatewayClientTest for onClosed/reconnect timing).
     */
    private suspend fun awaitHistoryRowCount(sessionKey: String, expected: Int): List<MessageEntity> {
        var rows: List<MessageEntity> = emptyList()
        var attempts = 0
        while (attempts < 100) {
            rows = fakeMessageDao.allRows().filter { it.sessionKey == sessionKey }
            if (rows.size == expected) break
            Thread.sleep(10)
            attempts++
        }
        return rows
    }

    /** Polls until [FakePersistenceMessageDao.historyReplaceCallCount] reaches [expected]. */
    private suspend fun awaitHistoryReplaceCount(expected: Int) {
        var attempts = 0
        while (fakeMessageDao.historyReplaceCallCount < expected && attempts < 100) {
            Thread.sleep(10)
            attempts++
        }
        assertTrue(
            "Expected historyReplaceCallCount >= $expected, got ${fakeMessageDao.historyReplaceCallCount}",
            fakeMessageDao.historyReplaceCallCount >= expected,
        )
    }
}

// -----------------------------------------------------------------------------
// Fakes — pure JVM, no Room runtime
// -----------------------------------------------------------------------------

private class FakePersistenceMessageDao : MessageDao {

    // ChatRepository launches a fresh coroutine per history/final event (real Room protects
    // this via @Transaction on the real SQLite connection); guard the fake's plain list the
    // same way so concurrent history reloads in tests don't race a ConcurrentModificationException.
    private val mutex = Mutex()
    private val rows = mutableListOf<MessageEntity>()

    /** Incremented each time [replaceHistoryMessages] completes, for test synchronization. */
    var historyReplaceCallCount = 0
        private set

    fun seed(entity: MessageEntity) {
        rows.add(entity)
    }

    override fun observeMessages(sessionKey: String): Flow<List<MessageEntity>> = emptyFlow()

    override suspend fun getMessages(sessionKey: String): List<MessageEntity> = mutex.withLock {
        rows.filter { it.sessionKey == sessionKey }.sortedBy { it.timestamp }
    }

    override suspend fun upsert(message: MessageEntity) = mutex.withLock {
        upsertLocked(message)
    }

    private fun upsertLocked(message: MessageEntity) {
        val idx = rows.indexOfFirst { it.id == message.id }
        if (idx >= 0) rows[idx] = message else rows.add(message)
    }

    override suspend fun upsertAll(messages: List<MessageEntity>) = mutex.withLock {
        messages.forEach { upsertLocked(it) }
    }

    override suspend fun deleteBySession(sessionKey: String): Unit = mutex.withLock {
        rows.removeAll { it.sessionKey == sessionKey }
        Unit
    }

    override suspend fun deleteHistoryMessages(sessionKey: String): Unit = mutex.withLock {
        rows.removeAll { it.sessionKey == sessionKey && it.id.startsWith("history-") }
        Unit
    }

    override suspend fun replaceHistoryMessages(sessionKey: String, messages: List<MessageEntity>) {
        mutex.withLock {
            rows.removeAll { it.sessionKey == sessionKey && it.id.startsWith("history-") }
            messages.forEach { upsertLocked(it) }
        }
        historyReplaceCallCount++
    }

    /** Snapshot of all rows currently held, for assertions. */
    suspend fun allRows(): List<MessageEntity> = mutex.withLock { rows.toList() }

    override suspend fun searchGlobal(query: String): List<SearchResult> = emptyList()

    override suspend fun countBySession(sessionKey: String): Int =
        rows.count { it.sessionKey == sessionKey }

    override suspend fun getPendingMessages(): List<MessageEntity> =
        rows.filter { it.syncState == "PENDING" }

    override suspend fun updateSyncState(messageId: String, syncState: String) {
        val idx = rows.indexOfFirst { it.id == messageId }
        if (idx >= 0) rows[idx] = rows[idx].copy(syncState = syncState)
    }
}

private class FakePersistenceToolEventDao : ToolEventDao {

    private val rows = mutableListOf<ToolEventEntity>()
    private var nextId = 1L

    override suspend fun upsert(event: ToolEventEntity): Long {
        val existing = rows.indexOfFirst { it.toolCallId == event.toolCallId }
        return if (existing >= 0) {
            val assignedId = rows[existing].id
            rows[existing] = event.copy(id = assignedId)
            assignedId
        } else {
            val assignedId = nextId++
            rows.add(event.copy(id = assignedId))
            assignedId
        }
    }

    override suspend fun getByMessageId(messageId: String): List<ToolEventEntity> =
        rows.filter { it.messageId == messageId }.sortedBy { it.startedAt }

    override suspend fun getByToolCallId(toolCallId: String): ToolEventEntity? =
        rows.firstOrNull { it.toolCallId == toolCallId }

    override suspend fun updateResult(
        toolCallId: String,
        status: String,
        output: String?,
        error: String?,
        duration: Long?,
    ) {
        val idx = rows.indexOfFirst { it.toolCallId == toolCallId }
        if (idx >= 0) {
            rows[idx] = rows[idx].copy(
                status = status,
                outputJson = output,
                error = error,
                durationMs = duration,
            )
        }
    }

    override suspend fun deleteByMessageId(messageId: String) {
        rows.removeAll { it.messageId == messageId }
    }
}

private class PersistenceFakeGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame = ResponseFrame(id = "fake", ok = true)
}
