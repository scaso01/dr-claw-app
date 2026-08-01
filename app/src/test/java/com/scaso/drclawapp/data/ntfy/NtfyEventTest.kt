package com.scaso.drclawapp.data.ntfy

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyEventTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- NtfyNotification deserialization ---

    @Test
    fun `deserializes full notification`() {
        val raw = """{
            "id": "ntfy-abc",
            "time": 1700000000,
            "event": "message",
            "topic": "drclaw-test",
            "title": "New session",
            "message": "OpenClaw started a task",
            "priority": 4,
            "tags": ["robot", "warning"],
            "click": "drclaw://session/sess-123"
        }"""
        val n = json.decodeFromString(NtfyNotification.serializer(), raw)
        assertEquals("ntfy-abc", n.id)
        assertEquals(1700000000L, n.time)
        assertEquals("message", n.event)
        assertEquals("drclaw-test", n.topic)
        assertEquals("New session", n.title)
        assertEquals("OpenClaw started a task", n.message)
        assertEquals(4, n.priority)
        assertEquals(listOf("robot", "warning"), n.tags)
        assertEquals("drclaw://session/sess-123", n.click)
    }

    @Test
    fun `deserializes minimal notification`() {
        val raw = """{"id": "n1", "time": 100}"""
        val n = json.decodeFromString(NtfyNotification.serializer(), raw)
        assertEquals("n1", n.id)
        assertEquals(100L, n.time)
        assertEquals("message", n.event)
        assertEquals("", n.topic)
        assertNull(n.title)
        assertEquals("", n.message)
        assertEquals(3, n.priority)
        assertEquals(emptyList<String>(), n.tags)
        assertNull(n.click)
    }

    @Test
    fun `ignores unknown fields`() {
        val raw = """{"id": "n2", "time": 200, "unknownField": "value", "extras": {}}"""
        val n = json.decodeFromString(NtfyNotification.serializer(), raw)
        assertEquals("n2", n.id)
    }

    // --- extractSessionKey ---

    @Test
    fun `extractSessionKey with primary format`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://session/sess-abc-123")
        assertEquals("sess-abc-123", n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey with legacy format`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://chat?session=legacy-key")
        assertEquals("legacy-key", n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey returns null when click is null`() {
        val n = NtfyNotification(id = "n1", time = 0, click = null)
        assertNull(n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey returns null for unrecognized scheme`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "https://example.com/session/key")
        assertNull(n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey returns null for empty path in primary format`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://session/")
        assertNull(n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey returns null for blank path in primary format`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://session/   ")
        assertNull(n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey returns null for empty value in legacy format`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://chat?session=")
        assertNull(n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey primary format takes priority over legacy`() {
        // If somehow both match patterns, primary is checked first
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://session/my-key")
        assertEquals("my-key", n.extractSessionKey())
    }

    @Test
    fun `extractSessionKey handles complex session key with special chars`() {
        val n = NtfyNotification(id = "n1", time = 0, click = "drclaw://session/sess_2026-02-23_abc.def")
        assertEquals("sess_2026-02-23_abc.def", n.extractSessionKey())
    }
}
