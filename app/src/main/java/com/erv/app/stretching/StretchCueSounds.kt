package com.erv.app.stretching

import com.erv.app.data.StretchSoundStyle
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The sounds a stretch hold makes, and when. Pure (no Android), so it is unit tested; the player in
 * `ui/media/StretchCuePlayer.kt` turns these into audio or vibration.
 */
enum class StretchCue {
    /** A hold begins. */
    HOLD_START,

    /** One quiet note a few seconds before a hold ends (calm styles only). */
    HOLD_ALMOST_DONE,

    /** One tick a second over the last five seconds ([StretchSoundStyle.CLASSIC] only). */
    COUNTDOWN_TICK,

    /** A hold is over. */
    HOLD_END,
}

/** Seconds left on a hold when the calm styles play [StretchCue.HOLD_ALMOST_DONE]. */
const val STRETCH_ALMOST_DONE_SECONDS = 5

/** Holds shorter than this get no almost-done note, so a short hold is not two sounds back to back. */
const val STRETCH_ALMOST_DONE_MIN_HOLD_SECONDS = 15

/**
 * The cue, if any, to play while [secondsLeft] of a [holdSeconds] hold remain (checked once a second,
 * before the second passes). Classic keeps today's five ticks; the calm styles and vibration give
 * one gentle heads-up instead.
 */
fun stretchCueForSecondLeft(style: StretchSoundStyle, holdSeconds: Int, secondsLeft: Int): StretchCue? {
    if (secondsLeft <= 0) return null
    return when (style) {
        StretchSoundStyle.CLASSIC ->
            if (secondsLeft <= minOf(5, holdSeconds)) StretchCue.COUNTDOWN_TICK else null
        else ->
            if (holdSeconds >= STRETCH_ALMOST_DONE_MIN_HOLD_SECONDS && secondsLeft == STRETCH_ALMOST_DONE_SECONDS) {
                StretchCue.HOLD_ALMOST_DONE
            } else {
                null
            }
    }
}

/** True for the styles ERV synthesizes as audio (not Classic tones, not vibration). */
val StretchSoundStyle.isSynthesized: Boolean
    get() = this == StretchSoundStyle.SOFT_CHIME ||
        this == StretchSoundStyle.SINGING_BOWL ||
        this == StretchSoundStyle.WOOD_BLOCK

const val STRETCH_CUE_SAMPLE_RATE = 44_100

/** Loudest sample of a synthesized cue, as a share of full scale: well under the old 100% tones. */
const val STRETCH_CUE_PEAK = 0.32

/** One sine partial: frequency, loudness relative to the note, and how fast it fades. */
private data class Partial(val hz: Double, val amp: Double, val decaySeconds: Double)

/** One struck note: its partials, when it starts within the cue, how soft its attack is. */
private data class Note(
    val partials: List<Partial>,
    val startSeconds: Double = 0.0,
    val gain: Double = 1.0,
    val attackSeconds: Double,
)

/** A bell-like chime: inharmonic partials that fade quickly at the top, slowly at the bottom. */
private fun chime(hz: Double, start: Double = 0.0, gain: Double = 1.0) = Note(
    partials = listOf(
        Partial(hz, 1.0, 0.55),
        Partial(hz * 2.76, 0.28, 0.22),
        Partial(hz * 5.40, 0.08, 0.10),
    ),
    startSeconds = start,
    gain = gain,
    attackSeconds = 0.012,
)

/** A singing bowl: low, long, with two near partials that beat slowly. */
private fun bowl(hz: Double, start: Double = 0.0, gain: Double = 1.0) = Note(
    partials = listOf(
        Partial(hz, 1.0, 1.6),
        Partial(hz * 1.004, 0.6, 1.6),
        Partial(hz * 2.71, 0.30, 0.9),
        Partial(hz * 5.15, 0.07, 0.4),
    ),
    startSeconds = start,
    gain = gain,
    attackSeconds = 0.06,
)

/** A wood block knock: short and dry. */
private fun knock(hz: Double, start: Double = 0.0, gain: Double = 1.0) = Note(
    partials = listOf(
        Partial(hz, 1.0, 0.045),
        Partial(hz * 2.32, 0.35, 0.025),
    ),
    startSeconds = start,
    gain = gain,
    attackSeconds = 0.002,
)

private fun notesFor(style: StretchSoundStyle, cue: StretchCue): List<Note> = when (style) {
    StretchSoundStyle.SOFT_CHIME -> when (cue) {
        StretchCue.HOLD_START -> listOf(chime(784.0))
        StretchCue.HOLD_ALMOST_DONE -> listOf(chime(1046.5, gain = 0.45))
        StretchCue.COUNTDOWN_TICK -> listOf(chime(1046.5, gain = 0.35))
        StretchCue.HOLD_END -> listOf(chime(784.0), chime(587.3, start = 0.22, gain = 0.9))
    }
    StretchSoundStyle.SINGING_BOWL -> when (cue) {
        StretchCue.HOLD_START -> listOf(bowl(262.0))
        StretchCue.HOLD_ALMOST_DONE -> listOf(bowl(392.0, gain = 0.4))
        StretchCue.COUNTDOWN_TICK -> listOf(bowl(392.0, gain = 0.3))
        StretchCue.HOLD_END -> listOf(bowl(196.0))
    }
    StretchSoundStyle.WOOD_BLOCK -> when (cue) {
        StretchCue.HOLD_START -> listOf(knock(820.0))
        StretchCue.HOLD_ALMOST_DONE -> listOf(knock(980.0, gain = 0.5))
        StretchCue.COUNTDOWN_TICK -> listOf(knock(980.0, gain = 0.4))
        StretchCue.HOLD_END -> listOf(knock(820.0), knock(620.0, start = 0.16))
    }
    StretchSoundStyle.CLASSIC, StretchSoundStyle.VIBRATE_ONLY -> emptyList()
}

/** How long a note rings before it is quieter than about -43 dB (then a short fade). */
private fun Note.endSeconds(): Double =
    startSeconds + attackSeconds + (partials.maxOf { it.decaySeconds } * 5.0)

/**
 * 16-bit mono PCM for [cue] in [style], peaking at [STRETCH_CUE_PEAK] × [volume] of full scale.
 * Empty for Classic and Vibrate Only, which the player handles without samples.
 */
fun synthesizeStretchCue(style: StretchSoundStyle, cue: StretchCue, volume: Double = 1.0): ShortArray {
    val notes = notesFor(style, cue)
    if (notes.isEmpty()) return ShortArray(0)
    val rate = STRETCH_CUE_SAMPLE_RATE
    val total = (notes.maxOf { it.endSeconds() } * rate).roundToInt().coerceAtLeast(1)
    val mix = DoubleArray(total)
    for (note in notes) {
        val first = (note.startSeconds * rate).roundToInt()
        for (i in first until total) {
            val t = (i - first).toDouble() / rate
            val attack = if (t < note.attackSeconds) {
                0.5 - 0.5 * kotlin.math.cos(PI * t / note.attackSeconds)
            } else {
                1.0
            }
            var s = 0.0
            for (p in note.partials) {
                s += p.amp * exp(-t / p.decaySeconds) * sin(2.0 * PI * p.hz * t)
            }
            mix[i] += note.gain * attack * s
        }
    }
    // A short fade at the very end so the last sample is silence, never a click.
    val fade = minOf(total, (0.03 * rate).roundToInt())
    for (k in 0 until fade) {
        mix[total - 1 - k] *= k.toDouble() / fade
    }
    val peak = mix.maxOf { kotlin.math.abs(it) }.takeIf { it > 0.0 } ?: return ShortArray(total)
    val scale = STRETCH_CUE_PEAK * volume.coerceIn(0.0, 1.0) / peak * Short.MAX_VALUE
    return ShortArray(total) { i -> (mix[i] * scale).roundToInt().toShort() }
}

/** Vibration for [cue] when the style is [StretchSoundStyle.VIBRATE_ONLY]: on/off milliseconds, starting with a pause. */
fun stretchCueVibrationPattern(cue: StretchCue): LongArray = when (cue) {
    StretchCue.HOLD_START -> longArrayOf(0, 60)
    StretchCue.HOLD_ALMOST_DONE -> longArrayOf(0, 35)
    StretchCue.COUNTDOWN_TICK -> longArrayOf(0, 25)
    StretchCue.HOLD_END -> longArrayOf(0, 80, 140, 80)
}

/** Amplitude (1–255) for [stretchCueVibrationPattern]'s on segments: gentle, never full strength. */
const val STRETCH_CUE_VIBRATION_AMPLITUDE = 110
