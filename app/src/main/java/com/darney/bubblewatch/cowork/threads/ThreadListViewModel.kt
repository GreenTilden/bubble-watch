package com.darney.bubblewatch.cowork.threads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.ThreadDto
import com.darney.bubblewatch.data.ThreadStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThreadListUiState(
    val loading: Boolean = true,
    val configured: Boolean = true,
    val threads: List<ThreadDto> = emptyList(),
    // Haiku one-liners for WORKING threads, keyed by thread index. A working thread
    // isn't asking for you, so instead of a tappable row it shows "what it's doing now".
    val summaries: Map<Int, String> = emptyMap(),
    val error: String? = null,
)

class ThreadListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BridgeRepository.get(app)
    private val _state = MutableStateFlow(ThreadListUiState())
    val state: StateFlow<ThreadListUiState> = _state.asStateFlow()

    // Per-thread last-summary-fetch wall clock, so a busy thread refreshes its blurb
    // at most every SUMMARY_STALE_MS instead of on every list poll (keeps Haiku spend
    // tiny). One in-flight batch at a time via [summaryJob].
    private val summaryFetchedAt = mutableMapOf<Int, Long>()
    private var summaryJob: Job? = null

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
                // Skip the list emit when identical (no recompose on a steady set),
                // but ALWAYS re-evaluate summaries — a working thread keeps working
                // even when the thread set is unchanged.
                if (threads != cur.threads || cur.loading || cur.error != null) {
                    _state.value = cur.copy(loading = false, threads = threads, error = null)
                }
                refreshSummaries(threads)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "request failed")
            }
        }
    }

    /** Fetch one-line summaries for WORKING threads, off the render/poll path. Prunes
     *  summaries for threads that stopped working or left the list, and only (re)fetches
     *  entries older than the stale window. Blank results (Haiku off / no key) still mark
     *  a fetch so we don't hammer — the row degrades to the plain status. */
    private fun refreshSummaries(threads: List<ThreadDto>) {
        val working = threads.filter { it.statusEnum == ThreadStatus.WORKING }.map { it.index }.toSet()

        val prunedMap = _state.value.summaries.filterKeys { it in working }
        if (prunedMap != _state.value.summaries) {
            _state.value = _state.value.copy(summaries = prunedMap)
        }
        summaryFetchedAt.keys.retainAll(working)

        if (summaryJob?.isActive == true) return
        val now = System.currentTimeMillis()
        val stale = working.filter { now - (summaryFetchedAt[it] ?: 0L) > SUMMARY_STALE_MS }
        if (stale.isEmpty()) return

        summaryJob = viewModelScope.launch {
            for (idx in stale) {
                val s = repo.summary(idx)
                summaryFetchedAt[idx] = System.currentTimeMillis()
                if (s.isNotBlank() && _state.value.summaries[idx] != s) {
                    _state.value = _state.value.copy(summaries = _state.value.summaries + (idx to s))
                }
            }
        }
    }

    private companion object {
        const val SUMMARY_STALE_MS = 20_000L
    }
}
