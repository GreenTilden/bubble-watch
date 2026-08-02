package com.darney.bubblewatch

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Re-posts the persistent "🫧 Bubbles" shortcut notification on a slow schedule so it
 * survives the two ways it used to vanish for good:
 *   1. Wear kills the backgrounded app process and the system later clears the ongoing
 *      notification. Nothing was re-posting it (only BubbleActivity.onCreate ever did),
 *      so it never came back until the app was manually relaunched from the launcher --
 *      the catch-22, since the notif is the main way back in after full-retreat.
 *   2. A reboot drops the notification. WorkManager persists periodic work and its own
 *      boot receiver reschedules it, so the bubble reappears after restart with no
 *      manual open.
 *
 * Battery-safe by design: no foreground service, no polling -- just an occasional
 * nm.notify() (~every 15 min, the WorkManager periodic floor) that is a no-op when the
 * notification is already showing. Keeps the "declined foreground service" trade-off
 * while closing the "disappears and will not come back" gap.
 */
class BubbleKeepAliveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        PersistentBubble.show(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "bubble_keepalive"

        /** Idempotent: safe to call on every app start (KEEP leaves an existing schedule). */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<BubbleKeepAliveWorker>(
                15, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
