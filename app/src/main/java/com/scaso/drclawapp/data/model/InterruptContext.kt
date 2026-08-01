package com.scaso.drclawapp.data.model

/**
 * Captured context when the user interrupts a running agent.
 * Used to offer a "Resume previous task" chip.
 */
data class InterruptContext(
    val lastActivity: String?,
    val partialText: String?,
    val runDurationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
)
