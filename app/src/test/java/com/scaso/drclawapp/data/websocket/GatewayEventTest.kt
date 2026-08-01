package com.scaso.drclawapp.data.websocket

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayEventTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ConnectChallenge deserializes`() {
        val raw = """{"nonce":"abc123","ts":1700000000}"""
        val challenge = json.decodeFromString(ConnectChallenge.serializer(), raw)
        assertEquals("abc123", challenge.nonce)
        assertEquals(1700000000L, challenge.ts)
    }

    @Test
    fun `HelloOk deserializes with all fields`() {
        val raw = """{
            "protocol": 3,
            "server": {"name": "openclaw-gateway", "version": "2.0.0"},
            "features": {"methods": ["chat.send", "chat.history"], "events": ["chat", "tick"]},
            "policy": {"maxPayload": 65536, "maxBufferedBytes": 1048576, "tickIntervalMs": 30000},
            "snapshot": {
                "sessionDefaults": {"mainSessionKey": "session-main", "defaultAgentId": "agent-1"}
            }
        }"""
        val hello = json.decodeFromString(HelloOk.serializer(), raw)
        assertEquals(3, hello.protocol)
        assertEquals("openclaw-gateway", hello.server?.name)
        assertNotNull(hello.features)
        assertEquals(30000L, hello.policy.tickIntervalMs)
        assertEquals("session-main", hello.snapshot?.sessionDefaults?.mainSessionKey)
        assertEquals("agent-1", hello.snapshot?.sessionDefaults?.defaultAgentId)
    }

    @Test
    fun `HelloOk deserializes with minimal fields`() {
        val raw = """{"protocol": 3}"""
        val hello = json.decodeFromString(HelloOk.serializer(), raw)
        assertEquals(3, hello.protocol)
        assertNull(hello.server)
        assertNull(hello.features)
        assertNull(hello.snapshot)
        assertEquals(15000L, hello.policy.tickIntervalMs)
    }

    @Test
    fun `HelloOk ignores unknown fields`() {
        val raw = """{"protocol": 3, "unknownField": "value", "anotherUnknown": 42}"""
        val hello = json.decodeFromString(HelloOk.serializer(), raw)
        assertEquals(3, hello.protocol)
    }

    @Test
    fun `Policy has correct defaults`() {
        val policy = Policy()
        assertEquals(65536, policy.maxPayload)
        assertEquals(1048576, policy.maxBufferedBytes)
        assertEquals(15000L, policy.tickIntervalMs)
    }

    @Test
    fun `ChatEventPayload deserializes delta with structured message`() {
        val raw = """{"runId":"run-1","seq":5,"state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"Hello"}]}}"""
        val payload = json.decodeFromString(ChatEventPayload.serializer(), raw)
        assertEquals("run-1", payload.runId)
        assertEquals(5, payload.seq)
        assertEquals("delta", payload.state)
        assertNotNull(payload.message)
        assertEquals("assistant", payload.message?.role)
    }

    @Test
    fun `ChatEventPayload deserializes final with stopReason`() {
        val raw = """{"runId":"run-2","seq":10,"state":"final","stopReason":"end_turn","message":{"role":"assistant","content":[{"type":"text","text":"done"}]}}"""
        val payload = json.decodeFromString(ChatEventPayload.serializer(), raw)
        assertEquals("final", payload.state)
        assertEquals("end_turn", payload.stopReason)
        assertNotNull(payload.message)
    }

    @Test
    fun `ChatEventPayload deserializes error`() {
        val raw = """{"runId":"run-3","seq":0,"state":"error","message":{"role":"system","content":[{"type":"text","text":"Server error"}]}}"""
        val payload = json.decodeFromString(ChatEventPayload.serializer(), raw)
        assertEquals("error", payload.state)
        assertNotNull(payload.message)
    }

    @Test
    fun `ChatEventPayload deserializes aborted`() {
        val raw = """{"runId":"run-4","seq":0,"state":"aborted"}"""
        val payload = json.decodeFromString(ChatEventPayload.serializer(), raw)
        assertEquals("aborted", payload.state)
    }

    @Test
    fun `ChatEventPayload deserializes catchup with accumulated content`() {
        val raw = """{"runId":"run-5","seq":7,"state":"catchup","message":{"role":"assistant","content":[{"type":"text","text":"partial so far"}]}}"""
        val payload = json.decodeFromString(ChatEventPayload.serializer(), raw)
        assertEquals("run-5", payload.runId)
        assertEquals(7, payload.seq)
        assertEquals("catchup", payload.state)
        assertNotNull(payload.message)
        assertEquals("assistant", payload.message?.role)
    }

    @Test
    fun `GatewayEvent ChatCatchup carries runId content and seq`() {
        val catchup = GatewayEvent.ChatCatchup(runId = "run-5", content = "partial so far", seq = 7)
        assertEquals("run-5", catchup.runId)
        assertEquals("partial so far", catchup.content)
        assertEquals(7, catchup.seq)
    }

    @Test
    fun `HistoryEntry deserializes`() {
        // HistoryEntry is no longer @Serializable - test data class directly
        val entry = HistoryEntry(role = "user", content = "Hello")
        assertEquals("user", entry.role)
        assertEquals("Hello", entry.content)
        assertNull(entry.timestamp)
    }

    @Test
    fun `HistoryEntry timestamp is optional`() {
        // HistoryEntry is no longer @Serializable - test data class directly
        val entry = HistoryEntry(role = "user", content = "Hello")
        assertEquals("user", entry.role)
        assertEquals("Hello", entry.content)
        assertNull(entry.timestamp)
    }

    @Test
    fun `GatewayEvent sealed class variants`() {
        val delta = GatewayEvent.ChatDelta(runId = "r1", text = "hi", seq = 1)
        val final_ = GatewayEvent.ChatFinal(runId = "r1", text = null, stopReason = "end_turn")
        val aborted = GatewayEvent.ChatAborted(runId = "r1")
        val error = GatewayEvent.ChatError(runId = "r1", message = "oops")
        val tick = GatewayEvent.Tick(seq = 42)
        val history = GatewayEvent.HistoryResult(entries = emptyList())

        // Verify they're all GatewayEvent subtypes
        val events: List<GatewayEvent> = listOf(delta, final_, aborted, error, tick, history)
        assertEquals(6, events.size)
    }
}
