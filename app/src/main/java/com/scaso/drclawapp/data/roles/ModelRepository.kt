package com.scaso.drclawapp.data.roles

import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
class ModelRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _modelInfo = MutableStateFlow(IronjawModelInfo())
    val modelInfo: StateFlow<IronjawModelInfo> = _modelInfo

    private val _llamaServerProps = MutableStateFlow(LlamaServerProps())
    val llamaServerProps: StateFlow<LlamaServerProps> = _llamaServerProps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _switchProgress = MutableStateFlow<SwitchProgress?>(null)
    val switchProgress: StateFlow<SwitchProgress?> = _switchProgress

    private var pollingJob: Job? = null

    init {
        // Listen for model.changed events from gateway
        scope.launch {
            gatewayClient.modelChangedFlow.collect { event ->
                // Model switch completed — stop polling, refresh state
                pollingJob?.cancel()
                _switchProgress.value = null
                _isSwitching.value = false
                refresh()
            }
        }
    }

    fun refresh() {
        scope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val statusJob = scope.launch {
                    val response = gatewayClient.sendGenericRequest("model.status")
                    if (response.ok && response.payload != null) {
                        _modelInfo.value = json.decodeFromString(
                            IronjawModelInfo.serializer(), response.payload.toString()
                        )
                    } else if (!response.ok) {
                        _error.value = response.error?.message ?: "Failed to load model status"
                    }
                }

                val llmJob = scope.launch {
                    val response = gatewayClient.sendGenericRequest("model.llm")
                    if (response.ok && response.payload != null) {
                        _llamaServerProps.value = json.decodeFromString(
                            LlamaServerProps.serializer(), response.payload.toString()
                        )
                    } else if (!response.ok && _error.value == null) {
                        _error.value = response.error?.message ?: "Failed to load LLM status"
                    }
                }

                statusJob.join()
                llmJob.join()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun switchModel(model: String) {
        scope.launch {
            try {
                _isSwitching.value = true
                _error.value = null

                val params = buildJsonObject {
                    put("model", model)
                    put("restart", true)
                }

                val response = gatewayClient.sendGenericRequest("model.switch", params)
                if (response.ok && response.payload != null) {
                    val switchResponse = json.decodeFromString(
                        ModelSwitchResponse.serializer(), response.payload.toString()
                    )
                    if (switchResponse.acknowledged) {
                        // Enable fast reconnect for model switch
                        gatewayClient.enableFastReconnect(65_000L)
                        // Start polling for switch progress
                        startSwitchPolling(model)
                    }
                } else {
                    _error.value = response.error?.message ?: "Switch failed"
                    _isSwitching.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isSwitching.value = false
            }
        }
    }

    private fun startSwitchPolling(targetModel: String) {
        pollingJob?.cancel()
        val startTime = System.currentTimeMillis()
        val timeoutMs = 60_000L

        pollingJob = scope.launch {
            while (isActive) {
                delay(2000L)
                val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                if (elapsed >= 60) {
                    _switchProgress.value = null
                    _error.value = "Model switch timed out after 60s"
                    _isSwitching.value = false
                    break
                }

                _switchProgress.value = SwitchProgress(
                    elapsedSecs = elapsed,
                    timeoutSecs = 60,
                    status = "switching",
                )

                // Poll model.status to check if new model is online
                try {
                    val response = gatewayClient.sendGenericRequest("model.status")
                    if (response.ok && response.payload != null) {
                        val info = json.decodeFromString(
                            IronjawModelInfo.serializer(), response.payload.toString()
                        )
                        val isOnline = info.status.equals("online", ignoreCase = true) ||
                                info.status.equals("ok", ignoreCase = true)
                        if (isOnline && info.name.contains(targetModel, ignoreCase = true)) {
                            // Switch completed
                            _modelInfo.value = info
                            _switchProgress.value = null
                            _isSwitching.value = false
                            refresh()
                            break
                        }
                    }
                } catch (_: Exception) {
                    // Gateway may be restarting — continue polling
                }
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
