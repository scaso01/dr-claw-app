package com.scaso.drclawapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.model.ToolStatus
import com.scaso.drclawapp.ui.theme.ToolCardSurface

/**
 * Collapsible card representing a tool invocation.
 *
 * Phase 15 addition: [status] param controls visual state.
 * - [ToolStatus.Running] → spinner in the header row
 * - [ToolStatus.Error]   → red/error border tint
 * - [ToolStatus.Pending] → muted/ghost appearance
 * - [ToolStatus.Done]    → default appearance (back-compat default)
 *
 * [collapseByDefault] mirrors the global per-session toggle (v1.1.3):
 * changing it resets the individual override.
 */
@Composable
fun ToolUseCard(
    toolName: String,
    inputJson: String,
    modifier: Modifier = Modifier,
    status: ToolStatus = ToolStatus.Done,
    collapseByDefault: Boolean = true,
) {
    // Changing collapseByDefault resets the individual override (v1.1.3 pattern)
    var expanded by remember(collapseByDefault) { mutableStateOf(!collapseByDefault) }

    val containerColor = when (status) {
        ToolStatus.Error   -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        ToolStatus.Pending -> ToolCardSurface.copy(alpha = 0.4f)
        else               -> ToolCardSurface
    }

    val borderStroke: BorderStroke? = when (status) {
        ToolStatus.Error   -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        else               -> null
    }

    val contentAlpha = if (status == ToolStatus.Pending) 0.5f else 1f

    val cardModifier = modifier
        .fillMaxWidth()
        .then(
            if (borderStroke != null) Modifier.border(borderStroke, MaterialTheme.shapes.small)
            else Modifier
        )

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Status indicator: spinner for Running, wrench icon otherwise
                if (status == ToolStatus.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (status) {
                            ToolStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                        },
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Tool: $toolName",
                    style = MaterialTheme.typography.labelMedium,
                    color = when (status) {
                        ToolStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Status label badge
                if (status != ToolStatus.Done) {
                    Text(
                        text = when (status) {
                            ToolStatus.Running -> "running"
                            ToolStatus.Pending -> "pending"
                            ToolStatus.Error   -> "error"
                            ToolStatus.Done    -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            ToolStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    text = inputJson.take(500),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f * contentAlpha),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
