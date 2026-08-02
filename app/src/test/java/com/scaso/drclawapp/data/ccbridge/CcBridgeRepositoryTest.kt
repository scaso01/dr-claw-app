package com.scaso.drclawapp.data.ccbridge

import app.cash.turbine.test
import com.scaso.drclawapp.data.websocket.CcBridgeClient
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.ErrorShape
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CcBridgeRepositoryTest {

    private lateinit var fakeClient: FakeCcBridgeGatewayClient
    private lateinit var fakeBridgeClient: CcBridgeClient
    private lateinit var repository: CcBridgeRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeCcBridgeGatewayClient()
        fakeBridgeClient = CcBridgeClient(url = "ws://fake", token = "fake", scope = scope)
        val fakePhoneBridge = CcBridgeClient(url = "", token = "fake", scope = scope)
        repository = CcBridgeRepository(fakeClient, fakeBridgeClient, fakePhoneBridge, scope)
    }

    @Test
    fun `initial sessions list is empty`() {
        assertTrue(repository.sessions.value.isEmpty())
    }

    @Test
    fun `initial isLoading is false`() {
        assertFalse(repository.isLoading.value)
    }

    @Test
    fun `initial error is null`() {
        assertNull(repository.error.value)
    }

    @Test
    fun `loadSessions populates sessions on success`() = runTest {
        val payload = json.encodeToJsonElement(
            CcSessionsResponse(
                sessions = listOf(
                    CcSession(sessionId = "s1", project = "proj1", machine = "docker-host", lastActivity = 200),
                    CcSession(sessionId = "s2", project = "proj2", machine = "workstation", lastActivity = 100),
                ),
            ),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.sessions.test {
            assertEquals(emptyList<CcSession>(), awaitItem()) // initial
            repository.loadSessions()
            val sessions = awaitItem()
            assertEquals(2, sessions.size)
            // Should be sorted by lastActivity descending
            assertEquals("s1", sessions[0].sessionId)
            assertEquals("s2", sessions[1].sessionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSessions sets error when both sources fail`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1",
            ok = false,
            error = ErrorShape(code = "NOT_FOUND", message = "Handler not found"),
        )

        repository.error.test {
            assertNull(awaitItem()) // initial
            repository.loadSessions()
            // Both v1 (gateway) and v2 (bridge) fail → generic error
            assertEquals("Failed to load CC sessions from both sources", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadSessions sets error on exception`() = runTest {
        fakeClient.shouldThrow = true

        repository.error.test {
            assertNull(awaitItem())
            repository.loadSessions()
            // Both v1 (throws) and v2 (not connected) fail → generic error
            assertEquals("Failed to load CC sessions from both sources", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendMessageViaGateway returns true on success`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)
        val result = repository.sendMessageViaGateway("s1", "docker-host", "hello")
        assertTrue(result)
    }

    @Test
    fun `sendMessageViaGateway returns false on failure`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)
        val result = repository.sendMessageViaGateway("s1", "docker-host", "hello")
        assertFalse(result)
    }

    // ── Ironjaw session operations ─────────────────────────────────

    @Test
    fun `loadIronjawSessions parses gateway response`() = runTest {
        // Real cc.sessions payload uses "sessionId" (mapped to ccBridgeId) + "title".
        val sessionsJson = """[
            {
                "sessionId": "bridge-1",
                "title": "Ironjaw work",
                "sdkSessionId": "sdk-1",
                "model": "opus",
                "project": "ironjaw",
                "cwd": "/home/user",
                "status": "running",
                "contextPct": 30.0,
                "createdAt": "2026-03-24T10:00:00Z",
                "lastActivity": "2026-03-24T10:30:00Z"
            },
            {
                "sessionId": "bridge-2",
                "project": "example-pipeline",
                "status": "idle",
                "lastActivity": "2026-03-24T09:00:00Z"
            }
        ]"""
        val payload = json.parseToJsonElement(sessionsJson)
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.ironjawSessions.test {
            assertEquals(emptyList<ClaudeSession>(), awaitItem()) // initial
            repository.loadIronjawSessions()
            val sessions = awaitItem()
            assertEquals(2, sessions.size)
            // Sorted by lastActivity descending — "10:30" > "09:00"
            assertEquals("bridge-1", sessions[0].ccBridgeId)
            assertEquals("bridge-2", sessions[1].ccBridgeId)
            assertEquals("opus", sessions[0].model)
            assertEquals("ironjaw", sessions[0].project)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `attachIronjawSession sends correct params`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)
        repository.attachIronjawSession("abc-123")
        assertEquals("cc.attach", fakeClient.lastMethod)
        val params = fakeClient.lastParams.toString()
        assertTrue("params should contain sessionId", params.contains("\"sessionId\":\"abc-123\""))
    }

    @Test
    fun `loadSessionHistory sends correct params`() = runTest {
        val historyPayload = json.parseToJsonElement("""{"messages": []}""")
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = historyPayload)
        repository.loadSessionHistory("abc-123", limit = 50)
        assertEquals("cc.history", fakeClient.lastMethod)
        val params = fakeClient.lastParams.toString()
        assertTrue("params should contain sessionId", params.contains("\"sessionId\":\"abc-123\""))
        assertTrue("params should contain limit", params.contains("\"limit\":50"))
    }
}

private class FakeCcBridgeGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false
    var lastMethod: String? = null
    var lastParams: JsonElement? = null

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        lastMethod = method
        lastParams = params
        if (shouldThrow) throw RuntimeException("Test exception")
        return nextResponse
    }
}
