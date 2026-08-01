package com.scaso.drclawapp.data.roles

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RoleRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _roles = MutableStateFlow<List<RoleSkill>>(emptyList())
    val roles: StateFlow<List<RoleSkill>> = _roles

    private val _activeRole = MutableStateFlow<RoleSkill?>(null)
    val activeRole: StateFlow<RoleSkill?> = _activeRole

    private val _activeModel = MutableStateFlow<String?>(null)
    val activeModel: StateFlow<String?> = _activeModel

    private val _aliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val aliases: StateFlow<Map<String, String>> = _aliases

    private val _availableModels = MutableStateFlow<List<AvailableModel>>(emptyList())
    val availableModels: StateFlow<List<AvailableModel>> = _availableModels

    private val _modelAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val modelAliases: StateFlow<Map<String, String>> = _modelAliases

    private val _loadedModel = MutableStateFlow<String?>(null)
    val loadedModel: StateFlow<String?> = _loadedModel

    private val _activePreset = MutableStateFlow<String?>(null)
    val activePreset: StateFlow<String?> = _activePreset.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Auto-refresh when model changes (broadcast from gateway)
        scope.launch {
            gatewayClient.modelChangedFlow.collect {
                loadRoles()
                loadAvailableModels()
            }
        }
    }

    fun loadRoles() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val listJob = scope.launch {
                    val response = gatewayClient.sendGenericRequest("role.list")
                    if (response.ok && response.payload != null && response.payload != JsonNull) {
                        val parsed = json.decodeFromString(
                            RoleListResponse.serializer(), response.payload.toString()
                        )
                        _roles.value = parsed.roles
                        _aliases.value = parsed.aliases.associate { it.alias to it.model }
                    } else if (!response.ok) {
                        _error.value = response.error?.message ?: "Failed to load roles"
                    }
                }

                val activeJob = scope.launch {
                    val response = gatewayClient.sendGenericRequest("role.active")
                    if (response.ok && response.payload != null && response.payload != JsonNull) {
                        val active = json.decodeFromString(
                            RoleActiveResponse.serializer(), response.payload.toString()
                        )
                        // Build RoleSkill from flat active response
                        val skill = RoleSkill(
                            name = active.role ?: "",
                            description = active.description ?: "",
                            model = active.model,
                            fallbackChain = active.fallbackChain,
                            cloudModels = active.cloudModels,
                            tools = active.tools,
                            temperature = active.temperature,
                        )
                        _activeRole.value = skill
                        _activeModel.value = active.resolvedModel
                    } else if (!response.ok && _error.value == null) {
                        _error.value = response.error?.message ?: "Failed to load active role"
                    }
                }

                listJob.join()
                activeJob.join()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAvailableModels() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("model.available")
                if (response.ok && response.payload != null && response.payload != JsonNull) {
                    val parsed = json.decodeFromString(
                        ModelAvailableResponse.serializer(), response.payload.toString()
                    )
                    _availableModels.value = parsed.models
                    _modelAliases.value = parsed.aliases.associate { it.alias to it.model }
                    _loadedModel.value = parsed.loadedModel
                } else if (!response.ok) {
                    _error.value = response.error?.message ?: "Failed to load available models"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun switchRole(name: String) {
        scope.launch {
            try {
                _isSwitching.value = true
                _error.value = null

                val params = buildJsonObject { put("name", name) }
                val response = gatewayClient.sendGenericRequest("role.switch", params)
                if (response.ok) {
                    loadRoles()
                } else {
                    _error.value = response.error?.message ?: "Switch role failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSwitching.value = false
            }
        }
    }

    fun switchModel(model: String) {
        scope.launch {
            try {
                _isSwitching.value = true
                _error.value = null

                val params = buildJsonObject { put("model", model) }
                val response = gatewayClient.sendGenericRequest("model.switch", params)
                if (response.ok) {
                    loadRoles()
                    loadAvailableModels()
                } else {
                    _error.value = response.error?.message ?: "Switch model failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSwitching.value = false
            }
        }
    }

    fun setRoleModel(role: String, model: String) {
        scope.launch {
            try {
                _isSwitching.value = true
                _error.value = null

                val params = buildJsonObject {
                    put("role", role)
                    put("model", model)
                }
                val response = gatewayClient.sendGenericRequest("model.session", params)
                if (response.ok) {
                    _activePreset.value = null
                    loadRoles()
                } else {
                    _error.value = response.error?.message ?: "Set role model failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSwitching.value = false
            }
        }
    }

    fun applyPreset(preset: String) {
        scope.launch {
            try {
                _isSwitching.value = true
                _error.value = null

                val params = buildJsonObject { put("preset", preset) }
                val response = gatewayClient.sendGenericRequest("role.preset.apply", params)
                if (response.ok) {
                    if (response.payload != null) {
                        json.decodeFromString(
                            PresetApplyResponse.serializer(), response.payload.toString()
                        )
                    }
                    _activePreset.value = preset
                    loadRoles()
                } else {
                    _error.value = response.error?.message ?: "Apply preset failed"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isSwitching.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
