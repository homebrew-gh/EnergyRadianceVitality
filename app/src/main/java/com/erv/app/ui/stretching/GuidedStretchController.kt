package com.erv.app.ui.stretching

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.erv.app.data.StretchGuidanceDetail
import com.erv.app.data.StretchGuidedTtsVoice
import com.erv.app.data.StretchSoundStyle
import com.erv.app.data.UserPreferences
import com.erv.app.stretching.GuidedStretchResult
import com.erv.app.stretching.GuidedStretchStep
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.STRETCH_CLOSING_REST_SECONDS
import com.erv.app.stretching.STRETCH_SETTLE_SECONDS
import com.erv.app.stretching.StretchCue
import com.erv.app.stretching.StretchSide
import com.erv.app.stretching.applyStretchGuidedTtsVoice
import com.erv.app.stretching.guidedBackTarget
import com.erv.app.stretching.guidedReadsProcedure
import com.erv.app.stretching.guidedSkipTarget
import com.erv.app.stretching.guidedStepHoldSeconds
import com.erv.app.stretching.guidedStretchResult
import com.erv.app.stretching.stretchCueForSecondLeft
import com.erv.app.stretching.toGuidedSessionSteps
import com.erv.app.ui.media.StretchCuePlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.max

/** Default gap between different stretches (or the same stretch again later in the routine). */
internal const val TRANSITION_SECONDS_NEW_STRETCH = 10

/** Same catalog stretch: right side → left side only. */
internal const val TRANSITION_SECONDS_BILATERAL_SIDE = 5

/** Prep time before the first hold (longer than between-stretch transitions so there is time to get into position). */
internal const val FIRST_STRETCH_PREP_SECONDS = 10

private const val TTS_LOG_TAG = "StretchingTts"

private fun ttsSpeakParams(): Bundle = Bundle().apply {
    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
}

/**
 * Only call when [engineReady] (init status was [TextToSpeech.SUCCESS]); otherwise speak/stop log "not bound".
 * Retries simpler [speak] shapes if needed. Uses [TextToSpeech.QUEUE_FLUSH] to replace any current
 * utterance — do not call [TextToSpeech.stop] right before [TextToSpeech.speak]: on several engines the
 * asynchronous stop lands after the new utterance is queued and silently drops it.
 */
@Suppress("DEPRECATION")
private fun runStretchTtsSpeak(
    tts: TextToSpeech,
    text: String,
    utteranceId: String,
    engineReady: Boolean
): Int {
    if (!engineReady) return TextToSpeech.ERROR
    var code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsSpeakParams(), utteranceId)
    if (code == TextToSpeech.ERROR) {
        code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }
    if (code == TextToSpeech.ERROR) {
        val legacy = java.util.HashMap<String, String>()
        legacy[TextToSpeech.Engine.KEY_PARAM_STREAM] = AudioManager.STREAM_MUSIC.toString()
        legacy[TextToSpeech.Engine.KEY_PARAM_VOLUME] = "1.0"
        legacy[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
        code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, legacy)
    }
    if (code == TextToSpeech.ERROR) {
        Log.w(TTS_LOG_TAG, "speak failed for utterance $utteranceId (engine=${tts.getDefaultEngine()})")
    }
    return code
}

@Suppress("DEPRECATION")
private fun requestTtsAudioFocusForPlayback(context: Context): AudioManager.OnAudioFocusChangeListener {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val listener = AudioManager.OnAudioFocusChangeListener { }
    am.requestAudioFocus(
        listener,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )
    return listener
}

/**
 * Seconds between two steps: a short side switch within one stretch, else the routine's
 * [gapSeconds] (its Gap Between Stretches, 10 s unless changed).
 */
internal fun transitionSecondsBetweenSteps(
    prev: GuidedStretchStep,
    next: GuidedStretchStep,
    gapSeconds: Int = TRANSITION_SECONDS_NEW_STRETCH,
): Int {
    val bilateralSideChange =
        prev.side == StretchSide.RIGHT &&
            next.side == StretchSide.LEFT &&
            prev.entry.id == next.entry.id
    return if (bilateralSideChange) TRANSITION_SECONDS_BILATERAL_SIDE else gapSeconds
}

internal fun guidedSideLabel(side: StretchSide?): String? = when (side) {
    null -> null
    StretchSide.RIGHT -> "Right side"
    StretchSide.LEFT -> "Left side"
}

/** Spoken form of the procedure: trimmed, single sentence-ending punctuation, or empty when the catalog has none. */
private fun spokenProcedure(entry: StretchCatalogEntry): String {
    val p = entry.procedure.trim()
    if (p.isEmpty()) return ""
    return if (p.last() in ".!?") p else "$p."
}

private fun buildFirstStretchIntroSpeech(entry: StretchCatalogEntry, readProcedure: Boolean): String {
    val name = entry.name.trim().ifBlank { "this stretch" }
    val procedure = if (readProcedure) spokenProcedure(entry).let { if (it.isEmpty()) "" else " $it" } else ""
    val bilateralNote = if (entry.requiresBothSides) " Right side first, then the left." else ""
    return "Welcome. Settle in, and take a slow breath. Your first stretch is the $name.$procedure$bilateralNote"
}

/** Opening of Settle In and Rest: a minute of slow breathing before the first pose is named. */
private const val SETTLE_SPEECH =
    "Welcome. Find a comfortable position and let your eyes soften. Breathe in slowly through your nose, " +
        "and let each breath out be a little longer than the breath in. We'll settle here for a minute."

/** Read near the end of the settle: the first pose. */
private fun buildSettleFirstPoseSpeech(entry: StretchCatalogEntry, readProcedure: Boolean): String {
    val name = entry.name.trim().ifBlank { "this pose" }
    val procedure = if (readProcedure) spokenProcedure(entry).let { if (it.isEmpty()) "" else " $it" } else ""
    val bilateralNote = if (entry.requiresBothSides) " Right side first, then the left." else ""
    return "When you're ready, your first pose is the $name.$procedure$bilateralNote"
}

private const val CLOSING_REST_SPEECH =
    "Now let everything go. Lie back, or simply be still. Let your body be heavy, and your breath easy."

private const val CLOSING_COMPLETE_SPEECH = "When you're ready, slowly come back."

/** Seconds before the settle ends when the first pose is announced. */
private const val SETTLE_ANNOUNCE_AT_SECONDS_LEFT = 25

/**
 * Spoken cue during the transition into [next]: its name and side, plus the how-to when
 * [readProcedure] (see [guidedReadsProcedure]).
 */
private fun buildNextStepSpeech(next: GuidedStretchStep, readProcedure: Boolean): String {
    val name = titleCaseStretchLabel(next.entry.name).ifBlank { "the next stretch" }
    val label = when (next.side) {
        null -> name
        StretchSide.RIGHT -> "$name, right side"
        StretchSide.LEFT -> "$name, left side"
    }
    val procedure = if (readProcedure) spokenProcedure(next.entry) else ""
    return if (procedure.isEmpty()) "Next, $label." else "Next, $label. $procedure"
}

private const val FIRST_INTRO_UTTERANCE_ID = "stretchFirst"
private const val FIRST_INTRO_SPEAK_WAIT_MS = 60_000L

private const val NEXT_STEP_UTTERANCE_ID = "stretchNext"
private const val NEXT_STEP_SPEAK_WAIT_MS = 30_000L

private const val COMPLETE_UTTERANCE_ID = "stretchComplete"
private const val COMPLETE_SPEAK_WAIT_MS = 15_000L

/**
 * Speaks [text] and suspends until the engine reports the utterance done, errored, or stopped
 * (e.g. the user tapped mute), or until [timeoutMs]. Never throws; returns promptly when the
 * engine is not ready or [speak] fails so the session timer is never blocked on audio.
 */
private suspend fun speakAndAwaitUtterance(
    context: Context,
    tts: TextToSpeech,
    text: String,
    utteranceId: String,
    engineReady: Boolean,
    timeoutMs: Long
) {
    if (!engineReady || text.isBlank()) return
    val expectedId = utteranceId
    withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val finished = AtomicBoolean(false)
            fun finishOnce() {
                if (finished.compareAndSet(false, true)) {
                    try {
                        tts.setOnUtteranceProgressListener(null)
                    } catch (_: Exception) {
                    }
                    cont.resume(Unit)
                }
            }
            val listener = object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == expectedId) finishOnce()
                }
                @Deprecated("Deprecated in Android UtteranceProgressListener; kept for older API levels.")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == expectedId) finishOnce()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TTS_LOG_TAG, "utterance $utteranceId error=$errorCode")
                    if (utteranceId == expectedId) finishOnce()
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId == expectedId) finishOnce()
                }
            }
            tts.setOnUtteranceProgressListener(listener)
            requestTtsAudioFocusForPlayback(context)
            val code = runStretchTtsSpeak(tts, text, utteranceId, engineReady)
            if (code == TextToSpeech.ERROR) {
                finishOnce()
            }
            cont.invokeOnCancellation {
                try {
                    tts.setOnUtteranceProgressListener(null)
                } catch (_: Exception) {
                }
            }
        }
    }
}

/**
 * Language, voice, rate, and [AudioAttributes] for speech playback (required for correct routing on many devices).
 * Call only after [TextToSpeech] init reports [TextToSpeech.SUCCESS].
 */
private fun applyStretchingTtsLayerConfig(
    tts: TextToSpeech,
    stretchGuidedTtsVoice: StretchGuidedTtsVoice
) {
    applyStretchGuidedTtsVoice(tts, Locale.getDefault(), stretchGuidedTtsVoice)
    // A touch slower and lower than normal speech, so the guidance sounds unhurried.
    tts.setSpeechRate(0.88f)
    tts.setPitch(0.95f)
    tts.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    )
}

internal fun guidedSessionTotalMinutes(
    sessionSteps: List<GuidedStretchStep>,
    stepHolds: List<Int>,
    gapSeconds: Int = TRANSITION_SECONDS_NEW_STRETCH,
    prepSeconds: Int = FIRST_STRETCH_PREP_SECONDS,
    closingRestSeconds: Int = 0,
): Int {
    if (sessionSteps.isEmpty()) return 0
    val holdTotal = sessionSteps.indices.sumOf { stepHolds.getOrNull(it) ?: stepHolds.lastOrNull() ?: 0 }
    val transitions = sessionSteps
        .zipWithNext()
        .sumOf { (a, b) -> transitionSecondsBetweenSteps(a, b, gapSeconds) }
    return max(1, (holdTotal + transitions + prepSeconds + closingRestSeconds + 59) / 60)
}


/** How a guided session ended, for the Stretching screen to log (or not) and then [GuidedStretchController.clear]. */
sealed interface GuidedStretchOutcome {
    data class Finished(val result: GuidedStretchResult) : GuidedStretchOutcome
    data object Cancelled : GuidedStretchOutcome
}

/** Everything the guided screen and its notification show. */
data class GuidedStretchSessionState(
    val routineId: String,
    val routineName: String,
    val entries: List<StretchCatalogEntry>,
    val steps: List<GuidedStretchStep>,
    /** One hold per step (both sides of a stretch share its time). */
    val stepHoldSeconds: List<Int>,
    /** Seconds between different stretches. */
    val gapSeconds: Int = TRANSITION_SECONDS_NEW_STRETCH,
    /** Seconds of settling in before the first hold (a minute with Settle In and Rest). */
    val settleSeconds: Int = FIRST_STRETCH_PREP_SECONDS,
    /** Seconds of closing rest after the last hold; 0 when there is none. */
    val closingRestSeconds: Int = 0,
    /** True during the closing rest. */
    val restActive: Boolean = false,
    val index: Int = 0,
    val phaseHold: Boolean = false,
    val firstPrepActive: Boolean = true,
    /** Negative while the intro is being spoken. */
    val secondsLeft: Int = -1,
    val paused: Boolean = false,
    val voiceEnabled: Boolean = true,
    /** Steps held for at least a second, and seconds spent (prep, holds, gaps) while not paused. */
    val heldStepIndexes: Set<Int> = emptySet(),
    val activeSeconds: Int = 0,
    val outcome: GuidedStretchOutcome? = null,
) {
    val currentStep: GuidedStretchStep? get() = steps.getOrNull(index)
    val nextStep: GuidedStretchStep? get() = steps.getOrNull(index + 1)

    /** The hold time of step [i]. */
    fun holdAt(i: Int): Int = stepHoldSeconds.getOrNull(i) ?: stepHoldSeconds.lastOrNull() ?: 30

    /** Seconds from [currentStep] to [nextStep]. */
    val gapAfterCurrent: Int?
        get() = currentStep?.let { c -> nextStep?.let { n -> transitionSecondsBetweenSteps(c, n, gapSeconds) } }

    /** The step being held, or the one coming up during a get-ready or gap. */
    val focusIndex: Int get() = if (phaseHold || firstPrepActive) index.coerceAtLeast(0) else index + 1

    /** The step the screen names: the one held, the first one while settling in, or the next one in a gap. */
    val shownStep: GuidedStretchStep?
        get() = when {
            phaseHold -> currentStep
            firstPrepActive -> steps.firstOrNull()
            else -> nextStep
        }

    val plannedMinutes: Int
        get() = guidedSessionTotalMinutes(steps, stepHoldSeconds, gapSeconds, settleSeconds, closingRestSeconds)

    fun result(): GuidedStretchResult? = guidedStretchResult(
        steps = steps,
        heldStepIndexes = heldStepIndexes,
        routineStretchIds = entries.map { it.id },
        plannedMinutes = plannedMinutes,
        activeSeconds = activeSeconds,
    )
}

/**
 * Runs a guided stretch routine (Stretching > Routines > Play) for the whole app process rather than
 * inside the screen, so it keeps going with the screen off, while another screen is open, or when the
 * app is in the background. [GuidedStretchService] keeps the process alive, holds a wake lock and shows
 * the lock-screen notification with Pause and Skip; the screen just draws [state].
 */
object GuidedStretchController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<GuidedStretchSessionState?>(null)
    val state: StateFlow<GuidedStretchSessionState?> = _state.asStateFlow()

    /** Set by a notification tap; the nav host opens the Stretching screen and clears it. */
    val openRequests = MutableStateFlow(false)

    private var appContext: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var ttsReady: CompletableDeferred<Unit>? = null
    private var runJob: Job? = null
    private var prefsJob: Job? = null

    @Volatile private var voicePref = StretchGuidedTtsVoice.SYSTEM_DEFAULT
    @Volatile private var soundStyle = StretchSoundStyle.SOFT_CHIME
    @Volatile private var guidanceDetail = StretchGuidanceDetail.FULL
    private var pausedBeforeDialog = false

    fun requestOpen() {
        openRequests.value = true
    }

    /** Starts [entries] as a guided session, unless that routine is already running. */
    fun start(
        context: Context,
        routineId: String,
        routineName: String,
        entries: List<StretchCatalogEntry>,
        /** One hold per entry, in order (see [com.erv.app.stretching.entryHoldSeconds]). */
        entryHoldSeconds: List<Int>,
        gapSeconds: Int = TRANSITION_SECONDS_NEW_STRETCH,
        /** Settle In and Rest: a minute's breathing settle and a closing rest with a soft bell. */
        settleAndRest: Boolean = false,
    ) {
        val current = _state.value
        if (current != null && current.outcome == null && current.routineId == routineId) return
        stopEngine()
        val app = context.applicationContext
        appContext = app
        _state.value = GuidedStretchSessionState(
            routineId = routineId,
            routineName = routineName,
            entries = entries,
            steps = entries.toGuidedSessionSteps(),
            stepHoldSeconds = guidedStepHoldSeconds(entries, entryHoldSeconds),
            gapSeconds = gapSeconds,
            settleSeconds = if (settleAndRest) STRETCH_SETTLE_SECONDS else FIRST_STRETCH_PREP_SECONDS,
            closingRestSeconds = if (settleAndRest) STRETCH_CLOSING_REST_SECONDS else 0,
        )
        val prefs = UserPreferences(app)
        prefsJob = scope.launch {
            launch { prefs.stretchSoundStyle.collect { soundStyle = it } }
            launch { prefs.stretchGuidanceDetail.collect { guidanceDetail = it } }
            launch {
                prefs.stretchGuidedTtsVoice.collect { v ->
                    voicePref = v
                    if (ttsOk) tts?.let { applyStretchingTtsLayerConfig(it, v) }
                }
            }
        }
        val ready = CompletableDeferred<Unit>()
        ttsReady = ready
        ttsOk = false
        tts = TextToSpeech(app) { status ->
            ttsOk = status == TextToSpeech.SUCCESS
            if (!ttsOk) Log.w(TTS_LOG_TAG, "TTS onInit status=$status")
            ready.complete(Unit)
        }
        GuidedStretchService.start(app)
        run(jumpTo = null)
    }

    fun togglePause() {
        val s = _state.value ?: return
        if (s.outcome != null || s.secondsLeft < 0) return
        val paused = !s.paused
        _state.update { it?.copy(paused = paused) }
        if (paused) stopSpeech()
    }

    fun setVoiceEnabled(enabled: Boolean) {
        _state.update { it?.copy(voiceEnabled = enabled) }
        if (!enabled) stopSpeech()
    }

    fun skip() {
        val s = _state.value ?: return
        if (s.outcome != null || s.secondsLeft < 0) return
        val target = guidedSkipTarget(s.focusIndex, s.steps.size)
        if (target == null) {
            // Skipping the last stretch ends the session; it logs what was held.
            val r = s.result()
            if (r != null) finish(r) else cancel()
        } else {
            run(jumpTo = target)
        }
    }

    fun back() {
        val s = _state.value ?: return
        if (s.outcome != null || s.secondsLeft < 0) return
        val intoHold = if (s.phaseHold) s.holdAt(s.index) - s.secondsLeft else 0
        run(jumpTo = guidedBackTarget(s.focusIndex, s.phaseHold, intoHold))
    }

    /** The End dialog opened: pause, remembering whether it was paused already. */
    fun pauseForDialog() {
        val s = _state.value ?: return
        pausedBeforeDialog = s.paused
        _state.update { it?.copy(paused = true) }
        stopSpeech()
    }

    /** The End dialog closed with Keep Going. */
    fun resumeAfterDialog() {
        _state.update { it?.copy(paused = pausedBeforeDialog) }
    }

    /** Ends the session and asks the screen to log [result]. */
    fun finish(result: GuidedStretchResult) {
        stopEngine()
        _state.update { it?.copy(outcome = GuidedStretchOutcome.Finished(result)) }
        appContext?.let { GuidedStretchService.stop(it) }
    }

    /** Ends the session without logging. */
    fun cancel() {
        stopEngine()
        _state.update { it?.copy(outcome = GuidedStretchOutcome.Cancelled) }
        appContext?.let { GuidedStretchService.stop(it) }
    }

    /** The screen handled the outcome: forget the session. */
    fun clear() {
        stopEngine()
        _state.value = null
        appContext?.let { GuidedStretchService.stop(it) }
    }

    private fun stopSpeech() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    private fun stopEngine() {
        runJob?.cancel()
        runJob = null
        prefsJob?.cancel()
        prefsJob = null
        stopSpeech()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ttsOk = false
    }

    /** One second of session time; paused time does not count. */
    private suspend fun waitOneSecond() {
        var ms = 0
        while (ms < 1000) {
            delay(100)
            if (_state.value?.paused != true) ms += 100
        }
        _state.update { it?.copy(activeSeconds = it.activeSeconds + 1) }
    }

    private suspend fun countDown(from: Int, onEachSecond: (Int) -> Unit = {}) {
        var left = from
        _state.update { it?.copy(secondsLeft = left) }
        while (left > 0) {
            onEachSecond(left)
            waitOneSecond()
            left--
            _state.update { it?.copy(secondsLeft = left) }
        }
    }

    private fun voiceOn(): Boolean = _state.value?.voiceEnabled == true && ttsOk

    private suspend fun say(context: Context, text: String, id: String, waitMs: Long) {
        val engine = tts ?: return
        if (!voiceOn()) return
        speakAndAwaitUtterance(context, engine, text, id, ttsOk, waitMs)
    }

    /** (Re)starts the session coroutine at [jumpTo]; null runs the intro first. */
    private fun run(jumpTo: Int?) {
        stopSpeech()
        runJob?.cancel()
        runJob = scope.launch {
            val context = appContext ?: return@launch
            withTimeoutOrNull(5_000L) { ttsReady?.await() }
            val start = _state.value ?: return@launch
            val steps = start.steps
            if (steps.isEmpty()) {
                finish(GuidedStretchResult(emptyList(), start.plannedMinutes))
                return@launch
            }
            if (ttsOk) {
                tts?.let { applyStretchingTtsLayerConfig(it, voicePref) }
                delay(150)
            }
            var i: Int
            if (jumpTo == null) {
                _state.update {
                    it?.copy(firstPrepActive = true, phaseHold = false, restActive = false, index = 0, secondsLeft = -1)
                }
                val readFirst = guidedReadsProcedure(guidanceDetail, steps, 0)
                if (start.settleSeconds > FIRST_STRETCH_PREP_SECONDS) {
                    // Settle In: a calm opening, a minute of breathing, then the first pose is named.
                    say(context, SETTLE_SPEECH, FIRST_INTRO_UTTERANCE_ID, FIRST_INTRO_SPEAK_WAIT_MS)
                    var announce: Job? = null
                    countDown(start.settleSeconds) { left ->
                        if (left == SETTLE_ANNOUNCE_AT_SECONDS_LEFT.coerceAtMost(start.settleSeconds)) {
                            announce = launch {
                                say(
                                    context,
                                    buildSettleFirstPoseSpeech(start.entries[0], readFirst),
                                    NEXT_STEP_UTTERANCE_ID,
                                    NEXT_STEP_SPEAK_WAIT_MS,
                                )
                            }
                        }
                    }
                    announce?.join()
                } else {
                    say(
                        context,
                        buildFirstStretchIntroSpeech(start.entries[0], readFirst),
                        FIRST_INTRO_UTTERANCE_ID,
                        FIRST_INTRO_SPEAK_WAIT_MS,
                    )
                    countDown(start.settleSeconds)
                }
                _state.update { it?.copy(firstPrepActive = false) }
                i = 0
            } else {
                // After Skip or Back: a short get-ready for the chosen stretch, announced like any other.
                i = jumpTo.coerceIn(0, steps.lastIndex)
                _state.update { it?.copy(firstPrepActive = false, phaseHold = false, restActive = false, index = i - 1) }
                val announce = async {
                    say(
                        context,
                        buildNextStepSpeech(steps[i], guidedReadsProcedure(guidanceDetail, steps, i)),
                        NEXT_STEP_UTTERANCE_ID,
                        NEXT_STEP_SPEAK_WAIT_MS,
                    )
                }
                countDown(TRANSITION_SECONDS_BILATERAL_SIDE)
                announce.await()
            }
            while (i < steps.size) {
                val stepIndex = i
                val hold = start.holdAt(stepIndex)
                _state.update { it?.copy(phaseHold = true, index = stepIndex) }
                StretchCuePlayer.play(context, soundStyle, StretchCue.HOLD_START)
                countDown(hold) { left ->
                    stretchCueForSecondLeft(soundStyle, hold, left)?.let { cue ->
                        StretchCuePlayer.play(context, soundStyle, cue)
                    }
                    if (left < hold) _state.update { it?.copy(heldStepIndexes = it.heldStepIndexes + stepIndex) }
                }
                _state.update { it?.copy(heldStepIndexes = it.heldStepIndexes + stepIndex) }
                StretchCuePlayer.play(context, soundStyle, StretchCue.HOLD_END)
                if (i == steps.lastIndex) break
                _state.update { it?.copy(phaseHold = false) }
                // Announce the next stretch while the gap counts down. If the how-to outlasts the gap,
                // the next hold waits for speech to finish so it is never cut off by the start sound.
                val announce = async {
                    say(
                        context,
                        buildNextStepSpeech(steps[i + 1], guidedReadsProcedure(guidanceDetail, steps, i + 1)),
                        NEXT_STEP_UTTERANCE_ID,
                        NEXT_STEP_SPEAK_WAIT_MS,
                    )
                }
                countDown(transitionSecondsBetweenSteps(steps[i], steps[i + 1], start.gapSeconds))
                announce.await()
                i++
                val next = i
                _state.update { it?.copy(index = next) }
            }
            if (start.closingRestSeconds > 0) {
                // Rest: a quiet minute after the last pose, ending on one soft bell.
                _state.update { it?.copy(phaseHold = false, restActive = true, index = steps.lastIndex) }
                val settle = async { say(context, CLOSING_REST_SPEECH, NEXT_STEP_UTTERANCE_ID, NEXT_STEP_SPEAK_WAIT_MS) }
                countDown(start.closingRestSeconds)
                settle.await()
                val bellStyle = if (soundStyle == StretchSoundStyle.VIBRATE_ONLY) soundStyle else StretchSoundStyle.SINGING_BOWL
                StretchCuePlayer.play(context, bellStyle, StretchCue.HOLD_END)
                delay(2_500)
                say(context, CLOSING_COMPLETE_SPEECH, COMPLETE_UTTERANCE_ID, COMPLETE_SPEAK_WAIT_MS)
            } else {
                say(context, "Stretching complete.", COMPLETE_UTTERANCE_ID, COMPLETE_SPEAK_WAIT_MS)
            }
            val done = _state.value ?: return@launch
            finish(done.result() ?: GuidedStretchResult(done.entries.map { it.id }, done.plannedMinutes))
        }
    }
}
