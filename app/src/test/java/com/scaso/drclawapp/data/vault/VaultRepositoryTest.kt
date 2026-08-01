package com.scaso.drclawapp.data.vault

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

class VaultRepositoryTest {

    private lateinit var fakeClient: FakeVaultGatewayClient
    private lateinit var repository: VaultRepository
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeVaultGatewayClient()
        repository = VaultRepository(fakeClient)
    }

    @Test
    fun `getSecret sends key param and parses success payload`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"success": true, "value": "s3cr3t"}"""),
        )

        val result = repository.getSecret("api_key")

        assertEquals("vault.get", fakeClient.lastMethod)
        assertEquals("api_key", fakeClient.lastParams!!.jsonObject["key"]!!.jsonPrimitive.content)
        assertTrue(result.success)
        assertEquals("s3cr3t", result.value)
    }

    @Test
    fun `getSecret returns failure result on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Key not found"))

        val result = repository.getSecret("missing_key")

        assertFalse(result.success)
        assertEquals("Key not found", result.error)
        assertNull(result.value)
    }

    @Test
    fun `getSecret returns failure result on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.getSecret("api_key")

        assertFalse(result.success)
        assertEquals("Test vault exception", result.error)
    }

    @Test
    fun `setSecret sends key and value params and reports success on ok=true`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)

        val result = repository.setSecret("api_key", "s3cr3t")

        assertEquals("vault.set", fakeClient.lastMethod)
        val params = fakeClient.lastParams!!.jsonObject
        assertEquals("api_key", params["key"]!!.jsonPrimitive.content)
        assertEquals("s3cr3t", params["value"]!!.jsonPrimitive.content)
        assertTrue(result.success)
    }

    @Test
    fun `setSecret returns failure result on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Write rejected"))

        val result = repository.setSecret("api_key", "s3cr3t")

        assertFalse(result.success)
        assertEquals("Write rejected", result.error)
    }

    @Test
    fun `setSecret returns failure result on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.setSecret("api_key", "s3cr3t")

        assertFalse(result.success)
        assertEquals("Test vault exception", result.error)
    }
}

private class FakeVaultGatewayClient : GatewayClient(
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
        if (shouldThrow) throw RuntimeException("Test vault exception")
        return nextResponse
    }
}
