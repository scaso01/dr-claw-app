package com.scaso.drclawapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.BuildConfig
import android.content.Context
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.repository.GatewayRepository
import com.scaso.drclawapp.data.websocket.MeshnetIpHolder
import com.scaso.drclawapp.service.ProactiveWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceOption(val id: String, val name: String, val character: String)

val defaultVoices = listOf(
    VoiceOption("kitt", "KITT", "Knight Rider"),
    VoiceOption("drclaw", "Dr. Claw", "Inspector Gadget"),
    VoiceOption("optimus", "Optimus Prime", "Transformers"),
    VoiceOption("megatron", "Megatron", "Transformers"),
    VoiceOption("vader", "Darth Vader", "Star Wars"),
    VoiceOption("cobra", "Cobra Commander", "G.I. Joe"),
    VoiceOption("starscream", "Starscream", "Transformers"),
    VoiceOption("soundwave", "Soundwave", "Transformers"),
    VoiceOption("alf", "ALF", "ALF"),
    VoiceOption("hal9000", "HAL 9000", "2001"),
    VoiceOption("skeletor", "Skeletor", "He-Man"),
    VoiceOption("mrt", "Mr. T", "A-Team"),
    VoiceOption("terminator", "Terminator", "Terminator"),
    VoiceOption("robocop", "RoboCop", "RoboCop"),
    VoiceOption("maxheadroom", "Max Headroom", "Max Headroom"),
    VoiceOption("freddy", "Freddy Krueger", "Nightmare on Elm Street"),
    VoiceOption("shredder", "Shredder", "TMNT"),
    VoiceOption("liono", "Lion-O", "ThunderCats"),
    VoiceOption("mummra", "Mumm-Ra", "ThunderCats"),
    VoiceOption("krang", "Krang", "TMNT"),
    VoiceOption("destro", "Destro", "G.I. Joe"),
)

data class SettingsUiState(
    val themeMode: String = "system",
    val customInstructions: String = "",
    val gatewayUrl: String = BuildConfig.GATEWAY_URL,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val buildType: String = BuildConfig.BUILD_TYPE,
    val bubbleEnabled: Boolean = false,
    val ntfyEnabled: Boolean = false,
    val ntfyTopic: String = "",
    val ntfyUrl: String = "https://ntfy.sh",
    val effortLevel: String = "medium",
    val selectedVoice: String = "kitt",
    val voices: List<VoiceOption> = defaultVoices,
    val proactiveEnabled: Boolean = false,
    /** Phase 16: tool names that are auto-approved without showing the HITL dialog. */
    val autoApproveTools: Set<String> = emptySet(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appPreferences: AppPreferences,
    private val gatewayRepository: GatewayRepository,
    private val meshnetIpHolder: MeshnetIpHolder,
) : ViewModel() {

    /**
     * Meshnet IP the gateway hostname is pinned to (Phase 3 transport hardening). Kept out of
     * the maxed-out SettingsUiState combine as its own flow. Blank pref -> BuildConfig default.
     */
    val meshnetIp: StateFlow<String> = appPreferences.meshnetIp
        .map { it.ifBlank { BuildConfig.MESHNET_IP } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BuildConfig.MESHNET_IP,
        )

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        com.scaso.drclawapp.util.combine(
            appPreferences.themeMode,
            appPreferences.customInstructions,
            appPreferences.gatewayUrl,
            appPreferences.bubbleEnabled,
            appPreferences.ntfyEnabled,
            appPreferences.ntfyTopic,
            appPreferences.ntfyUrl,
        ) { themeMode, customInstructions, gatewayUrl, bubbleEnabled,
            ntfyEnabled, ntfyTopic, ntfyUrl ->
            listOf(themeMode, customInstructions, gatewayUrl, bubbleEnabled,
                ntfyEnabled, ntfyTopic, ntfyUrl)
        },
        appPreferences.effortLevel,
        appPreferences.ttsVoice,
        appPreferences.proactiveEnabled,
        appPreferences.autoApproveTools,
    ) { base, effortLevel, ttsVoice, proactiveEnabled, autoApproveTools ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            themeMode = base[0] as String,
            customInstructions = base[1] as String,
            gatewayUrl = (base[2] as String).ifEmpty { BuildConfig.GATEWAY_URL },
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            bubbleEnabled = base[3] as Boolean,
            ntfyEnabled = base[4] as Boolean,
            ntfyTopic = base[5] as String,
            ntfyUrl = base[6] as String,
            effortLevel = effortLevel,
            selectedVoice = ttsVoice,
            proactiveEnabled = proactiveEnabled,
            autoApproveTools = autoApproveTools,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            appPreferences.setThemeMode(mode)
        }
    }

    fun setCustomInstructions(instructions: String) {
        viewModelScope.launch {
            appPreferences.setCustomInstructions(instructions)
        }
    }

    fun setBubbleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setBubbleEnabled(enabled)
        }
    }

    fun setNtfyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setNtfyEnabled(enabled)
        }
    }

    fun setNtfyTopic(topic: String) {
        viewModelScope.launch {
            appPreferences.setNtfyTopic(topic)
        }
    }

    fun setNtfyUrl(url: String) {
        viewModelScope.launch {
            appPreferences.setNtfyUrl(url)
        }
    }

    fun setEffortLevel(level: String) {
        viewModelScope.launch {
            appPreferences.setEffortLevel(level)
        }
    }

    fun setVoice(id: String) {
        viewModelScope.launch {
            appPreferences.setTtsVoice(id)
        }
    }

    fun setProactiveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setProactiveEnabled(enabled)
            if (enabled) {
                ProactiveWorker.schedule(appContext)
            } else {
                ProactiveWorker.cancel(appContext)
            }
        }
    }

    fun reconnectGateway(url: String) {
        viewModelScope.launch {
            appPreferences.setGatewayUrl(url)
            gatewayRepository.reconnectWith(url)
        }
    }

    /**
     * Phase 3: update the Meshnet IP the gateway hostname pins to, then force a fresh
     * connection so the new IP takes effect. Blank resets to the BuildConfig default.
     */
    fun setMeshnetIp(ip: String) {
        val trimmed = ip.trim()
        viewModelScope.launch {
            appPreferences.setMeshnetIp(trimmed)
            meshnetIpHolder.ip = trimmed.ifBlank { BuildConfig.MESHNET_IP }
            gatewayRepository.reconnect()
        }
    }

    // ── Phase 16: Tool Auto-Approve allowlist management ─────────────────────

    fun addAutoApproveTool(toolName: String) {
        val trimmed = toolName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            appPreferences.addAutoApproveTool(trimmed)
        }
    }

    fun removeAutoApproveTool(toolName: String) {
        viewModelScope.launch {
            appPreferences.removeAutoApproveTool(toolName)
        }
    }
}
