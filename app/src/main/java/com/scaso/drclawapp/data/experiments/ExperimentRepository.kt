package com.scaso.drclawapp.data.experiments

import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for agent experiment operations.
 * Calls experiment.status, experiment.results, experiment.abort,
 * experiment.pause, and experiment.resume RPCs on Ironjaw Gateway.
 *
 * No Android imports -- KMP-extractable.
 */
class ExperimentRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _experiments = MutableStateFlow<List<Experiment>>(emptyList())
    val experiments: StateFlow<List<Experiment>> = _experiments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _selectedResults = MutableStateFlow<ExperimentResultsResponse?>(null)
    val selectedResults: StateFlow<ExperimentResultsResponse?> = _selectedResults

    /** Pass-through so the ViewModel doesn't need a direct GatewayClient reference. */
    val connectionState: StateFlow<ConnectionState> = gatewayClient.connectionState

    fun loadExperiments() {
        scope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = gatewayClient.sendGenericRequest("experiment.status")
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromJsonElement(
                        ExperimentStatusResponse.serializer(),
                        response.payload,
                    )
                    _experiments.value = result.experiments
                } else {
                    _error.value = response.error?.message ?: "Failed to load experiments"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadResults(experimentId: String) {
        scope.launch {
            _error.value = null
            try {
                val params = buildJsonObject { put("id", experimentId) }
                val response = gatewayClient.sendGenericRequest("experiment.results", params)
                if (response.ok && response.payload != null) {
                    _selectedResults.value = json.decodeFromJsonElement(
                        ExperimentResultsResponse.serializer(),
                        response.payload,
                    )
                } else {
                    _error.value = response.error?.message ?: "Failed to load results"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    suspend fun abort(experimentId: String): Boolean {
        return try {
            val params = buildJsonObject { put("id", experimentId) }
            val response = gatewayClient.sendGenericRequest("experiment.abort", params)
            if (response.ok) loadExperiments()
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun pause(experimentId: String): Boolean {
        return try {
            val params = buildJsonObject { put("id", experimentId) }
            val response = gatewayClient.sendGenericRequest("experiment.pause", params)
            if (response.ok) loadExperiments()
            response.ok
        } catch (_: Exception) {
            false
        }
    }

    suspend fun resume(experimentId: String): Boolean {
        return try {
            val params = buildJsonObject { put("id", experimentId) }
            val response = gatewayClient.sendGenericRequest("experiment.resume", params)
            if (response.ok) loadExperiments()
            response.ok
        } catch (_: Exception) {
            false
        }
    }
}
