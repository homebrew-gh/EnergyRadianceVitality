package com.erv.app.ui.stretching

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
import com.erv.app.stretching.guidedEntryPositions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Keeps a guided stretch routine alive with the screen off: a foreground service with a partial
 * wake lock (so the timer, voice and sounds keep going when the phone sleeps) and a quiet ongoing
 * notification on the lock screen showing the stretch and a countdown, with Pause and Skip. It
 * draws whatever [GuidedStretchController.state] holds and stops when the session ends.
 */
class GuidedStretchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "erv:guidedStretch")?.apply {
            setReferenceCounted(false)
            // A ceiling in case something goes wrong; the longest routines are well under this.
            acquire(3 * 60 * 60 * 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PAUSE -> GuidedStretchController.togglePause()
            ACTION_SKIP -> GuidedStretchController.skip()
        }
        val state = GuidedStretchController.state.value
        if (state == null || state.outcome != null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            post(buildNotification(state))
            scope.launch {
                GuidedStretchController.state
                    // Only re-post when what the notification shows changes, not every second.
                    .distinctUntilChangedBy { s ->
                        s?.let { listOf(it.index, it.phaseHold, it.firstPrepActive, it.restActive, it.paused, it.secondsLeft < 0, it.outcome != null) }
                    }
                    .collect { s ->
                        if (s == null || s.outcome != null) {
                            stopSelf()
                        } else {
                            post(buildNotification(s))
                        }
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
            Log.w(TAG, "Guided stretch notification not shown", e)
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

    private fun buildNotification(s: GuidedStretchSessionState): Notification {
        val open = PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_GUIDED_STRETCH, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val step = s.shownStep
        val name = step?.let { st ->
            val base = titleCaseStretchLabel(st.entry.name)
            guidedSideLabel(st.side)?.let { "$base, ${it.lowercase()}" } ?: base
        }.orEmpty()
        val phase = when {
            s.restActive -> "Rest"
            s.phaseHold -> "Hold"
            s.firstPrepActive -> "Settle in"
            else -> "Next"
        }
        val text = buildString {
            if (s.paused) append("Paused · ")
            append(phase)
            if (name.isNotEmpty()) append(" · ").append(name)
        }
        val positions = guidedEntryPositions(s.steps)
        val total = (positions.lastOrNull() ?: -1) + 1
        val pos = positions.getOrNull(s.focusIndex.coerceIn(0, (s.steps.size - 1).coerceAtLeast(0))) ?: 0
        val counting = !s.paused && s.secondsLeft > 0
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(s.routineName.ifBlank { "Stretching" })
            .setContentText(text)
            .setSubText(if (total > 1) "${pos + 1} of $total" else null)
            .setShowWhen(counting)
            .setUsesChronometer(counting)
            .setWhen(System.currentTimeMillis() + s.secondsLeft.coerceAtLeast(0) * 1000L)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, if (s.paused) "Resume" else "Pause", serviceAction(ACTION_TOGGLE_PAUSE, PAUSE_REQUEST_CODE))
            .addAction(0, "Skip", serviceAction(ACTION_SKIP, SKIP_REQUEST_CODE))
        return builder.build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, GuidedStretchService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Guided stretching", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The routine playing in Stretching, with Pause and Skip."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "GuidedStretchFg"
        private const val ACTION_START = "com.erv.app.stretch.GUIDED_START"
        private const val ACTION_TOGGLE_PAUSE = "com.erv.app.stretch.GUIDED_TOGGLE_PAUSE"
        private const val ACTION_SKIP = "com.erv.app.stretch.GUIDED_SKIP"
        private const val OPEN_REQUEST_CODE = 30091
        private const val PAUSE_REQUEST_CODE = 30092
        private const val SKIP_REQUEST_CODE = 30093
        const val CHANNEL_ID = "erv_guided_stretch_v1"
        const val NOTIFICATION_ID = 30090

        /** MainActivity extra: open the Stretching screen showing the running routine. */
        const val EXTRA_OPEN_GUIDED_STRETCH = "erv.extra.OPEN_GUIDED_STRETCH"

        /** Call while the app is in the foreground (the person just tapped Play). */
        fun start(context: Context) {
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, GuidedStretchService::class.java).apply { action = ACTION_START },
                )
            } catch (e: RuntimeException) {
                // Without the service the routine still runs while ERV is on screen.
                Log.w(TAG, "Guided stretch service not started", e)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, GuidedStretchService::class.java))
        }
    }
}
