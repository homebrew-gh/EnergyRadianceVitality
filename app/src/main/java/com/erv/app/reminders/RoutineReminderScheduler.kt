package com.erv.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.erv.app.MainActivity
import com.erv.app.R
import com.erv.app.supplements.SupplementWeekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object RoutineReminderScheduler {

    const val ACTION_ROUTINE_REMINDER = "com.erv.app.reminders.ACTION_ROUTINE_REMINDER"
    const val EXTRA_ROUTINE_ID = "extra_routine_id"
    const val EXTRA_ROUTINE_NAME = "extra_routine_name"
    const val EXTRA_FROM_REMINDER = "extra_from_reminder"
    private const val CHANNEL_ID = "routine_reminders"
    private const val CHANNEL_NAME = "Routine reminders"
    private const val CHANNEL_DESCRIPTION = "Notifications for supplement routines"

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, reminder: RoutineReminder): Boolean {
        cancel(context, reminder.routineId)
        if (!reminder.enabled) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms(context)) return false

        val triggerAtMillis = nextTriggerMillis(reminder) ?: return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = reminderAlarmPendingIntent(context, reminder)
        return try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm not permitted; open Settings → Alarms & reminders", e)
            false
        }
    }

    fun cancel(context: Context, routineId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(reminderAlarmPendingIntent(context, routineId, null))
    }

    fun showNotification(context: Context, reminder: RoutineReminder) {
        val appContext = context.applicationContext
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            Log.w(TAG, "Skipping reminder notification: notifications disabled for app")
            return
        }
        ensureChannel(appContext)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) {
                Log.w(TAG, "Skipping reminder notification: channel blocked")
                return
            }
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_routine)
            .setContentTitle(reminder.routineName)
            .setContentText("Time for your routine.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Time for your routine. Tap to open and log it."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(reminderTapPendingIntent(appContext, reminder))
            .build()
        try {
            notificationManager.notify(notificationId(reminder.routineId), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "notify() blocked (notification permission?)", e)
        }
    }

    /** Opens system UI where the user can enable notifications for this app (and the routine channel on O+). */
    fun openAppNotificationSettings(context: Context) {
        val app = context.applicationContext
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun reminderTapPendingIntent(context: Context, reminder: RoutineReminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ROUTINE_REMINDER
            putExtra(EXTRA_ROUTINE_ID, reminder.routineId)
            putExtra(EXTRA_ROUTINE_NAME, reminder.routineName)
            putExtra(EXTRA_FROM_REMINDER, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId(reminder.routineId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reminderAlarmPendingIntent(context: Context, reminder: RoutineReminder): PendingIntent =
        reminderAlarmPendingIntent(context, reminder.routineId, reminder.routineName)

    private fun reminderAlarmPendingIntent(
        context: Context,
        routineId: String,
        routineName: String?
    ): PendingIntent {
        val intent = Intent(context, RoutineReminderReceiver::class.java).apply {
            action = ACTION_ROUTINE_REMINDER
            putExtra(EXTRA_ROUTINE_ID, routineId)
            routineName?.let { putExtra(EXTRA_ROUTINE_NAME, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(routineId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun nextTriggerMillis(reminder: RoutineReminder, nowMillis: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        val reminderTime = LocalTime.of(reminder.hour.coerceIn(0, 23), reminder.minute.coerceIn(0, 59))

        val candidateForDate: (LocalDate) -> Long = { date ->
            date.atTime(reminderTime).atZone(zone).toInstant().toEpochMilli()
        }

        return when (reminder.frequency) {
            RoutineReminderFrequency.ONCE,
            RoutineReminderFrequency.DAILY -> {
                val todayCandidate = candidateForDate(now.toLocalDate())
                if (todayCandidate > nowMillis) todayCandidate else candidateForDate(now.toLocalDate().plusDays(1))
            }

            RoutineReminderFrequency.WEEKLY,
            RoutineReminderFrequency.CUSTOM_DAYS -> {
                val selectedDays = if (reminder.repeatDays.isEmpty()) {
                    SupplementWeekday.entries
                } else {
                    reminder.repeatDays
                }
                for (offset in 0..13) {
                    val date = now.toLocalDate().plusDays(offset.toLong())
                    val weekday = date.dayOfWeek.toSupplementWeekday()
                    if (weekday in selectedDays) {
                        val candidate = candidateForDate(date)
                        if (candidate > nowMillis) return candidate
                    }
                }
                candidateForDate(now.toLocalDate().plusWeeks(1))
            }
        }
    }

    private fun DayOfWeek.toSupplementWeekday(): SupplementWeekday = when (this) {
        DayOfWeek.MONDAY -> SupplementWeekday.MONDAY
        DayOfWeek.TUESDAY -> SupplementWeekday.TUESDAY
        DayOfWeek.WEDNESDAY -> SupplementWeekday.WEDNESDAY
        DayOfWeek.THURSDAY -> SupplementWeekday.THURSDAY
        DayOfWeek.FRIDAY -> SupplementWeekday.FRIDAY
        DayOfWeek.SATURDAY -> SupplementWeekday.SATURDAY
        DayOfWeek.SUNDAY -> SupplementWeekday.SUNDAY
    }

    private fun notificationId(routineId: String): Int = routineId.hashCode()
}
