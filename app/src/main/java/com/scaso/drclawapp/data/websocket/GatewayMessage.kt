package com.scaso.drclawapp.data.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Protocol v4 frame types for Ironjaw Gateway WebSocket communication.
 * No Android imports — KMP-extractable.
 */



// --- Outbound frames ---

@Serializable
data class RequestFrame(
    val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonElement? = null,
)

/**
 * Outbound cmd.res frame for responding to server-initiated requests
 * (e.g., permission.request events from Ironjaw).
 * The [id] must match the request_id from the original event.
 */
@Serializable
data class CmdResFrame(
    val type: String = "cmd.res",
    val id: String,
    val ok: Boolean,
    val data: JsonElement? = null,
    val error: String? = null,
)

// --- Inbound frames ---

@Serializable
data class ResponseFrame(
    val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: JsonElement? = null,
    val error: ErrorShape? = null,
)

@Serializable
data class EventFrame(
    val type: String = "event",
    val event: String,
    val payload: JsonElement? = null,
    val seq: Long? = null,
)

// --- Chat params ---

@Serializable
data class ChatSendParams(
    val message: String,
    @SerialName("idempotencyKey")
    val idempotencyKey: String,
    val sessionKey: String? = null,
    val attachments: List<RpcAttachment>? = null,
    @SerialName("force_fresh")
    val forceFresh: Boolean? = null,
)

@Serializable
data class ChatHistoryParams(
    val sessionKey: String? = null,
    val limit: Int? = null,
)

@Serializable
data class ChatAbortParams(
    val runId: String? = null,
    val sessionKey: String? = null,
)

// --- Session params ---

@Serializable
data class SessionsListParams(
    val limit: Int? = null,
    val offset: Int? = null,
    val includeDerivedTitles: Boolean? = true,
    val includeLastMessage: Boolean? = true,
)

// v4: session.rename uses session_id and title
@Serializable
data class SessionRenameParams(
    @SerialName("session_id")
    val sessionId: String,
    val title: String,
)

// Legacy v3 alias kept for reference; no longer sent on the wire
@Deprecated("Use SessionRenameParams with session.rename instead", ReplaceWith("SessionRenameParams"))
@Serializable
data class SessionsPatchParams(
    val sessionKey: String,
    val title: String? = null,
)

@Serializable
data class SessionsDeleteParams(
    @SerialName("session_id")
    val sessionId: String,
)

@Serializable
data class SessionCreateParams(
    val title: String? = null,
)

@Serializable
data class ChatInjectParams(
    val message: String,
    val sessionKey: String? = null,
)

// --- Session response models ---

@Serializable
data class SessionsListResponse(
    val sessions: List<SessionListEntry> = emptyList(),
    val ts: Long? = null,
)

@Serializable
data class SessionListEntry(
    val key: String,
    val displayName: String? = null,
    val derivedTitle: String? = null,
    val title: String? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val messageCount: Int = 0,
    val lastMessagePreview: String? = null,
    val agentId: String? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val modelOverride: String? = null,
    val providerOverride: String? = null,
)

// --- Ironjaw session response models ---

@Serializable
data class IronjawSession(
    val id: String,
    val title: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    @SerialName("context_tokens")
    val contextTokens: Int? = null,
    @SerialName("compaction_count")
    val compactionCount: Int? = null,
    @SerialName("message_count")
    val messageCount: Int? = null,
    @SerialName("context_pct")
    val contextPct: Int? = null,
    @SerialName("context_window")
    val contextWindow: Int? = null,
    // Cumulative lifetime usage, persisted per turn by the gateway (2026-07-24)
    @SerialName("input_tokens")
    val inputTokens: Long = 0,
    @SerialName("output_tokens")
    val outputTokens: Long = 0,
)

@Serializable
data class IronjawSessionsListResponse(
    val sessions: List<IronjawSession> = emptyList(),
    val count: Int = 0,
)

// --- prompt.stats response (system-prompt token cost by layer) ---

@Serializable
data class PromptLayerStat(
    val name: String,
    val chars: Int = 0,
    @SerialName("estimated_tokens")
    val estimatedTokens: Int = 0,
)

@Serializable
data class PromptStatsResult(
    @SerialName("total_chars")
    val totalChars: Int = 0,
    @SerialName("estimated_tokens")
    val estimatedTokens: Int = 0,
    val layers: List<PromptLayerStat> = emptyList(),
)

@Serializable
data class SessionPreviewEntry(
    val sessionKey: String,
    val lastMessage: String? = null,
    val title: String? = null,
)

// --- Attachment model for chat.send (Phase 7) ---

@Serializable
data class RpcAttachment(
    val mimeType: String,
    val content: String,
    val fileName: String? = null,
    val type: String? = null,
)

@Serializable
data class TtsConvertParams(
    val text: String,
    val voice: String? = null,
)

// --- Error shape ---

@Serializable
data class ErrorShape(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val retryAfterMs: Long? = null,
)

// --- Helper to discriminate inbound frame type ---

@Serializable
data class FrameEnvelope(
    val type: String,
    val id: String? = null,
    val method: String? = null,
    val event: String? = null,
    val ok: Boolean? = null,
    val payload: JsonElement? = null,
    val data: JsonElement? = null,
    val error: ErrorShape? = null,
    val params: JsonElement? = null,
    val seq: Long? = null,
    val tool: String? = null,
) {
    /** Ironjaw sends "data", legacy gateway sends "payload" — accept both */
    val resolvedPayload: JsonElement? get() = payload ?: data
}
