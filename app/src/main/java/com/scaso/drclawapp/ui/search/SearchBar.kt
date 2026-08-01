package com.scaso.drclawapp.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * A search bar composable that replaces the normal TopAppBar title area
 * when search mode is active (Phase 8A).
 *
 * Usage: Place this inside the TopAppBar's `title` slot, or swap between
 * this and the normal title based on [isSearchActive].
 *
 * @param isSearchActive Whether search mode is currently active.
 * @param query The current search query text.
 * @param onQueryChange Called when the user types in the search field.
 * @param onClose Called when the user dismisses search mode.
 * @param resultCount Number of matching results to display as a hint.
 * @param modifier Modifier for the search bar container.
 */
@Composable
fun SearchBar(
    isSearchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    resultCount: Int,
    searchMode: SearchMode = SearchMode.IN_CHAT,
    onToggleMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isSearchActive,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
    ) {
        SearchBarContent(
            query = query,
            onQueryChange = onQueryChange,
            onClose = onClose,
            resultCount = resultCount,
            searchMode = searchMode,
            onToggleMode = onToggleMode,
            modifier = modifier,
        )
    }
}

/**
 * Internal content of the search bar. Separated to keep the animation
 * wrapper clean.
 */
@Composable
private fun SearchBarContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    resultCount: Int,
    searchMode: SearchMode = SearchMode.IN_CHAT,
    onToggleMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the text field when search opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleMode) {
            Icon(
                imageVector = if (searchMode == SearchMode.GLOBAL) Icons.Default.TravelExplore
                    else Icons.Default.Search,
                contentDescription = if (searchMode == SearchMode.GLOBAL) "Global search" else "Chat search",
                tint = if (searchMode == SearchMode.GLOBAL) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(8.dp))

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = { Text(if (searchMode == SearchMode.GLOBAL) "Search all conversations..." else "Search messages...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() },
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        // Show result count when query is non-empty
        if (query.isNotBlank()) {
            Text(
                text = "$resultCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }

        IconButton(onClick = {
            keyboardController?.hide()
            onClose()
        }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close search",
            )
        }
    }
}

/**
 * A search icon button intended for the TopAppBar `actions` slot.
 * Tapping it activates search mode.
 *
 * @param onClick Called when the search icon is tapped.
 */
@Composable
fun SearchIconButton(
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search messages",
        )
    }
}

/**
 * A TopAppBar that switches between normal title mode and search mode.
 *
 * This is a convenience composable that combines the standard TopAppBar
 * with the search bar toggle. The main agent can use this directly, or
 * compose [SearchBar] and [SearchIconButton] manually for finer control.
 *
 * @param isSearchActive Whether search mode is active.
 * @param searchQuery The current search text.
 * @param searchResultCount Number of matching messages.
 * @param onSearchQueryChange Called as the user types.
 * @param onSearchToggle Called to toggle search mode on/off.
 * @param onSearchClose Called to close search mode.
 * @param title The normal title content (shown when search is inactive).
 * @param navigationIcon The navigation icon composable.
 * @param extraActions Additional action buttons beyond the search button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableTopAppBar(
    isSearchActive: Boolean,
    searchQuery: String,
    searchResultCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchClose: () -> Unit,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    extraActions: @Composable () -> Unit = {},
    searchMode: SearchMode = SearchMode.IN_CHAT,
    onToggleSearchMode: () -> Unit = {},
) {
    TopAppBar(
        navigationIcon = {
            if (!isSearchActive) {
                navigationIcon()
            }
        },
        title = {
            if (isSearchActive) {
                SearchBar(
                    isSearchActive = true,
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onSearchClose,
                    resultCount = searchResultCount,
                    searchMode = searchMode,
                    onToggleMode = onToggleSearchMode,
                )
            } else {
                title()
            }
        },
        actions = {
            if (!isSearchActive) {
                SearchIconButton(onClick = onSearchToggle)
                extraActions()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
