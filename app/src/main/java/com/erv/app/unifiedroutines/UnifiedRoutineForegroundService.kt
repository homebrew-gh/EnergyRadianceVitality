package com.erv.app.unifiedroutines

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.erv.app.MainActivity
import com.erv.app.R

class UnifiedRoutineForegroundService : Service() {

    @Volatile
    private var routineId: String = ""

    @Volatile
    private var routineName: String = ""

    @Volatile
    private var startedAtEpochSeconds: Long = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        routineId = intent.getStringExtra(EXTRA_ROUTINE_ID).orEmpty()
        routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME).orEmpty()
        startedAtEpochSeconds = intent.getLongExtra(EXTRA_STARTED_AT_EPOCH_SEC, nowUnifiedRoutineEpochSeconds())
        postForeground()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun postForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                UnifiedLiveWorkoutConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(UnifiedLiveWorkoutConstants.NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val startedAtMs = startedAtEpochSeconds * 1000L
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(
            this,
            4,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(UnifiedLiveWorkoutConstants.EXTRA_OPEN_UNIFIED_LIVE_ROUTINE_ID, routineId)
            },
            immutable
        )
        return NotificationCompat.Builder(this, UnifiedLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(routineName.ifBlank { getString(R.string.unified_live_notification_title) })
            .setContentText(getString(R.string.unified_live_notification_chronometer_line))
            .setWhen(startedAtMs)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.unified_live_notification_action_open), openApp)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            UnifiedLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.unified_live_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.unified_live_notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        private const val ACTION_START = "com.erv.app.unified.LIVE_START"
        private const val EXTRA_ROUTINE_ID = "routineId"
        private const val EXTRA_ROUTINE_NAME = "routineName"
        private const val EXTRA_STARTED_AT_EPOCH_SEC = "startedAtEpochSec"

        fun start(
            context: Context,
            routineId: String,
            routineName: String,
            startedAtEpochSeconds: Long,
        ) {
            val app = context.applicationContext
            ContextCompat.startForegroundService(
                app,
                Intent(app, UnifiedRoutineForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ROUTINE_ID, routineId)
                    putExtra(EXTRA_ROUTINE_NAME, routineName)
                    putExtra(EXTRA_STARTED_AT_EPOCH_SEC, startedAtEpochSeconds)
                }
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, UnifiedRoutineForegroundService::class.java)
            )
        }
    }
}
