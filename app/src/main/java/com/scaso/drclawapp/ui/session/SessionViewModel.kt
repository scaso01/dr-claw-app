package com.scaso.drclawapp.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.model.ChatSession
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import android.util.Log
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val sessions: StateFlow<List<ChatSession>> = sessionRepository.sessions
    val currentSessionKey: StateFlow<String?> = sessionRepository.currentSessionKey

    private val _sessionSearchQuery = MutableStateFlow("")
    val sessionSearchQuery: StateFlow<String> = _sessionSearchQuery.asStateFlow()

    fun setSessionSearchQuery(query: String) {
        _sessionSearchQuery.value = query
    }

    val archivedSessions: StateFlow<List<ChatSession>> = combine(
        sessionRepository.archivedSessions,
        _sessionSearchQuery,
    ) { list, query ->
        list.filter { it.matchesQuery(query) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mainSessions: StateFlow<List<ChatSession>> = combine(
        sessions,
        currentSessionKey,
        _sessionSearchQuery,
    ) { list, activeKey, query ->
        list.filter { !it.isSubAgent && it.matchesQuery(query) }
            .sortedWith(compareByDescending<ChatSession> { it.sessionKey == activeKey }
                .thenByDescending { it.updatedAt ?: 0L })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subAgentSessions: StateFlow<List<ChatSession>> = combine(
        sessions,
        appPreferences.dismissedSubAgents,
        _sessionSearchQuery,
    ) { list, dismissed, query ->
        val maxAge = System.currentTimeMillis() - 3_600_000L // 1 hour hard ceiling
        list.filter { session ->
            session.isSubAgent &&
                session.sessionKey !in dismissed &&
                (session.updatedAt ?: 0L) > maxAge &&
                session.matchesQuery(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Title of the currently active session. */
    val currentSessionTitle: StateFlow<String> = combine(
        sessions,
        currentSessionKey,
    ) { list, key ->
        list.firstOrNull { it.sessionKey == key }?.title?.takeIf { it.isNotBlank() } ?: "New Chat"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "New Chat")

    /** Default model name from gateway infra.status, stored in preferences. */
    val defaultModelName: StateFlow<String?> = appPreferences.defaultModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Model name: session-specific override, or gateway default. */
    val currentSessionModelName: StateFlow<String?> = combine(
        sessions,
        currentSessionKey,
        defaultModelName,
    ) { list, key, defaultModel ->
        list.firstOrNull { it.sessionKey == key }?.modelName ?: defaultModel
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Fetches the gateway default model via gateway.model RPC and stores it in preferences. */
    fun refreshDefaultModel() {
        viewModelScope.launch {
            val model = sessionRepository.fetchDefaultModelName()
            if (!model.isNullOrBlank()) {
                appPreferences.setDefaultModel(model)
            }
        }
    }

    /** Count of active sub-agents (updated within last 2 min) for drawer badge. */
    val activeSubAgentCount: StateFlow<Int> = subAgentSessions
        .map { list ->
            val now = System.currentTimeMillis()
            list.count { (it.updatedAt ?: 0L) > now - 120_000L }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun loadSessions() {
        viewModelScope.launch {
            sessionRepository.loadSessions()
        }
    }

    fun switchSession(sessionKey: String) {
        viewModelScope.launch {
            sessionRepository.switchSession(sessionKey)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            sessionRepository.createNewSession()
        }
    }

    fun renameSession(sessionKey: String, title: String) {
        viewModelScope.launch {
            sessionRepository.renameSession(sessionKey, title)
        }
    }

    fun dismissSubAgent(sessionKey: String) {
        viewModelScope.launch {
            appPreferences.dismissSubAgent(sessionKey)
        }
    }

    fun clearAllDismissed() {
        viewModelScope.launch {
            // Dismiss all currently visible sub-agents (not un-dismiss)
            val current = subAgentSessions.value
            for (session in current) {
                appPreferences.dismissSubAgent(session.sessionKey)
            }
        }
    }

    fun pinSession(sessionKey: String, pinned: Boolean) {
        viewModelScope.launch {
            sessionRepository.pinSession(sessionKey, pinned)
            sessionRepository.loadSessions()
        }
    }

    fun archiveSession(sessionKey: String, archived: Boolean) {
        viewModelScope.launch {
            sessionRepository.archiveSession(sessionKey, archived)
        }
    }

    // -- Agent Fork -------------------------------------------------------

    private val _forkAgentResult = MutableStateFlow<String?>(null)
    val forkAgentResult: StateFlow<String?> = _forkAgentResult.asStateFlow()

    fun clearForkAgentResult() { _forkAgentResult.value = null }

    /**
     * Fires `agent.fork` for the current active session.
     * The UI must gate this with a [PermissionDialog] BEFORE calling.
     */
    fun forkAgent(task: String) {
        val sessionId = sessionRepository.getActiveSessionId()
        if (sessionId == null) {
            _forkAgentResult.value = "Not connected — no active session"
            return
        }
        viewModelScope.launch {
            try {
                val response = sessionRepository.forkAgent(
                    parentSessionId = sessionId,
                    task = task,
                )
                if (response.ok) {
                    _forkAgentResult.value = "Agent dispatched"
                } else {
                    val msg = response.error?.message ?: "Unknown error"
                    _forkAgentResult.value = "Failed: $msg"
                }
            } catch (e: Exception) {
                _forkAgentResult.value = "Error: ${e.message}"
            }
        }
    }

    // -- Delete -----------------------------------------------------------

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun clearDeleteError() { _deleteError.value = null }

    fun deleteSession(sessionKey: String) {
        viewModelScope.launch {
            try {
                sessionRepository.deleteSession(sessionKey)
            } catch (e: Exception) {
                Log.e("SessionViewModel", "Failed to delete session", e)
                _deleteError.value = "Failed to delete session: ${e.message}"
            }
        }
    }

    private fun ChatSession.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return (title?.lowercase()?.contains(q) == true) ||
            (lastMessage?.lowercase()?.contains(q) == true) ||
            sessionKey.lowercase().contains(q)
    }
}
