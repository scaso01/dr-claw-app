package com.scaso.drclawapp.data.filedownload

import app.cash.turbine.test
import com.scaso.drclawapp.data.websocket.ConnectionState
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileDownloadRepositoryTest {

    private lateinit var fakeClient: FakeFileGatewayClient
    private lateinit var repository: FileDownloadRepository
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeFileGatewayClient()
        repository = FileDownloadRepository(fakeClient)
    }

    @Test
    fun `initial downloadProgress is empty`() {
        assertTrue(repository.downloadProgress.value.isEmpty())
    }

    @Test
    fun `downloadFile returns response on success`() = runTest {
        val payload = json.encodeToJsonElement(
            FileGetResponse(
                path = "C:\\data\\file.csv",
                fileName = "file.csv",
                mimeType = "text/csv",
                content = "SGVsbG8=",
                sizeBytes = 5,
            ),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        val result = repository.downloadFile("C:\\data\\file.csv")
        assertNotNull(result)
        assertEquals("file.csv", result!!.fileName)
        assertEquals("text/csv", result.mimeType)
        assertEquals("SGVsbG8=", result.content)
    }

    @Test
    fun `downloadFile sets progress to COMPLETE on success`() = runTest {
        val payload = json.encodeToJsonElement(
            FileGetResponse(path = "C:\\f.txt", fileName = "f.txt", content = "dA=="),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.downloadFile("C:\\f.txt")
        assertEquals(DownloadState.COMPLETE, repository.downloadProgress.value["C:\\f.txt"])
    }

    @Test
    fun `downloadFile returns null on failure response`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)
        val result = repository.downloadFile("C:\\missing.txt")
        assertNull(result)
    }

    @Test
    fun `downloadFile sets progress to ERROR on failure`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)
        repository.downloadFile("C:\\bad.txt")
        assertEquals(DownloadState.ERROR, repository.downloadProgress.value["C:\\bad.txt"])
    }

    @Test
    fun `downloadFile returns null on exception`() = runTest {
        fakeClient.shouldThrow = true
        val result = repository.downloadFile("C:\\crash.txt")
        assertNull(result)
        assertEquals(DownloadState.ERROR, repository.downloadProgress.value["C:\\crash.txt"])
    }

    @Test
    fun `clearState removes path from progress`() = runTest {
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = false)
        repository.downloadFile("C:\\test.txt")
        assertEquals(DownloadState.ERROR, repository.downloadProgress.value["C:\\test.txt"])

        repository.clearState("C:\\test.txt")
        assertNull(repository.downloadProgress.value["C:\\test.txt"])
    }

    @Test
    fun `downloadFile tracks DOWNLOADING state during request`() = runTest {
        fakeClient.onRequest = {
            // Check state DURING the request — the path key is "C:\mid.txt"
            assertEquals(DownloadState.DOWNLOADING, repository.downloadProgress.value["C:\\mid.txt"])
        }
        val payload = json.encodeToJsonElement(
            FileGetResponse(path = "C:\\mid.txt", content = ""),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.downloadFile("C:\\mid.txt")
    }
}

private class FakeFileGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false
    var onRequest: ((String) -> Unit)? = null

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        // Extract path from params for callback
        val path = params?.toString() ?: ""
        onRequest?.invoke(path)
        if (shouldThrow) throw RuntimeException("Test file exception")
        return nextResponse
    }
}
