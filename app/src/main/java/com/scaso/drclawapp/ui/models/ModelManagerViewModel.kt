package com.scaso.drclawapp.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.roles.AvailableModel
import com.scaso.drclawapp.data.roles.IronjawModelInfo
import com.scaso.drclawapp.data.roles.LlamaServerProps
import com.scaso.drclawapp.data.roles.ModelRepository
import com.scaso.drclawapp.data.roles.RecentModel
import com.scaso.drclawapp.data.roles.RoleRepository
import com.scaso.drclawapp.data.roles.RoleSkill
import com.scaso.drclawapp.data.roles.SwitchProgress
import com.scaso.drclawapp.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.scaso.drclawapp.util.combine as combine6
import javax.inject.Inject

data class ModelManagerUiState(
    val modelInfo: IronjawModelInfo = IronjawModelInfo(),
    val llamaServerProps: LlamaServerProps = LlamaServerProps(),
    val roles: List<RoleSkill> = emptyList(),
    val activeRole: RoleSkill? = null,
    val activeModel: String? = null,
    val activePreset: String? = null,
    val availableModels: List<AvailableModel> = emptyList(),
    val loadedModel: String? = null,
    val isLoading: Boolean = false,
    val isSwitching: Boolean = false,
    val error: String? = null,
    val switchProgress: SwitchProgress? = null,
)

@HiltViewModel
class ModelManagerViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val roleRepository: RoleRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val recentModels: StateFlow<List<RecentModel>> = appPreferences.recentModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<ModelManagerUiState> = combine(
        combine(
            repository.modelInfo,
            repository.llamaServerProps,
            repository.isLoading,
            repository.isSwitching,
            repository.error,
        ) { modelInfo, llamaServerProps, isLoading, isSwitching, error ->
            ModelPart(modelInfo, llamaServerProps, isLoading, isSwitching, error)
        },
        combine6(
            roleRepository.roles,
            roleRepository.activeRole,
            roleRepository.activeModel,
            roleRepository.availableModels,
            roleRepository.loadedModel,
            roleRepository.isSwitching,
        ) { roles, activeRole, activeModel, availableModels, loadedModel, roleSwitching ->
            RolePart(roles, activeRole, activeModel, availableModels, loadedModel, roleSwitching)
        },
        roleRepository.activePreset,
        roleRepository.error,
        repository.switchProgress,
    ) { model, role, activePreset, roleError, switchProgress ->
        ModelManagerUiState(
            modelInfo = model.info,
            llamaServerProps = model.llama,
            roles = role.roles,
            activeRole = role.activeRole,
            activeModel = role.activeModel,
            activePreset = activePreset,
            availableModels = role.availableModels,
            loadedModel = role.loadedModel,
            isLoading = model.isLoading,
            isSwitching = model.isSwitching || role.isSwitching,
            error = model.error ?: roleError,
            switchProgress = switchProgress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelManagerUiState())

    init { refresh() }

    fun refresh() {
        repository.refresh()
        roleRepository.loadRoles()
        roleRepository.loadAvailableModels()
    }

    private var lastSwitchedModel: String? = null

    fun switchModel(model: String) {
        lastSwitchedModel = model
        viewModelScope.launch { appPreferences.addRecentModel(model) }
        repository.switchModel(model)
    }

    fun retryLastSwitch() {
        lastSwitchedModel?.let { switchModel(it) }
    }

    fun switchRole(name: String) = roleRepository.switchRole(name)

    fun setRoleModel(role: String, model: String) = roleRepository.setRoleModel(role, model)

    fun applyPreset(preset: String) = roleRepository.applyPreset(preset)

    fun dismissError() {
        repository.dismissError()
        roleRepository.dismissError()
    }
}

private data class ModelPart(
    val info: IronjawModelInfo,
    val llama: LlamaServerProps,
    val isLoading: Boolean,
    val isSwitching: Boolean,
    val error: String?,
)

private data class RolePart(
    val roles: List<RoleSkill>,
    val activeRole: RoleSkill?,
    val activeModel: String?,
    val availableModels: List<AvailableModel>,
    val loadedModel: String?,
    val isSwitching: Boolean,
)
