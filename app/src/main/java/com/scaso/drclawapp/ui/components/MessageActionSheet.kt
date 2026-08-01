package com.scaso.drclawapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.MessageType
import com.scaso.drclawapp.data.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionSheet(
    message: Message,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onFork: () -> Unit = {},
    onRemember: () -> Unit = {},
    onViewSnapshots: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Message Actions",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        ListItem(
            headlineContent = { Text("Copy") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                )
            },
            modifier = Modifier.clickable {
                onCopy()
                onDismiss()
            },
        )

        ListItem(
            headlineContent = { Text("Share") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share message",
                )
            },
            modifier = Modifier.clickable {
                onShare()
                onDismiss()
            },
        )

        ListItem(
            headlineContent = { Text("Reply") },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply to message",
                )
            },
            modifier = Modifier.clickable {
                onReply()
                onDismiss()
            },
        )

        ListItem(
            headlineContent = { Text("Fork from here") },
            leadingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = "Fork conversation",
                )
            },
            modifier = Modifier.clickable {
                onFork()
                onDismiss()
            },
        )

        ListItem(
            headlineContent = { Text("Remember") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Remember this message",
                )
            },
            modifier = Modifier.clickable {
                onRemember()
                onDismiss()
            },
        )

        // Show "View Snapshots" for file write tool results
        if (onViewSnapshots != null && message.messageType == MessageType.TOOL_RESULT) {
            ListItem(
                headlineContent = { Text("View Snapshots") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "View file snapshots",
                    )
                },
                modifier = Modifier.clickable {
                    onViewSnapshots()
                    onDismiss()
                },
            )
        }

        // Only show Regenerate for assistant messages
        if (message.role == Role.ASSISTANT) {
            ListItem(
                headlineContent = { Text("Regenerate") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Regenerate response",
                    )
                },
                modifier = Modifier.clickable {
                    onRegenerate()
                    onDismiss()
                },
            )
        }

        ListItem(
            headlineContent = {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete message",
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier.clickable {
                onDelete()
                onDismiss()
            },
        )

        // Bottom spacing for gesture navigation
        Spacer(modifier = Modifier.height(24.dp))
    }
}
