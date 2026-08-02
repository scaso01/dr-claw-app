package com.scaso.drclawapp.data.projects

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
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProjectRepositoryTest {

    private lateinit var fakeClient: FakeProjectGatewayClient
    private lateinit var repository: ProjectRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeClient = FakeProjectGatewayClient()
        repository = ProjectRepository(fakeClient, scope)
    }

    @Test
    fun `initial examplePipelineStatus has project name`() {
        assertEquals("example-pipeline", repository.examplePipelineStatus.value.project)
    }

    @Test
    fun `loadExamplePipelineStatus populates on success`() = runTest {
        val payload = json.encodeToJsonElement(
            ProjectStatusResponse(
                project = "example-pipeline",
                status = ProjectStatus.IDLE,
                lastRun = LastRunSummary(
                    timestamp = 1700000000,
                    jobsScraped = 200,
                    jobsMatched = 15,
                ),
            ),
        )
        fakeClient.nextResponse = ResponseFrame(id = "1", ok = true, payload = payload)

        repository.examplePipelineStatus.test {
            skipItems(1) // initial
            repository.loadExamplePipelineStatus()
            val result = awaitItem()
            assertEquals(ProjectStatus.IDLE, result.status)
            assertEquals(200, result.lastRun!!.jobsScraped)
            assertEquals(15, result.lastRun!!.jobsMatched)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadExamplePipelineStatus sets error on failure`() = runTest {
        fakeClient.nextResponse = ResponseFrame(
            id = "1",
            ok = false,
            error = ErrorShape(code = "ERR", message = "Project not found"),
        )

        repository.error.test {
            assertNull(awaitItem())
            repository.loadExamplePipelineStatus()
            assertEquals("Project not found", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadExamplePipelineStatus sets error on exception`() = runTest {
        fakeClient.shouldThrow = true

        repository.error.test {
            assertNull(awaitItem())
            repository.loadExamplePipelineStatus()
            assertEquals("Test project exception", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `triggerExamplePipeline returns true on success`() = runTest {
        fakeClient.nextSendMessageResponse = ResponseFrame(id = "1", ok = true)
        assertTrue(repository.triggerExamplePipeline())
    }

    @Test
    fun `triggerExamplePipeline returns false on failure`() = runTest {
        fakeClient.nextSendMessageResponse = ResponseFrame(id = "1", ok = false)
        assertFalse(repository.triggerExamplePipeline())
    }

    @Test
    fun `triggerExamplePipeline returns false on exception`() = runTest {
        fakeClient.shouldThrowOnSend = true
        assertFalse(repository.triggerExamplePipeline())
    }
}

private class FakeProjectGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var nextResponse = ResponseFrame(id = "0", ok = true)
    var nextSendMessageResponse = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false
    var shouldThrowOnSend = false

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Disconnected)

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame {
        if (shouldThrow) throw RuntimeException("Test project exception")
        return nextResponse
    }

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame {
        if (shouldThrowOnSend) throw RuntimeException("Test send exception")
        return nextSendMessageResponse
    }
}
