package com.scaso.drclawapp.ui.chat

import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.model.ActiveToolCall
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.StreamPhase
import com.scaso.drclawapp.ui.components.PermissionScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// Phase 14: combine the phase-machine flows into a single container
data class PhaseState(
    val streamingPhase: StreamPhase,
    val pendingPermission: GatewayEvent.NativePermissionRequest?,
    val activeTools: Set<ActiveToolCall>,
    val lastToolError: String?,
)

/**
 * Owns the Phase 14 native-chat stream-phase state machine: tool-call tracking,
 * the native HITL permission dialog state, and the phase-aware stall timer.
 * Extracted from [ChatViewModel] -- the gateway-event collector there still drives
 * this (same event branches, same order), just via delegated calls instead of
 * touching these fields directly.
 */
class StreamPhaseController(
    private val coroutineScope: CoroutineScope,
    private val approvalRepository: ApprovalRepository,
    private val onStall: () -> Unit,
) {
    private val _streamingPhase = MutableStateFlow(StreamPhase.Idle)
    private val _pendingPermission = MutableStateFlow<GatewayEvent.NativePermissionRequest?>(null)
    private val _activeTools = MutableStateFlow<Set<ActiveToolCall>>(emptySet())
    private val _lastToolError = MutableStateFlow<String?>(null)

    val streamingPhase: StateFlow<StreamPhase> = _streamingPhase.asStateFlow()

    val state: Flow<PhaseState> = combine(
        _streamingPhase,
        _pendingPermission,
        _activeTools,
        _lastToolError,
    ) { phase, permission, tools, toolError ->
        PhaseState(phase, permission, tools, toolError)
    }

    /**
     * Stall-warning thresholds (ms) are phase-aware.
     * Tools genuinely take seconds — give them 60s before warning.
     * Pure streaming stall is unusual — warn after 10s.
     */
    private val STALL_TIMEOUT_STREAMING_MS = 10_000L
    private val STALL_TIMEOUT_TOOL_RUNNING_MS = 60_000L
    private val STALL_TIMEOUT_AWAITING_APPROVAL_MS = 0L  // never stall — waiting on human
    // 90s: the 35B's prefill on long agentic contexts routinely exceeds 30s — a shorter
    // window fired "stall" on every synthesis pass of a healthy multi-tool turn.
    private val STALL_TIMEOUT_SYNTHESIZING_MS = 90_000L
    private var stallTimerJob: Job? = null

    /** Updates [streamingPhase] and restarts the phase-aware stall timer. */
    fun transitionPhase(phase: StreamPhase) {
        _streamingPhase.value = phase

        stallTimerJob?.cancel()
        val timeoutMs = when (phase) {
            StreamPhase.Streaming -> STALL_TIMEOUT_STREAMING_MS
            StreamPhase.ToolRunning -> STALL_TIMEOUT_TOOL_RUNNING_MS
            StreamPhase.AwaitingApproval -> STALL_TIMEOUT_AWAITING_APPROVAL_MS
            StreamPhase.Synthesizing -> STALL_TIMEOUT_SYNTHESIZING_MS
            StreamPhase.Idle, StreamPhase.Complete -> 0L
        }
        if (timeoutMs > 0L) {
            stallTimerJob = coroutineScope.launch {
                delay(timeoutMs)
                // Stall detected — surface the retry chip so the user can abort
                onStall()
            }
        }
    }

    fun onToolStart(event: GatewayEvent.NativeToolStart) {
        val call = ActiveToolCall(
            toolCallId = event.toolCallId,
            toolName = event.toolName,
            startedAt = System.currentTimeMillis(),
        )
        _activeTools.value = _activeTools.value + call
        // Only move to ToolRunning if we are not waiting on human approval
        if (_streamingPhase.value != StreamPhase.AwaitingApproval) {
            transitionPhase(StreamPhase.ToolRunning)
        }
    }

    fun onToolResult(event: GatewayEvent.NativeToolResult) {
        val remaining = _activeTools.value.filterNot { it.toolCallId == event.toolCallId }.toSet()
        _activeTools.value = remaining
        event.error?.let { _lastToolError.value = it }
        if (remaining.isEmpty() && _streamingPhase.value == StreamPhase.ToolRunning) {
            // All tools done — model is now synthesizing the response
            transitionPhase(StreamPhase.Synthesizing)
        }
    }

    fun onPermissionRequest(event: GatewayEvent.NativePermissionRequest, autoApproveTools: Set<String>) {
        // Check auto-approve allowlist before showing dialog
        if (event.tool in autoApproveTools) {
            // Auto-approve with Once scope — skip dialog entirely
            approvalRepository.respondNative(event.requestId, ok = true, scope = "once")
        } else {
            _pendingPermission.value = event
            transitionPhase(StreamPhase.AwaitingApproval)
        }
    }

    fun onStreamDone() {
        // Turn complete — mark Complete then tick to Idle so observers see the transition
        transitionPhase(StreamPhase.Complete)
        _activeTools.value = emptySet()
        _pendingPermission.value = null
        coroutineScope.launch {
            delay(1)
            transitionPhase(StreamPhase.Idle)
        }
    }

    /**
     * Drives Streaming from the first visible delta -- see ChatViewModel's messages watcher.
     * Called on EVERY delta while a message is streaming: re-entering Streaming re-arms the
     * stall timer, so active token flow never trips a false "stall" (previously the timer
     * only reset on phase CHANGES, so any answer streaming longer than the timeout stalled).
     */
    fun driveStreamingFromFirstDelta() {
        if (_streamingPhase.value != StreamPhase.AwaitingApproval) {
            transitionPhase(StreamPhase.Streaming)
        }
    }

    /** Clears tool/permission state and returns phase to Idle. */
    fun resetToIdle() {
        transitionPhase(StreamPhase.Idle)
        _activeTools.value = emptySet()
        _pendingPermission.value = null
    }

    fun setLastToolError(message: String?) {
        _lastToolError.value = message
    }

    /**
     * Approve a pending [GatewayEvent.NativePermissionRequest].
     * Sends cmd.res { ok=true, data.scope="once"|"session" } and clears the dialog.
     * Does NOT manually change streamingPhase — the gateway drives the next phase event.
     */
    fun approveNativePermission(scope: PermissionScope) {
        val req = _pendingPermission.value ?: return
        val wireScope = if (scope == PermissionScope.Session) "session" else "once"
        approvalRepository.respondNative(req.requestId, ok = true, scope = wireScope)
        _pendingPermission.value = null
    }

    /**
     * Deny a pending [GatewayEvent.NativePermissionRequest].
     * Sends cmd.res { ok=false, data.scope="once" } and clears the dialog.
     * Does NOT manually change streamingPhase — the gateway drives the next phase event.
     */
    fun denyNativePermission(scope: PermissionScope = PermissionScope.Once) {
        val req = _pendingPermission.value ?: return
        approvalRepository.respondNative(req.requestId, ok = false, scope = "once")
        _pendingPermission.value = null
    }
}
