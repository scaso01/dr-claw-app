package com.scaso.drclawapp.ui.components

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formats a message timestamp (epoch millis) into a human-readable string.
 *
 * Rules:
 * - Today: "2:30 PM"
 * - Yesterday: "Yesterday 2:30 PM"
 * - This year: "Feb 15, 2:30 PM"
 * - Older: "Feb 15, 2024, 2:30 PM"
 */
fun formatMessageTimestamp(epochMillis: Long): String {
    val now = Calendar.getInstance()
    val messageTime = Calendar.getInstance().apply { timeInMillis = epochMillis }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(epochMillis))

    val isToday = now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)

    if (isToday) return timeStr

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == messageTime.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) return "Yesterday $timeStr"

    val isSameYear = now.get(Calendar.YEAR) == messageTime.get(Calendar.YEAR)

    return if (isSameYear) {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        "${dateFormat.format(Date(epochMillis))}, $timeStr"
    } else {
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        "${dateFormat.format(Date(epochMillis))}, $timeStr"
    }
}
