package com.scaso.drclawapp.data.repository

import app.cash.turbine.test
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.dao.SearchResult
import com.scaso.drclawapp.data.local.dao.SessionDao
import com.scaso.drclawapp.data.local.entity.ChatSessionEntity
import com.scaso.drclawapp.data.local.entity.MessageEntity
import com.scaso.drclawapp.data.model.ChatSession
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.ErrorShape
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionRepositoryTest {

    private lateinit var fakeGateway: FakeSessionGatewayClient
    private lateinit var fakeChatRepo: ChatRepository
    private lateinit var fakeSessionDao: FakeSessionDao
    private lateinit var fakeMessageDao: FakeMessageDao
    private lateinit var repository: SessionRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        val fakeEvents = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
        val fakeConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        fakeGateway = FakeSessionGatewayClient(fakeEvents, fakeConnectionState)
        fakeChatRepo = ChatRepository(fakeGateway, scope)
        fakeSessionDao = FakeSessionDao()
        fakeMessageDao = FakeMessageDao()
        repository = SessionRepository(
            gatewayClient = fakeGateway,
            chatRepository = fakeChatRepo,
            scope = scope,
            sessionDao = fakeSessionDao,
            messageDao = fakeMessageDao,
        )
    }

    // ── Initial state ───────────────────────────────────────────────

    @Test
    fun `initial sessions list is empty`() {
        assertTrue(repository.sessions.value.isEmpty())
    }

    @Test
    fun `initial currentSessionKey is null`() {
        assertNull(repository.currentSessionKey.value)
    }

    // ── loadSessions ────────────────────────────────────────────────

    @Test
    fun `loadSessions populates sessions from gateway`() = runTest {
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    {
                        "id": "sess-1",
                        "title": "First Chat",
                        "created_at": "2026-03-30T10:00:00Z",
                        "updated_at": "2026-03-30T11:00:00Z",
                        "message_count": 5
                    },
                    {
                        "id": "sess-2",
                        "title": "Second Chat",
                        "created_at": "2026-03-29T10:00:00Z",
                        "message_count": 3
                    }
                ],
                "count": 2
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.sessions.test {
            assertEquals(emptyList<ChatSession>(), awaitItem()) // initial
            repository.loadSessions()
            val sessions = awaitItem()
            assertEquals(2, sessions.size)
            assertEquals("sess-1", sessions[0].sessionKey)
            assertEquals("First Chat", sessions[0].title)
            assertEquals(5, sessions[0].messageCount)
            assertEquals("sess-2", sessions[1].sessionKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSessions auto-selects most recently updated session`() = runTest {
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    {
                        "id": "old-sess",
                        "created_at": "2026-03-28T10:00:00Z",
                        "updated_at": "2026-03-28T10:00:00Z"
                    },
                    {
                        "id": "new-sess",
                        "created_at": "2026-03-30T10:00:00Z",
                        "updated_at": "2026-03-30T12:00:00Z"
                    }
                ],
                "count": 2
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.loadSessions()
        assertEquals("new-sess", repository.currentSessionKey.value)
    }

    @Test
    fun `loadSessions does not override existing currentSessionKey`() = runTest {
        repository.setCurrentSessionKey("existing-key")

        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    {
                        "id": "sess-1",
                        "created_at": "2026-03-30T10:00:00Z",
                        "updated_at": "2026-03-30T12:00:00Z"
                    }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.loadSessions()
        assertEquals("existing-key", repository.currentSessionKey.value)
    }

    @Test
    fun `loadSessions does nothing on failed response`() = runTest {
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = false)
        repository.loadSessions()
        assertTrue(repository.sessions.value.isEmpty())
    }

    @Test
    fun `loadSessions upserts sessions to Room`() = runTest {
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    {
                        "id": "sess-1",
                        "title": "Chat",
                        "created_at": "2026-03-30T10:00:00Z"
                    }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.loadSessions()
        assertEquals(1, fakeSessionDao.upsertedAll.size)
        assertEquals("sess-1", fakeSessionDao.upsertedAll[0].sessionKey)
    }

    // ── setCurrentSessionKey ────────────────────────────────────────

    @Test
    fun `setCurrentSessionKey updates state and chatRepository`() {
        repository.setCurrentSessionKey("key-42")
        assertEquals("key-42", repository.currentSessionKey.value)
        assertEquals("key-42", fakeChatRepo.currentSessionKey)
    }

    // ── switchSession ───────────────────────────────────────────────

    @Test
    fun `switchSession updates key and calls gateway`() = runTest {
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true)

        repository.switchSession("sess-99")
        assertEquals("sess-99", repository.currentSessionKey.value)
        assertEquals("sess-99", fakeChatRepo.currentSessionKey)
        assertTrue(fakeGateway.switchedTo.contains("sess-99"))
    }

    // ── createNewSession ────────────────────────────────────────────

    @Test
    fun `createNewSession adds session to list`() = runTest {
        val payload = json.parseToJsonElement("""
            { "session": { "id": "new-123" }, "switched": true }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.sessions.test {
            assertEquals(emptyList<ChatSession>(), awaitItem()) // initial
            repository.createNewSession()
            val sessions = awaitItem()
            assertEquals(1, sessions.size)
            assertEquals("new-123", sessions[0].sessionKey)
            assertEquals("New Chat", sessions[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createNewSession sets new session as current`() = runTest {
        val payload = json.parseToJsonElement("""
            { "session": { "id": "created-1" }, "switched": true }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.createNewSession()
        assertEquals("created-1", repository.currentSessionKey.value)
    }

    @Test
    fun `createNewSession handles legacy session_id format`() = runTest {
        val payload = json.parseToJsonElement("""
            { "session_id": "legacy-1" }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.createNewSession()
        assertEquals("legacy-1", repository.currentSessionKey.value)
    }

    @Test
    fun `createNewSession does nothing on failure`() = runTest {
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = false)
        repository.createNewSession()
        assertNull(repository.currentSessionKey.value)
        assertTrue(repository.sessions.value.isEmpty())
    }

    // ── renameSession ───────────────────────────────────────────────

    @Test
    fun `renameSession updates in-memory list and Room`() = runTest {
        // Seed a session
        val payload = json.parseToJsonElement("""
            { "session": { "id": "sess-1" }, "switched": true }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.createNewSession()

        // Rename
        fakeGateway.nextResponse = ResponseFrame(id = "2", ok = true)
        repository.renameSession("sess-1", "Renamed")
        assertEquals("Renamed", repository.sessions.value.first { it.sessionKey == "sess-1" }.title)
        assertEquals("Renamed", fakeSessionDao.updatedTitles["sess-1"])
    }

    @Test
    fun `renameSession does nothing on failure`() = runTest {
        // Seed a session
        val createPayload = json.parseToJsonElement("""
            { "session": { "id": "sess-1" }, "switched": true }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = createPayload)
        repository.createNewSession()

        fakeGateway.nextResponse = ResponseFrame(id = "2", ok = false)
        repository.renameSession("sess-1", "Should Not Update")
        assertEquals("New Chat", repository.sessions.value.first { it.sessionKey == "sess-1" }.title)
    }

    // ── deleteSession ───────────────────────────────────────────────

    @Test
    fun `deleteSession removes session from list`() = runTest {
        // Seed two sessions
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "title": "First", "created_at": "2026-03-30T10:00:00Z", "updated_at": "2026-03-30T12:00:00Z" },
                    { "id": "sess-2", "title": "Second", "created_at": "2026-03-29T10:00:00Z", "updated_at": "2026-03-29T10:00:00Z" }
                ],
                "count": 2
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()
        assertEquals(2, repository.sessions.value.size)

        // Delete non-active session
        fakeGateway.nextResponse = ResponseFrame(id = "2", ok = true)
        repository.deleteSession("sess-2")
        assertEquals(1, repository.sessions.value.size)
        assertEquals("sess-1", repository.sessions.value[0].sessionKey)
        assertTrue(fakeSessionDao.deletedKeys.contains("sess-2"))
    }

    @Test
    fun `deleteSession creates new session first when deleting active`() = runTest {
        // Seed two sessions
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "2026-03-30T10:00:00Z", "updated_at": "2026-03-30T12:00:00Z" },
                    { "id": "sess-2", "created_at": "2026-03-29T10:00:00Z", "updated_at": "2026-03-29T10:00:00Z" }
                ],
                "count": 2
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()

        // Set current to sess-2, then delete it — triggers createNewSession first
        repository.setCurrentSessionKey("sess-2")

        val createPayload = json.parseToJsonElement("""
            { "session": { "id": "new-sess" }, "switched": true }
        """.trimIndent())
        // Queue: create new session, then delete succeeds
        fakeGateway.responseQueue.add(ResponseFrame(id = "c", ok = true, payload = createPayload))
        fakeGateway.responseQueue.add(ResponseFrame(id = "d", ok = true))

        repository.deleteSession("sess-2")
        // sess-2 should be removed, new-sess should be in the list
        assertTrue(repository.sessions.value.none { it.sessionKey == "sess-2" })
        assertTrue(repository.sessions.value.any { it.sessionKey == "new-sess" })
    }

    @Test
    fun `deleteSession retries on CANNOT_DELETE_ACTIVE error`() = runTest {
        // Seed two sessions, neither is current
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "2026-03-30T10:00:00Z", "updated_at": "2026-03-30T12:00:00Z" },
                    { "id": "sess-2", "created_at": "2026-03-29T10:00:00Z", "updated_at": "2026-03-29T10:00:00Z" }
                ],
                "count": 2
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()
        // Set current to sess-1 so deleting sess-2 doesn't trigger create-first path
        repository.setCurrentSessionKey("sess-1")

        // First delete returns CANNOT_DELETE_ACTIVE, retry succeeds
        fakeGateway.responseQueue.add(ResponseFrame(
            id = "d1", ok = false,
            error = ErrorShape(code = "CANNOT_DELETE_ACTIVE", message = "Cannot delete active session"),
        ))
        fakeGateway.responseQueue.add(ResponseFrame(id = "d2", ok = true)) // retry succeeds

        repository.deleteSession("sess-2")
        assertTrue(repository.sessions.value.none { it.sessionKey == "sess-2" })
    }

    @Test
    fun `deleteSession throws on persistent failure`() = runTest {
        // Seed a session
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "2026-03-30T10:00:00Z", "updated_at": "2026-03-30T12:00:00Z" }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()

        fakeGateway.nextResponse = ResponseFrame(
            id = "2", ok = false,
            error = ErrorShape(code = "INTERNAL_ERROR", message = "Server error"),
        )

        var threw = false
        try {
            repository.deleteSession("sess-1")
        } catch (e: IllegalStateException) {
            threw = true
            assertTrue(e.message!!.contains("Server error"))
        }
        assertTrue("Expected deleteSession to throw on persistent failure", threw)
    }

    // ── pinSession / archiveSession ─────────────────────────────────

    @Test
    fun `pinSession delegates to DAO`() = runTest {
        repository.pinSession("sess-1", true)
        assertEquals(true, fakeSessionDao.pinnedState["sess-1"])
    }

    @Test
    fun `archiveSession removes from in-memory list`() = runTest {
        // Seed a session
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "2026-03-30T10:00:00Z" }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()
        assertEquals(1, repository.sessions.value.size)

        repository.archiveSession("sess-1", true)
        assertTrue(repository.sessions.value.isEmpty())
        assertEquals(true, fakeSessionDao.archivedState["sess-1"])
    }

    @Test
    fun `archiveSession with false does not remove from list`() = runTest {
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "2026-03-30T10:00:00Z" }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()

        repository.archiveSession("sess-1", false)
        assertEquals(1, repository.sessions.value.size)
    }

    // ── updateSessionContext ─────────────────────────────────────────

    @Test
    fun `updateSessionContext updates in-memory and Room`() = runTest {
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "2026-03-30T10:00:00Z" }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()

        repository.updateSessionContext("sess-1", contextPct = 42, contextWindow = 200000, contextTokens = 80000)
        val session = repository.sessions.value.first { it.sessionKey == "sess-1" }
        assertEquals(42, session.contextPct)
        assertEquals(200000, session.contextWindow)
        assertEquals(80000, session.contextTokens)
        assertTrue(fakeSessionDao.contextUpdated.containsKey("sess-1"))
    }

    // ── forkFromMessage ─────────────────────────────────────────────

    @Test
    fun `forkFromMessage creates forked session in list`() = runTest {
        // Set up current session
        repository.setCurrentSessionKey("original-sess")
        val sessions = listOf(
            ChatSession(sessionKey = "original-sess", title = "Original", createdAt = 1000L),
        )
        // Load sessions into state
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "original-sess", "title": "Original", "created_at": "2026-03-30T10:00:00Z" }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()
        repository.setCurrentSessionKey("original-sess")

        val message = com.scaso.drclawapp.data.model.Message(
            id = "msg-3",
            role = com.scaso.drclawapp.data.model.Role.USER,
            content = "Hello",
            timestamp = 3000L,
        )
        val allMessages = listOf(
            com.scaso.drclawapp.data.model.Message(id = "msg-1", role = com.scaso.drclawapp.data.model.Role.USER, content = "Hi", timestamp = 1000L),
            com.scaso.drclawapp.data.model.Message(id = "msg-2", role = com.scaso.drclawapp.data.model.Role.ASSISTANT, content = "Hey", timestamp = 2000L),
            message,
        )

        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true) // switchSession

        repository.forkFromMessage(message, allMessages)

        // Should have 2 sessions now (original + fork)
        val sessionList = repository.sessions.value
        assertEquals(2, sessionList.size)
        assertTrue(sessionList.any { it.title?.startsWith("Fork:") == true })

        // Fork should have 3 messages persisted
        assertEquals(3, fakeMessageDao.upsertedAll.size)

        // Session should be persisted to Room
        assertTrue(fakeSessionDao.upsertedSingle.any { it.forkedFromSessionKey == "original-sess" })
    }

    // ── parseIsoToEpochMs (tested indirectly) ───────────────────────

    @Test
    fun `loadSessions handles malformed dates gracefully`() = runTest {
        val payload = json.parseToJsonElement("""
            {
                "sessions": [
                    { "id": "sess-1", "created_at": "not-a-date" }
                ],
                "count": 1
            }
        """.trimIndent())
        fakeGateway.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)
        repository.loadSessions()
        assertEquals(0L, repository.sessions.value[0].createdAt)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────

/**
 * Fake GatewayClient for SessionRepository tests.
 * Supports a single nextResponse or a queue for multi-call scenarios.
 * All session API methods are overridden to return fake responses.
 */
private class FakeSessionGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    val responseQueue = ArrayDeque<ResponseFrame>()
    val switchedTo = mutableListOf<String>()
    var storedSessionKey: String? = null

    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    private fun dequeueOrDefault(): ResponseFrame {
        return if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else nextResponse
    }

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame = dequeueOrDefault()

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame = dequeueOrDefault()

    override suspend fun listSessions(limit: Int?, offset: Int?): ResponseFrame = dequeueOrDefault()

    override suspend fun createSession(title: String?): ResponseFrame = dequeueOrDefault()

    override suspend fun deleteSession(sessionId: String): ResponseFrame = dequeueOrDefault()

    override suspend fun patchSession(sessionKey: String, title: String?): ResponseFrame = dequeueOrDefault()

    override suspend fun switchSession(newSessionKey: String) {
        switchedTo.add(newSessionKey)
    }

    override fun setLocalSessionKey(key: String) {
        storedSessionKey = key
    }

    override suspend fun loadHistory(limit: Int?): ResponseFrame = dequeueOrDefault()
}

/**
 * In-memory SessionDao fake for unit testing.
 */
private class FakeSessionDao : SessionDao {
    val upsertedAll = mutableListOf<ChatSessionEntity>()
    val upsertedSingle = mutableListOf<ChatSessionEntity>()
    val deletedKeys = mutableListOf<String>()
    val updatedTitles = mutableMapOf<String, String>()
    val pinnedState = mutableMapOf<String, Boolean>()
    val archivedState = mutableMapOf<String, Boolean>()
    val contextUpdated = mutableMapOf<String, Triple<Int?, Int?, Int?>>()
    private val store = mutableMapOf<String, ChatSessionEntity>()

    override fun observeActiveSessions(): Flow<List<ChatSessionEntity>> =
        MutableStateFlow(store.values.filter { !it.isArchived }.toList())

    override fun observeArchivedSessions(): Flow<List<ChatSessionEntity>> =
        MutableStateFlow(store.values.filter { it.isArchived }.toList())

    override suspend fun getByKey(key: String): ChatSessionEntity? = store[key]

    override suspend fun upsert(session: ChatSessionEntity) {
        upsertedSingle.add(session)
        store[session.sessionKey] = session
    }

    override suspend fun upsertAll(sessions: List<ChatSessionEntity>) {
        upsertedAll.addAll(sessions)
        sessions.forEach { store[it.sessionKey] = it }
    }

    override suspend fun setPinned(key: String, pinned: Boolean) {
        pinnedState[key] = pinned
    }

    override suspend fun setArchived(key: String, archived: Boolean) {
        archivedState[key] = archived
    }

    override suspend fun updateTitle(key: String, title: String) {
        updatedTitles[key] = title
    }

    val modeUpdated = mutableMapOf<String, String>()

    override suspend fun updateMode(key: String, mode: String) {
        modeUpdated[key] = mode
    }

    override suspend fun delete(key: String) {
        deletedKeys.add(key)
        store.remove(key)
    }

    override suspend fun count(): Int = store.size

    override suspend fun updateSessionContext(key: String, contextPct: Int?, contextWindow: Int?, contextTokens: Int?) {
        contextUpdated[key] = Triple(contextPct, contextWindow, contextTokens)
    }
}

/**
 * In-memory MessageDao fake for unit testing.
 */
private class FakeMessageDao : MessageDao {
    val upsertedAll = mutableListOf<MessageEntity>()

    override fun observeMessages(sessionKey: String): Flow<List<MessageEntity>> =
        MutableStateFlow(emptyList())

    override suspend fun getMessages(sessionKey: String): List<MessageEntity> = emptyList()

    override suspend fun upsert(message: MessageEntity) {}

    override suspend fun upsertAll(messages: List<MessageEntity>) {
        upsertedAll.addAll(messages)
    }

    override suspend fun deleteBySession(sessionKey: String) {}

    override suspend fun deleteHistoryMessages(sessionKey: String) {}

    override suspend fun searchGlobal(query: String): List<SearchResult> = emptyList()

    override suspend fun countBySession(sessionKey: String): Int = 0

    override suspend fun getPendingMessages(): List<MessageEntity> = emptyList()

    override suspend fun updateSyncState(messageId: String, syncState: String) {}
}
