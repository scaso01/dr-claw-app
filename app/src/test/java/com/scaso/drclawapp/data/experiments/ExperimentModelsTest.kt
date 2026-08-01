package com.scaso.drclawapp.data.experiments

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Experiment deserializes from JSON with all fields`() {
        val jsonStr = """
        {
            "id": "exp-001",
            "name": "Test Experiment",
            "status": "running",
            "hypothesis": "Testing hypothesis",
            "progress": 0.5,
            "metrics": [{"label": "accuracy", "value": 0.85, "timestamp": 1000}],
            "steps": [{"index": 0, "description": "Setup", "completed": true, "active": false}],
            "decisions": [{"timestamp": 1000, "description": "Chose A", "rationale": "Better fit"}],
            "created_at": 100,
            "updated_at": 200,
            "error": null
        }
        """.trimIndent()

        val experiment = json.decodeFromString(Experiment.serializer(), jsonStr)
        assertEquals("exp-001", experiment.id)
        assertEquals("Test Experiment", experiment.name)
        assertEquals(ExperimentStatus.RUNNING, experiment.status)
        assertEquals(0.5f, experiment.progress)
        assertEquals(1, experiment.metrics.size)
        assertEquals(0.85f, experiment.metrics[0].value)
        assertEquals(1, experiment.steps.size)
        assertTrue(experiment.steps[0].completed)
        assertEquals(1, experiment.decisions.size)
    }

    @Test
    fun `ExperimentStatusResponse deserializes empty list`() {
        val jsonStr = """{"experiments": []}"""
        val response = json.decodeFromString(ExperimentStatusResponse.serializer(), jsonStr)
        assertTrue(response.experiments.isEmpty())
    }

    @Test
    fun `ExperimentStatus enum maps all variants`() {
        val statuses = listOf("pending", "running", "paused", "completed", "failed", "aborted")
        val expected = listOf(
            ExperimentStatus.PENDING, ExperimentStatus.RUNNING, ExperimentStatus.PAUSED,
            ExperimentStatus.COMPLETED, ExperimentStatus.FAILED, ExperimentStatus.ABORTED,
        )
        statuses.zip(expected).forEach { (str, status) ->
            val jsonStr = """{"id":"x","name":"n","status":"$str"}"""
            val exp = json.decodeFromString(Experiment.serializer(), jsonStr)
            assertEquals(status, exp.status)
        }
    }

    @Test
    fun `MetricPoint round-trips through serialization`() {
        val point = MetricPoint(label = "loss", value = 0.123f, timestamp = 999L)
        val encoded = json.encodeToString(MetricPoint.serializer(), point)
        val decoded = json.decodeFromString(MetricPoint.serializer(), encoded)
        assertEquals(point.label, decoded.label)
        assertEquals(point.value, decoded.value)
        assertEquals(point.timestamp, decoded.timestamp)
    }
}
