package com.darney.bubblewatch.cowork.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.darney.bubblewatch.cowork.threads.statusColor
import com.darney.bubblewatch.ui.rotaryScroll
import kotlinx.coroutines.delay

private val AMBER = Color(0xFFFFB300)

// Always-present momentum replies (the hybrid safety net if the LLM call is slow
// or fails). Short + fixed so they fit a 3-across row without clipping. Each is
// staged into the draft, then fired by "Send draft".
private val STATIC_REPLIES = listOf("continue", "go ahead", "explain")

@Composable
fun ThreadDetailScreen(
    index: Int,
    vm: ThreadDetailViewModel = viewModel(),
) {
    LaunchedEffect(index) { vm.start(index) }
    val state by vm.state.collectAsStateWithLifecycle()

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val clipboard = LocalClipboardManager.current

    // Keep the tail pinned to the newest line as it grows — but only if the user
    // is already near the bottom, so scrolling up to read isn't yanked away.
    // NOTE: deliberately NOT keyed on suggestions — populating them must not scroll.
    LaunchedEffect(state.lines.size, state.prompt) {
        val info = listState.layoutInfo
        val total = info.totalItemsCount
        val atBottom = info.visibleItemsInfo.lastOrNull()?.index?.let { it >= total - 2 } ?: true
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
        ScalingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
            modifier = Modifier.rotaryScroll(listState, focusRequester),
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

            state.error?.let { err ->
                item { Text("⚠ $err", color = AMBER) }
            }

            state.lines.forEachIndexed { i, line ->
                item(key = "line$i") {
                    Text(
                        text = line.ifBlank { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
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
                item(key = "q") {
                    Text(
                        text = "❓ ${prompt.question}".trim(),
                        color = AMBER,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                prompt.options.forEach { opt ->
                    item(key = "opt${opt.key}") {
                        Chip(
                            label = { Text("${opt.key}. ${opt.label}", maxLines = 2) },
                            onClick = { vm.selectOption(opt.key) },
                            colors = if (opt.selected) {
                                ChipDefaults.primaryChipColors()
                            } else {
                                ChipDefaults.secondaryChipColors()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Generated momentum suggestions — FULL-WIDTH STACKED (never beside the
            // tail): each can be up to ~60 chars, so a horizontal row would clip.
            // Tap stages into the draft; "Send draft" fires. Loading shows a hint
            // only while we have nothing yet, so it can't flicker over live chips.
            if (state.suggestionsLoading && state.suggestions.isEmpty()) {
                item(key = "sugloading") {
                    Text(
                        text = "💡 …",
                        color = AMBER,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            state.suggestions.forEachIndexed { i, s ->
                item(key = "sug$i") {
                    Chip(
                        label = { Text("💡 $s", maxLines = 2) },
                        onClick = { vm.appendToDraft(s) },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Static quick-reply row — always present (hybrid safety net). Short
            // fixed phrases, so a 3-across CompactChip row fits without clipping.
            item(key = "staticreplies") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    STATIC_REPLIES.forEach { phrase ->
                        CompactChip(
                            onClick = { vm.appendToDraft(phrase) },
                            label = { Text(phrase, maxLines = 1) },
                            colors = ChipDefaults.secondaryChipColors(),
                            modifier = Modifier.weight(1f),
                        )
                    }
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

            // Soft keys: allowlisted terminal control for one-tap autonomy. Two rows
            // of two so labels stay readable on a round display; Copy pairs with Clr.
            item(key = "softkeys1") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CompactChip(
                        onClick = { vm.sendKey("escape") },
                        label = { Text("Esc") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    CompactChip(
                        onClick = { vm.sendKey("interrupt") },
                        label = { Text("⏹") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "softkeys2") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Copy the current tail to the watch clipboard; paste it back
                    // into a reply via the system keyboard inside ✎ Add.
                    CompactChip(
                        onClick = { clipboard.setText(AnnotatedString(state.lines.joinToString("\n"))) },
                        label = { Text("📋 Copy") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                    CompactChip(
                        onClick = { vm.sendKey("clear") },
                        label = { Text("Clr") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
