package com.scaso.drclawapp.data.approval

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for HITL (Human-In-The-Loop) approval flow (D10).
 * No Android imports -- KMP-extractable.
 */

@Serializable
data class ApprovalRequest(
    val id: String,
    val action: String,
    val description: String,
    val context: String? = null,
    val severity: ApprovalSeverity = ApprovalSeverity.NORMAL,
    @SerialName("timeout_ms") val timeoutMs: Long = 300_000L, // 5 min default
    @SerialName("created_at") val createdAt: Long = 0L,
    /** When true, the approval UI offers a persistent "always allow this action" option. */
    @SerialName("allow_always_option") val allowAlwaysOption: Boolean = false,
)

@Serializable
enum class ApprovalSeverity {
    @SerialName("low") LOW,
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
    @SerialName("critical") CRITICAL,
}

@Serializable
enum class ApprovalDecision {
    @SerialName("approve") APPROVE,
    @SerialName("deny") DENY,
    @SerialName("modify") MODIFY,
    /** Approve once and persist the action as auto-approved going forward (local requests only). */
    @SerialName("always_allow") ALWAYS_ALLOW,
}
