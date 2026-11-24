package com.erv.app.workouts

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
import com.erv.app.MainActivity
import com.erv.app.R

/**
 * One foreground service for the whole single-screen workout session, replacing the separate
 * weight and cardio services a run used to hop between. It only shows what it is given: the
 * session screen sends a [WorkoutSessionNotificationContent] whenever the step, the countdown or
 * the heart rate changes (see [WorkoutSessionNotifier]).
 *
 * The clock is a notification chronometer, counting down during a rest or timed step and up from
 * the workout start otherwise, so nothing posts an update every second.
 */
class WorkoutSessionForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    /** The last content shown, so a finished countdown can switch back to the workout clock. */
    private var lastContent: WorkoutSessionNotificationContent? = null
    private var lastWorkoutId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_COUNTDOWN_OVER) {
            val last = lastContent
            if (!isRunning || last == null) {
                stopSelf()
            } else if (last.countDown) {
                post(last.copy(countDown = false, chronometerEpochSeconds = last.workoutStartedAtEpochSeconds), lastWorkoutId)
            }
            return START_NOT_STICKY
        }
        if (intent.action != ACTION_SHOW) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        val workoutStart = intent.getLongExtra(EXTRA_WORKOUT_STARTED_AT_EPOCH_SEC, nowWorkoutEpochSeconds())
        val content = WorkoutSessionNotificationContent(
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.workout_session_notification_title) },
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            progress = intent.getStringExtra(EXTRA_PROGRESS),
            chronometerEpochSeconds = intent.getLongExtra(EXTRA_CHRONOMETER_EPOCH_SEC, workoutStart),
            countDown = intent.getBooleanExtra(EXTRA_COUNT_DOWN, false),
            complete = intent.getBooleanExtra(EXTRA_COMPLETE, false),
            workoutStartedAtEpochSeconds = workoutStart,
        )
        post(content, intent.getStringExtra(EXTRA_WORKOUT_ID))
        return START_NOT_STICKY
    }

    private fun post(content: WorkoutSessionNotificationContent, workoutId: String?) {
        lastContent = content
        lastWorkoutId = workoutId
        val notification = buildNotification(content, workoutId)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Session notification not shown", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(content: WorkoutSessionNotificationContent, workoutId: String?): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                workoutId?.let { putExtra(EXTRA_OPEN_WORKOUT_RUN_ID, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setContentTitle(content.title)
            .setContentText(content.text.ifBlank { null })
            .setSubText(content.progress)
            .setShowWhen(!content.complete)
            .setUsesChronometer(!content.complete)
            .setWhen(content.chronometerEpochSeconds * 1000L)
            .setChronometerCountDown(content.countDown)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.workout_session_notification_action_open), open)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.workout_session_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.workout_session_notification_channel_desc)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "WorkoutSessionFg"
        private const val ACTION_SHOW = "com.erv.app.workout.SESSION_SHOW"
        private const val ACTION_COUNTDOWN_OVER = "com.erv.app.workout.SESSION_COUNTDOWN_OVER"
        private const val EXTRA_WORKOUT_STARTED_AT_EPOCH_SEC = "workoutStartedAtEpochSec"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_CHRONOMETER_EPOCH_SEC = "chronometerEpochSec"
        private const val EXTRA_COUNT_DOWN = "countDown"
        private const val EXTRA_COMPLETE = "complete"
        private const val EXTRA_WORKOUT_ID = "workoutId"
        private const val OPEN_REQUEST_CODE = 30082
        const val CHANNEL_ID = "erv_workout_session_v1"
        const val NOTIFICATION_ID = 30081

        /** MainActivity extra: open this workout's live run screen. */
        const val EXTRA_OPEN_WORKOUT_RUN_ID = "erv.extra.OPEN_WORKOUT_RUN_ID"

        /** True while the notification is up. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** Shows [content], starting the service if needed. Call from the app while it is in the foreground. */
        fun show(context: Context, workoutId: String, content: WorkoutSessionNotificationContent) {
            val app = context.applicationContext
            val intent = Intent(app, WorkoutSessionForegroundService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_WORKOUT_ID, workoutId)
                putExtra(EXTRA_TITLE, content.title)
                putExtra(EXTRA_TEXT, content.text)
                content.progress?.let { putExtra(EXTRA_PROGRESS, it) }
                putExtra(EXTRA_CHRONOMETER_EPOCH_SEC, content.chronometerEpochSeconds)
                putExtra(EXTRA_COUNT_DOWN, content.countDown)
                putExtra(EXTRA_COMPLETE, content.complete)
                putExtra(EXTRA_WORKOUT_STARTED_AT_EPOCH_SEC, content.workoutStartedAtEpochSeconds)
            }
            try {
                if (isRunning) app.startService(intent) else ContextCompat.startForegroundService(app, intent)
            } catch (e: IllegalStateException) {
                // Background start limits: the last notification stays up until the app returns.
                Log.w(TAG, "Session notification update skipped", e)
            }
        }

        /**
         * A rest or timed step ran out with nobody on screen: show the workout clock again instead
         * of a countdown running past zero. Does nothing when the notification isn't up.
         */
        fun countdownOver(context: Context) {
            if (!isRunning) return
            val app = context.applicationContext
            try {
                app.startService(
                    Intent(app, WorkoutSessionForegroundService::class.java).apply { action = ACTION_COUNTDOWN_OVER },
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Countdown update skipped", e)
            }
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            app.stopService(Intent(app, WorkoutSessionForegroundService::class.java))
        }
    }
}

/**
 * Keeps the ongoing notification and the end-of-rest alarm in step with the saved run. The session
 * screen calls [sync] after every event and whenever the heart rate changes; [stop] when the run
 * ends or is discarded.
 */
object WorkoutSessionNotifier {

    fun sync(
        context: Context,
        run: WorkoutActiveRun,
        names: WorkoutSessionNames,
        loadUnit: com.erv.app.data.BodyWeightUnit,
        heartRateBpm: Int? = null,
        nowEpochSeconds: Long = nowWorkoutEpochSeconds(),
    ) {
        val content = run.sessionNotificationContent(names, loadUnit, nowEpochSeconds, heartRateBpm)
        if (content == null) {
            stop(context)
            return
        }
        WorkoutSessionForegroundService.show(context, run.workoutId, content)
        val plan = run.sessionPlan()
        val state = run.session
        val step = state?.let { WorkoutSessionEngine.currentStep(plan, it) }
        val restEnd = run.sessionCountdownEndsAtEpochSeconds(plan)?.takeIf { it > nowEpochSeconds }
        if (step is WorkoutSessionStep.Rest && restEnd != null) {
            // The alarm wakes the phone when rest ends with the screen off. Cardio legs and
            // stretch holds are watched on screen, so only rests get an alarm.
            com.erv.app.weighttraining.RestTimerAlarm.schedule(
                context,
                endsAtEpochSeconds = restEnd,
                text = run.sessionNotificationContent(names, loadUnit, nowEpochSeconds)?.text
                    ?.substringAfter("Next: ", missingDelimiterValue = "")
                    .orEmpty(),
                openWeightLive = false,
            )
        } else {
            com.erv.app.weighttraining.RestTimerAlarm.cancel(context)
        }
    }

    fun stop(context: Context) {
        com.erv.app.weighttraining.RestTimerAlarm.cancel(context)
        WorkoutSessionForegroundService.stop(context)
    }
}
