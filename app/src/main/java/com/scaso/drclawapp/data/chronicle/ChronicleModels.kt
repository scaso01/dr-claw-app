package com.scaso.drclawapp.data.chronicle

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CC Chronicle session models.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class ChronicleSession(
    val id: String,
    val title: String? = null,
    val machine: String? = null,
    val date: String? = null,
    val summary: String? = null,
    val project: String? = null,
)

@Serializable
data class ChronicleSearchResult(
    @SerialName("session_id") val sessionId: String? = null,
    val title: String? = null,
    val snippet: String? = null,
    val score: Float? = null,
)

@Serializable
data class ChronicleTopic(
    val name: String,
    val description: String? = null,
    @SerialName("session_count") val sessionCount: Int = 0,
    @SerialName("last_updated") val lastUpdated: String? = null,
)

@Serializable
data class ChronicleStats(
    @SerialName("total_sessions") val totalSessions: Int? = null,
    @SerialName("avg_length") val avgLength: Float? = null,
    @SerialName("top_projects") val topProjects: List<String>? = null,
    @SerialName("by_machine") val byMachine: Map<String, Int>? = null,
)

@Serializable
data class ChronicleInsights(
    @SerialName("current_focus") val currentFocus: List<FocusProject> = emptyList(),
    @SerialName("stale_projects") val staleProjects: List<StaleProject> = emptyList(),
    @SerialName("unfinished_work") val unfinishedWork: List<UnfinishedItem> = emptyList(),
    @SerialName("recent_decisions") val recentDecisions: List<RecentDecision> = emptyList(),
    val activity: List<ActivityDay> = emptyList(),
    val activeProjects: ActiveProjects? = null,
    val peakHours: List<Int> = emptyList(),
    val avgDurationMinutes: Float? = null,
    val totalSessions: Int? = null,
    val totalSummaries: Int? = null,
)

@Serializable
data class FocusProject(
    val project: String,
    @SerialName("session_count") val sessionCount: Int = 0,
    @SerialName("total_seconds") val totalSeconds: Long = 0,
)

@Serializable
data class StaleProject(
    val project: String,
    @SerialName("last_session") val lastSession: String? = null,
)

@Serializable
data class UnfinishedItem(
    @SerialName("session_id") val sessionId: String? = null,
    val project: String? = null,
    val title: String? = null,
    val decision: String? = null,
)

@Serializable
data class RecentDecision(
    val project: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
    val decision: String? = null,
    val date: String? = null,
)

@Serializable
data class ActivityDay(
    val date: String,
    val count: Int = 0,
)

@Serializable
data class ActiveProjects(
    @SerialName("7d") val sevenDay: List<ActiveProjectCount> = emptyList(),
    @SerialName("30d") val thirtyDay: List<ActiveProjectCount> = emptyList(),
)

@Serializable
data class ActiveProjectCount(
    val project: String,
    val count: Int = 0,
)
