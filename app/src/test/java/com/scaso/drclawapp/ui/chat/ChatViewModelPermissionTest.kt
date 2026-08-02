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
import com.scaso.drclawapp.ui.components.PermissionScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 16 unit tests: HITL approve/deny dialog for Confirm-tier tools.
 *
 * Pattern: StandardTestDispatcher + MutableSharedFlow<GatewayEvent> + advanceUntilIdle.
 * No Turbine — follows the established ChatViewModelTest convention.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelPermissionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeEvents: MutableSharedFlow<GatewayEvent>
    private lateinit var fakeConnectionState: MutableStateFlow<ConnectionState>
    private lateinit var fakeGateway: FakePermissionGatewayClient
    private lateinit var fakePreferences: FakePermissionAppPreferences
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
        fakePreferences = FakePermissionAppPreferences()
        fakeGateway = FakePermissionGatewayClient(fakeEvents, fakeConnectionState)
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
    fun `approve_permission_sends_cmd_res_with_ok_true`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-approve-1",
                tool = "bash",
                description = "Run shell command",
                toolCallId = "tc-1",
                allowSessionCache = false,
            )
        )
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingPermission)

        viewModel.approveNativePermission(PermissionScope.Once)
        advanceUntilIdle()

        assertEquals("req-approve-1", fakeGateway.lastRequestId)
        assertTrue(fakeGateway.lastOk == true)
        assertNull(viewModel.uiState.value.pendingPermission)
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────────

    @Test
    fun `deny_permission_sends_cmd_res_with_ok_false`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-deny-2",
                tool = "read_file",
                description = "Read a file",
                toolCallId = "tc-2",
                allowSessionCache = false,
            )
        )
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingPermission)

        viewModel.denyNativePermission()
        advanceUntilIdle()

        assertEquals("req-deny-2", fakeGateway.lastRequestId)
        assertTrue(fakeGateway.lastOk == false)
        assertNull(viewModel.uiState.value.pendingPermission)
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────────

    @Test
    fun `approve_with_session_scope_sends_scope_session`() = runTest {
        advanceUntilIdle()

        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-session-3",
                tool = "write_file",
                description = "Write to a file",
                toolCallId = "tc-3",
                allowSessionCache = true,
            )
        )
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingPermission)

        viewModel.approveNativePermission(PermissionScope.Session)
        advanceUntilIdle()

        assertEquals("req-session-3", fakeGateway.lastRequestId)
        assertTrue(fakeGateway.lastOk == true)
        assertEquals("session", fakeGateway.lastScope)
        assertNull(viewModel.uiState.value.pendingPermission)
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────────

    @Test
    fun `auto_approve_allowlist_skips_dialog`() = runTest {
        // Pre-populate the allowlist before ViewModel construction so _autoApproveTools
        // sees the tool name when eagerly stateIn'd during init.
        fakePreferences.toolsFlow.value = setOf("bash")

        // Rebuild ViewModel now that the allowlist is seeded
        uiStateCollector?.cancel()
        val autoApproveGateway = FakePermissionGatewayClient(fakeEvents, fakeConnectionState)
        val autoApproveRepo = ChatRepository(autoApproveGateway, scope)
        val autoApproveSessionRepo = SessionRepository(autoApproveGateway, autoApproveRepo, scope)
        val autoApproveBrainRepo = BrainRepository(autoApproveGateway, scope)
        val autoApproveDownloadRepo = FileDownloadRepository(autoApproveGateway)
        val autoApproveApprovalRepo = ApprovalRepository(autoApproveGateway, scope)
        val autoApproveVm = ChatViewModel(
            repository = autoApproveRepo,
            sessionRepository = autoApproveSessionRepo,
            brainRepository = autoApproveBrainRepo,
            fileDownloadRepository = autoApproveDownloadRepo,
            fileSaver = null,
            approvalRepository = autoApproveApprovalRepo,
            appPreferences = fakePreferences,
        )
        uiStateCollector = scope.launch { autoApproveVm.uiState.collect {} }
        advanceUntilIdle()

        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-auto-4",
                tool = "bash",
                description = "Run shell command",
                toolCallId = "tc-4",
                allowSessionCache = false,
            )
        )
        advanceUntilIdle()

        // Tool is in the allowlist — dialog must NOT appear
        assertNull(autoApproveVm.uiState.value.pendingPermission)
        // And the gateway must have received an auto-approve response
        assertEquals("req-auto-4", autoApproveGateway.lastRequestId)
        assertTrue(autoApproveGateway.lastOk == true)
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────────

    @Test
    fun `dialog_shows_checkbox_only_when_allow_session_cache_is_true`() = runTest {
        advanceUntilIdle()

        // Emit a request with allowSessionCache = false — checkbox should NOT appear
        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-no-checkbox-5a",
                tool = "bash",
                description = "Run shell command",
                toolCallId = "tc-5a",
                allowSessionCache = false,
            )
        )
        advanceUntilIdle()

        val noCacheReq = viewModel.uiState.value.pendingPermission
        assertNotNull(noCacheReq)
        assertTrue(noCacheReq?.allowSessionCache == false)

        // Dismiss / clear by denying so we can test the next request
        viewModel.denyNativePermission()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.pendingPermission)

        // Emit a request with allowSessionCache = true — checkbox SHOULD appear
        fakeEvents.emit(
            GatewayEvent.NativePermissionRequest(
                requestId = "req-checkbox-5b",
                tool = "write_file",
                description = "Write to disk",
                toolCallId = "tc-5b",
                allowSessionCache = true,
            )
        )
        advanceUntilIdle()

        val cacheReq = viewModel.uiState.value.pendingPermission
        assertNotNull(cacheReq)
        assertTrue(cacheReq?.allowSessionCache == true)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────────────

/**
 * AppPreferences stub for Phase 16 tests.
 * Uses MutableStateFlow so the auto-approve allowlist can be seeded before
 * ViewModel construction (needed for test 4, since _autoApproveTools is
 * eagerly stateIn'd in the ViewModel constructor).
 */
private class FakePermissionAppPreferences : AppPreferences(null) {
    val toolsFlow = MutableStateFlow<Set<String>>(emptySet())
    override val autoApproveTools: Flow<Set<String>> = toolsFlow
}

/**
 * GatewayClient fake for Phase 16 permission tests.
 * Records the last call to [sendNativePermissionResponse] for assertion.
 */
private class FakePermissionGatewayClient(
    private val fakeEvents: MutableSharedFlow<GatewayEvent>,
    private val fakeConnectionState: MutableStateFlow<ConnectionState>,
) : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    override val connectionState: StateFlow<ConnectionState> = fakeConnectionState
    override val events: SharedFlow<GatewayEvent> = fakeEvents

    var lastRequestId: String? = null
    var lastOk: Boolean? = null
    var lastScope: String? = null

    override fun sendNativePermissionResponse(requestId: String, ok: Boolean, scope: String) {
        lastRequestId = requestId
        lastOk = ok
        lastScope = scope
    }

    override suspend fun sendMessage(text: String, forceFresh: Boolean): ResponseFrame =
        ResponseFrame(id = "fake", ok = true)

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
