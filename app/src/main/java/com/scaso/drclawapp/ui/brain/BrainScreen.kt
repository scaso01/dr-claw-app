package com.scaso.drclawapp.ui.brain

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.brain.BrainMemory
import com.scaso.drclawapp.data.brain.BrainSnapshot
import com.scaso.drclawapp.data.brain.BrainStatus
import com.scaso.drclawapp.data.brain.ContextVariable
import com.scaso.drclawapp.data.brain.DreamStatus
import com.scaso.drclawapp.data.brain.GraphEntity
import com.scaso.drclawapp.data.brain.PeekResult
import com.scaso.drclawapp.data.brain.PendingMemory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrainScreen(
    viewModel: BrainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 9 })
    var forgetTarget by remember { mutableStateOf<BrainMemory?>(null) }

    // Load status on first composition
    LaunchedEffect(Unit) {
        viewModel.loadStatus()
    }

    LaunchedEffect(uiState.rememberSuccess) {
        if (uiState.rememberSuccess) {
            snackbarHostState.showSnackbar("Memory saved")
            viewModel.dismissSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Brain") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Status header
            uiState.brainStatus?.let { status ->
                StatusHeader(status = status)
            }

            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Search") },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Search"),
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Add") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Add"),
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(2)
                            viewModel.loadContextVars()
                        }
                    },
                    text = { Text("Context") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Context"),
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(3)
                            viewModel.loadSummaries()
                        }
                    },
                    text = { Text("Summaries") },
                    icon = { Icon(Icons.Default.Summarize, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Summaries"),
                )
                Tab(
                    selected = pagerState.currentPage == 4,
                    onClick = { scope.launch { pagerState.animateScrollToPage(4) } },
                    text = { Text("Timeline") },
                    icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Timeline"),
                )
                Tab(
                    selected = pagerState.currentPage == 5,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(5)
                            viewModel.loadGraph()
                        }
                    },
                    text = { Text("Graph") },
                    icon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Graph"),
                )
                Tab(
                    selected = pagerState.currentPage == 6,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(6)
                            viewModel.loadPending()
                        }
                    },
                    text = { Text("Pending") },
                    icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Pending"),
                )
                Tab(
                    selected = pagerState.currentPage == 7,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(7)
                            viewModel.loadStatus()
                        }
                    },
                    text = { Text("Dream") },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Dream"),
                )
                Tab(
                    selected = pagerState.currentPage == 8,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(8)
                            viewModel.loadSnapshots()
                        }
                    },
                    text = { Text("Snapshots") },
                    icon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    modifier = Modifier.testTag("brain_tab_Snapshots"),
                )
            }

            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    0 -> SearchTab(
                        uiState = uiState,
                        onRecall = viewModel::recallWithDetail,
                        onLongPressMemory = { forgetTarget = it },
                    )
                    1 -> AddTab(
                        isRemembering = uiState.isRemembering,
                        onRemember = viewModel::remember,
                    )
                    2 -> ContextTab(
                        contextVars = uiState.contextVars,
                        isLoading = uiState.isLoading,
                        isRemembering = uiState.isRemembering,
                        onToggle = viewModel::toggleContextVar,
                        onDelete = viewModel::deleteContextVar,
                        onAdd = viewModel::addContextVar,
                        onPeek = viewModel::peekContextVar,
                    )
                    3 -> SummariesTab(
                        summaries = uiState.summaries,
                        isLoading = uiState.isLoading,
                        onSearch = viewModel::loadSummaries,
                    )
                    4 -> TimelineTab(
                        timelineResults = uiState.timelineResults,
                        isLoading = uiState.isLoading,
                        onSearch = viewModel::queryTimeline,
                    )
                    5 -> GraphTab(
                        entities = uiState.entities,
                        isLoading = uiState.isLoading,
                    )
                    6 -> PendingTab(
                        pendingMemories = uiState.pendingMemories,
                        isLoading = uiState.isLoading,
                        onLoadPending = viewModel::loadPending,
                        onApprove = viewModel::approvePending,
                        onReject = viewModel::rejectPending,
                    )
                    7 -> DreamTab(
                        dreamStatus = uiState.brainStatus?.dream,
                    )
                    8 -> SnapshotsTab(viewModel = viewModel)
                }
            }
        }
    }

    // Forget confirmation dialog
    if (forgetTarget != null) {
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text("Forget memory?") },
            text = {
                Text(
                    forgetTarget!!.content.take(200),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgetTarget!!.id?.let { viewModel.forget(it) }
                        forgetTarget = null
                    },
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { forgetTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Peek dialog
    uiState.peekResult?.let { peek ->
        PeekDialog(
            peekResult = peek,
            onDismiss = viewModel::dismissPeek,
        )
    }
}

// -- StatusHeader ----------------------------------------------------------

@Composable
private fun StatusHeader(status: BrainStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusChip(label = "Memories", value = status.memory_count.toString())
        StatusChip(label = "Temporal", value = status.temporal_active.toString())
        StatusChip(label = "Expired", value = status.temporal_expired.toString())
        StatusChip(label = "Context", value = status.context_vars.count.toString())
        StatusChip(
            label = "Tokens",
            value = formatTokenCount(status.context_vars.total_tokens),
        )
    }
}

@Composable
private fun StatusChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun formatTokenCount(tokens: Int): String {
    return when {
        tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
        tokens >= 1_000 -> "%.1fK".format(tokens / 1_000.0)
        else -> tokens.toString()
    }
}

// -- SearchTab (enhanced) --------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTab(
    uiState: BrainUiState,
    onRecall: (String, String) -> Unit,
    onLongPressMemory: (BrainMemory) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedDetail by remember { mutableStateOf("full") }
    val detailOptions = listOf("compact", "full", "deep")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search memories...") },
                singleLine = true,
            )
            Button(
                onClick = { onRecall(query, selectedDetail) },
                enabled = query.isNotBlank() && !uiState.isLoading,
            ) {
                Text("Search")
            }
        }

        // Detail level selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detailOptions.forEach { detail ->
                FilterChip(
                    selected = selectedDetail == detail,
                    onClick = { selectedDetail = detail },
                    label = { Text(detail.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.error != null) {
            Text(
                text = uiState.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.memories, key = { it.id ?: it.content.hashCode().toString() }) { memory ->
                EnhancedMemoryCard(
                    memory = memory,
                    onLongPress = { onLongPressMemory(memory) },
                )
            }

            if (uiState.memories.isEmpty() && !uiState.isLoading) {
                item(key = "empty") {
                    Text(
                        text = "No memories found. Try a different query.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

// -- EnhancedMemoryCard ----------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EnhancedMemoryCard(
    memory: BrainMemory,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Memory type chip + importance/strength row
            val hasMetadata = memory.memory_type != null || memory.importance != null || memory.strength != null
            if (hasMetadata) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    memory.memory_type?.let { type ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    memory.importance?.let { imp ->
                        Text(
                            text = "Imp: %.0f%%".format(imp * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    memory.strength?.let { str ->
                        Text(
                            text = "Str: %.0f%%".format(str * 100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
            )

            // Temporal date range
            val hasTemporal = memory.valid_from != null || memory.valid_to != null
            if (hasTemporal) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Valid: ${memory.valid_from ?: "..."} - ${memory.valid_to ?: "ongoing"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    memory.similarity?.let { sim ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "%.1f%%".format(sim * 100),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    memory.source?.let { src ->
                        Text(
                            text = src,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                memory.created_at?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -- AddTab ----------------------------------------------------------------

@Composable
private fun AddTab(
    isRemembering: Boolean,
    onRemember: (String) -> Unit,
) {
    var content by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter something to remember...") },
            minLines = 4,
            maxLines = 8,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                onRemember(content)
                content = ""
            },
            enabled = content.isNotBlank() && !isRemembering,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRemembering) "Saving..." else "Remember")
        }
    }
}

// -- ContextTab ------------------------------------------------------------

@Composable
private fun ContextTab(
    contextVars: List<ContextVariable>,
    isLoading: Boolean,
    isRemembering: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onPeek: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ContextVariable?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(contextVars, key = { it.id }) { variable ->
                ContextVarCard(
                    variable = variable,
                    onToggle = { loaded -> onToggle(variable.name, loaded) },
                    onPeek = { onPeek(variable.name) },
                    onDelete = { deleteTarget = variable },
                )
            }

            if (contextVars.isEmpty() && !isLoading) {
                item(key = "empty") {
                    Text(
                        text = "No context variables. Add one below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }

        // Add context variable form
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Variable name") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newContent,
            onValueChange = { newContent = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Content") },
            minLines = 2,
            maxLines = 4,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onAdd(newName.trim(), newContent)
                newName = ""
                newContent = ""
            },
            enabled = newName.isNotBlank() && newContent.isNotBlank() && !isRemembering,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRemembering) "Adding..." else "Add Variable")
        }
    }

    // Delete confirmation dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete context variable?") },
            text = {
                Text(
                    "Delete \"${deleteTarget!!.name}\"? This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(deleteTarget!!.name)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ContextVarCard(
    variable: ContextVariable,
    onToggle: (Boolean) -> Unit,
    onPeek: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = variable.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${variable.token_count} tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    variable.updated_at?.let { date ->
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Switch(
                checked = variable.loaded,
                onCheckedChange = onToggle,
            )

            IconButton(onClick = onPeek) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = "Peek",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// -- SummariesTab ----------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummariesTab(
    summaries: List<BrainMemory>,
    isLoading: Boolean,
    onSearch: (String?) -> Unit,
) {
    var sessionFilter by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = sessionFilter,
                onValueChange = { sessionFilter = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Session ID (optional)") },
                singleLine = true,
            )
            Button(
                onClick = {
                    onSearch(sessionFilter.ifBlank { null })
                },
                enabled = !isLoading,
            ) {
                Text("Search")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(summaries, key = { it.id ?: it.content.hashCode().toString() }) { memory ->
                SummaryCard(memory = memory)
            }

            if (summaries.isEmpty() && !isLoading) {
                item(key = "empty") {
                    Text(
                        text = "No summaries found. Tap Search to load.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(memory: BrainMemory) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            memory.memory_type?.let { type ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                memory.source?.let { src ->
                    Text(
                        text = src,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                memory.created_at?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// -- TimelineTab -----------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineTab(
    timelineResults: List<BrainMemory>,
    isLoading: Boolean,
    onSearch: (String, Int) -> Unit,
) {
    var dateInput by remember { mutableStateOf("") }
    var limitInput by remember { mutableStateOf("10") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = dateInput,
                onValueChange = { dateInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Date (e.g. 2026-03-23)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = limitInput,
                onValueChange = { limitInput = it },
                modifier = Modifier.width(80.dp),
                placeholder = { Text("Limit") },
                singleLine = true,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val limit = limitInput.toIntOrNull() ?: 10
                onSearch(dateInput, limit)
            },
            enabled = dateInput.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Search Timeline")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(timelineResults, key = { it.id ?: it.content.hashCode().toString() }) { memory ->
                EnhancedMemoryCard(
                    memory = memory,
                    onLongPress = {},
                )
            }

            if (timelineResults.isEmpty() && !isLoading) {
                item(key = "empty") {
                    Text(
                        text = "No timeline results. Enter a date and search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

// -- GraphTab --------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphTab(
    entities: List<GraphEntity>,
    isLoading: Boolean,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (entities.isEmpty() && !isLoading) {
            item(key = "empty") {
                Text(
                    text = "Tap the Graph tab to load entities.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        items(entities, key = { it.id }) { entity ->
            val isExpanded = expandedId == entity.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { expandedId = if (isExpanded) null else entity.id },
                        onLongClick = {},
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entity.name.ifBlank { entity.id },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (entity.type.isNotBlank()) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = entity.type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isExpanded && entity.relations.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            entity.relations.forEach { rel ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = rel.label.ifBlank { "-->" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                                    Text(
                                        text = rel.target,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    if (isExpanded && entity.relations.isEmpty()) {
                        Text(
                            text = "No relations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// -- PendingTab ------------------------------------------------------------

@Composable
private fun PendingTab(
    pendingMemories: List<PendingMemory>,
    isLoading: Boolean,
    onLoadPending: () -> Unit,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
) {
    LaunchedEffect(Unit) {
        onLoadPending()
    }

    if (isLoading && pendingMemories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (pendingMemories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No pending memories",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(pendingMemories, key = { it.id }) { memory ->
            PendingMemoryCard(
                memory = memory,
                onApprove = { onApprove(memory.id) },
                onReject = { onReject(memory.id) },
            )
        }
    }
}

@Composable
private fun PendingMemoryCard(
    memory: PendingMemory,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Source and type chips
            val hasHeader = memory.memoryType != null || memory.source != null
            if (hasHeader) {
                Row(
                    modifier = Modifier.padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    memory.memoryType?.let { type ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    memory.source?.let { src ->
                        Text(
                            text = "from: $src",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Content
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata row
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                memory.importance?.let { imp ->
                    Text(
                        text = "Imp: %.0f%%".format(imp * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                memory.confidence?.let { conf ->
                    Text(
                        text = "Conf: %.0f%%".format(conf * 100),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                memory.createdAt?.let { date ->
                    Text(
                        text = date.take(16),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onReject,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Reject")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onApprove) {
                    Text("Approve")
                }
            }
        }
    }
}

// -- PeekDialog ------------------------------------------------------------

@Composable
private fun PeekDialog(
    peekResult: PeekResult,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(peekResult.name)
                Text(
                    text = "Offset ${peekResult.offset} | ${peekResult.length}/${peekResult.total_length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = peekResult.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

// -- DreamTab ----------------------------------------------------------------

@Composable
private fun DreamTab(dreamStatus: DreamStatus?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (dreamStatus == null) {
            Text(
                text = "Loading dream status...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DreamStatusCard(dream = dreamStatus)
        }
    }
}

@Composable
private fun DreamStatusCard(dream: DreamStatus) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "KAIROS Dream",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                if (dream.active) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                        Text("Phase ${dream.currentPhase + 1}/4")
                    }
                } else {
                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text("Idle", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (dream.active) "In progress: ${dream.phaseName}"
                else dream.lastDreamAt?.let { "Last: $it" } ?: "No dream history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Gate Status",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GateIndicator(
                    icon = Icons.Default.Schedule,
                    label = "Time",
                    met = dream.timeGateMet,
                )
                GateIndicator(
                    icon = Icons.Default.Forum,
                    label = "Session",
                    met = dream.sessionGateMet,
                )
                GateIndicator(
                    icon = Icons.Default.Lock,
                    label = "Unlocked",
                    met = !dream.locked,
                )
            }
        }
    }
}

@Composable
private fun GateIndicator(icon: ImageVector, label: String, met: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (met) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (met) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

// -- SnapshotsTab ------------------------------------------------------------

@Composable
private fun SnapshotsTab(viewModel: BrainViewModel) {
    val snapshots by viewModel.snapshots.collectAsStateWithLifecycle()
    val restoreReport by viewModel.restoreReport.collectAsStateWithLifecycle()
    val busy by viewModel.snapshotBusy.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf("") }
    var restoreTarget by remember { mutableStateOf<BrainSnapshot?>(null) }
    var deleteTarget by remember { mutableStateOf<BrainSnapshot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Create row
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                label = { Text("Snapshot label") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.createSnapshot(newLabel.ifBlank { "manual snapshot" })
                    newLabel = ""
                },
                enabled = !busy,
            ) {
                Text("Create")
            }
        }
        Spacer(Modifier.height(12.dp))

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        if (snapshots.isEmpty() && !busy) {
            Text(
                text = "No snapshots yet. Create one to capture the current brain state " +
                    "(memories, identity, context variables) for disaster recovery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshots, key = { it.id }) { snapshot ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = snapshot.label ?: "Snapshot ${snapshot.id}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${snapshot.memoryCount} memories · " +
                                "${snapshot.identityCount} identity · " +
                                "${snapshot.contextVarCount} context vars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        snapshot.createdAt?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { restoreTarget = snapshot },
                                enabled = !busy,
                            ) { Text("Restore") }
                            TextButton(
                                onClick = { deleteTarget = snapshot },
                                enabled = !busy,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    // Restore confirmation — destructive, spell out exactly what happens
    restoreTarget?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore brain from snapshot?") },
            text = {
                Text(
                    "This REPLACES the live brain with \"${snapshot.label ?: snapshot.id}\" " +
                        "(${snapshot.memoryCount} memories). Every memory added since this " +
                        "snapshot will be soft-deleted. This cannot be undone from the app.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restoreSnapshot(snapshot.id)
                        restoreTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Delete confirmation
    deleteTarget?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete snapshot?") },
            text = { Text("Delete \"${snapshot.label ?: snapshot.id}\"? The live brain is not affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSnapshot(snapshot.id)
                        deleteTarget = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    // Restore report
    restoreReport?.let { report ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissRestoreReport() },
            title = { Text(if (report.restored) "Brain restored" else "Restore failed") },
            text = {
                Text(
                    "${report.memoriesWritten} memories written, " +
                        "${report.identitiesWritten} identity entries, " +
                        "${report.contextVarsWritten} context vars, " +
                        "${report.memoriesSoftDeleted} newer memories soft-deleted.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissRestoreReport() }) { Text("OK") }
            },
        )
    }
}
