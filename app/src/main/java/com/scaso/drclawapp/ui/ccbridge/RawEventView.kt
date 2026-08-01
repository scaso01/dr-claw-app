package com.scaso.drclawapp.ui.ccbridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A raw event entry captured from the cc-bridge WebSocket.
 */
data class RawEvent(
    val timestamp: Long,
    val type: String,
    val sessionId: String,
    val data: String,
)

/**
 * Full-screen composable that shows a timestamped, structured event log
 * from the cc-bridge WebSocket. Monospace font, auto-scrolls to bottom,
 * color-coded by event type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawEventView(
    events: List<RawEvent>,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    // Auto-scroll to bottom when new events arrive
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Raw Events",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1A1A2E)),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(events, key = { "${it.timestamp}-${it.type}-${it.data.hashCode()}" }) { event ->
                val color = eventTypeColor(event.type)
                val formattedTime = timeFormat.format(Date(event.timestamp))
                Text(
                    text = "[$formattedTime] ${event.type}: ${event.data}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                )
            }
        }
    }
}

/**
 * Returns a color for the given event type string.
 * stream=green, status=blue, error=red, permission=yellow, other=gray.
 */
private fun eventTypeColor(type: String): Color {
    return when {
        type.contains("stream", ignoreCase = true) -> Color(0xFF4CAF50)
        type.contains("status", ignoreCase = true) -> Color(0xFF2196F3)
        type.contains("error", ignoreCase = true) -> Color(0xFFF44336)
        type.contains("permission", ignoreCase = true) -> Color(0xFFFFC107)
        type.contains("init", ignoreCase = true) -> Color(0xFF2196F3)
        type.contains("result", ignoreCase = true) -> Color(0xFF4CAF50)
        type.contains("closed", ignoreCase = true) -> Color(0xFFF44336)
        type.contains("connection", ignoreCase = true) -> Color(0xFF9E9E9E)
        else -> Color(0xFF9E9E9E)
    }
}
