package com.scaso.drclawapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    @Test
    fun `Message creation with required fields`() {
        val msg = Message(
            id = "msg-1",
            role = Role.USER,
            content = "Hello",
            timestamp = 1700000000L,
        )
        assertEquals("msg-1", msg.id)
        assertEquals(Role.USER, msg.role)
        assertEquals("Hello", msg.content)
        assertEquals(1700000000L, msg.timestamp)
        assertFalse(msg.isStreaming)
        assertNull(msg.runId)
    }

    @Test
    fun `Message creation with streaming flag`() {
        val msg = Message(
            id = "msg-2",
            role = Role.ASSISTANT,
            content = "I'm thinking...",
            timestamp = 1700000001L,
            isStreaming = true,
            runId = "run-abc",
        )
        assertTrue(msg.isStreaming)
        assertEquals("run-abc", msg.runId)
    }

    @Test
    fun `Message copy updates content`() {
        val original = Message(
            id = "msg-3",
            role = Role.ASSISTANT,
            content = "Hello",
            timestamp = 1700000000L,
            isStreaming = true,
            runId = "run-1",
        )
        val updated = original.copy(content = "Hello world")
        assertEquals("Hello world", updated.content)
        assertEquals(original.id, updated.id)
        assertTrue(updated.isStreaming)
    }

    @Test
    fun `Message copy finalizes streaming`() {
        val streaming = Message(
            id = "msg-4",
            role = Role.ASSISTANT,
            content = "Complete response",
            timestamp = 1700000000L,
            isStreaming = true,
            runId = "run-2",
        )
        val finalized = streaming.copy(isStreaming = false)
        assertFalse(finalized.isStreaming)
        assertEquals(streaming.content, finalized.content)
    }

    @Test
    fun `Role enum values`() {
        assertEquals(4, Role.entries.size)
        assertEquals(Role.USER, Role.valueOf("USER"))
        assertEquals(Role.ASSISTANT, Role.valueOf("ASSISTANT"))
        assertEquals(Role.SYSTEM, Role.valueOf("SYSTEM"))
        assertEquals(Role.DENIAL, Role.valueOf("DENIAL"))
    }

    @Test
    fun `Message equality by data class contract`() {
        val msg1 = Message(id = "a", role = Role.USER, content = "hi", timestamp = 0L)
        val msg2 = Message(id = "a", role = Role.USER, content = "hi", timestamp = 0L)
        assertEquals(msg1, msg2)
    }

    @Test
    fun `System role message for errors`() {
        val msg = Message(
            id = "err-1",
            role = Role.SYSTEM,
            content = "[Error: Connection lost]",
            timestamp = 1700000000L,
        )
        assertEquals(Role.SYSTEM, msg.role)
        assertTrue(msg.content.contains("Error"))
    }
}
