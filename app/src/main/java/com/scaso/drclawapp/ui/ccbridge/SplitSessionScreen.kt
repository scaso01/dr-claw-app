package com.scaso.drclawapp.ui.ccbridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

/**
 * Side-by-side split view for two CC sessions in tablet/landscape mode.
 *
 * Each pane is backed by its own embedded NavHost so that Hilt creates
 * independent [AttachedSessionViewModel] instances, each with their own
 * [SavedStateHandle] containing the correct sessionId and backend args.
 */
@Composable
fun SplitSessionScreen(
    leftSessionId: String,
    leftBackend: String,
    rightSessionId: String,
    rightBackend: String,
    onNavigateBack: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left pane
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            SessionPane(
                sessionId = leftSessionId,
                backend = leftBackend,
                onNavigateBack = onNavigateBack,
            )
        }

        // Divider
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Right pane
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            SessionPane(
                sessionId = rightSessionId,
                backend = rightBackend,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}

/**
 * A single pane hosting an embedded NavHost that navigates directly to
 * the attached session route. This gives each pane its own backstack
 * entry, allowing Hilt to create a separate ViewModel with the correct
 * SavedStateHandle arguments.
 */
@Composable
private fun SessionPane(
    sessionId: String,
    backend: String,
    onNavigateBack: () -> Unit,
) {
    val paneNavController = rememberNavController()
    val route = "pane_session/{sessionId}?backend={backend}"
    val startDest = "pane_session/$sessionId?backend=$backend"

    NavHost(
        navController = paneNavController,
        startDestination = startDest,
    ) {
        composable(
            route = route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("backend") {
                    type = NavType.StringType
                    defaultValue = "daemon"
                },
            ),
        ) {
            val vm: AttachedSessionViewModel = hiltViewModel()
            AttachedSessionContent(
                viewModel = vm,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}
