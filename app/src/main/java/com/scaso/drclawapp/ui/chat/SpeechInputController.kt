package com.scaso.drclawapp.ui.chat

import android.content.Context
import com.scaso.drclawapp.ui.voice.SpeechRecognizerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Android STT ([SpeechRecognizerHelper]) lifecycle for the chat input bar.
 * Writes recognized text directly into [inputText] -- the same MutableStateFlow
 * ChatViewModel exposes as ChatUiState.inputText -- shared by reference, matching
 * how [com.scaso.drclawapp.ui.voice.VoiceConversationManager] is handed repositories.
 */
class SpeechInputController(
    private val scope: CoroutineScope,
    private val inputText: MutableStateFlow<String>,
) {
    private val _isListeningSTT = MutableStateFlow(false)
    val isListeningSTT: StateFlow<Boolean> = _isListeningSTT.asStateFlow()

    private var speechHelper: SpeechRecognizerHelper? = null
    private var sttTextJob: Job? = null
    private var sttStateJob: Job? = null

    fun startSTT(context: Context) {
        if (speechHelper == null) {
            speechHelper = SpeechRecognizerHelper(context)
        }
        val helper = speechHelper ?: return
        helper.startListening()
        _isListeningSTT.value = true

        // Cancel previous collectors to avoid leaks
        sttTextJob?.cancel()
        sttTextJob = scope.launch {
            helper.recognizedText.collect { text ->
                if (text.isNotEmpty()) {
                    inputText.value = text
                }
            }
        }

        sttStateJob?.cancel()
        sttStateJob = scope.launch {
            helper.isListening.collect { listening ->
                _isListeningSTT.value = listening
            }
        }
    }

    fun stopSTT() {
        speechHelper?.stopListening()
        _isListeningSTT.value = false
    }

    fun destroy() {
        speechHelper?.destroy()
        speechHelper = null
    }
}
