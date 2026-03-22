package com.erv.app.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
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
