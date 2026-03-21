package com.erv.app.weighttraining

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.erv.app.MainActivity
import com.erv.app.R
import com.erv.app.data.UserPreferences
import com.erv.app.ui.weighttraining.WeightWorkoutBubbleActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

/**
 * Foreground service for an active live weight workout: ongoing notification (required on Android)
 * and optional bubble (API 30+) when [UserPreferences.workoutBubbleEnabled] is true.
 */
class WeightLiveWorkoutForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null
    private lateinit var userPreferences: UserPreferences

    @Volatile
    private var startedAtEpochSeconds: Long = 0L

    @Volatile
    private var bubbleEnabled: Boolean = true

    /** False if shortcut/bubble setup failed (e.g. invalid icon) — notification still works. */
    @Volatile
    private var bubbleUiReady: Boolean = false

    override fun onCreate() {
        super.onCreate()
        userPreferences = UserPreferences(applicationContext)
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        startedAtEpochSeconds = intent!!.getLongExtra(EXTRA_STARTED_AT_EPOCH_SEC, weightNowEpochSeconds())
        bubbleEnabled = runBlocking(Dispatchers.IO) {
            userPreferences.workoutBubbleEnabled.first()
        }
        bubbleUiReady = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bubbleEnabled) {
            bubbleUiReady = try {
                pushBubbleShortcut()
                true
            } catch (e: RuntimeException) {
                Log.w(TAG, "Workout bubble shortcut failed; continuing with notification only", e)
                false
            }
        }
        startTicker()
        postForeground(buildNotification())
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopEverything() {
        updateJob?.cancel()
        updateJob = null
        ShortcutManagerCompat.removeDynamicShortcuts(
            this,
            listOf(WeightLiveWorkoutConstants.BUBBLE_SHORTCUT_ID)
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startTicker() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(WeightLiveWorkoutConstants.NOTIFICATION_ID, buildNotification())
                delay(TICK_MS)
            }
        }
    }

    private fun postForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                WeightLiveWorkoutConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(WeightLiveWorkoutConstants.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val elapsed = (System.currentTimeMillis() / 1000L - startedAtEpochSeconds).coerceAtLeast(0L)
        val elapsedLabel = formatElapsedForNotif(elapsed)

        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WeightLiveWorkoutConstants.EXTRA_OPEN_WEIGHT_LIVE, true)
            },
            immutable
        )

        val builder = NotificationCompat.Builder(this, WeightLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID)
            // Use foreground vector — @drawable/ic_launcher is adaptive on API 26+ and breaks shortcuts/bubbles.
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.weight_live_notification_title))
            .setContentText(getString(R.string.weight_live_notification_text, elapsedLabel))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.weight_live_notification_action_open), openApp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bubbleEnabled && bubbleUiReady) {
            try {
                val bubbleMeta = NotificationCompat.BubbleMetadata.Builder(
                    WeightLiveWorkoutConstants.BUBBLE_SHORTCUT_ID
                )
                    .setDesiredHeight(220)
                    .build()
                builder.setBubbleMetadata(bubbleMeta)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Omitting bubble metadata", e)
            }
        }

        return builder.build()
    }

    /**
     * Publishes the dynamic shortcut backing the bubble. Must not use @drawable/ic_launcher on API 26+
     * (adaptive icon); the system rejects it for shortcuts and can crash.
     */
    private fun pushBubbleShortcut() {
        val bubbleLaunch = bubbleActivityIntent(this, startedAtEpochSeconds).apply {
            action = Intent.ACTION_VIEW
        }
        val shortcut = ShortcutInfoCompat.Builder(this, WeightLiveWorkoutConstants.BUBBLE_SHORTCUT_ID)
            .setShortLabel(getString(R.string.weight_live_bubble_shortcut_short))
            .setLongLabel(getString(R.string.weight_live_bubble_shortcut_long))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_launcher_foreground))
            .setIntent(bubbleLaunch)
            .setLongLived(true)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            WeightLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.weight_live_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.weight_live_notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(true)
            }
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        private const val TAG = "WeightLiveFg"
        private const val ACTION_START = "com.erv.app.weight.LIVE_START"
        private const val EXTRA_STARTED_AT_EPOCH_SEC = "startedAtEpochSec"
        private const val TICK_MS = 1_000L

        fun start(context: Context, startedAtEpochSeconds: Long) {
            val app = context.applicationContext
            ContextCompat.startForegroundService(
                app,
                Intent(app, WeightLiveWorkoutForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STARTED_AT_EPOCH_SEC, startedAtEpochSeconds)
                }
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, WeightLiveWorkoutForegroundService::class.java)
            )
        }

        fun bubbleActivityIntent(context: Context, startedAtEpochSeconds: Long): Intent =
            Intent(context, WeightWorkoutBubbleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                putExtra(WeightLiveWorkoutConstants.EXTRA_BUBBLE_SESSION_START, startedAtEpochSeconds)
            }
    }
}

private fun formatElapsedForNotif(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
