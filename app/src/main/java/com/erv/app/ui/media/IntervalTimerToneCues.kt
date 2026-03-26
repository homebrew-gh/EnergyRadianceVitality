package com.erv.app.ui.media

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Short tones on [AudioManager.STREAM_MUSIC] (follows media volume), matching the stretching timer
 * pattern: [ToneGenerator.release] is delayed so playback is not cut off.
 */
private fun playTone(tone: Int, durationMs: Int, volumePercent: Int) {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, volumePercent.coerceIn(1, 100))
        tg.startTone(tone, durationMs)
        val h = Handler(Looper.getMainLooper())
        h.postDelayed(
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

/** Beginning of a work interval — distinct from [playWorkSegmentEnd]. */
fun playHiitWorkSegmentStartCue() {
    playTone(ToneGenerator.TONE_SUP_CONFIRM, 150, 86)
}

/** End of work (rest begins or session complete) — distinct from [playHiitWorkSegmentStartCue]. */
fun playHiitWorkSegmentEndCue() {
    playTone(ToneGenerator.TONE_PROP_PROMPT, 210, 87)
}

/** One tick per second during the final five seconds of work (same idea as stretching holds). */
fun playHiitWorkCountdownTickCue() {
    playTone(ToneGenerator.TONE_PROP_BEEP, 60, 55)
}

/** Start of a prep or rest segment (softer than [playHiitWorkSegmentStartCue]). */
fun playHiitSoftSegmentStartCue() {
    playTone(ToneGenerator.TONE_PROP_ACK, 130, 82)
}
