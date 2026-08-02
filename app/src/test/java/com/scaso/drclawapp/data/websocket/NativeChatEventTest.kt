package com.scaso.drclawapp.data.websocket

import app.cash.turbine.test
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.model.ToolStatus
import com.scaso.drclawapp.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 13 unit tests: native-chat tool events (NativeToolStart, NativeToolResult,
 * NativePermissionRequest, StreamPhaseChange, StreamDone).
 */
class NativeChatEventTest {

    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var fakeConnectionState: MutableStateFlow<ConnectionState>
    private lateinit var repository: ChatRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        fakeEvents = MutableSharedFlow(extraBufferCapacity = 64)
        fakeConnectionState = MutableStateFlow(ConnectionState.Disconnected)
        val fakeClient = FakeNativeGatewayClient(fakeEvents, fakeConnectionState)
        repository = ChatRepository(fakeClient, scope)
    }

    /**
     * Suspends until [ChatRepository]'s collector has actually subscribed to
     * [fakeEvents].
     *
     * The repository collects on [Dispatchers.Default], and a SharedFlow with no
     * replay discards anything emitted while it has no subscriber. Awaiting an item
     * from `repository.messages` does not establish that subscription, because that
     * is a separate flow whose initial value arrives the moment Turbine subscribes
     * to it. Without this wait the first emission is dropped whenever the collector
     * loses the race, which is load dependent and so fails only sometimes.
     */
    private suspend fun awaitCollector() = fakeEvents.subscriptionCount.first { it > 0 }

    // -------------------------------------------------------------------------
    // Test 1: NativeToolStart attaches a ToolEvent to the streaming message
    // -------------------------------------------------------------------------

    @Test
    fun `NativeToolStart attaches ToolEvent with Running status to streaming message`() = runTest {
        repository.messages.test {
            awaitItem() // initial empty list
            awaitCollector()

            // Start a streaming assistant message first
            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Thinking...", seq = 1))
            val afterDelta = awaitItem()
            assertEquals(1, afterDelta.size)
            assertTrue(afterDelta[0].isStreaming)

            // Emit NativeToolStart
            fakeEvents.emit(
                GatewayEvent.NativeToolStart(
                    runId = "run-1",
                    toolCallId = "tc-abc",
                    toolName = "bash",
                    input = buildJsonObject { put("command", "ls -la") },
                )
            )

            val afterTool = awaitItem()
            assertEquals(1, afterTool.size)
            val toolEvents = afterTool[0].toolEvents
            assertEquals(1, toolEvents.size)
            assertEquals("tc-abc", toolEvents[0].toolCallId)
            assertEquals("bash", toolEvents[0].toolName)
            assertEquals(ToolStatus.Running, toolEvents[0].status)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: NativeToolResult updates the ToolEvent status to Done
    // -------------------------------------------------------------------------

    @Test
    fun `NativeToolResult updates matching ToolEvent to Done with output`() = runTest {
        repository.messages.test {
            awaitItem() // initial empty list
            awaitCollector()

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-2", text = "Running tool", seq = 1))
            awaitItem()

            fakeEvents.emit(
                GatewayEvent.NativeToolStart(
                    runId = "run-2",
                    toolCallId = "tc-xyz",
                    toolName = "read_file",
                    input = buildJsonObject { put("path", "/etc/hosts") },
                )
            )
            awaitItem()

            val output = buildJsonObject { put("content", "127.0.0.1 localhost") }
            fakeEvents.emit(
                GatewayEvent.NativeToolResult(
                    runId = "run-2",
                    toolCallId = "tc-xyz",
                    toolName = "read_file",
                    output = output,
                    error = null,
                    durationMs = 42L,
                )
            )

            val afterResult = awaitItem()
            assertEquals(1, afterResult.size)
            val te = afterResult[0].toolEvents.first { it.toolCallId == "tc-xyz" }
            assertEquals(ToolStatus.Done, te.status)
            assertEquals(output, te.output)
            assertEquals(42L, te.durationMs)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Test 3: NativeToolResult with error sets ToolStatus.Error
    // -------------------------------------------------------------------------

    @Test
    fun `NativeToolResult with error field sets ToolEvent status to Error`() = runTest {
        repository.messages.test {
            awaitItem()
            awaitCollector()

            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-3", text = "...", seq = 1))
            awaitItem()

            fakeEvents.emit(
                GatewayEvent.NativeToolStart(
                    runId = "run-3",
                    toolCallId = "tc-err",
                    toolName = "write_file",
                    input = buildJsonObject { put("path", "/root/secret") },
                )
            )
            awaitItem()

            fakeEvents.emit(
                GatewayEvent.NativeToolResult(
                    runId = "run-3",
                    toolCallId = "tc-err",
                    toolName = "write_file",
                    output = JsonObject(emptyMap()),
                    error = "Permission denied",
                    durationMs = null,
                )
            )

            val afterResult = awaitItem()
            val te = afterResult[0].toolEvents.first { it.toolCallId == "tc-err" }
            assertEquals(ToolStatus.Error, te.status)
            assertEquals("Permission denied", te.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Test 4: NativePermissionRequest is forwarded to nativePermissionFlow
    // -------------------------------------------------------------------------

    @Test
    fun `NativePermissionRequest is emitted on nativePermissionFlow`() = runTest {
        repository.nativePermissionFlow.test {
            awaitCollector()

            fakeEvents.emit(
                GatewayEvent.NativePermissionRequest(
                    requestId = "req-1",
                    tool = "bash",
                    description = "Run shell command",
                    toolCallId = "tc-perm",
                    allowSessionCache = true,
                )
            )

            val req = awaitItem()
            assertEquals("req-1", req.requestId)
            assertEquals("bash", req.tool)
            assertEquals("tc-perm", req.toolCallId)
            assertTrue(req.allowSessionCache)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // -------------------------------------------------------------------------
    // Test 5: GatewayEvent sealed class has all 5 new Phase 13 variants
    // -------------------------------------------------------------------------

    @Test
    fun `GatewayEvent sealed class contains all Phase 13 variants`() {
        val events: List<GatewayEvent> = listOf(
            GatewayEvent.NativeToolStart(
                runId = "r",
                toolCallId = "tc-1",
                toolName = "tool",
                input = JsonObject(emptyMap()),
            ),
            GatewayEvent.NativeToolResult(
                runId = "r",
                toolCallId = "tc-1",
                toolName = "tool",
                output = JsonObject(emptyMap()),
            ),
            GatewayEvent.NativePermissionRequest(
                requestId = "req",
                tool = "bash",
                description = "desc",
            ),
            GatewayEvent.StreamPhaseChange(
                runId = "r",
                phase = StreamPhase.Streaming,
            ),
            GatewayEvent.StreamDone(
                runId = "r",
                usage = Usage(inputTokens = 10L, outputTokens = 20L),
            ),
        )
        assertEquals(5, events.size)

        // All 5 are proper GatewayEvent subtypes
        assertTrue(events[0] is GatewayEvent.NativeToolStart)
        assertTrue(events[1] is GatewayEvent.NativeToolResult)
        assertTrue(events[2] is GatewayEvent.NativePermissionRequest)
        assertTrue(events[3] is GatewayEvent.StreamPhaseChange)
        assertTrue(events[4] is GatewayEvent.StreamDone)
    }
}

// ---------------------------------------------------------------------------
// Fake GatewayClient for Phase 13 tests
// ---------------------------------------------------------------------------

private class FakeNativeGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val events: kotlinx.coroutines.flow.SharedFlow<GatewayEvent> = fakeEvents
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
}
