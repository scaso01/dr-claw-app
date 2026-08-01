package com.scaso.drclawapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.roles.AvailableModel
import com.scaso.drclawapp.data.roles.RecentModel
import com.scaso.drclawapp.data.roles.RoleRepository
import com.scaso.drclawapp.data.roles.RoleSkill
import com.scaso.drclawapp.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleUiState(
    val roles: List<RoleSkill> = emptyList(),
    val activeRole: RoleSkill? = null,
    val activeModel: String? = null,
    val availableModels: List<AvailableModel> = emptyList(),
    val loadedModel: String? = null,
    val isLoading: Boolean = false,
    val isSwitching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class RoleViewModel @Inject constructor(
    private val repository: RoleRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val recentModels: StateFlow<List<RecentModel>> = appPreferences.recentModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<RoleUiState> = combine(
        repository.roles,
        repository.activeRole,
        repository.activeModel,
        combine(
            repository.availableModels,
            repository.loadedModel,
            repository.isLoading,
            repository.isSwitching,
            repository.error,
        ) { models, loaded, loading, switching, error ->
            RoleUiExtra(models, loaded, loading, switching, error)
        },
    ) { roles, activeRole, activeModel, extra ->
        RoleUiState(
            roles = roles,
            activeRole = activeRole,
            activeModel = activeModel,
            availableModels = extra.models,
            loadedModel = extra.loaded,
            isLoading = extra.loading,
            isSwitching = extra.switching,
            error = extra.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoleUiState())

    init {
        refresh()
    }

    fun refresh() {
        repository.loadRoles()
        repository.loadAvailableModels()
    }

    fun switchRole(name: String) = repository.switchRole(name)

    fun switchModel(model: String) {
        viewModelScope.launch { appPreferences.addRecentModel(model) }
        repository.switchModel(model)
    }

    /** One-tap chat backend switch: "cloud" → Anthropic, "local" → llama-server (all roles). */
    fun applyPreset(preset: String) = repository.applyPreset(preset)

    fun dismissError() = repository.dismissError()
}

private data class RoleUiExtra(
    val models: List<AvailableModel>,
    val loaded: String?,
    val loading: Boolean,
    val switching: Boolean,
    val error: String?,
)
