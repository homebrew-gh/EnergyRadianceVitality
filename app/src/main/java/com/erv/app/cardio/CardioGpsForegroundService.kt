package com.erv.app.cardio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.erv.app.MainActivity
import com.erv.app.R

private const val CHANNEL_ID = "erv_cardio_gps"
private const val NOTIFICATION_ID = 7102
private const val MIN_TIME_MS = 2_000L
private const val MIN_DISTANCE_M = 5f
private const val ACTION_START = "com.erv.app.cardio.GPS_START"
private const val EXTRA_TITLE = "title"

/**
 * Foreground service that records GPS fixes into [CardioGpsRecordingHub] during an outdoor cardio timer.
 * Uses [LocationManager] only (no Play Services).
 */
class CardioGpsForegroundService : Service() {

    private var locationManager: LocationManager? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            appendIfMeaningful(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { getString(R.string.cardio_gps_notification_title) }
        CardioGpsRecordingHub.clear()
        postForeground(buildNotification(title))
        registerUpdates()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            locationManager?.removeUpdates(listener)
        } catch (_: Exception) {
        }
        locationManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun appendIfMeaningful(location: Location) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return
        val acc = location.accuracy
        if (acc > 0f && acc > 65f) return
        val epochSec = location.time / 1000L
        val last = CardioGpsRecordingHub.lastOrNull()
        if (last != null) {
            val dt = epochSec - last.epochSeconds
            val d = CardioGpsMath.haversineMeters(last.lat, last.lon, location.latitude, location.longitude)
            if (dt < 2L && d < 4.0) return
            if (d < 2.0 && dt < 8L) return
        }
        // Many devices report vertical accuracy > 30 m even when altitude is useful; only drop very poor fixes.
        val altM: Double? = if (location.hasAltitude()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasVerticalAccuracy() &&
                location.verticalAccuracyMeters > 100f
            ) {
                null
            } else {
                location.altitude
            }
        } else {
            null
        }
        CardioGpsRecordingHub.append(
            CardioGpsPoint(
                lat = location.latitude,
                lon = location.longitude,
                epochSeconds = epochSec,
                altitudeMeters = altM
            )
        )
    }

    private fun registerUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = lm
        val mainLooper = Looper.getMainLooper()
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    listener,
                    mainLooper
                )
            }
        } catch (_: SecurityException) {
        }
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    listener,
                    mainLooper
                )
            }
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.cardio_gps_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.cardio_gps_notification_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(title: String): android.app.Notification {
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            immutable
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(getString(R.string.cardio_gps_notification_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun postForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        fun start(context: Context, sessionTitle: String) {
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, CardioGpsForegroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_TITLE, sessionTitle)
                }
            )
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(
                Intent(context.applicationContext, CardioGpsForegroundService::class.java)
            )
        }
    }
}
