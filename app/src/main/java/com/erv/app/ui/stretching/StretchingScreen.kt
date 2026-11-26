@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.stretching

import com.erv.app.ui.theme.ervTopAppBarColors
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import com.erv.app.stretching.GuidedStretchResult
import com.erv.app.stretching.guidedBackTarget
import com.erv.app.stretching.guidedSkipTarget
import com.erv.app.stretching.guidedStretchResult
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.data.StretchGuidedTtsVoice
import com.erv.app.data.StretchSoundStyle
import com.erv.app.data.StretchGuidanceDetail
import com.erv.app.stretching.guidedReadsProcedure
import com.erv.app.stretching.guidedEntryPositions
import com.erv.app.stretching.guidedSecondsRemaining
import com.erv.app.stretching.StretchCue
import com.erv.app.stretching.stretchCueForSecondLeft
import com.erv.app.ui.media.StretchCuePlayer
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.stretching.GuidedStretchStep
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.StretchSide
import com.erv.app.stretching.routineHoldSegments
import com.erv.app.stretching.toGuidedSessionSteps
import com.erv.app.stretching.groupedByCategory
import com.erv.app.stretching.stretchCategoryDisplayLabel
import com.erv.app.stretching.StretchDayLog
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchRoutine
import com.erv.app.stretching.StretchSession
import com.erv.app.programs.decodeStretchLaunch
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.StretchingSync
import com.erv.app.workouts.WorkoutLoggedItemKind
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.activeWorkoutMobilityLaunch
import com.erv.app.workouts.linkFor
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.stretching.applyStretchGuidedTtsVoice
import com.erv.app.stretching.DatedStretchSession
import com.erv.app.SectionLogDateFilter
import com.erv.app.stretching.datedStretchSessionsForSectionLog
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.datesWithStretchActivity
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private enum class StretchTab { Stretches, Routines }

/** Title case each whitespace-separated word for display (catalog JSON may be lowercase). */
/** Line for routine cards / editor when bilateral stretches need two holds per slot. */
private fun routineHoldSummaryLine(
    stretchIds: List<String>,
    catalog: List<StretchCatalogEntry>,
    holdSeconds: Int
): String {
    if (stretchIds.isEmpty()) return "0 stretches"
    val segments = routineHoldSegments(stretchIds, catalog)
    val totalSec = segments * holdSeconds
    val min = totalSec / 60
    val sec = totalSec % 60
    val totalLabel = if (sec == 0) "${min} min" else "${min}m ${sec}s"
    return if (segments == stretchIds.size) {
        "${stretchIds.size} stretches · ${holdSeconds}s hold each"
    } else {
        "${stretchIds.size} stretches · $segments hold segments × ${holdSeconds}s ($totalLabel) — left & right where marked"
    }
}

private fun titleCaseStretchLabel(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return s
    return s.split(Regex("\\s+")).joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Between different stretches (or same stretch again later in the routine). */
private const val TRANSITION_SECONDS_NEW_STRETCH = 10

/** Same catalog stretch: right side → left side only. */
private const val TRANSITION_SECONDS_BILATERAL_SIDE = 5

/** Prep time before the first hold (longer than between-stretch transitions so there is time to get into position). */
private const val FIRST_STRETCH_PREP_SECONDS = 10

/** Guided player palette: dusk blue into deep sage, soft off-white text, a pale sage accent. */
private val CalmGuidedTop = Color(0xFF243047)
private val CalmGuidedBottom = Color(0xFF2F4A45)
private val CalmGuidedInk = Color(0xFFECEFE8)
private val CalmGuidedAccent = Color(0xFFB7D3C1)

/** One paced breath on the guided screen: 4 seconds in, 6 out. */
private const val BREATH_IN_MS = 4_000
private const val BREATH_CYCLE_MS = 10_000

/** Glow scale through a breath: eases up while breathing in, back down while breathing out. */
private fun breathScaleAt(ms: Float): Float {
    fun ease(x: Float) = (0.5f - 0.5f * kotlin.math.cos(Math.PI.toFloat() * x.coerceIn(0f, 1f)))
    val lift = if (ms < BREATH_IN_MS) ease(ms / BREATH_IN_MS) else 1f - ease((ms - BREATH_IN_MS) / (BREATH_CYCLE_MS - BREATH_IN_MS))
    return 0.82f + 0.18f * lift
}

private fun guidedTimeLeftLabel(seconds: Int): String = when {
    seconds <= 0 -> "Almost done"
    seconds < 60 -> "Under a minute left"
    else -> "About ${(seconds + 30) / 60} min left"
}


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

private fun transitionSecondsBetweenSteps(prev: GuidedStretchStep, next: GuidedStretchStep): Int {
    val bilateralSideChange =
        prev.side == StretchSide.RIGHT &&
            next.side == StretchSide.LEFT &&
            prev.entry.id == next.entry.id
    return if (bilateralSideChange) TRANSITION_SECONDS_BILATERAL_SIDE else TRANSITION_SECONDS_NEW_STRETCH
}

private fun guidedSideLabel(side: StretchSide?): String? = when (side) {
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

private fun guidedSessionTotalMinutes(sessionSteps: List<GuidedStretchStep>, holdSeconds: Int): Int {
    if (sessionSteps.isEmpty()) return 0
    val holdTotal = sessionSteps.size * holdSeconds
    val transitions = sessionSteps
        .zipWithNext()
        .sumOf { (a, b) -> transitionSecondsBetweenSteps(a, b) }
    val firstPrep = FIRST_STRETCH_PREP_SECONDS
    return max(1, (holdTotal + transitions + firstPrep + 59) / 60)
}

private fun resolveStretchEntries(
    stretchIds: List<String>,
    repository: StretchingRepository
): List<StretchCatalogEntry> =
    stretchIds.map { id ->
        repository.stretchById(id)
            ?: StretchCatalogEntry(
                id = id,
                name = "Unknown stretch",
                category = "other",
                requiresBothSides = false,
                procedure = "This stretch is no longer in the catalog."
            )
    }

@Composable
fun StretchingCategoryScreen(
    repository: StretchingRepository,
    workoutRepository: WorkoutRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onReturnToWorkoutRun: (String) -> Unit,
    onOpenLog: () -> Unit,
    initialTab: String = StretchTab.Stretches.name,
) {
    val stretchGuidedTtsVoice by userPreferences.stretchGuidedTtsVoice.collectAsState(
        initial = StretchGuidedTtsVoice.SYSTEM_DEFAULT
    )
    val stretchSoundStyle by userPreferences.stretchSoundStyle.collectAsState(
        initial = StretchSoundStyle.SOFT_CHIME
    )
    val stretchGuidanceDetail by userPreferences.stretchGuidanceDetail.collectAsState(
        initial = StretchGuidanceDetail.FULL
    )
    val state by repository.state.collectAsState(initial = StretchLibraryState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resolvedInitialTab = StretchTab.entries
        .firstOrNull { it.name.equals(initialTab, ignoreCase = true) }
        ?.ordinal
        ?: StretchTab.Stretches.ordinal
    var activeTab by rememberSaveable { mutableIntStateOf(resolvedInitialTab) }
    LaunchedEffect(resolvedInitialTab) {
        activeTab = resolvedInitialTab
    }
    var guidedRoutine by remember { mutableStateOf<StretchRoutine?>(null) }
    var routineEditor by remember { mutableStateOf<StretchRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        val raw = userPreferences.consumeProgramDashboardStretchLaunchJson() ?: return@LaunchedEffect
        val payload = decodeStretchLaunch(raw) ?: return@LaunchedEffect
        if (!payload.routineId.isNullOrBlank()) {
            for (attempt in 0..30) {
                val r = repository.currentState().routines.firstOrNull { it.id == payload.routineId }
                if (r != null) {
                    guidedRoutine = r
                    return@LaunchedEffect
                }
                delay(80)
            }
            snackbarHostState.showSnackbar("Stretch routine not found — open Weekly Planner to fix this block.")
        } else if (payload.stretchIds.isNotEmpty()) {
            guidedRoutine = StretchRoutine(
                id = java.util.UUID.randomUUID().toString(),
                name = payload.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Program stretch",
                stretchIds = payload.stretchIds,
                holdSecondsPerStretch = payload.holdSecondsPerStretch.coerceIn(5, 300)
            )
        }
    }

    suspend fun syncRoutines() {
        if (relayPool != null && signer != null) {
            StretchingSync.publishRoutinesMaster(
                appContext,
                relayPool,
                signer,
                repository.currentState(),
                keyManager.relayUrlsForKind30078Publish(),
            )
        }
    }

    suspend fun syncDailyLog(log: StretchDayLog) {
        if (relayPool != null && signer != null) {
            StretchingSync.publishDailyLog(
                appContext,
                relayPool,
                signer,
                log,
                keyManager.relayUrlsForKind30078Publish(),
            )
        }
    }

    LaunchedEffect(guidedRoutine) {
        val r = guidedRoutine ?: return@LaunchedEffect
        if (r.stretchIds.isEmpty()) {
            snackbarHostState.showSnackbar("Add stretches to this routine first")
            guidedRoutine = null
        }
    }

    guidedRoutine?.takeIf { it.stretchIds.isNotEmpty() }?.let { routine ->
        // Resolve once per launched routine. Keying on the live catalog would rebuild this list when a
        // relay catalog sync lands mid-session, which restarts the guided session from the intro.
        val entries = remember(routine.id, routine.stretchIds) {
            resolveStretchEntries(routine.stretchIds, repository)
        }
        val holdSec = routine.holdSecondsPerStretch.coerceIn(5, 300)
        StretchGuidedSessionOverlay(
            stretchEntries = entries,
            holdSeconds = holdSec,
            stretchGuidedTtsVoice = stretchGuidedTtsVoice,
            stretchSoundStyle = stretchSoundStyle,
            stretchGuidanceDetail = stretchGuidanceDetail,
            onFinished = { result ->
                scope.launch {
                    val today = LocalDate.now()
                    val workoutState = workoutRepository.currentState()
                    val workoutLaunch = workoutState.activeWorkoutMobilityLaunch()
                    val workoutLink = workoutLaunch?.let { launch ->
                        workoutState.activeRun?.linkFor(launch.segmentId, launch.itemId)
                    }
                    repository.logSession(
                        date = today,
                        routineId = routine.id,
                        routineName = routine.name,
                        stretchIds = result.stretchIds,
                        totalMinutes = result.totalMinutes,
                        workoutLink = workoutLink,
                    )
                    val log = repository.currentState().logFor(today)
                    val savedId = log?.sessions?.lastOrNull()?.id
                    if (workoutLaunch != null && !savedId.isNullOrBlank()) {
                        workoutRepository.completeLaunchedItem(
                            logDate = today.toString(),
                            entryId = savedId,
                            kind = WorkoutLoggedItemKind.MOBILITY,
                        )
                    }
                    when {
                        workoutLaunch != null -> onReturnToWorkoutRun(workoutLaunch.workoutId)
                        else -> snackbarHostState.showSnackbar("Logged stretching session")
                    }
                    launch {
                        log?.let { syncDailyLog(it) }
                    }
                }
                guidedRoutine = null
            },
            onCancel = {
                guidedRoutine = null
                scope.launch {
                    val workoutLaunch = workoutRepository.currentState().activeWorkoutMobilityLaunch()
                    if (workoutLaunch != null) {
                        onReturnToWorkoutRun(workoutLaunch.workoutId)
                    }
                }
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Stretching") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.DateRange, contentDescription = "Open log")
                    }
                },
                colors = ervTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                StretchTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(tab.name) }
                    )
                }
            }
            when (StretchTab.entries[activeTab]) {
                StretchTab.Stretches -> StretchesCatalogTab(repository = repository)
                StretchTab.Routines -> StretchRoutinesTab(
                    state = state,
                    repository = repository,
                    onAddRoutine = {
                        creatingRoutine = true
                        routineEditor = null
                    },
                    onEditRoutine = { routineEditor = it },
                    onDeleteRoutine = { id ->
                        scope.launch {
                            repository.deleteRoutine(id)
                            syncRoutines()
                            snackbarHostState.showSnackbar("Routine deleted")
                        }
                    },
                    onPlayRoutine = { guidedRoutine = it }
                )
            }
        }
    }

    if (creatingRoutine || routineEditor != null) {
        StretchRoutineEditorDialog(
            routine = routineEditor,
            creating = creatingRoutine,
            catalog = repository.catalog,
            onDismiss = {
                routineEditor = null
                creatingRoutine = false
            },
            onSave = { routine ->
                scope.launch {
                    if (routine.id == routineEditor?.id) {
                        repository.updateRoutine(routine)
                        snackbarHostState.showSnackbar("Routine updated")
                    } else {
                        repository.addRoutine(routine)
                        snackbarHostState.showSnackbar("Routine saved")
                    }
                    syncRoutines()
                }
                routineEditor = null
                creatingRoutine = false
            }
        )
    }
}

@Composable
private fun StretchesCatalogTab(repository: StretchingRepository) {
    var filter by remember { mutableStateOf("") }
    var selectedCategoryKey by remember { mutableStateOf<String?>(null) }
    val q = filter.trim().lowercase(Locale.getDefault())
    val locale = Locale.getDefault()
    fun entryCategoryNorm(e: StretchCatalogEntry) =
        e.category.trim().lowercase(locale).ifBlank { "other" }
    fun entryMatchesSearch(entry: StretchCatalogEntry): Boolean {
        if (q.isEmpty()) return true
        if (entry.name.lowercase(locale).contains(q)) return true
        if (entry.targetBodyParts.any { it.lowercase(locale).contains(q) }) return true
        if (entry.category.lowercase(locale).contains(q)) return true
        if (stretchCategoryDisplayLabel(entry.category).lowercase(locale).contains(q)) return true
        return false
    }
    val filtered = remember(repository.catalog, q, selectedCategoryKey) {
        repository.catalog.filter { entry ->
            val catOk = selectedCategoryKey == null || entryCategoryNorm(entry) == selectedCategoryKey
            catOk && entryMatchesSearch(entry)
        }
    }
    val grouped = remember(filtered) { filtered.groupedByCategory() }
    val categoriesForChips = remember(repository.catalog) {
        repository.catalog
            .map { entryCategoryNorm(it) }
            .distinct()
            .sorted()
    }
    var detailEntry by remember { mutableStateOf<StretchCatalogEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth(),
            label = { FieldLabel("Search stretches") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategoryKey == null,
                    onClick = { selectedCategoryKey = null },
                    label = { FieldLabel("All") }
                )
            }
            items(categoriesForChips, key = { it }) { cat ->
                FilterChip(
                    selected = selectedCategoryKey == cat,
                    onClick = {
                        selectedCategoryKey = if (selectedCategoryKey == cat) null else cat
                    },
                    label = { Text(stretchCategoryDisplayLabel(cat)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Text(
                "No stretches match your search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                grouped.forEach { (categoryKey, entries) ->
                    items(
                        listOf(categoryKey),
                        key = { "stretch_section_$it" }
                    ) {
                        Text(
                            stretchCategoryDisplayLabel(categoryKey),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailEntry = entry }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(titleCaseStretchLabel(entry.name), style = MaterialTheme.typography.titleMedium)
                                if (entry.requiresBothSides) {
                                    Text(
                                        "Left & right (2× hold time)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                if (entry.targetBodyParts.isNotEmpty()) {
                                    Text(
                                        entry.targetBodyParts.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { detailEntry = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(titleCaseStretchLabel(entry.name), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    stretchCategoryDisplayLabel(entry.category),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                if (entry.requiresBothSides) {
                    Text(
                        "Left & right: the guided player runs two timed holds (right, then left), each for your hold duration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (entry.targetBodyParts.isNotEmpty()) {
                    Text(
                        entry.targetBodyParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    entry.procedure.ifBlank { "No written steps for this stretch." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StretchRoutinesTab(
    state: StretchLibraryState,
    repository: StretchingRepository,
    onAddRoutine: () -> Unit,
    onEditRoutine: (StretchRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onPlayRoutine: (StretchRoutine) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.routines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No routines yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Create a named routine and add stretches from the catalog. Use Play for a guided hold timer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.routines, key = { it.id }) { routine ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                routineHoldSummaryLine(
                                    routine.stretchIds,
                                    repository.catalog,
                                    routine.holdSecondsPerStretch
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(
                                    onClick = { onPlayRoutine(routine) },
                                    enabled = routine.stretchIds.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play")
                                }
                                IconButton(onClick = { onEditRoutine(routine) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit routine")
                                }
                                IconButton(onClick = { onDeleteRoutine(routine.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete routine")
                                }
                            }
                        }
                    }
                }
            }
        }
        androidx.compose.material3.FloatingActionButton(
            onClick = onAddRoutine,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add routine")
        }
    }
}

@Composable
private fun StretchGuidedSessionOverlay(
    stretchEntries: List<StretchCatalogEntry>,
    holdSeconds: Int,
    stretchGuidedTtsVoice: StretchGuidedTtsVoice,
    stretchSoundStyle: StretchSoundStyle,
    stretchGuidanceDetail: StretchGuidanceDetail,
    onFinished: (GuidedStretchResult) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    /** True after [TextToSpeech] onInit runs (success or error). */
    var ttsInitSettled by remember { mutableStateOf(false) }
    /** Only [TextToSpeech.SUCCESS] means the engine is bound; otherwise speak/stop log "not bound". */
    var ttsEngineOk by remember { mutableStateOf(false) }
    val tts = remember(context) {
        val host = context.findActivity() ?: context
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(host) { status ->
            ttsEngineOk = (status == TextToSpeech.SUCCESS)
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TTS_LOG_TAG, "TTS onInit status=$status (expected SUCCESS=${TextToSpeech.SUCCESS})")
            }
            ttsInitSettled = true
        }
        engine
    }
    DisposableEffect(tts) {
        onDispose {
            try {
                tts.stop()
            } catch (_: Exception) {
            }
            try {
                tts.shutdown()
            } catch (_: Exception) {
            }
        }
    }
    LaunchedEffect(ttsInitSettled, ttsEngineOk, stretchGuidedTtsVoice, tts) {
        if (!ttsInitSettled || !ttsEngineOk) return@LaunchedEffect
        applyStretchingTtsLayerConfig(tts, stretchGuidedTtsVoice)
    }

    val soundStyleState = rememberUpdatedState(stretchSoundStyle)
    val guidanceDetailState = rememberUpdatedState(stretchGuidanceDetail)
    var voiceEnabled by remember { mutableStateOf(true) }
    val voiceEnabledState = rememberUpdatedState(voiceEnabled)
    val ttsEngineOkState = rememberUpdatedState(ttsEngineOk)

    var firstPrepActive by remember { mutableStateOf(true) }
    var phaseHold by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    /** Negative while waiting for first intro TTS to finish; then [FIRST_STRETCH_PREP_SECONDS] countdown. */
    var secondsLeft by remember { mutableIntStateOf(-1) }
    val steps = remember(stretchEntries) { stretchEntries.toGuidedSessionSteps() }

    val currentStep = steps.getOrNull(index)
    val nextStep = steps.getOrNull(index + 1)
    val firstStep = steps.firstOrNull()
    /** The step being held, or the one coming up during a get-ready or gap. */
    val focusIndex = if (phaseHold || firstPrepActive) index.coerceAtLeast(0) else index + 1

    /** Pause, Skip and Back: a jump restarts the session coroutine at [jumpTo] (null = from the intro). */
    var paused by remember { mutableStateOf(false) }
    val pausedState = rememberUpdatedState(paused)
    var jumpTo by remember { mutableStateOf<Int?>(null) }
    var runToken by remember { mutableIntStateOf(0) }
    var showEndDialog by remember { mutableStateOf(false) }
    var pausedBeforeEndDialog by remember { mutableStateOf(false) }
    /** Steps held for at least a second, and seconds spent (prep, holds, gaps) while not paused. */
    val heldStepIndexes = remember { mutableStateListOf<Int>() }
    var activeSeconds by remember { mutableIntStateOf(0) }

    val plannedMinutes = remember(steps, holdSeconds) { guidedSessionTotalMinutes(steps, holdSeconds) }
    fun result(): GuidedStretchResult? = guidedStretchResult(
        steps = steps,
        heldStepIndexes = heldStepIndexes.toSet(),
        routineStretchIds = stretchEntries.map { it.id },
        plannedMinutes = plannedMinutes,
        activeSeconds = activeSeconds,
    )

    fun jump(target: Int) {
        try {
            tts.stop()
        } catch (_: Exception) {
        }
        jumpTo = target
        runToken++
    }

    fun skip() {
        val target = guidedSkipTarget(focusIndex, steps.size)
        if (target == null) {
            // Skipping the last stretch ends the session; it logs what was held.
            val r = result()
            if (r != null) onFinished(r) else onCancel()
        } else {
            jump(target)
        }
    }

    fun back() {
        val intoHold = if (phaseHold) holdSeconds - secondsLeft else 0
        jump(guidedBackTarget(focusIndex, phaseHold, intoHold))
    }

    fun openEndDialog() {
        pausedBeforeEndDialog = paused
        paused = true
        try {
            tts.stop()
        } catch (_: Exception) {
        }
        showEndDialog = true
    }

    BackHandler { openEndDialog() }

    /** One second of session time; paused time does not count. */
    suspend fun waitOneSecond() {
        var ms = 0
        while (ms < 1000) {
            delay(100)
            if (!pausedState.value) ms += 100
        }
        activeSeconds++
    }

    LaunchedEffect(stretchEntries, holdSeconds, tts, ttsInitSettled, context, runToken) {
        if (!ttsInitSettled) return@LaunchedEffect
        val sessionSteps = steps
        if (stretchEntries.isEmpty() || sessionSteps.isEmpty()) {
            onFinished(GuidedStretchResult(emptyList(), plannedMinutes))
            return@LaunchedEffect
        }
        if (ttsEngineOkState.value) {
            applyStretchingTtsLayerConfig(tts, stretchGuidedTtsVoice)
            delay(150)
        }
        val start = jumpTo
        var i: Int
        if (start == null) {
            firstPrepActive = true
            phaseHold = false
            index = 0
            secondsLeft = -1
            if (voiceEnabledState.value && ttsEngineOkState.value) {
                speakAndAwaitUtterance(
                    context,
                    tts,
                    buildFirstStretchIntroSpeech(
                        stretchEntries[0],
                        guidedReadsProcedure(guidanceDetailState.value, sessionSteps, 0)
                    ),
                    FIRST_INTRO_UTTERANCE_ID,
                    ttsEngineOkState.value,
                    FIRST_INTRO_SPEAK_WAIT_MS
                )
            }
            secondsLeft = FIRST_STRETCH_PREP_SECONDS
            while (secondsLeft > 0) {
                waitOneSecond()
                secondsLeft--
            }
            firstPrepActive = false
            i = 0
        } else {
            // After Skip or Back: a short get-ready for the chosen stretch, announced like any other.
            firstPrepActive = false
            phaseHold = false
            i = start.coerceIn(0, sessionSteps.lastIndex)
            index = i - 1
            secondsLeft = TRANSITION_SECONDS_BILATERAL_SIDE
            val announce = if (voiceEnabledState.value && ttsEngineOkState.value) {
                async {
                    speakAndAwaitUtterance(
                        context,
                        tts,
                        buildNextStepSpeech(
                            sessionSteps[i],
                            guidedReadsProcedure(guidanceDetailState.value, sessionSteps, i)
                        ),
                        NEXT_STEP_UTTERANCE_ID,
                        ttsEngineOkState.value,
                        NEXT_STEP_SPEAK_WAIT_MS
                    )
                }
            } else {
                null
            }
            while (secondsLeft > 0) {
                waitOneSecond()
                secondsLeft--
            }
            announce?.await()
        }

        while (i < sessionSteps.size) {
            phaseHold = true
            index = i
            secondsLeft = holdSeconds
            StretchCuePlayer.play(context, soundStyleState.value, StretchCue.HOLD_START)
            while (secondsLeft > 0) {
                stretchCueForSecondLeft(soundStyleState.value, holdSeconds, secondsLeft)?.let { cue ->
                    StretchCuePlayer.play(context, soundStyleState.value, cue)
                }
                waitOneSecond()
                if (i !in heldStepIndexes) heldStepIndexes.add(i)
                secondsLeft--
            }
            StretchCuePlayer.play(context, soundStyleState.value, StretchCue.HOLD_END)
            if (i == sessionSteps.lastIndex) break
            phaseHold = false
            secondsLeft = transitionSecondsBetweenSteps(sessionSteps[i], sessionSteps[i + 1])
            // Announce the next stretch (name, side, and how to do it) while the transition counts down.
            // If the description outlasts the countdown, the next hold waits for speech to finish so
            // the instructions are never cut off by the start tone.
            val announce = if (voiceEnabledState.value && ttsEngineOkState.value) {
                async {
                    speakAndAwaitUtterance(
                        context,
                        tts,
                        buildNextStepSpeech(
                            sessionSteps[i + 1],
                            guidedReadsProcedure(guidanceDetailState.value, sessionSteps, i + 1)
                        ),
                        NEXT_STEP_UTTERANCE_ID,
                        ttsEngineOkState.value,
                        NEXT_STEP_SPEAK_WAIT_MS
                    )
                }
            } else {
                null
            }
            while (secondsLeft > 0) {
                waitOneSecond()
                secondsLeft--
            }
            announce?.await()
            i++
            index = i
        }
        if (voiceEnabledState.value && ttsEngineOkState.value) {
            speakAndAwaitUtterance(
                context,
                tts,
                "Stretching complete.",
                COMPLETE_UTTERANCE_ID,
                ttsEngineOkState.value,
                COMPLETE_SPEAK_WAIT_MS
            )
        }
        onFinished(result() ?: GuidedStretchResult(stretchEntries.map { it.id }, plannedMinutes))
    }

    // A calm, fixed palette (dusk blue into sage) rather than the app's accent colors.
    val ink = CalmGuidedInk
    val positions = remember(steps) { guidedEntryPositions(steps) }
    val entryCount = (positions.lastOrNull() ?: -1) + 1
    val focusPosition = positions.getOrNull(focusIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))) ?: 0
    val secondsRemaining = guidedSecondsRemaining(
        steps = steps,
        holdSeconds = holdSeconds,
        focusIndex = focusIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0)),
        inHold = phaseHold,
        secondsLeftInPhase = secondsLeft,
        gapSeconds = ::transitionSecondsBetweenSteps,
    )
    val shownStep = when {
        phaseHold -> currentStep
        firstPrepActive -> firstStep
        else -> nextStep
    }
    val phaseTotal = when {
        phaseHold -> holdSeconds
        firstPrepActive -> FIRST_STRETCH_PREP_SECONDS
        else -> currentStep?.let { c -> nextStep?.let { n -> transitionSecondsBetweenSteps(c, n) } }
            ?: TRANSITION_SECONDS_BILATERAL_SIDE
    }
    val ringTarget = if (secondsLeft < 0 || phaseTotal <= 0) 0f else
        ((phaseTotal - secondsLeft).toFloat() / phaseTotal).coerceIn(0f, 1f)
    val ringProgress by animateFloatAsState(
        targetValue = ringTarget,
        animationSpec = tween(durationMillis = if (ringTarget == 0f) 300 else 1000, easing = LinearEasing),
        label = "stretchRing"
    )
    // One slow breath: 4 s in, 6 s out. The ring's inner glow swells and settles with it.
    val breath = rememberInfiniteTransition(label = "stretchBreath")
    val breathMs by breath.animateFloat(
        initialValue = 0f,
        targetValue = BREATH_CYCLE_MS.toFloat(),
        animationSpec = infiniteRepeatable(tween(BREATH_CYCLE_MS, easing = LinearEasing)),
        label = "stretchBreathMs"
    )
    val breathingIn = breathMs < BREATH_IN_MS
    val breathScale = if (paused) 0.9f else breathScaleAt(breathMs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(CalmGuidedTop, CalmGuidedBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    phaseHold -> "Hold"
                    firstPrepActive -> "Settle In"
                    else -> "Next"
                },
                style = MaterialTheme.typography.titleMedium,
                color = ink.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(10.dp))
            if (entryCount in 2..14) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(entryCount) { k ->
                        Box(
                            Modifier
                                .size(if (k == focusPosition) 9.dp else 7.dp)
                                .background(
                                    color = when {
                                        k < focusPosition -> ink.copy(alpha = 0.55f)
                                        k == focusPosition -> CalmGuidedAccent
                                        else -> ink.copy(alpha = 0.18f)
                                    },
                                    shape = CircleShape
                                )
                        )
                    }
                }
            } else if (entryCount > 14) {
                Text(
                    "${focusPosition + 1} of $entryCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = ink.copy(alpha = 0.7f)
                )
            }
            if (secondsLeft >= 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    guidedTimeLeftLabel(secondsRemaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = ink.copy(alpha = 0.6f)
                )
            }

            Crossfade(
                targetState = Triple(phaseHold, firstPrepActive, shownStep),
                animationSpec = tween(700),
                label = "stretchText",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { (hold, _, step) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    if (step != null) {
                        Text(
                            titleCaseStretchLabel(step.entry.name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Light,
                            color = ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        guidedSideLabel(step.side)?.let { sideLabel ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                sideLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = CalmGuidedAccent,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            step.entry.procedure.ifBlank {
                                if (hold) "Breathe and hold the position." else "Ease into position when you're ready."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = ink.copy(alpha = 0.78f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    // Breathing glow inside the ring.
                    drawCircle(
                        color = ink.copy(alpha = if (phaseHold) 0.08f else 0.04f),
                        radius = (size.minDimension / 2f - stroke * 2.5f) * breathScale
                    )
                    drawArc(
                        color = ink.copy(alpha = 0.14f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = if (phaseHold) CalmGuidedAccent else ink.copy(alpha = 0.45f),
                        startAngle = -90f,
                        sweepAngle = 360f * ringProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (secondsLeft < 0) "—" else "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light,
                        color = ink.copy(alpha = if (paused) 0.55f else 0.95f)
                    )
                    val caption = when {
                        paused -> "Paused"
                        phaseHold -> if (breathingIn) "Breathe in" else "Breathe out"
                        else -> null
                    }
                    if (caption != null) {
                        Text(
                            caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ink.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = { back() }, enabled = secondsLeft >= 0) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous stretch",
                        tint = ink.copy(alpha = 0.8f),
                        modifier = Modifier.size(30.dp)
                    )
                }
                FilledIconButton(
                    onClick = {
                        paused = !paused
                        if (paused) {
                            try {
                                tts.stop()
                            } catch (_: Exception) {
                            }
                        }
                    },
                    enabled = secondsLeft >= 0,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = ink.copy(alpha = 0.14f),
                        contentColor = ink
                    ),
                    modifier = Modifier.size(68.dp)
                ) {
                    Icon(
                        if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (paused) "Resume" else "Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { skip() }, enabled = secondsLeft >= 0) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Skip stretch",
                        tint = ink.copy(alpha = 0.8f),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            TextButton(onClick = { openEndDialog() }) {
                Text("End session", color = ink.copy(alpha = 0.6f))
            }
        }
        if (showEndDialog) {
            val partial = result()
            AlertDialog(
                onDismissRequest = {
                    showEndDialog = false
                    paused = pausedBeforeEndDialog
                },
                title = { Text("End Session?") },
                text = {
                    Text(
                        if (partial != null) {
                            val n = partial.stretchIds.size
                            "You held $n ${if (n == 1) "stretch" else "stretches"} so far. Log them, or end without logging."
                        } else {
                            "Nothing has been held yet, so there is nothing to log."
                        }
                    )
                },
                confirmButton = {
                    if (partial != null) {
                        TextButton(onClick = {
                            showEndDialog = false
                            onFinished(partial)
                        }) { Text("Log and End") }
                    } else {
                        TextButton(onClick = {
                            showEndDialog = false
                            onCancel()
                        }) { Text("End") }
                    }
                },
                dismissButton = {
                    Row {
                        if (partial != null) {
                            TextButton(onClick = {
                                showEndDialog = false
                                onCancel()
                            }) { Text("Discard") }
                        }
                        TextButton(onClick = {
                            showEndDialog = false
                            paused = pausedBeforeEndDialog
                        }) { Text("Keep Going") }
                    }
                }
            )
        }
        IconButton(
            onClick = {
                voiceEnabled = !voiceEnabled
                if (!voiceEnabled) tts.stop()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = if (voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (voiceEnabled) "Mute next-stretch voice" else "Unmute next-stretch voice",
                tint = CalmGuidedInk.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun StretchRoutineEditorDialog(
    routine: StretchRoutine?,
    creating: Boolean,
    catalog: List<StretchCatalogEntry>,
    onDismiss: () -> Unit,
    onSave: (StretchRoutine) -> Unit
) {
    var name by remember(routine?.id, creating) {
        mutableStateOf(routine?.name.orEmpty().ifBlank { "My routine" })
    }
    var holdSecText by remember(routine?.id, creating) {
        mutableStateOf((routine?.holdSecondsPerStretch ?: 30).toString())
    }
    val orderedIds = remember(routine?.id, creating) {
        mutableStateListOf<String>().apply { addAll(routine?.stretchIds.orEmpty()) }
    }
    var showPicker by remember { mutableStateOf(false) }
    var selectedStretchCategory by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showPicker) {
        if (showPicker) selectedStretchCategory = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New routine" else "Edit routine") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { FieldLabel("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = holdSecText,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) holdSecText = it },
                    label = { FieldLabel("Hold seconds per stretch") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val holdForPlan = holdSecText.toIntOrNull()?.coerceIn(5, 300) ?: 30
                val planIds = orderedIds.toList()
                if (planIds.isNotEmpty()) {
                    Text(
                        routineHoldSummaryLine(planIds, catalog, holdForPlan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FormSectionLabel("Stretches (in order)")
                if (orderedIds.isEmpty()) {
                    Text(
                        "Tap Add stretch to build your routine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    orderedIds.forEachIndexed { idx, id ->
                        val entry = catalog.firstOrNull { it.id == id }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    titleCaseStretchLabel(entry?.name ?: id),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (entry?.requiresBothSides == true) {
                                    Text(
                                        "Left & right — 2× hold",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (idx > 0) {
                                        val t = orderedIds[idx - 1]
                                        orderedIds[idx - 1] = orderedIds[idx]
                                        orderedIds[idx] = t
                                    }
                                },
                                enabled = idx > 0
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = {
                                    if (idx < orderedIds.lastIndex) {
                                        val t = orderedIds[idx + 1]
                                        orderedIds[idx + 1] = orderedIds[idx]
                                        orderedIds[idx] = t
                                    }
                                },
                                enabled = idx < orderedIds.lastIndex
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                            }
                            IconButton(onClick = { orderedIds.removeAt(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
                TextButton(onClick = { showPicker = true }) { Text("Add stretch") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hold = holdSecText.toIntOrNull()?.coerceIn(5, 300) ?: 30
                    onSave(
                        (routine ?: StretchRoutine(name = name.trim())).copy(
                            name = name.trim().ifBlank { "Routine" },
                            stretchIds = orderedIds.toList(),
                            holdSecondsPerStretch = hold
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPicker) {
        // Use a platform Dialog so the picker stacks above the routine AlertDialog.
        // ModalBottomSheet nested under AlertDialog renders behind and is not tappable.
        val excludeIds = orderedIds.toSet()
        val stretchChoices = remember(catalog, excludeIds) {
            catalog.filter { it.id !in excludeIds }
        }
        val groupedChoices = remember(stretchChoices) { stretchChoices.groupedByCategory() }
        val pickerScroll = rememberScrollState()
        Dialog(
            onDismissRequest = { showPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.78f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (val cat = selectedStretchCategory) {
                                null -> Text(
                                    "Muscle group",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                else -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    IconButton(onClick = { selectedStretchCategory = null }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Change muscle group"
                                        )
                                    }
                                    Text(
                                        text = stretchCategoryDisplayLabel(cat),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                            TextButton(onClick = { showPicker = false }) {
                                Text("Close")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(pickerScroll)
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when {
                                stretchChoices.isEmpty() -> {
                                    Text(
                                        "No more stretches to add.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                groupedChoices.isEmpty() -> {
                                    Text(
                                        "No more stretches to add.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                selectedStretchCategory == null -> {
                                    Text(
                                        "Choose a muscle group, then select a stretch.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    groupedChoices.forEach { (categoryKey, list) ->
                                        TextButton(
                                            onClick = { selectedStretchCategory = categoryKey },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "${stretchCategoryDisplayLabel(categoryKey)} (${list.size})",
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    val inGroup = groupedChoices
                                        .firstOrNull { it.first == selectedStretchCategory }
                                        ?.second
                                        .orEmpty()
                                    if (inGroup.isEmpty()) {
                                        Text(
                                            "No stretches in this group.",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    } else {
                                        inGroup.forEach { entry ->
                                            TextButton(
                                                onClick = {
                                                    orderedIds.add(entry.id)
                                                    showPicker = false
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalAlignment = Alignment.Start
                                                ) {
                                                    Text(titleCaseStretchLabel(entry.name))
                                                    if (entry.requiresBothSides) {
                                                        Text(
                                                            "Left & right (2× hold)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StretchingLogScreen(
    repository: StretchingRepository,
    state: StretchLibraryState,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit
) {
    var dateFilter by remember { mutableStateOf<SectionLogDateFilter>(SectionLogDateFilter.AllHistory) }
    var showCalendar by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DatedStretchSession?>(null) }
    val datedEntries = remember(state, dateFilter) {
        state.datedStretchSessionsForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithStretchActivity(state) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyManager = LocalKeyManager.current
    val logAppContext = LocalContext.current.applicationContext

    suspend fun syncDailyLogForDate(date: LocalDate) {
        if (relayPool != null && signer != null) {
            repository.currentState().logFor(date)?.let { log ->
                StretchingSync.publishDailyLog(
                    logAppContext,
                    relayPool,
                    signer,
                    log,
                    keyManager.relayUrlsForKind30078Publish(),
                )
            }
        }
    }

    pendingDelete?.let { dated ->
        val session = dated.session
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove session?") },
            text = {
                Text(
                    "This removes the entry from this device and updates your synced day log.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = session.id
                        val logDate = dated.logDate
                        pendingDelete = null
                        scope.launch {
                            repository.deleteSession(logDate, id)
                            snackbarHostState.showSnackbar("Session removed")
                            launch { syncDailyLogForDate(logDate) }
                        }
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Stretching log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = ervTopAppBarColors()
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SectionLogFilterBar(
                filter = dateFilter,
                onOpenCalendar = { showCalendar = true },
                onClearFilter = { dateFilter = SectionLogDateFilter.AllHistory }
            )
            if (datedEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (dateFilter) {
                                SectionLogDateFilter.AllHistory -> "No stretching logged yet."
                                is SectionLogDateFilter.SingleDay -> "No stretching logged for this date."
                                is SectionLogDateFilter.DateRange -> "No stretching logged in this date range."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Complete a guided routine from the Stretching tab to log a session.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Newest first. Tap delete to remove from that day.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(datedEntries, key = { "${it.logDate}-${it.session.id}" }) { dated ->
                        val session = dated.session
                        val title = session.routineName ?: "Stretch session"
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (showLogDateOnCards) {
                                        Text(
                                            dated.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${session.totalMinutes} min · ${session.stretchIds.size} stretches",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatStretchLogTime(session.loggedAtEpochSeconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { pendingDelete = dated }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete session")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalendar) {
        SectionLogCalendarSheet(
            filter = dateFilter,
            onDismiss = { showCalendar = false },
            datesWithActivity = datesWithActivity,
            onApplyFilter = { dateFilter = it }
        )
    }
}

private fun formatStretchLogTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown time"
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}
