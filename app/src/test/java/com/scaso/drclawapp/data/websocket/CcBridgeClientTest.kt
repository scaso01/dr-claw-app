package com.scaso.drclawapp.data.websocket

import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.TimeUnit

class CcBridgeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: CcBridgeClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        try { client.disconnect() } catch (_: Exception) {}
        try { server.shutdown() } catch (_: Exception) {}
    }

    private fun createClient(): CcBridgeClient {
        val url = server.url("/cc/ws").toString().replace("http://", "ws://")
        return CcBridgeClient(
            url = url,
            token = "test-token",
            scope = scope,
            okHttpClient = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
        ).also { client = it }
    }

    // ── Initial state ───────────────────────────────────────────────

    @Test
    fun `initial state is Disconnected`() {
        val c = createClient()
        assertTrue(c.connectionState.value is CcBridgeConnectionState.Disconnected)
    }

    // ── Connection lifecycle ────────────────────────────────────────

    @Test
    fun `connect transitions to Connecting`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {}
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connectionState.test {
            assertEquals(CcBridgeConnectionState.Disconnected, awaitItem())
            c.connect()
            val next = awaitItem()
            assertTrue(
                "Expected Connecting but got $next",
                next is CcBridgeConnectionState.Connecting,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect sets state to Disconnected`() = runTest {
        val c = createClient()
        c.disconnect()
        assertTrue(c.connectionState.value is CcBridgeConnectionState.Disconnected)
    }

    @Test
    fun `disconnect cancels pending requests`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {}
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connect()
        Thread.sleep(200)
        // Disconnect should not throw and should clear state
        c.disconnect()
        assertTrue(c.connectionState.value is CcBridgeConnectionState.Disconnected)
    }

    // ── Challenge-response auth ─────────────────────────────────────

    @Test
    fun `auth challenge triggers authentication flow`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Server sends auth.challenge event
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Client responds with auth request
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    // Verify token was sent
                    assertTrue(text.contains("\"token\":\"test-token\""))
                    // Respond with auth success
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connectionState.test {
            assertEquals(CcBridgeConnectionState.Disconnected, awaitItem())
            c.connect()
            assertEquals(CcBridgeConnectionState.Connecting, awaitItem())
            assertEquals(CcBridgeConnectionState.Authenticating, awaitItem())
            assertEquals(CcBridgeConnectionState.Connected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `auth failure sets Error state`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":false,"error":{"code":"AUTH_FAILED","message":"Invalid token"}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connectionState.test {
            assertEquals(CcBridgeConnectionState.Disconnected, awaitItem())
            c.connect()
            assertEquals(CcBridgeConnectionState.Connecting, awaitItem())
            assertEquals(CcBridgeConnectionState.Authenticating, awaitItem())
            val error = awaitItem()
            assertTrue("Expected Error but got $error", error is CcBridgeConnectionState.Error)
            assertTrue((error as CcBridgeConnectionState.Error).message.contains("Invalid token"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Event parsing ───────────────────────────────────────────────

    @Test
    fun `session stream text_delta event is parsed correctly`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                    // After auth, send a stream event
                    webSocket.send("""{"type":"event","event":"session.stream","sessionId":"s1","data":{"streamEvent":{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.events.test {
            c.connect()

            // Skip ConnectionChanged(Connected) event
            var event = awaitItem()
            while (event is CcBridgeEvent.ConnectionChanged) {
                event = awaitItem()
            }

            assertTrue("Expected SessionStream but got $event", event is CcBridgeEvent.SessionStream)
            val stream = event as CcBridgeEvent.SessionStream
            assertEquals("s1", stream.sessionId)
            assertEquals("text_delta", stream.kind)
            assertEquals("Hello", stream.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session stream tool_use event is parsed correctly`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                    webSocket.send("""{"type":"event","event":"session.stream","sessionId":"s1","data":{"streamEvent":{"type":"content_block_start","content_block":{"type":"tool_use","name":"Read"}}}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.events.test {
            c.connect()
            var event = awaitItem()
            while (event is CcBridgeEvent.ConnectionChanged) {
                event = awaitItem()
            }
            assertTrue(event is CcBridgeEvent.SessionStream)
            val stream = event as CcBridgeEvent.SessionStream
            assertEquals("tool_use", stream.kind)
            assertEquals("Read", stream.toolName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session status event is parsed correctly`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                    webSocket.send("""{"type":"event","event":"session.status","sessionId":"s1","data":{"status":"streaming"}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.events.test {
            c.connect()
            var event = awaitItem()
            while (event is CcBridgeEvent.ConnectionChanged) {
                event = awaitItem()
            }
            assertTrue(event is CcBridgeEvent.SessionStatus)
            assertEquals("s1", (event as CcBridgeEvent.SessionStatus).sessionId)
            assertEquals("streaming", event.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session error event is parsed correctly`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                    webSocket.send("""{"type":"event","event":"session.error","sessionId":"s1","data":{"message":"Something broke"}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.events.test {
            c.connect()
            var event = awaitItem()
            while (event is CcBridgeEvent.ConnectionChanged) {
                event = awaitItem()
            }
            assertTrue(event is CcBridgeEvent.SessionError)
            assertEquals("Something broke", (event as CcBridgeEvent.SessionError).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session closed event is parsed correctly`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                    webSocket.send("""{"type":"event","event":"session.closed","sessionId":"s1","data":{"reason":"user_destroyed"}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.events.test {
            c.connect()
            var event = awaitItem()
            while (event is CcBridgeEvent.ConnectionChanged) {
                event = awaitItem()
            }
            assertTrue(event is CcBridgeEvent.SessionClosed)
            assertEquals("user_destroyed", (event as CcBridgeEvent.SessionClosed).reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `session permission event is parsed correctly`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                    webSocket.send("""{"type":"event","event":"session.permission","sessionId":"s1","data":{"requestId":"perm-1","toolName":"Bash","input":{"command":"rm -rf /"}}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.events.test {
            c.connect()
            var event = awaitItem()
            while (event is CcBridgeEvent.ConnectionChanged) {
                event = awaitItem()
            }
            assertTrue(event is CcBridgeEvent.SessionPermission)
            val perm = event as CcBridgeEvent.SessionPermission
            assertEquals("s1", perm.sessionId)
            assertEquals("perm-1", perm.requestId)
            assertEquals("Bash", perm.toolName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onFailure / reconnect ───────────────────────────────────────

    // Flaky: reconnect uses real-time delay on Dispatchers.Default while runTest uses virtual time.
    // Turbine awaitItem() cannot reliably observe the reconnect state transition.
    @Ignore("Flaky due to virtual/real time mismatch in reconnect delay")
    @Test
    fun `onFailure emits Error event and schedules reconnect`() = runTest {
        // Server that immediately closes the connection after upgrade
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.close(1001, "Going away")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        // Enqueue another for the reconnect attempt
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
        }))

        val c = createClient()
        c.connectionState.test(timeout = 10.seconds) {
            assertEquals(CcBridgeConnectionState.Disconnected, awaitItem())
            c.connect()
            assertEquals(CcBridgeConnectionState.Connecting, awaitItem())

            // Wait for the Error state from onClosed (server closed with 1001)
            var state = awaitItem()
            // Could be Disconnected (from onClosed) or Error (from reconnect schedule)
            // The client calls scheduleReconnect on non-intentional close
            while (state !is CcBridgeConnectionState.Error && state !is CcBridgeConnectionState.Connecting) {
                state = awaitItem()
            }
            // Eventually should attempt reconnect
            if (state is CcBridgeConnectionState.Error) {
                // Wait for the reconnect Connecting state
                val reconnecting = awaitItem()
                assertTrue(
                    "Expected Connecting for reconnect, got $reconnecting",
                    reconnecting is CcBridgeConnectionState.Connecting || reconnecting is CcBridgeConnectionState.Error,
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── onNetworkChanged ────────────────────────────────────────────

    @Test
    fun `onNetworkChanged is no-op when disconnected`() {
        val c = createClient()
        c.onNetworkChanged()
        assertTrue(c.connectionState.value is CcBridgeConnectionState.Disconnected)
    }

    @Test
    fun `onNetworkChanged is no-op after intentional disconnect`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {}
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connect()
        Thread.sleep(200)
        c.disconnect()
        assertTrue(c.connectionState.value is CcBridgeConnectionState.Disconnected)

        c.onNetworkChanged()
        assertTrue(c.connectionState.value is CcBridgeConnectionState.Disconnected)
    }

    @Test
    fun `onNetworkChanged triggers reconnect when connected`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connectionState.test {
            assertEquals(CcBridgeConnectionState.Disconnected, awaitItem())
            c.connect()
            assertEquals(CcBridgeConnectionState.Connecting, awaitItem())
            assertEquals(CcBridgeConnectionState.Authenticating, awaitItem())
            assertEquals(CcBridgeConnectionState.Connected, awaitItem())

            c.onNetworkChanged()

            // Should reconnect — transitions through Connecting
            var sawReconnecting = false
            while (!sawReconnecting) {
                val next = awaitItem()
                if (next is CcBridgeConnectionState.Connecting) {
                    sawReconnecting = true
                }
            }
            assertTrue("Should reconnect after network change", sawReconnecting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Empty URL ───────────────────────────────────────────────────

    @Test
    fun `connect with blank URL stays Disconnected`() {
        client = CcBridgeClient(url = "", token = "tok", scope = scope)
        client.connect()
        assertTrue(client.connectionState.value is CcBridgeConnectionState.Disconnected)
    }

    // ── Request when not connected ──────────────────────────────────

    @Test
    fun `sendRequest returns NOT_CONNECTED when WebSocket is null`() = runTest {
        val c = createClient()
        // Don't connect — webSocket is null
        val response = c.listSessions()
        assertEquals(false, response.ok)
        assertEquals("NOT_CONNECTED", response.error?.code)
    }

    // ── Response correlation ────────────────────────────────────────

    @Test
    fun `response is correlated to pending request`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"event","event":"auth.challenge"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                if (envelope.method == "auth") {
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true}""")
                } else if (envelope.method == "sessions.list") {
                    webSocket.send("""{"type":"res","id":"${envelope.id}","ok":true,"payload":{"sessions":[]}}""")
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connect()
        // Poll for Connected state (OkHttp runs on real threads, not virtual time)
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                while (c.connectionState.value !is CcBridgeConnectionState.Connected) {
                    kotlinx.coroutines.delay(50)
                }
            }
        }

        val response = withContext(Dispatchers.Default) { c.listSessions() }
        assertTrue("Expected ok response", response.ok)
    }
}
