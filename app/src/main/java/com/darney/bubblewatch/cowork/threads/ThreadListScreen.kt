package com.darney.bubblewatch.cowork.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.darney.bubblewatch.AmbientState
import com.darney.bubblewatch.data.CtxPressure
import com.darney.bubblewatch.data.ThreadDto
import com.darney.bubblewatch.data.ThreadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.darney.bubblewatch.ui.rotaryScroll
import kotlinx.coroutines.delay

private val AMBER = Color(0xFFFFB300)
private val BLUE = Color(0xFF6FA8DC)
private val GREY = Color(0xFF888888)
private val GREEN = Color(0xFF57D9A3) // has tappable options — answer silently
private val RED = Color(0xFFE06C6C)   // context/cost pressure — session hygiene warning

/** How many thread rows to show at most. NEEDS_INPUT threads are never hidden by
 *  this cap — WORKING threads fill whatever room is left. */
private const val MAX_VISIBLE = 3

fun statusColor(status: ThreadStatus): Color = when (status) {
    ThreadStatus.NEEDS_INPUT -> AMBER
    ThreadStatus.WORKING -> BLUE
    else -> GREY
}

/** Color the per-thread TX meter by OPERATOR pressure: red at hard, amber at soft,
 *  green when there's headroom. Glanceable hygiene.
 *
 *  This used to read `tier == "HARD" || tokens >= 400_000`, which was not merely
 *  unwired — it was calibrated to the wrong policy. The vendor's tier only appears
 *  near its OWN ~1M-context limit, and 400k is ~2.7x past the operator's actual
 *  hard stop of 150k, so the meter stayed green through the entire range where
 *  breaking the session was the highest-value thing to do. Pressure is now
 *  computed by the bridge against thresholds the operator sets.
 *
 *  `tier`/`tokens` remain as a fallback for a bridge too old to send ctxPressure —
 *  a watch that silently showed nothing would be worse than an imperfect tint. */
fun meterColor(pressure: CtxPressure?, tier: String?, tokens: Int?): Color = when {
    pressure == CtxPressure.HARD -> RED
    pressure == CtxPressure.SOFT -> AMBER
    pressure == CtxPressure.NONE -> GREEN
    tier?.equals("HARD", ignoreCase = true) == true -> RED
    (tokens ?: 0) >= 400_000 -> AMBER
    else -> GREEN
}

/** Humanize a token count: 812k below a million, 4.2M above. */
private fun tok(n: Int): String =
    if (n >= 1_000_000) String.format("%.1fM", n / 1_000_000.0) else "${n / 1000}k"

/** Compact meter string like "134k · Σ4.2M" (context · session token spend);
 *  null when no meter data is present. Tokens only — no dollars on the wrist. */
fun meterLabel(tokens: Int?, spend: Int?): String? {
    val parts = mutableListOf<String>()
    tokens?.let { parts += tok(it) }
    spend?.let { parts += "Σ${tok(it)}" }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}

/** Every thread, ordered actionable-first: NEEDS_INPUT, then WORKING, then IDLE. */
private fun orderedThreads(all: List<ThreadDto>): List<ThreadDto> {
    fun rank(t: ThreadDto) = when (t.statusEnum) {
        ThreadStatus.NEEDS_INPUT -> 0
        ThreadStatus.WORKING -> 1
        else -> 2
    }
    return all.sortedWith(compareBy({ rank(it) }, { it.index }))
}

/** Collapsed view: every thread needing input (never hidden), then WORKING up to the cap. */
private fun collapsedThreads(ordered: List<ThreadDto>): List<ThreadDto> {
    val needs = ordered.filter { it.statusEnum == ThreadStatus.NEEDS_INPUT }
    val working = ordered.filter { it.statusEnum == ThreadStatus.WORKING }
    val room = (MAX_VISIBLE - needs.size).coerceAtLeast(0)
    return needs + working.take(room)
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun ThreadListScreen(
    onOpenThread: (Int) -> Unit,
    onOpenIdle: () -> Unit,
    onOpenSettings: () -> Unit,
    onFirstLoad: () -> Unit = {},
    vm: ThreadListViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Signal the splash that the first poll landed so it can crossfade away.
    LaunchedEffect(state.loading) { if (!state.loading) onFirstLoad() }
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val scope = rememberCoroutineScope()

    // Land at the top whenever the list becomes current again — e.g. after a reply
    // auto-returns from a thread — so the most actionable items are the first glance.
    LifecycleResumeEffect(Unit) {
        scope.launch { listState.scrollToItem(0) }
        onPauseOrDispose { }
    }

    // Light foreground poll so status (NEEDS_INPUT/WORKING) stays fresh while visible.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (true) {
            AmbientState.isAmbient.first { !it } // pause while screen is asleep
            vm.refresh()
            delay(15000)
        }
    }

    var expanded by remember { mutableStateOf(false) }
    val ordered = orderedThreads(state.threads)
    val collapsed = collapsedThreads(ordered)
    val visible = if (expanded) ordered else collapsed
    val hidden = ordered.size - collapsed.size

    // NEEDS_INPUT threads are the only tappable ones (you reply to them); everything
    // else is shown read-only under a "Working" subheading with its live summary.
    val needs = visible.filter { it.statusEnum == ThreadStatus.NEEDS_INPUT }
    val others = visible.filter { it.statusEnum != ThreadStatus.NEEDS_INPUT }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
            modifier = Modifier.rotaryScroll(listState, focusRequester),
        ) {
            item { ListHeader { Text("🫧 Bubbles") } }

            if (!state.configured) {
                item {
                    Chip(
                        label = { Text("Set up connection") },
                        secondaryLabel = { Text("Enter bridge URL + token") },
                        onClick = onOpenSettings,
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier,
                    )
                }
            }

            state.error?.let { err ->
                item {
                    Text(
                        text = "⚠ $err",
                        color = AMBER,
                        textAlign = TextAlign.Center,
                        modifier = Modifier,
                    )
                }
            }

            // Tappable: threads awaiting you.
            items(needs, key = { "need${it.index}" }) { thread ->
                ThreadChip(thread) { onOpenThread(thread.index) }
            }

            // Read-only: threads that are busy. No tap — there's nothing to answer
            // while an agent is mid-work; the Haiku line says what it's doing.
            if (others.isNotEmpty()) {
                item(key = "workinghdr") {
                    Text(
                        text = "Working",
                        color = GREY,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                items(others, key = { "work${it.index}" }) { thread ->
                    WorkingRow(thread, state.summaries[thread.index])
                }
            }

            if (hidden > 0 && !expanded) {
                item {
                    Chip(
                        label = { Text("Show all ($hidden more)") },
                        onClick = { expanded = true },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
            if (expanded && ordered.size > MAX_VISIBLE) {
                item {
                    Chip(
                        label = { Text("Show less") },
                        onClick = { expanded = false },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }

            if (visible.isEmpty() && state.configured && !state.loading && state.error == null) {
                item { Text("Nothing needs you 🫧", color = GREY, textAlign = TextAlign.Center) }
            }

            item {
                Chip(
                    label = { Text("Idle · bubbles") },
                    onClick = onOpenIdle,
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
            item {
                Chip(
                    label = { Text("Settings") },
                    onClick = onOpenSettings,
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

@Composable
private fun ThreadChip(thread: ThreadDto, onClick: () -> Unit) {
    val label = thread.label.ifBlank { thread.title.ifBlank { thread.pane } }
    // A parsed menu is distinct from plain needs-input: green dot + "tap to answer",
    // so silently-answerable threads stand out from ones awaiting dictation.
    val dotColor = if (thread.hasPrompt) GREEN else statusColor(thread.statusEnum)
    val sub = if (thread.hasPrompt) "tap to answer"
        else thread.statusEnum.name.replace('_', ' ').lowercase()
    val meter = meterLabel(thread.ctxTokens, thread.spendTokens)
    Chip(
        label = { Text(label, maxLines = 2) },
        secondaryLabel = {
            Column {
                Text(sub, maxLines = 1)
                // Per-thread TX meter: context tokens + session token spend, colored by
                // pressure so a runaway (HARD-tier) session is obvious at a glance.
                if (meter != null) {
                    Text(
                        text = meter,
                        color = meterColor(thread.pressureOrNull, thread.ctxTier, thread.ctxTokens),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        },
        icon = { Dot(dotColor) },
        onClick = onClick,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier,
    )
}

/** A busy thread, rendered read-only (deliberately NOT a Chip — no onClick, nothing to
 *  tap). Shows the live Haiku one-liner when we have it, else the plain status, plus the
 *  per-thread TX meter for hygiene. */
@Composable
private fun WorkingRow(thread: ThreadDto, summary: String?) {
    val label = thread.label.ifBlank { thread.title.ifBlank { thread.pane } }
    val sub = when {
        !summary.isNullOrBlank() -> summary
        thread.statusEnum == ThreadStatus.WORKING -> "working…"
        else -> thread.statusEnum.name.replace('_', ' ').lowercase()
    }
    val meter = meterLabel(thread.ctxTokens, thread.spendTokens)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(statusColor(thread.statusEnum))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, maxLines = 2, fontSize = 14.sp)
            Text(sub, color = GREY, fontSize = 11.sp, maxLines = 2)
            if (meter != null) {
                Text(
                    text = meter,
                    color = meterColor(thread.pressureOrNull, thread.ctxTier, thread.ctxTokens),
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
