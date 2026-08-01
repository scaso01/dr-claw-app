package com.scaso.drclawapp.data.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Ironjaw tool metadata from tools.list RPC.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class IronjawTool(
    val name: String,
    val description: String = "",
    val tier: Int = 0,
    val schema: JsonElement? = null,
)

@Serializable
data class ToolExecuteResult(
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null,
)
