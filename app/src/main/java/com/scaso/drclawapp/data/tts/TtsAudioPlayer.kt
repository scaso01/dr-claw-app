package com.scaso.drclawapp.data.tts

/**
 * Plays PCM audio produced by a [TtsEngine]. [AudioTrackPlayer] is the real (Android
 * AudioTrack-backed) implementation; tests supply a fake so [TtsManager]'s fallback logic
 * is reachable on a plain JVM.
 *
 * No Android imports -- KMP-extractable.
 */
interface TtsAudioPlayer {

    /** Plays mono float samples at [sampleRate] Hz, replacing any in-progress playback. */
    suspend fun play(samples: FloatArray, sampleRate: Int)

    /** Stop any in-progress playback and release resources. */
    fun release()
}
