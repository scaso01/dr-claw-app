package com.scaso.drclawapp.data.projects

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- ProjectStatus @SerialName mapping ---

    @Test
    fun `ProjectStatus idle deserializes to IDLE`() {
        assertEquals(ProjectStatus.IDLE, json.decodeFromString(ProjectStatus.serializer(), "\"idle\""))
    }

    @Test
    fun `ProjectStatus running deserializes to RUNNING`() {
        assertEquals(ProjectStatus.RUNNING, json.decodeFromString(ProjectStatus.serializer(), "\"running\""))
    }

    @Test
    fun `ProjectStatus failed deserializes to FAILED`() {
        assertEquals(ProjectStatus.FAILED, json.decodeFromString(ProjectStatus.serializer(), "\"failed\""))
    }

    @Test
    fun `ProjectStatus unknown deserializes to UNKNOWN`() {
        assertEquals(ProjectStatus.UNKNOWN, json.decodeFromString(ProjectStatus.serializer(), "\"unknown\""))
    }

    @Test
    fun `ProjectStatus IDLE serializes to idle`() {
        assertEquals("\"idle\"", json.encodeToString(ProjectStatus.serializer(), ProjectStatus.IDLE))
    }

    @Test
    fun `ProjectStatus RUNNING serializes to running`() {
        assertEquals("\"running\"", json.encodeToString(ProjectStatus.serializer(), ProjectStatus.RUNNING))
    }

    // --- LastRunSummary deserialization ---

    @Test
    fun `deserializes full LastRunSummary`() {
        val raw = """{
            "timestamp": 1700000000,
            "durationMs": 210000,
            "itemsScraped": 500,
            "itemsMatched": 42,
            "itemsSubmitted": 10,
            "errors": 3
        }"""
        val summary = json.decodeFromString(LastRunSummary.serializer(), raw)
        assertEquals(1700000000L, summary.timestamp)
        assertEquals(210000L, summary.durationMs)
        assertEquals(500, summary.itemsScraped)
        assertEquals(42, summary.itemsMatched)
        assertEquals(10, summary.itemsSubmitted)
        assertEquals(3, summary.errors)
    }

    @Test
    fun `deserializes minimal LastRunSummary`() {
        val raw = """{}"""
        val summary = json.decodeFromString(LastRunSummary.serializer(), raw)
        assertNull(summary.timestamp)
        assertNull(summary.durationMs)
        assertEquals(0, summary.itemsScraped)
        assertEquals(0, summary.itemsMatched)
        assertEquals(0, summary.itemsSubmitted)
        assertEquals(0, summary.errors)
    }

    // --- PipelineStatus deserialization ---

    @Test
    fun `deserializes full PipelineStatus`() {
        val raw = """{"stage": "scraping", "progress": 0.75, "message": "Scraping LinkedIn..."}"""
        val pipeline = json.decodeFromString(PipelineStatus.serializer(), raw)
        assertEquals("scraping", pipeline.stage)
        assertEquals(0.75f, pipeline.progress, 0.001f)
        assertEquals("Scraping LinkedIn...", pipeline.message)
    }

    @Test
    fun `deserializes minimal PipelineStatus`() {
        val raw = """{}"""
        val pipeline = json.decodeFromString(PipelineStatus.serializer(), raw)
        assertEquals("", pipeline.stage)
        assertEquals(0f, pipeline.progress, 0.001f)
        assertNull(pipeline.message)
    }

    // --- ProjectStatusResponse deserialization ---

    @Test
    fun `deserializes full ProjectStatusResponse`() {
        val raw = """{
            "project": "example-pipeline",
            "status": "running",
            "lastRun": {
                "timestamp": 1700000000,
                "durationMs": 120000,
                "itemsScraped": 100,
                "itemsMatched": 20,
                "itemsSubmitted": 5,
                "errors": 0
            },
            "pipeline": {
                "stage": "matching",
                "progress": 0.5,
                "message": "Matching jobs..."
            }
        }"""
        val response = json.decodeFromString(ProjectStatusResponse.serializer(), raw)
        assertEquals("example-pipeline", response.project)
        assertEquals(ProjectStatus.RUNNING, response.status)
        assertEquals(100, response.lastRun!!.itemsScraped)
        assertEquals("matching", response.pipeline!!.stage)
    }

    @Test
    fun `deserializes minimal ProjectStatusResponse`() {
        val raw = """{}"""
        val response = json.decodeFromString(ProjectStatusResponse.serializer(), raw)
        assertEquals("", response.project)
        assertEquals(ProjectStatus.UNKNOWN, response.status)
        assertNull(response.lastRun)
        assertNull(response.pipeline)
    }

    @Test
    fun `ProjectStatusResponse round-trip preserves data`() {
        val original = ProjectStatusResponse(
            project = "test",
            status = ProjectStatus.IDLE,
            lastRun = LastRunSummary(timestamp = 100, itemsScraped = 50),
        )
        val serialized = json.encodeToString(ProjectStatusResponse.serializer(), original)
        val deserialized = json.decodeFromString(ProjectStatusResponse.serializer(), serialized)
        assertEquals(original, deserialized)
    }
}
