package com.darney.bubblewatch.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import kotlinx.coroutines.launch

/**
 * Wires the rotating crown to a scrollable (ScalingLazyListState / ScrollState).
 * Pair with a [FocusRequester] the caller focuses via LaunchedEffect, since rotary
 * events are only delivered to the focused component.
 */
@Composable
fun Modifier.rotaryScroll(
    scrollableState: ScrollableState,
    focusRequester: FocusRequester,
): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .onRotaryScrollEvent { event ->
            scope.launch { scrollableState.scrollBy(event.verticalScrollPixels) }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
