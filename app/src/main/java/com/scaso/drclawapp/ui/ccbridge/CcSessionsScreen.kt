package com.scaso.drclawapp.ui.ccbridge

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.scaso.drclawapp.data.ccbridge.CcActiveSession
import com.scaso.drclawapp.data.ccbridge.CcSession
import com.scaso.drclawapp.data.ccbridge.ClaudeSession
import com.scaso.drclawapp.data.ccbridge.Machine
import com.scaso.drclawapp.data.ccbridge.MailMessage
import com.scaso.drclawapp.data.ccbridge.SpawnMode
import com.scaso.drclawapp.ui.components.SessionSearchBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CcSessionsScreen(
    onBack: () -> Unit,
    onAttachSession: (sessionId: String, backend: String) -> Unit = { _, _ -> },
    onSplitSession: (
        leftId: String, leftBackend: String,
        rightId: String, rightBackend: String,
    ) -> Unit = { _, _, _, _ -> },
    viewModel: CcSessionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val showIronjawCreateDialog by viewModel.showIronjawCreateDialog.collectAsStateWithLifecycle()
    val resumedSessionId by viewModel.resumedSessionId.collectAsStateWithLifecycle()
    val resumeBackend by viewModel.resumeBackend.collectAsStateWithLifecycle()
    val resumeError by viewModel.resumeError.collectAsStateWithLifecycle()
    val sessionTemplates by viewModel.sessionTemplates.collectAsStateWithLifecycle()
    val isCreatingSession by viewModel.isCreatingSession.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Split-view selection state: holds the first selected session (id + backend)
    var splitSelection by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Auto-navigate after successful resume (use dynamic backend for Ironjaw fallback)
    LaunchedEffect(resumedSessionId) {
        resumedSessionId?.let { id ->
            val backend = resumeBackend
            viewModel.consumeResumedSessionId()
            onAttachSession(id, backend)
        }
    }

    // Show resume errors as snackbar
    LaunchedEffect(resumeError) {
        resumeError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeResumeError()
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = viewModel::dismissCreateDialog,
            templates = sessionTemplates,
            onSaveTemplate = viewModel::saveTemplate,
            onDeleteTemplate = viewModel::deleteTemplate,
            onCreate = viewModel::createSession,
        )
    }

    if (showIronjawCreateDialog) {
        CreateSessionDialog(
            dialogType = CreateDialogType.IRONJAW,
            onDismiss = viewModel::dismissIronjawCreateDialog,
            templates = sessionTemplates,
            onSaveTemplate = viewModel::saveTemplate,
            onDeleteTemplate = viewModel::deleteTemplate,
            onCreateIronjaw = { message, cwd ->
                viewModel.createIronjawSession(message, cwd)
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("CC Sessions")
                        if (uiState.bridgeConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50),
                            )
                        }
                    }
                },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.bridgeConnected) {
                        viewModel.showCreateDialog()
                    } else {
                        viewModel.showIronjawCreateDialog()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create session",
                )
            }
        },
    ) { padding ->
        var selectedTab by remember { mutableIntStateOf(0) }
        val mailMessages = uiState.mailMessages
        val unclaimedCount = mailMessages.count { it.claimedBy == null }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Sessions") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Mailbox")
                            if (unclaimedCount > 0) {
                                Badge { Text("$unclaimedCount") }
                            }
                        }
                    },
                )
            }

            when (selectedTab) {
                0 -> SessionsTab(
                    uiState = uiState,
                    searchQuery = searchQuery,
                    isCreatingSession = isCreatingSession,
                    splitSelection = splitSelection,
                    viewModel = viewModel,
                    onAttachSession = onAttachSession,
                    onSplitSession = onSplitSession,
                    onSplitSelectionChange = { splitSelection = it },
                )
                1 -> MailboxTab(
                    messages = mailMessages,
                    onClaim = viewModel::claimMailMessage,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionsTab(
    uiState: CcSessionsUiState,
    searchQuery: String,
    isCreatingSession: Boolean,
    splitSelection: Pair<String, String>?,
    viewModel: CcSessionsViewModel,
    onAttachSession: (String, String) -> Unit,
    onSplitSession: (String, String, String, String) -> Unit,
    onSplitSelectionChange: (Pair<String, String>?) -> Unit,
) {
    val ironjawSort by viewModel.ironjawSort.collectAsStateWithLifecycle()
    val ironjawStatusFilter by viewModel.ironjawStatusFilter.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
            val hasAnySessions = uiState.sessions.isNotEmpty() ||
                uiState.activeSessions.isNotEmpty() ||
                uiState.ironjawSessions.isNotEmpty()

            when {
                uiState.error != null && !hasAnySessions -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pull to refresh or check gateway connection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                !hasAnySessions && !uiState.isLoading -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SessionSearchBar(
                            query = searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                            placeholder = "Search CC sessions...",
                        )
                        if (isCreatingSession) {
                            CreatingSessionBanner(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching sessions"
                                else "No CC sessions found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        item(key = "search_bar") {
                            SessionSearchBar(
                                query = searchQuery,
                                onQueryChange = viewModel::setSearchQuery,
                                placeholder = "Search CC sessions...",
                            )
                        }

                        // Creating session indicator
                        if (isCreatingSession) {
                            item(key = "creating_session_banner") {
                                CreatingSessionBanner()
                            }
                        }

                        // Active Sessions section (live daemon sessions — workstation + Phone — pinned to top)
                        if (uiState.activeSessions.isNotEmpty()) {
                            item(key = "active_header") {
                                SectionHeader(title = "Active Sessions")
                            }
                            itemsIndexed(
                                uiState.activeSessions,
                                key = { index, it -> "active:$index:${it.source}:${it.id}" },
                            ) { _, session ->
                                ActiveSessionCard(
                                    session = session,
                                    onAttach = {
                                        onAttachSession(session.id, "daemon")
                                    },
                                    onDestroy = { viewModel.destroySession(session.id) },
                                )
                            }
                        }

                        // Ironjaw Sessions section (always show header with create button)
                        item(key = "ironjaw_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SectionHeader(title = "Ironjaw Sessions")
                                IconButton(
                                    onClick = viewModel::showIronjawCreateDialog,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Ironjaw session",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        // Sort + status filter controls (only when there's something to sort/unfilter)
                        if (uiState.ironjawSessions.isNotEmpty() ||
                            ironjawStatusFilter != IronjawStatusFilter.ALL
                        ) {
                            item(key = "ironjaw_controls") {
                                IronjawControls(
                                    sort = ironjawSort,
                                    statusFilter = ironjawStatusFilter,
                                    onSortChange = viewModel::setIronjawSort,
                                    onFilterChange = viewModel::setIronjawStatusFilter,
                                )
                            }
                        }
                        // Split selection banner
                        if (splitSelection != null) {
                            item(key = "split_banner") {
                                SplitSelectionBanner(
                                    firstSessionId = splitSelection!!.first,
                                    onCancel = { onSplitSelectionChange(null) },
                                )
                            }
                        }

                        if (uiState.ironjawSessions.isNotEmpty()) {
                            // index in the key: ccBridgeId can be blank for >1 session → would collide
                            itemsIndexed(
                                uiState.ironjawSessions,
                                key = { index, it -> "ironjaw:$index:${it.ccBridgeId}" },
                            ) { _, session ->
                                IronjawSessionCard(
                                    session = session,
                                    isSelectedForSplit = splitSelection?.first == session.ccBridgeId,
                                    onAttach = {
                                        val sel = splitSelection
                                        if (sel != null && sel.first != session.ccBridgeId) {
                                            // Second selection -- open split view
                                            onSplitSession(
                                                sel.first, sel.second,
                                                session.ccBridgeId, "ironjaw",
                                            )
                                            onSplitSelectionChange(null)
                                        } else {
                                            onAttachSession(session.ccBridgeId, "ironjaw")
                                        }
                                    },
                                    onDestroy = { viewModel.destroyIronjawSession(session.ccBridgeId) },
                                    onLongPress = {
                                        onSplitSelectionChange(Pair(session.ccBridgeId, "ironjaw"))
                                    },
                                )
                            }
                        }

                        // Historical Sessions section
                        if (uiState.sessions.isNotEmpty()) {
                            item(key = "historical_header") {
                                SectionHeader(
                                    title = if (uiState.activeSessions.isNotEmpty())
                                        "Historical Sessions"
                                    else
                                        "Sessions",
                                )
                            }
                            itemsIndexed(
                                uiState.sessions,
                                key = { index, it -> "hist:$index:${it.machine}:${it.sessionId}" },
                            ) { _, session ->
                                HistoricalSessionCard(
                                    session = session,
                                    bridgeConnected = uiState.bridgeConnected,
                                    onResume = { viewModel.resumeSession(it) },
                                    onLongPress = {
                                        onSplitSelectionChange(Pair("hist:${session.sessionId}", "gateway"))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

// ── Mailbox Tab ───────────────────────────────────────────────────────

@Composable
private fun MailboxTab(
    messages: List<MailMessage>,
    onClaim: (String) -> Unit,
) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No mailbox messages",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MailMessageCard(
                    message = msg,
                    onClaim = { onClaim(msg.id) },
                )
            }
        }
    }
}

@Composable
private fun MailMessageCard(
    message: MailMessage,
    onClaim: () -> Unit,
) {
    val priorityColor = when (message.priority.lowercase()) {
        "urgent" -> Color(0xFFF44336)
        "high" -> Color(0xFFFF9800)
        "low" -> Color(0xFF9E9E9E)
        else -> Color(0xFF2196F3) // normal
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Priority color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Subject + From
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.subject.ifBlank { "(no subject)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "From: ${message.from.ifBlank { "unknown" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body.take(100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Claim button or claimed indicator
            if (message.claimedBy != null) {
                Text(
                    text = "Claimed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedButton(onClick = onClaim) {
                    Text("Claim")
                }
            }
        }
    }
}

// ── Section Header ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

// ── Ironjaw Sort / Filter Controls ────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IronjawControls(
    sort: IronjawSort,
    statusFilter: IronjawStatusFilter,
    onSortChange: (IronjawSort) -> Unit,
    onFilterChange: (IronjawStatusFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Sort row — horizontally scrollable so chips never overflow narrow screens
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sort",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IronjawSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { onSortChange(option) },
                    label = {
                        Text(option.label, style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
        // Status filter row
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Show",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IronjawStatusFilter.entries.forEach { option ->
                FilterChip(
                    selected = statusFilter == option,
                    onClick = { onFilterChange(option) },
                    label = {
                        Text(option.label, style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
    }
}

// ── Active Session Card (v2 daemon) ───────────────────────────────────

@Composable
private fun ActiveSessionCard(
    session: CcActiveSession,
    onAttach: () -> Unit,
    onDestroy: () -> Unit,
) {
    val projectName = session.config?.project
        ?: session.config?.cwd?.let { cwd ->
            // Extract last directory from cwd path
            cwd.trimEnd('/', '\\')
                .split('/', '\\')
                .lastOrNull()
                ?.ifBlank { null }
        }
        ?: "Unknown"

    val backendLabel = when (session.config?.backend?.lowercase()) {
        "local" -> "Local"
        else -> "Cloud"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Project name + backend badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BackendBadge(backend = backendLabel)
                    Spacer(modifier = Modifier.width(8.dp))
                    MachineBadge(machine = session.source)
                }

                // Action buttons
                Row {
                    IconButton(
                        onClick = onAttach,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Attach",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = onDestroy,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Destroy",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Row 2: Model + status + client count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    session.config?.model?.let { model ->
                        Text(
                            text = model,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    StatusIndicator(status = session.status)
                }

                if (session.clients > 0) {
                    Text(
                        text = "${session.clients} client${if (session.clients != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Row 3: Session ID + relative time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.id.take(8),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatRelativeTime(session.lastActivityAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Historical Session Card (v1) ──────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoricalSessionCard(
    session: CcSession,
    bridgeConnected: Boolean = false,
    onResume: (CcSession) -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val machine = Machine.fromString(session.machine)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onResume(session) },
                onLongClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Dr. CLAW", session.sessionId))) }
                    onLongPress()
                },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // Topic as primary text (if available)
            if (!session.topic.isNullOrBlank()) {
                Text(
                    text = session.topic,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Fork indicator
            if (session.parentId != null) {
                Text(
                    text = "Fork of ${session.parentTitle ?: session.parentId.take(8)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            session.contextPct?.let { pct ->
                LinearProgressIndicator(
                    progress = { (pct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = when {
                        pct > 90f -> MaterialTheme.colorScheme.error
                        pct > 75f -> Color(0xFFFF9800)  // orange
                        pct > 50f -> Color(0xFFFFC107)  // amber
                        else -> MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Text(
                    text = "Ctx: ${pct.toInt()}%" +
                        if (session.compactionCount > 0) " (${session.compactionCount}x compacted)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MachineBadge(machine = machine)

                    // Worktree chip
                    if (SpawnMode.fromString(session.spawnMode) == SpawnMode.WORKTREE) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("WT", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Worktree",
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                labelColor = MaterialTheme.colorScheme.tertiary,
                                leadingIconContentColor = MaterialTheme.colorScheme.tertiary,
                            ),
                        )
                    }

                    // Fork count badge
                    if (session.forkCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Badge { Text("${session.forkCount}") }
                    }

                    // Backend badge (if present on historical session)
                    session.backend?.let { backend ->
                        Spacer(modifier = Modifier.width(4.dp))
                        BackendBadge(
                            backend = when (backend.lowercase()) {
                                "local" -> "Local"
                                else -> "Cloud"
                            },
                        )
                    }

                    val projectLabel = session.project?.ifEmpty { null }?.takeIf { it != "null" }
                    val hasTopic = !session.topic.isNullOrBlank()
                    if (projectLabel != null || !hasTopic) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = projectLabel ?: "~",
                            style = if (!hasTopic) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (!hasTopic) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

            }

            if (session.lastMessage != null) {
                Text(
                    text = session.lastMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatRelativeTime(session.lastActivity),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onResume(session) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = session.sessionId.take(8),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Shared Components ─────────────────────────────────────────────────

@Composable
private fun BackendBadge(backend: String) {
    val (color, label) = when (backend) {
        "Local" -> Pair(Color(0xFFFF9800), "Local")
        else -> Pair(Color(0xFF2196F3), "Cloud")
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

@Composable
private fun MachineBadge(machine: Machine) {
    val (color, label) = when (machine) {
        Machine.REMOTE -> Pair(Color(0xFF2196F3), "docker-host")
        Machine.LOCAL -> Pair(Color(0xFF4CAF50), "workstation")
        Machine.PHONE -> Pair(Color(0xFF9C27B0), "Phone")
        Machine.UNKNOWN -> Pair(MaterialTheme.colorScheme.outline, "Unknown")
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

@Composable
private fun StatusIndicator(status: String) {
    val color = when (status.lowercase()) {
        "active", "streaming" -> Color(0xFF4CAF50)
        "idle" -> MaterialTheme.colorScheme.onSurfaceVariant
        "error" -> MaterialTheme.colorScheme.error
        "waiting_permission" -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.outline
    }

    Text(
        text = status.replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

// ── Ironjaw Session Card ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IronjawSessionCard(
    session: ClaudeSession,
    isSelectedForSplit: Boolean = false,
    onAttach: () -> Unit,
    onDestroy: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val projectName = session.project?.takeIf { it.isNotEmpty() && it != "null" }
        ?: session.cwd.trimEnd('/', '\\')
            .split('/', '\\')
            .lastOrNull()
            ?.ifBlank { null }
        ?: "Unknown"

    val statusColor = when (session.status.lowercase()) {
        "active", "streaming" -> Color(0xFF4CAF50)
        "waiting", "waiting_permission" -> Color(0xFFFFA000)
        "idle" -> MaterialTheme.colorScheme.onSurfaceVariant
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    val cardColor = if (isSelectedForSplit) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onAttach,
                onLongClick = onLongPress,
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Title (or project) primary + project/model subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val hasTitle = !session.title.isNullOrBlank()
                    Text(
                        text = session.title?.takeIf { it.isNotBlank() } ?: projectName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Subtitle: show the project (dim) only when the title took the primary slot, plus the model chip.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        if (hasTitle) {
                            Text(
                                text = projectName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        session.model?.let { model ->
                            if (hasTitle) Spacer(modifier = Modifier.width(6.dp))
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                                    labelColor = Color(0xFF2196F3),
                                ),
                            )
                        }
                    }
                }

                // Action buttons
                Row {
                    IconButton(
                        onClick = onAttach,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Attach",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = onDestroy,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Destroy",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Row 2: Status + context % (cc.sessions sends no cost/tokens, so don't fake "$0.00")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIndicator(status = session.status)

                session.contextPct?.let { pct ->
                    Text(
                        text = "Ctx ${pct.toInt()}%" +
                            if (session.compactionCount > 0) " (${session.compactionCount}x)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Row 3: Session ID + last activity
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.ccBridgeId.take(8),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val lastActivityLabel = formatIsoRelative(session.lastActivity)
                if (lastActivityLabel.isNotBlank()) {
                    Text(
                        text = lastActivityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── Split Selection Banner ────────────────────────────────────────────

@Composable
private fun SplitSelectionBanner(
    firstSessionId: String,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Split View",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "Tap another session to open side-by-side with ${firstSessionId.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel split selection",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

// ── Creating Session Banner ───────────────────────────────────────────

@Composable
private fun CreatingSessionBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(
                    text = "Creating session\u2026",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────

private fun formatRelativeTime(epochMs: Long?): String {
    if (epochMs == null || epochMs == 0L) return "Unknown"
    val diffMs = System.currentTimeMillis() - epochMs
    val diffMinutes = diffMs / 60_000
    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
        else -> "${diffMinutes / 1440}d ago"
    }
}

private val isoUtcFormat by lazy {
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
}

// ponytail: parse only the second-precision prefix — chrono RFC3339 may append fractional secs
// or a "+00:00" offset we don't need for "Xh ago". Falls back to a trimmed slice on parse failure.
// SimpleDateFormat (not java.time) because minSdk 24 has no desugaring; synchronized as it's not thread-safe.
private fun formatIsoRelative(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val epochMs = synchronized(isoUtcFormat) { isoUtcFormat.parse(iso.take(19))?.time }
            ?: return iso.take(16).replace('T', ' ')
        formatRelativeTime(epochMs)
    } catch (_: Exception) {
        iso.take(16).replace('T', ' ')
    }
}
