package com.scaso.drclawapp.data.chronicle

import app.cash.turbine.test
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ChronicleRepositoryTest {

    private lateinit var fakeClient: FakeChronicleGatewayClient
    private lateinit var repository: ChronicleRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeChronicleGatewayClient()
        repository = ChronicleRepository(fakeClient, scope)
    }

    @Test
    fun `getSessions parses nested session plus summary format`() = runTest {
        val payload = json.parseToJsonElement(
            """
            {"sessions": [{
                "session": {"session_id": "s1", "machine": "workstation", "started_at": "2026-01-01", "project": "proj"},
                "summary": {"title": "t1", "summary": "sum1"}
            }]}
            """.trimIndent(),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.sessions.test {
            skipItems(1)
            repository.getSessions()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("s1", result[0].id)
            assertEquals("t1", result[0].title)
            assertEquals("workstation", result[0].machine)
            assertEquals("sum1", result[0].summary)
            assertEquals("proj", result[0].project)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSessions falls back to flat format when session key absent`() = runTest {
        val payload = json.parseToJsonElement(
            """{"sessions": [{"id": "s2", "title": "t2", "machine": "docker-host"}]}""",
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.sessions.test {
            skipItems(1)
            repository.getSessions()
            val result = awaitItem()
            assertEquals("s2", result[0].id)
            assertEquals("t2", result[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSessions sets error when sessions key missing`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = buildJsonObject {})

        repository.error.test {
            assertNull(awaitItem())
            repository.getSessions()
            assertEquals("Unexpected response format: missing 'sessions' key", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSessions sets error on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Sessions broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.getSessions()
            assertEquals("Sessions broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search parses nested session plus summary plus score`() = runTest {
        val payload = json.parseToJsonElement(
            """
            {"results": [{
                "session": {"session_id": "s1"},
                "summary": {"title": "t1", "summary": "snip1"},
                "score": 0.9
            }]}
            """.trimIndent(),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.searchResults.test {
            skipItems(1)
            repository.search("query")
            val result = awaitItem()
            assertEquals("s1", result[0].sessionId)
            assertEquals("t1", result[0].title)
            assertEquals("snip1", result[0].snippet)
            assertEquals(0.9f, result[0].score)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("query", (fakeClient.lastParams as JsonObject)["query"]!!.jsonPrimitive.content)
    }

    @Test
    fun `search falls back to flat result format when session key absent`() = runTest {
        val payload = json.parseToJsonElement(
            """{"results": [{"session_id": "s2", "title": "t2", "snippet": "snip2", "score": 0.5}]}""",
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.searchResults.test {
            skipItems(1)
            repository.search("query")
            val result = awaitItem()
            assertEquals("s2", result[0].sessionId)
            assertEquals("t2", result[0].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search sets error when results key missing and payload is not an array`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = buildJsonObject {})

        repository.error.test {
            assertNull(awaitItem())
            repository.search("query")
            assertNotNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTopics parses topics array`() = runTest {
        val payload = json.parseToJsonElement(
            """{"topics": [{"name": "example-pipeline", "session_count": 3}]}""",
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.topics.test {
            skipItems(1)
            repository.getTopics()
            val result = awaitItem()
            assertEquals("example-pipeline", result[0].name)
            assertEquals(3, result[0].sessionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTopics sets error on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Topics broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.getTopics()
            assertEquals("Topics broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getStats parses stats payload`() = runTest {
        val payload = json.parseToJsonElement(
            """{"total_sessions": 42, "avg_length": 12.5}""",
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.stats.test {
            assertNull(awaitItem())
            repository.getStats()
            val result = awaitItem()
            assertEquals(42, result!!.totalSessions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getInsights parses insights payload`() = runTest {
        val payload = json.parseToJsonElement(
            """{"totalSessions": 10, "current_focus": [{"project": "example-pipeline", "session_count": 2}]}""",
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.insights.test {
            assertNull(awaitItem())
            repository.getInsights()
            val result = awaitItem()
            assertEquals(10, result!!.totalSessions)
            assertEquals("example-pipeline", result.currentFocus[0].project)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeChronicleGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var lastParams: JsonElement? = null

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        lastParams = params
        return nextResponse
    }
}
