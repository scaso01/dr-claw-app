package com.scaso.drclawapp.data.tts

import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.websocket.GatewayClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages TTS with server-first (Chatterbox via Ironjaw) + on-device fallback (Piper).
 *
 * Call [speak] to convert text to speech. The manager tries the gateway
 * server first (3-second timeout). If the server is unreachable or returns
 * an error, it falls back to the on-device Piper engine (via [ttsEngine]).
 *
 * Note: The server audio path returns early here because
 * [com.scaso.drclawapp.ui.voice.VoiceConversationManager] already handles
 * server TTS playback via MediaPlayer. This class owns **only** the
 * on-device fallback playback, delegated to [audioPlayer].
 *
 * No Android imports -- KMP-extractable. [ttsEngine] and [audioPlayer] are the
 * platform-bound seams (real impls: [PiperTtsEngine], [AudioTrackPlayer]).
 */
class TtsManager(
    private val gatewayClient: GatewayClient,
    private val appPreferences: AppPreferences,
    private val ttsEngine: TtsEngine,
    private val audioPlayer: TtsAudioPlayer,
) {

    /**
     * Initialize the on-device Piper TTS engine.
     * Safe to call multiple times (idempotent).
     */
    suspend fun initialize() {
        ttsEngine.initialize()
    }

    /** Whether the on-device Piper engine is ready. */
    val isPiperReady: Boolean get() = ttsEngine.isReady

    /**
     * Speak text using the selected voice.
     * Tries Chatterbox server first (3-second timeout), falls back to Piper on-device.
     *
     * @return true if audio was produced (server or on-device), false otherwise.
     */
    suspend fun speak(text: String): Boolean {
        val voice = appPreferences.ttsVoice.first()

        // Try server TTS first (3 second timeout)
        val serverAudio = withTimeoutOrNull(3000L) {
            try {
                val response = gatewayClient.convertTextToSpeech(text, voice)
                if (response.ok) response.payload else null
            } catch (_: Exception) {
                null
            }
        }

        if (serverAudio != null) {
            // Server path succeeded -- caller (VoiceConversationManager) handles
            // base64 decoding and MediaPlayer playback for server audio.
            return true
        }

        // Fallback to Piper on-device
        if (ttsEngine.isReady) {
            val samples = ttsEngine.generate(text) ?: return false
            audioPlayer.play(samples, ttsEngine.sampleRate)
            return true
        }

        return false
    }

    /**
     * Speak text using only the on-device Piper engine (skip server).
     * Useful when the caller already knows the server is unavailable.
     *
     * @return true if audio was produced, false if Piper is not ready.
     */
    suspend fun speakLocal(text: String, speed: Float = 1.0f): Boolean {
        if (!ttsEngine.isReady) return false
        val samples = ttsEngine.generate(text, speed) ?: return false
        audioPlayer.play(samples, ttsEngine.sampleRate)
        return true
    }

    /**
     * Stop any in-progress playback and release resources.
     */
    fun release() {
        audioPlayer.release()
        ttsEngine.release()
    }
}
