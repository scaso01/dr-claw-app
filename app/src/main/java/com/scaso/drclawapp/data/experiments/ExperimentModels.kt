package com.scaso.drclawapp.data.experiments

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models for the Agent Experiments / Operations Dashboard (D2).
 * No Android imports -- KMP-extractable.
 */

@Serializable
data class Experiment(
    val id: String,
    val name: String,
    val status: ExperimentStatus = ExperimentStatus.PENDING,
    val hypothesis: String? = null,
    val progress: Float = 0f,
    val metrics: List<MetricPoint> = emptyList(),
    val steps: List<PlanStep> = emptyList(),
    val decisions: List<DecisionEntry> = emptyList(),
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L,
    val error: String? = null,
)

@Serializable
enum class ExperimentStatus {
    @SerialName("pending") PENDING,
    @SerialName("running") RUNNING,
    @SerialName("paused") PAUSED,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("aborted") ABORTED,
}

@Serializable
data class MetricPoint(
    val label: String,
    val value: Float,
    val timestamp: Long = 0L,
)

@Serializable
data class PlanStep(
    val index: Int,
    val description: String,
    val completed: Boolean = false,
    val active: Boolean = false,
)

@Serializable
data class DecisionEntry(
    val timestamp: Long,
    val description: String,
    val rationale: String? = null,
)

@Serializable
data class ExperimentStatusResponse(
    val experiments: List<Experiment> = emptyList(),
)

@Serializable
data class ExperimentResultsResponse(
    val experiment: Experiment? = null,
    val summary: String? = null,
)
