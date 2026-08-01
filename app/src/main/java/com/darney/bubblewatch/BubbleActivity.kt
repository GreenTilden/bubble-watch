package com.darney.bubblewatch

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.wear.ambient.AmbientLifecycleObserver
import com.darney.bubblewatch.ui.CoworkApp

/**
 * Single launcher activity. Hosts the Cowork nav graph (thread list / detail / voice
 * reply / bubble idle mode). Ambient + keep-screen-on are kept app-wide so the
 * co-pilot stays glanceable. The old global back-block was REMOVED: it belonged to
 * toddler mode only and would trap the co-pilot; the bubble idle screen now handles
 * its own exit (long-press), and everything else uses swipe-to-dismiss navigation.
 */
class BubbleActivity : ComponentActivity() {

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        // Ambient = screen dimmed/asleep. Signal it app-wide so the background poll
        // loops stop hitting the bridge while the pendant is pocketed, and resume the
        // instant the watch wakes. (See AmbientState + the poll loops.)
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            AmbientState.setAmbient(true)
        }
        override fun onExitAmbient() {
            AmbientState.setAmbient(false)
        }
        override fun onUpdateAmbient() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        // FLAG_KEEP_SCREEN_ON removed for pendant use: it pinned the display in
        // always-on ambient and was the single biggest battery drain. The watch now
        // dims and sleeps normally when idle (poll loops pause via AmbientState).
        // TURN_SCREEN_ON + SHOW_WHEN_LOCKED still wake it glanceable on demand.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        setContent {
            CoworkApp()
        }
    }
}
