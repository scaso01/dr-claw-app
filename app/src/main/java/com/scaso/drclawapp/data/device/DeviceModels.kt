package com.scaso.drclawapp.data.device

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Ironjaw device connection models.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class IronjawDevice(
    val id: String,
    val name: String = "",
    val platform: String = "",
    @SerialName("app_version")
    val appVersion: String? = null,
    @SerialName("connected_at")
    val connectedAt: String? = null,
    @SerialName("is_current")
    val isCurrent: Boolean? = null,
)

@Serializable
data class DeviceCmdResult(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null,
)
