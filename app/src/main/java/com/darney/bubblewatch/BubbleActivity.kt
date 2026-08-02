package com.darney.bubblewatch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

    // Re-post the persistent shortcut bubble once the user grants POST_NOTIFICATIONS.
    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) PersistentBubble.show(this)
        }

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
        // System splash covers cold-start until the first Compose frame (SplashCard).
        installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        // Pin a silent "🫧 Bubbles" shortcut in the notification stream so a tap always
        // gets back into the app. Shows immediately if already permitted; on Wear OS 4
        // (API 33+) request POST_NOTIFICATIONS first, then post from the callback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            PersistentBubble.show(this)
        } else {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

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
