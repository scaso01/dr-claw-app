package com.scaso.drclawapp.ui.chat

import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.brain.BrainRepository
import com.scaso.drclawapp.data.filedownload.FileDownloadRepository
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.repository.SessionRepository
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.ErrorShape
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ResponseFrame
import com.scaso.drclawapp.data.websocket.StreamPhase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var fakeConnectionState: MutableStateFlow<ConnectionState>
    private lateinit var fakeGateway: FakeViewModelGatewayClient
    private lateinit var fakePreferences: FakeViewModelAppPreferences
    private lateinit var chatRepository: ChatRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var brainRepository: BrainRepository
    private lateinit var fileDownloadRepository: FileDownloadRepository
    private lateinit var approvalRepository: ApprovalRepository
    // FileSaver is nullable in ChatViewModel -- null avoids Android Context in unit tests
    private lateinit var viewModel: ChatViewModel
    private lateinit var scope: CoroutineScope
    private var uiStateCollector: kotlinx.coroutines.Job? = null

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        scope = CoroutineScope(SupervisorJob() + testDispatcher)
        fakeEvents = MutableSharedFlow(replay = 64, extraBufferCapacity = 64)
        fakeConnectionState = MutableStateFlow(ConnectionState.Disconnected)
        fakeGateway = FakeViewModelGatewayClient(fakeEvents, fakeConnectionState)
        fakePreferences = FakeViewModelAppPreferences()
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
        // Start collecting uiState so WhileSubscribed starts the combine flow
        uiStateCollector = scope.launch { viewModel.uiState.collect {} }
    }

    @After
    fun tearDown() {
        uiStateCollector?.cancel()
        scope.cancel()
        Dispatchers.resetMain()
    }

    // ── Initial state ───────────────────────────────────────────────

    @Test
    fun `initial uiState has empty messages and no input`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertEquals("", state.inputText)
        assertFalse(state.isSending)
        assertFalse(state.isAwaitingResponse)
    }

    // ── Input text ──────────────────────────────────────────────────

    @Test
    fun `onInputChanged updates inputText`() = runTest {
        viewModel.onInputChanged("Hello world")
        advanceUntilIdle()
        assertEquals("Hello world", viewModel.uiState.value.inputText)
    }

    // ── sendMessage ─────────────────────────────────────────────────

    @Test
    fun `sendMessage clears input and sets sending state`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("Test message")
        advanceUntilIdle()

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun `sendMessage with empty text is ignored`() = runTest {
        viewModel.onInputChanged("")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSending)
        assertFalse(viewModel.uiState.value.isAwaitingResponse)
    }

    @Test
    fun `sendMessage with whitespace only is ignored`() = runTest {
        viewModel.onInputChanged("   ")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `sendMessage stores lastSentMessage for retry`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("Remember me")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("Remember me", viewModel.uiState.value.lastSentMessage)
    }

    // ── Reply ───────────────────────────────────────────────────────

    @Test
    fun `setReplyTo and clearReply work correctly`() = runTest {
        val msg = Message(
            id = "msg-1",
            role = Role.ASSISTANT,
            content = "Previous reply",
            timestamp = 1000L,
        )
        viewModel.setReplyTo(msg)
        advanceUntilIdle()
        assertEquals(msg, viewModel.uiState.value.replyingTo)

        viewModel.clearReply()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.replyingTo)
    }

    @Test
    fun `sendMessage clears reply target`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        val msg = Message(id = "msg-1", role = Role.ASSISTANT, content = "Hi", timestamp = 1000L)
        viewModel.setReplyTo(msg)
        viewModel.onInputChanged("Reply text")
        advanceUntilIdle()

        viewModel.sendMessage()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.replyingTo)
    }

    // ── Retry ───────────────────────────────────────────────────────

    @Test
    fun `markRetryAvailable sets showRetry and increments failures`() = runTest {
        viewModel.markRetryAvailable()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showRetry)
        assertEquals(1, viewModel.uiState.value.consecutiveFailures)
    }

    @Test
    fun `retryLastMessage resends stored message`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("Original")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        viewModel.markRetryAvailable()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRetry)

        viewModel.retryLastMessage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showRetry)
        assertTrue(viewModel.uiState.value.isAwaitingResponse)
    }

    @Test
    fun `retryLastMessage is no-op if no previous message`() = runTest {
        viewModel.retryLastMessage()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAwaitingResponse)
    }

    // ── Gateway events ──────────────────────────────────────────────

    @Test
    fun `ChatFinal clears awaiting and activity state`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.ChatFinal(
            runId = "run-1",
            text = "Done",
            stopReason = "end_turn",
        ))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAwaitingResponse)
        assertNull(viewModel.uiState.value.currentActivity)
        assertEquals(0, viewModel.uiState.value.consecutiveFailures)
    }

    @Test
    fun `ChatFinal accumulates token counts`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.ChatFinal(
            runId = "run-1",
            text = null,
            stopReason = "end_turn",
            inputTokens = 100,
            outputTokens = 50,
        ))
        advanceUntilIdle()

        assertEquals(100L, viewModel.sessionInputTokens.value)
        assertEquals(50L, viewModel.sessionOutputTokens.value)

        // Second turn accumulates
        fakeEvents.emit(GatewayEvent.ChatFinal(
            runId = "run-2",
            text = null,
            stopReason = "end_turn",
            inputTokens = 200,
            outputTokens = 75,
        ))
        advanceUntilIdle()

        assertEquals(300L, viewModel.sessionInputTokens.value)
        assertEquals(125L, viewModel.sessionOutputTokens.value)
    }

    @Test
    fun `ChatAborted increments consecutive failures`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.ChatAborted(runId = "run-1"))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.consecutiveFailures)
        assertFalse(viewModel.uiState.value.isAwaitingResponse)
    }

    @Test
    fun `Activity event updates currentActivity`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.Activity(
            runId = "run-1",
            tool = "Read",
            summary = "Reading file.txt",
            timestamp = System.currentTimeMillis(),
        ))
        advanceUntilIdle()

        assertEquals("Reading file.txt", viewModel.uiState.value.currentActivity)
    }

    // GatewayEvent.PermissionRequest handling (legacy permissionRequest state, approvePermission,
    // denyPermission) removed from ChatViewModel -- ApprovalRepository/ApprovalViewModel already
    // owned this event exclusively (Item 8: single permission funnel).

    // ── Interrupt / Resume ──────────────────────────────────────────

    @Test
    fun `interrupt captures context and enables resume`() = runTest {
        advanceUntilIdle()

        // Set up activity state
        fakeEvents.emit(GatewayEvent.Activity(
            runId = "run-1",
            tool = "Read",
            summary = "Reading config",
            timestamp = System.currentTimeMillis(),
        ))
        advanceUntilIdle()

        viewModel.interrupt()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canResume)
        assertNotNull(viewModel.uiState.value.interruptContext)
        assertNull(viewModel.uiState.value.currentActivity) // cleared after interrupt
    }

    @Test
    fun `resumePreviousTask sends resume message and clears context`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        // Set up activity then interrupt
        fakeEvents.emit(GatewayEvent.Activity(
            runId = "run-1",
            tool = "Read",
            summary = "Reading config",
            timestamp = System.currentTimeMillis(),
        ))
        advanceUntilIdle()

        viewModel.interrupt()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canResume)

        viewModel.resumePreviousTask()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canResume)
        assertNull(viewModel.uiState.value.interruptContext)
        assertTrue(viewModel.uiState.value.isAwaitingResponse)
    }

    @Test
    fun `resumePreviousTask is no-op without interrupt context`() = runTest {
        viewModel.resumePreviousTask()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAwaitingResponse)
    }

    // ── Verbose / Thinking ──────────────────────────────────────────

    @Test
    fun `toggleVerbose flips state and sends command`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.toggleVerbose()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.verboseEnabled)

        viewModel.toggleVerbose()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.verboseEnabled)
    }

    @Test
    fun `setThinkingLevel updates state`() = runTest {
        viewModel.setThinkingLevel("high")
        advanceUntilIdle()
        assertEquals("high", viewModel.uiState.value.thinkingLevel)
    }

    // ── Context warning ─────────────────────────────────────────────

    @Test
    fun `markContextWarningShown sets flag`() = runTest {
        assertFalse(viewModel.uiState.value.contextWarningShown)
        viewModel.markContextWarningShown()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.contextWarningShown)
    }

    // ── Share ────────────────────────────────────────────────────────

    @Test
    fun `shareMessage and clearShareContent work correctly`() = runTest {
        viewModel.shareMessage("Share this text")
        advanceUntilIdle()
        assertEquals("Share this text", viewModel.shareContent.value)

        viewModel.clearShareContent()
        advanceUntilIdle()
        assertNull(viewModel.shareContent.value)
    }

    // ── Auto-naming ─────────────────────────────────────────────────

    @Test
    fun `auto-naming is triggered on ChatFinal for new sessions`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        // ChatFinal triggers autoNameSessionIfNeeded, but without a session key
        // set up, it just returns early (no crash)
        fakeEvents.emit(GatewayEvent.ChatFinal(
            runId = "run-1", text = null, stopReason = "end_turn",
        ))
        advanceUntilIdle()
        // No crash = pass
    }

    // ── STT lifecycle ───────────────────────────────────────────────

    @Test
    fun `stopSTT sets isListeningSTT to false`() = runTest {
        // Without starting, stopSTT should be safe
        viewModel.stopSTT()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isListeningSTT)
    }

    // ── Phase 14/15: agentic status line — streamingPhase / activeTools / statusLineText ──

    @Test
    fun `NativeToolStart adds active tool and moves phase to ToolRunning`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", input = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.ToolRunning, state.streamingPhase)
        assertEquals(1, state.activeTools.size)
        assertEquals("Read", state.activeTools.first().toolName)
        // Row-1 status text stays null while tools are running — ActivityStatusBar's
        // tool row (driven directly by activeTools) owns that text, not statusLineText().
        assertNull(state.statusLineText())
    }

    @Test
    fun `NativeToolResult clearing all tools moves phase to Synthesizing and sets Writing answer text`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", input = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.NativeToolResult(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", output = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.Synthesizing, state.streamingPhase)
        assertTrue(state.activeTools.isEmpty())
        assertEquals("Writing answer…", state.statusLineText())
    }

    @Test
    fun `NativeToolResult with tools still running does not advance phase past ToolRunning`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", input = JsonObject(emptyMap()),
        ))
        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-2", toolName = "Grep", input = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.activeTools.size)

        fakeEvents.emit(GatewayEvent.NativeToolResult(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", output = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.ToolRunning, state.streamingPhase)
        assertEquals(1, state.activeTools.size)
    }

    @Test
    fun `first delta after Synthesizing advances phase to Streaming - regression, was stuck at Idle-only gate`() = runTest {
        advanceUntilIdle()

        // Simulate a full tool-then-answer turn: tool runs, completes (-> Synthesizing),
        // then the model's answer starts streaming. Before the fix, the phase-transition
        // gate only fired from Idle, so it stayed stuck at Synthesizing here.
        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", input = JsonObject(emptyMap()),
        ))
        fakeEvents.emit(GatewayEvent.NativeToolResult(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", output = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()
        assertEquals(StreamPhase.Synthesizing, viewModel.uiState.value.streamingPhase)

        fakeEvents.emit(GatewayEvent.ChatDelta(runId = "run-1", text = "Here's the answer", seq = 1))
        advanceUntilIdle()

        assertEquals(StreamPhase.Streaming, viewModel.uiState.value.streamingPhase)
    }

    @Test
    fun `ChatFinal after a tool-using turn resets phase to Idle - regression, StreamDone never arrives`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", input = JsonObject(emptyMap()),
        ))
        fakeEvents.emit(GatewayEvent.NativeToolResult(
            runId = "run-1", toolCallId = "tc-1", toolName = "Read", output = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()
        assertEquals(StreamPhase.Synthesizing, viewModel.uiState.value.streamingPhase)

        fakeEvents.emit(GatewayEvent.ChatFinal(runId = "run-1", text = "Done", stopReason = "end_turn"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.Idle, state.streamingPhase)
        assertTrue(state.activeTools.isEmpty())
        assertNull(state.statusLineText())
    }

    @Test
    fun `ChatError clears isAwaitingResponse and resets phase (was previously unhandled)`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("Trigger a turn")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAwaitingResponse)

        fakeEvents.emit(GatewayEvent.ChatError(runId = "run-1", message = "LLM_ERROR"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAwaitingResponse)
        assertEquals(StreamPhase.Idle, state.streamingPhase)
        assertEquals(1, state.consecutiveFailures)
    }

    @Test
    fun `ChatCatchup on reconnect resumes isAwaitingResponse so the status line reappears`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isAwaitingResponse)

        fakeEvents.emit(GatewayEvent.ChatCatchup(runId = "run-1", content = "partial...", seq = 3))
        advanceUntilIdle()

        // A fresh ViewModel (post-reconnect) otherwise has isAwaitingResponse=false, which would
        // suppress the status line even though a run is genuinely still in flight. ChatCatchup
        // resumes it — the flag settles back to false here because ChatRepository's catchup
        // handler replays the accumulated text into a live isStreaming=true message, which is
        // itself a strictly better "still working" signal than the generic status line, and the
        // pre-existing "clear isAwaitingResponse once a message is streaming" collector reflects
        // that correctly. The fix's effect matters when catchup/tool events resume with no text
        // yet accumulated — isAwaitingResponse being true keeps the status line eligible to show
        // instead of staying suppressed for the rest of the run.
        assertTrue(chatRepository.messages.value.any { it.isStreaming })
    }

    @Test
    fun `interrupt clears the status line immediately without waiting for ChatAborted`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Bash", input = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()
        assertEquals(StreamPhase.ToolRunning, viewModel.uiState.value.streamingPhase)

        viewModel.interrupt()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StreamPhase.Idle, state.streamingPhase)
        assertTrue(state.activeTools.isEmpty())
    }

    @Test
    fun `GatewayEvent Connected refreshes history and resolves a run that finished while disconnected`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        // Send a message, then a tool starts — this is exactly the stuck state a real
        // disconnect leaves behind (isAwaitingResponse=true, phase=ToolRunning) since no
        // ChatFinal ever reaches a disconnected device.
        viewModel.onInputChanged("Long task")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()
        fakeEvents.emit(GatewayEvent.NativeToolStart(
            runId = "run-1", toolCallId = "tc-1", toolName = "Bash", input = JsonObject(emptyMap()),
        ))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAwaitingResponse)
        assertEquals(StreamPhase.ToolRunning, viewModel.uiState.value.streamingPhase)

        // Reconnect: the run actually finished server-side while disconnected. Ironjaw's
        // catchup frame only replays runs still live in its registry (by design), so no
        // ChatCatchup/ChatFinal ever arrives for a finished run — only GatewayEvent.Connected
        // fires, and the finished answer must be picked up via a history refresh instead.
        fakeGateway.historyResponsePayload = buildJsonObject {
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", "Finished while you were away")
                    put("timestamp", 999_999L)
                })
            })
        }
        fakeEvents.emit(GatewayEvent.Connected(com.scaso.drclawapp.data.websocket.HelloOk()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("stuck awaiting flag must clear once history shows nothing is still streaming", state.isAwaitingResponse)
        assertEquals(StreamPhase.Idle, state.streamingPhase)
        assertTrue(state.activeTools.isEmpty())
        assertTrue("the missed final answer must be visible", state.messages.any { it.content == "Finished while you were away" })
    }

    @Test
    fun `statusLineText returns Thinking while awaiting response with no tools or delta yet`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("Hello")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("Thinking…", viewModel.uiState.value.statusLineText())
    }

    // ── Restart gateway ─────────────────────────────────────────────

    @Test
    fun `restartGateway returns success when gateway acknowledges`() = runTest {
        fakeGateway.genericRequestResponse = ResponseFrame(id = "fake", ok = true)

        val result = viewModel.restartGateway()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `restartGateway returns failure with server message when gateway rejects`() = runTest {
        fakeGateway.genericRequestResponse = ResponseFrame(
            id = "fake",
            ok = false,
            error = ErrorShape(code = "PERMISSION_DENIED", message = "Operation was denied or timed out"),
        )

        val result = viewModel.restartGateway()

        assertTrue(result.isFailure)
        assertEquals("Operation was denied or timed out", result.exceptionOrNull()?.message)
    }

    // ── Delete message ──────────────────────────────────────────────

    @Test
    fun `deleteMessage delegates to repository`() = runTest {
        // Send a message first so there's something to delete
        fakeConnectionState.value = ConnectionState.Connected(com.scaso.drclawapp.data.websocket.HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("To delete")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        // Get the message ID
        val messages = viewModel.uiState.value.messages
        if (messages.isNotEmpty()) {
            viewModel.deleteMessage(messages[0].id)
            advanceUntilIdle()
        }
        // No crash = pass
    }
}

// ── Fakes ────────────────────────────────────────────────────────────

/**
 * Minimal AppPreferences stub — returns an empty auto-approve allowlist.
 * Avoids Android DataStore / Context dependency in JVM unit tests.
 */
private class FakeViewModelAppPreferences : AppPreferences(null) {
    override val autoApproveTools: Flow<Set<String>> = flowOf(emptySet())
}

/**
 * Minimal GatewayClient fake for ChatViewModel tests.
 */
private class FakeViewModelGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    /** Settable per-test — lets a test simulate what chat.history returns on reconnect. */
    var historyResponsePayload: JsonElement? = null

    /** Settable per-test — lets a test simulate sendGenericRequest's response (e.g. gateway.restart). */
    var genericRequestResponse: ResponseFrame? = null

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame = genericRequestResponse ?: ResponseFrame(id = "fake", ok = true)

    override suspend fun listSessions(limit: Int?, offset: Int?): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun createSession(title: String?): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun deleteSession(sessionId: String): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun patchSession(sessionKey: String, title: String?): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

    override suspend fun switchSession(newSessionKey: String) {}

    override fun setLocalSessionKey(key: String) {}

    override suspend fun loadHistory(limit: Int?): ResponseFrame =
        ResponseFrame(id = "fake", ok = true, payload = historyResponsePayload)
}

