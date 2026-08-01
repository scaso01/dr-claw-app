package com.scaso.drclawapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scaso.drclawapp.data.local.dao.MessageDao
import com.scaso.drclawapp.data.local.dao.SearchResult
import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode {
    IN_CHAT,
    GLOBAL,
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messageDao: MessageDao,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchMode = MutableStateFlow(SearchMode.IN_CHAT)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    private val _globalResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val globalResults: StateFlow<List<SearchResult>> = _globalResults.asStateFlow()

    val searchResults: StateFlow<List<Message>> = combine(
        chatRepository.messages,
        _searchQuery,
    ) { messages, query ->
        if (query.isBlank()) {
            emptyList()
        } else {
            val lowerQuery = query.lowercase()
            messages.filter { message ->
                message.content.lowercase().contains(lowerQuery)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (_searchMode.value == SearchMode.GLOBAL && query.isNotBlank()) {
            performGlobalSearch(query)
        }
    }

    fun toggleSearchMode() {
        _searchMode.value = when (_searchMode.value) {
            SearchMode.IN_CHAT -> SearchMode.GLOBAL
            SearchMode.GLOBAL -> SearchMode.IN_CHAT
        }
        val query = _searchQuery.value
        if (_searchMode.value == SearchMode.GLOBAL && query.isNotBlank()) {
            performGlobalSearch(query)
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _isSearchActive.value = false
        _searchMode.value = SearchMode.IN_CHAT
        _globalResults.value = emptyList()
    }

    fun toggleSearch() {
        val newActive = !_isSearchActive.value
        _isSearchActive.value = newActive
        if (!newActive) {
            _searchQuery.value = ""
            _searchMode.value = SearchMode.IN_CHAT
            _globalResults.value = emptyList()
        }
    }

    fun activateSearch() {
        _isSearchActive.value = true
    }

    private fun performGlobalSearch(query: String) {
        viewModelScope.launch {
            _globalResults.value = try {
                messageDao.searchGlobal(query)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
