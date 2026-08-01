package com.scaso.drclawapp.data.device

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

class DeviceRepositoryTest {

    private lateinit var fakeClient: FakeDeviceGatewayClient
    private lateinit var repository: DeviceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeDeviceGatewayClient()
        repository = DeviceRepository(fakeClient, scope)
    }

    @Test
    fun `listDevices parses nested devices array`() = runTest {
        val payload = json.parseToJsonElement(
            """{"devices": [{"id": "d1", "name": "Phone", "platform": "android"}], "count": 1}""",
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.devices.test {
            skipItems(1)
            repository.listDevices()
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("d1", result[0].id)
            assertEquals("Phone", result[0].name)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("device.list", fakeClient.lastMethod)
    }

    @Test
    fun `listDevices sets error when devices key missing`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = json.parseToJsonElement("{}"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listDevices()
            assertEquals("Missing 'devices' key in response", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `listDevices sets error on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Device list broke"))

        repository.error.test {
            assertNull(awaitItem())
            repository.listDevices()
            assertEquals("Device list broke", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendCommand sends id and command params`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"success": true, "output": "done"}"""),
        )

        val result = repository.sendCommand("d1", "reboot")

        assertEquals("device.cmd", fakeClient.lastMethod)
        val params = fakeClient.lastParams!!.jsonObject
        assertEquals("d1", params["id"]!!.jsonPrimitive.content)
        assertEquals("reboot", params["command"]!!.jsonPrimitive.content)
        assertTrue(result.success)
        assertEquals("done", result.output)
    }

    @Test
    fun `sendCommand returns failure result on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Command rejected"))

        val result = repository.sendCommand("d1", "reboot")

        assertFalse(result.success)
        assertEquals("Command rejected", result.error)
    }

    @Test
    fun `sendCommand returns failure result on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.sendCommand("d1", "reboot")

        assertFalse(result.success)
        assertEquals("Test device exception", result.error)
    }
}

private class FakeDeviceGatewayClient : GatewayClient(
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
        MutableSharedFlow(extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        lastMethod = method
        lastParams = params
        if (shouldThrow) throw RuntimeException("Test device exception")
        return nextResponse
    }
}
