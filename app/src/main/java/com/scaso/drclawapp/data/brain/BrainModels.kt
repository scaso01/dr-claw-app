package com.scaso.drclawapp.data.brain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Brain/memory models for Ironjaw RAG.
 * No Android imports -- KMP-extractable.
 */
@Serializable
data class BrainMemory(
    val id: String? = null,
    val content: String,
    val similarity: Float? = null,
    val created_at: String? = null,
    val memory_type: String? = null,
    val importance: Double? = null,
    val strength: Double? = null,
    val valid_from: String? = null,
    val valid_to: String? = null,
    val source: String? = null,
)

@Serializable
data class RecallResult(
    val memories: List<BrainMemory> = emptyList(),
    val query: String = "",
)

@Serializable
data class GraphEntity(
    val id: String,
    val name: String = "",
    val type: String = "",
    val relations: List<GraphRelation> = emptyList(),
)

@Serializable
data class GraphRelation(
    val target: String,
    val label: String = "",
)

@Serializable
data class GraphResult(
    val entities: List<GraphEntity> = emptyList(),
)

@Serializable
data class ContextVariable(
    val id: Long = 0,
    val name: String,
    val content: String = "",
    val token_count: Long = 0,
    val source_path: String? = null,
    val loaded: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class ContextListResult(
    val variables: List<ContextVariable> = emptyList(),
)

@Serializable
data class PeekResult(
    val name: String = "",
    val content: String = "",
    val offset: Int = 0,
    val length: Int = 0,
    val total_length: Int = 0,
)

@Serializable
data class DreamStatus(
    val active: Boolean = false,
    @SerialName("current_phase") val currentPhase: Int = 0,
    @SerialName("phase_name") val phaseName: String = "",
    @SerialName("last_dream_at") val lastDreamAt: String? = null,
    @SerialName("time_gate_met") val timeGateMet: Boolean = false,
    @SerialName("session_gate_met") val sessionGateMet: Boolean = false,
    val locked: Boolean = false,
)

@Serializable
data class BrainStatus(
    val memory_count: Int = 0,
    val temporal_active: Int = 0,
    val temporal_expired: Int = 0,
    val context_vars: ContextVarStats = ContextVarStats(),
    val dream: DreamStatus = DreamStatus(),
)

@Serializable
data class ContextVarStats(
    val count: Int = 0,
    val total_tokens: Int = 0,
)

@Serializable
data class PendingMemory(
    val id: Long,
    val content: String,
    val source: String? = null,
    @SerialName("memory_type") val memoryType: String? = null,
    val tags: String? = null,
    val importance: Double? = null,
    val confidence: Double? = null,
    @SerialName("evidence_session_id") val evidenceSessionId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PendingListResult(
    val count: Int,
    val memories: List<PendingMemory>,
)

@Serializable
data class BrainSnapshot(
    val id: Long,
    val label: String? = null,
    @SerialName("memory_count") val memoryCount: Int = 0,
    @SerialName("identity_count") val identityCount: Int = 0,
    @SerialName("context_var_count") val contextVarCount: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SnapshotListResult(
    val snapshots: List<BrainSnapshot> = emptyList(),
)

@Serializable
data class SnapshotRestoreResult(
    val restored: Boolean = false,
    @SerialName("snapshot_id") val snapshotId: Long = 0,
    @SerialName("memories_written") val memoriesWritten: Int = 0,
    @SerialName("identities_written") val identitiesWritten: Int = 0,
    @SerialName("context_vars_written") val contextVarsWritten: Int = 0,
    @SerialName("memories_soft_deleted") val memoriesSoftDeleted: Int = 0,
)
