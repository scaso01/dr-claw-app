package com.scaso.drclawapp.data.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Typed event payloads from the Ironjaw Gateway.
 * No Android imports — KMP-extractable.
 */

// --- Legacy v3 connect.challenge payload (unused in v4) ---

@Deprecated("Protocol v4 does not use connect.challenge; auth.connect is sent immediately on open")
@Serializable
data class ConnectChallenge(
    val nonce: String,
    val ts: Long,
)

// --- hello-ok response payload (synthesized from v4 auth.connect response) ---

@Serializable
data class HelloOk(
    val protocol: Int = 4,
    val server: ServerInfo? = null,
    val features: JsonElement? = null,
    val snapshot: Snapshot? = null,
    val policy: Policy = Policy(),
    val auth: JsonElement? = null,
)

@Serializable
data class Snapshot(
    val sessionDefaults: SessionDefaults? = null,
)

@Serializable
data class ServerInfo(
    val name: String? = null,
    val version: String? = null,
)

@Serializable
data class SessionDefaults(
    val mainSessionKey: String? = null,
    val defaultAgentId: String? = null,
)

@Serializable
data class Policy(
    val maxPayload: Int = 65536,
    val maxBufferedBytes: Int = 1048576,
    val tickIntervalMs: Long = 15000,
)

// --- chat event payload ---
// The "message" field is a structured object, not a plain string:
// {"role":"assistant","content":[{"type":"text","text":"..."}],"timestamp":...}

@Serializable
data class ChatEventPayload(
    val runId: String? = null,
    val sessionKey: String? = null,
    val seq: Int = 0,
    val state: String, // "delta", "final", "aborted", "error"
    val message: ChatMessage? = null,
    val stopReason: String? = null,
    val contextPct: Int? = null,
    val contextWindow: Int? = null,
    val contextTokens: Long? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
)

@Serializable
data class ChatMessage(
    val role: String? = null,
    val content: JsonElement? = null,
    val timestamp: Long? = null,
)

// --- chat.history response ---

@Serializable
data class ChatHistoryResponse(
    val sessionKey: String? = null,
    val sessionId: String? = null,
    val messages: List<HistoryMessage> = emptyList(),
    val thinkingLevel: String? = null,
    val verboseLevel: String? = null,
)

@Serializable
data class HistoryMessage(
    val role: String? = null,
    val content: JsonElement? = null,
    val timestamp: Long? = null,
)

// Legacy model kept for GatewayEvent.HistoryResult compatibility
data class HistoryEntry(
    val role: String,
    val content: String,
    val timestamp: Long? = null,
)

// --- A6: Memory reference attached to ChatFinal events ---

@Serializable
data class MemoryRef(
    val id: String,
    val scope: String,
    val tags: List<String> = emptyList(),
    @SerialName("trust_level") val trustLevel: Int = 0,
    val snippet: String = "",
    val score: Float = 0.0f,
)

// --- Sealed class for typed gateway events consumed by ChatRepository ---

sealed class GatewayEvent {
    data class ChatDelta(val runId: String?, val text: String, val seq: Int) : GatewayEvent()

    /**
     * Server-side stream resume: after auth.connect resume, if a chat run is still
     * generating for the session, ONE event frame arrives with the full accumulated
     * text so far. Subsequent "delta" frames continue with seq counting on from here.
     */
    data class ChatCatchup(val runId: String?, val content: String, val seq: Int) : GatewayEvent()
    data class ChatFinal(
        val runId: String?,
        val text: String?,
        val stopReason: String?,
        val contextPct: Int? = null,
        val contextWindow: Int? = null,
        val contextTokens: Long? = null,
        val inputTokens: Long? = null,
        val outputTokens: Long? = null,
        @SerialName("injected_memories") val injectedMemories: List<MemoryRef> = emptyList(),
    ) : GatewayEvent()
    data class ChatAborted(val runId: String?) : GatewayEvent()
    data class ChatError(val runId: String?, val message: String?) : GatewayEvent()
    data class HistoryResult(val entries: List<HistoryEntry>) : GatewayEvent()
    data class Tick(val seq: Long) : GatewayEvent()
    data class Connected(val hello: HelloOk) : GatewayEvent()
    data class ToolUse(val runId: String?, val toolName: String, val toolInput: String) : GatewayEvent()
    data class ToolResult(val runId: String?, val toolName: String?, val content: String) : GatewayEvent()
    data class ChatInject(val text: String, val sessionKey: String?) : GatewayEvent()
    data class Activity(val runId: String?, val tool: String, val summary: String, val timestamp: Long) : GatewayEvent()
    data class PermissionRequest(val requestId: String, val tool: String, val description: String, val consequences: String? = null) : GatewayEvent()
    data class CcComplete(val sessionId: String, val runId: String?) : GatewayEvent()

    // --- Phase 13: native-chat tool events ---

    /** Server started executing a tool during a native-chat agentic turn. */
    data class NativeToolStart(
        val runId: String?,
        val toolCallId: String,
        val toolName: String,
        val input: kotlinx.serialization.json.JsonElement,
    ) : GatewayEvent()

    /** Server finished executing a tool and has a result. */
    data class NativeToolResult(
        val runId: String?,
        val toolCallId: String,
        val toolName: String?,
        val output: kotlinx.serialization.json.JsonElement,
        val error: String? = null,
        val durationMs: Long? = null,
    ) : GatewayEvent()

    /**
     * Server is requesting user permission for a tool during a native-chat turn.
     * Distinct from the legacy [PermissionRequest] which is used for cc.chat path.
     */
    data class NativePermissionRequest(
        val requestId: String,
        val tool: String,
        val description: String,
        val toolCallId: String? = null,
        val allowSessionCache: Boolean = false,
    ) : GatewayEvent()

    /** The streaming phase of the current turn changed. */
    data class StreamPhaseChange(
        val runId: String?,
        val phase: StreamPhase,
    ) : GatewayEvent()

    /** The native-chat stream has fully completed (final signal). */
    data class StreamDone(
        val runId: String?,
        val usage: Usage? = null,
    ) : GatewayEvent()
}

// --- Phase 13 supporting types ---

/**
 * Represents the current streaming phase of a native-chat agentic turn.
 * Phase 14 will expand with richer phase semantics.
 */
enum class StreamPhase {
    Idle,
    Streaming,
    AwaitingApproval,
    ToolRunning,
    Synthesizing,
    Complete,
}

/**
 * Token usage summary attached to [GatewayEvent.StreamDone].
 * Phase 14 will expand with per-model breakdowns.
 */
data class Usage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
)

// --- B1: Slash command wire types ---

@Serializable
data class ArgSpec(
    val name: String,
    val description: String = "",
    val required: Boolean = false,
)

@Serializable
data class CommandSpec(
    val name: String,
    val description: String,
    val tier: String,
    val args: List<ArgSpec> = emptyList(),
)

@Serializable
data class CommandResult(
    val ok: Boolean,
    val output: String,
    val consequences: String? = null,
    @SerialName("injected_memories") val injectedMemories: List<MemoryRef> = emptyList(),
)
