package com.scaso.drclawapp.data.infra

import app.cash.turbine.test
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.ErrorShape
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.HelloOk
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InfraRepositoryTest {

    private lateinit var fakeClient: FakeInfraGatewayClient
    private lateinit var repository: InfraRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeInfraGatewayClient()
        repository = InfraRepository(fakeClient, scope)
    }

    @Test
    fun `initial status has no components`() {
        assertTrue(repository.status.value.components.isEmpty())
    }

    @Test
    fun `checkStatus includes gateway component when connected`() = runTest {
        fakeClient.fakeConnectionState.value = ConnectionState.Connected(HelloOk(protocol = 3))

        val payload = json.encodeToJsonElement(
            IronjawMetrics(uptimeSecs = 3661, devicesConnected = 2),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.status.test {
            skipItems(1) // initial empty
            repository.checkStatus()
            val result = awaitItem()
            // First component is the gateway (locally added)
            assertEquals("Ironjaw Gateway", result.components[0].name)
            assertEquals(ComponentStatus.HEALTHY, result.components[0].status)
            // Second is uptime from metrics
            assertEquals("Uptime", result.components[1].name)
            // Third is connected devices
            assertEquals("Connected Devices", result.components[2].name)
            assertNotNull(result.checkedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkStatus shows gateway as ERROR when disconnected`() = runTest {
        fakeClient.fakeConnectionState.value = ConnectionState.Disconnected

        val payload = json.encodeToJsonElement(InfraStatusResponse())
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.status.test {
            skipItems(1)
            repository.checkStatus()
            val result = awaitItem()
            assertEquals("Ironjaw Gateway", result.components[0].name)
            assertEquals(ComponentStatus.ERROR, result.components[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkStatus shows gateway as WARNING when connecting`() = runTest {
        fakeClient.fakeConnectionState.value = ConnectionState.Connecting

        val payload = json.encodeToJsonElement(InfraStatusResponse())
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.status.test {
            skipItems(1)
            repository.checkStatus()
            val result = awaitItem()
            assertEquals(ComponentStatus.WARNING, result.components[0].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkStatus fallback on error response`() = runTest {
        fakeClient.fakeConnectionState.value = ConnectionState.Disconnected
        fakeClient.nextResponse = ResponseFrame(
            id = "1",
            ok = false,
            error = ErrorShape(code = "NOT_IMPL", message = "Not implemented"),
        )

        repository.status.test {
            skipItems(1)
            repository.checkStatus()
            val result = awaitItem()
            // Should still have gateway component
            assertEquals(1, result.components.size)
            assertEquals("Ironjaw Gateway", result.components[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkStatus sets error on exception`() = runTest {
        fakeClient.shouldThrow = true

        repository.error.test {
            assertNull(awaitItem())
            repository.checkStatus()
            assertEquals("Test infra exception", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeInfraGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    val fakeConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false

    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        if (shouldThrow) throw RuntimeException("Test infra exception")
        return nextResponse
    }
}
