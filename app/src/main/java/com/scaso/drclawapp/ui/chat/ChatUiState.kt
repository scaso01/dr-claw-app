package com.scaso.drclawapp.ui.chat

import com.scaso.drclawapp.data.model.ActiveToolCall
import com.scaso.drclawapp.data.model.Attachment
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.InterruptContext
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.StreamPhase

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val inputText: String = "",
    val isSending: Boolean = false,
    val isAwaitingResponse: Boolean = false,
    val replyingTo: Message? = null,
    val pendingAttachments: List<Attachment> = emptyList(),
    val isListeningSTT: Boolean = false,
    val verboseEnabled: Boolean = false,
    val forceFresh: Boolean = false,
    val thinkingLevel: String = "low",
    val thinkingStartTimeMs: Long = 0L,
    val canResume: Boolean = false,
    val interruptContext: InterruptContext? = null,
    val currentActivity: String? = null,
    val isCompacting: Boolean = false,
    val compactStrategy: String? = null,
    val showRetry: Boolean = false,
    val lastSentMessage: String? = null,
    val consecutiveFailures: Int = 0,
    val contextWarningShown: Boolean = false,
    val frustrationDetected: Boolean = false,

    // ── Phase 14: stream phase state machine ──────────────────────────────────
    /** Current phase of the active (or most recently completed) native-chat turn. */
    val streamingPhase: StreamPhase = StreamPhase.Idle,
    /** Pending native-chat permission request awaiting user HITL decision. */
    val pendingPermission: GatewayEvent.NativePermissionRequest? = null,
    /** Tool calls currently in-flight for the active turn. */
    val activeTools: Set<ActiveToolCall> = emptySet(),
    /** Error message from the most recent failed tool call, if any. Cleared on next turn start. */
    val lastToolError: String? = null,
    val sessionMode: String = "Default",
) {
    /**
     * Derived busy flag: true for any phase between turn start and completion.
     * Source of truth is [streamingPhase]; [isAwaitingResponse] is kept for
     * back-compat with pre-Phase-14 callers that wrote isBusy directly.
     */
    val isBusy: Boolean
        get() = streamingPhase !in setOf(StreamPhase.Idle, StreamPhase.Complete)
}

/**
 * Single source of truth for the unified agentic status line (the text shown above the
 * input bar between send and first visible answer text). Kept as a pure function so it's
 * unit-testable without Compose — [ui.chat.ChatScreen] renders whatever this returns.
 *
 * Priority: legacy cc.chat activity > compaction > Synthesizing ("Writing answer…") >
 * Thinking (awaiting first delta, no tools active yet) > nothing.
 * Tool-running text ("Running X…") is intentionally NOT here — [components.ActivityStatusBar]
 * derives it directly from [ChatUiState.activeTools] so it can tick an elapsed-seconds timer,
 * which a static string can't represent.
 */
fun ChatUiState.statusLineText(): String? = when {
    currentActivity != null -> currentActivity
    isCompacting -> "Compacting context" + (compactStrategy?.let { " ($it)" } ?: "") + "..."
    streamingPhase == StreamPhase.Synthesizing -> "Writing answer…"
    isAwaitingResponse && activeTools.isEmpty() && messages.none { it.isStreaming } -> "Thinking…"
    else -> null
}
