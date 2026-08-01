package com.scaso.drclawapp.data.schedule

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Ironjaw schedule/cron models.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class IronjawSchedule(
    val id: String,
    val name: String,
    val cron: String = "",
    val action: String? = null,
    val enabled: Boolean = true,
    @SerialName("last_run")
    val lastRun: String? = null,
    @SerialName("next_run")
    val nextRun: String? = null,
)
