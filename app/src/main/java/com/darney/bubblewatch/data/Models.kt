package com.darney.bubblewatch.data

/** DTOs mirroring clawatch-bridge's pydantic models (see ~/clawatch-bridge). */

enum class ThreadStatus { NEEDS_INPUT, WORKING, IDLE, UNKNOWN }

data class ThreadDto(
    val index: Int,
    val pane: String,
    val command: String,
    val status: String,
    val glyph: String,
    val title: String,
    val label: String,
) {
    val statusEnum: ThreadStatus
        get() = when (status) {
            "NEEDS_INPUT" -> ThreadStatus.NEEDS_INPUT
            "WORKING" -> ThreadStatus.WORKING
            "IDLE" -> ThreadStatus.IDLE
            else -> ThreadStatus.UNKNOWN
        }
}

data class ThreadsDto(val threads: List<ThreadDto>)

/** A single tappable option in a Claude Code interactive menu. */
data class PromptOptionDto(
    val key: String,
    val label: String,
    val selected: Boolean = false,
)

/** A parsed interactive prompt (permission gate / choice list). Null when none active. */
data class PromptDto(
    val question: String = "",
    val options: List<PromptOptionDto> = emptyList(),
)

data class TailDto(
    val index: Int,
    val pane: String,
    val lines: List<String>,
    val capturedAt: String,
    val prompt: PromptDto? = null,
)

data class SendRequest(val text: String, val submit: Boolean)

data class SendResponse(val ok: Boolean)

/** Where + how to reach the bridge. */
data class BridgeConfig(val baseUrl: String, val token: String) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
    /** Normalized base with no trailing slash. */
    val base: String get() = baseUrl.trim().trimEnd('/')
}
