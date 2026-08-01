package com.scaso.drclawapp.data.model

import kotlinx.serialization.json.JsonElement

/**
 * In-memory record of a single tool invocation during a native-chat agentic turn.
 * Not persisted to Room in this phase — Phase 17 will add schema migration.
 * No Android imports — KMP-extractable.
 */
data class ToolEvent(
    val toolCallId: String,
    val toolName: String,
    val input: JsonElement,
    var status: ToolStatus,
    var output: JsonElement? = null,
    var error: String? = null,
    var durationMs: Long? = null,
)

enum class ToolStatus { Pending, Running, Done, Error }
