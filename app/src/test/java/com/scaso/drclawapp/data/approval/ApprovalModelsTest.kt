package com.scaso.drclawapp.data.approval

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ApprovalModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ApprovalRequest deserializes with defaults`() {
        val jsonStr = """
        {
            "id": "req-001",
            "action": "file.delete",
            "description": "Delete temp files"
        }
        """.trimIndent()

        val request = json.decodeFromString(ApprovalRequest.serializer(), jsonStr)
        assertEquals("req-001", request.id)
        assertEquals("file.delete", request.action)
        assertEquals(ApprovalSeverity.NORMAL, request.severity)
        assertEquals(300_000L, request.timeoutMs)
    }

    @Test
    fun `ApprovalRequest deserializes with explicit severity`() {
        val jsonStr = """
        {
            "id": "req-002",
            "action": "db.drop",
            "description": "Drop table users",
            "severity": "critical",
            "timeout_ms": 60000
        }
        """.trimIndent()

        val request = json.decodeFromString(ApprovalRequest.serializer(), jsonStr)
        assertEquals(ApprovalSeverity.CRITICAL, request.severity)
        assertEquals(60_000L, request.timeoutMs)
    }

    @Test
    fun `ApprovalSeverity covers all levels`() {
        val levels = listOf("low", "normal", "high", "critical")
        val expected = listOf(
            ApprovalSeverity.LOW, ApprovalSeverity.NORMAL,
            ApprovalSeverity.HIGH, ApprovalSeverity.CRITICAL,
        )
        levels.zip(expected).forEach { (str, severity) ->
            val jsonStr = """{"id":"x","action":"a","description":"d","severity":"$str"}"""
            val request = json.decodeFromString(ApprovalRequest.serializer(), jsonStr)
            assertEquals(severity, request.severity)
        }
    }

    @Test
    fun `ApprovalRequest round-trips through serialization`() {
        val request = ApprovalRequest(
            id = "req-003",
            action = "git.push",
            description = "Push to main",
            context = "branch: main, 3 commits",
            severity = ApprovalSeverity.HIGH,
            timeoutMs = 120_000L,
            createdAt = 1234567890L,
        )
        val encoded = json.encodeToString(ApprovalRequest.serializer(), request)
        val decoded = json.decodeFromString(ApprovalRequest.serializer(), encoded)
        assertEquals(request.id, decoded.id)
        assertEquals(request.action, decoded.action)
        assertEquals(request.severity, decoded.severity)
        assertEquals(request.timeoutMs, decoded.timeoutMs)
        assertEquals(request.context, decoded.context)
    }
}
