package com.scaso.drclawapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.scaso.drclawapp.data.model.ActiveToolCall
import kotlinx.coroutines.delay

/**
 * Activity status bar shown between the message list and input bar.
 *
 * Phase 10 (v0.6.0): Displays a pulsing dot and the current cc-path tool activity text.
 *
 * Phase 15 extension: also accepts [activeTools] (native-path Set<ActiveToolCall>).
 * When [activeTools] is non-empty, a second row shows "Running: tool1, tool2 (Ns)"
 * where N is the elapsed seconds since the earliest [ActiveToolCall.startedAt].
 * A 1-second ticker drives the elapsed counter via [LaunchedEffect].
 *
 * Both rows render independently — if both [activity] and [activeTools] are present,
 * both rows are shown stacked inside the same Surface.
 */
@Composable
fun ActivityStatusBar(
    activity: String? = null,
    activeTools: Set<ActiveToolCall> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val hasActivity = !activity.isNullOrEmpty()
    val hasActiveTools = activeTools.isNotEmpty()

    if (!hasActivity && !hasActiveTools) return

    val transition = rememberInfiniteTransition(label = "activity_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "activityAlpha",
    )

    // 1-second ticker for elapsed time — only needed when native tools are running
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (hasActiveTools) {
        LaunchedEffect(activeTools) {
            while (true) {
                delay(1_000L)
                nowMs = System.currentTimeMillis()
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            // Row 1: cc-path activity text (legacy / Phase 10)
            if (hasActivity) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = activity!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // Row 2: native-path active tools (Phase 15)
            if (hasActiveTools) {
                val earliestStart = activeTools.minOf { it.startedAt }
                val elapsedSeconds = ((nowMs - earliestStart) / 1_000L).coerceAtLeast(0L)
                val label = if (activeTools.size == 1) {
                    "Running ${activeTools.first().toolName}… (${elapsedSeconds}s)"
                } else {
                    "Running ${activeTools.size} tools… (${elapsedSeconds}s)"
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = alpha),
                    ) {}
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
