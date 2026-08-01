package com.scaso.drclawapp.ui.approval

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.approval.ApprovalRequest
import com.scaso.drclawapp.data.approval.ApprovalSeverity
import kotlinx.coroutines.delay

/**
 * Full-screen approval dialog for HITL actions.
 * Shows action details, countdown timer, and approve/deny/modify buttons.
 */
@Composable
fun ApprovalDialog(
    request: ApprovalRequest,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onModify: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onApproveAlways: (String) -> Unit = {},
) {
    var showModifyField by remember { mutableStateOf(false) }
    var modificationText by remember { mutableStateOf("") }
    var timeRemainingFraction by remember { mutableFloatStateOf(1f) }

    // Countdown timer - auto-deny on expiry
    LaunchedEffect(request.id) {
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val fraction = 1f - (elapsed.toFloat() / request.timeoutMs.toFloat())
            timeRemainingFraction = fraction.coerceAtLeast(0f)
            if (fraction <= 0f) {
                onDeny(request.id)
                break
            }
            delay(100L)
        }
    }

    val remainingSeconds = ((timeRemainingFraction * request.timeoutMs) / 1000).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val (icon, tint) = when (request.severity) {
                ApprovalSeverity.CRITICAL -> Icons.Default.Warning to MaterialTheme.colorScheme.error
                ApprovalSeverity.HIGH -> Icons.Default.Warning to MaterialTheme.colorScheme.tertiary
                else -> Icons.Default.Warning to MaterialTheme.colorScheme.primary
            }
            Icon(icon, contentDescription = null, tint = tint)
        },
        title = {
            Text(
                text = "Action Approval Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Action name
                Text(
                    text = request.action,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Text(
                    text = request.description,
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Context details
                request.context?.let { ctx ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ctx,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Timer bar
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Auto-deny in ${remainingSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remainingSeconds < 30) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                LinearProgressIndicator(
                    progress = { timeRemainingFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (remainingSeconds < 30) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )

                // Modify field
                if (showModifyField) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = modificationText,
                        onValueChange = { modificationText = it },
                        label = { Text("Modification") },
                        placeholder = { Text("Describe changes...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showModifyField) {
                    Button(
                        onClick = { onModify(request.id, modificationText) },
                        enabled = modificationText.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Apply")
                    }
                } else {
                    // Modify button
                    if (!request.allowAlwaysOption) {
                        TextButton(onClick = { showModifyField = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Modify")
                        }
                    } else {
                        TextButton(onClick = { onApproveAlways(request.id) }) {
                            Text("Always Allow")
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(onClick = { onApprove(request.id) }) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Approve")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onDeny(request.id) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text("Deny")
            }
        },
    )
}
