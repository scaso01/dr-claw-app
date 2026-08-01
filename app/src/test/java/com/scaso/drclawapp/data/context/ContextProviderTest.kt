package com.scaso.drclawapp.data.context

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `AmbientContext serialization round-trip`() {
        val context = AmbientContext(
            timestamp = 1711800000000L,
            timeOfDay = "morning",
            dayOfWeek = "Monday",
            localTime = "09:30",
            upcomingEvents = listOf(
                CalendarEvent(
                    title = "Team standup",
                    startTime = 1711800600000L,
                    endTime = 1711802400000L,
                    location = "Zoom",
                ),
            ),
            batteryLevel = 85,
        )

        val serialized = json.encodeToString(AmbientContext.serializer(), context)
        val deserialized = json.decodeFromString(AmbientContext.serializer(), serialized)

        assertEquals(context.timestamp, deserialized.timestamp)
        assertEquals(context.timeOfDay, deserialized.timeOfDay)
        assertEquals(context.dayOfWeek, deserialized.dayOfWeek)
        assertEquals(context.localTime, deserialized.localTime)
        assertEquals(1, deserialized.upcomingEvents.size)
        assertEquals("Team standup", deserialized.upcomingEvents[0].title)
        assertEquals("Zoom", deserialized.upcomingEvents[0].location)
        assertEquals(85, deserialized.batteryLevel)
    }

    @Test
    fun `AmbientContext with no events serializes correctly`() {
        val context = AmbientContext(
            timestamp = 1711800000000L,
            timeOfDay = "evening",
            dayOfWeek = "Friday",
            localTime = "18:00",
            upcomingEvents = emptyList(),
            batteryLevel = 42,
        )

        val serialized = json.encodeToString(AmbientContext.serializer(), context)
        assertNotNull(serialized)
        // Verify round-trip preserves empty events list
        val deserialized = json.decodeFromString(AmbientContext.serializer(), serialized)
        assertEquals(emptyList<CalendarEvent>(), deserialized.upcomingEvents)
        assertEquals(42, deserialized.batteryLevel)
        assertEquals("evening", deserialized.timeOfDay)
    }

    @Test
    fun `CalendarEvent without location serializes with null`() {
        val event = CalendarEvent(
            title = "Quick sync",
            startTime = 1711800000000L,
            endTime = 1711801800000L,
            location = null,
        )

        val serialized = json.encodeToString(CalendarEvent.serializer(), event)
        val deserialized = json.decodeFromString(CalendarEvent.serializer(), serialized)

        assertEquals("Quick sync", deserialized.title)
        assertEquals(null, deserialized.location)
    }

    @Test
    fun `AmbientContext default batteryLevel is negative one`() {
        val context = AmbientContext(
            timestamp = 0L,
            timeOfDay = "night",
            dayOfWeek = "Sunday",
            localTime = "23:00",
        )

        assertEquals(-1, context.batteryLevel)
        assertEquals(emptyList<CalendarEvent>(), context.upcomingEvents)
    }
}
