package com.scaso.drclawapp.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.schedule.IronjawSchedule
import com.scaso.drclawapp.data.schedule.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleUiState(
    val schedules: List<IronjawSchedule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    val uiState: StateFlow<ScheduleUiState> = combine(
        scheduleRepository.schedules,
        scheduleRepository.isLoading,
        scheduleRepository.error,
    ) { schedules, loading, error ->
        ScheduleUiState(schedules = schedules, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    init {
        loadSchedules()
    }

    fun loadSchedules() {
        scheduleRepository.listSchedules()
    }

    fun createSchedule(name: String, cron: String, action: String?) {
        viewModelScope.launch {
            scheduleRepository.createSchedule(name, cron, action)
        }
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        viewModelScope.launch {
            scheduleRepository.toggleSchedule(id, enabled)
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            scheduleRepository.deleteSchedule(id)
        }
    }
}
