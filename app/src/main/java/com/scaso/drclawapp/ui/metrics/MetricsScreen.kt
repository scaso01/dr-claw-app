package com.scaso.drclawapp.ui.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val metrics = uiState.metrics

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            // Uptime card
            item(key = "uptime") {
                val days = metrics.uptimeSecs / 86400
                val hours = (metrics.uptimeSecs % 86400) / 3600
                val mins = (metrics.uptimeSecs % 3600) / 60
                val uptimeStr = buildString {
                    if (days > 0) append("${days}d ")
                    append("${hours}h ${mins}m")
                }
                MetricCard(label = "Uptime", value = uptimeStr)
            }

            // Connected devices
            metrics.devicesConnected?.let { count ->
                item(key = "devices") {
                    MetricCard(label = "Connected Devices", value = count.toString())
                }
            }

            // Started at
            metrics.startedAt?.let { started ->
                item(key = "started") {
                    MetricCard(label = "Started At", value = started)
                }
            }

            // Circuit breakers
            if (metrics.circuitBreakers != null && metrics.circuitBreakers.isNotEmpty()) {
                item(key = "cb_header") {
                    Text(
                        text = "Circuit Breakers",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                metrics.circuitBreakers.forEach { (name, state) ->
                    val stateStr = state.toString().trim('"')
                    item(key = "cb_$name") {
                        val color = when {
                            stateStr.contains("closed", ignoreCase = true) -> Color(0xFF4CAF50)
                            stateStr.contains("half", ignoreCase = true) -> Color(0xFFFFC107)
                            stateStr.contains("open", ignoreCase = true) -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = color.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = stateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Raw metrics
            if (metrics.metrics != null && metrics.metrics.isNotEmpty()) {
                item(key = "metrics_header") {
                    Text(
                        text = "Raw Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                metrics.metrics.forEach { (key, value) ->
                    item(key = "metric_$key") {
                        MetricCard(label = key, value = value.toString().trim('"'))
                    }
                }
            }

            if (uiState.error != null) {
                item(key = "error") {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
