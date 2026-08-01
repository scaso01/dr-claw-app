package com.scaso.drclawapp.ui.infra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.infra.DaemonStatus
import com.scaso.drclawapp.data.infra.InfraComponent
import com.scaso.drclawapp.data.infra.InfraRepository
import com.scaso.drclawapp.data.websocket.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class InfraUiState(
    val components: List<InfraComponent> = emptyList(),
    val checkedAt: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val lastCompactStrategy: String? = null,
    val tokensBefore: Long? = null,
    val tokensAfter: Long? = null,
    val lastCompactedAt: Long? = null,
    val compactionCircuitOpen: Boolean = false,
    val daemonStatus: DaemonStatus = DaemonStatus(),
)

@HiltViewModel
class InfraViewModel @Inject constructor(
    private val repository: InfraRepository,
) : ViewModel() {

    val uiState: StateFlow<InfraUiState> = combine(
        repository.status,
        repository.isLoading,
        repository.error,
        repository.connectionState,
        repository.daemonStatus,
    ) { status, isLoading, error, connState, daemonStatus ->
        InfraUiState(
            components = status.components,
            checkedAt = status.checkedAt,
            isLoading = isLoading,
            error = error,
            connectionState = connState,
            daemonStatus = daemonStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InfraUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        repository.checkStatus()
    }
}
