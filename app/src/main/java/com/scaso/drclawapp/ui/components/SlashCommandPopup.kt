package com.scaso.drclawapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.model.BuiltInSlashCommands
import com.scaso.drclawapp.data.model.SlashCommand
import com.scaso.drclawapp.data.model.SlashIcon

/**
 * A popup card that appears above the input field when the user types "/".
 * Shows matching slash commands filtered by the current input text.
 *
 * @param inputText The current text in the input field (e.g., "/he")
 * @param onCommandSelected Called when the user taps a command; the full command name is passed
 * @param modifier Modifier for the popup container
 * @param commands The list of available slash commands; defaults to built-in commands
 */
@Composable
fun SlashCommandPopup(
    inputText: String,
    onCommandSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    commands: List<SlashCommand> = BuiltInSlashCommands,
) {
    val filteredCommands by remember(inputText, commands) {
        derivedStateOf {
            if (!inputText.startsWith("/")) {
                emptyList()
            } else {
                val query = inputText.lowercase()
                commands.filter { it.name.lowercase().startsWith(query) }
            }
        }
    }

    if (filteredCommands.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .heightIn(max = 200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        LazyColumn {
            items(
                items = filteredCommands,
                key = { it.name },
            ) { command ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = command.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = command.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = command.icon.toImageVector(),
                            contentDescription = command.name,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                    modifier = Modifier.clickable {
                        onCommandSelected(command.name)
                    },
                )
            }
        }
    }
}

/** Maps the platform-neutral [SlashIcon] to a concrete Compose icon at the UI layer. */
private fun SlashIcon.toImageVector(): ImageVector = when (this) {
    SlashIcon.ADD -> Icons.Default.Add
    SlashIcon.DELETE -> Icons.Default.Delete
    SlashIcon.INFO -> Icons.Default.Info
    SlashIcon.BUILD -> Icons.Default.Build
}
