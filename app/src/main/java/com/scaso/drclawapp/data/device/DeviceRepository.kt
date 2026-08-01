package com.scaso.drclawapp.data.device

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Repository for Ironjaw connected devices.
 * No Android imports -- KMP-extractable.
 */
class DeviceRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _devices = MutableStateFlow<List<IronjawDevice>>(emptyList())
    val devices: StateFlow<List<IronjawDevice>> = _devices

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun listDevices() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("device.list")
                if (response.ok && response.payload != null) {
                    // Ironjaw returns {"devices": [...], "count": N} — extract the nested array
                    val arr = response.payload!!.jsonObject["devices"]?.jsonArray
                        ?: throw IllegalStateException("Missing 'devices' key in response")
                    _devices.value = arr.map { json.decodeFromJsonElement(IronjawDevice.serializer(), it) }
                } else {
                    _error.value = response.error?.message ?: "Failed to load devices"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun sendCommand(deviceId: String, command: String): DeviceCmdResult {
        return try {
            val params = buildJsonObject {
                put("id", deviceId)
                put("command", command)
            }
            val response = gatewayClient.sendGenericRequest("device.cmd", params)
            if (response.ok && response.payload != null) {
                json.decodeFromJsonElement(DeviceCmdResult.serializer(), response.payload!!)
            } else {
                DeviceCmdResult(
                    success = false,
                    error = response.error?.message ?: "Command failed",
                )
            }
        } catch (e: Exception) {
            DeviceCmdResult(success = false, error = e.message)
        }
    }
}
