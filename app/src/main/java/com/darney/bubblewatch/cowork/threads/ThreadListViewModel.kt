package com.darney.bubblewatch.cowork.threads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.ThreadDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThreadListUiState(
    val loading: Boolean = true,
    val configured: Boolean = true,
    val threads: List<ThreadDto> = emptyList(),
    val error: String? = null,
)

class ThreadListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BridgeRepository.get(app)
    private val _state = MutableStateFlow(ThreadListUiState())
    val state: StateFlow<ThreadListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.configFlow.collect { cfg ->
                _state.value = _state.value.copy(configured = cfg.isConfigured)
            }
        }
        refresh(showLoading = true)
    }

    /** [showLoading] only for the first fetch; background polls update silently to avoid flicker. */
    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _state.value = _state.value.copy(loading = true)
            try {
                val threads = repo.listThreads()
                val cur = _state.value
                // Skip the emit when the list is identical, so a steady set of
                // threads doesn't recompose the list on every poll.
                if (threads == cur.threads && !cur.loading && cur.error == null) return@launch
                _state.value = cur.copy(loading = false, threads = threads, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "request failed")
            }
        }
    }
}
