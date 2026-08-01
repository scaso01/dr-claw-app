package com.scaso.drclawapp.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.scaso.drclawapp.data.model.ChatSession
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.roles.ChatBackend
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.ui.components.ClipboardPreview
import com.scaso.drclawapp.ui.components.DefaultQuickActions
import com.scaso.drclawapp.ui.components.MessageBubble
import com.scaso.drclawapp.ui.components.PermissionDialog
import com.scaso.drclawapp.ui.components.QuickActionsBar
import com.scaso.drclawapp.ui.components.SlashCommandPopup
import com.scaso.drclawapp.ui.components.SwipeToReply
import com.scaso.drclawapp.ui.components.ActivityStatusBar
import com.scaso.drclawapp.ui.components.ChatStatusBar
import com.scaso.drclawapp.ui.components.StatusDetailSheet
import com.scaso.drclawapp.ui.components.ThinkingIndicator
import com.scaso.drclawapp.ui.components.ToolEventCard
import com.scaso.drclawapp.data.model.MessageType
import com.scaso.drclawapp.ui.search.SearchMode
import com.scaso.drclawapp.ui.search.SearchViewModel
import com.scaso.drclawapp.ui.search.SearchableTopAppBar
import com.scaso.drclawapp.ui.session.ConfirmDeleteDialog
import com.scaso.drclawapp.ui.session.RenameSessionDialog
import com.scaso.drclawapp.ui.session.SessionDrawerContent
import com.scaso.drclawapp.ui.session.SessionViewModel
import com.scaso.drclawapp.ui.theme.ConnectedGreen
import com.scaso.drclawapp.ui.theme.ConnectingYellow
import com.scaso.drclawapp.ui.theme.DisconnectedRed
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.scaso.drclawapp.data.model.SyncState
import com.scaso.drclawapp.data.roles.RoleSkill
import com.scaso.drclawapp.service.ProactiveSuggestion
import com.scaso.drclawapp.service.ProactiveWorker
import com.scaso.drclawapp.ui.proactive.ProactiveCards
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCamera: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToCcSessions: () -> Unit = {},
    onNavigateToProjects: () -> Unit = {},
    onNavigateToModels: () -> Unit = {},
    onNavigateToChronicle: () -> Unit = {},
    initialSharedText: String? = null,
    initialSessionKey: String? = null,
    viewModel: ChatViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
    roleViewModel: RoleViewModel = hiltViewModel(),
    attachmentViewModel: com.scaso.drclawapp.ui.media.AttachmentViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isStreaming = uiState.messages.any { it.isStreaming }
    // Single source of truth for the unified agentic status line — see statusLineText().
    // Only one status indicator is ever shown at a time (ThinkingIndicator for "Thinking…",
    // ActivityStatusBar for everything else).
    val statusText = uiState.statusLineText()
    val view = LocalView.current
    val pendingAttachments by attachmentViewModel.pendingAttachments.collectAsStateWithLifecycle()

    // Search state
    val isSearchActive by searchViewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchQuery by searchViewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by searchViewModel.searchResults.collectAsStateWithLifecycle()
    val searchMode by searchViewModel.searchMode.collectAsStateWithLifecycle()
    val globalResults by searchViewModel.globalResults.collectAsStateWithLifecycle()

    // Session state
    val mainSessions by sessionViewModel.mainSessions.collectAsStateWithLifecycle()
    val subAgentSessions by sessionViewModel.subAgentSessions.collectAsStateWithLifecycle()
    val currentSessionKey by sessionViewModel.currentSessionKey.collectAsStateWithLifecycle()
    val currentSessionTitle by sessionViewModel.currentSessionTitle.collectAsStateWithLifecycle()
    val currentModelName by sessionViewModel.currentSessionModelName.collectAsStateWithLifecycle()
    val archivedSessions by sessionViewModel.archivedSessions.collectAsStateWithLifecycle()
    val sessionSearchQuery by sessionViewModel.sessionSearchQuery.collectAsStateWithLifecycle()
    val deleteError by sessionViewModel.deleteError.collectAsStateWithLifecycle()
    val forkAgentResult by sessionViewModel.forkAgentResult.collectAsStateWithLifecycle()

    // Current session data for status bar
    val currentSession = remember(mainSessions, currentSessionKey) {
        mainSessions.firstOrNull { it.sessionKey == currentSessionKey }
    }
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val brainMemoryCount by viewModel.brainMemoryCount.collectAsStateWithLifecycle()
    val availableCommands by viewModel.availableCommands.collectAsStateWithLifecycle()

    // Active sub-agent count for drawer badge (updated within 2 min)
    val activeSubAgentCount = remember(subAgentSessions) {
        val cutoff = System.currentTimeMillis() - 120_000L
        subAgentSessions.count { (it.updatedAt ?: 0L) > cutoff }
    }

    // Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Close drawer on back press instead of exiting the app
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Dialog state
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }

    // Role/model state
    val roleState by roleViewModel.uiState.collectAsStateWithLifecycle()
    val recentModels by roleViewModel.recentModels.collectAsStateWithLifecycle()
    var showRoleMenu by remember { mutableStateOf(false) }
    var showQuickSwitch by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showCloudConfirm by remember { mutableStateOf(false) }

    // Thinking level menu state
    var showThinkingMenu by remember { mutableStateOf(false) }

    // Status detail sheet state
    var showStatusDetail by remember { mutableStateOf(false) }

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }

    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }

    // Track previous streaming state to detect completion transition
    var wasStreaming by remember { mutableStateOf(false) }

    // Haptic feedback when streaming completes (true -> false transition)
    LaunchedEffect(isStreaming) {
        if (wasStreaming && !isStreaming) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        wasStreaming = isStreaming
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Pre-fill input with shared text
    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            viewModel.onInputChanged(initialSharedText)
        }
    }

    // Deep-link: switch to session from ntfy notification tap
    LaunchedEffect(initialSessionKey) {
        if (!initialSessionKey.isNullOrBlank()) {
            sessionViewModel.switchSession(initialSessionKey)
        }
    }

    // Handle share intent
    val shareContent by viewModel.shareContent.collectAsStateWithLifecycle()
    val context = view.context
    LaunchedEffect(shareContent) {
        shareContent?.let { content ->
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                putExtra(android.content.Intent.EXTRA_TEXT, content)
                type = "text/plain"
            }
            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share message"))
            viewModel.clearShareContent()
        }
    }

    // Show snackbar on brain remember result
    val rememberResult by viewModel.rememberResult.collectAsStateWithLifecycle()
    LaunchedEffect(rememberResult) {
        rememberResult?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short,
            )
            viewModel.clearRememberResult()
        }
    }

    // Show snackbar on delete errors
    LaunchedEffect(deleteError) {
        deleteError?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
            )
            sessionViewModel.clearDeleteError()
        }
    }

    // Show snackbar on agent fork result
    LaunchedEffect(forkAgentResult) {
        forkAgentResult?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
            )
            sessionViewModel.clearForkAgentResult()
        }
    }

    // Show snackbar on connection errors
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is ConnectionState.Error) {
            val error = (uiState.connectionState as ConnectionState.Error).message
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Load sessions, default model, and roles when connected
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState is ConnectionState.Connected) {
            sessionViewModel.loadSessions()
            sessionViewModel.refreshDefaultModel()
            roleViewModel.refresh()
        }
    }

    // Stall warning: snackbar after 90s of waiting
    LaunchedEffect(uiState.isAwaitingResponse) {
        if (uiState.isAwaitingResponse) {
            delay(90_000L)
            if (uiState.isAwaitingResponse) {
                val result = snackbarHostState.showSnackbar(
                    message = "No response for 90 seconds",
                    actionLabel = "Abort",
                    duration = SnackbarDuration.Long,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    viewModel.abortCurrentRun()
                    viewModel.markRetryAvailable()
                }
            }
        }
    }

    // Escalated warning: gateway may be stuck after consecutive failures
    LaunchedEffect(uiState.consecutiveFailures) {
        if (uiState.consecutiveFailures >= 2) {
            snackbarHostState.showSnackbar(
                message = "Gateway may be stuck (${uiState.consecutiveFailures} failures)",
                duration = SnackbarDuration.Long,
            )
        }
    }

    // Context warning: snackbar when context crosses 75%
    val contextPctFloat = currentSession?.contextPct?.let { it / 100f } ?: 0f
    LaunchedEffect(contextPctFloat) {
        if (contextPctFloat >= 0.75f && !uiState.contextWarningShown) {
            val pctInt = (contextPctFloat * 100).toInt()
            snackbarHostState.showSnackbar(
                message = "Context at $pctInt% — consider starting a new session",
                duration = SnackbarDuration.Long,
            )
            viewModel.markContextWarningShown()
        }
    }

    // Refresh sessions when drawer opens
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            sessionViewModel.loadSessions()
        }
    }

    // Rename dialog
    sessionToRename?.let { session ->
        RenameSessionDialog(
            currentTitle = session.title ?: "Untitled Chat",
            onConfirm = { newTitle ->
                sessionViewModel.renameSession(session.sessionKey, newTitle)
                sessionToRename = null
            },
            onDismiss = { sessionToRename = null },
        )
    }

    // Delete confirmation dialog
    sessionToDelete?.let { session ->
        ConfirmDeleteDialog(
            sessionTitle = session.title ?: "Untitled Chat",
            onConfirm = {
                sessionViewModel.deleteSession(session.sessionKey)
                sessionToDelete = null
            },
            onDismiss = { sessionToDelete = null },
        )
    }

    // Cloud backend cost-confirm dialog (switching chat to paid Anthropic)
    if (showCloudConfirm) {
        AlertDialog(
            onDismissRequest = { showCloudConfirm = false },
            title = { Text("Switch chat to Cloud?") },
            text = { Text("Chat will use Anthropic Claude — instant, but costs money per token. Local (llama) is free.") },
            dismissButton = {
                TextButton(onClick = { showCloudConfirm = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCloudConfirm = false
                    roleViewModel.applyPreset("cloud")
                }) { Text("Switch to Cloud") }
            },
        )
    }

    // Status detail bottom sheet
    val vmInputTokens by viewModel.sessionInputTokens.collectAsStateWithLifecycle()
    val vmOutputTokens by viewModel.sessionOutputTokens.collectAsStateWithLifecycle()
    val vmSessionStart by viewModel.sessionStartTimeMs.collectAsStateWithLifecycle()
    val promptStats by viewModel.promptStats.collectAsStateWithLifecycle()
    LaunchedEffect(showStatusDetail) {
        if (showStatusDetail) viewModel.loadPromptStats()
    }
    if (showStatusDetail) {
        StatusDetailSheet(
            modelName = currentModelName,
            promptStats = promptStats,
            inputTokens = if (vmInputTokens > 0) vmInputTokens else currentSession?.inputTokens ?: 0L,
            outputTokens = if (vmOutputTokens > 0) vmOutputTokens else currentSession?.outputTokens ?: 0L,
            contextPercent = currentSession?.contextPct?.let { pct ->
                (pct.toFloat() / 100f).coerceIn(0f, 1f)
            } ?: currentSession?.contextTokens?.let { tokens ->
                val window = currentSession?.contextWindow ?: 200_000
                (tokens.toFloat() / window.toFloat()).coerceIn(0f, 1f)
            } ?: 0f,
            sessionStartTime = if (vmSessionStart > 0) vmSessionStart else currentSession?.createdAt,
            onNewConversation = {
                sessionViewModel.createNewSession()
            },
            onDismiss = { showStatusDetail = false },
        )
    }

    // Phase 16: native-chat HITL dialog. GatewayEvent.PermissionRequest (legacy cc.chat +
    // slash_command path) is handled by the global ApprovalDialog in NavGraph.kt instead.
    uiState.pendingPermission?.let { nativeReq ->
        PermissionDialog(
            request = nativeReq,
            onDecision = { approve, scope ->
                if (approve) {
                    viewModel.approveNativePermission(scope)
                } else {
                    viewModel.denyNativePermission(scope)
                }
            },
            onDismiss = { viewModel.denyNativePermission() },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawerContent(
                mainSessions = mainSessions,
                subAgentSessions = subAgentSessions,
                currentSessionKey = currentSessionKey,
                onSessionClick = { session ->
                    sessionViewModel.switchSession(session.sessionKey)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    sessionViewModel.createNewSession()
                    scope.launch { drawerState.close() }
                },
                onRenameSession = { session ->
                    sessionToRename = session
                },
                onDeleteSession = { session ->
                    sessionToDelete = session
                },
                onPinSession = { session, pinned ->
                    sessionViewModel.pinSession(session.sessionKey, pinned)
                },
                onArchiveSession = { session, archived ->
                    sessionViewModel.archiveSession(session.sessionKey, archived)
                },
                archivedSessions = archivedSessions,
                onDismissSubAgent = { key ->
                    sessionViewModel.dismissSubAgent(key)
                },
                onClearAllDismissed = {
                    sessionViewModel.clearAllDismissed()
                },
                connectionState = uiState.connectionState,
                onRestartGateway = {
                    scope.launch {
                        val result = viewModel.restartGateway()
                        snackbarHostState.showSnackbar(
                            message = result.fold(
                                onSuccess = { "Gateway restart requested" },
                                onFailure = { "Gateway restart failed: ${it.message}" },
                            ),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onNavigateToCcSessions = {
                    scope.launch { drawerState.close() }
                    onNavigateToCcSessions()
                },
                onNavigateToChronicle = {
                    scope.launch { drawerState.close() }
                    onNavigateToChronicle()
                },
                onNavigateToProjects = {
                    scope.launch { drawerState.close() }
                    onNavigateToProjects()
                },
                onNavigateToModels = {
                    scope.launch { drawerState.close() }
                    onNavigateToModels()
                },
                brainMemoryCount = brainMemoryCount,
                sessionSearchQuery = sessionSearchQuery,
                onSessionSearchQueryChange = sessionViewModel::setSessionSearchQuery,
                onForkAgent = { task ->
                    sessionViewModel.forkAgent(task)
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                SearchableTopAppBar(
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    searchResultCount = if (searchMode == SearchMode.GLOBAL) globalResults.size else searchResults.size,
                    onSearchQueryChange = searchViewModel::setSearchQuery,
                    onSearchToggle = searchViewModel::toggleSearch,
                    onSearchClose = searchViewModel::clearSearch,
                    searchMode = searchMode,
                    onToggleSearchMode = searchViewModel::toggleSearchMode,
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = currentSessionTitle,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            ConnectionDot(uiState.connectionState)
                            // Role chip
                            val roleName = roleState.activeRole?.name
                            if (!roleName.isNullOrBlank()) {
                                Spacer(Modifier.width(4.dp))
                                RoleChip(
                                    roleName = roleName,
                                    roles = roleState.roles,
                                    expanded = showRoleMenu,
                                    onToggle = { showRoleMenu = !showRoleMenu },
                                    onDismiss = { showRoleMenu = false },
                                    onSelect = { role ->
                                        roleViewModel.switchRole(role.name)
                                        showRoleMenu = false
                                    },
                                )
                            }
                            // Model chip with quick-switch
                            val modelDisplay = roleState.activeModel ?: currentModelName
                            if (!modelDisplay.isNullOrBlank()) {
                                Spacer(Modifier.width(4.dp))
                                ModelChipWithQuickSwitch(
                                    modelName = modelDisplay,
                                    recentModels = recentModels,
                                    showQuickSwitch = showQuickSwitch,
                                    onToggleQuickSwitch = { showQuickSwitch = !showQuickSwitch },
                                    onDismiss = { showQuickSwitch = false },
                                    onSelect = { model ->
                                        roleViewModel.switchModel(model)
                                        showQuickSwitch = false
                                    },
                                )
                            }
                            // Plan-mode chip
                            Spacer(Modifier.width(4.dp))
                            Box {
                                val modeColor = when (uiState.sessionMode) {
                                    "Plan" -> ConnectingYellow
                                    "AutoAccept" -> ConnectedGreen
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                AssistChip(
                                    onClick = { showModeMenu = !showModeMenu },
                                    label = { Text(uiState.sessionMode, style = MaterialTheme.typography.labelSmall) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = modeColor),
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = modeColor),
                                )
                                DropdownMenu(
                                    expanded = showModeMenu,
                                    onDismissRequest = { showModeMenu = false },
                                ) {
                                    listOf("Default", "Plan", "AutoAccept").forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode) },
                                            onClick = {
                                                viewModel.setSessionMode(mode)
                                                showModeMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            BadgedBox(
                                badge = {
                                    if (activeSubAgentCount > 0) {
                                        Badge { Text("$activeSubAgentCount") }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open sessions",
                                )
                            }
                        }
                    },
                    extraActions = {
                        // Verbose toggle
                        IconButton(onClick = { viewModel.toggleVerbose() }) {
                            Icon(
                                imageVector = if (uiState.verboseEnabled) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = if (uiState.verboseEnabled) "Verbose on" else "Verbose off",
                                tint = if (uiState.verboseEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Force-fresh toggle — skip cached brain memories for this message
                        IconButton(onClick = { viewModel.toggleForceFresh() }) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = if (uiState.forceFresh)
                                    "Force fresh on — skip cached brain memories for this message"
                                else
                                    "Force fresh off — tap to skip brain memories for next message",
                                tint = if (uiState.forceFresh) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Thinking level menu
                        Box {
                            IconButton(onClick = { showThinkingMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Thinking: ${uiState.thinkingLevel}",
                                )
                            }
                            DropdownMenu(
                                expanded = showThinkingMenu,
                                onDismissRequest = { showThinkingMenu = false },
                            ) {
                                listOf("off", "low", "medium", "high").forEach { level ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = level.replaceFirstChar { it.uppercase() },
                                                fontWeight = if (level == uiState.thinkingLevel)
                                                    androidx.compose.ui.text.font.FontWeight.Bold else null,
                                            )
                                        },
                                        onClick = {
                                            viewModel.setThinkingLevel(level)
                                            showThinkingMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    },
                )
            },
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Reconnect banner for disconnected/error/auth-failed states
                if (uiState.connectionState is ConnectionState.Disconnected ||
                    uiState.connectionState is ConnectionState.Error ||
                    uiState.connectionState is ConnectionState.AuthFailed
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = when (uiState.connectionState) {
                                is ConnectionState.AuthFailed ->
                                    "Authentication failed — check gateway token in Settings"
                                is ConnectionState.Error ->
                                    "Connection error: ${(uiState.connectionState as ConnectionState.Error).message}"
                                else -> "Disconnected — pull to refresh to reconnect"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                // Persistent status bar
                ChatStatusBar(
                    modelName = currentModelName,
                    contextPercent = currentSession?.contextPct?.let { pct ->
                        (pct.toFloat() / 100f).coerceIn(0f, 1f)
                    } ?: currentSession?.contextTokens?.let { tokens ->
                        val window = currentSession?.contextWindow ?: 200_000
                        (tokens.toFloat() / window.toFloat()).coerceIn(0f, 1f)
                    } ?: 0f,
                    connectionState = uiState.connectionState,
                    sessionCost = null, // calculated in status bar from tokens
                    inputTokens = currentSession?.inputTokens ?: 0L,
                    outputTokens = currentSession?.outputTokens ?: 0L,
                    compactionCount = currentSession?.compactionCount ?: 0,
                    onClick = { showStatusDetail = true },
                    trailing = {
                        val backendModel = roleState.activeModel ?: currentModelName
                        if (!backendModel.isNullOrBlank()) {
                            BackendToggle(
                                backend = com.scaso.drclawapp.data.roles.chatBackendOf(backendModel),
                                isSwitching = roleState.isSwitching,
                                onLocal = { roleViewModel.applyPreset("local") },
                                onCloud = { showCloudConfirm = true },
                            )
                        }
                    },
                )

                // Message list with pull-to-refresh (reversed so newest at bottom)
                Box(modifier = Modifier.weight(1f)) {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            scope.launch {
                                try {
                                    viewModel.refreshHistory()
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            verticalArrangement = Arrangement.Top,
                        ) {
                            // Unified status line, part 1: "Thinking…" dots at bottom (first in
                            // reversed layout). Only shown when statusText picked "Thinking…" —
                            // ActivityStatusBar (below the list) handles every other status text
                            // so the two indicators never both show at once.
                            if (statusText == "Thinking…") {
                                item(key = "thinking_indicator") {
                                    ThinkingIndicator(startTimeMs = uiState.thinkingStartTimeMs)
                                }
                            }

                            val displayMessages = if (isSearchActive && searchQuery.isNotBlank()) {
                                searchResults
                            } else {
                                uiState.messages
                            }

                            items(
                                items = displayMessages.reversed(),
                                key = { it.id },
                            ) { message ->
                                when (message.messageType) {
                                    MessageType.TOOL_USE, MessageType.TOOL_RESULT -> {
                                        if (uiState.verboseEnabled) {
                                            ToolEventCard(message = message)
                                        }
                                    }
                                    else -> {
                                        SwipeToReply(
                                            onReply = { viewModel.setReplyTo(message) },
                                        ) {
                                            MessageBubble(
                                                message = message,
                                                onReply = { viewModel.setReplyTo(message) },
                                                onShare = { viewModel.shareMessage(message.content) },
                                                onFork = { viewModel.forkFromMessage(message) },
                                                onRemember = { viewModel.rememberMessage(message.content) },
                                                onRegenerate = { viewModel.regenerateResponse(message.id) },
                                                onDelete = {
                                                    if (message.role == Role.USER) {
                                                        viewModel.startEditMessage(message)
                                                    } else {
                                                        viewModel.deleteMessage(message.id)
                                                    }
                                                },
                                                onActionClick = { action -> viewModel.onInputChanged(action) },
                                                onFileDownload = { path -> viewModel.downloadFile(path) },
                                                onRetrySync = { msgId -> viewModel.retrySyncMessage(msgId) },
                                                downloadStates = downloadStates,
                                                collapseByDefault = !uiState.verboseEnabled,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Scroll-to-bottom FAB (reverseLayout: index 0 = newest)
                    val showScrollFab by remember {
                        derivedStateOf { listState.firstVisibleItemIndex > 0 }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showScrollFab,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        SmallFloatingActionButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                            )
                        }
                    }
                }

                // Quick actions when conversation is empty
                if (uiState.messages.isEmpty()) {
                    QuickActionsBar(
                        actions = DefaultQuickActions,
                        onActionClick = { action ->
                            viewModel.onInputChanged("$action: ")
                        },
                    )
                }

                // Clipboard preview
                ClipboardPreview(
                    onSendClipboard = { clipText ->
                        viewModel.onInputChanged(clipText)
                        viewModel.sendMessage()
                    },
                )

                // Unified status line, part 2: tool/synthesizing/legacy-activity text row.
                // "Thinking…" is rendered above instead (bouncing dots) — never both at once.
                val activityText = statusText?.takeIf { it != "Thinking…" }
                if (activityText != null || uiState.activeTools.isNotEmpty()) {
                    ActivityStatusBar(
                        activity = activityText,
                        activeTools = uiState.activeTools,
                    )
                }

                // Resume chip after interrupt
                if (uiState.canResume) {
                    AssistChip(
                        onClick = { viewModel.resumePreviousTask() },
                        label = { Text("Resume previous task") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // Retry chip after stall abort
                if (uiState.showRetry && uiState.lastSentMessage != null) {
                    AssistChip(
                        onClick = { viewModel.retryLastMessage() },
                        label = {
                            Text(
                                text = "Retry: " + (uiState.lastSentMessage?.take(30) ?: "") +
                                    if ((uiState.lastSentMessage?.length ?: 0) > 30) "..." else "",
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                // Slash command popup
                if (uiState.inputText.startsWith("/")) {
                    SlashCommandPopup(
                        inputText = uiState.inputText,
                        commands = availableCommands,
                        onCommandSelected = { command ->
                            val trimmed = command.trimEnd()
                            if (trimmed in setOf("/new", "/clear", "/help")) {
                                viewModel.onInputChanged(command + " ")
                            } else {
                                viewModel.executeCommand(trimmed)
                            }
                        },
                    )
                }

                // Reply preview bar
                uiState.replyingTo?.let { replyMsg ->
                    ReplyPreviewBar(
                        replyMessage = replyMsg,
                        onDismiss = { viewModel.clearReply() },
                    )
                }

                // Proactive suggestion cards
                val proactiveSuggestions by ProactiveWorker.suggestions.collectAsStateWithLifecycle()
                ProactiveCards(
                    suggestions = proactiveSuggestions,
                    onSuggestionTap = { suggestionText ->
                        viewModel.onInputChanged(suggestionText)
                        viewModel.sendMessage()
                    },
                    onDismiss = { index ->
                        ProactiveWorker.dismissSuggestion(index)
                    },
                )

                // Input bar
                InputBar(
                    text = uiState.inputText,
                    onTextChange = viewModel::onInputChanged,
                    onSend = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (pendingAttachments.isNotEmpty()) {
                            val unsupported = viewModel.sendMessageWithAttachments(view.context, attachmentViewModel)
                            if (unsupported.isNotEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Unsupported: ${unsupported.joinToString(", ")}",
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
                        } else {
                            viewModel.sendMessage()
                        }
                    },
                    onStop = viewModel::interrupt,
                    isStreaming = isStreaming,
                    enabled = uiState.connectionState is ConnectionState.Connected,
                    pendingAttachments = pendingAttachments,
                    onAttachmentSelected = { uri, mimeType ->
                        attachmentViewModel.addAttachment(uri, mimeType, displayName = uri.lastPathSegment ?: "file")
                    },
                    onRemoveAttachment = { id -> attachmentViewModel.removeAttachment(id) },
                    onCameraClick = onNavigateToCamera,
                    onVoiceClick = onNavigateToVoice,
                )
            }
        }
    }
}

@Composable
private fun ReplyPreviewBar(
    replyMessage: com.scaso.drclawapp.data.model.Message,
    onDismiss: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(width = 3.dp, height = 32.dp),
                content = {},
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (replyMessage.role == com.scaso.drclawapp.data.model.Role.USER) "You" else "Dr. CLAW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = replyMessage.content.take(80),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Cancel reply",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val color = when (state) {
        is ConnectionState.Connected -> ConnectedGreen
        is ConnectionState.Connecting,
        is ConnectionState.Authenticating -> ConnectingYellow
        is ConnectionState.Disconnected,
        is ConnectionState.Error,
        is ConnectionState.AuthFailed -> DisconnectedRed
    }

    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(10.dp),
        content = {},
    )
}

@Composable
private fun RoleChip(
    roleName: String,
    roles: List<RoleSkill>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (RoleSkill) -> Unit,
) {
    Box {
        Surface(
            onClick = onToggle,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = roleName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .widthIn(max = 80.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
        ) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = role.name,
                            fontWeight = if (role.name == roleName)
                                androidx.compose.ui.text.font.FontWeight.Bold else null,
                        )
                    },
                    onClick = { onSelect(role) },
                    modifier = Modifier.semantics {
                        contentDescription = "Switch to ${role.name} role"
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelChipWithQuickSwitch(
    modelName: String,
    recentModels: List<com.scaso.drclawapp.data.roles.RecentModel>,
    showQuickSwitch: Boolean,
    onToggleQuickSwitch: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    // Filter to local models only (no cloud in quick-switch), exclude currently loaded, limit to 3
    val quickSwitchModels = remember(recentModels, modelName) {
        recentModels
            .filter { it.name != modelName && !it.name.contains("/") }
            .take(3)
    }

    Column {
        Surface(
            onClick = onToggleQuickSwitch,
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .widthIn(max = 120.dp),
            )
        }
        AnimatedVisibility(visible = showQuickSwitch && quickSwitchModels.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                quickSwitchModels.forEach { recent ->
                    Surface(
                        onClick = { onSelect(recent.name) },
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.semantics {
                            contentDescription = "Quick switch to ${recent.name}"
                        },
                    ) {
                        Text(
                            text = recent.name
                                .removePrefix("llama-server/")
                                .take(16),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .widthIn(max = 100.dp),
                        )
                    }
                }
            }
        }
    }
}

// Chat backend toggle: a two-segment Cloud | Local control. The highlighted segment is the
// badge (which backend is serving chat now); tapping the other switches via role.preset.apply.
@Composable
private fun BackendToggle(
    backend: ChatBackend,
    isSwitching: Boolean,
    onLocal: () -> Unit,
    onCloud: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        BackendSegment("Cloud", backend == ChatBackend.CLOUD, !isSwitching, onCloud)
        BackendSegment("Local", backend == ChatBackend.LOCAL, !isSwitching, onLocal)
    }
}

@Composable
private fun BackendSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.semantics {
            contentDescription = (if (selected) "Chat on $label" else "Switch chat to $label")
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    enabled: Boolean,
    pendingAttachments: List<com.scaso.drclawapp.data.model.Attachment> = emptyList(),
    onAttachmentSelected: (android.net.Uri, String) -> Unit = { _, _ -> },
    onRemoveAttachment: (String) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Attachment preview strip
        if (pendingAttachments.isNotEmpty()) {
            com.scaso.drclawapp.ui.media.AttachmentPreview(
                attachments = pendingAttachments,
                onRemove = onRemoveAttachment,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Attachment button (left)
            com.scaso.drclawapp.ui.media.AttachmentButton(
                onAttachmentSelected = onAttachmentSelected,
                onCameraClick = onCameraClick,
            )

            // Text field (center)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Dr. CLAW...") },
                enabled = enabled,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (text.isNotBlank() || pendingAttachments.isNotEmpty()) onSend() },
                ),
                shape = MaterialTheme.shapes.extraLarge,
            )

            Spacer(Modifier.width(4.dp))

            // Right button: Stop / Send / Mic
            if (isStreaming) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (text.isBlank() && pendingAttachments.isEmpty()) {
                IconButton(onClick = onVoiceClick, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice mode",
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (enabled)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
        }
    }
}
