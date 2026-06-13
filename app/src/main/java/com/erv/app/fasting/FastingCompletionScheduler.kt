package com.erv.app.fasting

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.erv.app.MainActivity
import com.erv.app.R

object FastingCompletionScheduler {
    private const val TAG = "FastingCompletion"
    const val ACTION_FASTING_COMPLETE = "com.erv.app.fasting.ACTION_FASTING_COMPLETE"
    const val EXTRA_SESSION_ID = "extra_session_id"

    fun schedule(context: Context, session: FastingSession): Boolean {
        cancel(context, session.id)
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                session.targetEndEpochSeconds * 1000L,
                completionPendingIntent(app, session.id),
            )
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact fasting completion alarm not permitted", e)
            false
        }
    }

    fun cancel(context: Context, sessionId: String?) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(completionPendingIntent(app, sessionId ?: DEFAULT_REQUEST_ID))
    }

    suspend fun restoreActiveSchedule(context: Context) {
        FastingRepository(context).currentState().activeSession?.let { session ->
            if (session.targetEndEpochSeconds > fastingNowEpochSeconds()) {
                schedule(context, session)
                FastingForegroundService.start(context, session)
            } else {
                showCompletionNotification(context, session)
            }
        }
    }

    fun showCompletionNotification(context: Context, session: FastingSession) {
        val app = context.applicationContext
        if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            Log.w(TAG, "Skipping fasting completion notification: notifications disabled")
            return
        }
        ensureCompletionChannel(app)
        val notification = NotificationCompat.Builder(app, FastingConstants.COMPLETION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(app.getString(R.string.fasting_complete_notification_title))
            .setContentText(app.getString(R.string.fasting_complete_notification_text, session.targetDays))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    app.getString(R.string.fasting_complete_notification_big_text),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openFastingPendingIntent(app))
            .build()
        try {
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(FastingConstants.COMPLETION_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Fasting completion notify() blocked", e)
        }
    }

    private fun completionPendingIntent(context: Context, sessionId: String): PendingIntent {
        val intent = Intent(context, FastingCompletionReceiver::class.java).apply {
            action = ACTION_FASTING_COMPLETE
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openFastingPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(FastingConstants.EXTRA_OPEN_FASTING, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureCompletionChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(FastingConstants.COMPLETION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FastingConstants.COMPLETION_CHANNEL_ID,
            context.getString(R.string.fasting_complete_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.fasting_complete_notification_channel_desc)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private const val DEFAULT_REQUEST_ID = "active_fasting_session"
}
