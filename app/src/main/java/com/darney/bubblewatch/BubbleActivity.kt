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
import com.darney.bubblewatch.ui.CoworkApp

/**
 * Single launcher activity. Hosts the Cowork nav graph (thread list / detail / voice
 * reply / bubble idle mode).
 *
 * FULL-RETREAT behaviour (2026-08-02): Bubbles is a normal foreground app, NOT an
 * always-on ambient surface. It no longer registers for ambient callbacks, so when
 * the display times out (wrist down / idle) the system returns to the user's own
 * watch face instead of keeping Bubbles up — a wrist-raise shows the watch face, not
 * this app. Background poll loops are suspended whenever the activity isn't visible
 * (onStop -> AmbientState=true; onStart -> false) so nothing hits the bridge while
 * backgrounded. The way back in is an explicit tap: the persistent 🫧 notification or
 * the launcher. This replaces the old ambient-surface design, which resumed on every
 * wrist-turn and burned battery.
 *
 * NB: AmbientState is now really an "app not in foreground" gate (name kept to avoid
 * churn in the poll loops that read it); it covers screen-off AND background.
 */
class BubbleActivity : ComponentActivity() {

    // Re-post the persistent shortcut bubble once the user grants POST_NOTIFICATIONS.
    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) PersistentBubble.show(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash covers cold-start until the first Compose frame (SplashCard).
        installSplashScreen()
        super.onCreate(savedInstanceState)

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

        // Keep that bubble alive: a slow WorkManager job re-posts it if Wear
        // kills the process and the system clears the notification, and again
        // after a reboot -- otherwise it vanishes and never comes back.
        BubbleKeepAliveWorker.ensureScheduled(this)

        // Wake glanceable on demand (e.g. relaunched from the notif) but do NOT pin
        // the screen on — FLAG_KEEP_SCREEN_ON was the biggest drain and stays off.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        setContent {
            CoworkApp()
        }
    }

    // Foreground/visible -> resume the background poll loops.
    override fun onStart() {
        super.onStart()
        AmbientState.setAmbient(false)
    }

    // Not visible (screen off / backgrounded / retreated to watch face) -> suspend the
    // poll loops so Bubbles never talks to the bridge while it isn't on screen.
    override fun onStop() {
        super.onStop()
        AmbientState.setAmbient(true)
    }
}
