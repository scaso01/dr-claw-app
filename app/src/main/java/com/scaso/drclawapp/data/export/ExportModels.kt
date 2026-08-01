package com.scaso.drclawapp.data.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Export/backup models for Ironjaw.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class ExportResult(
    val success: Boolean,
    val data: JsonElement? = null,
    val count: Int? = null,
    val error: String? = null,
)
