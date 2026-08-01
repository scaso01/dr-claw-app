package com.scaso.drclawapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Animated "thinking" indicator with 3 bouncing dots.
 * Shown when the app is waiting for the assistant to start streaming a reply.
 * Each dot bounces with a staggered delay to create a wave effect.
 */
@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    startTimeMs: Long = 0L,
) {
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant

    // Elapsed timer: tick every second after 3s
    val elapsed = remember { mutableLongStateOf(0L) }
    LaunchedEffect(startTimeMs) {
        if (startTimeMs > 0L) {
            while (true) {
                elapsed.longValue = (System.currentTimeMillis() - startTimeMs) / 1000L
                delay(1000L)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Thinking",
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor,
                )
                repeat(3) { index ->
                    BouncingDot(
                        delayMillis = index * 160,
                        color = dotColor,
                    )
                }
                // Show elapsed seconds after 3s
                if (startTimeMs > 0L && elapsed.longValue >= 3) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${elapsed.longValue}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BouncingDot(
    delayMillis: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_$delayMillis")

    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0f at delayMillis
                -6f at delayMillis + 200
                0f at delayMillis + 400
                0f at 1200
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "dotOffset_$delayMillis",
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .offset(y = offsetY.dp)
            .clip(CircleShape)
            .background(color),
    )
}
