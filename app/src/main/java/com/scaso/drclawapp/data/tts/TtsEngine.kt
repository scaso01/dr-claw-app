package com.scaso.drclawapp.data.tts

/**
 * On-device text-to-speech engine contract. [PiperTtsEngine] is the real (Android-bound,
 * sherpa-onnx-backed) implementation; tests supply a fake so [TtsManager]'s fallback logic
 * is reachable on a plain JVM.
 *
 * No Android imports -- KMP-extractable.
 */
interface TtsEngine {

    /** Whether the engine has finished loading its model and can [generate]. */
    val isReady: Boolean

    /** Sample rate of audio produced by [generate], in Hz. */
    val sampleRate: Int

    /** Load the model. Must be called before [generate]. Safe to call multiple times. */
    suspend fun initialize()

    /**
     * Generate speech audio from text.
     * @return PCM float samples at [sampleRate] Hz, or null if the engine isn't ready.
     */
    suspend fun generate(text: String, speed: Float = 1.0f): FloatArray?

    /** Release native/engine resources. */
    fun release()
}
