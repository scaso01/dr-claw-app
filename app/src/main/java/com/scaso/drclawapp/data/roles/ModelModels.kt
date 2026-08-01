package com.scaso.drclawapp.data.roles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IronjawModelInfo(
    val id: String? = null,
    val name: String = "",
    val provider: String = "",
    val status: String = "",
)

@Serializable
data class LlamaServerProps(
    val model: String? = null,
    @SerialName("n_ctx") val nCtx: Int? = null,
    @SerialName("total_slots") val totalSlots: Int? = null,
)

@Serializable
data class ModelSwitchResponse(
    val model: String = "",
    val acknowledged: Boolean = false,
    val note: String? = null,
)

@Serializable
data class LocalModel(
    val name: String,
    val size: String,
    val loaded: Boolean = false,
    val vram: String? = null,
)

data class ModelChangedEvent(
    val model: String,
    val previous: String? = null,
    val source: String? = null,
)

data class SwitchProgress(
    val elapsedSecs: Int,
    val timeoutSecs: Int = 60,
    val status: String = "switching",
)

@Serializable
data class RecentModel(
    val name: String,
    val lastUsed: Long,
)
