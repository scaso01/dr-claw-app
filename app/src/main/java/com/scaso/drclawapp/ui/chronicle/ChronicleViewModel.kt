package com.scaso.drclawapp.ui.chronicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.chronicle.ChronicleInsights
import com.scaso.drclawapp.data.chronicle.ChronicleRepository
import com.scaso.drclawapp.data.chronicle.ChronicleSearchResult
import com.scaso.drclawapp.data.chronicle.ChronicleSession
import com.scaso.drclawapp.data.chronicle.ChronicleStats
import com.scaso.drclawapp.data.chronicle.ChronicleTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import com.scaso.drclawapp.util.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ChronicleUiState(
    val sessions: List<ChronicleSession> = emptyList(),
    val searchResults: List<ChronicleSearchResult> = emptyList(),
    val topics: List<ChronicleTopic> = emptyList(),
    val stats: ChronicleStats? = null,
    val insights: ChronicleInsights? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChronicleViewModel @Inject constructor(
    private val chronicleRepository: ChronicleRepository,
) : ViewModel() {

    val uiState: StateFlow<ChronicleUiState> = combine(
        chronicleRepository.sessions,
        chronicleRepository.searchResults,
        chronicleRepository.topics,
        chronicleRepository.stats,
        chronicleRepository.insights,
        chronicleRepository.isLoading,
        chronicleRepository.error,
    ) { sessions, searchResults, topics, stats, insights, loading, error ->
        ChronicleUiState(
            sessions = sessions,
            searchResults = searchResults,
            topics = topics,
            stats = stats,
            insights = insights,
            isLoading = loading,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChronicleUiState())

    init {
        loadSessions()
    }

    fun loadSessions() {
        chronicleRepository.getSessions()
    }

    fun search(query: String) {
        chronicleRepository.search(query)
    }

    fun loadTopics() {
        chronicleRepository.getTopics()
    }

    fun loadStats() {
        chronicleRepository.getStats()
    }

    fun loadInsights() {
        chronicleRepository.getInsights()
    }
}
