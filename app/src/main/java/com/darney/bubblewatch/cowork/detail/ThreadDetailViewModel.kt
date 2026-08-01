package com.darney.bubblewatch.cowork.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.PromptDto
import com.darney.bubblewatch.data.ThreadStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ThreadDetailUiState(
    val index: Int = -1,
    val title: String = "",
    val status: ThreadStatus = ThreadStatus.UNKNOWN,
    val lines: List<String> = emptyList(),
    val prompt: PromptDto? = null,
    val draft: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)

class ThreadDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BridgeRepository.get(app)
    private val _state = MutableStateFlow(ThreadDetailUiState())
    val state: StateFlow<ThreadDetailUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Begin polling tail + status for [index]. Safe to call repeatedly. */
    fun start(index: Int) {
        if (_state.value.index == index && pollJob?.isActive == true) return
        _state.value = ThreadDetailUiState(index = index)
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                refreshOnce(index)
                // Working threads stream — poll moderately. Threads sitting at a
                // question or idle are stable, so poll lazily to avoid churn.
                val fast = _state.value.status == ThreadStatus.WORKING
                delay(if (fast) 5000L else 10000L)
            }
        }
    }

    private suspend fun refreshOnce(index: Int) {
        try {
            val threads = repo.listThreads()
            val meta = threads.find { it.index == index }
            val tail = repo.tail(index)
            val cur = _state.value
            val newStatus = meta?.statusEnum ?: ThreadStatus.UNKNOWN
            val newTitle = meta?.label?.ifBlank { meta.title } ?: cur.title
            val linesChanged = tail.lines != cur.lines
            val promptChanged = tail.prompt != cur.prompt
            // Nothing moved — emit nothing at all, so there's zero recomposition
            // for a thread parked at a question or idle. This is the calm case.
            if (!linesChanged && !promptChanged &&
                newStatus == cur.status && newTitle == cur.title && cur.error == null
            ) {
                return
            }
            _state.value = cur.copy(
                title = newTitle,
                status = newStatus,
                // Keep the SAME instances when unchanged so their items don't recompose.
                lines = if (linesChanged) tail.lines else cur.lines,
                prompt = if (promptChanged) tail.prompt else cur.prompt,
                error = null,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "request failed")
        }
    }

    /** Tap an interactive option: send its digit as a single keypress (no Enter —
     *  Claude Code menus select on the number key). */
    fun selectOption(key: String) {
        val index = _state.value.index
        if (index < 0) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            try {
                repo.send(index, key, submit = false)
                _state.value = _state.value.copy(sending = false, error = null)
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = e.message ?: "send failed")
            }
        }
    }

    /** Send an allowlisted control key: escape | interrupt | clear | enter. */
    fun sendKey(action: String) {
        val index = _state.value.index
        if (index < 0) return
        viewModelScope.launch {
            try {
                repo.sendKey(index, action)
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "key failed")
            }
        }
    }

    fun appendToDraft(text: String) {
        val cur = _state.value.draft
        val joined = if (cur.isBlank()) text else "$cur $text"
        _state.value = _state.value.copy(draft = joined)
    }

    fun clearDraft() {
        _state.value = _state.value.copy(draft = "")
    }

    /** Send arbitrary text immediately (used by the Reply button). */
    fun sendText(text: String, submit: Boolean = true) = send(text, submit)

    /** Send the accumulated draft (used by the Add → Send flow). */
    fun sendDraft(submit: Boolean = true) {
        val text = _state.value.draft
        if (text.isBlank()) return
        send(text, submit) { clearDraft() }
    }

    private fun send(text: String, submit: Boolean, onOk: () -> Unit = {}) {
        val index = _state.value.index
        if (index < 0) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            try {
                repo.send(index, text, submit)
                onOk()
                _state.value = _state.value.copy(sending = false, error = null)
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = e.message ?: "send failed")
            }
        }
    }
}
