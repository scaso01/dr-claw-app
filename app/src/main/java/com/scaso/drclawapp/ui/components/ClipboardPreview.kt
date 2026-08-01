package com.scaso.drclawapp.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Detects clipboard content when the app comes to the foreground and
 * shows a preview card above the input bar (Phase 8F).
 *
 * The card shows a truncated preview of the clipboard text with two
 * actions: "Send" (sends clipboard content as a message) and a dismiss
 * button (hides the card).
 *
 * The preview is only shown when:
 * - The app gains focus (ON_RESUME)
 * - The clipboard contains text content
 * - The user hasn't already dismissed this particular clipboard content
 *
 * @param onSendClipboard Called with the clipboard text when the user
 *                        taps "Send".
 * @param modifier Modifier for the card container.
 */
@Composable
fun ClipboardPreview(
    onSendClipboard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var clipboardText by remember { mutableStateOf<String?>(null) }
    var isDismissed by remember { mutableStateOf(false) }
    var lastDismissedContent by remember { mutableStateOf<String?>(null) }
    // Skip the initial ON_RESUME (app launch / composable creation).
    // Only show the clipboard card after a real foreground return (ON_PAUSE -> ON_RESUME).
    var hasBeenPaused by remember { mutableStateOf(false) }

    // Check clipboard when app resumes (after having been paused)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasBeenPaused = true
                Lifecycle.Event.ON_RESUME -> {
                    if (!hasBeenPaused) return@LifecycleEventObserver
                    val text = getClipboardText(context)
                    if (text != null && text != lastDismissedContent) {
                        clipboardText = text
                        isDismissed = false
                    } else {
                        clipboardText = null
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // React immediately when clipboard changes (e.g. user clears it while app is open)
    DisposableEffect(Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val listener = ClipboardManager.OnPrimaryClipChangedListener {
            val text = getClipboardText(context)
            if (text == null || text == lastDismissedContent) {
                clipboardText = null
            } else if (text != clipboardText) {
                clipboardText = text
                isDismissed = false
            }
        }
        clipboard.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboard.removePrimaryClipChangedListener(listener)
        }
    }

    val showPreview = clipboardText != null && !isDismissed

    AnimatedVisibility(
        visible = showPreview,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        clipboardText?.let { text ->
            ClipboardPreviewCard(
                text = text,
                onSend = {
                    onSendClipboard(text)
                    isDismissed = true
                    lastDismissedContent = text
                    clipboardText = null
                },
                onDismiss = {
                    isDismissed = true
                    lastDismissedContent = text
                    clipboardText = null
                },
                modifier = modifier,
            )
        }
    }
}

/**
 * The visual card component showing clipboard content preview.
 */
@Composable
private fun ClipboardPreviewCard(
    text: String,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Send clipboard content?",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    text = text.take(120).replace("\n", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(onClick = onSend) {
                Text(
                    text = "Send",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss clipboard preview",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * Retrieves the current clipboard text content, or null if the clipboard
 * is empty or does not contain text.
 */
private fun getClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString()
    return text?.takeIf {
        it.isNotBlank() && it.length > 1
            && !it.startsWith("Error:")
            && !it.contains("API error:")
    }
}
