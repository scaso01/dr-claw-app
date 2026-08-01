package com.scaso.drclawapp.data.infra

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InfraModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- ComponentStatus @SerialName mapping ---

    @Test
    fun `ComponentStatus ok deserializes to HEALTHY`() {
        val raw = """"ok""""
        val status = json.decodeFromString(ComponentStatus.serializer(), raw)
        assertEquals(ComponentStatus.HEALTHY, status)
    }

    @Test
    fun `ComponentStatus warn deserializes to WARNING`() {
        val raw = """"warn""""
        val status = json.decodeFromString(ComponentStatus.serializer(), raw)
        assertEquals(ComponentStatus.WARNING, status)
    }

    @Test
    fun `ComponentStatus error deserializes to ERROR`() {
        val raw = """"error""""
        val status = json.decodeFromString(ComponentStatus.serializer(), raw)
        assertEquals(ComponentStatus.ERROR, status)
    }

    @Test
    fun `ComponentStatus unknown deserializes to UNKNOWN`() {
        val raw = """"unknown""""
        val status = json.decodeFromString(ComponentStatus.serializer(), raw)
        assertEquals(ComponentStatus.UNKNOWN, status)
    }

    @Test
    fun `ComponentStatus HEALTHY serializes to ok`() {
        val serialized = json.encodeToString(ComponentStatus.serializer(), ComponentStatus.HEALTHY)
        assertEquals("\"ok\"", serialized)
    }

    @Test
    fun `ComponentStatus WARNING serializes to warn`() {
        val serialized = json.encodeToString(ComponentStatus.serializer(), ComponentStatus.WARNING)
        assertEquals("\"warn\"", serialized)
    }

    // --- InfraComponent deserialization ---

    @Test
    fun `deserializes full InfraComponent`() {
        val raw = """{
            "name": "Caddy Proxy",
            "status": "ok",
            "message": "All good",
            "detail": "Running v2.8",
            "endpoint": "https://gateway.example.com",
            "certExpiresAt": 1800000000
        }"""
        val c = json.decodeFromString(InfraComponent.serializer(), raw)
        assertEquals("Caddy Proxy", c.name)
        assertEquals(ComponentStatus.HEALTHY, c.status)
        assertEquals("All good", c.message)
        assertEquals("Running v2.8", c.detail)
        assertEquals("https://gateway.example.com", c.endpoint)
        assertEquals(1800000000L, c.certExpiresAt)
    }

    @Test
    fun `deserializes InfraComponent with only name`() {
        val raw = """{"name": "Docker"}"""
        val c = json.decodeFromString(InfraComponent.serializer(), raw)
        assertEquals("Docker", c.name)
        assertEquals(ComponentStatus.UNKNOWN, c.status)
        assertNull(c.message)
        assertNull(c.detail)
        assertNull(c.endpoint)
        assertNull(c.certExpiresAt)
    }

    @Test
    fun `deserializes InfraComponent with detail but no message`() {
        val raw = """{"name": "ntfy", "status": "warn", "detail": "High memory usage"}"""
        val c = json.decodeFromString(InfraComponent.serializer(), raw)
        assertEquals(ComponentStatus.WARNING, c.status)
        assertNull(c.message)
        assertEquals("High memory usage", c.detail)
    }

    // --- InfraStatusResponse deserialization ---

    @Test
    fun `deserializes InfraStatusResponse with components`() {
        val raw = """{
            "components": [
                {"name": "Gateway", "status": "ok"},
                {"name": "Docker", "status": "error", "detail": "Container down"}
            ],
            "checkedAt": 1700000000
        }"""
        val response = json.decodeFromString(InfraStatusResponse.serializer(), raw)
        assertEquals(2, response.components.size)
        assertEquals("Gateway", response.components[0].name)
        assertEquals(ComponentStatus.HEALTHY, response.components[0].status)
        assertEquals("Docker", response.components[1].name)
        assertEquals(ComponentStatus.ERROR, response.components[1].status)
        assertEquals("Container down", response.components[1].detail)
        assertEquals(1700000000L, response.checkedAt)
    }

    @Test
    fun `deserializes empty InfraStatusResponse`() {
        val raw = """{}"""
        val response = json.decodeFromString(InfraStatusResponse.serializer(), raw)
        assertEquals(0, response.components.size)
        assertNull(response.checkedAt)
    }

    @Test
    fun `InfraStatusResponse round-trip preserves data`() {
        val original = InfraStatusResponse(
            components = listOf(
                InfraComponent(name = "Test", status = ComponentStatus.WARNING, detail = "Slow"),
            ),
            checkedAt = 999L,
        )
        val serialized = json.encodeToString(InfraStatusResponse.serializer(), original)
        val deserialized = json.decodeFromString(InfraStatusResponse.serializer(), serialized)
        assertEquals(original, deserialized)
    }
}
