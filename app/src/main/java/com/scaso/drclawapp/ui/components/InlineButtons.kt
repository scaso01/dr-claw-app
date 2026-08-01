package com.scaso.drclawapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A horizontal row of suggestion chips representing inline action buttons.
 * Displayed below a message bubble when the message includes suggested actions.
 *
 * @param actions List of action labels to display as chips
 * @param onActionClick Callback fired when a chip is tapped, with the action label
 * @param modifier Modifier for the LazyRow container
 */
@Composable
fun InlineActionButtons(
    actions: List<String>,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(
            items = actions,
            key = { it },
        ) { action ->
            SuggestionChip(
                onClick = { onActionClick(action) },
                label = {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}
