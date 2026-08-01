package com.scaso.drclawapp.ui.ccbridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.ccbridge.CcActiveSession
import com.scaso.drclawapp.data.ccbridge.CcBridgeRepository
import com.scaso.drclawapp.data.ccbridge.CcSession
import com.scaso.drclawapp.data.ccbridge.Machine
import com.scaso.drclawapp.data.ccbridge.ClaudeSession
import com.scaso.drclawapp.data.model.SessionTemplate
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.websocket.CcBridgeConnectionState
import com.scaso.drclawapp.util.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class CcSessionsUiState(
    val sessions: List<CcSession> = emptyList(),
    val activeSessions: List<CcActiveSession> = emptyList(),
    val ironjawSessions: List<ClaudeSession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val bridgeConnected: Boolean = false,
    val mailMessages: List<com.scaso.drclawapp.data.ccbridge.MailMessage> = emptyList(),
)

@HiltViewModel
class CcSessionsViewModel @Inject constructor(
    private val repository: CcBridgeRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    /** Emits the daemon session ID after a successful resume, for auto-navigation. */
    private val _resumedSessionId = MutableStateFlow<String?>(null)
    val resumedSessionId: StateFlow<String?> = _resumedSessionId.asStateFlow()

    /** Backend to use when navigating after resume (daemon or ironjaw fallback). */
    private val _resumeBackend = MutableStateFlow("daemon")
    val resumeBackend: StateFlow<String> = _resumeBackend.asStateFlow()

    fun consumeResumedSessionId() {
        _resumedSessionId.value = null
        _resumeBackend.value = "daemon"
    }

    /** Error from resume operation, shown as snackbar. */
    private val _resumeError = MutableStateFlow<String?>(null)
    val resumeError: StateFlow<String?> = _resumeError.asStateFlow()

    fun consumeResumeError() {
        _resumeError.value = null
    }

    val sessionTemplates: StateFlow<List<SessionTemplate>> = appPreferences.sessionTemplates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun saveTemplate(template: SessionTemplate) {
        viewModelScope.launch {
            appPreferences.saveTemplate(template)
        }
    }

    fun deleteTemplate(name: String) {
        viewModelScope.launch {
            appPreferences.deleteTemplate(name)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun showCreateDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    private val _isCreatingSession = MutableStateFlow(false)
    val isCreatingSession: StateFlow<Boolean> = _isCreatingSession.asStateFlow()

    private val _showIronjawCreateDialog = MutableStateFlow(false)
    val showIronjawCreateDialog: StateFlow<Boolean> = _showIronjawCreateDialog.asStateFlow()

    fun showIronjawCreateDialog() {
        _showIronjawCreateDialog.value = true
    }

    fun dismissIronjawCreateDialog() {
        _showIronjawCreateDialog.value = false
    }

    // ── Ironjaw list sort / filter (de-clutter controls) ──────────────
    private val _ironjawSort = MutableStateFlow(IronjawSort.RECENT)
    val ironjawSort: StateFlow<IronjawSort> = _ironjawSort.asStateFlow()

    fun setIronjawSort(sort: IronjawSort) {
        _ironjawSort.value = sort
    }

    private val _ironjawStatusFilter = MutableStateFlow(IronjawStatusFilter.ALL)
    val ironjawStatusFilter: StateFlow<IronjawStatusFilter> = _ironjawStatusFilter.asStateFlow()

    fun setIronjawStatusFilter(filter: IronjawStatusFilter) {
        _ironjawStatusFilter.value = filter
    }

    // 10-flow combine: sessions, activeSessions, ironjawSessions, isLoading, error, bridgeConnectionState, mailMessages, searchQuery, ironjawSort, ironjawStatusFilter
    val uiState: StateFlow<CcSessionsUiState> = combine(
        repository.sessions,
        repository.activeSessions,
        repository.ironjawSessions,
        repository.isLoading,
        repository.error,
        repository.bridgeConnectionState.map { it is CcBridgeConnectionState.Connected },
        repository.mailMessages,
        _searchQuery,
        _ironjawSort,
        _ironjawStatusFilter,
    ) { sessions, activeSessions, ironjawSessions, isLoading, error, bridgeConnected, mail, query, sort, statusFilter ->
        CcSessionsUiState(
            sessions = sessions.filter { it.matchesQuery(query) },
            activeSessions = activeSessions.filter { it.matchesQuery(query) },
            ironjawSessions = applyIronjawSortFilter(
                ironjawSessions.filter { it.matchesQuery(query) },
                sort,
                statusFilter,
            ),
            isLoading = isLoading,
            error = error,
            bridgeConnected = bridgeConnected,
            mailMessages = mail,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CcSessionsUiState(),
    )

    private var ironjawPollJob: Job? = null

    init {
        repository.connectBridge()
        refresh()
        startIronjawPolling()
        reloadActiveOnConnect()
    }

    // ponytail: the init refresh() races bridge auth (both bridges still mid-handshake),
    // so the first active-session fetch comes back empty. Re-fetch each time a bridge
    // reports Connected — fires once per bridge as workstation/phone finish their handshake.
    private fun reloadActiveOnConnect() {
        viewModelScope.launch {
            repository.bridgeConnectionState.collect { state ->
                if (state is CcBridgeConnectionState.Connected) {
                    repository.loadSessions()
                }
            }
        }
    }

    private fun startIronjawPolling() {
        ironjawPollJob?.cancel()
        ironjawPollJob = viewModelScope.launch {
            while (isActive) {
                repository.loadSessions() // backstop: catches the 2nd bridge + post-create races
                repository.loadIronjawSessions()
                repository.loadMailMessages()
                delay(10_000L)
            }
        }
    }

    fun refresh() {
        repository.loadSessions()
        repository.loadIronjawSessions()
        repository.loadMailMessages()
    }

    // ── v1: Gateway-proxied message send ──────────────────────────────

    fun sendMessage(sessionId: String, machine: String, message: String) {
        viewModelScope.launch {
            repository.sendMessageViaGateway(sessionId, machine, message)
        }
    }

    // ── v2: Session daemon operations ─────────────────────────────────

    fun createSession(
        cwd: String,
        project: String?,
        backend: String?,
        model: String?,
        permissionMode: String?,
        target: Machine = Machine.LOCAL,
    ) {
        viewModelScope.launch {
            repository.createSession(
                cwd = cwd,
                project = project,
                backend = backend ?: "cloud",
                model = model,
                permissionMode = permissionMode ?: "bypassPermissions",
                target = target,
            )
            dismissCreateDialog()
            refresh()
        }
    }

    fun detachSession(id: String) {
        viewModelScope.launch {
            repository.detachSession(id)
        }
    }

    fun destroySession(id: String) {
        viewModelScope.launch {
            repository.destroySession(id)
            refresh()
        }
    }

    /**
     * Resume a historical session as a live daemon session, then
     * auto-navigate to the attached view by emitting the daemon ID.
     */
    fun resumeSession(session: CcSession) {
        viewModelScope.launch {
            try {
                val response = repository.resumeSession(
                    sessionId = session.sessionId,
                    cwd = session.cwd ?: "~",
                    project = session.project?.ifEmpty { null },
                )
                if (response.ok) {
                    val payloadObj = response.payload?.jsonObject
                    val daemonId = payloadObj?.get("id")?.jsonPrimitive?.contentOrNull
                    val fallback = payloadObj?.get("fallback")?.jsonPrimitive?.contentOrNull
                    if (daemonId != null) {
                        refresh()
                        _resumeBackend.value = if (fallback == "ironjaw") "ironjaw" else "daemon"
                        _resumedSessionId.value = daemonId
                    } else {
                        refresh()
                    }
                } else {
                    val msg = try {
                        response.payload?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                        ?: response.error?.message
                        ?: "Resume failed"
                    _resumeError.value = msg
                }
            } catch (e: Exception) {
                _resumeError.value = e.message ?: "Resume failed"
            }
        }
    }

    // ── Ironjaw session operations ──────────────────────────────────────

    fun createIronjawSession(
        message: String,
        cwd: String = "C:\\Users\\deploy",
    ) {
        viewModelScope.launch {
            dismissIronjawCreateDialog()
            var pendingSessionId: String? = null
            var runId: String? = null
            try {
                val response = repository.createIronjawSession(
                    message = message,
                    cwd = cwd,
                )
                if (response.ok) {
                    pendingSessionId = try {
                        response.payload?.jsonObject?.get("sessionId")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                    runId = try {
                        response.payload?.jsonObject?.get("runId")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                } else {
                    val msg = try {
                        response.payload?.jsonObject?.get("error")?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) { null }
                        ?: response.error?.message
                        ?: "Create failed"
                    _resumeError.value = msg
                }
            } catch (e: Exception) {
                _resumeError.value = e.message ?: "Create failed"
            }

            if (pendingSessionId != null) {
                // Wait for cc.complete event (subprocess finished), then navigate.
                // The complete event has the RESOLVED session ID (may differ from tracking key).
                _isCreatingSession.value = true
                try {
                    val completeEvent = kotlinx.coroutines.withTimeout(120_000L) {
                        repository.ccCompleteEvents.first { it.runId == runId }
                    }
                    val resolvedId = completeEvent.sessionId
                    refresh()
                    _resumeBackend.value = "ironjaw"
                    _resumedSessionId.value = resolvedId
                } catch (_: Exception) {
                    // Timeout or error — fall back to just refreshing the list
                    refresh()
                } finally {
                    _isCreatingSession.value = false
                }
            } else {
                refresh()
            }
        }
    }

    fun destroyIronjawSession(sessionId: String) {
        viewModelScope.launch {
            try {
                repository.destroyIronjawSession(sessionId)
            } catch (_: Exception) {
                // Best-effort destroy
            }
            refresh()
        }
    }

    fun claimMailMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.claimMailMessage(messageId)
            } catch (_: Exception) {
                // Best-effort claim
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        ironjawPollJob?.cancel()
        repository.disconnectBridge()
    }

    // ── Deep content search ──────────────────────────────────────────

    private val _contentSearchResults = MutableStateFlow<List<ContentSearchResult>>(emptyList())
    val contentSearchResults: StateFlow<List<ContentSearchResult>> = _contentSearchResults.asStateFlow()

    private val _isContentSearching = MutableStateFlow(false)
    val isContentSearching: StateFlow<Boolean> = _isContentSearching.asStateFlow()

    private var contentSearchJob: Job? = null

    /**
     * Deep search through session history content via cc.history RPC.
     * Searches up to 20 most recent sessions for matching message content.
     */
    fun searchContent(query: String) {
        if (query.isBlank()) {
            _contentSearchResults.value = emptyList()
            return
        }
        contentSearchJob?.cancel()
        contentSearchJob = viewModelScope.launch {
            _isContentSearching.value = true
            val results = mutableListOf<ContentSearchResult>()
            val q = query.trim().lowercase()

            // Search Ironjaw sessions (have cc.history)
            val sessions = repository.ironjawSessions.value.take(20)
            for (session in sessions) {
                try {
                    val history = repository.loadSessionHistory(session.ccBridgeId, limit = 100)
                    val matches = history.filter { msg ->
                        msg.content.lowercase().contains(q)
                    }
                    if (matches.isNotEmpty()) {
                        results.add(
                            ContentSearchResult(
                                sessionId = session.ccBridgeId,
                                project = session.project,
                                matchCount = matches.size,
                                firstMatch = matches.first().content.take(200),
                            ),
                        )
                    }
                } catch (_: Exception) {
                    // Skip sessions with inaccessible history
                }
                if (!isActive) break
            }
            _contentSearchResults.value = results
            _isContentSearching.value = false
        }
    }

    fun clearContentSearch() {
        contentSearchJob?.cancel()
        _contentSearchResults.value = emptyList()
        _isContentSearching.value = false
    }

    // ── Search filtering ──────────────────────────────────────────────

    private fun CcSession.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return (topic?.lowercase()?.contains(q) == true) ||
            (project?.lowercase()?.contains(q) == true) ||
            machine.lowercase().contains(q) ||
            (lastMessage?.lowercase()?.contains(q) == true) ||
            shortId.lowercase().contains(q) ||
            sessionId.lowercase().contains(q)
    }

    private fun CcActiveSession.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return (config?.project?.lowercase()?.contains(q) == true) ||
            (config?.cwd?.lowercase()?.contains(q) == true) ||
            (config?.model?.lowercase()?.contains(q) == true) ||
            id.lowercase().contains(q)
    }

    private fun ClaudeSession.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return (title?.lowercase()?.contains(q) == true) ||
            (role?.lowercase()?.contains(q) == true) ||
            (model?.lowercase()?.contains(q) == true) ||
            (project?.lowercase()?.contains(q) == true) ||
            ccBridgeId.lowercase().contains(q) ||
            status.lowercase().contains(q)
    }
}

/** Result from a deep content search through session history. */
data class ContentSearchResult(
    val sessionId: String,
    val project: String? = null,
    val matchCount: Int = 0,
    val firstMatch: String = "",
)

// ── Ironjaw list sort / filter ────────────────────────────────────────

// Sort options track the fields `cc.sessions` actually sends (id, title, project,
// status, lastActivity, contextPct). Cost/tokens are NOT in that payload — no sort for them.
/** Sort order for the Ironjaw session list. */
enum class IronjawSort(val label: String) {
    RECENT("Recent"),
    TITLE("Title"),
    PROJECT("Project"),
}

/** Status filter for the Ironjaw session list. */
enum class IronjawStatusFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    IDLE("Idle"),
}

// "running" = the active-subprocess status from handle_cc_sessions; the rest are daemon/agentic statuses.
private val IRONJAW_ACTIVE_STATUSES =
    setOf("running", "active", "streaming", "waiting", "waiting_permission")

/** Display label for a session's project (mirrors the card's projectName resolution). */
internal fun ClaudeSession.projectLabel(): String =
    project?.takeIf { it.isNotEmpty() && it != "null" }
        ?: cwd.trimEnd('/', '\\').split('/', '\\').lastOrNull()?.ifBlank { null }
        ?: "Unknown"

/** Sort key for TITLE: real title, else project label, lowercased. */
private fun ClaudeSession.titleSortKey(): String =
    (title?.takeIf { it.isNotBlank() } ?: projectLabel()).lowercase()

/**
 * Apply the user-selected status filter + sort to the Ironjaw session list.
 * Pure (no Android deps) so it is unit-testable. RECENT relies on ISO-8601
 * lastActivity strings sorting lexicographically == chronologically.
 */
fun applyIronjawSortFilter(
    sessions: List<ClaudeSession>,
    sort: IronjawSort,
    filter: IronjawStatusFilter,
): List<ClaudeSession> {
    val filtered = when (filter) {
        IronjawStatusFilter.ALL -> sessions
        IronjawStatusFilter.ACTIVE -> sessions.filter { it.status.lowercase() in IRONJAW_ACTIVE_STATUSES }
        IronjawStatusFilter.IDLE -> sessions.filter { it.status.lowercase() !in IRONJAW_ACTIVE_STATUSES }
    }
    return when (sort) {
        IronjawSort.RECENT -> filtered.sortedByDescending { it.lastActivity }
        IronjawSort.TITLE -> filtered.sortedBy { it.titleSortKey() }
        IronjawSort.PROJECT -> filtered.sortedBy { it.projectLabel().lowercase() }
    }
}
