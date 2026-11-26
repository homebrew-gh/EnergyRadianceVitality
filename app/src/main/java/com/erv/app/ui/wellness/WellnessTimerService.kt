package com.erv.app.ui.wellness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.erv.app.MainActivity
import com.erv.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Keeps a sauna, cold plunge or light therapy timer alive with the screen off: a foreground service
 * with a partial wake lock and a quiet lock-screen notification showing the countdown, with
 * Pause / Resume and End and Save. It draws [WellnessTimerController.state] and stops when the
 * timer ends.
 */
class WellnessTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "erv:wellnessTimer")?.apply {
            setReferenceCounted(false)
            // A ceiling in case something goes wrong; sessions are well under this.
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> WellnessTimerController.togglePause()
            ACTION_END_AND_SAVE -> WellnessTimerController.endAndSave()
        }
        val state = WellnessTimerController.state.value
        if (state == null) {
            // Started then ended before this ran: still go foreground once, as the system requires.
            if (!started) post(placeholderNotification())
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            post(buildNotification(state))
            scope.launch {
                WellnessTimerController.state
                    // Re-post only when what the notification shows changes, not every second.
                    .distinctUntilChangedBy { s -> s?.let { listOf(it.kind, it.title, it.paused, it.endAtMs) } }
                    .collect { s ->
                        if (s == null) stopSelf() else post(buildNotification(s))
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun post(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Wellness timer notification not shown", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(s: WellnessTimerState): Notification {
        val open = PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_WELLNESS_TIMER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = buildString {
            if (s.paused) {
                append("Paused, ").append(formatTimerClock(s.remainingSeconds)).append(" left")
            } else {
                append("Time left")
            }
            if (s.detail.isNotBlank()) append(" · ").append(s.detail)
        }
        val counting = !s.paused
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(s.title)
            .setContentText(text)
            .setShowWhen(counting)
            .setUsesChronometer(counting)
            .setWhen(if (counting) s.endAtMs else System.currentTimeMillis())
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (s.paused) "Resume" else "Pause", serviceAction(ACTION_TOGGLE_PAUSE, PAUSE_REQUEST_CODE))
            .addAction(0, "End and Save", serviceAction(ACTION_END_AND_SAVE, END_REQUEST_CODE))
            .build()
    }

    private fun placeholderNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle("Wellness timer")
            .setSilent(true)
            .build()

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, WellnessTimerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Wellness timers", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Sauna, cold plunge and light therapy timers, with Pause and End and Save."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WellnessTimerFg"
        private const val ACTION_START = "com.erv.app.wellness.TIMER_START"
        private const val ACTION_TOGGLE_PAUSE = "com.erv.app.wellness.TIMER_TOGGLE_PAUSE"
        private const val ACTION_END_AND_SAVE = "com.erv.app.wellness.TIMER_END_AND_SAVE"
        private const val OPEN_REQUEST_CODE = 30101
        private const val PAUSE_REQUEST_CODE = 30102
        private const val END_REQUEST_CODE = 30103
        const val CHANNEL_ID = "erv_wellness_timer_v1"
        const val NOTIFICATION_ID = 30100

        /** MainActivity extra: open the section whose timer is running. */
        const val EXTRA_OPEN_WELLNESS_TIMER = "erv.extra.OPEN_WELLNESS_TIMER"

        /** Call while the app is in the foreground (the person just tapped Start). */
        fun start(context: Context) {
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, WellnessTimerService::class.java).apply { action = ACTION_START },
                )
            } catch (e: RuntimeException) {
                // Without the service the timer still runs while ERV is on screen.
                Log.w(TAG, "Wellness timer service not started", e)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, WellnessTimerService::class.java))
        }
    }
}

/** "m:ss", or "h:mm:ss" from an hour. */
fun formatTimerClock(totalSeconds: Int): String {
    val t = totalSeconds.coerceAtLeast(0)
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
