package com.scaso.drclawapp.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Full-screen voice conversation screen with half-duplex and full-duplex modes.
 *
 * Half-duplex: press-to-talk with sequential record/send/receive/play.
 * Full-duplex: continuous bidirectional audio via Pipecat server with
 * visual indicators for listening, AI thinking, AI speaking, and interrupts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    voiceManager: VoiceConversationManager,
    onNavigateBack: () -> Unit,
    fullDuplexAvailable: Boolean = false,
    onToggleMode: () -> Unit = {},
) {
    val voiceState by voiceManager.voiceState.collectAsStateWithLifecycle()
    val voiceMode by voiceManager.voiceMode.collectAsStateWithLifecycle()
    val fullDuplexState by voiceManager.fullDuplexState.collectAsStateWithLifecycle()
    val transcribedText by voiceManager.transcribedText.collectAsStateWithLifecycle()
    val partialText by voiceManager.partialText.collectAsStateWithLifecycle()
    val responseText by voiceManager.responseText.collectAsStateWithLifecycle()
    val errorMessage by voiceManager.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val hasPermission = rememberSaveable { mutableStateOf(false) }

    val isFullDuplex = voiceMode == VoiceMode.FULL_DUPLEX

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission.value = granted
        if (granted) {
            voiceManager.startListening()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        hasPermission.value = androidx.core.content.ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceManager.destroy()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = androidx.compose.material3.SnackbarDuration.Long)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isFullDuplex) "Voice Mode (Full-Duplex)" else "Voice Mode"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        voiceManager.cancel()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (fullDuplexAvailable) {
                        IconButton(onClick = onToggleMode) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Toggle voice mode",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Full-duplex status indicators
            if (isFullDuplex) {
                FullDuplexStatusBar(fullDuplexState = fullDuplexState)
            }

            // Status text area (top section)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VoiceStatusText(
                    voiceState = voiceState,
                    isFullDuplex = isFullDuplex,
                    fullDuplexState = fullDuplexState,
                )

                Spacer(Modifier.height(24.dp))

                // Show transcribed text (partial or final)
                val displayText = when {
                    isFullDuplex && transcribedText.isNotBlank() -> transcribedText
                    voiceState == VoiceState.LISTENING -> partialText.ifBlank { "Listening..." }
                    voiceState == VoiceState.SENDING -> transcribedText
                    voiceState in listOf(VoiceState.WAITING, VoiceState.PLAYING) -> transcribedText
                    voiceState == VoiceState.ERROR -> transcribedText.ifBlank { "Error occurred" }
                    else -> ""
                }

                if (displayText.isNotBlank()) {
                    TranscriptionCard(
                        label = "You said:",
                        text = displayText,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Show response text
                if (responseText.isNotBlank()) {
                    TranscriptionCard(
                        label = "Dr. CLAW:",
                        text = responseText,
                        isInterrupted = fullDuplexState.interrupted,
                    )
                }
            }

            // Talk button (bottom section)
            Spacer(Modifier.height(24.dp))

            if (isFullDuplex) {
                FullDuplexButton(
                    voiceState = voiceState,
                    fullDuplexState = fullDuplexState,
                    onStart = {
                        if (hasPermission.value) {
                            voiceManager.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStop = { voiceManager.stopListening() },
                )
            } else {
                TalkButton(
                    voiceState = voiceState,
                    onTalkStart = {
                        if (hasPermission.value) {
                            voiceManager.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onTalkStop = { voiceManager.stopListening() },
                    onCancel = { voiceManager.cancel() },
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * Status bar showing full-duplex connection indicators.
 */
@Composable
private fun FullDuplexStatusBar(fullDuplexState: FullDuplexState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            active = fullDuplexState.connected,
            label = "Connected",
            activeColor = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        StatusDot(
            active = fullDuplexState.micActive,
            label = "Mic",
            activeColor = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(16.dp))
        StatusDot(
            active = fullDuplexState.userSpeaking,
            label = "You",
            activeColor = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(16.dp))
        StatusDot(
            active = fullDuplexState.botSpeaking,
            label = "AI",
            activeColor = MaterialTheme.colorScheme.secondary,
        )
        if (fullDuplexState.interrupted) {
            Spacer(Modifier.width(16.dp))
            StatusDot(
                active = true,
                label = "Interrupted",
                activeColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StatusDot(
    active: Boolean,
    label: String,
    activeColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (active) activeColor
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) activeColor
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shows the current pipeline state as a status label.
 */
@Composable
private fun VoiceStatusText(
    voiceState: VoiceState,
    isFullDuplex: Boolean = false,
    fullDuplexState: FullDuplexState = FullDuplexState(),
) {
    val statusText = when {
        isFullDuplex && fullDuplexState.interrupted -> "Interrupted -- listening..."
        isFullDuplex && fullDuplexState.botSpeaking && fullDuplexState.userSpeaking -> "Both speaking (interrupt detected)"
        isFullDuplex && fullDuplexState.botSpeaking -> "AI is speaking..."
        isFullDuplex && fullDuplexState.userSpeaking -> "You are speaking..."
        isFullDuplex && fullDuplexState.micActive && voiceState == VoiceState.WAITING -> "AI is thinking..."
        isFullDuplex && fullDuplexState.micActive -> "Listening (full-duplex)..."
        voiceState == VoiceState.IDLE && isFullDuplex -> "Tap to start full-duplex conversation"
        voiceState == VoiceState.IDLE -> "Tap the microphone to start"
        voiceState == VoiceState.LISTENING -> "Listening..."
        voiceState == VoiceState.SENDING -> "Sending..."
        voiceState == VoiceState.WAITING -> "Thinking..."
        voiceState == VoiceState.PLAYING -> "Speaking..."
        voiceState == VoiceState.ERROR -> "Something went wrong"
        else -> ""
    }

    val statusColor = when {
        fullDuplexState.interrupted -> MaterialTheme.colorScheme.error
        fullDuplexState.botSpeaking -> MaterialTheme.colorScheme.secondary
        fullDuplexState.userSpeaking -> MaterialTheme.colorScheme.tertiary
        voiceState == VoiceState.ERROR -> MaterialTheme.colorScheme.error
        voiceState == VoiceState.LISTENING -> MaterialTheme.colorScheme.primary
        voiceState == VoiceState.PLAYING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.titleMedium,
        color = statusColor,
        textAlign = TextAlign.Center,
    )
}

/**
 * A card that displays transcribed or response text.
 */
@Composable
private fun TranscriptionCard(
    label: String,
    text: String,
    isInterrupted: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (isInterrupted) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "(interrupted)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Full-duplex conversation button: start/stop streaming.
 */
@Composable
private fun FullDuplexButton(
    voiceState: VoiceState,
    fullDuplexState: FullDuplexState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val isActive = fullDuplexState.micActive

    Box(
        contentAlignment = Alignment.Center,
    ) {
        // Pulsing ring for active conversation
        if (isActive) {
            if (fullDuplexState.botSpeaking) {
                PulsingRing(color = MaterialTheme.colorScheme.secondary)
            } else if (fullDuplexState.userSpeaking) {
                PulsingRing(color = MaterialTheme.colorScheme.primary)
            } else {
                // Subtle idle pulse
                PulsingRing(
                    color = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }

        FilledIconButton(
            onClick = {
                if (isActive) onStop() else onStart()
            },
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (isActive) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
            ),
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PhoneInTalk,
                contentDescription = if (isActive) "End conversation" else "Start conversation",
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * Large circular talk button with pulsing animation during recording.
 * (Half-duplex mode -- unchanged from original)
 */
@Composable
private fun TalkButton(
    voiceState: VoiceState,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val isActive = voiceState == VoiceState.LISTENING
    val isProcessing = voiceState in listOf(
        VoiceState.SENDING,
        VoiceState.WAITING,
        VoiceState.PLAYING,
    )

    Box(
        contentAlignment = Alignment.Center,
    ) {
        if (isActive) {
            PulsingRing()
        }

        FilledIconButton(
            onClick = {
                when (voiceState) {
                    VoiceState.IDLE, VoiceState.ERROR -> onTalkStart()
                    VoiceState.LISTENING -> onTalkStop()
                    VoiceState.SENDING,
                    VoiceState.WAITING,
                    VoiceState.PLAYING -> onCancel()
                }
            },
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when {
                    isActive -> MaterialTheme.colorScheme.error
                    isProcessing -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                contentColor = when {
                    isActive -> MaterialTheme.colorScheme.onError
                    isProcessing -> MaterialTheme.colorScheme.onTertiary
                    else -> MaterialTheme.colorScheme.onPrimary
                },
            ),
        ) {
            val icon = when (voiceState) {
                VoiceState.IDLE -> Icons.Default.Mic
                VoiceState.LISTENING -> Icons.Default.Stop
                VoiceState.ERROR -> Icons.Default.MicOff
                VoiceState.SENDING,
                VoiceState.WAITING,
                VoiceState.PLAYING -> Icons.Default.Close
            }

            val description = when (voiceState) {
                VoiceState.IDLE -> "Start talking"
                VoiceState.LISTENING -> "Stop recording"
                VoiceState.ERROR -> "Try again"
                else -> "Cancel"
            }

            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/**
 * Pulsing ring animation behind the talk button.
 */
@Composable
private fun PulsingRing(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha",
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .background(
                color = color,
                shape = CircleShape,
            ),
    )
}
