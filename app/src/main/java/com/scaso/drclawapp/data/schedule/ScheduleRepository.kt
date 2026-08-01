package com.scaso.drclawapp.data.schedule

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Repository for Ironjaw schedule (cron) operations.
 * No Android imports -- KMP-extractable.
 */
class ScheduleRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _schedules = MutableStateFlow<List<IronjawSchedule>>(emptyList())
    val schedules: StateFlow<List<IronjawSchedule>> = _schedules

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun listSchedules() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("schedule.list")
                if (response.ok && response.payload != null) {
                    // Ironjaw returns {"schedules": [...]} — extract the nested array
                    val arr = response.payload!!.jsonObject["schedules"]?.jsonArray
                        ?: throw IllegalStateException("Missing 'schedules' key in response")
                    _schedules.value = arr.map { json.decodeFromJsonElement(IronjawSchedule.serializer(), it) }
                } else {
                    _error.value = response.error?.message ?: "Failed to load schedules"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun createSchedule(name: String, cron: String, action: String?): Boolean {
        return try {
            val params = buildJsonObject {
                put("name", name)
                put("cron", cron)
                if (action != null) put("action", action)
            }
            val response = gatewayClient.sendGenericRequest("schedule.create", params)
            if (response.ok) {
                listSchedules()
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun toggleSchedule(id: String, enabled: Boolean): Boolean {
        return try {
            val params = buildJsonObject {
                put("id", id)
                put("enabled", enabled)
            }
            val response = gatewayClient.sendGenericRequest("schedule.toggle", params)
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteSchedule(id: String): Boolean {
        return try {
            val params = buildJsonObject { put("id", id) }
            val response = gatewayClient.sendGenericRequest("schedule.delete", params)
            if (response.ok) {
                listSchedules()
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }
}
