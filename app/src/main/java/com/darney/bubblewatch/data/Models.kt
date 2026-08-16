package com.darney.bubblewatch.data

/** DTOs mirroring clawatch-bridge's pydantic models (see ~/clawatch-bridge). */

enum class ThreadStatus { NEEDS_INPUT, WORKING, IDLE, UNKNOWN }

/** Operator context pressure, computed by the bridge against thresholds you set. */
enum class CtxPressure { NONE, SOFT, HARD }

data class ThreadDto(
    val index: Int,
    val pane: String,
    val command: String,
    val status: String,
    val glyph: String,
    val title: String,
    val label: String,
    val hasPrompt: Boolean = false,
    // Durable pane identity. The bridge computed these for a while before it
    // DECLARED them, so they were silently dropped on the way out — they only
    // started arriving with the pressure work.
    val paneId: String? = null,
    val repo: String? = null,
    // Claude Code status-bar meter (per pane); null when the status line was not visible.
    val model: String? = null,
    val ctxTokens: Int? = null,
    // The rendering step behind ctxTokens ("107k" -> 107000 at resolution 1000).
    // Never report a delta smaller than this as an improvement.
    val ctxResolution: Int? = null,
    val ctxTier: String? = null,
    // Operator pressure, computed bridge-side: NONE | SOFT | HARD.
    // NOT ctxTier — the vendor only emits a tier near its own ~1M limit, so real
    // sessions carry none and a tier-driven meter never lights when it matters.
    val ctxPressure: String? = null,
    val costUsd: Double? = null,
    val spendTokens: Int? = null,
) {
    /** Null when the bridge sent no pressure at all — an older bridge, not "no
     *  pressure". Callers fall back rather than render a confident green. */
    val pressureOrNull: CtxPressure?
        get() = when (ctxPressure) {
            "HARD" -> CtxPressure.HARD
            "SOFT" -> CtxPressure.SOFT
            "NONE" -> CtxPressure.NONE
            else -> null
        }

    val statusEnum: ThreadStatus
        get() = when (status) {
            "NEEDS_INPUT" -> ThreadStatus.NEEDS_INPUT
            "WORKING" -> ThreadStatus.WORKING
            "IDLE" -> ThreadStatus.IDLE
            else -> ThreadStatus.UNKNOWN
        }
}

data class ThreadsDto(val threads: List<ThreadDto>)

/** 202 response from POST /api/threads/{i}/wash. */
data class WashStartDto(val washId: String, val stage: String = "QUEUED")

/** GET /api/threads/{i}/wash/{washId} — the wash's current stage. */
data class WashStatusDto(
    val washId: String,
    val stage: String,              // QUEUED | CLEAR | VERIFY | RESEED | DONE
    val outcome: String? = null,    // ok | cleared_not_reseeded | failed | blocked
    val reseed: String? = null,     // command | none
)

/** A single tappable option in a Claude Code interactive menu. */
data class PromptOptionDto(
    val key: String,
    val label: String,
    val selected: Boolean = false,
    // Multi-select checkbox state; null when the option (or menu) isn't a toggle.
    val checked: Boolean? = null,
)

/** A parsed interactive prompt (permission gate / choice list). Null when none active. */
data class PromptDto(
    val question: String = "",
    val options: List<PromptOptionDto> = emptyList(),
    // True for a multi-select menu: digits TOGGLE; submission is a separate step
    // (the bridge's submit-menu action).
    val multiSelect: Boolean = false,
)

data class TailDto(
    val index: Int,
    val pane: String,
    val lines: List<String>,
    val capturedAt: String,
    val prompt: PromptDto? = null,
)

/** GET /api/threads/{i}/history — Claude's own transcript, not the terminal.
 *
 *  A Claude pane runs on the alternate screen and tmux keeps no scrollback for it,
 *  so [TailDto] can only ever return what is on the screen, already wrapped to the
 *  desktop's column count. These lines are LOGICAL: a paragraph arrives whole,
 *  never wrapped by anything, which is what lets a 1.4" screen re-flow it instead
 *  of inheriting a 200-column layout.
 *
 *  [lastTurn] is how many of the LAST lines are Claude's closing message -- 0 when
 *  the session ends on a prompt or a tool call, so it doubles as "it is your move".
 *  The watch cannot work this out for itself: only the FIRST line of a prompt is
 *  marked (`▸ `), so an unmarked line is the operator's second paragraph or
 *  Claude's first and the text does not say which.
 */
data class HistoryDto(
    val index: Int = -1,
    val pane: String = "",
    val lines: List<String> = emptyList(),
    val capturedAt: String = "",
    val hasOlder: Boolean = false,
    // "matched" = this pane's own screen was found in the transcript · "only" =
    // nothing to disambiguate · "mtime" = guessed by recency · "none" = no
    // transcript. A repo commonly has two sessions running, so a guess must not be
    // presented as this pane's history.
    val confidence: String = "none",
    // Defaulted, not required: an older bridge does not send it, and 0 reads as
    // "nothing to set apart" rather than as an error.
    val lastTurn: Int = 0,
) {
    /** The closing message, or empty when the pane is not waiting on you. */
    val closing: List<String>
        get() = if (lastTurn > 0) lines.takeLast(lastTurn) else emptyList()

    /** False for a transcript picked by recency alone -- shown differently,
     *  because reading the pane next door's message is a privacy failure and not a
     *  display glitch. */
    val isThisPane: Boolean get() = confidence == "matched" || confidence == "only"
}

data class SendRequest(val text: String, val submit: Boolean)

/** An allowlisted control key: "escape" | "interrupt" | "clear" | "enter" | "tab". */
data class KeyRequest(val action: String)

data class SendResponse(val ok: Boolean)

/** Result of the submit-menu action. submitted = answers went in; advanced = the
 *  dialog moved to another (unanswered) question instead — re-scrape and keep going. */
data class SubmitMenuDto(
    val ok: Boolean,
    val submitted: Boolean = false,
    val advanced: Boolean = false,
)

/** 2-3 momentum-biased canned replies generated by the bridge; empty on any failure. */
data class SuggestResponseDto(val suggestions: List<String> = emptyList())

/** One-line Haiku summary of what a WORKING thread is doing; empty on any failure. */
data class SummaryResponseDto(val summary: String = "")

/** Catch-me-up digest for a pane you left running: what it did (recap), where it
 *  stands (state), what you could say next (options). All three empty is the bridge
 *  saying "nothing to catch up on" — a real answer, distinct from a FAILED request
 *  (null at the repository). The two must stay tellable apart. */
data class DigestDto(
    val recap: List<String> = emptyList(),
    val state: String = "",
    val options: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = recap.isEmpty() && state.isBlank() && options.isEmpty()
}

/** Where + how to reach the bridge. */
data class BridgeConfig(val baseUrl: String, val token: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
    /** Normalized base with no trailing slash. */
    val base: String get() = baseUrl.trim().trimEnd('/')
}
