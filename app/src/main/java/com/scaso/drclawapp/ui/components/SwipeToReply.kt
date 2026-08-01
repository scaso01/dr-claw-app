package com.scaso.drclawapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Wraps message bubble content and adds a swipe-right-to-reply gesture.
 * Swiping right reveals a reply icon; releasing past the threshold triggers [onReply].
 *
 * @param onReply Callback fired when the user completes the swipe gesture
 * @param content The composable content to wrap (typically a message bubble)
 */
@Composable
fun SwipeToReply(
    onReply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val triggerThresholdPx = with(density) { 72.dp.toPx() }
    val maxDragPx = with(density) { 100.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var hasTriggered by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = if (offsetX == 0f) 200 else 0),
        label = "swipeOffset",
    )

    // Progress from 0 to 1 for the reply icon reveal
    val revealProgress = (animatedOffsetX / triggerThresholdPx).coerceIn(0f, 1f)

    Box {
        // Reply icon revealed behind the message
        if (revealProgress > 0f) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Reply",
                tint = if (hasTriggered) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .alpha(revealProgress),
            )
        }

        // Message content that slides right
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasTriggered = false
                        },
                        onDragEnd = {
                            if (offsetX >= triggerThresholdPx) {
                                onReply()
                            }
                            offsetX = 0f
                            hasTriggered = false
                        },
                        onDragCancel = {
                            offsetX = 0f
                            hasTriggered = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX + dragAmount).coerceIn(0f, maxDragPx)
                            offsetX = newOffset
                            if (newOffset >= triggerThresholdPx && !hasTriggered) {
                                hasTriggered = true
                            } else if (newOffset < triggerThresholdPx) {
                                hasTriggered = false
                            }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
