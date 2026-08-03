package com.darney.bubblewatch.cowork.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.darney.bubblewatch.cowork.input.rememberVoiceInput
import com.darney.bubblewatch.cowork.threads.meterColor
import com.darney.bubblewatch.cowork.threads.meterLabel
import com.darney.bubblewatch.cowork.threads.statusColor
import com.darney.bubblewatch.data.ThreadStatus
import com.darney.bubblewatch.ui.KeeperIndicator
import com.darney.bubblewatch.ui.KeeperMode
import com.darney.bubblewatch.ui.SentCheck
import com.darney.bubblewatch.ui.confirm
import com.darney.bubblewatch.ui.rememberVibrator
import com.darney.bubblewatch.ui.rotaryScroll
import com.darney.bubblewatch.ui.tick
import kotlinx.coroutines.delay

private val AMBER = Color(0xFFFFB300)
private val CONTEXT_CUE = Color(0xFFAEC97E) // olive-green — the "what you're deciding" line

// Always-present momentum replies (the hybrid safety net if the LLM call is slow or
// fails). Short + fixed so they fit a 2-across row without clipping. Each is staged
// into the draft, then fired by "Send draft". ("explain" was swapped for the ⏎ Enter
// key below, which acts on the terminal directly rather than staging text.)
private val STATIC_REPLIES = listOf("continue", "go ahead")

@Composable
fun ThreadDetailScreen(
    index: Int,
    vm: ThreadDetailViewModel = viewModel(),
    onDone: () -> Unit = {},
) {
    LaunchedEffect(index) { vm.start(index) }
    val state by vm.state.collectAsStateWithLifecycle()

    // Post-send confirm: when a reply / menu-answer finishes (sending true → false,
    // no error), play the foreman for a beat, then auto-return to the thread list —
    // the "answer and glance back at the overview" loop. Guarded so it never fires
    // on a failed send or on the 🛁 / soft-key paths (those don't touch `sending`).
    var showConfirm by remember { mutableStateOf(false) }
    // Auto-return is driven by a one-shot from the VM (a voice reply, or the LAST
    // answer in a stacked-question set) — NOT every `sending` flip, so answering
    // question 1 of N no longer bounces us out before question 2 renders.
    LaunchedEffect(state.sendConfirm) {
        if (state.sendConfirm) {
            showConfirm = true
            delay(1250)
            showConfirm = false
            onDone()
            vm.consumeSendConfirm()
        }
    }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val clipboard = LocalClipboardManager.current
    val vibrator = rememberVibrator()

    // Transient "copied" acknowledgement so the Copy tap is visibly confirmed.
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }

    // 🛁 context-bath is destructive to conversation context, so tapping the button
    // opens an explicit confirm overlay — single taps, no timing window (the old
    // two-tap-within-6s arm/confirm on the tiny chip was too easy to miss).
    var bathConfirm by remember { mutableStateOf(false) }

    // On first entry, land at the TOP of the tail (natural reading order) rather
    // than auto-scrolling to the options at the bottom. After that first landing,
    // follow new lines as they arrive — but only if the user is already near the
    // bottom, so scrolling up to read isn't yanked away.
    // NOTE: deliberately NOT keyed on suggestions — populating them must not scroll.
    var landedAtTop by remember(index) { mutableStateOf(false) }
    LaunchedEffect(state.lines.size, state.prompt) {
        if (state.lines.isEmpty()) return@LaunchedEffect
        if (!landedAtTop) {
            landedAtTop = true
            listState.scrollToItem(0)
            return@LaunchedEffect
        }
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        val atBottom = info.visibleItemsInfo.lastOrNull()?.index?.let { it >= total - 2 } ?: false
        if (atBottom && total > 0) {
            delay(50)
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }

    val onReply = rememberVoiceInput(label = "Reply") { text -> vm.sendText(text, submit = true) }
    val onAdd = rememberVoiceInput(label = "Add") { text -> vm.appendToDraft(text) }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
            modifier = Modifier.rotaryScroll(listState, focusRequester),
            // Flatter edge scale/fade so the tail scrolls like a regular list rather
            // than each line "breathing" as it nears the rim — reads smoother + more
            // even, while keeping the Wear focus feel. (Tail readability pass.)
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                edgeScale = 0.85f,
                edgeAlpha = 0.7f,
            ),
        ) {
            item {
                ListHeader {
                    Text(
                        text = state.title.ifBlank { "dev:1.$index" },
                        color = statusColor(state.status),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }

            // Per-thread TX meter under the title: context tokens + session token
            // spend + tier, colored by pressure so a HARD session is obvious on entry.
            meterLabel(state.ctxTokens, state.spendTokens)?.let { m ->
                item(key = "meter") {
                    Text(
                        text = m + (state.ctxTier?.let { " · $it" } ?: ""),
                        color = meterColor(state.ctxTier, state.ctxTokens),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.error?.let { err ->
                item { Text("⚠ $err", color = AMBER) }
            }

            // While Claude Code is actively generating, pin the mark + bouncing yellow
            // sparkline at the top of the tail — the "it's thinking" tell.
            if (state.status == ThreadStatus.WORKING) {
                item(key = "working") {
                    KeeperIndicator(label = "working…", mode = KeeperMode.THINKING)
                }
            }

            state.lines.forEachIndexed { i, line ->
                item(key = "line$i") {
                    Text(
                        text = line.ifBlank { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Interactive prompt: tappable option chips, pinned near the bottom
            // so the auto-scroll lands on them. This stays the PRIMARY menu path —
            // Claude Code menus select on a digit keypress, so freetext staging
            // can't reliably answer a menu (and mustn't clobber stacked questions).
            state.prompt?.let { prompt ->
                if (state.moreQuestions) {
                    item(key = "nextq") {
                        Text(
                            text = "▸ Next question — answer below",
                            color = Color(0xFF57D9A3),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // Haiku "what you're being asked to decide" line, above the question —
                // so you can pick without reading the whole tail. Auto-fetched per prompt.
                if (state.promptSummary.isNotBlank() || state.promptSummaryLoading) {
                    item(key = "psum") {
                        Text(
                            text = if (state.promptSummary.isNotBlank()) "▸ ${state.promptSummary}" else "▸ …",
                            color = CONTEXT_CUE,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item(key = "q") {
                    Text(
                        text = "❓ ${prompt.question}".trim(),
                        color = AMBER,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Key by POSITION, not opt.key: the server digit isn't guaranteed
                // unique (a plan's numbered lists can repeat 1./2./3.), and a
                // duplicate LazyColumn key throws when the chips compose into view
                // — i.e. exactly when you scroll to the bottom to accept a plan.
                prompt.options.forEachIndexed { i, opt ->
                    item(key = "opt$i") {
                        // A checkbox option TOGGLES in place; anything else (incl.
                        // the action rows of a multi-select menu) answers directly.
                        val isToggle = prompt.multiSelect && opt.checked != null
                        val prefix = when (opt.checked) {
                            true -> "☑ "
                            false -> "☐ "
                            null -> ""
                        }
                        Chip(
                            label = { Text("${opt.key}. $prefix${opt.label}", maxLines = 2) },
                            onClick = {
                                if (isToggle) vm.toggleOption(opt.key) else vm.selectOption(opt.key)
                            },
                            colors = if (opt.selected || opt.checked == true) {
                                ChipDefaults.primaryChipColors()
                            } else {
                                ChipDefaults.secondaryChipColors()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // Multi-select menus need an explicit submit — the digit taps above
                // only toggle. This is the step a bare ⏎ can't do from the wrist.
                if (prompt.multiSelect) {
                    item(key = "submitmenu") {
                        Chip(
                            label = { Text("✔ Submit these") },
                            onClick = { vm.submitMenu() },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 💡 button — suggestions are NEVER fetched automatically (saves the
            // Haiku call). Tap to pull them; tap again to re-fetch. Results render
            // in the full-width stacked chips below. Full Chip = a real tap target.
            item(key = "sugbtn") {
                Chip(
                    onClick = { vm.requestSuggestions() },
                    label = { Text(if (state.suggestionsLoading) "💡 …" else "💡 Ideas", maxLines = 1) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Generated momentum suggestions — FULL-WIDTH STACKED (never beside the
            // tail): each can be up to ~60 chars, so a horizontal row would clip.
            // Tap stages into the draft; "Send draft" fires. Loading shows a hint
            // only while we have nothing yet, so it can't flicker over live chips.
            if (state.suggestionsLoading && state.suggestions.isEmpty()) {
                item(key = "sugloading") {
                    KeeperIndicator(label = "💡 thinking…", mode = KeeperMode.THINKING)
                }
            }
            state.suggestions.forEachIndexed { i, s ->
                item(key = "sug$i") {
                    Chip(
                        // Smaller font + 3 lines so wordier, more specific suggestions
                        // read comfortably without clipping.
                        label = { Text("💡 $s", fontSize = 12.sp, maxLines = 3) },
                        onClick = { vm.appendToDraft(s) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Static quick-reply row — always present (hybrid safety net). Two short
            // phrases as full Chips (2-across) so they're comfortably tappable.
            item(key = "staticreplies") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    STATIC_REPLIES.forEach { phrase ->
                        Chip(
                            onClick = { vm.appendToDraft(phrase) },
                            label = { Text(phrase, maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ⏎ Enter — presses Enter on the remote pane (confirm a prompt, submit the
            // input line). Replaces the old "explain" quick-reply; acts on the terminal
            // directly instead of staging draft text. Full-width so it's easy to hit.
            // Hidden while a multi-select menu is up: there Enter only toggles the
            // highlighted row — "✔ Submit these" (above) is the real submit.
            if (state.prompt?.multiSelect != true) {
                item(key = "enterkey") {
                    Chip(
                        onClick = { vm.sendKey("enter") },
                        label = { Text("⏎ Enter") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.draft.isNotBlank()) {
                item(key = "draft") {
                    Text(
                        text = "✎ ${state.draft}",
                        fontSize = 12.sp,
                        color = AMBER,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "senddraft") {
                    Chip(
                        label = { Text(if (state.sending) "Sending…" else "Send draft") },
                        onClick = { vm.sendDraft(submit = true) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "reply") {
                Chip(
                    label = { Text("🎤 Reply") },
                    onClick = onReply,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "add") {
                Chip(
                    label = { Text("✎ Add") },
                    onClick = onAdd,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 🛁 Clean context — its own full-width button (was a cramped 1/3 chip). One
            // tap opens the confirm overlay; the overlay's "Do it" fires the destructive
            // macro (COPY → CLEAR → PASTE → GO). Inert while a bath is already running.
            item(key = "cleanctx") {
                Chip(
                    onClick = {
                        if (state.bathStage != null) return@Chip
                        bathConfirm = true
                        vibrator.tick()
                    },
                    label = { Text("🛁 Clean context") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 🛁 context-bath renders as a centered overlay (see below), not an inline
            // list item — so the tub plays front-and-center instead of scrolling off.

            // Terminal controls — act on the remote pane. Bigger 2-across Chips so a
            // stop/escape actually lands when you need it.
            item(key = "softkeys1") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        onClick = { vm.sendKey("escape") },
                        label = { Text("Esc") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        onClick = { vm.sendKey("interrupt") },
                        label = { Text("⏹ Stop") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Draft / clipboard tools — act on the LOCAL draft, not the terminal.
            // Copy: tail → watch clipboard (with ✓ feedback). Paste: clipboard → draft.
            item(key = "drafttools") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        onClick = {
                            clipboard.setText(AnnotatedString(state.lines.joinToString("\n")))
                            copied = true
                        },
                        label = { Text(if (copied) "✓ Copied" else "📋 Copy", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Chip(
                        onClick = {
                            clipboard.getText()?.text?.takeIf { it.isNotBlank() }
                                ?.let { vm.appendToDraft(it) }
                        },
                        label = { Text("📥 Paste", maxLines = 1) },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Brief "answering…" overlay while we send the digit and watch for the next
        // stacked question — blocks a double-tap and shows the tap registered.
        if (state.answering && !showConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                KeeperIndicator(label = "answering…", mode = KeeperMode.THINKING)
            }
        }

        // Post-send confirm overlay — the foreman "lands" the reply for a beat
        // before the screen auto-returns to the thread list (see onDone above).
        if (showConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SentCheck()
                    Text(
                        text = "sent",
                        color = Color(0xFF4ADE80),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // 🛁 confirm overlay — single-tap, explicit, unmistakable. "Do it" fires the
        // (destructive) macro; Cancel or tapping the backdrop backs out.
        if (bathConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF2000000))
                    .clickable { bathConfirm = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "🛁 Reset context?",
                        color = AMBER,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Copies the tail, clears the session, re-seeds it.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                    Chip(
                        label = { Text("🛁 Do it") },
                        onClick = {
                            bathConfirm = false
                            vibrator.confirm()
                            vm.contextBath()
                        },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompactChip(
                        label = { Text("Cancel") },
                        onClick = { bathConfirm = false },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 🛁 context-bath overlay — front-and-center like the demo: the tub + rising
        // bubbles, with the live stage caption cycling COPY → CLEAR → PASTE → GO → ✓.
        state.bathStage?.let { stage ->
            // The Keeper grows one step bigger at each stage of the soak, climaxing
            // biggest on the ✓ (a failure deflates back to the starting size).
            val step = when {
                stage.startsWith("🛁 COPY") -> 0
                stage.startsWith("🛁 CLEAR") -> 1
                stage.startsWith("🛁 PASTE") -> 2
                stage.startsWith("🛁 GO") -> 3
                stage.startsWith("✓") -> 4
                else -> 0
            }
            val bathScale by animateFloatAsState(
                targetValue = 1f + step * 0.2f,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "bathGrow",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)),
                contentAlignment = Alignment.Center,
            ) {
                KeeperIndicator(label = stage, mode = KeeperMode.BATH, scale = bathScale)
            }
        }
      }
    }
}
