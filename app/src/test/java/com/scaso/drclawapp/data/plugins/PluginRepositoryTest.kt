package com.scaso.drclawapp.data.plugins

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

class PluginRepositoryTest {

    private lateinit var fakeClient: FakePluginGatewayClient
    private lateinit var repository: PluginRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakePluginGatewayClient()
        repository = PluginRepository(fakeClient, scope)
    }

    @Test
    fun `listPlugins parses nested plugins array`() = runTest {
        fakeClient.responsesByMethod["plugins.list"] = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"plugins": [{"id": "p1", "name": "Weather", "enabled": true}]}"""),
        )

        repository.plugins.test {
            skipItems(1)
            repository.listPlugins()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("p1", result[0].id)
            assertTrue(result[0].enabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listPlugins sets error when plugins key missing`() = runTest {
        fakeClient.responsesByMethod["plugins.list"] = ResponseFrame(id = "1", ok = true, payload = json.parseToJsonElement("{}"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listPlugins()
            assertEquals("Missing 'plugins' key in response", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listPlugins sets error on ok=false`() = runTest {
        fakeClient.responsesByMethod["plugins.list"] = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Plugin list broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listPlugins()
            assertEquals("Plugin list broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `togglePlugin sends id and enabled params and returns true on success`() = runTest {
        fakeClient.responsesByMethod["plugins.toggle"] = ResponseFrame(id = "1", ok = true)
        fakeClient.responsesByMethod["plugins.list"] = ResponseFrame(
            id = "2", ok = true,
            payload = json.parseToJsonElement("""{"plugins": []}"""),
        )

        val result = repository.togglePlugin("p1", false)

        assertTrue(result)
        val toggleCall = fakeClient.calls.first { it.first == "plugins.toggle" }
        val params = toggleCall.second!!.jsonObject
        assertEquals("p1", params["id"]!!.jsonPrimitive.content)
        assertFalse(params["enabled"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `togglePlugin reloads the plugin list on success`() = runTest {
        fakeClient.responsesByMethod["plugins.toggle"] = ResponseFrame(id = "1", ok = true)
        fakeClient.responsesByMethod["plugins.list"] = ResponseFrame(
            id = "2", ok = true,
            payload = json.parseToJsonElement("""{"plugins": [{"id": "p1", "name": "Weather", "enabled": false}]}"""),
        )

        repository.plugins.test {
            skipItems(1)
            repository.togglePlugin("p1", false)
            val result = awaitItem()
            assertEquals(1, result.size)
            assertFalse(result[0].enabled)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(fakeClient.calls.any { it.first == "plugins.list" })
    }

    @Test
    fun `togglePlugin returns false on ok=false without reloading`() = runTest {
        fakeClient.responsesByMethod["plugins.toggle"] = ResponseFrame(id = "1", ok = false)

        val result = repository.togglePlugin("p1", true)

        assertFalse(result)
        assertFalse(fakeClient.calls.any { it.first == "plugins.list" })
    }

    @Test
    fun `togglePlugin returns false on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.togglePlugin("p1", true)

        assertFalse(result)
    }
}

private class FakePluginGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** Per-method canned response; falls back to ok=true empty response if unset. */
    val responsesByMethod = mutableMapOf<String, ResponseFrame>()
    val calls = mutableListOf<Pair<String, JsonElement?>>()
    var shouldThrow = false

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        calls.add(method to params)
        if (shouldThrow) throw RuntimeException("Test plugin exception")
        return responsesByMethod[method] ?: ResponseFrame(id = "0", ok = true)
    }
}
