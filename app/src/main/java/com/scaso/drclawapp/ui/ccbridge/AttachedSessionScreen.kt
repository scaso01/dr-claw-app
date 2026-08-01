package com.scaso.drclawapp.ui.ccbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.scaso.drclawapp.ui.components.CanvasCard
import com.scaso.drclawapp.ui.components.formatMessageTimestamp
import com.scaso.drclawapp.data.filedownload.FileSnapshot
import com.scaso.drclawapp.ui.components.FileSnapshotsSheet
import com.scaso.drclawapp.ui.components.StreamingIndicator
import com.scaso.drclawapp.ui.theme.ToolCardSurface
import com.scaso.drclawapp.ui.voice.SpeechRecognizerHelper
import com.scaso.drclawapp.ui.voice.VoiceRecordButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachedSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: AttachedSessionViewModel = hiltViewModel(),
) {
    AttachedSessionContent(
        viewModel = viewModel,
        onNavigateBack = onBack,
    )
}

/**
 * Extracted content composable that accepts a ViewModel parameter directly.
 * Reusable in SplitSessionScreen where two independent ViewModels are
 * created via separate NavHost backstack entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachedSessionContent(
    viewModel: AttachedSessionViewModel,
    onNavigateBack: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val isAttached by viewModel.isAttached.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val rawEvents by viewModel.rawEvents.collectAsStateWithLifecycle()
    val permissionRequest by viewModel.permissionRequest.collectAsStateWithLifecycle()
    val totalCostUsd by viewModel.totalCostUsd.collectAsStateWithLifecycle()
    val totalTokens by viewModel.totalTokens.collectAsStateWithLifecycle()
    val isLoadingHistory by viewModel.isLoadingHistory.collectAsStateWithLifecycle()
    val historicalMessageCount by viewModel.historicalMessageCount.collectAsStateWithLifecycle()
    val activeInTerminal by viewModel.activeInTerminal.collectAsStateWithLifecycle()
    val toolOutputsCollapsed by viewModel.toolOutputsCollapsed.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val snapshotPath by viewModel.snapshotPath.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var inputText by rememberSaveable { mutableStateOf("") }
    var showRawEvents by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // ── Voice input ─────────────────────────────────────────────
    val context = LocalContext.current
    val speechHelper = remember { SpeechRecognizerHelper(context) }
    val isRecording by speechHelper.isListening.collectAsStateWithLifecycle()
    val recognizedText by speechHelper.recognizedText.collectAsStateWithLifecycle()
    val speechError by speechHelper.error.collectAsStateWithLifecycle()

    // When final transcription arrives, send it as a message
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotBlank() && !isRecording) {
            viewModel.sendMessage(recognizedText)
        }
    }

    // Show speech errors in snackbar
    LaunchedEffect(speechError) {
        speechError?.let { snackbarHostState.showSnackbar(it) }
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.destroy() }
    }

    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems == 0 || (lastVisibleItem != null && lastVisibleItem.index >= totalItems - 1)
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = viewModel.sessionId.take(8),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusChip(status = status)
                        }
                        if (totalTokens > 0 || totalCostUsd > 0.0) {
                            Text(
                                text = "$${String.format("%.2f", totalCostUsd)} | ${formatTokenCount(totalTokens)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleToolOutputsCollapsed() },
                    ) {
                        Icon(
                            imageVector = if (toolOutputsCollapsed) Icons.Default.UnfoldMore
                            else Icons.Default.UnfoldLess,
                            contentDescription = if (toolOutputsCollapsed) "Expand all"
                            else "Collapse all",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { showRawEvents = !showRawEvents },
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataObject,
                            contentDescription = "Raw Events",
                            tint = if (showRawEvents) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.detach()
                            onNavigateBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Detach",
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
        // ── Permission dialog overlay ─────────────────────────────
        permissionRequest?.let { request ->
            CcPermissionDialog(
                toolName = request.toolName,
                input = request.input,
                onAllow = { viewModel.respondPermission(request.requestId, allow = true) },
                onDeny = { viewModel.respondPermission(request.requestId, allow = false) },
            )
        }

        if (showRawEvents) {
            // ── Raw event view ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                RawEventView(
                    events = rawEvents,
                    onClose = { showRawEvents = false },
                )
            }
        } else {
            // ── Chat view ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                // ── Active-in-terminal banner ─────────────────────
                if (activeInTerminal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "This session is active in Claude Code",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // ── Message list ──────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                    ) {
                        if (!isAttached && status != SessionStatus.SPAWNING) {
                            item(key = "not_attached") {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Not attached to session",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // History loading indicator
                        if (isLoadingHistory) {
                            item(key = "history_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Text(
                                            text = "Loading history...",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        // All messages sorted by timestamp
                        val lastHistIdx = messages.indexOfLast { it.id.startsWith("hist-") || it.id.startsWith("sync-") }

                        val onSnapshot: (String) -> Unit = { viewModel.loadSnapshots(it) }

                        if (lastHistIdx >= 0) {
                            val historySlice = messages.subList(0, lastHistIdx + 1)
                            items(historySlice, key = { it.id }) { message ->
                                MessageItem(message, toolOutputsCollapsed, onSnapshot)
                            }

                            // History divider
                            if (!isLoadingHistory) {
                                item(key = "history_divider") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                        Text(
                                            text = " History (${lastHistIdx + 1}) ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                }
                            }

                            // Live messages (after history)
                            val liveMessages = messages.subList(lastHistIdx + 1, messages.size)
                            items(liveMessages, key = { it.id }) { message ->
                                MessageItem(message, toolOutputsCollapsed, onSnapshot)
                            }
                        } else {
                            items(messages, key = { it.id }) { message ->
                                MessageItem(message, toolOutputsCollapsed, onSnapshot)
                            }
                        }
                    }

                    // ── Scroll-to-bottom FAB ────────────────────────
                    if (!isAtBottom) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(
                                defaultElevation = 4.dp,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                            )
                        }
                    }
                }

                // ── Input bar ─────────────────────────────────────
                InputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    onInterrupt = viewModel::interrupt,
                    isStreaming = isStreaming,
                    enabled = isAttached && status != SessionStatus.CLOSED,
                    isRecording = isRecording,
                    onStartRecording = { speechHelper.startListening() },
                    onStopRecording = { speechHelper.stopListening() },
                )
            }
        }
    }

    // File snapshots sheet
    snapshotPath?.let { path ->
        FileSnapshotsSheet(
            filePath = path,
            snapshots = snapshots,
            onRestore = { snapshot -> viewModel.restoreSnapshot(snapshot) },
            onDismiss = { viewModel.dismissSnapshots() },
        )
    }
}

// ── Status chip ──────────────────────────────────────────────────────

@Composable
private fun StatusChip(status: SessionStatus) {
    val (label, color) = when (status) {
        SessionStatus.IDLE -> "Idle" to MaterialTheme.colorScheme.onSurfaceVariant
        SessionStatus.STREAMING -> "Streaming" to Color(0xFF4CAF50)
        SessionStatus.TOOL_EXECUTING -> "Tool" to Color(0xFF2196F3)
        SessionStatus.SPAWNING -> "Connecting" to Color(0xFFFFC107)
        SessionStatus.WAITING_PERMISSION -> "Permission" to Color(0xFFFF9800)
        SessionStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
        SessionStatus.CLOSED -> "Closed" to MaterialTheme.colorScheme.outline
    }

    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color,
        ),
    )
}

// ── Message dispatch ────────────────────────────────────────────────

@Composable
private fun MessageItem(
    message: SessionMessage,
    collapseByDefault: Boolean,
    onViewSnapshots: ((String) -> Unit)? = null,
) {
    when (message) {
        is SessionMessage.UserMessage -> UserBubble(message)
        is SessionMessage.AssistantMessage -> AssistantBubble(message)
        is SessionMessage.ToolUseMessage -> ToolUseCard(message, collapseByDefault)
        is SessionMessage.ToolResultMessage -> ToolResultCard(
            message, collapseByDefault, onViewSnapshots,
        )
        is SessionMessage.ThinkingMessage -> ThinkingBubble(message, collapseByDefault)
        is SessionMessage.ErrorMessage -> ErrorBubble(message)
        is SessionMessage.CanvasMessage -> CanvasCard(
            html = message.html,
            url = message.url,
        )
    }
}

// ── User message bubble ──────────────────────────────────────────────

@Composable
private fun UserBubble(message: SessionMessage.UserMessage) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.85f).dp
    val sourceLabel = sourceLabel(message.source)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        val timestampStr = formatMessageTimestamp(message.timestamp)
        val combinedLabel = if (sourceLabel != null) "$sourceLabel · $timestampStr" else timestampStr

        Text(
            text = combinedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

private fun sourceLabel(source: String?): String? = when (source) {
    "sdk-cli" -> "via Phone"
    "cli" -> "via Terminal"
    else -> null
}

// ── Assistant message bubble ─────────────────────────────────────────

@Composable
private fun AssistantBubble(message: SessionMessage.AssistantMessage) {
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.85f).dp
    val sourceLabel = sourceLabel(message.source)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        val timestampStr = formatMessageTimestamp(message.timestamp)
        val combinedLabel = if (sourceLabel != null) "$sourceLabel · $timestampStr" else timestampStr

        Text(
            text = combinedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                    )
                    .padding(12.dp),
            ) {
                Column {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (message.isStreaming) {
                        StreamingIndicator(modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

// ── Tool use card ────────────────────────────────────────────────────

@Composable
private fun ToolUseCard(message: SessionMessage.ToolUseMessage, collapseByDefault: Boolean) {
    var expanded by remember(collapseByDefault) { mutableStateOf(!collapseByDefault) }
    val inputSummary = message.input?.toString()?.take(120) ?: ""
    val fullInput = message.input?.toString() ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = ToolCardSurface.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tool icon placeholder
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = Color(0xFF2196F3).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\uD83D\uDD27", // Wrench emoji
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF90CAF9),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!expanded && inputSummary.isNotEmpty()) {
                    Text(
                        text = inputSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (expanded && fullInput.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = fullInput,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 30,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess
                else Icons.Default.ExpandMore,
                contentDescription = "Toggle input",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Tool result card (collapsible) ──────────────────────────────────

private val FILE_WRITE_TOOLS = setOf(
    "write_file", "edit", "write", "patch", "Edit", "Write",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolResultCard(
    message: SessionMessage.ToolResultMessage,
    collapseByDefault: Boolean,
    onViewSnapshots: ((String) -> Unit)? = null,
) {
    var expanded by remember(collapseByDefault) { mutableStateOf(!collapseByDefault) }
    val isFileWrite = message.toolName in FILE_WRITE_TOOLS

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = if (isFileWrite && onViewSnapshots != null) {
                    { onViewSnapshots(message.toolName) }
                } else {
                    null
                },
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\u2713",
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = "Toggle output",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded && message.output.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message.output,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 20,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Thinking bubble (collapsible) ──────────────────────────────────

@Composable
private fun ThinkingBubble(message: SessionMessage.ThinkingMessage, collapseByDefault: Boolean) {
    var expanded by remember(collapseByDefault) { mutableStateOf(!collapseByDefault) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { expanded = !expanded },
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic,
                )
                if (message.isStreaming) {
                    Spacer(Modifier.width(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

// ── Error bubble ─────────────────────────────────────────────────────

@Composable
private fun ErrorBubble(message: SessionMessage.ErrorMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = message.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Input bar ────────────────────────────────────────────────────────

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    isStreaming: Boolean,
    enabled: Boolean,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = when {
                        isRecording -> "Listening..."
                        enabled -> "Send a message..."
                        else -> "Session unavailable"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            enabled = enabled && !isRecording,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.width(4.dp))

        if (isStreaming) {
            // Show interrupt button while streaming
            IconButton(
                onClick = onInterrupt,
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Interrupt",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            // Show mic button when text is empty or recording; send button otherwise
            if (text.isBlank() || isRecording) {
                VoiceRecordButton(
                    isRecording = isRecording,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                )
            } else {
                // Show send button
                IconButton(
                    onClick = onSend,
                    enabled = enabled && text.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (enabled && text.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> "$tokens"
}
