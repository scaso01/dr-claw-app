package com.scaso.drclawapp.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.device.DeviceCmdResult
import com.scaso.drclawapp.data.device.DeviceRepository
import com.scaso.drclawapp.data.device.IronjawDevice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val devices: List<IronjawDevice> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val cmdResult: DeviceCmdResult? = null,
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _cmdResult = MutableStateFlow<DeviceCmdResult?>(null)

    val uiState: StateFlow<DeviceUiState> = combine(
        deviceRepository.devices,
        deviceRepository.isLoading,
        deviceRepository.error,
        _cmdResult,
    ) { devices, loading, error, cmdResult ->
        DeviceUiState(
            devices = devices,
            isLoading = loading,
            error = error,
            cmdResult = cmdResult,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeviceUiState())

    init {
        loadDevices()
    }

    fun loadDevices() {
        deviceRepository.listDevices()
    }

    fun sendCommand(deviceId: String, command: String) {
        viewModelScope.launch {
            val result = deviceRepository.sendCommand(deviceId, command)
            _cmdResult.value = result
        }
    }

    fun dismissCmdResult() {
        _cmdResult.value = null
    }
}
