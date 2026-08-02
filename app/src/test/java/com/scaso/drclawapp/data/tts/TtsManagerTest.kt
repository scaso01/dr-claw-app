package com.scaso.drclawapp.data.tts

import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.websocket.ConnectionState
import com.scaso.drclawapp.data.websocket.GatewayClient
import com.scaso.drclawapp.data.websocket.GatewayEvent
import com.scaso.drclawapp.data.websocket.ResponseFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TtsManagerTest {

    private lateinit var fakeGatewayClient: FakeTtsGatewayClient
    private lateinit var fakePreferences: FakeTtsAppPreferences
    private lateinit var fakeEngine: FakeTtsEngine
    private lateinit var fakePlayer: FakeTtsAudioPlayer
    private lateinit var manager: TtsManager

    @Before
    fun setup() {
        fakeGatewayClient = FakeTtsGatewayClient()
        fakePreferences = FakeTtsAppPreferences()
        fakeEngine = FakeTtsEngine()
        fakePlayer = FakeTtsAudioPlayer()
        manager = TtsManager(fakeGatewayClient, fakePreferences, fakeEngine, fakePlayer)
    }

    @Test
    fun `speak returns true and skips Piper when server succeeds`() = runTest {
        fakeGatewayClient.response = ResponseFrame(id = "1", ok = true, payload = JsonPrimitive("base64audio"))

        val result = manager.speak("hello")

        assertTrue(result)
        assertEquals(0, fakeEngine.generateCalls.size)
        assertEquals(0, fakePlayer.playCalls.size)
    }

    @Test
    fun `speak falls back to Piper when server responds not ok`() = runTest {
        fakeGatewayClient.response = ResponseFrame(id = "1", ok = false)
        fakeEngine.ready = true
        fakeEngine.generateResult = floatArrayOf(0.1f, 0.2f)

        val result = manager.speak("hello")

        assertTrue(result)
        assertEquals(listOf("hello"), fakeEngine.generateCalls.map { it.first })
        assertEquals(1, fakePlayer.playCalls.size)
        assertEquals(fakeEngine.sampleRate, fakePlayer.playCalls[0].second)
    }

    @Test
    fun `speak falls back to Piper when server throws`() = runTest {
        fakeGatewayClient.shouldThrow = true
        fakeEngine.ready = true
        fakeEngine.generateResult = floatArrayOf(0.1f)

        val result = manager.speak("hello")

        assertTrue(result)
        assertEquals(1, fakePlayer.playCalls.size)
    }

    @Test
    fun `speak returns false when server fails and Piper not ready`() = runTest {
        fakeGatewayClient.response = ResponseFrame(id = "1", ok = false)
        fakeEngine.ready = false

        val result = manager.speak("hello")

        assertFalse(result)
        assertEquals(0, fakePlayer.playCalls.size)
    }

    @Test
    fun `speak returns false when Piper ready but generate returns null`() = runTest {
        fakeGatewayClient.response = ResponseFrame(id = "1", ok = false)
        fakeEngine.ready = true
        fakeEngine.generateResult = null

        val result = manager.speak("hello")

        assertFalse(result)
        assertEquals(0, fakePlayer.playCalls.size)
    }

    @Test
    fun `speakLocal returns false when Piper not ready`() = runTest {
        fakeEngine.ready = false

        val result = manager.speakLocal("hello")

        assertFalse(result)
        assertEquals(0, fakeGatewayClient.calls)
    }

    @Test
    fun `speakLocal generates and plays with given speed when ready`() = runTest {
        fakeEngine.ready = true
        fakeEngine.generateResult = floatArrayOf(0.5f)

        val result = manager.speakLocal("hello", speed = 1.5f)

        assertTrue(result)
        assertEquals(listOf("hello" to 1.5f), fakeEngine.generateCalls)
        assertEquals(1, fakePlayer.playCalls.size)
    }

    @Test
    fun `initialize delegates to engine`() = runTest {
        manager.initialize()

        assertTrue(fakeEngine.initializeCalled)
    }

    @Test
    fun `isPiperReady reflects engine state`() {
        fakeEngine.ready = true
        assertTrue(manager.isPiperReady)

        fakeEngine.ready = false
        assertFalse(manager.isPiperReady)
    }

    @Test
    fun `release delegates to engine and audio player`() {
        manager.release()

        assertTrue(fakeEngine.releaseCalled)
        assertTrue(fakePlayer.releaseCalled)
    }
}

// ── Fakes ────────────────────────────────────────────────────────────

private class FakeTtsGatewayClient : GatewayClient(
    url = "ws://fake",
    token = "fake",
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    var response: ResponseFrame = ResponseFrame(id = "0", ok = true)
    var shouldThrow = false
    var calls = 0

    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.Connected(fakeHelloOk()))

    override val events: SharedFlow<GatewayEvent> =
        MutableSharedFlow(replay = 64, extraBufferCapacity = 64)

    override suspend fun convertTextToSpeech(text: String, voice: String?): ResponseFrame {
        calls++
        if (shouldThrow) throw RuntimeException("Test TTS exception")
        return response
    }
}

private fun fakeHelloOk() = com.scaso.drclawapp.data.websocket.HelloOk(
    protocol = 4,
    server = com.scaso.drclawapp.data.websocket.ServerInfo(name = "fake", version = "0"),
    policy = com.scaso.drclawapp.data.websocket.Policy(),
)

/** Avoids Android DataStore / Context dependency in JVM unit tests. */
private class FakeTtsAppPreferences : AppPreferences(null) {
    var voice = "kitt"
    override val ttsVoice = flowOf(voice)
}

private class FakeTtsEngine : TtsEngine {
    var ready = false
    var generateResult: FloatArray? = null
    override var sampleRate: Int = 22050
    val generateCalls = mutableListOf<Pair<String, Float>>()
    var initializeCalled = false
    var releaseCalled = false

    override val isReady: Boolean get() = ready

    override suspend fun initialize() {
        initializeCalled = true
    }

    override suspend fun generate(text: String, speed: Float): FloatArray? {
        generateCalls.add(text to speed)
        return generateResult
    }

    override fun release() {
        releaseCalled = true
    }
}

private class FakeTtsAudioPlayer : TtsAudioPlayer {
    val playCalls = mutableListOf<Pair<FloatArray, Int>>()
    var releaseCalled = false

    override suspend fun play(samples: FloatArray, sampleRate: Int) {
        playCalls.add(samples to sampleRate)
    }

    override fun release() {
        releaseCalled = true
    }
}
