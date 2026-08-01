package com.scaso.drclawapp.data.security

import kotlinx.serialization.Serializable

/**
 * Permission rule for pattern-based tool access control.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class PermissionRule(
    val id: String = "",
    val toolPattern: String = "",
    val pathPattern: String? = null,
    val action: String = "allow",
    val createdAt: Long = 0,
)
