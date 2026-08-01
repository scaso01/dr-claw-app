package com.scaso.drclawapp.ui.ccbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.ccbridge.Machine
import com.scaso.drclawapp.data.model.SessionTemplate

/**
 * The type of session to create, controlling which fields are shown.
 */
enum class CreateDialogType {
    /** CC daemon session (v2) — shows backend + permission mode, no role. */
    DAEMON,
    /** Ironjaw session — shows role, no backend or permission mode. */
    IRONJAW,
}

/**
 * Unified dialog for creating a new CC session (daemon or Ironjaw).
 *
 * DAEMON: project name, working directory, backend, model, permission mode.
 * IRONJAW: message (required) + working directory only — matches what the handler actually uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionDialog(
    onDismiss: () -> Unit,
    dialogType: CreateDialogType = CreateDialogType.DAEMON,
    templates: List<SessionTemplate> = emptyList(),
    onSaveTemplate: (SessionTemplate) -> Unit = {},
    onDeleteTemplate: (String) -> Unit = {},
    onCreate: (cwd: String, project: String?, backend: String?, model: String?, permissionMode: String?, target: Machine) -> Unit = { _, _, _, _, _, _ -> },
    onCreateIronjaw: (message: String, cwd: String) -> Unit = { _, _ -> },
) {
    val localCwd = "C:\\Users\\deploy"
    val phoneCwd = "/root"
    var project by remember { mutableStateOf("") }
    var cwd by remember { mutableStateOf(localCwd) }
    var backend by remember { mutableStateOf("cloud") }
    var model by remember { mutableStateOf("") }
    var permissionMode by remember { mutableStateOf("bypassPermissions") }
    var message by remember { mutableStateOf("") }
    // Which daemon to run on (workstation desktop vs phone-local). Swaps the default cwd.
    var target by remember { mutableStateOf(Machine.LOCAL) }

    // Template UI state (DAEMON only)
    var templateDropdownExpanded by remember { mutableStateOf(false) }
    var showSaveTemplateField by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    val title = when (dialogType) {
        CreateDialogType.DAEMON -> "Create Session"
        CreateDialogType.IRONJAW -> "Create Ironjaw Session"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (dialogType == CreateDialogType.IRONJAW) {
                    // ── Ironjaw: message + cwd only ──
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Message *") },
                        placeholder = { Text("What should Claude do?") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        isError = message.isBlank(),
                        supportingText = if (message.isBlank()) {
                            { Text("Required", color = MaterialTheme.colorScheme.error) }
                        } else {
                            null
                        },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cwd,
                        onValueChange = { cwd = it },
                        label = { Text("Working directory") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // ── Daemon: full form ──

                    // Run on: workstation desktop vs phone-local daemon. Flipping swaps the
                    // default working directory (Windows vs Linux) unless the user edited it.
                    Text(
                        text = "Run on",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = target == Machine.LOCAL,
                            onClick = {
                                target = Machine.LOCAL
                                if (cwd.isBlank() || cwd == phoneCwd) cwd = localCwd
                            },
                            label = { Text("workstation") },
                        )
                        FilterChip(
                            selected = target == Machine.PHONE,
                            onClick = {
                                target = Machine.PHONE
                                if (cwd.isBlank() || cwd == localCwd) cwd = phoneCwd
                            },
                            label = { Text("Phone") },
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Load Template dropdown
                    if (templates.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = templateDropdownExpanded,
                            onExpandedChange = { templateDropdownExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Load Template") },
                                placeholder = { Text("Select a template...") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownExpanded)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(
                                expanded = templateDropdownExpanded,
                                onDismissRequest = { templateDropdownExpanded = false },
                            ) {
                                templates.forEach { tmpl ->
                                    DropdownMenuItem(
                                        text = { Text(tmpl.name) },
                                        onClick = {
                                            model = tmpl.model ?: ""
                                            project = tmpl.project ?: ""
                                            cwd = tmpl.cwd
                                            backend = tmpl.backend
                                            templateDropdownExpanded = false
                                        },
                                        trailingIcon = {
                                            TextButton(
                                                onClick = {
                                                    onDeleteTemplate(tmpl.name)
                                                    templateDropdownExpanded = false
                                                },
                                            ) {
                                                Text(
                                                    "Delete",
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = project,
                        onValueChange = { project = it },
                        label = { Text("Project name") },
                        placeholder = { Text("e.g. ExamplePipeline") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cwd,
                        onValueChange = { cwd = it },
                        label = { Text("Working directory *") },
                        placeholder = { Text("C:\\Users\\deploy") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = cwd.isBlank(),
                        supportingText = if (cwd.isBlank()) {
                            { Text("Required", color = MaterialTheme.colorScheme.error) }
                        } else {
                            null
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Backend",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = backend == "cloud",
                            onClick = { backend = "cloud" },
                            label = { Text("Cloud") },
                        )
                        FilterChip(
                            selected = backend == "local",
                            onClick = { backend = "local" },
                            label = { Text("Local") },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        placeholder = { Text("e.g. sonnet, opus") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Permission mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = permissionMode == "bypassPermissions",
                            onClick = { permissionMode = "bypassPermissions" },
                            label = { Text("Auto") },
                        )
                        FilterChip(
                            selected = permissionMode == "interactive",
                            onClick = { permissionMode = "interactive" },
                            label = { Text("Interactive") },
                        )
                    }

                    // Save as Template section
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    if (showSaveTemplateField) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = templateName,
                                onValueChange = { templateName = it },
                                label = { Text("Template name") },
                                placeholder = { Text("e.g. My Config") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    if (templateName.isNotBlank()) {
                                        onSaveTemplate(
                                            SessionTemplate(
                                                name = templateName.trim(),
                                                model = model.trim().ifBlank { null },
                                                project = project.trim().ifBlank { null },
                                                cwd = cwd.trim().ifBlank { "." },
                                                backend = backend,
                                            ),
                                        )
                                        showSaveTemplateField = false
                                        templateName = ""
                                    }
                                },
                                enabled = templateName.isNotBlank(),
                            ) {
                                Text("Save")
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showSaveTemplateField = true },
                        ) {
                            Text("Save as Template")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (dialogType) {
                        CreateDialogType.DAEMON -> onCreate(
                            cwd.trim(),
                            project.trim().ifBlank { null },
                            backend,
                            model.trim().ifBlank { null },
                            permissionMode,
                            target,
                        )
                        CreateDialogType.IRONJAW -> onCreateIronjaw(
                            message.trim(),
                            cwd.trim().ifBlank { "C:\\Users\\deploy" },
                        )
                    }
                },
                enabled = when (dialogType) {
                    CreateDialogType.DAEMON -> cwd.isNotBlank()
                    CreateDialogType.IRONJAW -> message.isNotBlank()
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
