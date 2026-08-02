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
    // Menu-answer flow for stacked (multi-)questions:
    val answering: Boolean = false,     // digit sent; watching for the next question / completion
    val moreQuestions: Boolean = false, // the current prompt is a follow-up in a stacked ask
    val sendConfirm: Boolean = false,   // one-shot: a returning send finished -> confirm + go back
    val error: String? = null,
    // Momentum suggestions from the bridge. Fetched off the poll path (on entry
    // and when a new prompt / needs-input transition appears), not every tick.
    val suggestions: List<String> = emptyList(),
    val suggestionsLoading: Boolean = false,
    // Menu-aware "what you're being asked to decide" line, auto-fetched when a
    // prompt appears (cheap: the paused tail is static, so one Haiku call per prompt).
    val promptSummary: String = "",
    val promptSummaryLoading: Boolean = false,
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
    private var promptSummaryJob: Job? = null

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
        // Don't fight the menu-answer sequence — selectOption drives the prompt then.
        if (_state.value.answering) return
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
                moreQuestions = if (promptChanged) false else cur.moreQuestions,
                promptSummary = if (promptChanged) "" else cur.promptSummary,
                // Meter from list metadata; keep last known when a poll misses it.
                model = meta?.model ?: cur.model,
                ctxTokens = meta?.ctxTokens ?: cur.ctxTokens,
                ctxTier = meta?.ctxTier ?: cur.ctxTier,
                costUsd = meta?.costUsd ?: cur.costUsd,
                error = null,
            )
            // A new menu appeared -> pull its decision summary; it vanished -> clear.
            if (promptChanged) {
                if (tail.prompt != null) fetchPromptSummary(index)
                else _state.value = _state.value.copy(promptSummary = "", promptSummaryLoading = false)
            }
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

    /** Auto-fetch the menu-aware "what you're deciding" line when a prompt appears.
     *  Off the poll path, cancels any prior fetch on a new question, never throws
     *  (repo -> ""). Cheap: the paused tail is static, so it's one call per prompt. */
    private fun fetchPromptSummary(index: Int) {
        promptSummaryJob?.cancel()
        promptSummaryJob = viewModelScope.launch {
            _state.value = _state.value.copy(promptSummary = "", promptSummaryLoading = true)
            val s = repo.promptSummary(index)
            val cur = _state.value
            // Apply only if a menu is still up, so a late result can't flash after
            // the user already answered.
            _state.value = if (cur.prompt != null)
                cur.copy(promptSummary = s, promptSummaryLoading = false)
            else
                cur.copy(promptSummary = "", promptSummaryLoading = false)
        }
    }

    /** Tap an interactive option: send its digit (no Enter — CC menus select on the
     *  number key), then watch for the NEXT stacked question. Claude Code presents a
     *  multi-question ask one menu at a time; after sending we poll briefly and, if a
     *  DIFFERENT menu appears, present it and cue the user to answer it too — looping
     *  until the menu stays gone (all answered), only THEN confirming + returning. */
    fun selectOption(key: String) {
        val index = _state.value.index
        if (index < 0) return
        val answered = _state.value.prompt
        viewModelScope.launch {
            _state.value = _state.value.copy(answering = true, error = null, suggestions = emptyList())
            try {
                repo.send(index, key, submit = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(answering = false, error = e.message ?: "send failed")
                return@launch
            }
            // Poll for the next question (a different menu) or completion (menu gone).
            var nullStreak = 0
            var latestLines = _state.value.lines
            repeat(NEXT_Q_POLLS) {
                delay(NEXT_Q_INTERVAL_MS)
                val tail = runCatching { repo.tail(index) }.getOrNull() ?: return@repeat
                latestLines = tail.lines
                val p = tail.prompt
                when {
                    p != null && p != answered -> {
                        // The next stacked question — present it, cue the user, and
                        // pull a fresh "what you're deciding" line for it.
                        _state.value = _state.value.copy(
                            answering = false,
                            prompt = p,
                            lines = tail.lines,
                            moreQuestions = true,
                            promptSummary = "",
                        )
                        fetchPromptSummary(index)
                        return@launch
                    }
                    p == null -> {
                        nullStreak++
                        if (nullStreak >= NEXT_Q_DONE_STREAK) {
                            // Menu stayed gone -> every question answered. Confirm + return.
                            _state.value = _state.value.copy(
                                answering = false,
                                prompt = null,
                                lines = tail.lines,
                                moreQuestions = false,
                                sendConfirm = true,
                                promptSummary = "",
                            )
                            return@launch
                        }
                    }
                    else -> nullStreak = 0 // same menu still rendering; keep waiting
                }
            }
            // Timed out with the same menu up (CC slow, or identical stacked questions):
            // don't bounce — resume normal polling on this screen.
            _state.value = _state.value.copy(answering = false, lines = latestLines)
            refreshOnce(index)
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

    /** Clear the one-shot auto-return flag once the screen has played the confirm. */
    fun consumeSendConfirm() {
        _state.value = _state.value.copy(sendConfirm = false)
    }

    private companion object {
        const val NEXT_Q_INTERVAL_MS = 400L
        const val NEXT_Q_POLLS = 20        // ~8s safety window
        const val NEXT_Q_DONE_STREAK = 2   // menu gone ~0.8s => all questions answered
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
                // sendConfirm is the one-shot that plays the confirm + auto-returns.
                _state.value = _state.value.copy(sending = false, error = null, suggestions = emptyList(), sendConfirm = true)
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = e.message ?: "send failed")
            }
        }
    }
}
