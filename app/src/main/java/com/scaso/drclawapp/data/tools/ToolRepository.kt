package com.scaso.drclawapp.data.tools

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Repository for Ironjaw tool operations.
 * No Android imports -- KMP-extractable.
 */
class ToolRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _tools = MutableStateFlow<List<IronjawTool>>(emptyList())
    val tools: StateFlow<List<IronjawTool>> = _tools

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun listTools() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("tools.list")
                if (response.ok && response.payload != null) {
                    // Ironjaw returns {"tools": [...], "count": N} — extract the nested array
                    val toolsArray = response.payload!!.jsonObject["tools"]?.jsonArray
                        ?: throw IllegalStateException("Missing 'tools' key in response")
                    _tools.value = toolsArray.map { element ->
                        json.decodeFromJsonElement(IronjawTool.serializer(), element)
                    }
                } else {
                    _error.value = response.error?.message ?: "Failed to load tools"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun executeTool(name: String, params: JsonElement? = null): ToolExecuteResult {
        return try {
            val requestParams = buildJsonObject {
                put("name", name)
                if (params != null) {
                    put("params", params)
                }
            }
            val response = gatewayClient.sendGenericRequest("tools.execute", requestParams)
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(ToolExecuteResult.serializer(), response.payload!!)
            } else {
                ToolExecuteResult(
                    success = false,
                    error = response.error?.message ?: "Execution failed",
                )
            }
        } catch (e: Exception) {
            ToolExecuteResult(success = false, error = e.message)
        }
    }
}
