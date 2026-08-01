package com.scaso.drclawapp.data.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Secrets vault models for Ironjaw.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class VaultGetResult(
    val success: Boolean,
    val value: String? = null,
    val error: String? = null,
)

@Serializable
data class VaultSetResult(
    val success: Boolean,
    val error: String? = null,
)
