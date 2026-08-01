package com.scaso.drclawapp.data.websocket

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Events emitted by cc-bridge WebSocket.
 * Maps to server-side event frames: { type: "event", event: "<name>", sessionId, data }
 */
sealed class CcBridgeEvent {

    /** Session initialized — transport spawned, SDK session ID assigned */
    data class SessionInit(
        val sessionId: String,
        val data: JsonObject,
    ) : CcBridgeEvent()

    /** Streaming content delta — text, tool_use, tool_result, thinking */
    data class SessionStream(
        val sessionId: String,
        val kind: String,        // text_delta, tool_use, tool_result, thinking
        val text: String? = null,
        val toolName: String? = null,
        val input: JsonElement? = null,
        val data: JsonObject,
    ) : CcBridgeEvent()

    /** Final result from session (generation complete) */
    data class SessionResult(
        val sessionId: String,
        val data: JsonObject,
    ) : CcBridgeEvent()

    /** Session status change — idle, streaming, tool_executing, waiting_permission */
    data class SessionStatus(
        val sessionId: String,
        val status: String,
    ) : CcBridgeEvent()

    /** Full assistant message block */
    data class SessionAssistant(
        val sessionId: String,
        val data: JsonObject,
    ) : CcBridgeEvent()

    /** Session error */
    data class SessionError(
        val sessionId: String,
        val error: String,
        val data: JsonObject? = null,
    ) : CcBridgeEvent()

    /** Session closed / destroyed */
    data class SessionClosed(
        val sessionId: String,
        val reason: String? = null,
    ) : CcBridgeEvent()

    /** Permission request from interactive-mode session */
    data class SessionPermission(
        val sessionId: String,
        val requestId: String,
        val toolName: String,
        val input: JsonElement? = null,
        val data: JsonObject,
    ) : CcBridgeEvent()

    /** Session ID resolved — tracking key mapped to real CC session UUID */
    data class SessionResolved(
        val trackingKey: String,
        val resolvedSessionId: String,
    ) : CcBridgeEvent()

    /** Agent-generated UI canvas (HTML rendered inline in chat) */
    data class SessionCanvas(
        val sessionId: String,
        val html: String,
        val url: String? = null,
    ) : CcBridgeEvent()

    /** Connection state changed (synthetic, not from server) */
    data class ConnectionChanged(
        val state: CcBridgeConnectionState,
    ) : CcBridgeEvent()
}

/** Connection state for cc-bridge WebSocket (simpler than Gateway — no HelloOk) */
sealed class CcBridgeConnectionState {
    data object Disconnected : CcBridgeConnectionState()
    data object Connecting : CcBridgeConnectionState()
    data object Authenticating : CcBridgeConnectionState()
    data object Connected : CcBridgeConnectionState()
    data class Error(val message: String, val retryMs: Long? = null) : CcBridgeConnectionState()
}
