package com.scaso.drclawapp.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.device.IronjawDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var cmdTarget by remember { mutableStateOf<IronjawDevice?>(null) }
    var cmdText by remember { mutableStateOf("") }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::loadDevices,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            if (uiState.error != null) {
                item(key = "error") {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(uiState.devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onClick = {
                        cmdTarget = device
                        cmdText = ""
                    },
                )
            }

            if (uiState.devices.isEmpty() && !uiState.isLoading && uiState.error == null) {
                item(key = "empty") {
                    Text(
                        text = "No devices connected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        }
    }

    // Send command dialog
    if (cmdTarget != null) {
        AlertDialog(
            onDismissRequest = { cmdTarget = null },
            title = { Text("Send Command") },
            text = {
                Column {
                    Text(
                        text = "Device: ${cmdTarget!!.name.ifBlank { cmdTarget!!.id }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cmdText,
                        onValueChange = { cmdText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter command...") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.sendCommand(cmdTarget!!.id, cmdText)
                        cmdTarget = null
                    },
                    enabled = cmdText.isNotBlank(),
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { cmdTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Command result dialog
    if (uiState.cmdResult != null) {
        val result = uiState.cmdResult!!
        AlertDialog(
            onDismissRequest = viewModel::dismissCmdResult,
            title = { Text(if (result.success) "Command Result" else "Command Error") },
            text = {
                Text(
                    text = result.output ?: result.error ?: "No output",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCmdResult) { Text("OK") }
            },
        )
    }
}

@Composable
private fun DeviceCard(
    device: IronjawDevice,
    onClick: () -> Unit = {},
) {
    val icon = when {
        device.platform.contains("android", ignoreCase = true) -> Icons.Default.PhoneAndroid
        device.platform.contains("web", ignoreCase = true) -> Icons.Default.Language
        else -> Icons.Default.Computer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            Icon(
                imageVector = icon,
                contentDescription = device.platform,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name.ifBlank { device.id },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (device.isCurrent == true) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "This device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = device.platform,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                device.appVersion?.let { version ->
                    Text(
                        text = "v$version",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                device.connectedAt?.let { time ->
                    Text(
                        text = "Connected: $time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
