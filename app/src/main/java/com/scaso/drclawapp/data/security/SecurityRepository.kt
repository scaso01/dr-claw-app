package com.scaso.drclawapp.data.security

import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for permission rules via Ironjaw security.rules RPC.
 * No Android imports -- KMP-extractable.
 */
class SecurityRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _rules = MutableStateFlow<List<PermissionRule>>(emptyList())
    val rules: StateFlow<List<PermissionRule>> = _rules

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadRules() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("security.rules.list")
                if (response.ok && response.payload != null) {
                    val rules = json.decodeFromString(
                        kotlinx.serialization.builtins.ListSerializer(PermissionRule.serializer()),
                        response.payload.toString(),
                    )
                    _rules.value = rules.sortedByDescending { it.createdAt }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load rules"
            }
            _isLoading.value = false
        }
    }

    suspend fun addRule(
        toolPattern: String,
        pathPattern: String?,
        action: String,
    ): ResponseFrame {
        val params = buildJsonObject {
            put("toolPattern", toolPattern)
            pathPattern?.let { put("pathPattern", it) }
            put("action", action)
        }
        return gatewayClient.sendGenericRequest("security.rules.add", params)
    }

    suspend fun deleteRule(ruleId: String): ResponseFrame {
        val params = buildJsonObject {
            put("id", ruleId)
        }
        return gatewayClient.sendGenericRequest("security.rules.delete", params)
    }
}
