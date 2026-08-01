package com.darney.bubblewatch.cowork.idle

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darney.bubblewatch.AmbientState
import com.darney.bubblewatch.BubbleScreen
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.ThreadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BridgeRepository.get(app)

    // The set of threads already needing input when idle mode opened. We do NOT
    // bounce back for these — only for a thread that NEWLY flips to needs-input.
    private var baseline: Set<Int>? = null

    private val _newAttention = MutableStateFlow(false)
    val newAttention: StateFlow<Boolean> = _newAttention.asStateFlow()

    init {
        viewModelScope.launch {
            // Back off the longer idle sits: a quick attention-bounce right after
            // entering idle, then widen to save battery. Suspends entirely while the
            // watch is ambient/asleep so a pocketed pendant isn't polling every 4s.
            var interval = 4000L
            while (true) {
                AmbientState.isAmbient.first { !it } // pause while screen is asleep
                try {
                    val needs = repo.listThreads()
                        .filter { it.statusEnum == ThreadStatus.NEEDS_INPUT }
                        .map { it.index }
                        .toSet()
                    val base = baseline
                    if (base == null) {
                        // First reading: whatever already needs input is the baseline.
                        baseline = needs
                    } else if (needs.any { it !in base }) {
                        _newAttention.value = true
                    }
                } catch (_: Exception) {
                    // ignore transient errors while idling
                }
                delay(interval)
                interval = (interval + 3000L).coerceAtMost(20000L)
            }
        }
    }
}

/**
 * The co-pilot idle/ambient screen: the bubble animation, shown while threads work.
 * When a thread that WASN'T already pending flips to NEEDS_INPUT it pulses a haptic
 * and calls [onNeedsAttention] so the nav host returns to the list. Threads already
 * pending when idle opened are ignored, so idle doesn't instantly bounce. A long-press
 * exits manually via [onExit].
 */
@Composable
fun IdleScreen(
    onExit: () -> Unit,
    onNeedsAttention: () -> Unit,
    vm: IdleViewModel = viewModel(),
) {
    val newAttention by vm.newAttention.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(newAttention) {
        if (newAttention) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onNeedsAttention()
        }
    }

    BubbleScreen(toddlerLock = false, onExit = onExit)
}
