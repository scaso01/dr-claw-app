package com.scaso.drclawapp.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.export.ExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val isExporting: Boolean = false,
    val lastResult: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exportRepository: ExportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState

    fun exportConversations() {
        viewModelScope.launch {
            _uiState.value = ExportUiState(isExporting = true)
            val result = exportRepository.exportConversations()
            _uiState.value = if (result.success) {
                ExportUiState(lastResult = "Exported ${result.count ?: 0} conversations")
            } else {
                ExportUiState(error = result.error)
            }
        }
    }

    fun exportBrain() {
        viewModelScope.launch {
            _uiState.value = ExportUiState(isExporting = true)
            val result = exportRepository.exportBrain()
            _uiState.value = if (result.success) {
                ExportUiState(lastResult = "Brain exported (${result.count ?: 0} memories)")
            } else {
                ExportUiState(error = result.error)
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            _uiState.value = ExportUiState(isExporting = true)
            val result = exportRepository.createBackup()
            _uiState.value = if (result.success) {
                ExportUiState(lastResult = "Backup created successfully")
            } else {
                ExportUiState(error = result.error)
            }
        }
    }

    fun dismissResult() {
        _uiState.value = ExportUiState()
    }
}
