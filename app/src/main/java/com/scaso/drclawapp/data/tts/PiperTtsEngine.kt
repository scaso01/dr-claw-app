package com.scaso.drclawapp.data.tts

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device TTS using Piper via sherpa-onnx.
 * Used as fallback when Chatterbox server (workstation/Ironjaw) is unreachable.
 */
class PiperTtsEngine(private val context: Context) : TtsEngine {

    private var tts: OfflineTts? = null
    private var initialized = false

    /**
     * Initialize the TTS engine. Must be called before [generate].
     * Copies model assets from the APK's `assets/voices/` to internal
     * storage on first run, then configures the sherpa-onnx OfflineTts.
     */
    override suspend fun initialize() = withContext(Dispatchers.IO) {
        if (initialized) return@withContext

        val modelDir = File(context.filesDir, "piper-models")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            // Copy model files from assets
            copyAssetDir(context, "voices", modelDir)
        }

        val modelFile = File(modelDir, "en_US-lessac-medium.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        val dataDir = File(modelDir, "espeak-ng-data")

        if (!modelFile.exists()) {
            // Model not bundled yet -- skip init silently
            return@withContext
        }

        val vitsConfig = OfflineTtsVitsModelConfig(
            model = modelFile.absolutePath,
            tokens = tokensFile.absolutePath,
            dataDir = dataDir.absolutePath,
        )

        val modelConfig = OfflineTtsModelConfig(
            vits = vitsConfig,
            numThreads = 2,
        )

        val config = OfflineTtsConfig(
            model = modelConfig,
        )

        tts = OfflineTts(config = config)
        initialized = true
    }

    /**
     * Generate speech audio from text.
     * @return PCM float samples at [sampleRate] Hz, or null if engine not initialized.
     */
    override suspend fun generate(text: String, speed: Float): FloatArray? =
        withContext(Dispatchers.IO) {
            val engine = tts ?: return@withContext null
            val audio = engine.generate(text, speed = speed)
            audio.samples
        }

    /** Sample rate of generated audio (Piper default: 22050 Hz). */
    override val sampleRate: Int get() = tts?.sampleRate() ?: 22050

    /** Whether the engine is ready to generate speech. */
    override val isReady: Boolean get() = initialized && tts != null

    /** Release native resources. */
    override fun release() {
        tts?.release()
        tts = null
        initialized = false
    }

    /**
     * Recursively copies an asset directory to a target directory on disk.
     */
    private fun copyAssetDir(context: Context, assetDir: String, targetDir: File) {
        val assets = context.assets.list(assetDir) ?: return
        for (name in assets) {
            val assetPath = "$assetDir/$name"
            val targetFile = File(targetDir, name)
            val subList = context.assets.list(assetPath)
            if (subList != null && subList.isNotEmpty()) {
                targetFile.mkdirs()
                copyAssetDir(context, assetPath, targetFile)
            } else {
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
