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
import com.darney.bubblewatch.BubbleScreen
import com.darney.bubblewatch.data.BridgeRepository
import com.darney.bubblewatch.data.ThreadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IdleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BridgeRepository.get(app)
    private val _needsAttention = MutableStateFlow(false)
    val needsAttention: StateFlow<Boolean> = _needsAttention.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    val any = repo.listThreads().any { it.statusEnum == ThreadStatus.NEEDS_INPUT }
                    _needsAttention.value = any
                } catch (_: Exception) {
                    // ignore transient errors while idling
                }
                delay(4000)
            }
        }
    }
}

/**
 * The co-pilot idle/ambient screen: the bubble animation, shown while threads work.
 * When any thread flips to NEEDS_INPUT it pulses a haptic and calls [onNeedsAttention]
 * so the nav host can return to the list. A long-press exits manually via [onExit].
 */
@Composable
fun IdleScreen(
    onExit: () -> Unit,
    onNeedsAttention: () -> Unit,
    vm: IdleViewModel = viewModel(),
) {
    val needsAttention by vm.needsAttention.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(needsAttention) {
        if (needsAttention) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onNeedsAttention()
        }
    }

    BubbleScreen(toddlerLock = false, onExit = onExit)
}
