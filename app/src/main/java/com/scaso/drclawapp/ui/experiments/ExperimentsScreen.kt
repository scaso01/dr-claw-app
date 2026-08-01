package com.scaso.drclawapp.ui.experiments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.experiments.Experiment
import com.scaso.drclawapp.data.experiments.ExperimentStatus
import com.scaso.drclawapp.data.experiments.MetricPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentsScreen(
    onBack: () -> Unit,
    viewModel: ExperimentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Experiments") },
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
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.experiments.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.error ?: "No experiments running",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.experiments, key = { it.id }) { experiment ->
                        ExperimentCard(
                            experiment = experiment,
                            onAbort = { viewModel.abort(experiment.id) },
                            onPause = { viewModel.pause(experiment.id) },
                            onResume = { viewModel.resume(experiment.id) },
                            onViewResults = { viewModel.loadResults(experiment.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content-only variant for embedding in the System tab (no Scaffold/TopAppBar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentsContent(
    viewModel: ExperimentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (uiState.experiments.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = uiState.error ?: "No experiments running",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.experiments, key = { it.id }) { experiment ->
                    ExperimentCard(
                        experiment = experiment,
                        onAbort = { viewModel.abort(experiment.id) },
                        onPause = { viewModel.pause(experiment.id) },
                        onResume = { viewModel.resume(experiment.id) },
                        onViewResults = { viewModel.loadResults(experiment.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExperimentCard(
    experiment: Experiment,
    onAbort: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onViewResults: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: name + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = experiment.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(experiment.status)
            }

            // Hypothesis
            experiment.hypothesis?.let { hypothesis ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hypothesis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Progress bar
            if (experiment.status == ExperimentStatus.RUNNING || experiment.status == ExperimentStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { experiment.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(experiment.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Sparkline for metrics
            if (experiment.metrics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Sparkline(
                    points = experiment.metrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                )
            }

            // Plan steps (D8 progress view inline)
            if (experiment.steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PlanStepsView(experiment.steps)
            }

            // Decision log (D8)
            if (experiment.decisions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decisions",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                experiment.decisions.takeLast(3).forEach { decision ->
                    Text(
                        text = decision.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Action buttons
            if (experiment.status == ExperimentStatus.RUNNING || experiment.status == ExperimentStatus.PAUSED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (experiment.status == ExperimentStatus.RUNNING) {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", modifier = Modifier.size(20.dp))
                        }
                    }
                    if (experiment.status == ExperimentStatus.PAUSED) {
                        IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onAbort, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Abort",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Error display
            experiment.error?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ExperimentStatus) {
    val (text, color) = when (status) {
        ExperimentStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.outline
        ExperimentStatus.RUNNING -> "Running" to Color(0xFF4CAF50)
        ExperimentStatus.PAUSED -> "Paused" to Color(0xFFFF9800)
        ExperimentStatus.COMPLETED -> "Done" to MaterialTheme.colorScheme.primary
        ExperimentStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
        ExperimentStatus.ABORTED -> "Aborted" to MaterialTheme.colorScheme.outline
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PlanStepsView(steps: List<com.scaso.drclawapp.data.experiments.PlanStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Plan",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when {
                    step.completed -> "✓"
                    step.active -> "►"
                    else -> "○"
                }
                val color = when {
                    step.completed -> Color(0xFF4CAF50)
                    step.active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(text = icon, color = color, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (step.completed || step.active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun Sparkline(
    points: List<MetricPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.size < 2) return
    val values = points.map { it.value }
    val minVal = values.min()
    val maxVal = values.max()
    val range = (maxVal - minVal).coerceAtLeast(0.001f)

    Canvas(modifier = modifier) {
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        val path = Path()

        points.forEachIndexed { i, point ->
            val x = i * stepX
            val y = size.height - ((point.value - minVal) / range) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))

        // Draw dots at each point
        points.forEachIndexed { i, point ->
            val x = i * stepX
            val y = size.height - ((point.value - minVal) / range) * size.height
            drawCircle(color, radius = 3.dp.toPx(), center = Offset(x, y))
        }
    }
}
