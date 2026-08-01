package com.scaso.drclawapp.ui.brain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.brain.BrainMemory
import com.scaso.drclawapp.data.brain.BrainRepository
import com.scaso.drclawapp.data.brain.BrainSnapshot
import com.scaso.drclawapp.data.brain.BrainStatus
import com.scaso.drclawapp.data.brain.SnapshotRestoreResult
import com.scaso.drclawapp.data.brain.ContextVariable
import com.scaso.drclawapp.data.brain.GraphEntity
import com.scaso.drclawapp.data.brain.PeekResult
import com.scaso.drclawapp.data.brain.PendingMemory
import dagger.hilt.android.lifecycle.HiltViewModel
import com.scaso.drclawapp.util.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrainUiState(
    val memories: List<BrainMemory> = emptyList(),
    val entities: List<GraphEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isRemembering: Boolean = false,
    val error: String? = null,
    val rememberSuccess: Boolean = false,
    val contextVars: List<ContextVariable> = emptyList(),
    val summaries: List<BrainMemory> = emptyList(),
    val timelineResults: List<BrainMemory> = emptyList(),
    val brainStatus: BrainStatus? = null,
    val peekResult: PeekResult? = null,
    val pendingMemories: List<PendingMemory> = emptyList(),
)

@HiltViewModel
class BrainViewModel @Inject constructor(
    private val brainRepository: BrainRepository,
) : ViewModel() {

    private val _isRemembering = MutableStateFlow(false)
    private val _rememberSuccess = MutableStateFlow(false)
    private val _peekResult = MutableStateFlow<PeekResult?>(null)

    val uiState: StateFlow<BrainUiState> = combine(
        brainRepository.memories,
        brainRepository.entities,
        brainRepository.isLoading,
        brainRepository.error,
        _isRemembering,
        _rememberSuccess,
        brainRepository.contextVars,
        brainRepository.summaries,
        brainRepository.timelineResults,
        brainRepository.brainStatus,
        _peekResult,
        brainRepository.pendingMemories,
    ) { memories, entities, loading, error, remembering, success,
        contextVars, summaries, timelineResults, brainStatus, peekResult, pendingMemories ->
        BrainUiState(
            memories = memories,
            entities = entities,
            isLoading = loading,
            error = error,
            isRemembering = remembering,
            rememberSuccess = success,
            contextVars = contextVars,
            summaries = summaries,
            timelineResults = timelineResults,
            brainStatus = brainStatus,
            peekResult = peekResult,
            pendingMemories = pendingMemories,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrainUiState())

    fun recall(query: String) {
        brainRepository.recall(query)
    }

    fun recallWithDetail(query: String, detail: String) {
        brainRepository.recall(query, detail)
    }

    fun remember(content: String) {
        viewModelScope.launch {
            _isRemembering.value = true
            _rememberSuccess.value = false
            val ok = brainRepository.remember(content)
            _rememberSuccess.value = ok
            _isRemembering.value = false
        }
    }

    fun forget(memoryId: String) {
        viewModelScope.launch {
            brainRepository.forget(memoryId)
        }
    }

    fun loadGraph() {
        brainRepository.loadGraph()
    }

    fun dismissSuccess() {
        _rememberSuccess.value = false
    }

    fun loadStatus() {
        brainRepository.loadStatus()
    }

    fun loadContextVars() {
        brainRepository.loadContextVars()
    }

    fun toggleContextVar(name: String, loaded: Boolean) {
        viewModelScope.launch {
            val ok = brainRepository.contextLoad(name, loaded)
            if (ok) {
                brainRepository.loadContextVars()
            }
        }
    }

    fun deleteContextVar(name: String) {
        viewModelScope.launch {
            val ok = brainRepository.contextDelete(name)
            if (ok) {
                brainRepository.loadContextVars()
            }
        }
    }

    fun addContextVar(name: String, content: String) {
        viewModelScope.launch {
            _isRemembering.value = true
            val ok = brainRepository.contextSet(name, content)
            _isRemembering.value = false
            if (ok) {
                brainRepository.loadContextVars()
            }
        }
    }

    fun peekContextVar(name: String) {
        viewModelScope.launch {
            val result = brainRepository.contextPeek(name)
            _peekResult.value = result
        }
    }

    fun dismissPeek() {
        _peekResult.value = null
    }

    fun loadSummaries(sessionId: String? = null) {
        brainRepository.loadSummaries(sessionId)
    }

    fun queryTimeline(at: String, limit: Int = 10) {
        brainRepository.timeline(at, limit)
    }

    // ── Snapshots (kept out of the big uiState combine — separate flows) ──────
    val snapshots: StateFlow<List<BrainSnapshot>> = brainRepository.snapshots

    private val _restoreReport = MutableStateFlow<SnapshotRestoreResult?>(null)
    val restoreReport: StateFlow<SnapshotRestoreResult?> = _restoreReport

    private val _snapshotBusy = MutableStateFlow(false)
    val snapshotBusy: StateFlow<Boolean> = _snapshotBusy

    fun loadSnapshots() {
        brainRepository.loadSnapshots()
    }

    fun createSnapshot(label: String) {
        viewModelScope.launch {
            _snapshotBusy.value = true
            if (brainRepository.createSnapshot(label)) {
                brainRepository.loadSnapshots()
            }
            _snapshotBusy.value = false
        }
    }

    fun restoreSnapshot(id: Long) {
        viewModelScope.launch {
            _snapshotBusy.value = true
            _restoreReport.value = brainRepository.restoreSnapshot(id)
            brainRepository.loadStatus() // memory counts changed
            _snapshotBusy.value = false
        }
    }

    fun deleteSnapshot(id: Long) {
        viewModelScope.launch {
            _snapshotBusy.value = true
            if (brainRepository.deleteSnapshot(id)) {
                brainRepository.loadSnapshots()
            }
            _snapshotBusy.value = false
        }
    }

    fun dismissRestoreReport() {
        _restoreReport.value = null
    }

    fun loadPending() {
        brainRepository.listPending()
    }

    fun approvePending(id: Long) {
        brainRepository.approvePending(id)
    }

    fun rejectPending(id: Long) {
        brainRepository.rejectPending(id)
    }
}
