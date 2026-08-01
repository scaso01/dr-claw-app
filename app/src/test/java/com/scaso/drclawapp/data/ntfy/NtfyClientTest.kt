package com.scaso.drclawapp.data.ntfy

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfyClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: NtfyClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = NtfyClient(scope)
    }

    @After
    fun tearDown() {
        client.unsubscribe()
        try { server.shutdown() } catch (_: Exception) { }
    }

    @Test
    fun `initial connected state is false`() {
        assertFalse(client.connected.value)
    }

    @Test
    fun `unsubscribe sets connected to false`() {
        client.unsubscribe()
        assertFalse(client.connected.value)
    }

    @Test
    fun `subscribe with blank topic does nothing`() {
        client.subscribe("http://localhost", "")
        assertFalse(client.connected.value)
    }

    @Test
    fun `subscribe emits notification from SSE stream`() = runTest {
        val sseBody = buildString {
            append("data: {\"id\":\"msg-1\",\"time\":1700000000,\"event\":\"message\",\"topic\":\"test\",\"message\":\"Hello\"}\n")
            append("\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val baseUrl = server.url("/").toString().trimEnd('/')

        client.events.test {
            client.subscribe(baseUrl, "test")
            val event = awaitItem()
            assertEquals("msg-1", event.id)
            assertEquals("Hello", event.message)
            assertEquals("message", event.event)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscribe filters out non-message events`() = runTest {
        val sseBody = buildString {
            // keepalive event — should be skipped
            append("data: {\"id\":\"evt-1\",\"time\":100,\"event\":\"keepalive\",\"topic\":\"test\"}\n")
            append("\n")
            // actual message event — should be emitted
            append("data: {\"id\":\"msg-2\",\"time\":200,\"event\":\"message\",\"topic\":\"test\",\"message\":\"Real\"}\n")
            append("\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val baseUrl = server.url("/").toString().trimEnd('/')

        client.events.test {
            client.subscribe(baseUrl, "test")
            val event = awaitItem()
            assertEquals("msg-2", event.id)
            assertEquals("Real", event.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscribe skips non-data lines`() = runTest {
        val sseBody = buildString {
            append(": this is a comment\n")
            append("event: message\n")
            append("data: {\"id\":\"msg-3\",\"time\":300,\"event\":\"message\",\"topic\":\"t\",\"message\":\"OK\"}\n")
            append("\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val baseUrl = server.url("/").toString().trimEnd('/')

        client.events.test {
            client.subscribe(baseUrl, "t")
            val event = awaitItem()
            assertEquals("msg-3", event.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subscribe with click URL preserves it`() = runTest {
        val sseBody = buildString {
            append("data: {\"id\":\"msg-4\",\"time\":400,\"event\":\"message\",\"topic\":\"t\",\"message\":\"Tap\",\"click\":\"drclaw://session/sess-xyz\"}\n")
            append("\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val baseUrl = server.url("/").toString().trimEnd('/')

        client.events.test {
            client.subscribe(baseUrl, "t")
            val event = awaitItem()
            assertEquals("drclaw://session/sess-xyz", event.click)
            assertEquals("sess-xyz", event.extractSessionKey())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connected becomes true on successful connection`() = runTest {
        val sseBody = "data: {\"id\":\"m1\",\"time\":0,\"event\":\"message\",\"topic\":\"t\",\"message\":\"hi\"}\n\n"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val baseUrl = server.url("/").toString().trimEnd('/')

        client.connected.test {
            assertFalse(awaitItem()) // initial false
            client.subscribe(baseUrl, "t")
            assertTrue(awaitItem()) // becomes true on connect
            cancelAndIgnoreRemainingEvents()
        }
    }
}
