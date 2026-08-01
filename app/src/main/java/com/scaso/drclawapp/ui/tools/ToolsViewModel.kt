package com.scaso.drclawapp.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.tools.IronjawTool
import com.scaso.drclawapp.data.tools.ToolExecuteResult
import com.scaso.drclawapp.data.tools.ToolRepository
import com.scaso.drclawapp.util.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

data class ToolsUiState(
    val tools: List<IronjawTool> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val executeResult: ToolExecuteResult? = null,
    val searchQuery: String = "",
    val filteredTools: List<IronjawTool> = emptyList(),
    val selectedTool: IronjawTool? = null,
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val toolRepository: ToolRepository,
) : ViewModel() {

    private val _executeResult = MutableStateFlow<ToolExecuteResult?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTool = MutableStateFlow<IronjawTool?>(null)

    val uiState: StateFlow<ToolsUiState> = combine(
        toolRepository.tools,
        toolRepository.isLoading,
        toolRepository.error,
        _executeResult,
        _searchQuery,
        _selectedTool,
    ) { tools, loading, error, result, query, selected ->
        val filtered = if (query.isBlank()) tools
        else tools.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
        ToolsUiState(
            tools = tools,
            isLoading = loading,
            error = error,
            executeResult = result,
            searchQuery = query,
            filteredTools = filtered,
            selectedTool = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ToolsUiState())

    init {
        loadTools()
    }

    fun loadTools() {
        toolRepository.listTools()
    }

    fun executeTool(name: String, params: JsonElement? = null) {
        viewModelScope.launch {
            val result = toolRepository.executeTool(name, params)
            _executeResult.value = result
        }
    }

    fun onSearchChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectTool(tool: IronjawTool) {
        _selectedTool.value = tool
    }

    fun dismissToolSheet() {
        _selectedTool.value = null
    }

    fun dismissResult() {
        _executeResult.value = null
    }
}
