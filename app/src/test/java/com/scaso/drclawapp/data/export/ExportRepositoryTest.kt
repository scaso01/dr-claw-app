package com.scaso.drclawapp.data.export

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExportRepositoryTest {

    private lateinit var fakeClient: FakeExportGatewayClient
    private lateinit var repository: ExportRepository
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeExportGatewayClient()
        repository = ExportRepository(fakeClient)
    }

    @Test
    fun `exportConversations calls export-conversations and parses success payload`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"success": true, "count": 42}"""),
        )

        val result = repository.exportConversations()

        assertEquals("export.conversations", fakeClient.lastMethod)
        assertTrue(result.success)
        assertEquals(42, result.count)
    }

    @Test
    fun `exportConversations returns failure result on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false, error = ErrorShape(code = "E", message = "Export failed"))

        val result = repository.exportConversations()

        assertFalse(result.success)
        assertEquals("Export failed", result.error)
    }

    @Test
    fun `exportConversations returns failure result on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.exportConversations()

        assertFalse(result.success)
        assertEquals("Test export exception", result.error)
    }

    @Test
    fun `exportBrain calls export-brain`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"success": true}"""),
        )

        val result = repository.exportBrain()

        assertEquals("export.brain", fakeClient.lastMethod)
        assertTrue(result.success)
    }

    @Test
    fun `exportBrain returns failure result on ok=false`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)

        val result = repository.exportBrain()

        assertFalse(result.success)
        assertEquals("Export failed", result.error)
    }

    @Test
    fun `createBackup calls backup-create`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1", ok = true,
            payload = json.parseToJsonElement("""{"success": true}"""),
        )

        val result = repository.createBackup()

        assertEquals("backup.create", fakeClient.lastMethod)
        assertTrue(result.success)
    }

    @Test
    fun `createBackup returns failure result on exception`() = runTest {
        fakeClient.shouldThrow = true

        val result = repository.createBackup()

        assertFalse(result.success)
        assertEquals("Test export exception", result.error)
    }
}

private class FakeExportGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false
    var lastMethod: String? = null

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        lastMethod = method
        if (shouldThrow) throw RuntimeException("Test export exception")
        return nextResponse
    }
}
