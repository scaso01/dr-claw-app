package com.scaso.drclawapp.data.infra

import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Repository for infrastructure health status.
 * Calls `infra.metrics` on Ironjaw Gateway and parses the response.
 *
 * No Android imports — KMP-extractable.
 */
class InfraRepository(
    private val gatewayClient: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _metrics = MutableStateFlow(IronjawMetrics())
    val metrics: StateFlow<IronjawMetrics> = _metrics

    private val _status = MutableStateFlow(InfraStatusResponse())
    val status: StateFlow<InfraStatusResponse> = _status

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _daemonStatus = MutableStateFlow(DaemonStatus())
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus

    /** Pass-through so the ViewModel doesn't need a direct GatewayClient reference. */
    val connectionState: StateFlow<ConnectionState> = gatewayClient.connectionState

    /**
     * Fetch infrastructure metrics from the Ironjaw Gateway.
     * Also includes the gateway's own connection state as the first component.
     */
    fun checkStatus() {
        scope.launch {
            _isLoading.value = true
            _error.value = null

            // Gateway connection is always locally known
            val gatewayComponent = InfraComponent(
                name = "Ironjaw Gateway",
                status = when (gatewayClient.connectionState.value) {
                    is ConnectionState.Connected -> ComponentStatus.HEALTHY
                    is ConnectionState.Connecting, is ConnectionState.Authenticating ->
                        ComponentStatus.WARNING
                    else -> ComponentStatus.ERROR
                },
                message = when (val s = gatewayClient.connectionState.value) {
                    is ConnectionState.Connected -> "Connected"
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Authenticating -> "Authenticating..."
                    is ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Error -> s.message
                    is ConnectionState.AuthFailed -> "Auth failed: ${s.message}"
                },
            )

            try {
                val response = gatewayClient.sendGenericRequest("infra.metrics")
                if (response.ok && response.payload != null) {
                    val result = json.decodeFromString(
                        IronjawMetrics.serializer(),
                        response.payload.toString(),
                    )
                    _metrics.value = result

                    // Build component list from metrics
                    val components = mutableListOf(gatewayComponent)

                    // Add uptime info as a component
                    val uptimeHours = result.uptimeSecs / 3600
                    val uptimeMins = (result.uptimeSecs % 3600) / 60
                    components.add(
                        InfraComponent(
                            name = "Uptime",
                            status = ComponentStatus.HEALTHY,
                            message = "${uptimeHours}h ${uptimeMins}m",
                        )
                    )

                    // Add device count if available
                    result.devicesConnected?.let { count ->
                        components.add(
                            InfraComponent(
                                name = "Connected Devices",
                                status = ComponentStatus.HEALTHY,
                                message = "$count device(s)",
                            )
                        )
                    }

                    // Add circuit breaker states
                    result.circuitBreakers?.forEach { (name, state) ->
                        val stateStr = state.toString().trim('"')
                        components.add(
                            InfraComponent(
                                name = "CB: $name",
                                status = when {
                                    stateStr.contains("closed", ignoreCase = true) -> ComponentStatus.HEALTHY
                                    stateStr.contains("half", ignoreCase = true) -> ComponentStatus.WARNING
                                    stateStr.contains("open", ignoreCase = true) -> ComponentStatus.ERROR
                                    else -> ComponentStatus.UNKNOWN
                                },
                                message = stateStr,
                            )
                        )
                    }

                    _status.value = InfraStatusResponse(
                        components = components,
                        checkedAt = System.currentTimeMillis(),
                    )
                } else {
                    _status.value = InfraStatusResponse(
                        components = listOf(gatewayComponent),
                        checkedAt = System.currentTimeMillis(),
                    )
                    _error.value = response.error?.message
                }
            } catch (e: Exception) {
                _status.value = InfraStatusResponse(
                    components = listOf(gatewayComponent),
                    checkedAt = System.currentTimeMillis(),
                )
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }

            // Also fetch daemon status (best-effort)
            try {
                val daemonRes = gatewayClient.sendGenericRequest("daemon.status")
                if (daemonRes.ok && daemonRes.payload != null) {
                    _daemonStatus.value = json.decodeFromString(
                        DaemonStatus.serializer(),
                        daemonRes.payload.toString(),
                    )
                }
            } catch (_: Exception) {
                // Daemon status is best-effort
            }
        }
    }
}
