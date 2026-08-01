package com.scaso.drclawapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Default quick actions shown when the message list is empty or when
 * configured in Settings.
 */
val DefaultQuickActions: List<String> = listOf(
    "Summarize",
    "Explain",
    "Translate",
    "Code review",
)

/**
 * A horizontal scrollable row of suggestion chips displayed above the
 * input bar (Phase 8C).
 *
 * Each chip represents a quick action. Tapping a chip calls [onActionClick]
 * with the action text, which the main agent can use to pre-fill the input
 * field or send directly.
 *
 * Typically shown when:
 * - The message list is empty (first session / new conversation).
 * - The user has enabled "Show quick actions" in Settings.
 *
 * @param actions List of action labels to display as chips.
 * @param onActionClick Callback invoked when a chip is tapped.
 * @param modifier Modifier applied to the LazyRow container.
 */
@Composable
fun QuickActionsBar(
    actions: List<String>,
    onActionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = actions,
            key = { it },
        ) { action ->
            QuickActionChip(
                label = action,
                onClick = { onActionClick(action) },
            )
        }
    }
}

/**
 * Individual suggestion chip for a quick action.
 */
@Composable
private fun QuickActionChip(
    label: String,
    onClick: () -> Unit,
) {
    SuggestionChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
    )
}
