package com.scaso.drclawapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.ui.theme.ConnectedGreen
import com.scaso.drclawapp.ui.theme.ConnectingYellow
import com.scaso.drclawapp.ui.theme.DisconnectedRed

/**
 * Persistent status bar between the app bar and messages.
 * Shows: model chip, context progress, connection dot, session cost.
 */
@Composable
fun ChatStatusBar(
    modelName: String?,
    contextPercent: Float,
    connectionState: ConnectionState,
    sessionCost: Double?,
    inputTokens: Long,
    outputTokens: Long,
    compactionCount: Int = 0,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val progressColor by animateColorAsState(
        targetValue = when {
            contextPercent > 0.90f -> MaterialTheme.colorScheme.error
            contextPercent > 0.75f -> Color(0xFFFF9800) // orange
            contextPercent > 0.50f -> Color(0xFFFFC107) // amber
            else -> MaterialTheme.colorScheme.primary
        },
        label = "progressColor",
    )

    val dotColor = when (connectionState) {
        is ConnectionState.Connected -> ConnectedGreen
        is ConnectionState.Connecting, is ConnectionState.Authenticating -> ConnectingYellow
        is ConnectionState.Disconnected, is ConnectionState.Error, is ConnectionState.AuthFailed -> DisconnectedRed
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Model chip
            if (!modelName.isNullOrBlank()) {
                SuggestionChip(
                    onClick = onClick,
                    label = {
                        Text(
                            text = formatModelName(modelName),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }

            // Context progress bar + percentage label
            LinearProgressIndicator(
                progress = { contextPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            val pctInt = (contextPercent * 100).toInt()
            if (pctInt > 0) {
                Text(
                    text = "$pctInt%",
                    style = MaterialTheme.typography.labelSmall,
                    color = progressColor,
                )
            }
            if (compactionCount > 0) {
                Text(
                    text = "${compactionCount}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Connection dot
            Surface(
                shape = CircleShape,
                color = dotColor,
                modifier = Modifier.size(8.dp),
                content = {},
            )

            // Cost text
            val totalTokens = inputTokens + outputTokens
            if (totalTokens > 0) {
                val costText = if (sessionCost != null && sessionCost > 0.0) {
                    formatCost(sessionCost)
                } else {
                    formatTokenCount(totalTokens)
                }
                Text(
                    text = costText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Trailing slot (e.g. the Cloud/Local backend toggle)
            trailing()
        }
    }
}

private fun formatModelName(name: String): String {
    val lower = name.lowercase()
    return when {
        "opus" in lower -> "Opus"
        "sonnet" in lower -> "Sonnet"
        "haiku" in lower -> "Haiku"
        else -> name.take(12)
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fk", tokens / 1_000.0)
    else -> "${tokens}t"
}

private fun formatCost(cost: Double): String =
    if (cost < 0.01) String.format("$%.3f", cost)
    else String.format("$%.2f", cost)
