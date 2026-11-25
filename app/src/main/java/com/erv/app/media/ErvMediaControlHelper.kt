package com.erv.app.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi

object ErvMediaControlHelper {

    private fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, ErvMediaNotificationListenerService::class.java)

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = listenerComponent(context)
        return flat.split(":").mapNotNull { ComponentName.unflattenFromString(it) }.any { it == target }
    }

    @RequiresApi(21)
    fun activeMediaControllers(context: Context): List<MediaController> {
        if (!isNotificationListenerEnabled(context)) return emptyList()
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return emptyList()
        return try {
            msm.getActiveSessions(listenerComponent(context))
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** Prefer the first controller (typically the most recently active session). */
    @RequiresApi(21)
    fun primaryMediaController(context: Context): MediaController? =
        activeMediaControllers(context).firstOrNull()

    /** Jump size for the podcast-friendly skip buttons. */
    const val SEEK_STEP_MS = 30_000L

    /**
     * Seeks the other app's session by [deltaMs] (negative = back). Uses seekTo from the
     * estimated current position when the session reports a position; otherwise falls back to
     * fastForward / rewind, which most podcast apps map to their own skip-forward / skip-back.
     * Apps that support neither simply ignore the command.
     */
    @RequiresApi(21)
    fun seekBy(controller: MediaController, deltaMs: Long) {
        val tc = controller.transportControls
        val state = controller.playbackState
        val position = state?.let { estimatedPositionMs(it) }
        val actions = state?.actions ?: 0L
        val canSeek = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
        val fallbackAction = if (deltaMs >= 0) PlaybackState.ACTION_FAST_FORWARD else PlaybackState.ACTION_REWIND
        val canFallback = (actions and fallbackAction) != 0L
        if (position != null && (canSeek || !canFallback)) {
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            tc.seekTo(clampSeekTarget(position + deltaMs, duration))
        } else if (deltaMs >= 0) {
            tc.fastForward()
        } else {
            tc.rewind()
        }
    }

    /** Position now, extrapolated from the last update while playing; null if unknown. */
    @RequiresApi(21)
    private fun estimatedPositionMs(state: PlaybackState): Long? {
        val pos = state.position
        if (pos == PlaybackState.PLAYBACK_POSITION_UNKNOWN || pos < 0) return null
        if (state.state != PlaybackState.STATE_PLAYING) return pos
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return pos + (elapsed * state.playbackSpeed).toLong()
    }

    /** Keeps a seek target within [0, duration]; a duration of 0 or less means unknown. */
    internal fun clampSeekTarget(targetMs: Long, durationMs: Long): Long {
        val floor = targetMs.coerceAtLeast(0L)
        return if (durationMs > 0) floor.coerceAtMost(durationMs) else floor
    }

    fun musicStreamVolume(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun musicStreamMaxVolume(context: Context): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    fun setMusicStreamVolume(context: Context, index: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val clamped = index.coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    }

    /** App info screen (restricted settings live under the ⋮ menu on many devices). */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openNotificationListenerSettings(context: Context) {
        val cn = listenerComponent(context)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
