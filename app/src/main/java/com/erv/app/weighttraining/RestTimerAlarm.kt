package com.erv.app.weighttraining

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.erv.app.MainActivity
import com.erv.app.R

/**
 * Wakes the phone when a rest countdown ends, so rest works with the screen locked or the app in
 * the background. The countdown itself is just an end time (kept in the live draft or the run
 * screen); this schedules an exact alarm for it.
 *
 * When the alarm fires with ERV in the foreground, the screen on view plays its own cue, so only
 * the ongoing notification is updated. In the background it posts a "Rest over" notification
 * with sound and vibration.
 */
object RestTimerAlarm {
    private const val TAG = "RestTimerAlarm"
    private const val ACTION_REST_OVER = "com.erv.app.rest.ACTION_REST_OVER"
    private const val EXTRA_TEXT = "text"
    private const val EXTRA_OPEN_WEIGHT_LIVE = "openWeightLive"
    private const val REQUEST_CODE = 30079
    const val CHANNEL_ID = "erv_rest_timer_v1"
    const val NOTIFICATION_ID = 30079

    private val vibrationPattern = longArrayOf(0, 350, 150, 350)

    /**
     * Schedule the end-of-rest alert for [endsAtEpochSeconds], replacing any earlier one.
     * [text] says what is next ("Next set: Bench Press"). [openWeightLive] reopens the live
     * lifting screen from the notification instead of the app's last screen.
     */
    fun schedule(context: Context, endsAtEpochSeconds: Long, text: String, openWeightLive: Boolean) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = alarmPendingIntent(app, text, openWeightLive)
        val triggerAtMs = endsAtEpochSeconds * 1000L
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            } else {
                // Without exact-alarm access the alert can arrive late, but it still arrives.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Rest alarm not permitted", e)
        }
    }

    /** Cancel a pending end-of-rest alert and remove a "Rest over" notification still showing. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(alarmPendingIntent(app, text = "", openWeightLive = false))
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }

    private fun alarmPendingIntent(context: Context, text: String, openWeightLive: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RestTimerAlarmReceiver::class.java).apply {
                action = ACTION_REST_OVER
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_OPEN_WEIGHT_LIVE, openWeightLive)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun onAlarm(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_OVER) return
        val app = context.applicationContext
        WeightLiveWorkoutForegroundService.updateRest(app, endsAtEpochSeconds = null, label = null)
        if (appInForeground()) return
        showRestOverNotification(
            app,
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            openWeightLive = intent.getBooleanExtra(EXTRA_OPEN_WEIGHT_LIVE, false),
        )
    }

    private fun appInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun showRestOverNotification(context: Context, text: String, openWeightLive: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (openWeightLive) putExtra(WeightLiveWorkoutConstants.EXTRA_OPEN_WEIGHT_LIVE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(context.getString(R.string.rest_timer_over_title))
            .setContentText(text.ifBlank { null })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVibrate(vibrationPattern)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Rest over notification not permitted", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.rest_timer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.rest_timer_channel_desc)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = this@RestTimerAlarm.vibrationPattern
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    /** A short double buzz for the end of a rest while ERV is on screen. */
    fun vibrateRestOver(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationPattern, -1)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Vibration not permitted", e)
        }
    }
}

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimerAlarm.onAlarm(context, intent)
    }
}

/** Seconds left in a rest ending at [endsAtEpochSeconds], or null when none is running. */
fun restSecondsRemaining(endsAtEpochSeconds: Long?, nowEpochSeconds: Long): Int? {
    val end = endsAtEpochSeconds ?: return null
    val left = end - nowEpochSeconds
    return if (left <= 0) null else left.toInt()
}

/**
 * Whether the screen should play its own end cue for a rest that ended at [endsAtEpochSeconds].
 * A rest that ended a while ago (the app was in the background and the alarm already alerted)
 * stays quiet when the screen comes back.
 */
fun restEndCueIsFresh(endsAtEpochSeconds: Long, nowEpochSeconds: Long): Boolean =
    nowEpochSeconds - endsAtEpochSeconds <= REST_END_CUE_GRACE_SECONDS

const val REST_END_CUE_GRACE_SECONDS = 3L
