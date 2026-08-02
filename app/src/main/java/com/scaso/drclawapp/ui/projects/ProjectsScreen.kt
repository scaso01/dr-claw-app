package com.scaso.drclawapp.ui.projects

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.projects.ProjectInfo
import com.scaso.drclawapp.data.projects.ProjectStatus
import com.scaso.drclawapp.data.projects.ProjectStatusResponse
import com.scaso.drclawapp.ui.ccbridge.CcPermissionDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when setCwdResult is populated
    LaunchedEffect(uiState.setCwdResult) {
        val msg = uiState.setCwdResult
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCwdResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            // Project CWD picker card (C7)
            item {
                ProjectCwdCard(
                    recentPaths = uiState.recentPaths,
                    isCwdSwitching = uiState.isCwdSwitching,
                    onSetCwd = { path -> viewModel.setProjectCwd(path) },
                )
            }

            items(uiState.projects, key = { it.info.name }) { projectState ->
                if (projectState.info.statusRpcSupported) {
                    ExamplePipelineCard(
                        project = projectState.info,
                        status = projectState.status,
                        isLoading = uiState.isLoading,
                        onTrigger = viewModel::triggerExamplePipeline,
                    )
                } else {
                    ProjectCard(project = projectState.info)
                }
            }

            if (uiState.error != null) {
                item {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Card for setting the active Claude Code session's working directory (C7).
 *
 * Shows a text field for manual path entry, recent-path chips for quick
 * re-selection, and a "Switch project" button wrapped in a PermissionDialog.
 * The dialog fires BEFORE the RPC so the user sees the consequences warning
 * even though GatewayClient auto-approves all session.* server-gate events.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCwdCard(
    recentPaths: List<String>,
    isCwdSwitching: Boolean,
    onSetCwd: (path: String?) -> Unit,
) {
    var pathInput by rememberSaveable { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }
    // The confirmed path to send when the user approves the dialog
    var pendingPath by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Active Project",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set the working directory for the current Claude Code session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = pathInput,
                onValueChange = { pathInput = it },
                label = { Text("Project path") },
                placeholder = { Text("C:\\code\\my-project") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Recent paths chips
            if (recentPaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recentPaths.forEach { path ->
                        FilterChip(
                            selected = pathInput == path,
                            onClick = { pathInput = path },
                            label = {
                                Text(
                                    text = path.substringAfterLast('\\').ifEmpty { path },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(
                onClick = {
                    val trimmed = pathInput.trim().ifEmpty { null }
                    pendingPath = trimmed
                    showPermissionDialog = true
                },
                enabled = !isCwdSwitching,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isCwdSwitching) {
                    Text("Switching...")
                } else {
                    Text("Switch project")
                }
            }
        }
    }

    // CcPermissionDialog shown BEFORE the RPC fires (pre-confirmation in UI).
    // This is required because GatewayClient auto-approves session.* methods
    // at the server-gate level, bypassing the normal HITL flow.
    if (showPermissionDialog) {
        CcPermissionDialog(
            toolName = "session.set_cwd",
            input = null,
            consequences = "Switching project changes the file root for tools and reloads CLAUDE.md.",
            onAllow = {
                showPermissionDialog = false
                onSetCwd(pendingPath)
            },
            onDeny = {
                showPermissionDialog = false
                pendingPath = null
            },
        )
    }
}

@Composable
private fun ProjectCard(project: ProjectInfo) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (project.dashboardUrl != null) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(project.dashboardUrl))
                            context.startActivity(intent)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dashboard")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExamplePipelineCard(
    project: ProjectInfo,
    status: ProjectStatusResponse,
    isLoading: Boolean,
    onTrigger: () -> Unit,
) {
    val context = LocalContext.current
    val (statusColor, statusLabel) = when (status.status) {
        ProjectStatus.IDLE -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "Idle")
        ProjectStatus.RUNNING -> Pair(Color(0xFF2196F3), "Running")
        ProjectStatus.FAILED -> Pair(Color(0xFFF44336), "Failed")
        ProjectStatus.UNKNOWN -> Pair(MaterialTheme.colorScheme.outline, "Unknown")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = statusColor,
                    ) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = project.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Pipeline progress (when running)
            if (status.status == ProjectStatus.RUNNING && status.pipeline != null) {
                Spacer(modifier = Modifier.height(12.dp))
                if (status.pipeline.message != null) {
                    Text(
                        text = status.pipeline.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { status.pipeline.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Last run summary
            if (status.lastRun != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Last Run",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))

                val lastRun = status.lastRun
                if (lastRun.timestamp != null) {
                    StatRow("Completed", formatTimestamp(lastRun.timestamp))
                }
                if (lastRun.durationMs != null) {
                    StatRow("Duration", formatDuration(lastRun.durationMs))
                }
                StatRow("Items scraped", "${lastRun.itemsScraped}")
                StatRow("Items matched", "${lastRun.itemsMatched}")
                if (lastRun.itemsSubmitted > 0) {
                    StatRow("Items submitted", "${lastRun.itemsSubmitted}")
                }
                if (lastRun.errors > 0) {
                    StatRow("Errors", "${lastRun.errors}")
                }
            }

            // Action buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (project.dashboardUrl != null) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(project.dashboardUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dashboard")
                    }
                }
                FilledTonalButton(
                    onClick = onTrigger,
                    enabled = status.status != ProjectStatus.RUNNING && !isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (status.status == ProjectStatus.RUNNING) "Running..." else "Run",
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val diffMs = System.currentTimeMillis() - epochMs
    val diffMin = diffMs / 60_000
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffMin < 1440 -> "${diffMin / 60}h ago"
        else -> "${diffMin / 1440}d ago"
    }
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
    }
}
