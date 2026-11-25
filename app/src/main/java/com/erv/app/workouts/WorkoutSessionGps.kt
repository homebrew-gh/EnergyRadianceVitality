package com.erv.app.workouts

import android.content.Context
import android.util.Log
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioGpsForegroundService
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioGpsRecordingHub
import com.erv.app.cardio.supportsPhoneGpsTracking

/**
 * GPS for a cardio item run on the workout session screen. It uses the cardio screen's GPS service
 * and point buffer, keyed by the item instead of a timer start, so a route survives the app being
 * killed and comes back when the run is reopened.
 */
object WorkoutSessionGps {
    private const val TAG = "WorkoutSessionGps"

    /** A stable, non-negative key for one cardio item of one run. */
    fun keyFor(sessionId: String, segmentId: String, itemId: String): Long =
        "erv-session-gps|$sessionId|$segmentId|$itemId".hashCode().toLong() and 0x7fffffffL

    /** Outdoor activities the phone can track; the athlete can still turn it off per item. */
    fun supports(activity: CardioActivitySnapshot?): Boolean = activity?.supportsPhoneGpsTracking() == true

    fun start(context: Context, title: String, key: Long) {
        CardioGpsRecordingHub.paused = false
        try {
            CardioGpsForegroundService.start(context.applicationContext, title, key)
        } catch (e: Exception) {
            // FGS start can be refused (e.g. from the background on API 31+); the leg still runs.
            Log.w(TAG, "Could not start session GPS", e)
        }
    }

    /** Stops recording without dropping the points; [take] collects them when the item is logged. */
    fun stopIfRecording(context: Context, key: Long? = null) {
        val current = CardioGpsRecordingHub.sessionKey()
        if (current == Long.MIN_VALUE) return
        if (key != null && current != key) return
        CardioGpsForegroundService.stop(context.applicationContext)
    }

    /** Stops GPS for [key] (if it is the one recording) and returns its route. */
    fun take(context: Context, key: Long): List<CardioGpsPoint> {
        stopIfRecording(context, key)
        return CardioGpsRecordingHub.takeSession(context, key)
    }
}
