package com.scaso.drclawapp.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.repository.ChatRepository
import com.scaso.drclawapp.data.tts.TtsManager
import com.scaso.drclawapp.data.voice.PipecatClient
import com.scaso.drclawapp.service.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val appPreferences: AppPreferences,
    private val ttsManager: TtsManager,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val pipecatClient = PipecatClient(scope = viewModelScope)

    // Give the singleton NetworkMonitor a handle to this voice session's PipecatClient so a
    // network change (WiFi <-> 5G) reconnects it too -- cleared in onCleared() below.
    init {
        networkMonitor.pipecatClient = pipecatClient
    }

    val voiceManager = VoiceConversationManager(
        context = context,
        chatRepository = chatRepository,
        scope = viewModelScope,
        ttsVoice = appPreferences.ttsVoice,
        ttsManager = ttsManager,
        pipecatClient = pipecatClient,
    )

    /** Whether full-duplex mode is available (URL configured + enabled). */
    val fullDuplexAvailable = combine(
        appPreferences.pipecatUrl,
        appPreferences.fullDuplexEnabled,
    ) { url, enabled ->
        url.isNotBlank() && enabled
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Auto-connect to Pipecat if configured
        viewModelScope.launch {
            combine(
                appPreferences.pipecatUrl,
                appPreferences.fullDuplexEnabled,
            ) { url, enabled -> Pair(url, enabled) }.collect { (url, enabled) ->
                if (url.isNotBlank() && enabled) {
                    pipecatClient.connect(url)
                    voiceManager.setMode(VoiceMode.FULL_DUPLEX)
                } else {
                    pipecatClient.disconnect()
                    voiceManager.setMode(VoiceMode.HALF_DUPLEX)
                }
            }
        }
    }

    fun toggleMode() {
        val current = voiceManager.voiceMode.value
        val newMode = if (current == VoiceMode.HALF_DUPLEX) {
            VoiceMode.FULL_DUPLEX
        } else {
            VoiceMode.HALF_DUPLEX
        }
        voiceManager.setMode(newMode)

        // Connect/disconnect Pipecat as needed
        if (newMode == VoiceMode.FULL_DUPLEX) {
            viewModelScope.launch {
                val url = appPreferences.pipecatUrl.stateIn(viewModelScope).value
                if (url.isNotBlank() && !pipecatClient.isConnected) {
                    pipecatClient.connect(url)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (networkMonitor.pipecatClient === pipecatClient) {
            networkMonitor.pipecatClient = null
        }
        voiceManager.destroy()
    }
}
