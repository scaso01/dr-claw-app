package com.scaso.drclawapp.ui.infra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.infra.ComponentStatus
import com.scaso.drclawapp.data.infra.DaemonStatus
import com.scaso.drclawapp.data.infra.InfraComponent
import com.scaso.drclawapp.data.websocket.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfraScreen(
    onBack: () -> Unit,
    viewModel: InfraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDaemonLog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Infrastructure") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                // Gateway status card
                item(key = "gateway_control") {
                    GatewayControlCard(
                        connectionState = uiState.connectionState,
                    )
                }

                items(uiState.components, key = { it.name }) { component ->
                    InfraComponentCard(component = component)
                }

                // Compaction status row
                item(key = "compaction") {
                    CompactionStatusCard(
                        lastStrategy = uiState.lastCompactStrategy,
                        tokensBefore = uiState.tokensBefore,
                        tokensAfter = uiState.tokensAfter,
                        lastCompactedAt = uiState.lastCompactedAt,
                        circuitBreakerOpen = uiState.compactionCircuitOpen,
                    )
                }

                // Daemon status card
                item(key = "daemon") {
                    DaemonStatusCard(
                        daemonStatus = uiState.daemonStatus,
                        onViewLog = { showDaemonLog = true },
                    )
                }

                if (uiState.checkedAt != null) {
                    item(key = "footer") {
                        Text(
                            text = "Last checked: ${formatTimestamp(uiState.checkedAt!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                if (uiState.error != null) {
                    item(key = "error") {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (showDaemonLog) {
        DaemonLogSheet(
            actionLog = uiState.daemonStatus.actionLog,
            onDismiss = { showDaemonLog = false },
        )
    }
}

@Composable
private fun InfraComponentCard(component: InfraComponent) {
    val (statusColor, statusLabel) = when (component.status) {
        ComponentStatus.HEALTHY -> Pair(Color(0xFF4CAF50), "Healthy")
        ComponentStatus.WARNING -> Pair(Color(0xFFFFC107), "Warning")
        ComponentStatus.ERROR -> Pair(Color(0xFFF44336), "Error")
        ComponentStatus.UNKNOWN -> Pair(MaterialTheme.colorScheme.outline, "Unknown")
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = statusColor,
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                val displayDetail = component.message ?: component.detail
                if (displayDetail != null) {
                    Text(
                        text = displayDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (component.certExpiresAt != null) {
                    val daysLeft = ((component.certExpiresAt - System.currentTimeMillis()) / 86_400_000).toInt()
                    val certColor = when {
                        daysLeft < 7 -> Color(0xFFF44336)
                        daysLeft < 14 -> Color(0xFFFFC107)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = "TLS cert: ${daysLeft}d remaining",
                        style = MaterialTheme.typography.bodySmall,
                        color = certColor,
                    )
                }
            }

            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun GatewayControlCard(
    connectionState: ConnectionState,
) {
    val (statusColor, statusText) = when (connectionState) {
        is ConnectionState.Connected -> Color(0xFF4CAF50) to "Connected"
        is ConnectionState.Connecting, is ConnectionState.Authenticating -> Color(0xFFFFC107) to "Connecting..."
        is ConnectionState.Disconnected -> Color(0xFFF44336) to "Disconnected"
        is ConnectionState.Error -> Color(0xFFF44336) to "Error"
        is ConnectionState.AuthFailed -> Color(0xFFF44336) to "Auth failed"
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = statusColor,
            ) {}

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ironjaw Gateway",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Content-only variant for embedding in the System tab (no Scaffold/TopAppBar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfraScreenContent(
    viewModel: InfraViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            item(key = "gateway_control") {
                GatewayControlCard(connectionState = uiState.connectionState)
            }

            items(uiState.components, key = { it.name }) { component ->
                InfraComponentCard(component = component)
            }

            item(key = "compaction_content") {
                CompactionStatusCard(
                    lastStrategy = uiState.lastCompactStrategy,
                    tokensBefore = uiState.tokensBefore,
                    tokensAfter = uiState.tokensAfter,
                    lastCompactedAt = uiState.lastCompactedAt,
                    circuitBreakerOpen = uiState.compactionCircuitOpen,
                )
            }

            item(key = "daemon_content") {
                DaemonStatusCard(
                    daemonStatus = uiState.daemonStatus,
                    onViewLog = {},
                )
            }

            if (uiState.checkedAt != null) {
                item(key = "footer") {
                    Text(
                        text = "Last checked: ${formatTimestamp(uiState.checkedAt!!)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (uiState.error != null) {
                item(key = "error") {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactionStatusCard(
    lastStrategy: String?,
    tokensBefore: Long?,
    tokensAfter: Long?,
    lastCompactedAt: Long?,
    circuitBreakerOpen: Boolean,
) {
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circuit breaker dot (10dp circle)
            val cbColor = if (circuitBreakerOpen) Color(0xFFF44336) else Color(0xFF4CAF50)
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = cbColor,
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Context Compaction",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (lastStrategy != null) {
                    Text(
                        text = "Strategy: $lastStrategy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tokensBefore != null && tokensAfter != null) {
                    Text(
                        text = "${formatTokenCount(tokensBefore)} -> ${formatTokenCount(tokensAfter)} tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lastCompactedAt != null) {
                    Text(
                        text = "Last: ${formatTimestamp(lastCompactedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lastStrategy == null) {
                    Text(
                        text = "No compaction data yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> "$tokens"
}

private fun formatTimestamp(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    return when {
        diffMs < 5_000 -> "Just now"
        diffMs < 60_000 -> "${diffMs / 1000}s ago"
        else -> "${diffMs / 60_000}m ago"
    }
}

@Composable
private fun DaemonStatusCard(
    daemonStatus: DaemonStatus,
    onViewLog: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status dot
            val statusColor = if (daemonStatus.active) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
            Surface(
                modifier = Modifier.size(12.dp),
                shape = CircleShape,
                color = statusColor,
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "KAIROS Daemon",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Badge(
                        containerColor = if (daemonStatus.active)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = if (daemonStatus.active) "ACTIVE" else "IDLE",
                            color = if (daemonStatus.active)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                daemonStatus.lastHeartbeat?.let { hb ->
                    Text(
                        text = "Last heartbeat: $hb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Today: ${daemonStatus.actionsToday} actions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (daemonStatus.actionLog.isNotEmpty()) {
                TextButton(onClick = onViewLog) {
                    Text("Log")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaemonLogSheet(
    actionLog: List<String>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Daemon Action Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (actionLog.isEmpty()) {
                Text(
                    text = "No actions recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.height(300.dp),
                ) {
                    items(actionLog.size) { index ->
                        Text(
                            text = actionLog[index],
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
