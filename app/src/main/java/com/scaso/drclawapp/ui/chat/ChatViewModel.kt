package com.scaso.drclawapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.scaso.drclawapp.data.filedownload.DownloadState
import com.scaso.drclawapp.data.filedownload.FileDownloadRepository
import com.scaso.drclawapp.data.model.InterruptContext
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.SlashCommand
import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.brain.BrainRepository
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.service.FileSaver
import com.scaso.drclawapp.ui.components.EditMessageHandler
import com.scaso.drclawapp.ui.components.buildEditResult
import com.scaso.drclawapp.ui.media.AttachmentViewModel
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.PromptStatsResult
import com.scaso.drclawapp.data.websocket.StreamPhase
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.ui.components.PermissionScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val sessionRepository: com.scaso.drclawapp.data.repository.SessionRepository,
    private val brainRepository: BrainRepository,
    private val fileDownloadRepository: FileDownloadRepository,
    private val fileSaver: FileSaver?,
    private val approvalRepository: ApprovalRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _inputText = MutableStateFlow("")
    private val _isSending = MutableStateFlow(false)
    private val _isAwaitingResponse = MutableStateFlow(false)
    private val _replyingTo = MutableStateFlow<Message?>(null)
    private val _verboseEnabled = MutableStateFlow(false)
    private val _forceFresh = MutableStateFlow(false)
    private val _thinkingLevel = MutableStateFlow("low")
    private val _thinkingStartTimeMs = MutableStateFlow(0L)
    private val _canResume = MutableStateFlow(false)
    private val _interruptContext = MutableStateFlow<InterruptContext?>(null)
    private val _currentActivity = MutableStateFlow<String?>(null)
    private val _lastSentMessage = MutableStateFlow<String?>(null)
    private val _showRetry = MutableStateFlow(false)
    private val _consecutiveFailures = MutableStateFlow(0)
    private val _contextWarningShown = MutableStateFlow(false)
    // Phase 16: class-level StateFlow for auto-approve allowlist (avoids stateIn() per event)
    private val _autoApproveTools: StateFlow<Set<String>> = appPreferences.autoApproveTools
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ── Phase 14: stream phase state machine (extracted -- see StreamPhaseController) ──
    private val phaseController = StreamPhaseController(
        coroutineScope = viewModelScope,
        approvalRepository = approvalRepository,
        onStall = {
            // A stall warning is a "no progress observed" hint, not a gateway failure —
            // only real failure events (ChatError/ChatAborted/health-check fail) count
            // toward consecutiveFailures and the "Gateway may be stuck" escalation.
            _showRetry.value = true
        },
    )

    private val _sessionInputTokens = MutableStateFlow(0L)
    private val _sessionOutputTokens = MutableStateFlow(0L)
    private val _sessionStartTimeMs = MutableStateFlow(0L)
    val sessionInputTokens: StateFlow<Long> = _sessionInputTokens.asStateFlow()
    val sessionOutputTokens: StateFlow<Long> = _sessionOutputTokens.asStateFlow()
    val sessionStartTimeMs: StateFlow<Long> = _sessionStartTimeMs.asStateFlow()

    // System-prompt cost breakdown for the Session Details sheet (fetched on open)
    private val _promptStats = MutableStateFlow<PromptStatsResult?>(null)
    val promptStats: StateFlow<PromptStatsResult?> = _promptStats.asStateFlow()

    fun loadPromptStats() {
        viewModelScope.launch {
            _promptStats.value = repository.fetchPromptStats()
        }
    }
    private val speechInput = SpeechInputController(viewModelScope, _inputText)
    val editHandler = EditMessageHandler()
    val downloadStates: StateFlow<Map<String, DownloadState>> = fileDownloadRepository.downloadProgress

    // B1-T10: server-driven slash commands (extracted -- see SlashCommandController)
    private val slashCommands = SlashCommandController(repository, viewModelScope)
    val availableCommands: StateFlow<List<SlashCommand>> = slashCommands.availableCommands

    // Combine local send/reply state into one flow to stay within combine's 5-param limit
    private data class LocalState(
        val isSending: Boolean,
        val isAwaitingResponse: Boolean,
        val replyingTo: Message?,
        val isListeningSTT: Boolean,
        val verboseEnabled: Boolean,
        val forceFresh: Boolean,
        val thinkingLevel: String,
        val thinkingStartTimeMs: Long,
        val canResume: Boolean,
        val interruptContext: InterruptContext?,
        val currentActivity: String?,
        val showRetry: Boolean,
        val lastSentMessage: String?,
        val consecutiveFailures: Int,
    )

    private data class ThinkingFields(
        val thinkingStartTimeMs: Long, val canResume: Boolean,
        val interruptContext: InterruptContext?, val currentActivity: String?,
    )

    private data class SttVerboseFields(
        val isListeningSTT: Boolean,
        val verboseEnabled: Boolean,
        val forceFresh: Boolean,
        val thinkingLevel: String,
    )

    private val _localState = combine(
        _isSending,
        _isAwaitingResponse,
        _replyingTo,
        combine(speechInput.isListeningSTT, _verboseEnabled, _forceFresh, _thinkingLevel) { stt, verbose, fresh, thinking ->
            SttVerboseFields(stt, verbose, fresh, thinking)
        },
        combine(
            combine(_thinkingStartTimeMs, _canResume, _interruptContext, _currentActivity) { ts, resume, ctx, act ->
                ThinkingFields(ts, resume, ctx, act)
            },
            combine(_showRetry, _lastSentMessage, _consecutiveFailures) { retry, msg, failures ->
                Triple(retry, msg, failures)
            },
        ) { fields, retryState -> fields to retryState },
    ) { isSending, isAwaitingResponse, replyingTo, extras, combined ->
        val (thinkingFields, retryState) = combined
        LocalState(
            isSending, isAwaitingResponse, replyingTo,
            extras.isListeningSTT, extras.verboseEnabled, extras.forceFresh, extras.thinkingLevel,
            thinkingFields.thinkingStartTimeMs, thinkingFields.canResume,
            thinkingFields.interruptContext, thinkingFields.currentActivity,
            retryState.first, retryState.second, retryState.third,
        )
    }

    private val _sessionMode = MutableStateFlow("Default")

    val uiState = combine(
        combine(
            repository.messages,
            repository.connectionState,
            _inputText,
            _localState,
            _contextWarningShown,
        ) { messages, connectionState, inputText, localState, contextWarningShown ->
            // partial state — phase fields added in outer combine
            Pair(
                Pair(messages, connectionState),
                Triple(inputText, localState, contextWarningShown),
            )
        },
        phaseController.state,
        _sessionMode,
    ) { baseData, phaseState, sessionMode ->
        val (msgConn, rest) = baseData
        val (messages, connectionState) = msgConn
        val (inputText, localState, contextWarningShown) = rest
        ChatUiState(
            messages = messages,
            connectionState = connectionState,
            inputText = inputText,
            isSending = localState.isSending,
            isAwaitingResponse = localState.isAwaitingResponse,
            replyingTo = localState.replyingTo,
            isListeningSTT = localState.isListeningSTT,
            verboseEnabled = localState.verboseEnabled,
            forceFresh = localState.forceFresh,
            thinkingLevel = localState.thinkingLevel,
            thinkingStartTimeMs = localState.thinkingStartTimeMs,
            canResume = localState.canResume,
            interruptContext = localState.interruptContext,
            currentActivity = localState.currentActivity,
            showRetry = localState.showRetry,
            lastSentMessage = localState.lastSentMessage,
            consecutiveFailures = localState.consecutiveFailures,
            contextWarningShown = contextWarningShown,
            streamingPhase = phaseState.streamingPhase,
            pendingPermission = phaseState.pendingPermission,
            activeTools = phaseState.activeTools,
            lastToolError = phaseState.lastToolError,
            sessionMode = sessionMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    init {
        repository.connect()

        // Auto-load history when connected + health verification + brain status
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    repository.loadHistory()
                    brainRepository.loadStatus()
                    slashCommands.loadServerCommands()
                    // Post-reconnect health check: verify gateway can process RPC
                    if (repository.verifyGatewayHealth()) {
                        _consecutiveFailures.value = 0
                    } else {
                        _consecutiveFailures.value++
                    }
                }
            }
        }

        // Clear awaiting flag when assistant starts streaming (first delta arrived)
        viewModelScope.launch {
            repository.messages.collect { messages ->
                if (_isAwaitingResponse.value && messages.any { it.isStreaming }) {
                    _isAwaitingResponse.value = false
                }
            }
        }

        // Track thinkingStartTimeMs: set when awaiting, clear when not
        viewModelScope.launch {
            _isAwaitingResponse.collect { awaiting ->
                _thinkingStartTimeMs.value = if (awaiting) System.currentTimeMillis() else 0L
            }
        }

        // Sync model name to ChatRepository for per-message badges
        viewModelScope.launch {
            combine(sessionRepository.sessions, sessionRepository.currentSessionKey) { sessions, key ->
                sessions.firstOrNull { it.sessionKey == key }?.modelName
            }.collect { modelName ->
                repository.currentModelName = modelName
            }
        }

        // Sync session mode for the plan-mode chip
        viewModelScope.launch {
            combine(sessionRepository.sessions, sessionRepository.currentSessionKey) { sessions, key ->
                sessions.firstOrNull { it.sessionKey == key }?.mode ?: "Default"
            }.collect { mode ->
                _sessionMode.value = mode
            }
        }

        // Collect gateway events for Activity display
        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    is GatewayEvent.Activity -> _currentActivity.value = event.summary
                    is GatewayEvent.Connected -> launch { refreshHistoryAndResolveStaleRun() }
                    is GatewayEvent.ChatFinal -> {
                        _currentActivity.value = null
                        _canResume.value = false
                        // ── Phase 15 fix: reset the phase machine here, not on StreamDone ────
                        // GatewayClient never emits StreamDone (no wire event maps to it), so
                        // waiting for it left streamingPhase stuck at ToolRunning/Synthesizing
                        // forever after any tool-using turn — breaking the status line on every
                        // subsequent turn. ChatFinal is the actual "turn complete" signal.
                        phaseController.resetToIdle()
                        _isAwaitingResponse.value = false
                        _consecutiveFailures.value = 0
                        _showRetry.value = false
                        // Accumulate token usage from this turn
                        event.inputTokens?.let { _sessionInputTokens.value += it }
                        event.outputTokens?.let { _sessionOutputTokens.value += it }
                        // Capture context fields before launching coroutines
                        val sessionKey = sessionRepository.currentSessionKey.value
                        val hasContext = event.contextPct != null || event.contextWindow != null || event.contextTokens != null
                        // Auto-name new sessions from first user message
                        launch { autoNameSessionIfNeeded() }
                        // Reload full history to pick up messages sent from other clients (PC, webchat)
                        launch { repository.loadHistory() }
                        // Refresh sessions then apply context from final event
                        // (sequential to prevent loadSessions from clobbering context data)
                        launch {
                            sessionRepository.loadSessions()
                            if (sessionKey != null && hasContext) {
                                sessionRepository.updateSessionContext(
                                    sessionId = sessionKey,
                                    contextPct = event.contextPct,
                                    contextWindow = event.contextWindow,
                                    contextTokens = event.contextTokens,
                                )
                            }
                        }
                    }
                    is GatewayEvent.ChatAborted -> {
                        _currentActivity.value = null
                        _canResume.value = false
                        _isAwaitingResponse.value = false
                        _consecutiveFailures.value++
                        // ── Phase 14: abort clears entire phase machine ──────────────────────
                        phaseController.resetToIdle()
                        phaseController.setLastToolError("Turn aborted")
                    }
                    is GatewayEvent.ChatError -> {
                        // Was previously unhandled (fell through to `else -> {}`), leaving
                        // isAwaitingResponse/streamingPhase stuck forever on a genuine LLM
                        // error — the status line never cleared. Mirror ChatAborted's reset.
                        _currentActivity.value = null
                        _canResume.value = false
                        _isAwaitingResponse.value = false
                        _consecutiveFailures.value++
                        phaseController.resetToIdle()
                        phaseController.setLastToolError(event.message)
                    }
                    is GatewayEvent.ChatCatchup -> {
                        // Reconnect mid-run: resume the "awaiting" status so the status line
                        // reappears until the next final/error/aborted event, since a fresh
                        // ViewModel instance otherwise defaults isAwaitingResponse to false.
                        _isAwaitingResponse.value = true
                    }
                    // GatewayEvent.PermissionRequest is handled exclusively by the global
                    // ApprovalRepository/ApprovalDialog (see NavGraph.kt) -- do not duplicate
                    // that dialog here (Item 8: single permission funnel).
                    // ── Phase 14: native-chat agentic event handlers (see StreamPhaseController) ──
                    is GatewayEvent.NativeToolStart -> phaseController.onToolStart(event)
                    is GatewayEvent.NativeToolResult -> phaseController.onToolResult(event)
                    is GatewayEvent.NativePermissionRequest ->
                        phaseController.onPermissionRequest(event, _autoApproveTools.value)
                    is GatewayEvent.StreamPhaseChange -> {
                        // PHASE 18: wire server StreamPhaseChange events when gateway emits them.
                        // For now phase is derived locally from primitive events above.
                        @Suppress("UNUSED_EXPRESSION")
                        event
                    }
                    is GatewayEvent.StreamDone -> phaseController.onStreamDone()
                    else -> {}
                }
            }
        }

        // ── Phase 14/15: drive phase from first streaming delta ──────────────────
        // Fixed to fire from ToolRunning/Synthesizing too (not just Idle) — the common
        // agentic case is tools run first, then the final answer starts streaming, which
        // left the phase stuck at Synthesizing (showing "Writing answer…") even while the
        // real text was already visibly streaming to the user.
        viewModelScope.launch {
            repository.messages.collect { messages ->
                if (messages.any { it.isStreaming }) {
                    phaseController.driveStreamingFromFirstDelta()
                }
            }
        }

        // On session switch: reset per-session UI state and SEED the usage counters
        // from the session record's persisted lifetime usage (the gateway accumulates
        // per turn as of 2026-07-24). Live ChatFinal events keep adding on top.
        // Seeds once per key change, as soon as the sessions list has the record —
        // previously these reset to 0, so the Session Details sheet showed no usage
        // for any turn the app wasn't awake for.
        viewModelScope.launch {
            var seededKey: String? = null
            combine(sessionRepository.currentSessionKey, sessionRepository.sessions) { key, sessions ->
                key to sessions.firstOrNull { it.sessionKey == key }
            }.collect { (key, record) ->
                if (key != seededKey) {
                    _contextWarningShown.value = false
                    _sessionStartTimeMs.value = 0L
                    _sessionInputTokens.value = record?.inputTokens ?: 0L
                    _sessionOutputTokens.value = record?.outputTokens ?: 0L
                    // Record still absent (list not loaded yet): keep trying on the
                    // next sessions emission rather than locking in zeros.
                    if (record != null || key == null) seededKey = key
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        // Handle edit mode: delete original message before resending
        val editResult = editHandler.buildEditResult(text)
        if (editResult != null) {
            repository.deleteMessage(editResult.originalMessageId)
        }

        _lastSentMessage.value = text
        _showRetry.value = false
        if (_sessionStartTimeMs.value == 0L) {
            _sessionStartTimeMs.value = System.currentTimeMillis()
        }
        val replyId = _replyingTo.value?.id
        // compareAndSet atomically reads and clears the flag. Two rapid sendMessage calls on the
        // same dispatcher could both observe true before either resets it; CAS ensures only the
        // first caller consumes the flag (returns true) while the second gets false.
        val freshFlag = _forceFresh.compareAndSet(expect = true, update = false)
        _inputText.value = ""
        _replyingTo.value = null
        // _forceFresh already cleared atomically above — no second write needed.
        _isSending.value = true
        _isAwaitingResponse.value = true

        viewModelScope.launch {
            try {
                repository.sendMessage(text, replyToId = replyId, forceFresh = freshFlag)
            } finally {
                _isSending.value = false
            }
        }
    }

    fun toggleForceFresh() {
        _forceFresh.value = !_forceFresh.value
    }

    fun startEditMessage(message: Message) {
        val content = editHandler.startEditing(message) ?: return
        _inputText.value = content
    }

    fun cancelEdit() {
        editHandler.cancelEditing()
    }

    fun abortCurrentRun() {
        viewModelScope.launch {
            repository.abortCurrentRun()
        }
    }

    /**
     * Sends `gateway.restart` (Confirm tier -- the gateway will round-trip a
     * permission.request over this same connection before restarting itself).
     * Restart happens ~600ms after the ack, so the caller should expect a brief
     * disconnect/reconnect rather than treating a success response as "already back up".
     */
    suspend fun restartGateway(): Result<Unit> {
        val response = repository.restartGateway()
        return if (response.ok) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(response.error?.message ?: "Gateway restart failed"))
        }
    }

    // ── B1: server slash command execution (see SlashCommandController) ───────
    fun executeCommand(commandName: String) = slashCommands.executeCommand(commandName)

    // ── Phase 16: native-chat HITL permission methods (see StreamPhaseController) ──
    fun approveNativePermission(scope: PermissionScope) = phaseController.approveNativePermission(scope)
    fun denyNativePermission(scope: PermissionScope = PermissionScope.Once) =
        phaseController.denyNativePermission(scope)

    fun markRetryAvailable() {
        _showRetry.value = true
        _consecutiveFailures.value++
    }

    fun retryLastMessage() {
        val msg = _lastSentMessage.value ?: return
        _showRetry.value = false
        _isAwaitingResponse.value = true
        viewModelScope.launch {
            repository.sendMessage(msg)
        }
    }

    fun retrySyncMessage(messageId: String) {
        viewModelScope.launch {
            repository.retrySendMessage(messageId)
        }
    }

    fun markContextWarningShown() {
        _contextWarningShown.value = true
    }

    fun interrupt() {
        val ctx = InterruptContext(
            lastActivity = _currentActivity.value,
            partialText = repository.messages.value
                .lastOrNull { it.isStreaming }?.content?.takeLast(200),
            runDurationMs = if (_thinkingStartTimeMs.value > 0)
                System.currentTimeMillis() - _thinkingStartTimeMs.value else 0L,
        )
        _interruptContext.value = ctx
        _currentActivity.value = null
        // Clear the status line immediately on local abort — don't wait for the server's
        // ChatAborted confirmation, which can lag by a round-trip.
        phaseController.resetToIdle()

        viewModelScope.launch {
            try {
                repository.abortCurrentRun()
            } catch (_: Exception) {
                // Best-effort abort — proceed to enable resume regardless
            }
            _canResume.value = true
        }
    }

    fun forkFromMessage(message: Message) {
        viewModelScope.launch {
            sessionRepository.forkFromMessage(message, repository.messages.value)
        }
    }

    fun resumePreviousTask() {
        val ctx = _interruptContext.value ?: return
        _canResume.value = false
        _interruptContext.value = null

        val parts = mutableListOf("Resume the task you were working on.")
        ctx.lastActivity?.let { parts.add("You were: " + it + ".") }
        ctx.partialText?.let { parts.add("Last output: ..." + it) }
        val resumeMsg = parts.joinToString(" ")

        _isAwaitingResponse.value = true
        viewModelScope.launch {
            repository.sendMessage(resumeMsg)
        }
    }

    fun toggleVerbose() {
        val newValue = !_verboseEnabled.value
        _verboseEnabled.value = newValue
        viewModelScope.launch {
            repository.sendMessage(if (newValue) "/verbose on" else "/verbose off")
        }
    }

    fun setThinkingLevel(level: String) {
        _thinkingLevel.value = level
        viewModelScope.launch {
            repository.sendMessage("/think $level")
        }
    }

    fun setSessionMode(mode: String) {
        val key = sessionRepository.currentSessionKey.value ?: return
        viewModelScope.launch {
            sessionRepository.setMode(key, mode)
        }
    }

    /**
     * Sets the message being replied to. Pass null to clear.
     */
    fun setReplyTo(message: Message?) {
        _replyingTo.value = message
    }

    /**
     * Clears the current reply target.
     */
    fun clearReply() {
        _replyingTo.value = null
    }

    fun sendMessageWithAttachments(context: Context, attachmentViewModel: AttachmentViewModel): List<String> {
        val text = _inputText.value.trim()
        val prepared = attachmentViewModel.prepareAttachments(context)

        // Build the full message: user text + any inline file content
        val fullMessage = listOfNotNull(
            text.ifBlank { null },
            prepared.inlineText.ifBlank { null },
        ).joinToString("\n\n")

        if (fullMessage.isEmpty() && prepared.imageAttachments.isEmpty()) {
            return prepared.unsupportedFiles
        }

        _inputText.value = ""
        _replyingTo.value = null
        _isSending.value = true
        _isAwaitingResponse.value = true

        viewModelScope.launch {
            try {
                if (prepared.imageAttachments.isNotEmpty()) {
                    repository.sendMessageWithAttachments(fullMessage, prepared.imageAttachments)
                } else {
                    repository.sendMessage(fullMessage)
                }
            } finally {
                _isSending.value = false
                attachmentViewModel.clearAttachments()
            }
        }

        return prepared.unsupportedFiles
    }

    fun startSTT(context: Context) = speechInput.startSTT(context)

    fun stopSTT() = speechInput.stopSTT()

    fun deleteMessage(messageId: String) {
        repository.deleteMessage(messageId)
    }

    fun regenerateResponse(assistantMessageId: String) {
        val lastUserMsg = repository.findLastUserMessageBefore(assistantMessageId)
        if (lastUserMsg != null) {
            viewModelScope.launch {
                repository.abortCurrentRun()
                repository.deleteMessage(assistantMessageId)
                _isAwaitingResponse.value = true
                repository.sendMessage(lastUserMsg.content)
            }
        }
    }

    fun shareMessage(content: String) {
        _shareContent.value = content
    }

    private val _shareContent = MutableStateFlow<String?>(null)
    val shareContent: StateFlow<String?> = _shareContent.asStateFlow()

    fun clearShareContent() {
        _shareContent.value = null
    }

    private val _rememberResult = MutableStateFlow<String?>(null)
    val rememberResult: StateFlow<String?> = _rememberResult.asStateFlow()

    fun rememberMessage(content: String) {
        viewModelScope.launch {
            val ok = brainRepository.remember(content)
            _rememberResult.value = if (ok) "Memory saved" else "Failed to save memory"
            if (ok) brainRepository.loadStatus()
        }
    }

    fun clearRememberResult() {
        _rememberResult.value = null
    }

    val brainMemoryCount: StateFlow<Int> = brainRepository.brainStatus
        .let { statusFlow ->
            val mapped = MutableStateFlow(0)
            viewModelScope.launch {
                statusFlow.collect { status ->
                    mapped.value = status?.memory_count ?: 0
                }
            }
            mapped
        }

    suspend fun refreshHistory() {
        repository.loadHistory()
    }

    fun downloadFile(path: String) {
        viewModelScope.launch {
            val response = fileDownloadRepository.downloadFile(path)
            if (response != null) {
                fileSaver?.save(response)
            }
        }
    }

    private suspend fun autoNameSessionIfNeeded() {
        val key = sessionRepository.currentSessionKey.value ?: return
        val sessions = sessionRepository.sessions.value
        val current = sessions.firstOrNull { it.sessionKey == key } ?: return
        val title = current.title
        if (title != null && title != "New Chat") return

        // Find the first user message in this conversation
        val firstUserMsg = repository.messages.value.firstOrNull {
            it.role == com.scaso.drclawapp.data.model.Role.USER
        } ?: return

        val autoTitle = firstUserMsg.content
            .replace("\n", " ")
            .trim()
            .take(40)
            .let { if (firstUserMsg.content.length > 40) "$it..." else it }

        if (autoTitle.isNotBlank()) {
            sessionRepository.renameSession(key, autoTitle)
        }
    }

    // ── Reconnect reliability: history refresh + stale-run resolution ────────────
    /**
     * Refreshes chat history via [ChatRepository.loadHistory] (the v0.9.3 "parse res frame,
     * filter tool/placeholder messages" machinery — reused as-is) and, if this device was
     * still marked busy from before the reconnect, resolves that state once the refresh
     * confirms nothing is actively streaming.
     *
     * Why this is needed on top of the existing `connectionState.collect { ... }` block in
     * [init]: that block only fires on a genuine *change* to [ConnectionState] because
     * [GatewayClient.connectionState] is a StateFlow, which silently drops an emission that
     * is `equals()` to the current value. Reconnecting into the *same* resumed session often
     * returns a structurally-identical [com.scaso.drclawapp.data.websocket.HelloOk] (same
     * server info, same session snapshot/policy) — `Connected(hello)` compares equal to the
     * previous `Connected(hello)`, so the StateFlow never re-emits and that block never runs
     * again. [GatewayEvent.Connected] is delivered over a SharedFlow instead, which has no
     * such dedup, so it fires reliably on every successful (re)connect/auth.
     *
     * Why the busy state can be stuck: if a turn finishes entirely on the server while the
     * app is disconnected, the gateway's catchup replay only covers runs still live in its
     * registry (by design) — a finished run is gone, so no ChatCatchup/ChatFinal ever reaches
     * this device to clear isAwaitingResponse/streamingPhase. Without this, the elapsed timer
     * and status line would count up indefinitely even though the answer already landed.
     */
    private suspend fun refreshHistoryAndResolveStaleRun() {
        val wasBusy = _isAwaitingResponse.value ||
            phaseController.streamingPhase.value !in setOf(StreamPhase.Idle, StreamPhase.Complete)
        repository.loadHistory()
        if (wasBusy && repository.messages.value.none { it.isStreaming }) {
            // Nothing came back as still-streaming — either the missed final answer is now
            // in the refreshed history, or the run was aborted server-side. Either way,
            // nothing is still generating, so stop the stuck spinner/status line.
            _isAwaitingResponse.value = false
            phaseController.resetToIdle()
            _currentActivity.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechInput.destroy()
        repository.disconnect()
    }

}
