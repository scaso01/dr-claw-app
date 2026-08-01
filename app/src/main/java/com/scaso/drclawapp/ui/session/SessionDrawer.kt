package com.scaso.drclawapp.ui.session

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.model.ChatSession
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.ui.ccbridge.CcPermissionDialog
import com.scaso.drclawapp.ui.components.SessionSearchBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDrawerContent(
    mainSessions: List<ChatSession>,
    subAgentSessions: List<ChatSession>,
    currentSessionKey: String?,
    onSessionClick: (ChatSession) -> Unit,
    onNewChat: () -> Unit,
    onRenameSession: (ChatSession) -> Unit,
    onDeleteSession: (ChatSession) -> Unit,
    onNavigateToCcSessions: () -> Unit = {},
    onNavigateToProjects: () -> Unit = {},
    onNavigateToModels: () -> Unit = {},
    onNavigateToChronicle: () -> Unit = {},
    onPinSession: (ChatSession, Boolean) -> Unit = { _, _ -> },
    onArchiveSession: (ChatSession, Boolean) -> Unit = { _, _ -> },
    archivedSessions: List<ChatSession> = emptyList(),
    onDismissSubAgent: (String) -> Unit = {},
    onClearAllDismissed: () -> Unit = {},
    connectionState: ConnectionState = ConnectionState.Disconnected,
    onRestartGateway: () -> Unit = {},
    brainMemoryCount: Int = 0,
    sessionSearchQuery: String = "",
    onSessionSearchQueryChange: (String) -> Unit = {},
    /** Called when the user confirms a new agent fork. Receives the task description. */
    onForkAgent: (task: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Agent-fork sheet state
    var showForkSheet by remember { mutableStateOf(false) }
    var forkTaskText by remember { mutableStateOf("") }
    var showForkPermissionDialog by remember { mutableStateOf(false) }
    val forkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val forkScope = rememberCoroutineScope()

    ModalDrawerSheet(modifier = modifier) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sessions",
                style = MaterialTheme.typography.headlineSmall,
            )
            FloatingActionButton(
                onClick = onNewChat,
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Chat",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        HorizontalDivider()

        SessionSearchBar(
            query = sessionSearchQuery,
            onQueryChange = onSessionSearchQueryChange,
        )

        // Session list with main/sub-agent sections
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            // Empty search results
            if (sessionSearchQuery.isNotBlank() &&
                mainSessions.isEmpty() && subAgentSessions.isEmpty() && archivedSessions.isEmpty()
            ) {
                item(key = "empty_search") {
                    Text(
                        text = "No matching sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }

            // Main sessions section
            if (mainSessions.isNotEmpty()) {
                item(key = "header_main") {
                    Text(
                        text = "Conversations",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(
                    items = mainSessions,
                    key = { it.sessionKey },
                ) { session ->
                    SessionItem(
                        session = session,
                        isActive = session.sessionKey == currentSessionKey,
                        onClick = { onSessionClick(session) },
                        onRename = { onRenameSession(session) },
                        onDelete = { onDeleteSession(session) },
                        onPin = { onPinSession(session, !session.isPinned) },
                        onArchive = { onArchiveSession(session, true) },
                        isPinned = session.isPinned,
                    )
                }
            }

            // Sub-agent sessions section (enhanced tracker)
            if (subAgentSessions.isNotEmpty()) {
                item(key = "header_subagent") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Sub-Agents",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Dispatch new sub-agent
                            IconButton(
                                onClick = {
                                    forkTaskText = ""
                                    showForkSheet = true
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Dispatch sub-agent",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(onClick = onClearAllDismissed) {
                                Text(
                                    text = "Clear all",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            // Badge count
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = "${subAgentSessions.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                items(
                    items = subAgentSessions,
                    key = { it.sessionKey },
                ) { session ->
                    SubAgentItem(
                        session = session,
                        isActive = session.sessionKey == currentSessionKey,
                        onClick = { onSessionClick(session) },
                        onDelete = { onDismissSubAgent(session.sessionKey) },
                    )
                }
            }

            // Archived sessions section (collapsible)
            if (archivedSessions.isNotEmpty()) {
                item(key = "header_archived") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Archived (${archivedSessions.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            archivedSessions.forEach { session ->
                                ArchivedSessionItem(
                                    session = session,
                                    onClick = { onSessionClick(session) },
                                    onUnarchive = { onArchiveSession(session, false) },
                                    onDelete = { onDeleteSession(session) },
                                )
                            }
                        }
                    }
                }
            }

            // Navigation items
            item(key = "nav_divider") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "nav_cc_sessions") {
                NavigationDrawerItem(
                    label = { Text("CC Sessions") },
                    selected = false,
                    onClick = onNavigateToCcSessions,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "nav_chronicle") {
                NavigationDrawerItem(
                    label = { Text("Chronicle") },
                    selected = false,
                    onClick = onNavigateToChronicle,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Dashboard,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "nav_models") {
                NavigationDrawerItem(
                    label = { Text("Models") },
                    selected = false,
                    onClick = onNavigateToModels,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Memory,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "nav_projects") {
                NavigationDrawerItem(
                    label = { Text("Projects") },
                    selected = false,
                    onClick = onNavigateToProjects,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Work,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            item(key = "nav_brain") {
                NavigationDrawerItem(
                    label = { Text("Brain") },
                    selected = false,
                    onClick = { /* Brain is on bottom nav, no-op */ },
                    icon = {
                        if (brainMemoryCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text("$brainMemoryCount")
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }

    // -- Agent fork bottom sheet ------------------------------------------

    if (showForkSheet) {
        ModalBottomSheet(
            onDismissRequest = { showForkSheet = false },
            sheetState = forkSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Dispatch Sub-Agent",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = forkTaskText,
                    onValueChange = { forkTaskText = it },
                    label = { Text("Task description") },
                    placeholder = { Text("Describe what the agent should do…") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        forkScope.launch { forkSheetState.hide() }
                        showForkSheet = false
                    }) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            if (forkTaskText.isNotBlank()) {
                                showForkPermissionDialog = true
                            }
                        },
                        enabled = forkTaskText.isNotBlank(),
                    ) {
                        Text("Dispatch")
                    }
                }
            }
        }
    }

    if (showForkPermissionDialog) {
        CcPermissionDialog(
            toolName = "agent.fork",
            input = JsonPrimitive(forkTaskText),
            consequences = "Spawns a child agent that will execute autonomously and may invoke tools.",
            onAllow = {
                showForkPermissionDialog = false
                forkScope.launch { forkSheetState.hide() }
                showForkSheet = false
                onForkAgent(forkTaskText)
            },
            onDeny = {
                showForkPermissionDialog = false
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit = {},
    onArchive: () -> Unit = {},
    isPinned: Boolean = false,
    showSubAgentBadge: Boolean = false,
) {
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box {
        Surface(
            color = backgroundColor,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (showSubAgentBadge) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "SA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = session.title ?: "Untitled Chat",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // "Cleared" badge: session with 0 messages older than 5 min
                    val isCleared = session.messageCount == 0 &&
                        session.createdAt != null &&
                        (System.currentTimeMillis() - session.createdAt) > 300_000L
                    if (isCleared) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = MaterialTheme.shapes.extraSmall,
                        ) {
                            Text(
                                text = "cleared",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }

                    if (session.messageCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "${session.messageCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = formatRelativeTime(session.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Token count + cost line
                val totalTokens = session.inputTokens + session.outputTokens
                if (totalTokens > 0) {
                    val tokenText = "${formatTokenCount(totalTokens)} tok"
                    val cost = estimateCost(session.modelName, session.inputTokens, session.outputTokens)
                    val displayText = if (cost != null) "$tokenText · ${formatCost(cost)}" else tokenText

                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // Context menu on long press
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showMenu = false
                    onRename()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(if (isPinned) "Unpin" else "Pin") },
                onClick = {
                    showMenu = false
                    onPin()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text("Archive") },
                onClick = {
                    showMenu = false
                    onArchive()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubAgentItem(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    // Determine if agent is active (updated recently = within 2 minutes)
    val isAgentActive = session.updatedAt != null &&
        (System.currentTimeMillis() - session.updatedAt) < 120_000L

    val statusColor = if (isAgentActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline

    Box {
        Surface(
            color = backgroundColor,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status indicator dot (pulses when active)
                if (isAgentActive) {
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pulseAlpha",
                    )
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = CircleShape,
                        color = statusColor.copy(alpha = alpha),
                    ) {}
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.title ?: "Sub-Agent",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (session.agentId != null) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text = session.agentId.split(":").lastOrNull() ?: "agent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    if (session.lastMessage != null) {
                        Text(
                            text = session.lastMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatRelativeTime(session.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Elapsed time since creation
                        if (session.createdAt != null) {
                            val elapsed = System.currentTimeMillis() - session.createdAt
                            val elapsedMin = elapsed / 60_000
                            Text(
                                text = if (elapsedMin < 60) "${elapsedMin}m elapsed"
                                else "${elapsedMin / 60}h ${elapsedMin % 60}m elapsed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Dismiss",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchivedSessionItem(
    session: ChatSession,
    onClick: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true },
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = "Archived",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title ?: "Untitled Chat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatRelativeTime(session.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Unarchive") },
                onClick = {
                    showMenu = false
                    onUnarchive()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Unarchive,
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

@Composable
private fun GatewayStatusRow(
    connectionState: ConnectionState,
) {
    val (statusColor, statusText) = when (connectionState) {
        is ConnectionState.Connected -> Color(0xFF4CAF50) to "Connected"
        is ConnectionState.Connecting, is ConnectionState.Authenticating -> Color(0xFFFFC107) to "Connecting..."
        is ConnectionState.Disconnected -> Color(0xFFF44336) to "Disconnected"
        is ConnectionState.Error -> Color(0xFFF44336) to "Error"
        is ConnectionState.AuthFailed -> Color(0xFFF44336) to "Auth failed"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = statusColor,
        ) {}
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Ironjaw Gateway",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Formats a Unix timestamp (milliseconds) into a human-readable relative time string.
 */
private fun formatRelativeTime(timestampMs: Long?): String {
    if (timestampMs == null || timestampMs <= 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - timestampMs

    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> {
            val minutes = diff / 60_000L
            if (minutes == 1L) "1 min ago" else "$minutes mins ago"
        }
        diff < 86_400_000L -> {
            val hours = diff / 3_600_000L
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        diff < 604_800_000L -> {
            val days = diff / 86_400_000L
            if (days == 1L) "Yesterday" else "$days days ago"
        }
        diff < 2_592_000_000L -> {
            val weeks = diff / 604_800_000L
            if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        }
        else -> {
            val months = diff / 2_592_000_000L
            if (months == 1L) "1 month ago" else "$months months ago"
        }
    }
}

private fun formatTokenCount(tokens: Long): String {
    return when {
        tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
        else -> "$tokens"
    }
}

/**
 * Estimates session cost based on model name and token counts.
 * Returns null if cost cannot be calculated (unknown/local model).
 */
private fun estimateCost(modelName: String?, inputTokens: Long, outputTokens: Long): Double? {
    if (modelName == null) return null
    val model = modelName.lowercase()
    // Prices per 1M tokens: Pair(input, output)
    val (inputPer1M, outputPer1M) = when {
        "opus" in model -> 15.0 to 75.0
        "haiku" in model -> 0.25 to 1.25
        "sonnet" in model || "claude" in model -> 3.0 to 15.0
        else -> return null // unknown/local models — no cost
    }
    return (inputTokens * inputPer1M + outputTokens * outputPer1M) / 1_000_000.0
}

private fun formatCost(cost: Double): String {
    return if (cost < 0.01) String.format("$%.3f", cost)
    else String.format("$%.2f", cost)
}
