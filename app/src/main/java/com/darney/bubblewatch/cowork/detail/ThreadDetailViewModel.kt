package com.darney.bubblewatch.cowork.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darney.bubblewatch.AmbientState
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.CtxPressure
import com.darney.bubblewatch.data.DigestDto
import com.darney.bubblewatch.data.WashStatusDto
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
    // The pane this screen believes it is looking at. An INDEX is not an identity:
    // tmux renumbers indices when a pane is killed, so index 3 can become a
    // different Claude session between two polls. Read on every refresh and sent
    // back as an assertion the bridge checks; null until the first poll lands, and
    // null is a no-op server-side.
    val paneId: String? = null,
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
    // Catch-me-up digest (📰). Fetched ONLY on tap — it is the priciest LLM call
    // on this screen (Sonnet over a 150-line history tail). null = not requested
    // (or cleared as stale); an all-empty digest = "nothing to catch up on".
    val digest: DigestDto? = null,
    val digestLoading: Boolean = false,
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
    // Operator pressure from the bridge; null means an older bridge, not "fine".
    val ctxPressure: CtxPressure? = null,
    val costUsd: Double? = null,
    val spendTokens: Int? = null,
)

class ThreadDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BridgeRepository.get(app)
    private val _state = MutableStateFlow(ThreadDetailUiState())
    val state: StateFlow<ThreadDetailUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var suggestJob: Job? = null
    private var promptSummaryJob: Job? = null
    private var digestJob: Job? = null

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
            // Taken from THIS poll, not from stored state: it is the freshest reading
            // of what index means, and every call below asserts against it.
            val paneId = meta?.paneId ?: _state.value.paneId
            val tail = repo.tail(index, paneId = paneId)
            val cur = _state.value
            val newStatus = meta?.statusEnum ?: ThreadStatus.UNKNOWN
            val newTitle = meta?.label?.ifBlank { meta.title } ?: cur.title
            val linesChanged = tail.lines != cur.lines
            val promptChanged = tail.prompt != cur.prompt
            // Suggestions are no longer auto-fetched on prompt / needs-input
            // transitions — the user pulls them on demand via 💡 (requestSuggestions).
            // Nothing moved — emit nothing at all, so there's zero recomposition
            // for a thread parked at a question or idle. This is the calm case.
            // paneId is part of "something moved" on purpose. Without it the calm
            // path returns early and a quiet pane never learns its own identity --
            // and worse, an index that has come to point at a DIFFERENT pane while
            // nothing on screen changed is exactly the case that must not be silent.
            if (!linesChanged && !promptChanged && paneId == cur.paneId &&
                newStatus == cur.status && newTitle == cur.title && cur.error == null
            ) {
                return
            }
            _state.value = cur.copy(
                paneId = paneId,
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
                ctxPressure = meta?.pressureOrNull ?: cur.ctxPressure,
                costUsd = meta?.costUsd ?: cur.costUsd,
                spendTokens = meta?.spendTokens ?: cur.spendTokens,
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
            val sug = repo.suggest(index, paneId = _state.value.paneId)
            val cur = _state.value
            if (sug != cur.suggestions || cur.suggestionsLoading) {
                _state.value = cur.copy(suggestions = sug, suggestionsLoading = false)
            }
        }
    }

    /** User-initiated catch-me-up (📰). The bridge reads a 150-line HISTORY tail —
     *  lines above the visible screen — and answers "what was I doing and what are
     *  my moves". On-demand only, same policy as 💡: nothing auto-fires it.
     *  A null from the repo is a FAILED request and surfaces as an error; an empty
     *  digest renders as "nothing to catch up on" — never conflate them. */
    fun requestDigest() {
        val index = _state.value.index
        if (index < 0) return
        if (digestJob?.isActive == true) return
        digestJob = viewModelScope.launch {
            _state.value = _state.value.copy(digestLoading = true)
            val d = repo.digest(index, paneId = _state.value.paneId)
            val cur = _state.value
            _state.value = if (d == null)
                cur.copy(digestLoading = false, error = "catch-me-up failed")
            else
                cur.copy(digest = d, digestLoading = false)
        }
    }

    /** Auto-fetch the menu-aware "what you're deciding" line when a prompt appears.
     *  Off the poll path, cancels any prior fetch on a new question, never throws
     *  (repo -> ""). Cheap: the paused tail is static, so it's one call per prompt. */
    private fun fetchPromptSummary(index: Int) {
        promptSummaryJob?.cancel()
        promptSummaryJob = viewModelScope.launch {
            _state.value = _state.value.copy(promptSummary = "", promptSummaryLoading = true)
            val s = repo.promptSummary(index, paneId = _state.value.paneId)
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
            // Digest cleared with the suggestions: both describe the pane as it was
            // before this answer, and a stale "where it stands" is worse than none.
            _state.value = _state.value.copy(answering = true, error = null, suggestions = emptyList(), digest = null)
            try {
                repo.send(index, key, submit = false, paneId = _state.value.paneId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(answering = false, error = e.message ?: "send failed")
                return@launch
            }
            watchAfterAnswer(index, answered)
        }
    }

    /** Toggle one checkbox of a multi-select menu: send the digit, then refresh the
     *  same menu in place (it stays up — submission is the separate ✔ button). */
    fun toggleOption(key: String) {
        val index = _state.value.index
        if (index < 0) return
        viewModelScope.launch {
            try {
                repo.send(index, key, submit = false, paneId = _state.value.paneId)
                delay(600)
                val tail = repo.tail(index, paneId = _state.value.paneId)
                _state.value = _state.value.copy(prompt = tail.prompt, lines = tail.lines, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "toggle failed")
            }
        }
    }

    /** The ✔ Submit-these button of a multi-select menu. The bridge Tabs to the
     *  review tab and confirms; if an unanswered question renders instead, the
     *  shared watch loop presents it just like a stacked single-select. */
    fun submitMenu() {
        val index = _state.value.index
        if (index < 0) return
        val answered = _state.value.prompt
        viewModelScope.launch {
            _state.value = _state.value.copy(answering = true, error = null, suggestions = emptyList(), digest = null)
            try {
                repo.submitMenu(index, paneId = _state.value.paneId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(answering = false, error = e.message ?: "submit failed")
                return@launch
            }
            watchAfterAnswer(index, answered)
        }
    }

    /** Shared post-answer watch: poll for the next stacked question (a different
     *  menu) or completion (menu stays gone), then confirm + return. */
    private suspend fun watchAfterAnswer(index: Int, answered: PromptDto?) {
        run {
            // Poll for the next question (a different menu) or completion (menu gone).
            var nullStreak = 0
            var latestLines = _state.value.lines
            repeat(NEXT_Q_POLLS) {
                delay(NEXT_Q_INTERVAL_MS)
                val tail = runCatching { repo.tail(index, paneId = _state.value.paneId) }.getOrNull() ?: return@repeat
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
                        return@run
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
                            return@run
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
                repo.sendKey(index, action, paneId = _state.value.paneId)
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "key failed")
            }
        }
    }

    /**
     * Context-bath macro (🛁). The watch now ASKS; the bridge does the work.
     *
     * This used to be orchestrated here: capture the tail, send Escape / C-u /
     * "/clear", poll to verify, then PASTE THE WHOLE TAIL BACK. All of that moved
     * server-side, for three reasons that are worth keeping written down:
     *
     *  1. The re-paste is GONE, with no fallback. It round-tripped full pane
     *     content through this watch and back, and — because the bridge collapses
     *     newlines on /send — it landed as one space-joined line. The bridge now
     *     re-seeds with a configured slash-command instead. Net effect: this
     *     screen no longer handles pane content at all.
     *  2. A wash cannot be watch-driven anyway once it is automatic: the polling
     *     loop suspends whenever the screen goes ambient, and the watch is off the
     *     wrist half the day. Bridge-side is what makes automation a config flip
     *     rather than a rewrite.
     *  3. The guards (pane-command allowlist, identity re-check, clear
     *     verification, the two-part autocomplete guard) belong next to the code
     *     that presses the keys, not one network hop away.
     *
     * Failures still surface AT the bath indicator, because that is where the user
     * is looking. The confirm-tap gate on the screen is unchanged.
     */
    fun contextBath() {
        val index = _state.value.index
        if (index < 0) return
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(bathStage = "🛁 …", error = null)
                val started = repo.startWash(index, paneId = _state.value.paneId)

                // Poll the stage. 400ms is fast enough to feel live on a wrist and
                // slow enough not to hammer the bridge; the cap is a backstop so a
                // wedged wash cannot spin this coroutine forever.
                var last: WashStatusDto? = null
                for (tick in 1..75) {                 // ~30s ceiling
                    delay(400)
                    last = runCatching { repo.washStatus(index, started.washId) }.getOrNull()
                    _state.value = _state.value.copy(bathStage = stageGlyph(last?.stage))
                    if (last?.stage == "DONE") break
                }

                when (last?.outcome) {
                    "ok" -> {
                        _state.value = _state.value.copy(
                            bathStage = "✓ context reset", suggestions = emptyList(), digest = null)
                        refreshOnce(index)
                        delay(1500)
                        if (_state.value.bathStage == "✓ context reset") {
                            _state.value = _state.value.copy(bathStage = null)
                        }
                    }
                    // Cleared but not re-seeded is a REAL, distinct outcome, not a
                    // failure: this repo has no re-seed command or cannot satisfy
                    // its probe. Saying "✓ cleared" rather than "✓ context reset"
                    // is the honest difference.
                    "cleared_not_reseeded" -> {
                        _state.value = _state.value.copy(
                            bathStage = "✓ cleared", suggestions = emptyList(), digest = null)
                        refreshOnce(index)
                        delay(1800)
                        if (_state.value.bathStage == "✓ cleared") {
                            _state.value = _state.value.copy(bathStage = null)
                        }
                    }
                    else -> {
                        _state.value = _state.value.copy(
                            bathStage = if (last?.outcome == "blocked") "⚠ blocked" else "⚠ not cleared",
                            error = if (last?.outcome == "blocked")
                                "a guard refused this wash"
                            else
                                "pane didn't clear — nothing was re-seeded",
                        )
                        delay(2500)
                        _state.value = _state.value.copy(bathStage = null)
                    }
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

    private fun stageGlyph(stage: String?): String = when (stage) {
        "QUEUED" -> "🛁 …"
        "CLEAR" -> "🛁 CLEAR"
        "VERIFY" -> "🛁 VERIFY"
        "RESEED" -> "🛁 RESEED"
        else -> "🛁 …"
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
                repo.send(index, text, submit, paneId = _state.value.paneId)
                onOk()
                // Suggestions were for the pre-send screen; drop them so nothing stale lingers.
                // sendConfirm is the one-shot that plays the confirm + auto-returns.
                _state.value = _state.value.copy(sending = false, error = null, suggestions = emptyList(), digest = null, sendConfirm = true)
                refreshOnce(index)
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = e.message ?: "send failed")
            }
        }
    }
}
