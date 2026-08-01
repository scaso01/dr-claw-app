package com.scaso.drclawapp.ui.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.approval.ApprovalDecision
import com.scaso.drclawapp.data.approval.ApprovalRepository
import com.scaso.drclawapp.data.approval.ApprovalRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApprovalUiState(
    val pendingApprovals: List<ApprovalRequest> = emptyList(),
    val currentRequest: ApprovalRequest? = null,
    val showDialog: Boolean = false,
)

@HiltViewModel
class ApprovalViewModel @Inject constructor(
    private val repository: ApprovalRepository,
) : ViewModel() {

    private val _showDialog = MutableStateFlow(false)
    private val _currentRequest = MutableStateFlow<ApprovalRequest?>(null)

    val uiState: StateFlow<ApprovalUiState> = combine(
        repository.pendingApprovals,
        _currentRequest,
        _showDialog,
    ) { pending, current, show ->
        ApprovalUiState(
            pendingApprovals = pending,
            currentRequest = current,
            showDialog = show,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ApprovalUiState(),
    )

    init {
        // Auto-show dialog when new approval arrives
        viewModelScope.launch {
            repository.approvalEvents.collect { request ->
                _currentRequest.value = request
                _showDialog.value = true
            }
        }
    }

    fun approve(requestId: String) {
        viewModelScope.launch {
            repository.respond(requestId, ApprovalDecision.APPROVE)
            dismissDialog()
        }
    }

    fun deny(requestId: String) {
        viewModelScope.launch {
            repository.respond(requestId, ApprovalDecision.DENY)
            dismissDialog()
        }
    }

    fun approveAlways(requestId: String) {
        viewModelScope.launch {
            repository.respond(requestId, ApprovalDecision.ALWAYS_ALLOW)
            dismissDialog()
        }
    }

    fun modify(requestId: String, modification: String) {
        viewModelScope.launch {
            repository.respond(requestId, ApprovalDecision.MODIFY, modification)
            dismissDialog()
        }
    }

    fun showRequest(request: ApprovalRequest) {
        _currentRequest.value = request
        _showDialog.value = true
    }

    fun dismissDialog() {
        _showDialog.value = false
        _currentRequest.value = null
        repository.dismissExpired()
    }
}
