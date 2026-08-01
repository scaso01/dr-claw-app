package com.scaso.drclawapp.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.voice.PipecatClient
import com.scaso.drclawapp.data.voice.PipecatConnectionState
import com.scaso.drclawapp.data.voice.PipecatEvent
import com.scaso.drclawapp.data.websocket.GatewayEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream

/**
 * Voice conversation mode.
 */
enum class VoiceMode {
    /** Legacy half-duplex: record -> STT -> send -> receive -> TTS -> play */
    HALF_DUPLEX,
    /** Full-duplex via Pipecat: simultaneous listen + speak */
    FULL_DUPLEX,
}

/**
 * State of the voice conversation pipeline.
 */
enum class VoiceState {
    /** Idle -- waiting for user to press the talk button. */
    IDLE,

    /** Actively recording via STT. */
    LISTENING,

    /** Transcription complete, sending to gateway. */
    SENDING,

    /** Waiting for the assistant response to complete. */
    WAITING,

    /** Playing the TTS audio response. */
    PLAYING,

    /** An error occurred in the pipeline. */
    ERROR,
}

/**
 * Full-duplex specific state flags (concurrent with VoiceState).
 * In full-duplex mode, multiple of these can be true simultaneously.
 */
data class FullDuplexState(
    /** Microphone is active and streaming audio to server. */
    val micActive: Boolean = false,
    /** Server-side VAD detected user speaking. */
    val userSpeaking: Boolean = false,
    /** Bot is generating/playing audio response. */
    val botSpeaking: Boolean = false,
    /** Bot was interrupted by user speech. */
    val interrupted: Boolean = false,
    /** Connected to Pipecat server. */
    val connected: Boolean = false,
)

/**
 * Manages the voice conversation pipeline with two modes:
 *
 * **Half-duplex (legacy):** linear STT -> Gateway -> TTS pipeline.
 * **Full-duplex (Pipecat):** bidirectional audio streaming with concurrent
 * listen/speak, server-side VAD, and interrupt support.
 *
 * The mode is selected based on whether a Pipecat URL is configured and
 * the full-duplex setting is enabled. Falls back to half-duplex if the
 * Pipecat server is unavailable.
 */
class VoiceConversationManager(
    private val context: Context,
    private val chatRepository: ChatRepository,
    private val scope: CoroutineScope,
    private val ttsVoice: Flow<String>,
    private val ttsManager: com.scaso.drclawapp.data.tts.TtsManager? = null,
    private val pipecatClient: PipecatClient? = null,
    initialMode: VoiceMode = VoiceMode.HALF_DUPLEX,
) {

    private val speechHelper = SpeechRecognizerHelper(context)

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _voiceMode = MutableStateFlow(initialMode)
    val voiceMode: StateFlow<VoiceMode> = _voiceMode.asStateFlow()

    private val _fullDuplexState = MutableStateFlow(FullDuplexState())
    val fullDuplexState: StateFlow<FullDuplexState> = _fullDuplexState.asStateFlow()

    private val _transcribedText = MutableStateFlow("")
    val transcribedText: StateFlow<String> = _transcribedText.asStateFlow()

    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Exposes the speech helper's real-time partial text (half-duplex mode). */
    val partialText: StateFlow<String> = speechHelper.recognizedText

    /** Exposes the speech helper's listening state (half-duplex mode). */
    val isListening: StateFlow<Boolean> = speechHelper.isListening

    private var mediaPlayer: MediaPlayer? = null
    private var eventCollectorJob: Job? = null
    private var sttObserverJob: Job? = null

    // Full-duplex audio resources
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var audioStreamJob: Job? = null
    private var pipecatEventJob: Job? = null
    private var playbackJob: Job? = null

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_MS = 20 // 20ms frames
        const val FRAME_SIZE_SAMPLES = SAMPLE_RATE * FRAME_SIZE_MS / 1000 // 320 samples
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2 // 640 bytes (16-bit)
    }

    // ========== Mode Management ==========

    fun setMode(mode: VoiceMode) {
        if (_voiceState.value != VoiceState.IDLE) {
            cancel()
        }
        _voiceMode.value = mode
    }

    // ========== Permission Check ==========

    fun hasRecordPermission(): Boolean = speechHelper.hasRecordPermission()

    // ========== Half-Duplex API (unchanged from original) ==========

    fun startListening() {
        if (_voiceMode.value == VoiceMode.FULL_DUPLEX) {
            startFullDuplex()
            return
        }
        startHalfDuplex()
    }

    fun stopListening() {
        if (_voiceMode.value == VoiceMode.FULL_DUPLEX) {
            stopFullDuplex()
            return
        }
        speechHelper.stopListening()
    }

    fun cancel() {
        if (_voiceMode.value == VoiceMode.FULL_DUPLEX) {
            stopFullDuplex()
        } else {
            speechHelper.stopListening()
            stopAudioPlayback()
            eventCollectorJob?.cancel()
            sttObserverJob?.cancel()
        }
        _voiceState.value = VoiceState.IDLE
        _fullDuplexState.value = FullDuplexState()
    }

    fun destroy() {
        cancel()
        speechHelper.destroy()
        releaseMediaPlayer()
        releaseFullDuplexAudio()
        pipecatClient?.disconnect()
    }

    // ========== Full-Duplex Implementation ==========

    private fun startFullDuplex() {
        val client = pipecatClient ?: run {
            _errorMessage.value = "Pipecat not configured"
            _voiceState.value = VoiceState.ERROR
            return
        }

        if (!client.isConnected) {
            _errorMessage.value = "Pipecat server not connected"
            _voiceState.value = VoiceState.ERROR
            return
        }

        _voiceState.value = VoiceState.LISTENING
        _transcribedText.value = ""
        _responseText.value = ""
        _errorMessage.value = null

        startAudioCapture()
        startPipecatEventListener()

        _fullDuplexState.value = _fullDuplexState.value.copy(
            micActive = true,
            connected = true,
        )
    }

    private fun stopFullDuplex() {
        stopAudioCapture()
        stopAudioPlaybackFd()
        pipecatEventJob?.cancel()
        pipecatEventJob = null
        _fullDuplexState.value = FullDuplexState()
    }

    @Suppress("MissingPermission")
    private fun startAudioCapture() {
        val bufferSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT),
            FRAME_SIZE_BYTES * 4,
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            bufferSize,
        )

        // Attach echo canceler if available
        if (AcousticEchoCanceler.isAvailable()) {
            audioRecord?.audioSessionId?.let { sessionId ->
                echoCanceler = AcousticEchoCanceler.create(sessionId)
                echoCanceler?.enabled = true
            }
        }

        audioRecord?.startRecording()

        audioStreamJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(FRAME_SIZE_BYTES)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    pipecatClient?.sendAudioFrame(buffer.copyOf(bytesRead))
                }
            }
        }
    }

    private fun stopAudioCapture() {
        audioStreamJob?.cancel()
        audioStreamJob = null
        echoCanceler?.release()
        echoCanceler = null
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) { }
        audioRecord?.release()
        audioRecord = null
    }

    private fun startPipecatEventListener() {
        val client = pipecatClient ?: return
        pipecatEventJob?.cancel()
        pipecatEventJob = scope.launch {
            client.events.collect { event ->
                when (event) {
                    is PipecatEvent.AudioFrame -> {
                        playAudioFrameFd(event.pcmData)
                    }
                    is PipecatEvent.UserTranscript -> {
                        _transcribedText.value = event.text
                        if (event.isFinal) {
                            _fullDuplexState.value = _fullDuplexState.value.copy(
                                userSpeaking = false,
                            )
                        }
                    }
                    is PipecatEvent.BotTranscript -> {
                        _responseText.value = if (event.isFinal) {
                            event.text
                        } else {
                            _responseText.value + event.text
                        }
                    }
                    is PipecatEvent.BotStartedSpeaking -> {
                        _fullDuplexState.value = _fullDuplexState.value.copy(
                            botSpeaking = true,
                            interrupted = false,
                        )
                        _voiceState.value = VoiceState.PLAYING
                    }
                    is PipecatEvent.BotStoppedSpeaking -> {
                        _fullDuplexState.value = _fullDuplexState.value.copy(
                            botSpeaking = false,
                        )
                        if (_fullDuplexState.value.micActive) {
                            _voiceState.value = VoiceState.LISTENING
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }
                    is PipecatEvent.UserStartedSpeaking -> {
                        _fullDuplexState.value = _fullDuplexState.value.copy(
                            userSpeaking = true,
                        )
                        // Interrupt bot if it's speaking
                        if (_fullDuplexState.value.botSpeaking) {
                            client.sendInterrupt()
                            stopAudioPlaybackFd()
                            _fullDuplexState.value = _fullDuplexState.value.copy(
                                interrupted = true,
                                botSpeaking = false,
                            )
                        }
                        _voiceState.value = VoiceState.LISTENING
                        _transcribedText.value = ""
                    }
                    is PipecatEvent.UserStoppedSpeaking -> {
                        _fullDuplexState.value = _fullDuplexState.value.copy(
                            userSpeaking = false,
                        )
                        _voiceState.value = VoiceState.WAITING
                    }
                    is PipecatEvent.Error -> {
                        _errorMessage.value = event.message
                        _voiceState.value = VoiceState.ERROR
                    }
                    is PipecatEvent.Closed -> {
                        _fullDuplexState.value = _fullDuplexState.value.copy(
                            connected = false,
                        )
                        if (_voiceState.value != VoiceState.IDLE) {
                            _errorMessage.value = "Pipecat connection closed"
                            _voiceState.value = VoiceState.ERROR
                        }
                    }
                }
            }
        }
    }

    private fun initAudioTrackFd(): AudioTrack {
        val existing = audioTrack
        if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) {
            return existing
        }

        val bufferSize = maxOf(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT),
            FRAME_SIZE_BYTES * 8,
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()
        return track
    }

    private fun playAudioFrameFd(pcmData: ByteArray) {
        scope.launch(Dispatchers.IO) {
            val track = initAudioTrackFd()
            track.write(pcmData, 0, pcmData.size)
        }
    }

    private fun stopAudioPlaybackFd() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: IllegalStateException) { }
    }

    private fun releaseFullDuplexAudio() {
        stopAudioCapture()
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) { }
        audioTrack?.release()
        audioTrack = null
    }

    // ========== Half-Duplex Implementation (unchanged) ==========

    private fun startHalfDuplex() {
        _voiceState.value = VoiceState.LISTENING
        _transcribedText.value = ""
        _responseText.value = ""
        _errorMessage.value = null

        speechHelper.startListening()
        observeSpeechResults()
    }

    private fun observeSpeechResults() {
        sttObserverJob?.cancel()
        sttObserverJob = scope.launch {
            launch {
                speechHelper.recognizedText.collect { text ->
                    _transcribedText.value = text
                }
            }

            launch {
                speechHelper.error.collect { error ->
                    if (error != null) {
                        _errorMessage.value = error
                        _voiceState.value = VoiceState.ERROR
                    }
                }
            }

            var wasListening = false
            speechHelper.isListening.collect { listening ->
                if (listening) {
                    wasListening = true
                } else if (wasListening && _voiceState.value == VoiceState.LISTENING) {
                    wasListening = false
                    val finalText = speechHelper.recognizedText.value
                    if (finalText.isNotBlank()) {
                        _transcribedText.value = finalText
                        sendTranscribedText(finalText)
                    } else if (_errorMessage.value == null) {
                        _errorMessage.value = "No speech detected"
                        _voiceState.value = VoiceState.ERROR
                    }
                }
            }
        }
    }

    private fun sendTranscribedText(text: String) {
        _voiceState.value = VoiceState.SENDING

        scope.launch {
            try {
                chatRepository.sendMessage(text)
                _voiceState.value = VoiceState.WAITING
                waitForResponseAndSpeak()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to send: ${e.message}"
                _voiceState.value = VoiceState.ERROR
            }
        }
    }

    private fun waitForResponseAndSpeak() {
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            chatRepository.events.collect { event ->
                when (event) {
                    is GatewayEvent.ChatDelta -> {
                        _responseText.value += event.text
                    }

                    is GatewayEvent.ChatFinal -> {
                        val fullText = if (!event.text.isNullOrBlank()) {
                            event.text
                        } else {
                            _responseText.value
                        }
                        _responseText.value = fullText
                        convertAndPlay(fullText)
                        return@collect
                    }

                    is GatewayEvent.ChatError -> {
                        _errorMessage.value = event.message ?: "Unknown error"
                        _voiceState.value = VoiceState.ERROR
                        return@collect
                    }

                    is GatewayEvent.ChatAborted -> {
                        _voiceState.value = VoiceState.IDLE
                        return@collect
                    }

                    else -> { /* ignore other events */ }
                }
            }
        }
    }

    private fun convertAndPlay(text: String) {
        scope.launch {
            _voiceState.value = VoiceState.PLAYING
            try {
                val voice = ttsVoice.first()
                val response = chatRepository.convertTextToSpeech(text, voice)
                if (!response.ok) {
                    _errorMessage.value = response.error?.message ?: "TTS conversion failed"
                    _voiceState.value = VoiceState.ERROR
                    return@launch
                }

                val payload = response.payload
                if (payload is JsonObject) {
                    val audioData = payload["audio"]?.jsonPrimitive?.contentOrNull
                    val format = payload["format"]?.jsonPrimitive?.contentOrNull ?: "mp3"
                    if (audioData != null) {
                        playAudioFromBase64(audioData, format)
                    } else {
                        _errorMessage.value = "No audio data in TTS response"
                        _voiceState.value = VoiceState.ERROR
                    }
                } else {
                    _errorMessage.value = "Unexpected TTS response format"
                    _voiceState.value = VoiceState.ERROR
                }
            } catch (e: Exception) {
                if (ttsManager != null) {
                    try {
                        ttsManager.speakLocal(text)
                        _voiceState.value = VoiceState.IDLE
                        return@launch
                    } catch (_: Exception) { }
                }
                _errorMessage.value = "TTS error: ${e.message}"
                _voiceState.value = VoiceState.ERROR
            }
        }
    }

    private suspend fun playAudioFromBase64(base64Audio: String, format: String) {
        withContext(Dispatchers.IO) {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("tts_", ".$format", context.cacheDir)
            try {
                FileOutputStream(tempFile).use { it.write(audioBytes) }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to write audio: ${e.message}"
                _voiceState.value = VoiceState.ERROR
                return@withContext
            }

            withContext(Dispatchers.Main) {
                playAudioFile(tempFile)
            }
        }
    }

    private fun playAudioFile(file: File) {
        releaseMediaPlayer()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            setDataSource(file.absolutePath)
            prepare()

            setOnCompletionListener {
                _voiceState.value = VoiceState.IDLE
                file.delete()
                releaseMediaPlayer()
            }

            setOnErrorListener { _, what, extra ->
                _errorMessage.value = "Playback error (what=$what, extra=$extra)"
                _voiceState.value = VoiceState.ERROR
                file.delete()
                true
            }

            start()
        }
    }

    private fun stopAudioPlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
