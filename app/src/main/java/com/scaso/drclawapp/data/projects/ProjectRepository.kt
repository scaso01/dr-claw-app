package com.scaso.drclawapp.data.projects

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
 * Repository for project status and triggers.
 * Fetches via gateway proxy (`project.status`).
 *
 * No Android imports — KMP-extractable.
 */
class ProjectRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _jobHunterStatus = MutableStateFlow(ProjectStatusResponse(project = "jobhunter"))
    val jobHunterStatus: StateFlow<ProjectStatusResponse> = _jobHunterStatus

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadJobHunterStatus() {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val params = buildJsonObject { put("project", "jobhunter") }
                val response = gatewayClient.sendGenericRequest("project.status", params)
                if (response.ok && response.payload != null) {
                    _jobHunterStatus.value = json.decodeFromString(
                        ProjectStatusResponse.serializer(),
                        response.payload.toString(),
                    )
                } else {
                    _error.value = response.error?.message ?: "Failed to load project status"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load project status"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Trigger a JobHunter run by sending a chat message to the main session.
     */
    suspend fun triggerJobHunter(): Boolean {
        return try {
            val response = gatewayClient.sendMessage("Run JobHunter now")
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    /** Returns the active Ironjaw session ID (null if not yet connected). */
    fun getActiveSessionId(): String? = gatewayClient.getCurrentSessionKey()

    /** Sets the working directory for [sessionId] via `session.set_cwd` (Ironjaw C1). */
    suspend fun setCwd(sessionId: String, path: String?): ResponseFrame =
        gatewayClient.sessionSetCwd(sessionId, path)
}
