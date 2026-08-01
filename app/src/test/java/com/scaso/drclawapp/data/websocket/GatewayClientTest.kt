package com.scaso.drclawapp.data.websocket

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
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
import org.junit.Test
import java.util.concurrent.TimeUnit

class GatewayClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GatewayClient
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
        try { client.disconnect() } catch (_: Exception) { }
        try { server.shutdown() } catch (_: Exception) { }
    }

    private fun createClient(url: String = server.url("/").toString().replace("http://", "ws://")): GatewayClient {
        return GatewayClient(
            url = url,
            token = "test-token",
            scope = scope,
            okHttpClient = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
        ).also { client = it }
    }

    @Test
    fun `initial state is Disconnected`() {
        val c = createClient()
        assertTrue(c.connectionState.value is ConnectionState.Disconnected)
    }

    @Test
    fun `connectionState transitions to Connecting on connect`() = runTest {
        // Enqueue a WebSocket upgrade response
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {}
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()

        c.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            c.connect()
            val next = awaitItem()
            assertTrue(
                "Expected Connecting but got $next",
                next is ConnectionState.Connecting,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect sets state to Disconnected`() = runTest {
        val c = createClient()
        c.disconnect()
        assertTrue(c.connectionState.value is ConnectionState.Disconnected)
    }

    @Test
    fun `RequestFrame format matches protocol`() {
        val frame = RequestFrame(
            id = "req-1",
            method = "auth.connect",
            params = json.encodeToJsonElement(
                mapOf("token" to "tok", "device_id" to "dev-1")
            ),
        )
        val serialized = json.encodeToString(RequestFrame.serializer(), frame)
        assertTrue(serialized.contains("\"type\":\"req\""))
        assertTrue(serialized.contains("\"method\":\"auth.connect\""))
        assertTrue(serialized.contains("\"token\":\"tok\""))
    }

    @Test
    fun `auth connect params include token and device_id`() {
        val params = mapOf("token" to "test-token", "device_id" to "dev-123")
        val serialized = json.encodeToString(params)
        assertTrue(serialized.contains("\"token\":\"test-token\""))
        assertTrue(serialized.contains("\"device_id\":\"dev-123\""))
    }

    @Test
    fun `chat send params include sessionKey when set`() {
        val params = ChatSendParams(
            message = "hello",
            idempotencyKey = "key-1",
            sessionKey = "session-abc",
        )
        val serialized = json.encodeToString(ChatSendParams.serializer(), params)
        assertTrue(serialized.contains("\"sessionKey\":\"session-abc\""))
    }

    // ── onNetworkChanged tests ──────────────────────────────────────

    @Test
    fun `onNetworkChanged is no-op when disconnected`() = runTest {
        val c = createClient()
        // Should not throw or change state
        c.onNetworkChanged()
        assertTrue(c.connectionState.value is ConnectionState.Disconnected)
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
        // Wait for Connecting state
        Thread.sleep(200)
        c.disconnect()
        assertTrue(c.connectionState.value is ConnectionState.Disconnected)

        // onNetworkChanged should be no-op after intentional disconnect
        c.onNetworkChanged()
        assertTrue(c.connectionState.value is ConnectionState.Disconnected)
    }

    @Test
    fun `onNetworkChanged triggers reconnect when connected`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth.connect\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    val authOk = """{"type":"res","id":"${envelope.id}","ok":true,"payload":{"protocol":4,"server":{"name":"ironjaw","version":"0.1.0"},"session_id":"sess-1"}}"""
                    webSocket.send(authOk)
                }
            }
        }
        // Enqueue two upgrades: initial connect + reconnect after network change
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()

        c.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            c.connect()
            assertEquals(ConnectionState.Connecting::class, awaitItem()::class)
            assertEquals(ConnectionState.Authenticating::class, awaitItem()::class)
            assertEquals(ConnectionState.Connected::class, awaitItem()::class)

            // Simulate network change
            c.onNetworkChanged()

            // Should transition through Error → Connecting (reconnect).
            // The exact Error message depends on race between onFailure("Socket closed")
            // and the onNetworkChanged coroutine — both are valid.
            var sawReconnecting = false
            while (!sawReconnecting) {
                val next = awaitItem()
                if (next is ConnectionState.Connecting) {
                    sawReconnecting = true
                } else {
                    assertTrue(
                        "Expected Error or Connecting after network change, got $next",
                        next is ConnectionState.Error,
                    )
                }
            }
            assertTrue("Should have reconnected after network change", sawReconnecting)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onNetworkChanged triggers reconnect when in Error state`() = runTest {
        // Server that accepts connection but then we'll trigger network change
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {}
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connect()
        Thread.sleep(300)

        // Should not be Disconnected (it connected)
        val state = c.connectionState.value
        assertTrue(
            "Expected non-Disconnected state, got $state",
            state !is ConnectionState.Disconnected,
        )

        // Trigger network change — should attempt reconnect regardless of current state
        c.onNetworkChanged()
        Thread.sleep(100)

        val afterChange = c.connectionState.value
        assertTrue(
            "Expected Error or Connecting after network change, got $afterChange",
            afterChange is ConnectionState.Error || afterChange is ConnectionState.Connecting,
        )
    }

    // ── AuthFailed terminal state tests ───────────────────────────────

    @Test
    fun `auth-reject response sets AuthFailed and does not schedule reconnect`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {}
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("\"method\":\"auth.connect\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    val authReject = """{"type":"res","id":"${envelope.id}","ok":false,"error":{"code":"UNAUTHORIZED","message":"Invalid token"}}"""
                    webSocket.send(authReject)
                    // Real Ironjaw behavior: server closes the socket after rejecting auth.
                    webSocket.close(1008, "Unauthorized")
                }
            }
        }
        // Only ONE upgrade enqueued: a reconnect attempt would send a second WS
        // upgrade request that this server has nothing queued to answer.
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connect()

        // Real (non-virtual) delay: GatewayClient's internal scope runs on
        // Dispatchers.Default, not the test dispatcher, so runTest's virtual
        // clock does not apply here — match the Thread.sleep pattern used
        // elsewhere in this file for onClosed/reconnect timing.
        Thread.sleep(500)
        val afterReject = c.connectionState.value
        assertTrue("Expected AuthFailed, got $afterReject", afterReject is ConnectionState.AuthFailed)
        assertEquals("Invalid token", (afterReject as ConnectionState.AuthFailed).message)

        // Wait past the first backoff window (1000ms) — no reconnect should fire,
        // and the terminal state should not be overwritten by onClosed.
        Thread.sleep(1300)
        val afterBackoffWindow = c.connectionState.value
        assertTrue(
            "Expected state to remain AuthFailed (no reconnect), got $afterBackoffWindow",
            afterBackoffWindow is ConnectionState.AuthFailed,
        )
        assertEquals(
            "Expected exactly one WS upgrade request (no reconnect attempt)",
            1,
            server.requestCount,
        )
    }

    // ── Full handshake test ──────────────────────────────────────────

    @Test
    fun `full v4 handshake via MockWebServer`() = runTest {
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // v4: no challenge event — client sends auth.connect immediately
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Client sends auth.connect, server responds with session_id
                if (text.contains("\"method\":\"auth.connect\"")) {
                    val envelope = json.decodeFromString(FrameEnvelope.serializer(), text)
                    val authOk = """{"type":"res","id":"${envelope.id}","ok":true,"payload":{"protocol":4,"server":{"name":"ironjaw","version":"0.1.0"},"session_id":"sess-1"}}"""
                    webSocket.send(authOk)
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()

        c.connectionState.test {
            assertEquals(ConnectionState.Disconnected, awaitItem())
            c.connect()

            val connecting = awaitItem()
            assertTrue("Expected Connecting, got $connecting", connecting is ConnectionState.Connecting)

            val authenticating = awaitItem()
            assertTrue("Expected Authenticating, got $authenticating", authenticating is ConnectionState.Authenticating)

            val connected = awaitItem()
            assertTrue("Expected Connected, got $connected", connected is ConnectionState.Connected)

            val info = (connected as ConnectionState.Connected).serverInfo
            assertEquals(4, info.protocol)
            assertEquals("ironjaw", info.server?.name)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── cmd frame HITL gating tests ────────────────────────────────────

    @Test
    fun `cmd frame is denied when no approval gate is configured`() = runTest {
        // Fail-closed default: a freshly constructed client has no cmdApprovalGate wired up,
        // so an incoming cmd frame must be denied rather than silently executed.
        val serverReceived = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"cmd","id":"cmd-1","tool":"device.info","params":{}}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                serverReceived.add(text)
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.connect()

        var cmdRes: String? = null
        var attempts = 0
        while (cmdRes == null && attempts < 50) {
            cmdRes = serverReceived.firstOrNull { it.contains("\"type\":\"cmd.res\"") }
            if (cmdRes == null) {
                Thread.sleep(100)
                attempts++
            }
        }

        assertTrue("Expected a cmd.res frame, got: $serverReceived", cmdRes != null)
        assertTrue("Expected id cmd-1 in response: $cmdRes", cmdRes!!.contains("\"id\":\"cmd-1\""))
        assertTrue("Expected ok:false (fail closed), got: $cmdRes", cmdRes.contains("\"ok\":false"))
    }

    @Test
    fun `cmd frame is dispatched when approval gate approves`() = runTest {
        val serverReceived = java.util.concurrent.CopyOnWriteArrayList<String>()
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"cmd","id":"cmd-2","tool":"device.info","params":{}}""")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                serverReceived.add(text)
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        c.cmdApprovalGate = object : com.scaso.drclawapp.data.approval.CmdApprovalGate {
            override suspend fun requestApproval(
                tool: String,
                params: kotlinx.serialization.json.JsonObject,
            ): Boolean = true
        }
        c.cmdDispatcher = object : CmdDispatcherInterface {
            override suspend fun dispatch(
                tool: String,
                params: kotlinx.serialization.json.JsonObject,
            ): kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonObject(emptyMap())
        }
        c.connect()

        var cmdRes: String? = null
        var attempts = 0
        while (cmdRes == null && attempts < 50) {
            cmdRes = serverReceived.firstOrNull { it.contains("\"type\":\"cmd.res\"") }
            if (cmdRes == null) {
                Thread.sleep(100)
                attempts++
            }
        }

        assertTrue("Expected a cmd.res frame, got: $serverReceived", cmdRes != null)
        assertTrue("Expected id cmd-2 in response: $cmdRes", cmdRes!!.contains("\"id\":\"cmd-2\""))
        assertTrue("Expected ok:true when approved, got: $cmdRes", cmdRes.contains("\"ok\":true"))
    }
}
