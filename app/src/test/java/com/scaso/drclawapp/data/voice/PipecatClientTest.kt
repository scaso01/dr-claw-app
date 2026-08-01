package com.scaso.drclawapp.data.voice

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PipecatClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: PipecatClient
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    private fun wsUrl(): String =
        server.url("/").toString().replace("http://", "ws://")

    private fun createClient(): PipecatClient {
        return PipecatClient(
            scope = scope,
            okHttpClient = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
        ).also { client = it }
    }

    /** Helper: enqueue WS upgrade and return a latch + holder for the server-side WebSocket. */
    private fun enqueueUpgradeWithLatch(): Pair<CountDownLatch, Array<WebSocket?>> {
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<WebSocket>(1)
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                holder[0] = webSocket
                latch.countDown()
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        return Pair(latch, holder)
    }

    /** Helper: connect and wait until CONNECTED state is reached. */
    private suspend fun connectAndWait(c: PipecatClient, latch: CountDownLatch) {
        c.connect(wsUrl())
        latch.await(5, TimeUnit.SECONDS)
        // Also wait for client-side state update
        c.connectionState.test {
            while (awaitItem() != PipecatConnectionState.CONNECTED) { /* spin */ }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initial state is DISCONNECTED`() {
        val c = createClient()
        assertEquals(PipecatConnectionState.DISCONNECTED, c.connectionState.value)
        assertFalse(c.isConnected)
    }

    @Test
    fun `connect transitions to CONNECTING then CONNECTED`() = runTest {
        val (latch, _) = enqueueUpgradeWithLatch()
        val c = createClient()

        c.connectionState.test {
            assertEquals(PipecatConnectionState.DISCONNECTED, awaitItem())
            c.connect(wsUrl())
            val next = awaitItem()
            assertTrue(
                "Expected CONNECTING but got $next",
                next == PipecatConnectionState.CONNECTING || next == PipecatConnectionState.CONNECTED,
            )
            if (next == PipecatConnectionState.CONNECTING) {
                assertEquals(PipecatConnectionState.CONNECTED, awaitItem())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `disconnect sets state to DISCONNECTED`() = runTest {
        val c = createClient()
        c.disconnect()
        assertEquals(PipecatConnectionState.DISCONNECTED, c.connectionState.value)
    }

    @Test
    fun `sendAudioFrame returns false when not connected`() {
        val c = createClient()
        assertFalse(c.sendAudioFrame(ByteArray(640)))
    }

    @Test
    fun `sendControlMessage returns false when not connected`() {
        val c = createClient()
        assertFalse(c.sendControlMessage("""{"type":"interrupt"}"""))
    }

    @Test
    fun `text message parsed as user transcript`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        c.events.test {
            holder[0]?.send("""{"type":"user_transcript","text":"hello world","final":true}""")
            val event = awaitItem()
            assertTrue("Expected UserTranscript but got $event", event is PipecatEvent.UserTranscript)
            val transcript = event as PipecatEvent.UserTranscript
            assertEquals("hello world", transcript.text)
            assertTrue(transcript.isFinal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `text message parsed as bot transcript`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        c.events.test {
            holder[0]?.send("""{"type":"bot_transcript","text":"I am Dr. CLAW","final":false}""")
            val event = awaitItem()
            assertTrue("Expected BotTranscript but got $event", event is PipecatEvent.BotTranscript)
            val transcript = event as PipecatEvent.BotTranscript
            assertEquals("I am Dr. CLAW", transcript.text)
            assertFalse(transcript.isFinal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `binary message emitted as AudioFrame`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        val testAudio = ByteArray(640) { (it % 256).toByte() }

        c.events.test {
            holder[0]?.send(testAudio.toByteString())
            val event = awaitItem()
            assertTrue("Expected AudioFrame but got $event", event is PipecatEvent.AudioFrame)
            val frame = event as PipecatEvent.AudioFrame
            assertTrue(testAudio.contentEquals(frame.pcmData))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bot speaking events parsed correctly`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        c.events.test {
            holder[0]?.send("""{"type":"bot_started_speaking"}""")
            val started = awaitItem()
            assertTrue(started is PipecatEvent.BotStartedSpeaking)

            holder[0]?.send("""{"type":"bot_stopped_speaking"}""")
            val stopped = awaitItem()
            assertTrue(stopped is PipecatEvent.BotStoppedSpeaking)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `user speaking events parsed correctly`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        c.events.test {
            holder[0]?.send("""{"type":"user_started_speaking"}""")
            val started = awaitItem()
            assertTrue(started is PipecatEvent.UserStartedSpeaking)

            holder[0]?.send("""{"type":"user_stopped_speaking"}""")
            val stopped = awaitItem()
            assertTrue(stopped is PipecatEvent.UserStoppedSpeaking)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `error event parsed correctly`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        c.events.test {
            holder[0]?.send("""{"type":"error","message":"something broke"}""")
            val event = awaitItem()
            assertTrue(event is PipecatEvent.Error)
            assertEquals("something broke", (event as PipecatEvent.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `hyphenated event types also parsed`() = runTest {
        val (latch, holder) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        c.events.test {
            holder[0]?.send("""{"type":"user-transcript","text":"hello","final":true}""")
            val event = awaitItem()
            assertTrue(event is PipecatEvent.UserTranscript)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendInterrupt sends interrupt JSON`() = runTest {
        var receivedMessage: String? = null
        val latch = CountDownLatch(1)
        val messageLatch = CountDownLatch(1)
        val holder = arrayOfNulls<WebSocket>(1)
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                holder[0] = webSocket
                latch.countDown()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                receivedMessage = text
                messageLatch.countDown()
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val c = createClient()
        connectAndWait(c, latch)

        assertTrue(c.sendInterrupt())
        messageLatch.await(2, TimeUnit.SECONDS)
        assertEquals("""{"type":"interrupt"}""", receivedMessage)
    }

    @Test
    fun `connect is idempotent when already connected`() = runTest {
        val (latch, _) = enqueueUpgradeWithLatch()
        val c = createClient()
        connectAndWait(c, latch)

        // Second connect should be a no-op
        c.connect(wsUrl())
        assertEquals(PipecatConnectionState.CONNECTED, c.connectionState.value)
    }
}
