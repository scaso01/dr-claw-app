package com.scaso.drclawapp.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.vault.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultUiState(
    val lastResult: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState

    fun getSecret(key: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, lastResult = null)
            val result = vaultRepository.getSecret(key)
            _uiState.value = if (result.success) {
                _uiState.value.copy(isLoading = false, lastResult = result.value ?: "(empty)")
            } else {
                _uiState.value.copy(isLoading = false, error = result.error)
            }
        }
    }

    fun setSecret(key: String, value: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, lastResult = null)
            val result = vaultRepository.setSecret(key, value)
            _uiState.value = if (result.success) {
                _uiState.value.copy(isLoading = false, lastResult = "Secret saved")
            } else {
                _uiState.value.copy(isLoading = false, error = result.error)
            }
        }
    }

    fun dismissResult() {
        _uiState.value = _uiState.value.copy(lastResult = null, error = null)
    }
}
