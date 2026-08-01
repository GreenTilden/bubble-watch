package com.darney.bubblewatch.ui

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Platform-Vibrator haptics. Compose's `LocalHapticFeedback` is weak or a no-op on
 * Wear OS, so anything that has to actually be *felt* — the 🛁 bath confirm, the
 * needs-input alert — goes through the real system Vibrator instead (the same path
 * BubbleScreen already uses). Always guarded: a rejected haptic must never crash the UI.
 */
@Composable
fun rememberVibrator(): Vibrator? {
    val context = LocalContext.current
    return remember { context.getSystemService(Vibrator::class.java) }
}

/** Light tick — "your tap registered" (e.g. arming a two-tap action). */
fun Vibrator?.tick() {
    this ?: return
    try {
        vibrate(VibrationEffect.createOneShot(20, 60))
    } catch (_: Exception) {
        // Some devices/permissions reject haptics — never let it bubble up.
    }
}

/** Firm double buzz — a committed action or an attention alert you shouldn't miss. */
fun Vibrator?.confirm() {
    this ?: return
    try {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 60, 35), -1))
    } catch (_: Exception) {
    }
}
