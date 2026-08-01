package com.scaso.drclawapp.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.infra.InfraRepository
import com.scaso.drclawapp.data.infra.IronjawMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetricsUiState(
    val metrics: IronjawMetrics = IronjawMetrics(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val infraRepository: InfraRepository,
) : ViewModel() {

    val uiState: StateFlow<MetricsUiState> = combine(
        infraRepository.metrics,
        infraRepository.isLoading,
        infraRepository.error,
    ) { metrics, loading, error ->
        MetricsUiState(metrics = metrics, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetricsUiState())

    init {
        refresh()
        startAutoRefresh()
    }

    fun refresh() {
        infraRepository.checkStatus()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                infraRepository.checkStatus()
            }
        }
    }
}
