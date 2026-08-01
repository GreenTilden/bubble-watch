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

    // Keep the tail pinned to the newest line as it grows — but only if the user
    // is already near the bottom, so scrolling up to read isn't yanked away.
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
            // so the auto-scroll lands on them.
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

            // Soft keys: allowlisted terminal control for one-tap autonomy.
            item(key = "softkeys") {
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
