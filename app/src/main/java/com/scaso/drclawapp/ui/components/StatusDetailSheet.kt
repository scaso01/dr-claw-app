package com.scaso.drclawapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.scaso.drclawapp.data.websocket.PromptStatsResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet showing detailed session status.
 * Triggered by tapping the persistent status bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDetailSheet(
    modelName: String?,
    inputTokens: Long,
    outputTokens: Long,
    contextPercent: Float,
    sessionStartTime: Long?,
    onNewConversation: () -> Unit,
    onDismiss: () -> Unit,
    promptStats: PromptStatsResult? = null,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Session Details",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Model name
            DetailRow(label = "Model", value = modelName ?: "Unknown")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Token breakdown
            DetailRow(label = "Input tokens", value = formatTokens(inputTokens))
            DetailRow(label = "Output tokens", value = formatTokens(outputTokens))
            DetailRow(
                label = "Total tokens",
                value = formatTokens(inputTokens + outputTokens),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Estimated cost — local llama-server models are free; only Claude
            // cloud models get the per-token rate table.
            val costText = if (isLocalModel(modelName)) {
                "Free (local)"
            } else {
                formatCostValue(estimateCost(modelName, inputTokens, outputTokens))
            }
            DetailRow(label = "Estimated cost", value = costText)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Session duration
            val durationText = if (sessionStartTime != null && sessionStartTime > 0) {
                formatDuration(System.currentTimeMillis() - sessionStartTime)
            } else {
                "Unknown"
            }
            DetailRow(label = "Session duration", value = durationText)

            // Context capacity
            val contextText = "${(contextPercent * 100).toInt()}%"
            DetailRow(label = "Context capacity", value = contextText)

            // Prompt breakdown: what the system prompt costs per layer on every
            // turn (identity, memories, context vars, …). Explains why a fresh
            // chat already starts with context consumed.
            if (promptStats != null && promptStats.layers.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Prompt breakdown (per turn)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                promptStats.layers.forEach { layer ->
                    DetailRow(
                        label = layer.name,
                        value = "~${formatTokens(layer.estimatedTokens.toLong())} tok",
                    )
                }
                DetailRow(
                    label = "System prompt total",
                    value = "~${formatTokens(promptStats.estimatedTokens.toLong())} tok",
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // New conversation button
            Button(
                onClick = {
                    onNewConversation()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("New conversation")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
    else -> "$tokens"
}

private fun isLocalModel(modelName: String?): Boolean {
    val name = modelName?.lowercase() ?: return false
    return listOf("qwen", "gemma", "gpt-oss", "devstral", "llama", "ornith")
        .any { it in name }
}

private fun estimateCost(modelName: String?, inputTokens: Long, outputTokens: Long): Double {
    // Approximate pricing per million tokens (as of 2025)
    val (inputRate, outputRate) = when {
        modelName == null -> Pair(3.0, 15.0)
        "opus" in modelName.lowercase() -> Pair(15.0, 75.0)
        "sonnet" in modelName.lowercase() -> Pair(3.0, 15.0)
        "haiku" in modelName.lowercase() -> Pair(0.25, 1.25)
        else -> Pair(3.0, 15.0)
    }
    return (inputTokens * inputRate + outputTokens * outputRate) / 1_000_000.0
}

private fun formatCostValue(cost: Double): String =
    if (cost < 0.01) String.format("$%.4f", cost)
    else String.format("$%.2f", cost)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
