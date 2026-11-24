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
import com.erv.app.MainActivity
import com.erv.app.R
import com.erv.app.data.UserPreferences
import com.erv.app.notification.bubbleMetadataIconCompat
import com.erv.app.ui.weighttraining.WeightWorkoutBubbleActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service for an active live weight workout: ongoing notification (required on Android)
 * and optional bubble (API 30–34) when [UserPreferences.workoutBubbleEnabled] is true.
 *
 * On API 35+ (incl. Android 16), bubble metadata on FGS notifications is rejected asynchronously
 * ([CannotPostForegroundServiceNotificationException]); we omit bubbles there. Ongoing notification remains.
 *
 * Elapsed time uses a notification [chronometer] ([setUsesChronometer]) so we do not call
 * [NotificationManager.notify] every second — frequent updates prevent bubble promotion on many devices.
 *
 * Bubble metadata uses [NotificationCompat.BubbleMetadata.Builder] with a [PendingIntent] and
 * [R.drawable.ic_stat_erv] (sun icon), not shortcut-only metadata, so a failed dynamic shortcut
 * cannot block the bubble.
 */
class WeightLiveWorkoutForegroundService : Service() {

    private lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Async notification update failed", e)
        }
    )

    @Volatile
    private var startedAtEpochSeconds: Long = 0L

    /** Session name for the notification title (workout or routine); null uses the default title. */
    @Volatile
    private var title: String? = null

    /** End of the rest countdown being shown, or null when not resting. */
    @Volatile
    private var restEndsAtEpochSeconds: Long? = null

    @Volatile
    private var restLabel: String? = null

    /** Updated async from DataStore; false until loaded so we do not attach bubble metadata prematurely. */
    @Volatile
    private var bubbleEnabled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        userPreferences = UserPreferences(applicationContext)
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == ACTION_REST) {
            if (!isRunning) {
                // The session ended before this update arrived; don't put a notification back up.
                stopSelf()
                return START_NOT_STICKY
            }
            restEndsAtEpochSeconds = intent.getLongExtra(EXTRA_REST_ENDS_AT_EPOCH_SEC, 0L)
                .takeIf { it > weightNowEpochSeconds() }
            restLabel = intent.getStringExtra(EXTRA_REST_LABEL)
            postForeground()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        isRunning = true
        startedAtEpochSeconds = intent!!.getLongExtra(EXTRA_STARTED_AT_EPOCH_SEC, weightNowEpochSeconds())
        title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
        postForeground()
        serviceScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                userPreferences.workoutBubbleEnabled.first()
            }
            if (enabled != bubbleEnabled) {
                bubbleEnabled = enabled
                postForeground()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * API 35+ validates FGS notifications on the main thread and throws
     * [android.app.RemoteServiceException] for bubble metadata we cannot catch around [startForeground].
     */
    private fun bubbleMetadataAllowedOnThisApi(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Build.VERSION.SDK_INT < 35

    private fun postForeground() {
        val notification = buildNotification()
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
        val startedAtMs = startedAtEpochSeconds * 1000L

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

        val restEnd = restEndsAtEpochSeconds?.takeIf { it > weightNowEpochSeconds() }
        val sessionTitle = title ?: getString(R.string.weight_live_notification_title)
        val builder = NotificationCompat.Builder(this, WeightLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_erv)
            .setShowWhen(true)
            .setUsesChronometer(true)
        if (restEnd != null) {
            // The chronometer counts down to the end of rest, so the shade and lock screen show
            // the time left without the app posting an update every second.
            builder
                .setContentTitle(getString(R.string.weight_live_notification_rest_title))
                .setContentText(restLabel?.takeIf { it.isNotBlank() } ?: sessionTitle)
                .setWhen(restEnd * 1000L)
                .setChronometerCountDown(true)
        } else {
            builder
                .setContentTitle(sessionTitle)
                .setContentText(getString(R.string.weight_live_notification_chronometer_line))
                .setWhen(startedAtMs)
        }
        builder
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.weight_live_notification_action_open), openApp)

        if (bubbleMetadataAllowedOnThisApi() && bubbleEnabled) {
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
                if (bubbleIcon != null) {
                    val bubbleMeta = NotificationCompat.BubbleMetadata.Builder(bubblePi, bubbleIcon)
                        .setDesiredHeight(220)
                        .build()
                    builder.setBubbleMetadata(bubbleMeta)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Omitting bubble metadata", t)
            }
        }

        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // Old channel ID was created before bubble flags; channels are immutable — remove stale channel.
        nm.deleteNotificationChannel(LEGACY_NOTIFICATION_CHANNEL_ID)
        val ch = NotificationChannel(
            WeightLiveWorkoutConstants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.weight_live_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.weight_live_notification_channel_desc)
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
        private const val TAG = "WeightLiveFg"
        private const val BUBBLE_PENDING_INTENT_REQUEST = 2
        private const val ACTION_START = "com.erv.app.weight.LIVE_START"
        private const val EXTRA_STARTED_AT_EPOCH_SEC = "startedAtEpochSec"
        private const val EXTRA_TITLE = "title"
        private const val ACTION_REST = "com.erv.app.weight.LIVE_REST"
        private const val EXTRA_REST_ENDS_AT_EPOCH_SEC = "restEndsAtEpochSec"
        private const val EXTRA_REST_LABEL = "restLabel"

        /** True while the service is in the foreground; rest updates go only to a running service. */
        @Volatile
        var isRunning: Boolean = false
            private set
        /** Pre–bubble-v2 channel; deleting lets users get a single channel with bubble toggles. */
        private const val LEGACY_NOTIFICATION_CHANNEL_ID = "erv_weight_live_workout"

        fun start(context: Context, startedAtEpochSeconds: Long, title: String? = null) {
            val app = context.applicationContext
            ContextCompat.startForegroundService(
                app,
                Intent(app, WeightLiveWorkoutForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STARTED_AT_EPOCH_SEC, startedAtEpochSeconds)
                    title?.let { putExtra(EXTRA_TITLE, it) }
                }
            )
        }

        /**
         * Show a rest countdown ending at [endsAtEpochSeconds] in the ongoing notification, or go
         * back to the session clock when it is null. Does nothing when the service isn't running
         * (no notification is up), so it never starts one.
         */
        fun updateRest(context: Context, endsAtEpochSeconds: Long?, label: String?) {
            if (!isRunning) return
            val app = context.applicationContext
            try {
                app.startService(
                    Intent(app, WeightLiveWorkoutForegroundService::class.java).apply {
                        action = ACTION_REST
                        putExtra(EXTRA_REST_ENDS_AT_EPOCH_SEC, endsAtEpochSeconds ?: 0L)
                        label?.let { putExtra(EXTRA_REST_LABEL, it) }
                    }
                )
            } catch (e: IllegalStateException) {
                // Background start limits: the notification keeps the session clock instead.
                Log.w(TAG, "Rest notification update skipped", e)
            }
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
