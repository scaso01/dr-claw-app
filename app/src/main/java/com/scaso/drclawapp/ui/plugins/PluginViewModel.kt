package com.scaso.drclawapp.ui.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.plugins.IronjawPlugin
import com.scaso.drclawapp.data.plugins.PluginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PluginUiState(
    val plugins: List<IronjawPlugin> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PluginViewModel @Inject constructor(
    private val pluginRepository: PluginRepository,
) : ViewModel() {

    val uiState: StateFlow<PluginUiState> = combine(
        pluginRepository.plugins,
        pluginRepository.isLoading,
        pluginRepository.error,
    ) { plugins, loading, error ->
        PluginUiState(plugins = plugins, isLoading = loading, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PluginUiState())

    init {
        loadPlugins()
    }

    fun loadPlugins() {
        pluginRepository.listPlugins()
    }

    fun togglePlugin(id: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginRepository.togglePlugin(id, enabled)
        }
    }
}
