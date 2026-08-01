package com.scaso.drclawapp.ui.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * A microphone button that supports two interaction modes:
 *
 * 1. **Hold-to-record**: Press down starts recording, release stops recording.
 * 2. **Tap-to-toggle**: A single tap toggles recording on/off.
 *
 * Displays a microphone icon when idle and a stop icon when recording.
 * The button tint changes to the error color while recording to provide
 * a clear visual indicator.
 *
 * @param isRecording Whether speech recognition is currently active.
 * @param onStartRecording Called when the user begins recording.
 * @param onStopRecording Called when the user ends recording.
 * @param modifier Modifier for the root composable.
 */
@Composable
fun VoiceRecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track whether the current gesture is a press-and-hold (vs. a tap)
    var isHolding by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isRecording) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "voiceButtonColor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (isRecording) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "voiceIconColor",
    )

    IconButton(
        onClick = {
            // Tap-to-toggle: only fires if we did NOT just finish a hold gesture.
            // The hold gesture sets isHolding=true on press and resets on release,
            // but onClick fires after release. We use a flag to distinguish.
            if (!isHolding) {
                if (isRecording) {
                    onStopRecording()
                } else {
                    onStartRecording()
                }
            }
            isHolding = false
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Start recording on press (hold-to-record)
                        isHolding = true
                        if (!isRecording) {
                            onStartRecording()
                        }
                        // Wait for release
                        val released = tryAwaitRelease()
                        if (released && isHolding) {
                            // Release after hold -- stop recording
                            onStopRecording()
                        }
                        isHolding = false
                    },
                )
            },
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "Stop recording" else "Start voice input",
        )
    }
}
