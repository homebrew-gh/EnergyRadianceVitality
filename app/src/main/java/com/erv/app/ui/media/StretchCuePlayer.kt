package com.erv.app.ui.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.erv.app.data.StretchSoundStyle
import com.erv.app.stretching.STRETCH_CUE_SAMPLE_RATE
import com.erv.app.stretching.STRETCH_CUE_VIBRATION_AMPLITUDE
import com.erv.app.stretching.StretchCue
import com.erv.app.stretching.isSynthesized
import com.erv.app.stretching.stretchCueVibrationPattern
import com.erv.app.stretching.synthesizeStretchCue
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays stretch hold cues in the chosen [StretchSoundStyle]. The calm styles are synthesized once and
 * cached, then played through a static [AudioTrack] on the media stream (follows media volume, music
 * ducks under it as for the other timer cues). Classic keeps the old [ToneGenerator] tones.
 */
object StretchCuePlayer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = ConcurrentHashMap<Pair<StretchSoundStyle, StretchCue>, ShortArray>()

    fun play(context: Context, style: StretchSoundStyle, cue: StretchCue) {
        val app = context.applicationContext
        when {
            style == StretchSoundStyle.VIBRATE_ONLY -> vibrate(app, cue)
            style == StretchSoundStyle.CLASSIC -> playClassic(app, cue)
            style.isSynthesized -> playSynth(app, style, cue)
        }
    }

    /** A gentle buzz alongside the sound at a hold's end, so it is felt with the phone in a pocket. */
    fun nudge(context: Context) {
        vibrate(context.applicationContext, StretchCue.HOLD_ALMOST_DONE)
    }

    private fun playSynth(context: Context, style: StretchSoundStyle, cue: StretchCue) {
        val pcm = cache.getOrPut(style to cue) { synthesizeStretchCue(style, cue) }
        if (pcm.isEmpty()) return
        val durationMs = pcm.size * 1000L / STRETCH_CUE_SAMPLE_RATE
        duckOtherAudioForTimerCue(context, durationMs.coerceAtMost(5_000L))
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(STRETCH_CUE_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            mainHandler.postDelayed({
                try {
                    track.stop()
                } catch (_: Exception) {
                }
                try {
                    track.release()
                } catch (_: Exception) {
                }
            }, durationMs + 150L)
        } catch (_: Exception) {
        }
    }

    private fun playClassic(context: Context, cue: StretchCue) {
        val (tone, ms, duck) = when (cue) {
            StretchCue.HOLD_START -> Triple(ToneGenerator.TONE_PROP_ACK, 140, 500L)
            StretchCue.HOLD_END -> Triple(ToneGenerator.TONE_PROP_PROMPT, 220, 700L)
            StretchCue.COUNTDOWN_TICK, StretchCue.HOLD_ALMOST_DONE ->
                Triple(ToneGenerator.TONE_PROP_BEEP, 120, 1_300L)
        }
        duckOtherAudioForTimerCue(context, duck)
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(tone, ms)
            mainHandler.postDelayed({
                try {
                    tg.release()
                } catch (_: Exception) {
                }
            }, ms + 50L)
        } catch (_: Exception) {
        }
    }

    private fun vibrate(context: Context, cue: StretchCue) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        val timings = stretchCueVibrationPattern(cue)
        try {
            val effect = if (vibrator.hasAmplitudeControl()) {
                val amps = IntArray(timings.size) { i -> if (i % 2 == 1) STRETCH_CUE_VIBRATION_AMPLITUDE else 0 }
                VibrationEffect.createWaveform(timings, amps, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }
}
