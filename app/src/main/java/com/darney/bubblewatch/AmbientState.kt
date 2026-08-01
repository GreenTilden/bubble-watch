package com.darney.bubblewatch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide ambient (screen dimmed / asleep) signal for battery.
 *
 * [BubbleActivity] flips this from the watch's `AmbientLifecycleObserver`. The
 * background poll loops suspend while [isAmbient] is true (see `awaitActive`) so a
 * pocketed pendant stops hammering the bridge over WiFi; they resume the instant the
 * screen comes back. Cheap and process-wide — a single source of truth rather than
 * threading a flag through every ViewModel.
 */
object AmbientState {
    private val _isAmbient = MutableStateFlow(false)
    val isAmbient: StateFlow<Boolean> = _isAmbient.asStateFlow()

    fun setAmbient(value: Boolean) {
        _isAmbient.value = value
    }
}
