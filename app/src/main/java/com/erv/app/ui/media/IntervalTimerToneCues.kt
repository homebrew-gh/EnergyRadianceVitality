package com.erv.app.ui.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Short tones on [AudioManager.STREAM_MUSIC] (follows media volume), matching the stretching timer
 * pattern: [ToneGenerator.release] is delayed so playback is not cut off.
 *
 * Volume is a percent of the music stream (100 = as loud as that stream allows). While a cue plays,
 * other apps are asked to duck so music on the same device drops for the beep.
 */
private const val CUE_VOLUME_PERCENT = 100

private val mainHandler = Handler(Looper.getMainLooper())
private val focusLock = Any()
private var audioFocusRequest: AudioFocusRequest? = null
private var abandonFocusRunnable: Runnable? = null

private fun playTone(
    context: Context,
    tone: Int,
    durationMs: Int,
    volumePercent: Int,
    duckHoldMs: Long,
) {
    duckOtherAudioForTimerCue(context, duckHoldMs)
    try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, volumePercent.coerceIn(1, 100))
        tg.startTone(tone, durationMs)
        mainHandler.postDelayed(
            {
                try {
                    tg.release()
                } catch (_: Exception) {
                }
            },
            durationMs.toLong() + 50L
        )
    } catch (_: Exception) {
    }
}

/**
 * Asks the current music app to lower its playback until [holdMs] after the latest call.
 * Repeated cues (the final five one-second ticks) keep the duck held instead of pumping the music.
 */
internal fun duckOtherAudioForTimerCue(context: Context, holdMs: Long) {
    val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val abandon = Runnable {
        val request = synchronized(focusLock) {
            abandonFocusRunnable = null
            val current = audioFocusRequest
            audioFocusRequest = null
            current
        } ?: return@Runnable
        try {
            am.abandonAudioFocusRequest(request)
        } catch (_: Exception) {
        }
    }
    synchronized(focusLock) {
        if (audioFocusRequest == null) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener({ }, mainHandler)
                .build()
            audioFocusRequest = request
            try {
                val granted = am.requestAudioFocus(request)
                if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    audioFocusRequest = null
                }
            } catch (_: Exception) {
                audioFocusRequest = null
            }
        }
        abandonFocusRunnable?.let { mainHandler.removeCallbacks(it) }
        abandonFocusRunnable = abandon
    }
    mainHandler.postDelayed(abandon, holdMs.coerceIn(200L, 5_000L))
}

/** Beginning of a work interval — distinct from [playHiitWorkSegmentEndCue]. */
fun playHiitWorkSegmentStartCue(context: Context) {
    playTone(context, ToneGenerator.TONE_SUP_CONFIRM, 150, CUE_VOLUME_PERCENT, duckHoldMs = 500L)
}

/** End of work (rest begins or session complete) — distinct from [playHiitWorkSegmentStartCue]. */
fun playHiitWorkSegmentEndCue(context: Context) {
    playTone(context, ToneGenerator.TONE_PROP_PROMPT, 210, CUE_VOLUME_PERCENT, duckHoldMs = 700L)
}

/** One tick per second during the final five seconds of work (same idea as stretching holds). */
fun playHiitWorkCountdownTickCue(context: Context) {
    // Hold the duck past the one-second gap so the next tick extends it.
    playTone(context, ToneGenerator.TONE_PROP_BEEP, 120, CUE_VOLUME_PERCENT, duckHoldMs = 1_300L)
}

/** Start of a prep or rest segment (softer than [playHiitWorkSegmentStartCue]). */
fun playHiitSoftSegmentStartCue(context: Context) {
    playTone(context, ToneGenerator.TONE_PROP_ACK, 130, CUE_VOLUME_PERCENT, duckHoldMs = 500L)
}
