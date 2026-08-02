package com.darney.bubblewatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * A persistent, silent "🫧 Bubbles" entry pinned in the Wear notification stream so a
 * tap always drops straight back into the app — useful now that the pendant battery
 * fix lets the screen sleep instead of staying pinned on.
 *
 * Deliberately a PLAIN ongoing notification: no foreground service, no polling, so it
 * adds zero battery cost (last session's priority; the heavier foreground-service
 * path was declined). Trade-off of "always": the system can clear it and it will not
 * repost after a reboot until the app is opened once. Silent + IMPORTANCE_LOW so it
 * never buzzes — it is a shortcut, not an alert.
 */
object PersistentBubble {
    private const val CHANNEL_ID = "bubbles_shortcut"
    private const val NOTIF_ID = 1001

    fun show(context: Context) {
        val nm = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Open Bubbles",
                NotificationManager.IMPORTANCE_LOW, // no sound / no vibration
            ).apply {
                description = "A quick shortcut back into the app"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        // Bring the existing task to the front rather than spawning a new one.
        val launch = Intent(context, BubbleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🫧 Bubbles")
            .setContentText("Tap to open")
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        // No-ops if POST_NOTIFICATIONS is not yet granted; the activity re-invokes
        // show() from the permission-result callback.
        nm.notify(NOTIF_ID, notif)
    }
}
