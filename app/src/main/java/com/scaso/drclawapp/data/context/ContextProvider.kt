package com.scaso.drclawapp.data.context

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Collects ambient context from the device for proactive AI suggestions.
 * Gathers calendar events, time/day info, and device state.
 *
 * Permissions (calendar, location) are only used if granted — the provider
 * degrades gracefully when permissions are missing.
 */
class ContextProvider(private val context: Context) {

    private val json = Json { encodeDefaults = true }

    fun collectContext(): AmbientContext {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return AmbientContext(
            timestamp = now,
            timeOfDay = getTimeOfDay(calendar),
            dayOfWeek = getDayOfWeek(calendar),
            localTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
            upcomingEvents = getUpcomingCalendarEvents(),
            batteryLevel = getBatteryLevel(),
        )
    }

    fun collectContextJson(): String = json.encodeToString(collectContext())

    private fun getTimeOfDay(calendar: Calendar): String {
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
    }

    private fun getDayOfWeek(calendar: Calendar): String {
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            Calendar.SUNDAY -> "Sunday"
            else -> "Unknown"
        }
    }

    private fun getUpcomingCalendarEvents(): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis()
        val twoHoursLater = now + 2 * 60 * 60 * 1000L

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), twoHoursLater.toString())

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC",
            )

            cursor?.let {
                while (it.moveToNext()) {
                    val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                    val start = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    val end = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                    val location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))

                    events.add(
                        CalendarEvent(
                            title = title ?: "Untitled",
                            startTime = start,
                            endTime = end,
                            location = location,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read calendar events: ${e.message}")
        } finally {
            cursor?.close()
        }

        return events
    }

    private fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE)
                as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            -1
        }
    }

    companion object {
        private const val TAG = "ContextProvider"
    }
}

@Serializable
data class AmbientContext(
    val timestamp: Long,
    val timeOfDay: String,
    val dayOfWeek: String,
    val localTime: String,
    val upcomingEvents: List<CalendarEvent> = emptyList(),
    val batteryLevel: Int = -1,
)

@Serializable
data class CalendarEvent(
    val title: String,
    val startTime: Long,
    val endTime: Long,
    val location: String? = null,
)
