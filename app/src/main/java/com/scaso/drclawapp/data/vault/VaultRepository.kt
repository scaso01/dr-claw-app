package com.scaso.drclawapp.data.vault

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for Ironjaw secrets vault operations.
 * No Android imports -- KMP-extractable.
 */
class VaultRepository(
    private val gatewayClient: GatewayClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSecret(key: String): VaultGetResult {
        return try {
            val params = buildJsonObject { put("key", key) }
            val response = gatewayClient.sendGenericRequest("vault.get", params)
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(VaultGetResult.serializer(), response.payload!!)
            } else {
                VaultGetResult(
                    success = false,
                    error = response.error?.message ?: "Failed to get secret",
                )
            }
        } catch (e: Exception) {
            VaultGetResult(success = false, error = e.message)
        }
    }

    suspend fun setSecret(key: String, value: String): VaultSetResult {
        return try {
            val params = buildJsonObject {
                put("key", key)
                put("value", value)
            }
            val response = gatewayClient.sendGenericRequest("vault.set", params)
            if (response.ok) {
                VaultSetResult(success = true)
            } else {
                VaultSetResult(
                    success = false,
                    error = response.error?.message ?: "Failed to set secret",
                )
            }
        } catch (e: Exception) {
            VaultSetResult(success = false, error = e.message)
        }
    }
}
