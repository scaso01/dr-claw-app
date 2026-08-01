package com.scaso.drclawapp.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.Role
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.ui.theme.DrClawTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A minimal transparent activity that serves as the expanded view for
 * the floating chat bubble (Phase 8D).
 *
 * Shows the last few messages and a compact input field. Messages are
 * displayed in a simplified format without the full MessageBubble
 * component to keep the bubble lightweight.
 *
 * This activity must be declared in AndroidManifest.xml with:
 *   android:documentLaunchMode="always"
 *   android:resizeableActivity="true"
 *   android:allowEmbedded="true"
 *
 * The main agent is responsible for adding the manifest entry.
 */
@AndroidEntryPoint
class BubbleActivity : ComponentActivity() {

    @Inject lateinit var chatRepository: ChatRepository

    companion object {
        /** Maximum number of recent messages to display in the bubble. */
        private const val MAX_BUBBLE_MESSAGES = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DrClawTheme {
                BubbleChatScreen(
                    chatRepository = chatRepository,
                    maxMessages = MAX_BUBBLE_MESSAGES,
                )
            }
        }
    }
}

/**
 * Compact chat screen for the bubble expanded view.
 * Shows recent messages and a simple input field.
 */
@Composable
private fun BubbleChatScreen(
    chatRepository: ChatRepository,
    maxMessages: Int,
) {
    val allMessages by chatRepository.messages.collectAsStateWithLifecycle()
    val recentMessages = remember(allMessages) {
        allMessages.takeLast(maxMessages)
    }

    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            // Header
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Dr. CLAW",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            // Message list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true,
            ) {
                items(
                    items = recentMessages.reversed(),
                    key = { it.id },
                ) { message ->
                    BubbleMessageItem(message = message)
                }
            }

            // Compact input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...", style = MaterialTheme.typography.bodySmall) },
                    maxLines = 3,
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                val text = inputText.trim()
                                inputText = ""
                                scope.launch { chatRepository.sendMessage(text) }
                            }
                        },
                    ),
                    shape = MaterialTheme.shapes.large,
                )

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val text = inputText.trim()
                            inputText = ""
                            scope.launch { chatRepository.sendMessage(text) }
                        }
                    },
                    enabled = inputText.isNotBlank(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Simplified message display for the bubble view.
 * Uses minimal styling compared to the full MessageBubble.
 */
@Composable
private fun BubbleMessageItem(message: Message) {
    val isUser = message.role == Role.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            color = containerColor,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = message.content.take(500),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
