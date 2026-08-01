package com.scaso.drclawapp.data.infra

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Infrastructure health check models.
 * Gateway proxies health checks for all components.
 *
 * No Android imports — KMP-extractable.
 */

@Serializable
data class InfraStatusResponse(
    val components: List<InfraComponent> = emptyList(),
    val checkedAt: Long? = null,
)

@Serializable
data class InfraComponent(
    val name: String,
    val status: ComponentStatus = ComponentStatus.UNKNOWN,
    val message: String? = null,
    val detail: String? = null,
    val endpoint: String? = null,
    val certExpiresAt: Long? = null,
)

@Serializable
enum class ComponentStatus {
    @SerialName("ok") HEALTHY,
    @SerialName("warn") WARNING,
    @SerialName("error") ERROR,
    @SerialName("unknown") UNKNOWN,
}

@Serializable
data class DaemonStatus(
    val active: Boolean = false,
    @SerialName("last_heartbeat") val lastHeartbeat: String? = null,
    @SerialName("actions_today") val actionsToday: Int = 0,
    @SerialName("action_log") val actionLog: List<String> = emptyList(),
)

@Serializable
data class IronjawMetrics(
    @SerialName("uptime_secs")
    val uptimeSecs: Long = 0,
    @SerialName("started_at")
    val startedAt: String? = null,
    val metrics: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("circuit_breakers")
    val circuitBreakers: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    @SerialName("devices_connected")
    val devicesConnected: Int? = null,
)
