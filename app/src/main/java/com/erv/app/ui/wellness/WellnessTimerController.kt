package com.erv.app.ui.wellness

import android.content.Context
import com.erv.app.data.StretchSoundStyle
import com.erv.app.data.UserPreferences
import com.erv.app.stretching.StretchCue
import com.erv.app.stretching.stretchCueForSecondLeft
import com.erv.app.ui.media.StretchCuePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** The sections that share the wellness timer. */
enum class WellnessTimerKind { SAUNA, COLD_PLUNGE, LIGHT }

/** A running (or paused) wellness timer. Counts down from [totalSeconds]. */
data class WellnessTimerState(
    val kind: WellnessTimerKind,
    /** Shown large on the timer screen and as the notification title, e.g. "Sauna". */
    val title: String,
    /** A second line such as "160 °F" or the light's name; may be blank. */
    val detail: String,
    val totalSeconds: Int,
    /** Wall-clock end while running; ignored while paused. */
    val endAtMs: Long,
    /** Seconds left when paused, or null while running. */
    val pausedRemainingSeconds: Int?,
    val remainingSeconds: Int,
) {
    val paused: Boolean get() = pausedRemainingSeconds != null
    val elapsedSeconds: Int get() = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)
}

/** What a finished timer saved, for the screen's snackbar. */
data class WellnessTimerResult(val kind: WellnessTimerKind, val message: String)

/**
 * One step of a chained timer (a contrast session's sauna, switch and cold rounds).
 * [onSave] works as in [WellnessTimerController.start]; [next] gives the step after this one,
 * or null when this is the last.
 */
class WellnessTimerStep(
    val kind: WellnessTimerKind,
    val title: String,
    val detail: String,
    val totalSeconds: Int,
    val onSave: suspend (elapsedSeconds: Int) -> String?,
    val next: (() -> WellnessTimerStep?)? = null,
)

/** Whole minutes to log for [seconds] of light: rounded, never zero once something was done. */
fun lightMinutesForSeconds(seconds: Int): Int = ((seconds + 30) / 60).coerceAtLeast(1)

/**
 * One timer for sauna, cold plunge and light therapy that keeps going with the screen off.
 *
 * It counts against a wall-clock end time (so it does not drift), runs in its own scope rather than
 * in a screen, and [WellnessTimerService] keeps the process awake with a lock-screen notification
 * (Pause / Resume and End and Save). When the time is up it plays the Settings stretch sound style
 * and saves through the `onSave` callback given to [start]; End and Save saves the time done so far;
 * [discard] saves nothing.
 */
object WellnessTimerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<WellnessTimerState?>(null)
    val state: StateFlow<WellnessTimerState?> = _state.asStateFlow()

    private val _results = MutableSharedFlow<WellnessTimerResult>(extraBufferCapacity = 4)
    /** One event per saved session; the open section screen shows it in a snackbar. */
    val results: SharedFlow<WellnessTimerResult> = _results.asSharedFlow()

    /** Set by a notification tap; the nav host opens the section and clears it. */
    val openRequests = MutableStateFlow(false)

    private var appContext: Context? = null
    private var onSave: (suspend (elapsedSeconds: Int) -> String?)? = null
    private var next: (() -> WellnessTimerStep?)? = null
    private var tickJob: Job? = null
    @Volatile private var soundStyle = StretchSoundStyle.SOFT_CHIME

    fun requestOpen() {
        openRequests.value = true
    }

    /**
     * Starts a timer unless one is already running (one at a time: returns false then).
     * [onSave] gets the seconds done and returns the snackbar text; it runs outside any screen,
     * so it must only capture long-lived objects (repositories, relay pool, signer) and plain values.
     */
    fun start(
        context: Context,
        kind: WellnessTimerKind,
        title: String,
        detail: String,
        totalSeconds: Int,
        onSave: suspend (elapsedSeconds: Int) -> String?,
    ): Boolean = startSteps(context, WellnessTimerStep(kind, title, detail, totalSeconds, onSave))

    /**
     * Starts a chain of timers. When a step ends (time up or End and Save) it saves and the next
     * step starts at once, without stopping the lock-screen notification. Discard stops the chain.
     */
    fun startSteps(context: Context, first: WellnessTimerStep): Boolean {
        if (_state.value != null || first.totalSeconds <= 0) return false
        val app = context.applicationContext
        appContext = app
        load(first)
        scope.launch {
            soundStyle = runCatching { UserPreferences(app).stretchSoundStyle.first() }
                .getOrDefault(StretchSoundStyle.SOFT_CHIME)
        }
        WellnessTimerService.start(app)
        startTicking()
        return true
    }

    private fun load(step: WellnessTimerStep) {
        onSave = step.onSave
        next = step.next
        _state.value = WellnessTimerState(
            kind = step.kind,
            title = step.title,
            detail = step.detail,
            totalSeconds = step.totalSeconds,
            endAtMs = System.currentTimeMillis() + step.totalSeconds * 1000L,
            pausedRemainingSeconds = null,
            remainingSeconds = step.totalSeconds,
        )
    }

    fun togglePause() {
        val s = _state.value ?: return
        if (s.paused) {
            val left = s.pausedRemainingSeconds ?: s.remainingSeconds
            _state.value = s.copy(
                endAtMs = System.currentTimeMillis() + left * 1000L,
                pausedRemainingSeconds = null,
                remainingSeconds = left,
            )
            startTicking()
        } else {
            tickJob?.cancel()
            val left = remainingNow(s)
            _state.value = s.copy(pausedRemainingSeconds = left, remainingSeconds = left)
        }
    }

    /** Stops now and saves the time done so far. */
    fun endAndSave() {
        val s = _state.value ?: return
        finish(save = true, elapsedSeconds = (s.totalSeconds - remainingNow(s)).coerceIn(0, s.totalSeconds))
    }

    /** Stops now without saving. */
    fun discard() {
        finish(save = false, elapsedSeconds = 0)
    }

    private fun remainingNow(s: WellnessTimerState): Int =
        s.pausedRemainingSeconds
            ?: ceil((s.endAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 1000.0).toInt()

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (true) {
                val s = _state.value ?: return@launch
                if (s.paused) return@launch
                val left = remainingNow(s)
                if (left != s.remainingSeconds) {
                    _state.value = s.copy(remainingSeconds = left)
                    if (left > 0) {
                        stretchCueForSecondLeft(soundStyle, s.totalSeconds, left)?.let { cue ->
                            appContext?.let { StretchCuePlayer.play(it, soundStyle, cue) }
                        }
                    }
                }
                if (left <= 0) {
                    appContext?.let {
                        StretchCuePlayer.play(it, soundStyle, StretchCue.HOLD_END)
                        StretchCuePlayer.nudge(it)
                    }
                    finish(save = true, elapsedSeconds = s.totalSeconds)
                    return@launch
                }
                // Wake just after the next whole second so the shown number changes on time.
                val msToNextSecond = ((s.endAtMs - System.currentTimeMillis()) % 1000L).let { if (it <= 0) 1000L else it }
                delay(msToNextSecond + 15L)
            }
        }
    }

    private fun finish(save: Boolean, elapsedSeconds: Int) {
        val s = _state.value ?: return
        val saver = onSave
        val nextStep = if (save) runCatching { next?.invoke() }.getOrNull() else null
        tickJob?.cancel()
        tickJob = null
        onSave = null
        next = null
        if (nextStep != null && nextStep.totalSeconds > 0) {
            // Straight into the next step: the state never goes null, so the service keeps running.
            load(nextStep)
            startTicking()
        } else {
            _state.value = null
            appContext?.let { WellnessTimerService.stop(it) }
        }
        if (save && saver != null && elapsedSeconds > 0) {
            scope.launch {
                val message = runCatching { saver(elapsedSeconds) }.getOrNull()
                if (message != null) _results.tryEmit(WellnessTimerResult(s.kind, message))
            }
        }
    }
}
