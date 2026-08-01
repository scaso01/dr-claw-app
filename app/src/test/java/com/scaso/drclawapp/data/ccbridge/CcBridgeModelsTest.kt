package com.scaso.drclawapp.data.ccbridge

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcBridgeModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- CcSession deserialization ---

    @Test
    fun `deserializes full CcSession`() {
        val raw = """{
            "sessionId": "sess-001",
            "project": "example-pipeline",
            "branch": "main",
            "shortId": "s001",
            "machine": "docker-host",
            "lastActivity": 1700000000,
            "status": "active",
            "lastMessage": "Running tests..."
        }"""
        val session = json.decodeFromString(CcSession.serializer(), raw)
        assertEquals("sess-001", session.sessionId)
        assertEquals("example-pipeline", session.project)
        assertEquals("main", session.branch)
        assertEquals("s001", session.shortId)
        assertEquals("docker-host", session.machine)
        assertEquals(1700000000L, session.lastActivity)
        assertEquals("active", session.status)
        assertEquals("Running tests...", session.lastMessage)
    }

    @Test
    fun `deserializes minimal CcSession with defaults`() {
        val raw = """{}"""
        val session = json.decodeFromString(CcSession.serializer(), raw)
        assertEquals("", session.sessionId)
        assertNull(session.project)
        assertNull(session.branch)
        assertEquals("", session.shortId)
        assertEquals("", session.machine)
        assertNull(session.lastActivity)
        assertEquals("unknown", session.status)
        assertNull(session.lastMessage)
    }

    @Test
    fun `deserializes CcSession with null project`() {
        val raw = """{"sessionId": "sess-002", "project": null, "lastActivity": 1700000000}"""
        val session = json.decodeFromString(CcSession.serializer(), raw)
        assertEquals("sess-002", session.sessionId)
        assertNull(session.project)
        assertEquals(1700000000L, session.lastActivity)
    }

    // --- CcSessionsResponse deserialization ---

    @Test
    fun `deserializes CcSessionsResponse with sessions`() {
        val raw = """{
            "sessions": [
                {"sessionId": "s1", "project": "proj1", "machine": "docker-host"},
                {"sessionId": "s2", "project": "proj2", "machine": "workstation"}
            ]
        }"""
        val response = json.decodeFromString(CcSessionsResponse.serializer(), raw)
        assertEquals(2, response.sessions.size)
        assertEquals("s1", response.sessions[0].sessionId)
        assertEquals("s2", response.sessions[1].sessionId)
    }

    @Test
    fun `deserializes CcSessionsResponse with empty sessions`() {
        val raw = """{"sessions": []}"""
        val response = json.decodeFromString(CcSessionsResponse.serializer(), raw)
        assertEquals(0, response.sessions.size)
    }

    @Test
    fun `deserializes CcSessionsResponse with default`() {
        val raw = """{}"""
        val response = json.decodeFromString(CcSessionsResponse.serializer(), raw)
        assertEquals(0, response.sessions.size)
    }

    // --- Machine enum ---

    @Test
    fun `Machine fromString docker-host`() {
        assertEquals(Machine.REMOTE, Machine.fromString("docker-host"))
    }

    @Test
    fun `Machine fromString docker-host mixed case`() {
        assertEquals(Machine.REMOTE, Machine.fromString("Docker-Host"))
    }

    @Test
    fun `Machine fromString docker-host uppercase`() {
        assertEquals(Machine.REMOTE, Machine.fromString("DOCKER-HOST"))
    }

    @Test
    fun `Machine fromString workstation`() {
        assertEquals(Machine.LOCAL, Machine.fromString("workstation"))
    }

    @Test
    fun `Machine fromString workstation mixed case`() {
        assertEquals(Machine.LOCAL, Machine.fromString("WorkStation"))
    }

    @Test
    fun `Machine fromString unknown value`() {
        assertEquals(Machine.UNKNOWN, Machine.fromString("raspberry-pi"))
    }

    @Test
    fun `Machine fromString empty string`() {
        assertEquals(Machine.UNKNOWN, Machine.fromString(""))
    }

    @Test
    fun `Machine display names are correct`() {
        assertEquals("docker-host", Machine.REMOTE.displayName)
        assertEquals("workstation", Machine.LOCAL.displayName)
        assertEquals("Unknown", Machine.UNKNOWN.displayName)
    }

    // --- CcSession camelCase parsing (post snake_case fix) ---

    @Test
    fun `CcSession parses from camelCase JSON with all fields`() {
        val raw = """{
            "sessionId": "abc-123",
            "project": "ironjaw",
            "shortId": "abc",
            "cwd": "/home/user",
            "machine": "workstation",
            "lastActivity": 1711234567890,
            "status": "idle",
            "lastMessage": "hello",
            "topic": "test session",
            "contextPct": 45.5,
            "compactionCount": 2,
            "backend": "cloud",
            "model": "opus",
            "permissionMode": "default",
            "isActive": true,
            "clients": 1
        }"""
        val session = json.decodeFromString(CcSession.serializer(), raw)
        assertEquals("abc-123", session.sessionId)
        assertEquals("ironjaw", session.project)
        assertEquals("abc", session.shortId)
        assertEquals("/home/user", session.cwd)
        assertEquals("workstation", session.machine)
        assertEquals(1711234567890L, session.lastActivity)
        assertEquals("idle", session.status)
        assertEquals("hello", session.lastMessage)
        assertEquals("test session", session.topic)
        assertEquals(45.5f, session.contextPct!!, 0.01f)
        assertEquals(2, session.compactionCount)
        assertEquals("cloud", session.backend)
        assertEquals("opus", session.model)
        assertEquals("default", session.permissionMode)
        assertTrue(session.isActive)
        assertEquals(1, session.clients)
    }

    @Test
    fun `CcSession defaults for missing optional fields`() {
        val raw = """{"sessionId": "x"}"""
        val session = json.decodeFromString(CcSession.serializer(), raw)
        assertEquals("x", session.sessionId)
        assertNull(session.project)
        assertNull(session.branch)
        assertEquals("", session.shortId)
        assertNull(session.cwd)
        assertEquals("", session.machine)
        assertNull(session.lastActivity)
        assertEquals("unknown", session.status)
        assertNull(session.lastMessage)
        assertNull(session.topic)
        assertNull(session.contextPct)
        assertEquals(0, session.compactionCount)
        assertNull(session.backend)
        assertNull(session.model)
        assertNull(session.permissionMode)
        assertFalse(session.isActive)
        assertEquals(0, session.clients)
    }

    @Test
    fun `CcSession ignores unknown keys`() {
        val raw = """{"sessionId": "x", "unknownField": "value", "anotherExtra": 999}"""
        val session = json.decodeFromString(CcSession.serializer(), raw)
        assertEquals("x", session.sessionId)
    }

    @Test
    fun `CcSessionsResponse parses session list`() {
        val raw = """{"sessions": [{"sessionId": "a"}, {"sessionId": "b"}]}"""
        val response = json.decodeFromString(CcSessionsResponse.serializer(), raw)
        assertEquals(2, response.sessions.size)
        assertEquals("a", response.sessions[0].sessionId)
        assertEquals("b", response.sessions[1].sessionId)
    }

    // --- HistoryMessage SerialName mapping ---

    @Test
    fun `HistoryMessage parses with SerialName fields`() {
        val raw = """{
            "role": "assistant",
            "content": "hi",
            "timestamp": 1700000000,
            "entry_type": "Assistant",
            "tool_name": "read"
        }"""
        val msg = json.decodeFromString(HistoryMessage.serializer(), raw)
        assertEquals("assistant", msg.role)
        assertEquals("hi", msg.content)
        assertEquals(1700000000L, msg.timestamp)
        assertEquals("Assistant", msg.entryType)
        assertEquals("read", msg.toolName)
    }

    @Test
    fun `HistoryMessage defaults when optional fields missing`() {
        val raw = """{"role": "user"}"""
        val msg = json.decodeFromString(HistoryMessage.serializer(), raw)
        assertEquals("user", msg.role)
        assertEquals("", msg.content)
        assertEquals(0L, msg.timestamp)
        assertNull(msg.entryType)
        assertNull(msg.toolName)
    }

    // --- ClaudeSession parsing ---

    @Test
    fun `ClaudeSession parses complete JSON`() {
        // cc.sessions sends "sessionId" + "title" (Ironjaw handle_cc_sessions / HistoricalSession);
        // ccBridgeId is @SerialName("sessionId") so it maps the real wire key.
        val raw = """{
            "sessionId": "bridge-001",
            "title": "Resume the bridge",
            "sdkSessionId": "sdk-abc",
            "role": "coder",
            "model": "opus",
            "project": "ironjaw",
            "cwd": "/home/user/projects/ironjaw",
            "status": "active",
            "contextPct": 42.5,
            "createdAt": "2026-03-24T10:00:00Z",
            "lastActivity": "2026-03-24T10:30:00Z"
        }"""
        val session = json.decodeFromString(ClaudeSession.serializer(), raw)
        assertEquals("bridge-001", session.ccBridgeId)
        assertEquals("Resume the bridge", session.title)
        assertEquals("sdk-abc", session.sdkSessionId)
        assertEquals("coder", session.role)
        assertEquals("opus", session.model)
        assertEquals("ironjaw", session.project)
        assertEquals("/home/user/projects/ironjaw", session.cwd)
        assertEquals("active", session.status)
        assertEquals(42.5f, session.contextPct)
        // cc.sessions carries no cost/tokens — they stay at defaults.
        assertEquals(0L, session.totalTokens)
        assertEquals(0.0, session.totalCostUsd, 0.001)
        assertEquals("2026-03-24T10:00:00Z", session.createdAt)
        assertEquals("2026-03-24T10:30:00Z", session.lastActivity)
    }

    @Test
    fun `ClaudeSession defaults for minimal JSON`() {
        val raw = """{}"""
        val session = json.decodeFromString(ClaudeSession.serializer(), raw)
        assertEquals("", session.ccBridgeId)
        assertNull(session.sdkSessionId)
        assertNull(session.role)
        assertNull(session.model)
        assertNull(session.project)
        assertEquals(".", session.cwd)
        assertEquals("unknown", session.status)
        assertEquals(0L, session.totalTokens)
        assertEquals(0.0, session.totalCostUsd, 0.001)
        assertEquals("", session.createdAt)
        assertEquals("", session.lastActivity)
    }

    // --- CcActiveSession parsing ---

    @Test
    fun `CcActiveSession parses with config`() {
        val raw = """{
            "id": "active-001",
            "status": "running",
            "sdkSessionId": "sdk-xyz",
            "config": {
                "project": "example-pipeline",
                "cwd": "/home/user/projects/example-pipeline",
                "backend": "local",
                "model": "qwen3.5",
                "permissionMode": "bypassPermissions",
                "resumedFrom": "old-session-id"
            },
            "clients": 2,
            "createdAt": 1711234000000,
            "lastActivityAt": 1711234567890
        }"""
        val session = json.decodeFromString(CcActiveSession.serializer(), raw)
        assertEquals("active-001", session.id)
        assertEquals("running", session.status)
        assertEquals("sdk-xyz", session.sdkSessionId)
        assertEquals(2, session.clients)
        assertEquals(1711234000000L, session.createdAt)
        assertEquals(1711234567890L, session.lastActivityAt)

        val config = session.config!!
        assertEquals("example-pipeline", config.project)
        assertEquals("/home/user/projects/example-pipeline", config.cwd)
        assertEquals("local", config.backend)
        assertEquals("qwen3.5", config.model)
        assertEquals("bypassPermissions", config.permissionMode)
        assertEquals("old-session-id", config.resumedFrom)
    }

    @Test
    fun `CcActiveSession defaults without config`() {
        val raw = """{"id": "active-002"}"""
        val session = json.decodeFromString(CcActiveSession.serializer(), raw)
        assertEquals("active-002", session.id)
        assertEquals("unknown", session.status)
        assertNull(session.sdkSessionId)
        assertNull(session.config)
        assertEquals(0, session.clients)
        assertEquals(0L, session.createdAt)
        assertEquals(0L, session.lastActivityAt)
    }
}
