package com.scaso.drclawapp.ui.components

import android.content.ClipData
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.scaso.drclawapp.data.filedownload.DownloadState
import com.scaso.drclawapp.data.filedownload.FilePathDetector
import com.scaso.drclawapp.data.model.AckState
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.model.SyncState
import com.scaso.drclawapp.data.model.ToolStatus
import com.scaso.drclawapp.ui.theme.AbortedAmber
import com.scaso.drclawapp.ui.theme.InjectedBlue
import kotlinx.serialization.json.JsonElement

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit = {},
    onShare: () -> Unit = {},
    onRegenerate: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReply: () -> Unit = {},
    onFork: () -> Unit = {},
    onRemember: () -> Unit = {},
    onActionClick: (String) -> Unit = {},
    onFileDownload: (String) -> Unit = {},
    onRetrySync: (String) -> Unit = {},
    downloadStates: Map<String, DownloadState> = emptyMap(),
    /**
     * Phase 15: mirrors the global per-session collapse toggle (v1.1.3).
     * Passed down to [ToolUseCard] and [ToolResultCard] so they reset their
     * individual expanded states when the session-level toggle flips.
     */
    collapseByDefault: Boolean = true,
    onMemoryChipClick: (String) -> Unit = {},
) {
    val isUser = message.role == Role.USER
    val isDenial = message.role == Role.DENIAL
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.85f).dp

    val backgroundColor = when {
        isDenial -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        message.isAborted -> MaterialTheme.colorScheme.surfaceVariant
        message.isInjected -> MaterialTheme.colorScheme.surfaceVariant
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor: Color? = when {
        isDenial -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        message.isAborted -> AbortedAmber
        message.isInjected -> InjectedBlue
        message.isTruncated -> MaterialTheme.colorScheme.outline
        else -> null
    }

    val textColor = when {
        isDenial -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    // Action sheet state
    var showActionSheet by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    if (showActionSheet) {
        MessageActionSheet(
            message = message,
            onDismiss = { showActionSheet = false },
            onCopy = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Dr. CLAW", message.content))) }
                onCopy()
            },
            onShare = onShare,
            onRegenerate = onRegenerate,
            onDelete = onDelete,
            onReply = onReply,
            onFork = onFork,
            onRemember = onRemember,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showActionSheet = true },
                    indication = null,
                    interactionSource = remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                ),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .then(
                        if (borderColor != null) {
                            Modifier.border(BorderStroke(1.5.dp, borderColor), shape)
                        } else {
                            Modifier
                        }
                    )
                    .background(backgroundColor, shape)
                    .padding(12.dp),
            ) {
                if (message.isStreaming) {
                    // Streaming: plain text + cursor (partial markdown would break renderer)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        StreamingIndicator(modifier = Modifier.padding(start = 2.dp))
                    }
                } else if (isDenial) {
                    // Denial message: italic plain text
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        ),
                        color = textColor,
                    )
                } else if (!isUser && message.content.isNotEmpty()) {
                    // Finalized assistant message: render markdown
                    MarkdownContent(content = message.content)
                } else {
                    // User message or empty: plain text
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                    )
                }

                // Timestamp below message content
                Text(
                    text = formatMessageTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(if (isUser) Alignment.End else Alignment.Start)
                        .padding(top = 4.dp),
                )

                // Model badge for assistant messages
                if (!isUser && !message.modelName.isNullOrBlank()) {
                    Text(
                        text = message.modelName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 2.dp),
                    )
                }

                // Ack state indicator for user messages
                if (isUser && message.ackState != AckState.NONE) {
                    AckIndicator(
                        ackState = message.ackState,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                    )
                }

                // Offline sync indicator: pending shows clock, failed shows retry chip
                if (isUser && message.syncState == SyncState.PENDING) {
                    Text(
                        text = "Queued offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                    )
                }
                if (isUser && message.syncState == SyncState.FAILED) {
                    AssistChip(
                        onClick = { onRetrySync(message.id) },
                        label = { Text("Retry", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                    )
                }

                // Status chips for aborted/injected/truncated messages
                if (message.isAborted) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Aborted", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = AbortedAmber,
                        ),
                        border = BorderStroke(1.dp, AbortedAmber),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (message.isInjected) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Injected", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = InjectedBlue,
                        ),
                        border = BorderStroke(1.dp, InjectedBlue),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (message.isTruncated) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Truncated", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.outline,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // File download chips for assistant messages with file paths
                if (!isUser && !message.isStreaming) {
                    val filePaths = remember(message.content) {
                        FilePathDetector.findFilePaths(message.content)
                    }
                    filePaths.forEach { path ->
                        val fileName = path.substringAfterLast('\\')
                            .substringAfterLast('/')
                        FileDownloadChip(
                            fileName = fileName,
                            downloadState = downloadStates[path],
                            onClick = { onFileDownload(path) },
                        )
                    }
                }

                // Phase 15: nested tool activity for native-path agentic turns.
                // Always rendered (verbose toggle only controls collapse state, not visibility).
                if (!isUser && message.toolEvents.isNotEmpty()) {
                    message.toolEvents.forEach { event ->
                        val inputText = try {
                            event.input.toString()
                        } catch (_: Exception) {
                            ""
                        }
                        ToolUseCard(
                            toolName = event.toolName,
                            inputJson = inputText,
                            status = event.status,
                            collapseByDefault = collapseByDefault,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // Show result card once the tool is finished (Done or Error)
                        if (event.status == ToolStatus.Done || event.status == ToolStatus.Error) {
                            val outputText = try {
                                (event.output as? JsonElement)?.toString() ?: ""
                            } catch (_: Exception) {
                                ""
                            }
                            ToolResultCard(
                                toolName = event.toolName,
                                outputJson = outputText,
                                error = event.error,
                                collapseByDefault = collapseByDefault,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                if (!isUser && !message.isStreaming && message.injectedMemories.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.injectedMemories.forEach { ref ->
                            AssistChip(
                                onClick = { onMemoryChipClick(ref.id) },
                                label = {
                                    Text(
                                        text = ref.scope.takeIf { it.isNotBlank() }
                                            ?: ref.snippet.take(30),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.secondary,
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Inline action buttons below the bubble
        if (message.suggestedActions.isNotEmpty()) {
            InlineActionButtons(
                actions = message.suggestedActions,
                onActionClick = onActionClick,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Displays the ack/delivery state indicator below the timestamp.
 * - SENT: single checkmark
 * - PROCESSING: animated eye icon (pulsing)
 * - DONE: double checkmark
 */
@Composable
private fun AckIndicator(
    ackState: AckState,
    modifier: Modifier = Modifier,
) {
    when (ackState) {
        AckState.NONE -> { /* No indicator */ }
        AckState.PENDING -> {
            Text(
                text = "\uD83D\uDD52", // clock icon
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f),
                modifier = modifier,
            )
        }
        AckState.SENT -> {
            Text(
                text = "\u2713", // single checkmark
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = modifier,
            )
        }
        AckState.PROCESSING -> {
            // Animated pulsing eye, similar to StreamingIndicator style
            val infiniteTransition = rememberInfiniteTransition(label = "ackProcessing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "processingAlpha",
            )
            Text(
                text = "\uD83D\uDC41", // eye emoji
                style = MaterialTheme.typography.labelSmall,
                modifier = modifier.alpha(alpha),
            )
        }
        AckState.DONE -> {
            Text(
                text = "\u2713\u2713", // double checkmark
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = modifier,
            )
        }
        AckState.ERROR -> {
            Text(
                text = "\u26A0", // warning triangle
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier,
            )
        }
    }
}
