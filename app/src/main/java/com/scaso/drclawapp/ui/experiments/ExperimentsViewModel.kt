package com.scaso.drclawapp.ui.experiments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.experiments.Experiment
import com.scaso.drclawapp.data.experiments.ExperimentRepository
import com.scaso.drclawapp.data.experiments.ExperimentResultsResponse
import com.scaso.drclawapp.data.experiments.ExperimentStatus
import com.scaso.drclawapp.data.websocket.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExperimentsUiState(
    val experiments: List<Experiment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedResults: ExperimentResultsResponse? = null,
)

@HiltViewModel
class ExperimentsViewModel @Inject constructor(
    private val repository: ExperimentRepository,
) : ViewModel() {

    private var pollJob: Job? = null

    val uiState: StateFlow<ExperimentsUiState> = combine(
        repository.experiments,
        repository.isLoading,
        repository.error,
        repository.connectionState,
        repository.selectedResults,
    ) { experiments, isLoading, error, connState, results ->
        ExperimentsUiState(
            experiments = experiments,
            isLoading = isLoading,
            error = error,
            connectionState = connState,
            selectedResults = results,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExperimentsUiState(),
    )

    init {
        refresh()
        startAutoPoll()
    }

    fun refresh() {
        repository.loadExperiments()
    }

    fun loadResults(experimentId: String) {
        repository.loadResults(experimentId)
    }

    fun abort(experimentId: String) {
        viewModelScope.launch { repository.abort(experimentId) }
    }

    fun pause(experimentId: String) {
        viewModelScope.launch { repository.pause(experimentId) }
    }

    fun resume(experimentId: String) {
        viewModelScope.launch { repository.resume(experimentId) }
    }

    private fun startAutoPoll() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                // Only auto-poll if there are active experiments
                val hasActive = uiState.value.experiments.any {
                    it.status == ExperimentStatus.RUNNING || it.status == ExperimentStatus.PAUSED
                }
                if (hasActive) {
                    repository.loadExperiments()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
