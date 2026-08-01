package com.scaso.drclawapp.data.plugins

import kotlinx.serialization.Serializable

/**
 * Plugin management models for Ironjaw.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class IronjawPlugin(
    val id: String,
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = false,
    val version: String? = null,
)
