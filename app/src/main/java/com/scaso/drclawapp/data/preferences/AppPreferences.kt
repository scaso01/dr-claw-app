package com.scaso.drclawapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scaso.drclawapp.data.model.SessionTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

open class AppPreferences(private val context: Context?) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        val GATEWAY_URL = stringPreferencesKey("gateway_url")
        val MESHNET_IP = stringPreferencesKey("meshnet_ip")
        val BUBBLE_ENABLED = stringPreferencesKey("bubble_enabled")
        val NTFY_ENABLED = stringPreferencesKey("ntfy_enabled")
        // Note: ntfy topic is a shared secret (anyone with it can push notifications).
        // Sensitive values like this should migrate to SecureTokenStore in a future pass.
        val NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        val NTFY_URL = stringPreferencesKey("ntfy_url")
        val DISMISSED_SUB_AGENTS = stringSetPreferencesKey("dismissed_sub_agents")
        val AUTO_APPROVE_TOOLS = stringSetPreferencesKey("auto_approve_tools")
        val EFFORT_LEVEL = stringPreferencesKey("effort_level")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val THINKING_LEVEL = stringPreferencesKey("thinking_level")
        val SESSION_TEMPLATES = stringPreferencesKey("session_templates")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val PIPECAT_URL = stringPreferencesKey("pipecat_url")
        val FULL_DUPLEX_ENABLED = stringPreferencesKey("full_duplex_enabled")
        val PROACTIVE_ENABLED = stringPreferencesKey("proactive_enabled")
        val RECENT_MODELS = stringPreferencesKey("recent_models")
        val RECENT_PATHS = stringPreferencesKey("recent_paths")
    }

    val themeMode: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.THEME_MODE] ?: "system"
        }
    }

    val customInstructions: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.CUSTOM_INSTRUCTIONS] ?: ""
        }
    }

    val gatewayUrl: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.GATEWAY_URL] ?: ""
        }
    }

    /** Meshnet IP override for the gateway hostname pin. Blank = use BuildConfig default. */
    val meshnetIp: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.MESHNET_IP] ?: ""
        }
    }

    val bubbleEnabled: Flow<Boolean> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.BUBBLE_ENABLED] == "true"
        }
    }

    val ntfyEnabled: Flow<Boolean> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.NTFY_ENABLED] == "true"
        }
    }

    val ntfyTopic: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.NTFY_TOPIC] ?: generateDefaultTopic()
        }
    }

    val ntfyUrl: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.NTFY_URL] ?: DEFAULT_NTFY_URL
        }
    }

    val dismissedSubAgents: Flow<Set<String>> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.DISMISSED_SUB_AGENTS] ?: emptySet()
        }
    }

    /** Tool names that are automatically approved without showing the HITL dialog. */
    open val autoApproveTools: Flow<Set<String>> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.AUTO_APPROVE_TOOLS] ?: emptySet()
        }
    }

    val effortLevel: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.EFFORT_LEVEL] ?: "medium"
        }
    }

    val defaultModel: Flow<String?> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.DEFAULT_MODEL]
        }
    }

    val thinkingLevel: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.THINKING_LEVEL] ?: "medium"
        }
    }

    open val ttsVoice: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.TTS_VOICE] ?: "kitt"
        }
    }

    val pipecatUrl: Flow<String> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.PIPECAT_URL] ?: ""
        }
    }

    val fullDuplexEnabled: Flow<Boolean> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.FULL_DUPLEX_ENABLED] == "true"
        }
    }

    val proactiveEnabled: Flow<Boolean> by lazy {
        context!!.dataStore.data.map { prefs ->
            prefs[Keys.PROACTIVE_ENABLED] == "true"
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    val recentModels: Flow<List<com.scaso.drclawapp.data.roles.RecentModel>> by lazy {
        context!!.dataStore.data.map { prefs ->
            val raw = prefs[Keys.RECENT_MODELS] ?: "[]"
            try {
                json.decodeFromString<List<com.scaso.drclawapp.data.roles.RecentModel>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addRecentModel(modelName: String) {
        context!!.dataStore.edit { prefs ->
            val raw = prefs[Keys.RECENT_MODELS] ?: "[]"
            val current = try {
                json.decodeFromString<List<com.scaso.drclawapp.data.roles.RecentModel>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            // Remove existing entry for this model, add at front
            current.removeAll { it.name == modelName }
            current.add(0, com.scaso.drclawapp.data.roles.RecentModel(name = modelName, lastUsed = System.currentTimeMillis()))
            // Keep only last 5
            val trimmed = current.take(5)
            prefs[Keys.RECENT_MODELS] = json.encodeToString(trimmed)
        }
    }

    /** Most-recently-used project paths (most recent first, capped at 8). */
    val recentPaths: Flow<List<String>> by lazy {
        context!!.dataStore.data.map { prefs ->
            val raw = prefs[Keys.RECENT_PATHS] ?: "[]"
            try {
                json.decodeFromString<List<String>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addRecentPath(path: String) {
        context!!.dataStore.edit { prefs ->
            val raw = prefs[Keys.RECENT_PATHS] ?: "[]"
            val current = try {
                json.decodeFromString<List<String>>(raw).toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            // Remove duplicate, insert at front, cap at 8
            current.removeAll { it == path }
            current.add(0, path)
            prefs[Keys.RECENT_PATHS] = json.encodeToString(current.take(8))
        }
    }

    val sessionTemplates: Flow<List<SessionTemplate>> by lazy {
        context!!.dataStore.data.map { prefs ->
            val raw = prefs[Keys.SESSION_TEMPLATES] ?: "[]"
            try {
                json.decodeFromString<List<SessionTemplate>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun saveTemplate(template: SessionTemplate) {
        val current = sessionTemplates.first().toMutableList()
        // Replace existing template with same name, or append
        val idx = current.indexOfFirst { it.name == template.name }
        if (idx >= 0) {
            current[idx] = template
        } else {
            current.add(template)
        }
        context!!.dataStore.edit { prefs ->
            prefs[Keys.SESSION_TEMPLATES] = json.encodeToString(current)
        }
    }

    suspend fun deleteTemplate(name: String) {
        val current = sessionTemplates.first().filter { it.name != name }
        context!!.dataStore.edit { prefs ->
            prefs[Keys.SESSION_TEMPLATES] = json.encodeToString(current)
        }
    }

    suspend fun setThemeMode(mode: String) {
        require(mode in listOf("system", "dark", "light")) {
            "Invalid theme mode: $mode. Must be one of: system, dark, light"
        }
        context!!.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setCustomInstructions(instructions: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_INSTRUCTIONS] = instructions
        }
    }

    suspend fun setGatewayUrl(url: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.GATEWAY_URL] = url
        }
    }

    suspend fun setMeshnetIp(ip: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.MESHNET_IP] = ip.trim()
        }
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.BUBBLE_ENABLED] = if (enabled) "true" else "false"
        }
    }

    suspend fun setNtfyEnabled(enabled: Boolean) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.NTFY_ENABLED] = if (enabled) "true" else "false"
        }
    }

    suspend fun setNtfyTopic(topic: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.NTFY_TOPIC] = topic
        }
    }

    suspend fun setNtfyUrl(url: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.NTFY_URL] = url
        }
    }

    suspend fun dismissSubAgent(sessionKey: String) {
        context!!.dataStore.edit { prefs ->
            val current = prefs[Keys.DISMISSED_SUB_AGENTS] ?: emptySet()
            prefs[Keys.DISMISSED_SUB_AGENTS] = current + sessionKey
        }
    }

    suspend fun clearDismissedSubAgents() {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.DISMISSED_SUB_AGENTS] = emptySet()
        }
    }

    open suspend fun addAutoApproveTool(toolName: String) {
        context!!.dataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_APPROVE_TOOLS] ?: emptySet()
            prefs[Keys.AUTO_APPROVE_TOOLS] = current + toolName
        }
    }

    open suspend fun removeAutoApproveTool(toolName: String) {
        context!!.dataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_APPROVE_TOOLS] ?: emptySet()
            prefs[Keys.AUTO_APPROVE_TOOLS] = current - toolName
        }
    }

    suspend fun setDefaultModel(model: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_MODEL] = model
        }
    }

    suspend fun setEffortLevel(level: String) {
        require(level in listOf("low", "medium", "high")) {
            "Invalid effort level: $level"
        }
        context!!.dataStore.edit { prefs ->
            prefs[Keys.EFFORT_LEVEL] = level
        }
    }

    suspend fun setThinkingLevel(level: String) {
        require(level in listOf("none", "low", "medium", "high")) {
            "Invalid thinking level: $level. Must be one of: none, low, medium, high"
        }
        context!!.dataStore.edit { prefs ->
            prefs[Keys.THINKING_LEVEL] = level
        }
    }

    suspend fun setTtsVoice(voice: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.TTS_VOICE] = voice
        }
    }

    suspend fun setPipecatUrl(url: String) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.PIPECAT_URL] = url
        }
    }

    suspend fun setFullDuplexEnabled(enabled: Boolean) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.FULL_DUPLEX_ENABLED] = if (enabled) "true" else "false"
        }
    }

    suspend fun setProactiveEnabled(enabled: Boolean) {
        context!!.dataStore.edit { prefs ->
            prefs[Keys.PROACTIVE_ENABLED] = if (enabled) "true" else "false"
        }
    }

    /**
     * Initializes the ntfy topic with a random value if not yet set.
     * Call once at app startup.
     */
    suspend fun ensureNtfyTopicInitialized() {
        context!!.dataStore.edit { prefs ->
            if (prefs[Keys.NTFY_TOPIC] == null) {
                prefs[Keys.NTFY_TOPIC] = generateDefaultTopic()
            }
        }
    }

    companion object {
        const val DEFAULT_NTFY_URL = "https://ntfy.sh"

        private fun generateDefaultTopic(): String {
            val suffix = UUID.randomUUID().toString().take(8)
            return "drclaw-sean-$suffix"
        }
    }
}
