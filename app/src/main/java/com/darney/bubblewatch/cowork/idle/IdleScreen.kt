package com.darney.bubblewatch.cowork.idle

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.darney.bubblewatch.ui.confirm
import com.darney.bubblewatch.ui.tick
import com.darney.bubblewatch.ui.rememberVibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darney.bubblewatch.AmbientState
import com.darney.bubblewatch.BubbleScreen
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.CtxPressure
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

    // The same baseline trick for context pressure. Threads ALREADY under hard
    // pressure when idle opened are not news — buzzing for them would fire on
    // every entry to idle and train the wearer to ignore the buzz, which is
    // precisely the outcome the whole pressure feature exists to avoid.
    // Keyed on paneId, not index: indices are recomputed every poll on the bridge
    // and have already diverged from pane identity in practice.
    private var pressureBaseline: Set<String>? = null

    private val _newAttention = MutableStateFlow(false)
    val newAttention: StateFlow<Boolean> = _newAttention.asStateFlow()

    private val _newPressure = MutableStateFlow(false)
    val newPressure: StateFlow<Boolean> = _newPressure.asStateFlow()

    init {
        viewModelScope.launch {
            // Back off the longer idle sits: a quick attention-bounce right after
            // entering idle, then widen to save battery. Suspends entirely while the
            // watch is ambient/asleep so a pocketed pendant isn't polling every 4s.
            var interval = 4000L
            while (true) {
                AmbientState.isAmbient.first { !it } // pause while screen is asleep
                try {
                    val threads = repo.listThreads()

                    val needs = threads
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

                    val hard = threads
                        .filter { it.pressureOrNull == CtxPressure.HARD }
                        .mapNotNull { it.paneId }
                        .toSet()
                    val pbase = pressureBaseline
                    if (pbase == null) {
                        pressureBaseline = hard
                    } else if (hard.any { it !in pbase }) {
                        _newPressure.value = true
                        // Fold the crossing into the baseline so one thread crossing
                        // the line buzzes ONCE, not on every poll while it sits there.
                        pressureBaseline = pbase + hard
                    }
                } catch (_: Exception) {
                    // ignore transient errors while idling
                }
                delay(interval)
                interval = (interval + 3000L).coerceAtMost(20000L)
            }
        }
    }

    /** Called after the pressure haptic fires, so it can fire again next crossing. */
    fun acknowledgePressure() {
        _newPressure.value = false
    }
}

/**
 * The co-pilot idle/ambient screen: the bubble animation, shown while threads work.
 * When a thread that WASN'T already pending flips to NEEDS_INPUT it pulses a haptic
 * and calls [onNeedsAttention] so the nav host returns to the list. Threads already
 * pending when idle opened are ignored, so idle doesn't instantly bounce. A long-press
 * exits manually via [onExit].
 *
 * A thread newly crossing into HARD context pressure buzzes too, but does NOT navigate.
 * That asymmetry is deliberate: a question is blocking and wants you now, whereas
 * context pressure is advisory — yanking the wearer out of the bubble screen for it
 * would make the nudge feel like an alarm and get it turned off within a week.
 */
@Composable
fun IdleScreen(
    onExit: () -> Unit,
    onNeedsAttention: () -> Unit,
    vm: IdleViewModel = viewModel(),
) {
    val newAttention by vm.newAttention.collectAsStateWithLifecycle()
    val newPressure by vm.newPressure.collectAsStateWithLifecycle()
    val vibrator = rememberVibrator()

    LaunchedEffect(newAttention) {
        if (newAttention) {
            vibrator.confirm()
            onNeedsAttention()
        }
    }

    LaunchedEffect(newPressure) {
        if (newPressure) {
            vibrator.tick()          // a lighter touch than confirm() — advisory, not blocking
            vm.acknowledgePressure()
        }
    }

    BubbleScreen(toddlerLock = false, onExit = onExit)
}
