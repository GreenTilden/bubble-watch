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
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {}
        override fun onExitAmbient() {}
        override fun onUpdateAmbient() {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        setContent {
            CoworkApp()
        }
    }
}
