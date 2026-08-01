package com.scaso.drclawapp.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.preferences.AppPreferences
import com.scaso.drclawapp.data.projects.ALL_PROJECTS
import com.scaso.drclawapp.data.projects.ProjectInfo
import com.scaso.drclawapp.data.projects.ProjectRepository
import com.scaso.drclawapp.data.projects.ProjectStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectState(
    val info: ProjectInfo,
    val status: ProjectStatusResponse = ProjectStatusResponse(project = info.name),
)

data class ProjectsUiState(
    val projects: List<ProjectState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Recent project paths for the path picker chips (most recent first). */
    val recentPaths: List<String> = emptyList(),
    /** True while the session.set_cwd RPC is in flight. */
    val isCwdSwitching: Boolean = false,
    /** Non-null after a set_cwd attempt — cleared when screen reads it. */
    val setCwdResult: String? = null,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _cwdState = MutableStateFlow(
        Triple(
            /* isCwdSwitching */ false,
            /* setCwdResult   */ null as String?,
            /* recentPaths    */ emptyList<String>(),
        )
    )

    val uiState: StateFlow<ProjectsUiState> = combine(
        repository.examplePipelineStatus,
        repository.isLoading,
        repository.error,
        appPreferences.recentPaths,
        _cwdState,
    ) { examplePipeline, isLoading, error, recentPaths, cwdState ->
        val projects = ALL_PROJECTS.map { info ->
            if (info.statusRpcSupported && info.name == "ExamplePipeline") {
                ProjectState(info = info, status = examplePipeline)
            } else {
                ProjectState(info = info)
            }
        }
        ProjectsUiState(
            projects = projects,
            isLoading = isLoading,
            error = error,
            recentPaths = recentPaths,
            isCwdSwitching = cwdState.first,
            setCwdResult = cwdState.second,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectsUiState(projects = ALL_PROJECTS.map { ProjectState(info = it) }),
    )

    init {
        refresh()
    }

    fun refresh() {
        repository.loadExamplePipelineStatus()
    }

    fun triggerExamplePipeline() {
        viewModelScope.launch {
            repository.triggerExamplePipeline()
            kotlinx.coroutines.delay(2_000)
            refresh()
        }
    }

    /** Returns the active Ironjaw session ID (null if not yet connected). */
    fun getActiveSessionId(): String? = repository.getActiveSessionId()

    /**
     * Fires `session.set_cwd` for the active gateway session.
     *
     * The session_id forwarded to Ironjaw is resolved from the repository's
     * current active session key.  A null [path] clears the server-side cwd.
     *
     * The PermissionDialog is shown by the UI BEFORE this method is called —
     * we do NOT rely on the server-side Confirm-tier gate (which is bypassed
     * for all session.* methods by GatewayClient.handlePermissionRequest).
     */
    fun setProjectCwd(path: String?) {
        val sessionId = repository.getActiveSessionId()
        if (sessionId == null) {
            _cwdState.update { it.copy(first = false, second = "Not connected — no active session") }
            return
        }
        viewModelScope.launch {
            _cwdState.update { it.copy(first = true, second = null) }
            try {
                val response = repository.setCwd(sessionId, path)
                if (response.ok) {
                    // Persist path to recents (null = clear, don't add)
                    if (path != null) {
                        appPreferences.addRecentPath(path)
                    }
                    _cwdState.update { it.copy(first = false, second = "Project set to: ${path ?: "(cleared)"}") }
                } else {
                    val msg = response.error?.message ?: "Unknown error"
                    _cwdState.update { it.copy(first = false, second = "Failed: $msg") }
                }
            } catch (e: Exception) {
                _cwdState.update { it.copy(first = false, second = "Error: ${e.message}") }
            }
        }
    }

    /** Called by the UI after it has consumed the snackbar message. */
    fun clearCwdResult() {
        _cwdState.update { it.copy(second = null) }
    }
}
