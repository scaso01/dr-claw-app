package com.scaso.drclawapp.data.roles

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RoleSkill(
    val name: String = "",
    val description: String = "",
    val model: String? = null,
    val fallbackChain: List<String> = emptyList(),
    val cloudModels: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val temperature: Float? = null,
    val enabled: Boolean = false,
    val resolvedModel: String? = null,
    val resolvedCloudModels: List<String> = emptyList(),
)

@Serializable
data class AliasEntry(
    val alias: String = "",
    val model: String = "",
)

@Serializable
data class RoleListResponse(
    val roles: List<RoleSkill> = emptyList(),
    val aliases: List<AliasEntry> = emptyList(),
)

@Serializable
data class RoleActiveResponse(
    val role: String? = null,
    val description: String? = null,
    val model: String? = null,
    val resolvedModel: String? = null,
    val fallbackChain: List<String> = emptyList(),
    val cloudModels: List<String> = emptyList(),
    val temperature: Float? = null,
    val tools: List<String> = emptyList(),
    val note: String? = null,
)

@Serializable
data class AvailableModel(
    val name: String = "",
    val description: String? = null,
    val context: Int? = null,
    val loaded: Boolean = false,
    @SerialName("has_vision") val vision: Boolean = false,
    val type: String = "local",
    val provider: String = "",
    val available: Boolean = true,
)

sealed class ModelDropdownEntry {
    abstract val id: String
    abstract val displayName: String

    data class LocalModel(
        override val id: String,
        override val displayName: String,
        val isLoaded: Boolean,
        val context: Int?,
        val hasVision: Boolean,
    ) : ModelDropdownEntry()

    data class CloudModel(
        override val id: String,
        override val displayName: String,
        val provider: String,
    ) : ModelDropdownEntry()
}

fun AvailableModel.toDropdownEntry(): ModelDropdownEntry {
    return if (type == "cloud") {
        ModelDropdownEntry.CloudModel(
            id = name,
            displayName = name
                .removePrefix("anthropic/")
                .removePrefix("gemini/")
                .removePrefix("openai/"),
            provider = provider,
        )
    } else {
        ModelDropdownEntry.LocalModel(
            id = name,
            displayName = name.removePrefix("llama-server/"),
            isLoaded = loaded,
            context = context,
            hasVision = vision,
        )
    }
}

@Serializable
data class ModelAvailableResponse(
    val models: List<AvailableModel> = emptyList(),
    val aliases: List<AliasEntry> = emptyList(),
    @SerialName("loaded_model") val loadedModel: String? = null,
)

@Serializable
data class PresetApplyResponse(
    val preset: String = "",
    val roles: List<PresetRoleResult> = emptyList(),
)

@Serializable
data class PresetRoleResult(
    val name: String = "",
    val model: String? = null,
    val resolvedModel: String? = null,
)
