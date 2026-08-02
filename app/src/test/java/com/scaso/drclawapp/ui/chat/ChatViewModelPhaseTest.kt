package com.scaso.drclawapp.ui.chat

import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.brain.BrainRepository
import com.scaso.drclawapp.data.filedownload.FileDownloadRepository
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.repository.SessionRepository
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ResponseFrame
import com.scaso.drclawapp.data.websocket.StreamPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 14 unit tests: stream phase state machine in [ChatViewModel].
 *
 * Pattern: StandardTestDispatcher + fakeEvents SharedFlow + advanceUntilIdle.
 * No Turbine — follows the existing ChatViewModelTest convention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPhaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var fakeConnectionState: MutableStateFlow<ConnectionState>
    private lateinit var fakeGateway: FakePhaseGatewayClient
    private lateinit var fakePreferences: FakeAppPreferencesPhase
    private lateinit var chatRepository: ChatRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var brainRepository: BrainRepository
    private lateinit var fileDownloadRepository: FileDownloadRepository
    private lateinit var approvalRepository: ApprovalRepository
    private lateinit var viewModel: ChatViewModel
    private lateinit var scope: CoroutineScope
    private var uiStateCollector: kotlinx.coroutines.Job? = null

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(SupervisorJob() + testDispatcher)
        fakeEvents = MutableSharedFlow(replay = 64, extraBufferCapacity = 64)
        fakeConnectionState = MutableStateFlow(ConnectionState.Disconnected)
        fakeGateway = FakePhaseGatewayClient(fakeEvents, fakeConnectionState)
        fakePreferences = FakeAppPreferencesPhase()
        chatRepository = ChatRepository(fakeGateway, scope)
        sessionRepository = SessionRepository(fakeGateway, chatRepository, scope)
        brainRepository = BrainRepository(fakeGateway, scope)
        fileDownloadRepository = FileDownloadRepository(fakeGateway)
        approvalRepository = ApprovalRepository(fakeGateway, scope)
        viewModel = ChatViewModel(
            repository = chatRepository,
            sessionRepository = sessionRepository,
            brainRepository = brainRepository,
            fileDownloadRepository = fileDownloadRepository,
            fileSaver = null,
            approvalRepository = approvalRepository,
            appPreferences = fakePreferences,
        )
        // Subscribe so WhileSubscribed(5_000) activates the combine graph
        uiStateCollector = scope.launch { viewModel.uiState.collect {} }
    }

    @After
    fun tearDown() {
        uiStateCollector?.cancel()
        scope.cancel()
        Dispatchers.resetMain()
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────────

    @Test
    fun `state_transitions_idle_to_streaming_on_first_delta`() = runTest {
        advanceUntilIdle()
        assertEquals(StreamPhase.Idle, viewModel.uiState.value.streamingPhase)

        // First streaming delta arrives (simulated via a streaming message in the repository)
        // The ViewModel watches repository.messages for isStreaming=true; emit a ChatDelta-like
        // message by injecting a streaming message through the repository's fake state.
        // We test the phase watcher by directly emitting a message with isStreaming=true.
        // Since ChatRepository holds messages in a MutableStateFlow we reach it via fakeGateway.
        // The simplest approach: emit a ChatDelta which the repository translates to a streaming msg.
        fakeEvents.emit(
            GatewayEvent.ChatDelta(
                runId = "run-1",
                text = "Hello",
                seq = 0,
            )
        )
        advanceUntilIdle()

        assertEquals(StreamPhase.Streaming, viewModel.uiState.value.streamingPhase)
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────────

    @Test
    fun `tool_start_transitions_to_tool_running`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(
            GatewayEvent.NativeToolStart(
                runId = "run-1",
                toolCallId = "tc-1",
                toolName = "bash",
                input = JsonNull,
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.ToolRunning, state.streamingPhase)
        assertEquals(1, state.activeTools.size)
        assertEquals("bash", state.activeTools.first().toolName)
        assertEquals("tc-1", state.activeTools.first().toolCallId)
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────────

    @Test
    fun `tool_result_keeps_tool_running_when_other_tools_active`() = runTest {
        advanceUntilIdle()

        // Start two tools
        fakeEvents.emit(
            GatewayEvent.NativeToolStart(runId = "run-1", toolCallId = "tc-1", toolName = "bash", input = JsonNull)
        )
        fakeEvents.emit(
            GatewayEvent.NativeToolStart(runId = "run-1", toolCallId = "tc-2", toolName = "read_file", input = JsonNull)
        )
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.activeTools.size)

        // Complete the first tool only
        fakeEvents.emit(
            GatewayEvent.NativeToolResult(
                runId = "run-1",
                toolCallId = "tc-1",
                toolName = "bash",
                output = JsonNull,
                error = null,
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.ToolRunning, state.streamingPhase)
        assertEquals(1, state.activeTools.size)
        assertEquals("tc-2", state.activeTools.first().toolCallId)
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────────

    @Test
    fun `permission_request_transitions_to_awaiting_approval`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-1",
                tool = "bash",
                description = "Run shell command",
                toolCallId = "tc-3",
                allowSessionCache = false,
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.AwaitingApproval, state.streamingPhase)
        assertNotNull(state.pendingPermission)
        assertEquals("req-1", state.pendingPermission?.requestId)
        assertEquals("bash", state.pendingPermission?.tool)
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────────

    @Test
    fun `stream_done_transitions_to_complete_then_idle`() = runTest {
        advanceUntilIdle()

        // Put the machine into a non-Idle state first
        fakeEvents.emit(
            GatewayEvent.NativeToolStart(runId = "run-1", toolCallId = "tc-1", toolName = "bash", input = JsonNull)
        )
        advanceUntilIdle()
        assertEquals(StreamPhase.ToolRunning, viewModel.uiState.value.streamingPhase)

        fakeEvents.emit(GatewayEvent.StreamDone(runId = "run-1", usage = null))
        advanceUntilIdle()

        // After the 1ms delay tick, phase should have advanced to Idle
        // advanceUntilIdle() runs all pending coroutines including the delay(1) in StreamDone handler
        assertEquals(StreamPhase.Idle, viewModel.uiState.value.streamingPhase)
        assertTrue(viewModel.uiState.value.activeTools.isEmpty())
        assertNull(viewModel.uiState.value.pendingPermission)
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────────

    @Test
    fun `is_busy_is_true_from_streaming_to_complete`() = runTest {
        advanceUntilIdle()

        // Idle → not busy
        assertFalse(viewModel.uiState.value.isBusy)

        // Enter Streaming
        fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Hi", seq = 0))
        advanceUntilIdle()
        assertTrue("Should be busy during Streaming", viewModel.uiState.value.isBusy)

        // ToolRunning also busy
        fakeEvents.emit(
            GatewayEvent.NativeToolStart(runId = "run-1", toolCallId = "tc-1", toolName = "bash", input = JsonNull)
        )
        advanceUntilIdle()
        assertTrue("Should be busy during ToolRunning", viewModel.uiState.value.isBusy)

        // ChatFinal clears busy. This assertion used to require StreamDone for the reset
        // ("only StreamDone clears") — but GatewayClient never emits StreamDone in production
        // (no wire event maps to it; grep confirms handleEvent() only dispatches "chat",
        // "chat.inject", "tick", "permission.request", "cc.chat", "cc.complete",
        // "model.changed", "tool.start", "tool.result"). Waiting for it left the status line
        // stuck at ToolRunning/Synthesizing forever after any tool-using turn, which is the
        // root cause of the "chat screen shows nothing" bug. ChatFinal is the real signal.
        fakeEvents.emit(GatewayEvent.ChatFinal(runId = "run-1", text = null, stopReason = "end_turn"))
        advanceUntilIdle()
        assertFalse("Should not be busy after ChatFinal", viewModel.uiState.value.isBusy)

        // StreamDone remains a valid (if currently unreachable) input — still resolves to Idle.
        fakeEvents.emit(GatewayEvent.StreamDone(runId = "run-1", usage = null))
        advanceUntilIdle()
        assertFalse("Should not be busy after StreamDone + Idle transition", viewModel.uiState.value.isBusy)
    }

    // ── Test 7 ───────────────────────────────────────────────────────────────────

    @Test
    fun `abort_during_any_phase_clears_state`() = runTest {
        advanceUntilIdle()

        // Set up some active state
        fakeEvents.emit(
            GatewayEvent.NativeToolStart(runId = "run-1", toolCallId = "tc-1", toolName = "bash", input = JsonNull)
        )
        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(requestId = "req-1", tool = "bash", description = "run", toolCallId = "tc-1")
        )
        advanceUntilIdle()

        // Abort
        fakeEvents.emit(GatewayEvent.ChatAborted(runId = "run-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.Idle, state.streamingPhase)
        assertTrue("activeTools should be cleared on abort", state.activeTools.isEmpty())
        assertNull("pendingPermission should be cleared on abort", state.pendingPermission)
        assertFalse("isBusy should be false after abort", state.isBusy)
    }

    // ── Test 8 ───────────────────────────────────────────────────────────────────

    @Test
    fun `stall_warning_uses_longer_timeout_when_tool_running`() = runTest {
        advanceUntilIdle()

        // Enter ToolRunning phase — stall timeout is 60 000 ms
        fakeEvents.emit(
            GatewayEvent.NativeToolStart(runId = "run-1", toolCallId = "tc-1", toolName = "bash", input = JsonNull)
        )
        // Use runCurrent() instead of advanceUntilIdle() here:
        // advanceUntilIdle() drains ALL pending work including the 60s stall timer, which would
        // fire immediately and set showRetry=true before we get to test the threshold.
        // runCurrent() only runs coroutines that are immediately due (delay=0), so the 60s
        // stall timer stays pending and the clock remains at 0.
        runCurrent()
        assertEquals(StreamPhase.ToolRunning, viewModel.uiState.value.streamingPhase)

        // Advance 59 seconds — stall timer should NOT have fired yet
        advanceTimeBy(59_000L)
        assertFalse(
            "showRetry should not be set before ToolRunning stall timeout",
            viewModel.uiState.value.showRetry,
        )

        // Advance past the 60-second threshold
        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(
            "showRetry should be set after ToolRunning stall timeout (60s)",
            viewModel.uiState.value.showRetry,
        )
    }

    // ── Test 9 ───────────────────────────────────────────────────────────────────

    @Test
    fun `active delta flow re-arms stall timer - regression, long streams tripped false stall`() = runTest {
        advanceUntilIdle()

        // Stream deltas every 5s for 40s. Each delta re-arms the 10s Streaming stall
        // timer, so a healthy answer streaming longer than 10s must never trip it.
        for (i in 0..8) {
            fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "chunk$i ", seq = i))
            runCurrent()
            advanceTimeBy(5_000L)
        }
        runCurrent()
        assertEquals(StreamPhase.Streaming, viewModel.uiState.value.streamingPhase)
        assertFalse(
            "40s of active streaming must not trip the stall warning",
            viewModel.uiState.value.showRetry,
        )

        // Genuine stall: no deltas for longer than the 10s Streaming timeout
        advanceTimeBy(11_000L)
        runCurrent()
        assertTrue(
            "showRetry should fire once deltas actually stop",
            viewModel.uiState.value.showRetry,
        )
    }

    // ── Test 10 ──────────────────────────────────────────────────────────────────

    @Test
    fun `stall warning is not a gateway failure - consecutiveFailures stays 0`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "partial", seq = 0))
        runCurrent()
        advanceTimeBy(11_000L) // past the 10s Streaming stall timeout
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue("stall should surface the retry chip", state.showRetry)
        assertEquals(
            "a stall warning must not count toward the 'Gateway may be stuck' escalation",
            0,
            state.consecutiveFailures,
        )
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

/**
 * Minimal AppPreferences stub for Phase 14 tests — returns empty allowlist.
 * Avoids Android DataStore / Context dependency in JVM unit tests.
 */
private class FakeAppPreferencesPhase : AppPreferences(null) {
    override val autoApproveTools: Flow<Set<String>> = flowOf(emptySet())
}

/**
 * Minimal GatewayClient stub for Phase 14 phase state-machine tests.
 * Mirrors [FakeViewModelGatewayClient] in ChatViewModelTest.
 */
private class FakePhaseGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame = ResponseFrame(id = "fake", ok = true)

    override suspend fun listSessions(limit: Int?, offset: Int?): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)
}
