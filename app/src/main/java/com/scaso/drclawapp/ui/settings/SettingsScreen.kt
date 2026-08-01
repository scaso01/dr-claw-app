package com.scaso.drclawapp.ui.settings

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    darkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    onNavigateToPairing: () -> Unit = {},
    onNavigateToPermissionRules: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // -- Appearance Section --
            SectionHeader(title = "Appearance")

            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            ThemeModeSelector(
                currentMode = uiState.themeMode,
                onModeSelected = { mode ->
                    viewModel.setThemeMode(mode)
                    // Also update the live theme via the callback
                    when (mode) {
                        "dark" -> onThemeChanged(true)
                        "light" -> onThemeChanged(false)
                        // "system" — caller handles via isSystemInDarkTheme()
                    }
                },
            )

            SectionDivider()

            // -- Chat Section --
            SectionHeader(title = "Chat")

            Text(
                text = "Custom Instructions",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            Text(
                text = "These instructions are sent at the start of each session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            OutlinedTextField(
                value = uiState.customInstructions,
                onValueChange = viewModel::setCustomInstructions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .testTag("custom_instructions_field"),
                placeholder = { Text("e.g., Always respond in a concise manner...") },
                maxLines = 8,
                shape = MaterialTheme.shapes.medium,
            )

            SectionDivider()

            // -- Floating Bubble Section --
            SectionHeader(title = "Floating Bubble")

            Text(
                text = "Show a floating chat bubble when the app is in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable bubble overlay", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = uiState.bubbleEnabled,
                    onCheckedChange = { viewModel.setBubbleEnabled(it) },
                )
            }

            SectionDivider()

            // -- Push Notifications Section --
            NtfySection(
                enabled = uiState.ntfyEnabled,
                topic = uiState.ntfyTopic,
                serverUrl = uiState.ntfyUrl,
                onEnabledChanged = viewModel::setNtfyEnabled,
                onTopicChanged = viewModel::setNtfyTopic,
                onServerUrlChanged = viewModel::setNtfyUrl,
            )

            SectionDivider()

            // -- Effort Level Section --
            SectionHeader(title = "Effort Level")

            Text(
                text = "Controls how much effort the AI puts into responses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            EffortLevelSelector(
                currentLevel = uiState.effortLevel,
                onLevelSelected = viewModel::setEffortLevel,
            )

            SectionDivider()

            // -- Voice Section --
            SectionHeader(title = "Voice")

            Text(
                text = "Character voice used for TTS responses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            VoicePicker(
                voices = uiState.voices,
                selectedVoiceId = uiState.selectedVoice,
                onVoiceSelected = viewModel::setVoice,
            )

            SectionDivider()

            // -- Proactive AI Section --
            SectionHeader(title = "Proactive AI")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable proactive suggestions", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Periodically collects context (calendar, time) and suggests actions. Requires calendar permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.proactiveEnabled,
                    onCheckedChange = { viewModel.setProactiveEnabled(it) },
                )
            }

            SectionDivider()

            // -- Tool Auto-Approve Section (Phase 16) --
            AutoApproveToolsSection(
                tools = uiState.autoApproveTools,
                onAddTool = viewModel::addAutoApproveTool,
                onRemoveTool = viewModel::removeAutoApproveTool,
            )

            SectionDivider()

            // -- Connection Section --
            SectionHeader(title = "Connection")

            var editableUrl by rememberSaveable { mutableStateOf("") }
            val currentUrl = uiState.gatewayUrl
            LaunchedEffect(currentUrl) {
                if (editableUrl.isEmpty()) editableUrl = currentUrl
            }
            OutlinedTextField(
                value = editableUrl,
                onValueChange = { editableUrl = it },
                label = { Text("Gateway URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { viewModel.reconnectGateway(editableUrl) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Reconnect")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phase 3: Meshnet IP the gateway hostname is pinned to (skips DNS on the Meshnet
            // path). Escape hatch if the IP ever changes — blank resets to the built-in default.
            val meshnetIp by viewModel.meshnetIp.collectAsStateWithLifecycle()
            var editableMeshnetIp by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(meshnetIp) {
                if (editableMeshnetIp.isEmpty()) editableMeshnetIp = meshnetIp
            }
            OutlinedTextField(
                value = editableMeshnetIp,
                onValueChange = { editableMeshnetIp = it },
                label = { Text("Meshnet IP (advanced)") },
                supportingText = { Text("Pins the gateway host to this IP. Blank = built-in default.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { viewModel.setMeshnetIp(editableMeshnetIp) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Apply IP & Reconnect")
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onNavigateToPairing,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Pair Device")
            }

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onNavigateToPermissionRules,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Permission Rules")
            }

            SectionDivider()

            // -- About Section --
            SectionHeader(title = "About")

            InfoRow(label = "Version", value = uiState.appVersion)
            InfoRow(label = "Build", value = uiState.buildType)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    currentMode: String,
    onModeSelected: (String) -> Unit,
) {
    val options = listOf("system", "light", "dark")
    val labels = listOf("System", "Light", "Dark")

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onModeSelected(mode) },
                selected = currentMode == mode,
            ) {
                Text(labels[index])
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffortLevelSelector(
    currentLevel: String,
    onLevelSelected: (String) -> Unit,
) {
    val options = listOf("low", "medium", "high")
    val labels = listOf("Low", "Medium", "High")

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, level ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onLevelSelected(level) },
                selected = currentLevel == level,
            ) {
                Text(labels[index])
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    voices: List<VoiceOption>,
    selectedVoiceId: String,
    onVoiceSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedVoice = voices.find { it.id == selectedVoiceId } ?: voices.first()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = "${selectedVoice.name} — ${selectedVoice.character}",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = voice.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = voice.character,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onVoiceSelected(voice.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Phase 16: Tool Auto-Approve settings section.
 * Shows the current allowlist with trash-icon delete buttons, plus an add-tool text field.
 * Tools in this list are auto-approved without showing the HITL dialog in native chat.
 */
@Composable
private fun AutoApproveToolsSection(
    tools: Set<String>,
    onAddTool: (String) -> Unit,
    onRemoveTool: (String) -> Unit,
) {
    var newToolName by rememberSaveable { mutableStateOf("") }

    SectionHeader(title = "Tool Auto-Approve")

    Text(
        text = "Tools in this list are approved automatically without showing a permission dialog during native chat.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    // Sorted list of current allowlist entries
    val sortedTools = remember(tools) { tools.sorted() }
    sortedTools.forEach { tool ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tool,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRemoveTool(tool) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove $tool from auto-approve list",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (sortedTools.isEmpty()) {
        Text(
            text = "No tools auto-approved. Add a tool name below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = newToolName,
            onValueChange = { newToolName = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("e.g., read_file") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = MaterialTheme.shapes.medium,
        )
        FilledTonalButton(
            onClick = {
                if (newToolName.isNotBlank()) {
                    onAddTool(newToolName)
                    newToolName = ""
                }
            },
        ) {
            Text("Add")
        }
    }
}

@Composable
private fun NtfySection(
    enabled: Boolean,
    topic: String,
    serverUrl: String,
    onEnabledChanged: (Boolean) -> Unit,
    onTopicChanged: (String) -> Unit,
    onServerUrlChanged: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    SectionHeader(title = "Push Notifications")

    Text(
        text = "Receive push notifications from Dr. CLAW via ntfy when the app is backgrounded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Enable ntfy notifications", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
    }

    if (enabled) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Topic",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = topic,
                onValueChange = onTopicChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                shape = MaterialTheme.shapes.medium,
            )
            FilledTonalButton(
                onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Dr. CLAW", topic))) } },
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy topic",
                )
            }
        }

        Text(
            text = "Share this topic with Dr. CLAW so it can send you notifications.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Server URL",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://ntfy.sh") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            shape = MaterialTheme.shapes.medium,
        )

        Text(
            text = "Use ntfy.sh (free) or your own self-hosted ntfy server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
