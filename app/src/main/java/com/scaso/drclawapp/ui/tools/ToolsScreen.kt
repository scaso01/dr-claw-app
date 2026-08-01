package com.scaso.drclawapp.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.tools.IronjawTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var executeToolName by remember { mutableStateOf<String?>(null) }
    var executeParams by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::loadTools,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                // Search field
                item(key = "search") {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search tools...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                    )
                }

                if (uiState.error != null) {
                    item(key = "error") {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                items(uiState.filteredTools, key = { it.name }) { tool ->
                    ToolListCard(
                        tool = tool,
                        onClick = { viewModel.selectTool(tool) },
                        onExecute = { name ->
                            executeToolName = name
                            executeParams = ""
                        },
                    )
                }

                if (uiState.filteredTools.isEmpty() && !uiState.isLoading && uiState.error == null) {
                    item(key = "empty") {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No matching tools"
                            else "No tools available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                }
            }
        }
    }

    // Schema bottom sheet
    if (uiState.selectedTool != null) {
        val tool = uiState.selectedTool!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissToolSheet,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TierChip(tier = tool.tier)
                Spacer(modifier = Modifier.height(12.dp))
                if (tool.schema != null) {
                    Text(
                        text = "Schema",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val prettyJson = try {
                        Json { prettyPrint = true }
                            .encodeToString(JsonElement.serializer(), tool.schema)
                    } catch (_: Exception) {
                        tool.schema.toString()
                    }
                    Text(
                        text = prettyJson,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "No schema available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        viewModel.dismissToolSheet()
                        executeToolName = tool.name
                        executeParams = ""
                    },
                ) {
                    Text("Execute")
                }
            }
        }
    }

    // Execute dialog
    if (executeToolName != null) {
        AlertDialog(
            onDismissRequest = { executeToolName = null },
            title = { Text("Execute: ${executeToolName}") },
            text = {
                Column {
                    Text(
                        "JSON parameters (optional):",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = executeParams,
                        onValueChange = { executeParams = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("{}") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        minLines = 3,
                        maxLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val params: JsonElement? = try {
                            if (executeParams.isBlank()) null
                            else Json.parseToJsonElement(executeParams)
                        } catch (_: Exception) {
                            null
                        }
                        viewModel.executeTool(executeToolName!!, params)
                        executeToolName = null
                    },
                ) {
                    Text("Execute")
                }
            },
            dismissButton = {
                TextButton(onClick = { executeToolName = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Result dialog
    if (uiState.executeResult != null) {
        val result = uiState.executeResult!!
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = {
                Text(if (result.success) "Success" else "Error")
            },
            text = {
                Text(
                    text = result.result?.toString()
                        ?: result.error
                        ?: "No output",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissResult) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun ToolListCard(
    tool: IronjawTool,
    onClick: () -> Unit,
    onExecute: (String) -> Unit,
) {
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (tool.description.isNotBlank()) {
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            TierChip(tier = tool.tier)
        }
    }
}

@Composable
private fun TierChip(tier: Int) {
    val (color, label) = when (tier) {
        0 -> Color(0xFF4CAF50) to "Auto"
        1 -> Color(0xFFFFC107) to "Notify"
        2 -> Color(0xFFFF9800) to "Confirm"
        3 -> Color(0xFFF44336) to "Multi"
        else -> Color.Gray to "Unknown"
    }
    AssistChip(
        onClick = {},
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color,
        ),
    )
}
