package com.darney.bubblewatch.cowork.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darney.bubblewatch.AmbientState
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.PromptDto
import com.darney.bubblewatch.data.ThreadStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
    // Momentum suggestions from the bridge. Fetched off the poll path (on entry
    // and when a new prompt / needs-input transition appears), not every tick.
    val suggestions: List<String> = emptyList(),
    val suggestionsLoading: Boolean = false,
    // Context-bath (🛁) progress. null = idle. Otherwise a short stage label the
    // screen renders as a live checkpoint line: COPY → CLEAR → PASTE → GO → done.
    val bathStage: String? = null,
    // Per-thread TX meter, carried from the thread list metadata (retains last
    // known values when a poll misses the status line).
    val model: String? = null,
    val ctxTokens: Int? = null,
    val ctxTier: String? = null,
    val costUsd: Double? = null,
)

class ThreadDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BridgeRepository.get(app)
    private val _state = MutableStateFlow(ThreadDetailUiState())
    val state: StateFlow<ThreadDetailUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var suggestJob: Job? = null

    /** Begin polling tail + status for [index]. Safe to call repeatedly. */
    fun start(index: Int) {
        if (_state.value.index == index && pollJob?.isActive == true) return
        _state.value = ThreadDetailUiState(index = index)
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                AmbientState.isAmbient.first { !it } // pause while screen is asleep
                refreshOnce(index)
                // Suggestions are NEVER fetched automatically — they cost a Haiku
                // call, so they only happen when the user taps the 💡 button
                // (see requestSuggestions()).
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
            // Suggestions are no longer auto-fetched on prompt / needs-input
            // transitions — the user pulls them on demand via 💡 (requestSuggestions).
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
                // Meter from list metadata; keep last known when a poll misses it.
                model = meta?.model ?: cur.model,
                ctxTokens = meta?.ctxTokens ?: cur.ctxTokens,
                ctxTier = meta?.ctxTier ?: cur.ctxTier,
                costUsd = meta?.costUsd ?: cur.costUsd,
                error = null,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "request failed")
        }
    }

    /** User-initiated suggestion fetch (the 💡 button). No Haiku call happens
     *  until this is tapped. Re-tapping re-fetches (guarded against stacking). */
    fun requestSuggestions() {
        val index = _state.value.index
        if (index < 0) return
        fetchSuggestions(index)
    }

    /** Fetch momentum suggestions for [index]. Off the poll path, guarded against
     *  stacking, never throws (repo swallows to []), emits only on change. */
    private fun fetchSuggestions(index: Int) {
        if (suggestJob?.isActive == true) return
        suggestJob = viewModelScope.launch {
            _state.value = _state.value.copy(suggestionsLoading = true)
            val sug = repo.suggest(index)
            val cur = _state.value
            if (sug != cur.suggestions || cur.suggestionsLoading) {
                _state.value = cur.copy(suggestions = sug, suggestionsLoading = false)
            }
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
                _state.value = _state.value.copy(sending = false, error = null, suggestions = emptyList())
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

    /**
     * Context-bath macro (🛁), watch-orchestrated with a visible per-stage
     * checkpoint. Destructive to conversation context, so the screen arms this
     * behind a confirm tap before calling it. Runs COPY → CLEAR → /clear settle →
     * PASTE → GO, updating [ThreadDetailUiState.bathStage] at each step so the user
     * can watch it happen. Failures surface via [error] and reset the bath state.
     *
     * CAVEAT: the bridge collapses newlines to spaces on /send, so the re-seeded
     * tail lands as one space-joined line (documented/accepted behavior).
     */
    fun contextBath() {
        val index = _state.value.index
        if (index < 0) return
        viewModelScope.launch {
            try {
                // Stage COPY: capture the current tail before we wipe context.
                _state.value = _state.value.copy(bathStage = "🛁 COPY", error = null)
                val captured = _state.value.lines.joinToString("\n")
                delay(200)
                // Stage CLEAR: reset Claude Code's conversation context.
                _state.value = _state.value.copy(bathStage = "🛁 CLEAR")
                repo.send(index, "/clear", submit = true)
                // Give Claude Code a beat to process /clear before we re-seed.
                delay(600)
                // Stage PASTE: type the captured tail back in (no submit yet).
                _state.value = _state.value.copy(bathStage = "🛁 PASTE")
                repo.send(index, captured, submit = false)
                delay(200)
                // Stage GO: press Enter to submit the re-seeded context.
                _state.value = _state.value.copy(bathStage = "🛁 GO")
                repo.sendKey(index, "enter")
                // Done — brief confirmation, drop any stale suggestions, refresh.
                _state.value = _state.value.copy(bathStage = "✓ context reset", suggestions = emptyList())
                refreshOnce(index)
                delay(1500)
                if (_state.value.bathStage == "✓ context reset") {
                    _state.value = _state.value.copy(bathStage = null)
                }
            } catch (e: Exception) {
                // Surface the failure AT the bath indicator — the user is watching the
                // button, not the error banner up by the title — then clear it.
                _state.value = _state.value.copy(
                    bathStage = "⚠ bath failed",
                    error = e.message ?: "context bath failed",
                )
                delay(2500)
                if (_state.value.bathStage == "⚠ bath failed") {
                    _state.value = _state.value.copy(bathStage = null)
                }
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
                // Suggestions were for the pre-send screen; drop them so nothing stale lingers.
                _state.value = _state.value.copy(sending = false, error = null, suggestions = emptyList())
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = e.message ?: "send failed")
            }
        }
    }
}
