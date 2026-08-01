package com.scaso.drclawapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.websocket.GatewayEvent

/**
 * Scope for a HITL permission decision.
 * [Once] — approve for this single invocation.
 * [Session] — approve for the rest of the current chat session.
 */
enum class PermissionScope {
    Once,
    Session,
}

/**
 * Generalized HITL permission dialog for native-chat tool requests.
 *
 * Shows:
 *  - Title: "Allow <tool>?"
 *  - A static "Confirm" tier badge (PHASE 18: add tier field to NativePermissionRequest)
 *  - Description / input preview body (scrollable)
 *  - "Allow for this session" checkbox — only when [request.allowSessionCache] is true
 *  - Deny (OutlinedButton) / Allow (TextButton) actions
 *
 * [onDismiss] acts as an implicit "Deny, once" (e.g., back-press or scrim tap).
 */
@Composable
fun PermissionDialog(
    request: GatewayEvent.NativePermissionRequest,
    onDecision: (approve: Boolean, scope: PermissionScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var sessionScopeChecked by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Allow “${request.tool}”?",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (request.allowSessionCache) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // PHASE 18: replace static "Confirm" with actual tier from NativePermissionRequest
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Confirm", style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = request.description
                        .take(500)
                        .let { if (request.description.length > 500) "$it…" else it },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                )
                if (request.allowSessionCache) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = sessionScopeChecked,
                            onCheckedChange = { sessionScopeChecked = it },
                        )
                        Text(
                            text = "Allow for this session",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onDecision(false, PermissionScope.Once)
                },
            ) {
                Text("Deny")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val scope = if (sessionScopeChecked) PermissionScope.Session else PermissionScope.Once
                    onDecision(true, scope)
                },
            ) {
                Text("Allow")
            }
        },
    )
}
