package com.scaso.drclawapp.data.plugins

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Repository for Ironjaw plugin management.
 * No Android imports -- KMP-extractable.
 */
class PluginRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _plugins = MutableStateFlow<List<IronjawPlugin>>(emptyList())
    val plugins: StateFlow<List<IronjawPlugin>> = _plugins

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun listPlugins() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("plugins.list")
                if (response.ok && response.payload != null) {
                    val arr = response.payload!!.jsonObject["plugins"]?.jsonArray
                        ?: throw IllegalStateException("Missing 'plugins' key in response")
                    _plugins.value = arr.map {
                        json.decodeFromJsonElement(IronjawPlugin.serializer(), it)
                    }
                } else {
                    _error.value = response.error?.message ?: "Failed to load plugins"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun togglePlugin(id: String, enabled: Boolean): Boolean {
        return try {
            val params = buildJsonObject {
                put("id", id)
                put("enabled", enabled)
            }
            val response = gatewayClient.sendGenericRequest("plugins.toggle", params)
            if (response.ok) {
                listPlugins()
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
