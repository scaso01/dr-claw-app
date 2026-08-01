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
import com.scaso.drclawapp.data.websocket.HelloOk
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the force-fresh toggle (Phase 12).
 *
 * Covers:
 *   1. toggle_force_fresh_flips_state
 *   2. send_message_with_force_fresh_includes_flag_in_frame
 *   3. send_message_without_force_fresh_omits_flag
 *   4. force_fresh_resets_after_send
 *   5. force_fresh_default_is_false
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelForceFreshTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var fakeConnectionState: MutableStateFlow<ConnectionState>
    private lateinit var fakeGateway: RecordingFakeGatewayClient
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
        fakeEvents = MutableSharedFlow(extraBufferCapacity = 64)
        fakeConnectionState = MutableStateFlow(ConnectionState.Disconnected)
        fakeGateway = RecordingFakeGatewayClient(fakeEvents, fakeConnectionState)
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
            appPreferences = FakeFreshAppPreferences(),
        )
        uiStateCollector = scope.launch { viewModel.uiState.collect {} }
    }

    @After
    fun tearDown() {
        uiStateCollector?.cancel()
        scope.cancel()
        Dispatchers.resetMain()
    }

    // ── 1. toggle_force_fresh_flips_state ───────────────────────────

    @Test
    fun `toggleForceFresh flips forceFresh state`() = runTest {
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.forceFresh)

        viewModel.toggleForceFresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.forceFresh)

        viewModel.toggleForceFresh()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.forceFresh)
    }

    // ── 2. send_message_with_force_fresh_includes_flag_in_frame ─────

    @Test
    fun `sendMessage with forceFresh=true passes flag to repository`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(HelloOk())
        advanceUntilIdle()

        viewModel.toggleForceFresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.forceFresh)

        viewModel.onInputChanged("Skip the brain please")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertTrue("Expected forceFresh=true to be forwarded to gateway", fakeGateway.lastForceFresh)
    }

    // ── 3. send_message_without_force_fresh_omits_flag ──────────────

    @Test
    fun `sendMessage without toggling forceFresh passes false to gateway`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(HelloOk())
        advanceUntilIdle()

        viewModel.onInputChanged("Normal message")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertFalse("Expected forceFresh=false when toggle not activated", fakeGateway.lastForceFresh)
    }

    // ── 4. force_fresh_resets_after_send ────────────────────────────

    @Test
    fun `forceFresh resets to false after send`() = runTest {
        fakeConnectionState.value = ConnectionState.Connected(HelloOk())
        advanceUntilIdle()

        viewModel.toggleForceFresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.forceFresh)

        viewModel.onInputChanged("Send and reset")
        advanceUntilIdle()
        viewModel.sendMessage()
        advanceUntilIdle()

        assertFalse("forceFresh must reset to false after send", viewModel.uiState.value.forceFresh)
    }

    // ── 5. force_fresh_default_is_false ─────────────────────────────

    @Test
    fun `forceFresh is false by default in initial uiState`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.forceFresh)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────

/**
 * AppPreferences stub that avoids Android DataStore / Context in JVM tests.
 */
private class FakeFreshAppPreferences : AppPreferences(null) {
    override val autoApproveTools: Flow<Set<String>> = flowOf(emptySet())
}

/**
 * GatewayClient fake that records the forceFresh parameter passed to sendMessage.
 */
private class RecordingFakeGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    var lastForceFresh: Boolean = false
        private set

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame {
        lastForceFresh = forceFresh
        return ResponseFrame(id = "fake", ok = true)
    }

    override suspend fun sendGenericRequest(
        method: String,
        params: JsonElement?,
    ): ResponseFrame = ResponseFrame(id = "fake", ok = true)

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
        ResponseFrame(id = "fake", ok = true)
}
