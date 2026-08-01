package com.scaso.drclawapp.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarResult
import com.scaso.drclawapp.data.roles.AvailableModel
import com.scaso.drclawapp.data.roles.IronjawModelInfo
import com.scaso.drclawapp.data.roles.LlamaServerProps
import com.scaso.drclawapp.data.roles.ModelDropdownEntry
import com.scaso.drclawapp.data.roles.RecentModel
import com.scaso.drclawapp.data.roles.RoleSkill
import com.scaso.drclawapp.data.roles.SwitchProgress
import com.scaso.drclawapp.data.roles.toDropdownEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onBack: () -> Unit,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retryLastSwitch()
            }
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Model Manager") },
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
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                // llama-server status card — compact
                item {
                    LlamaServerCard(
                        modelInfo = uiState.modelInfo,
                        llamaServerProps = uiState.llamaServerProps,
                        isSwitching = uiState.isSwitching,
                        switchProgress = uiState.switchProgress,
                    )
                }
                // Recent models — quick-switch chips
                if (recentModels.isNotEmpty()) {
                    item {
                        RecentModelsRow(
                            recentModels = recentModels,
                            loadedModel = uiState.loadedModel,
                            isSwitching = uiState.isSwitching,
                            onSwitch = { viewModel.switchModel(it) },
                        )
                    }
                }
                // Preset toggle — Local / Cloud
                item {
                    PresetToggle(
                        activePreset = uiState.activePreset,
                        isSwitching = uiState.isSwitching,
                        onApplyPreset = viewModel::applyPreset,
                    )
                }
                // Role assignment card — single-line rows
                if (uiState.roles.isNotEmpty()) {
                    item {
                        RoleAssignmentCard(
                            roles = uiState.roles,
                            availableModels = uiState.availableModels,
                            loadedModel = uiState.loadedModel,
                            isSwitching = uiState.isSwitching,
                            onSetRoleModel = viewModel::setRoleModel,
                        )
                    }
                }
                // Available models section
                if (uiState.availableModels.isNotEmpty()) {
                    item {
                        Text(
                            text = "Available Models",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(uiState.availableModels, key = { it.name }) { model ->
                        AvailableModelRow(
                            model = model,
                            isLoaded = model.name == uiState.loadedModel,
                            onSwitch = { viewModel.switchModel(model.name) },
                        )
                    }
                }
            }
        }
    }
}

// ── llama-server card ─────────────────────────────────────────────────
// Compact: title + status badge on top line, model name below,
// context + slots as a single subtitle line.

@Composable
private fun LlamaServerCard(
    modelInfo: IronjawModelInfo,
    llamaServerProps: LlamaServerProps,
    isSwitching: Boolean = false,
    switchProgress: SwitchProgress? = null,
) {
    val isOnline = modelInfo.status.equals("online", ignoreCase = true) ||
            modelInfo.status.equals("ok", ignoreCase = true)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header: "llama-server" left, status badge right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "llama-server",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOnline)
                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = modelInfo.status.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Loading indicator when switching models
            if (isSwitching) {
                if (switchProgress != null) {
                    val progress = switchProgress.elapsedSecs.toFloat() / switchProgress.timeoutSecs.toFloat()
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }

            // Model name (monospace, prominent) — dimmed when switching
            val modelName = modelInfo.name.ifBlank { null } ?: llamaServerProps.model
            if (modelName != null) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSwitching)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            }

            if (isSwitching) {
                val progressText = if (switchProgress != null) {
                    "Switching... (${switchProgress.elapsedSecs}s / ${switchProgress.timeoutSecs}s)"
                } else {
                    "Loading new model..."
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Context + slots as a single subtitle line: "8192 ctx · 1 slot"
            val details = listOfNotNull(
                llamaServerProps.nCtx?.let { "$it ctx" },
                llamaServerProps.totalSlots?.let { "$it slot${if (it != 1) "s" else ""}" },
            ).joinToString(" \u00B7 ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Display name utility ─────────────────────────────────────────────

private fun displayModelName(resolvedModel: String): String {
    return resolvedModel
        .removePrefix("llama-server/")
        .removePrefix("anthropic/")
        .removePrefix("gemini/")
}

// ── Recent models row ────────────────────────────────────────────────
// Horizontally scrollable FilterChips for last 5 used models.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentModelsRow(
    recentModels: List<RecentModel>,
    loadedModel: String?,
    isSwitching: Boolean,
    onSwitch: (String) -> Unit,
) {
    Column {
        Text(
            text = "Recent Models",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recentModels.forEach { recent ->
                val isLoaded = recent.name == loadedModel
                FilterChip(
                    selected = isLoaded,
                    onClick = { if (!isSwitching && !isLoaded) onSwitch(recent.name) },
                    enabled = !isSwitching,
                    label = {
                        Text(
                            text = displayModelName(recent.name),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = if (isLoaded) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else null,
                )
            }
        }
    }
}

// ── Preset toggle ────────────────────────────────────────────────────
// Two FilterChips: "Local" and "Cloud". Neither selected when manual override.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetToggle(
    activePreset: String?,
    isSwitching: Boolean = false,
    onApplyPreset: (String) -> Unit,
) {
    var showCloudDialog by remember { mutableStateOf(false) }

    if (showCloudDialog) {
        AlertDialog(
            onDismissRequest = { showCloudDialog = false },
            title = { Text("Switch to Cloud Models?") },
            text = {
                Text("All roles will use Anthropic Claude. Responses are instant but cost money per token.")
            },
            dismissButton = {
                TextButton(onClick = { showCloudDialog = false }) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloudDialog = false
                    onApplyPreset("cloud")
                }) {
                    Text("Switch to Cloud")
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Preset",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = activePreset == "local",
                    onClick = { onApplyPreset("local") },
                    label = {
                        Text(if (isSwitching && activePreset != "local") "Applying..." else "Local")
                    },
                    enabled = !isSwitching,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
                FilterChip(
                    selected = activePreset == "cloud",
                    onClick = { showCloudDialog = true },
                    label = {
                        Text(if (isSwitching && activePreset != "cloud") "Applying..." else "Cloud")
                    },
                    enabled = !isSwitching,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
                if (activePreset == null) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Role assignment card ──────────────────────────────────────────────
// Each role row: name left, resolved model name center, dropdown chevron right.

@Composable
private fun RoleAssignmentCard(
    roles: List<RoleSkill>,
    availableModels: List<AvailableModel>,
    loadedModel: String?,
    isSwitching: Boolean = false,
    onSetRoleModel: (String, String) -> Unit,
) {
    val dropdownEntries = remember(availableModels, loadedModel) {
        availableModels.map { model ->
            if (model.type == "local" && model.name == loadedModel) {
                model.copy(loaded = true).toDropdownEntry()
            } else {
                model.toDropdownEntry()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Roles",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            roles.forEachIndexed { index, role ->
                RoleRow(
                    role = role,
                    dropdownEntries = dropdownEntries,
                    isSwitching = isSwitching,
                    onModelSelected = { model -> onSetRoleModel(role.name, model) },
                )
                if (index < roles.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

// ── Two-line role row with sectioned dropdown ─────────────────────────
// Line 1: Role name (bold)
// Line 2: Description (secondary)
// Line 3: Full-width dropdown trigger button

@Composable
private fun RoleRow(
    role: RoleSkill,
    dropdownEntries: List<ModelDropdownEntry>,
    isSwitching: Boolean = false,
    onModelSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = role.resolvedModel?.let { displayModelName(it) }
        ?: role.model
        ?: "Not set"

    val localModels = dropdownEntries.filterIsInstance<ModelDropdownEntry.LocalModel>()
    val cloudModels = dropdownEntries.filterIsInstance<ModelDropdownEntry.CloudModel>()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        // Line 1: Role name
        Text(
            text = role.name.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )

        // Line 2: Description
        if (role.description.isNotBlank()) {
            Text(
                text = role.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Line 3: Full-width dropdown trigger
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { if (!isSwitching) expanded = true },
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSwitching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "\u25BC",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                // Local models section
                if (localModels.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "LOCAL MODELS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    localModels.forEach { entry ->
                        val isSelected = entry.id == role.resolvedModel || entry.id == role.model
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check
                                        else Icons.Default.Computer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = entry.displayName,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else null,
                                    )
                                    if (entry.isLoaded) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                text = "loaded",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF4CAF50),
                                                modifier = Modifier.padding(
                                                    horizontal = 4.dp,
                                                    vertical = 1.dp,
                                                ),
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                onModelSelected(entry.id)
                                expanded = false
                            },
                            enabled = !isSwitching,
                        )
                    }
                }

                // Divider between sections
                if (localModels.isNotEmpty() && cloudModels.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                // Cloud models section
                if (cloudModels.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "CLOUD MODELS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    cloudModels.forEach { entry ->
                        val isSelected = entry.id == role.resolvedModel || entry.id == role.model
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check
                                        else Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = entry.displayName,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else null,
                                    )
                                }
                            },
                            onClick = {
                                onModelSelected(entry.id)
                                expanded = false
                            },
                            enabled = !isSwitching,
                        )
                    }
                }
            }
        }
    }
}

// ── Available model row ───────────────────────────────────────────────
// Single-line per model: green dot + name left, details subtitle, Load/check right.
// Replaces the old AvailableModelCard (less padding, tighter).

@Composable
private fun AvailableModelRow(
    model: AvailableModel,
    isLoaded: Boolean,
    onSwitch: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                if (isLoaded) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.size(8.dp),
                        content = {},
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isLoaded) FontWeight.Bold else null,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val details = listOfNotNull(
                        model.context?.let { "${it} ctx" },
                        if (model.vision) "vision" else null,
                        model.description,
                    ).joinToString(" \u00B7 ")
                    if (details.isNotBlank()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!isLoaded) {
                OutlinedButton(
                    onClick = onSwitch,
                    modifier = Modifier.semantics {
                        contentDescription = "Load ${model.name}"
                    },
                ) {
                    Text("Load")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Loaded",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
