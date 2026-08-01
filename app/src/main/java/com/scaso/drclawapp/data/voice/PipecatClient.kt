package com.scaso.drclawapp.data.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

/**
 * Connection state for the Pipecat voice server.
 */
enum class PipecatConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

/**
 * Events emitted by the Pipecat server.
 */
sealed class PipecatEvent {
    /** Raw audio frame from the server (PCM 16-bit, 16kHz, mono). */
    data class AudioFrame(val pcmData: ByteArray) : PipecatEvent() {
        override fun equals(other: Any?): Boolean =
            other is AudioFrame && pcmData.contentEquals(other.pcmData)
        override fun hashCode(): Int = pcmData.contentHashCode()
    }

    /** Server-side transcription of user speech (for display). */
    data class UserTranscript(val text: String, val isFinal: Boolean) : PipecatEvent()

    /** Server-side transcription of bot speech (for display). */
    data class BotTranscript(val text: String, val isFinal: Boolean) : PipecatEvent()

    /** Bot started speaking. */
    data object BotStartedSpeaking : PipecatEvent()

    /** Bot stopped speaking. */
    data object BotStoppedSpeaking : PipecatEvent()

    /** User started speaking (server-side VAD detected). */
    data object UserStartedSpeaking : PipecatEvent()

    /** User stopped speaking (server-side VAD detected). */
    data object UserStoppedSpeaking : PipecatEvent()

    /** Server signaled an error. */
    data class Error(val message: String) : PipecatEvent()

    /** Connection closed. */
    data class Closed(val code: Int, val reason: String) : PipecatEvent()
}

/**
 * WebSocket client for a Pipecat voice server.
 *
 * Pipecat uses a simple binary WebSocket protocol:
 * - Client sends raw PCM audio frames (16kHz, mono, 16-bit)
 * - Server sends back PCM audio frames + JSON control messages
 *
 * JSON messages are UTF-8 text frames; audio is binary frames.
 *
 * No Android imports -- KMP-extractable.
 */
open class PipecatClient(
    private val scope: CoroutineScope,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build(),
) {
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private companion object {
        private const val MAX_BACKOFF_MS = 30_000L
    }

    private val _connectionState = MutableStateFlow(PipecatConnectionState.DISCONNECTED)
    val connectionState: StateFlow<PipecatConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<PipecatEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PipecatEvent> = _events.asSharedFlow()

    private var currentUrl: String? = null

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Connect to a Pipecat voice server at the given WebSocket URL.
     */
    fun connect(url: String) {
        if (_connectionState.value == PipecatConnectionState.CONNECTED ||
            _connectionState.value == PipecatConnectionState.CONNECTING
        ) {
            return
        }
        reconnectAttempts = 0
        doConnect(url)
    }

    private fun doConnect(url: String) {
        currentUrl = url
        reconnectJob?.cancel()
        _connectionState.value = PipecatConnectionState.CONNECTING

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = PipecatConnectionState.CONNECTED
                reconnectAttempts = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = PipecatConnectionState.DISCONNECTED
                _events.tryEmit(PipecatEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = PipecatConnectionState.ERROR
                _events.tryEmit(PipecatEvent.Error(t.message ?: "Connection failed"))
                scheduleReconnect()
            }
        })
    }

    /**
     * Disconnect from the Pipecat server.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = PipecatConnectionState.DISCONNECTED
    }

    /**
     * Send a raw PCM audio frame to the server.
     * Audio must be 16kHz, mono, 16-bit PCM (little-endian).
     *
     * @return true if the frame was queued for sending.
     */
    fun sendAudioFrame(pcmData: ByteArray): Boolean {
        if (_connectionState.value != PipecatConnectionState.CONNECTED) return false
        return webSocket?.send(pcmData.toByteString()) ?: false
    }

    /**
     * Send a text control message to the server.
     */
    fun sendControlMessage(message: String): Boolean {
        if (_connectionState.value != PipecatConnectionState.CONNECTED) return false
        return webSocket?.send(message) ?: false
    }

    /**
     * Send an interrupt signal to stop bot speech.
     */
    fun sendInterrupt(): Boolean {
        return sendControlMessage("""{"type":"interrupt"}""")
    }

    open val isConnected: Boolean
        get() = _connectionState.value == PipecatConnectionState.CONNECTED

    private fun handleTextMessage(text: String) {
        try {
            val jsonObj = json.parseToJsonElement(text)
            if (jsonObj is kotlinx.serialization.json.JsonObject) {
                val type = jsonObj["type"]
                    ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }

                when (type) {
                    "user_transcript", "user-transcript" -> {
                        val transcript = jsonObj["text"]
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                        val isFinal = jsonObj["final"]
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() } ?: false
                        _events.tryEmit(PipecatEvent.UserTranscript(transcript, isFinal))
                    }
                    "bot_transcript", "bot-transcript" -> {
                        val transcript = jsonObj["text"]
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
                        val isFinal = jsonObj["final"]
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull() } ?: false
                        _events.tryEmit(PipecatEvent.BotTranscript(transcript, isFinal))
                    }
                    "bot_started_speaking", "bot-started-speaking" -> {
                        _events.tryEmit(PipecatEvent.BotStartedSpeaking)
                    }
                    "bot_stopped_speaking", "bot-stopped-speaking" -> {
                        _events.tryEmit(PipecatEvent.BotStoppedSpeaking)
                    }
                    "user_started_speaking", "user-started-speaking" -> {
                        _events.tryEmit(PipecatEvent.UserStartedSpeaking)
                    }
                    "user_stopped_speaking", "user-stopped-speaking" -> {
                        _events.tryEmit(PipecatEvent.UserStoppedSpeaking)
                    }
                    "error" -> {
                        val message = jsonObj["message"]
                            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: "Unknown error"
                        _events.tryEmit(PipecatEvent.Error(message))
                    }
                }
            }
        } catch (_: Exception) {
            // Malformed JSON -- ignore
        }
    }

    private fun handleBinaryMessage(bytes: ByteString) {
        _events.tryEmit(PipecatEvent.AudioFrame(bytes.toByteArray()))
    }

    private fun scheduleReconnect() {
        val url = currentUrl ?: return
        reconnectJob?.cancel()
        val delayMs = minOf(1000L * (1L shl minOf(reconnectAttempts, 4)), MAX_BACKOFF_MS)
        reconnectAttempts++
        reconnectJob = scope.launch {
            delay(delayMs)
            if (isActive && _connectionState.value != PipecatConnectionState.CONNECTED) {
                doConnect(url)
            }
        }
    }

    /**
     * Called when the network changes (e.g., WiFi ↔ 5G, tower handoff). Cancels the current
     * WebSocket and reconnects immediately, bypassing the in-progress backoff delay.
     * No-op if not currently connected/connecting (mirrors [disconnect] leaving DISCONNECTED).
     */
    fun onNetworkChanged() {
        val url = currentUrl ?: return
        if (_connectionState.value == PipecatConnectionState.DISCONNECTED) return
        reconnectJob?.cancel()
        reconnectAttempts = 0
        webSocket?.cancel()
        webSocket = null
        scope.launch {
            _connectionState.value = PipecatConnectionState.CONNECTING
            delay(500L)
            doConnect(url)
        }
    }
}
