package com.scaso.drclawapp.data.tools

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolRepositoryTest {

    private lateinit var fakeClient: FakeToolGatewayClient
    private lateinit var repository: ToolRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeToolGatewayClient()
        repository = ToolRepository(fakeClient, scope)
    }

    @Test
    fun `listTools parses nested tools array`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"tools": [{"name": "bash", "tier": 2}], "count": 1}"""),
        )

        repository.tools.test {
            skipItems(1)
            repository.listTools()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("bash", result[0].name)
            assertEquals(2, result[0].tier)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listTools sets error when tools key missing`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.parseToJsonElement("{}"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listTools()
            assertEquals("Missing 'tools' key in response", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listTools sets error on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Tool list broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listTools()
            assertEquals("Tool list broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeTool sends name and nested params`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"success": true}"""),
        )
        val toolParams = json.parseToJsonElement("""{"path": "/tmp/x"}""")

        val result = repository.executeTool("read_file", toolParams)

        assertEquals("tools.execute", fakeClient.lastMethod)
        val sent = fakeClient.lastParams!!.jsonObject
        assertEquals("read_file", sent["name"]!!.jsonPrimitive.content)
        assertEquals(toolParams, sent["params"])
        assertTrue(result.success)
    }

    @Test
    fun `executeTool omits params key when null`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.parseToJsonElement("""{"success": true}"""))

        repository.executeTool("list_tools", null)

        assertFalse(fakeClient.lastParams!!.jsonObject.containsKey("params"))
    }

    @Test
    fun `executeTool returns failure result on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Execution rejected"))

        val result = repository.executeTool("read_file", null)

        assertFalse(result.success)
        assertEquals("Execution rejected", result.error)
    }

    @Test
    fun `executeTool returns failure result on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.executeTool("read_file", null)

        assertFalse(result.success)
        assertEquals("Test tool exception", result.error)
    }
}

private class FakeToolGatewayClient : GatewayClient(
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
        if (shouldThrow) throw RuntimeException("Test tool exception")
        return nextResponse
    }
}
