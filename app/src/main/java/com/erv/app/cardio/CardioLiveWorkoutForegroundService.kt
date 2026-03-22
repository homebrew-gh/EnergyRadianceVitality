package com.erv.app.cardio

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
import androidx.core.graphics.drawable.IconCompat
import com.erv.app.MainActivity
import com.erv.app.R
import com.erv.app.data.UserPreferences
import com.erv.app.notification.bubbleMetadataIconCompat
import com.erv.app.ui.cardio.CardioWorkoutBubbleActivity
import com.erv.app.cardio.nowEpochSeconds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service during an active cardio timer (session clock). Optional bubble (API 30+)
 * when [UserPreferences.workoutBubbleEnabled] is true — same preference as weight training.
 * Bubble metadata uses a [PendingIntent] and [R.drawable.ic_stat_erv] (sun icon), not shortcuts.
 */
class CardioLiveWorkoutForegroundService : Service() {

    private lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Async notification update failed", e)
        }
    )

    @Volatile
    private var startedAtEpochSeconds: Long = 0L

    @Volatile
    private var bubbleEnabled: Boolean = false

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
        startedAtEpochSeconds = intent.getLongExtra(EXTRA_STARTED_AT_EPOCH_SEC, nowEpochSeconds())
        postForeground(buildNotification())
        serviceScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                userPreferences.workoutBubbleEnabled.first()
            }
            if (enabled != bubbleEnabled) {
                bubbleEnabled = enabled
                postForeground(buildNotification())
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun postForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                CardioLiveWorkoutConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(CardioLiveWorkoutConstants.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val startedAtMs = startedAtEpochSeconds * 1000L
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(CardioLiveWorkoutConstants.EXTRA_OPEN_CARDIO_LIVE, true)
            },
            immutable
        )
        val builder = NotificationCompat.Builder(this, CardioLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(getString(R.string.cardio_live_notification_title))
            .setContentText(getString(R.string.cardio_live_notification_chronometer_line))
            .setWhen(startedAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.cardio_live_notification_action_open), openApp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bubbleEnabled) {
            try {
                val bubbleLaunch = bubbleActivityIntent(this, startedAtEpochSeconds).apply {
                    action = Intent.ACTION_VIEW
                }
                val bubblePi = PendingIntent.getActivity(
                    this,
                    BUBBLE_PENDING_INTENT_REQUEST,
                    bubbleLaunch,
                    immutable
                )
                val bubbleIcon = bubbleMetadataIconCompat(this)
                val bubbleMeta = NotificationCompat.BubbleMetadata.Builder(bubblePi, bubbleIcon)
                    .setDesiredHeight(220)
                    .build()
                builder.setBubbleMetadata(bubbleMeta)
            } catch (t: Throwable) {
                Log.w(TAG, "Omitting cardio bubble metadata", t)
            }
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CardioLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.cardio_live_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.cardio_live_notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(true)
            }
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        private const val TAG = "CardioLiveFg"
        private const val BUBBLE_PENDING_INTENT_REQUEST = 3
        private const val ACTION_START = "com.erv.app.cardio.LIVE_TIMER_START"
        private const val EXTRA_STARTED_AT_EPOCH_SEC = "startedAtEpochSec"

        fun start(context: Context, startedAtEpochSeconds: Long) {
            val app = context.applicationContext
            ContextCompat.startForegroundService(
                app,
                Intent(app, CardioLiveWorkoutForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STARTED_AT_EPOCH_SEC, startedAtEpochSeconds)
                }
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, CardioLiveWorkoutForegroundService::class.java)
            )
        }

        fun bubbleActivityIntent(context: Context, startedAtEpochSeconds: Long): Intent =
            Intent(context, CardioWorkoutBubbleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                putExtra(CardioLiveWorkoutConstants.EXTRA_BUBBLE_SESSION_START, startedAtEpochSeconds)
            }
    }
}
