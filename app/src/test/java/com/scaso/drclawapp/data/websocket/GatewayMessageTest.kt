package com.scaso.drclawapp.data.websocket

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayMessageTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `RequestFrame serializes correctly`() {
        val frame = RequestFrame(
            id = "test-123",
            method = "chat.send",
            params = buildJsonObject {
                put("message", "hello")
                put("idempotencyKey", "key-456")
            },
        )
        val serialized = json.encodeToString(RequestFrame.serializer(), frame)
        assertTrue(serialized.contains("\"type\":\"req\""))
        assertTrue(serialized.contains("\"id\":\"test-123\""))
        assertTrue(serialized.contains("\"method\":\"chat.send\""))
        assertTrue(serialized.contains("\"message\":\"hello\""))
    }

    @Test
    fun `RequestFrame round-trips through serialization`() {
        val original = RequestFrame(
            id = "round-trip",
            method = "connect",
            params = buildJsonObject { put("token", "abc") },
        )
        val serialized = json.encodeToString(RequestFrame.serializer(), original)
        val deserialized = json.decodeFromString(RequestFrame.serializer(), serialized)
        assertEquals(original.id, deserialized.id)
        assertEquals(original.method, deserialized.method)
        assertEquals(original.type, deserialized.type)
    }

    @Test
    fun `ResponseFrame deserializes ok response`() {
        val raw = """{"type":"res","id":"req-1","ok":true,"payload":{"protocol":3}}"""
        val frame = json.decodeFromString(ResponseFrame.serializer(), raw)
        assertEquals("res", frame.type)
        assertEquals("req-1", frame.id)
        assertTrue(frame.ok)
        assertNotNull(frame.payload)
    }

    @Test
    fun `ResponseFrame deserializes error response`() {
        val raw = """{"type":"res","id":"req-2","ok":false,"error":{"code":"AUTH_FAILED","message":"Bad token","retryable":false}}"""
        val frame = json.decodeFromString(ResponseFrame.serializer(), raw)
        assertEquals("req-2", frame.id)
        assertTrue(!frame.ok)
        assertNotNull(frame.error)
        assertEquals("AUTH_FAILED", frame.error!!.code)
        assertEquals("Bad token", frame.error!!.message)
    }

    @Test
    fun `EventFrame deserializes correctly`() {
        val raw = """{"type":"event","event":"tick","seq":42}"""
        val frame = json.decodeFromString(EventFrame.serializer(), raw)
        assertEquals("event", frame.type)
        assertEquals("tick", frame.event)
        assertEquals(42L, frame.seq)
    }

    @Test
    fun `ChatSendParams serializes correctly`() {
        val params = ChatSendParams(
            message = "Hello world",
            idempotencyKey = "idem-1",
            sessionKey = "session-abc",
        )
        val serialized = json.encodeToString(ChatSendParams.serializer(), params)
        assertTrue(serialized.contains("\"message\":\"Hello world\""))
        assertTrue(serialized.contains("\"idempotencyKey\":\"idem-1\""))
        assertTrue(serialized.contains("\"sessionKey\":\"session-abc\""))
    }

    @Test
    fun `ChatSendParams sessionKey is optional`() {
        val params = ChatSendParams(
            message = "test",
            idempotencyKey = "idem-2",
        )
        val serialized = json.encodeToString(ChatSendParams.serializer(), params)
        assertTrue(serialized.contains("\"message\":\"test\""))
    }

    @Test
    fun `ErrorShape deserializes with retryAfterMs`() {
        val raw = """{"code":"RATE_LIMITED","message":"Slow down","retryable":true,"retryAfterMs":5000}"""
        val error = json.decodeFromString(ErrorShape.serializer(), raw)
        assertEquals("RATE_LIMITED", error.code)
        assertTrue(error.retryable)
        assertEquals(5000L, error.retryAfterMs)
    }

    @Test
    fun `FrameEnvelope discriminates event frames`() {
        val raw = """{"type":"event","event":"chat","payload":{"state":"delta","message":"hi"}}"""
        val envelope = json.decodeFromString(FrameEnvelope.serializer(), raw)
        assertEquals("event", envelope.type)
        assertEquals("chat", envelope.event)
        assertNotNull(envelope.payload)
    }

    @Test
    fun `FrameEnvelope discriminates response frames`() {
        val raw = """{"type":"res","id":"req-5","ok":true,"payload":{}}"""
        val envelope = json.decodeFromString(FrameEnvelope.serializer(), raw)
        assertEquals("res", envelope.type)
        assertEquals("req-5", envelope.id)
        assertTrue(envelope.ok!!)
    }
}
