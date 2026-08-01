package com.scaso.drclawapp.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.security.PermissionRule
import com.scaso.drclawapp.data.security.SecurityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionRulesUiState(
    val rules: List<PermissionRule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PermissionRulesViewModel @Inject constructor(
    private val repository: SecurityRepository,
) : ViewModel() {

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    private val _selectedRule = MutableStateFlow<PermissionRule?>(null)
    val selectedRule: StateFlow<PermissionRule?> = _selectedRule.asStateFlow()

    val uiState: StateFlow<PermissionRulesUiState> = combine(
        repository.rules,
        repository.isLoading,
        repository.error,
    ) { rules, isLoading, error ->
        PermissionRulesUiState(
            rules = rules,
            isLoading = isLoading,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PermissionRulesUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        repository.loadRules()
    }

    fun showAddDialog() {
        _showAddDialog.value = true
    }

    fun dismissAddDialog() {
        _showAddDialog.value = false
    }

    fun selectRule(rule: PermissionRule) {
        _selectedRule.value = rule
    }

    fun dismissRuleSheet() {
        _selectedRule.value = null
    }

    fun addRule(toolPattern: String, pathPattern: String?, action: String) {
        viewModelScope.launch {
            val response = repository.addRule(toolPattern, pathPattern, action)
            if (response.ok) {
                dismissAddDialog()
                refresh()
            }
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
            _selectedRule.value = null
            refresh()
        }
    }
}
