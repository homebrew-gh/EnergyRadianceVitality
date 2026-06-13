package com.erv.app.fasting

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

class FastingForegroundService : Service() {
    private var startedAtEpochSeconds: Long = 0L
    private var targetEndEpochSeconds: Long = 0L

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
        startedAtEpochSeconds = intent.getLongExtra(EXTRA_STARTED_AT_EPOCH_SEC, fastingNowEpochSeconds())
        targetEndEpochSeconds = intent.getLongExtra(EXTRA_TARGET_END_EPOCH_SEC, startedAtEpochSeconds)
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
                FastingConstants.ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FastingConstants.ONGOING_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(FastingConstants.EXTRA_OPEN_FASTING, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, FastingConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(getString(R.string.fasting_notification_title))
            .setContentText(getString(R.string.fasting_notification_text, formatFastingDateTime(targetEndEpochSeconds)))
            .setWhen(startedAtEpochSeconds * 1000L)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.fasting_notification_action_open), openApp)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        if (notificationManager.getNotificationChannel(FastingConstants.NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FastingConstants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.fasting_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.fasting_notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.erv.app.fasting.ACTION_FOREGROUND_START"
        private const val EXTRA_STARTED_AT_EPOCH_SEC = "startedAtEpochSec"
        private const val EXTRA_TARGET_END_EPOCH_SEC = "targetEndEpochSec"

        fun start(context: Context, session: FastingSession) {
            val app = context.applicationContext
            ContextCompat.startForegroundService(
                app,
                Intent(app, FastingForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STARTED_AT_EPOCH_SEC, session.startedAtEpochSeconds)
                    putExtra(EXTRA_TARGET_END_EPOCH_SEC, session.targetEndEpochSeconds)
                },
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, FastingForegroundService::class.java),
            )
        }
    }
}
