package com.scaso.drclawapp.data.security

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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityRepositoryTest {

    private lateinit var fakeClient: FakeSecurityGatewayClient
    private lateinit var repository: SecurityRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeSecurityGatewayClient()
        repository = SecurityRepository(fakeClient, scope)
    }

    @Test
    fun `initial rules is empty`() {
        assertTrue(repository.rules.value.isEmpty())
    }

    @Test
    fun `loadRules sorts results by createdAt descending`() = runTest {
        val payload = buildJsonArray {
            add(json.encodeToJsonElement(PermissionRule(id = "1", toolPattern = "bash*", action = "allow", createdAt = 100)))
            add(json.encodeToJsonElement(PermissionRule(id = "2", toolPattern = "write*", action = "deny", createdAt = 300)))
            add(json.encodeToJsonElement(PermissionRule(id = "3", toolPattern = "read*", action = "allow", createdAt = 200)))
        }
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.rules.test {
            skipItems(1) // initial empty
            repository.loadRules()
            val result = awaitItem()
            assertEquals(listOf("2", "3", "1"), result.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadRules leaves rules empty on ok=false response`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1",
            ok = false,
            error = ErrorShape(code = "ERR", message = "boom"),
        )

        repository.loadRules()
        // scope runs on Dispatchers.Default; let it complete before asserting.
        repository.isLoading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repository.rules.value.isEmpty())
    }

    @Test
    fun `loadRules sets error on exception`() = runTest {
        fakeClient.shouldThrow = true

        repository.error.test {
            assertNull(awaitItem())
            repository.loadRules()
            assertEquals("Test security exception", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addRule sends toolPattern pathPattern and action params`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)

        repository.addRule("bash*", "/home/*", "allow")

        assertEquals("security.rules.add", fakeClient.lastMethod)
        val params = fakeClient.lastParams!!.jsonObject
        assertEquals("bash*", params["toolPattern"]!!.jsonPrimitive.content)
        assertEquals("/home/*", params["pathPattern"]!!.jsonPrimitive.content)
        assertEquals("allow", params["action"]!!.jsonPrimitive.content)
    }

    @Test
    fun `addRule omits pathPattern key when null`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)

        repository.addRule("bash*", null, "deny")

        val params = fakeClient.lastParams!!.jsonObject
        assertFalse(params.containsKey("pathPattern"))
    }

    @Test
    fun `deleteRule sends id param`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true)

        repository.deleteRule("rule-42")

        assertEquals("security.rules.delete", fakeClient.lastMethod)
        assertEquals("rule-42", fakeClient.lastParams!!.jsonObject["id"]!!.jsonPrimitive.content)
    }
}

private class FakeSecurityGatewayClient : GatewayClient(
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
        if (shouldThrow) throw RuntimeException("Test security exception")
        return nextResponse
    }
}
