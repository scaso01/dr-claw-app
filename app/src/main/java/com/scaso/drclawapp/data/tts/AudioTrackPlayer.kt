package com.scaso.drclawapp.data.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real [TtsAudioPlayer] backed by [AudioTrack] (mono, 16-bit PCM). */
class AudioTrackPlayer : TtsAudioPlayer {

    private var audioTrack: AudioTrack? = null

    override suspend fun play(samples: FloatArray, sampleRate: Int) =
        withContext(Dispatchers.IO) {
            val shortSamples = ShortArray(samples.size) { i ->
                (samples[i] * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )

            audioTrack?.release()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferSize, shortSamples.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            audioTrack = track

            track.write(shortSamples, 0, shortSamples.size)
            track.play()
        }

    override fun release() {
        audioTrack?.release()
        audioTrack = null
    }
}
