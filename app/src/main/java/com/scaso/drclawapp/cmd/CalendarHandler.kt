package com.scaso.drclawapp.cmd

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Queries CalendarContract for upcoming events and returns structured data.
 * Tool: device.calendar
 *
 * Params:
 *   - days: number of days to look ahead (default 7)
 */
class CalendarHandler : CmdFrameHandler {
    override val tool: String = "device.calendar"

    override suspend fun execute(params: JsonObject, context: Context): JsonElement {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return buildJsonObject {
                put("error", "READ_CALENDAR permission not granted")
                put("events", buildJsonArray { })
            }
        }

        val daysAhead = params["days"]?.jsonPrimitive?.int ?: DEFAULT_DAYS
        val now = System.currentTimeMillis()
        val end = now + daysAhead * DAY_MS

        return try {
            val events = queryEvents(context.contentResolver, now, end)
            buildJsonObject {
                put("count", events.size)
                put("days_ahead", daysAhead)
                put("events", buildJsonArray {
                    events.forEach { event -> add(event) }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Calendar query failed", e)
            buildJsonObject {
                put("error", e.message ?: "Calendar query failed")
                put("events", buildJsonArray { })
            }
        }
    }

    private fun queryEvents(resolver: ContentResolver, startMs: Long, endMs: Long): List<JsonElement> {
        val events = mutableListOf<JsonElement>()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION,
        )

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(startMs.toString())
            .appendPath(endMs.toString())
            .build()

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            cursor?.use {
                while (it.moveToNext() && events.size < MAX_EVENTS) {
                    events.add(buildJsonObject {
                        put("title", it.getString(0) ?: "")
                        put("start", it.getLong(1))
                        put("end", it.getLong(2))
                        put("location", it.getString(3) ?: "")
                        put("all_day", it.getInt(4) == 1)
                        put("description", (it.getString(5) ?: "").take(200))
                    })
                }
            }
        } finally {
            cursor?.close()
        }
        return events
    }

    companion object {
        private const val TAG = "CalendarHandler"
        private const val DEFAULT_DAYS = 7
        private const val DAY_MS = 86_400_000L
        private const val MAX_EVENTS = 50
    }
}
