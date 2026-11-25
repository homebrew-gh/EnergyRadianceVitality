package com.erv.app.ui.workouts

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.erv.app.ui.media.duckOtherAudioForTimerCue
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import java.util.Locale

/**
 * Speaks and buzzes the session screen's cardio cues (wording in
 * [com.erv.app.workouts.WorkoutSessionCardioCues]). Music ducks under the voice.
 */
internal class SessionCardioCuePlayer(private val context: Context) {
    @Volatile
    private var ready = false
    private var counter = 0

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) Log.w(TAG, "TTS init status=$status")
    }
    private var languageSet = false

    /** Says [text] with a short buzz; replaces anything still being said. */
    fun say(text: String) {
        buzz()
        if (!ready) return
        if (!languageSet) {
            languageSet = true
            runCatching { tts.language = Locale.getDefault() }
        }
        duckOtherAudioForTimerCue(context, holdMs = 4_000L)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        runCatching { tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "erv-cardio-cue-${counter++}") }
            .onFailure { Log.w(TAG, "speak failed", it) }
    }

    /** One countdown beep (3, 2, 1 before a timed leg ends). */
    fun tick() {
        playHiitWorkCountdownTickCue(context)
    }

    private fun buzz() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 120, 80, 120), -1)
            }
        }
    }

    fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    private companion object {
        const val TAG = "SessionCardioCues"
    }
}

@Composable
internal fun rememberSessionCardioCuePlayer(): SessionCardioCuePlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(context) { SessionCardioCuePlayer(context) }
    DisposableEffect(player) { onDispose { player.shutdown() } }
    return player
}
