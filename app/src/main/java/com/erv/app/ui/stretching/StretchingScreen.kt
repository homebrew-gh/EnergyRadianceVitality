@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.stretching

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme
import com.erv.app.data.StretchGuidedTtsVoice
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
import com.erv.app.unifiedroutines.UnifiedRoutineBlockType
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.unifiedroutines.linkFor
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.stretching.applyStretchGuidedTtsVoice
import com.erv.app.stretching.DatedStretchSession
import com.erv.app.SectionLogDateFilter
import com.erv.app.stretching.datedStretchSessionsForSectionLog
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.datesWithStretchActivity
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

/**
 * Plays a short tone on the music stream (follows media volume). [ToneGenerator.release] is posted
 * after the tone duration; releasing immediately after [ToneGenerator.startTone] often cancels playback.
 */
private fun playTone(tone: Int, durationMs: Int, volumePercent: Int) {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, volumePercent.coerceIn(1, 100))
        tg.startTone(tone, durationMs)
        val h = Handler(Looper.getMainLooper())
        h.postDelayed({
            try {
                tg.release()
            } catch (_: Exception) {
            }
        }, durationMs.toLong() + 50L)
    } catch (_: Exception) {
    }
}

/** Short cue when a hold begins. */
private fun playStretchStartTone() {
    playTone(ToneGenerator.TONE_PROP_ACK, 140, 85)
}

/** Cue when a hold ends (before transition or session end). */
private fun playStretchEndTone() {
    playTone(ToneGenerator.TONE_PROP_PROMPT, 220, 88)
}

/** Soft tick for the last five seconds of a hold (one per second while 5…1 remain). */
private fun playStretchCountdownTick() {
    playTone(ToneGenerator.TONE_PROP_BEEP, 60, 55)
}

private const val TTS_LOG_TAG = "StretchingTts"

private fun ttsSpeakParams(): Bundle = Bundle().apply {
    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
}

/**
 * Only call when [engineReady] (init status was [TextToSpeech.SUCCESS]); otherwise speak/stop log "not bound".
 * Retries simpler [speak] shapes if needed.
 */
@Suppress("DEPRECATION")
private fun runStretchTtsSpeak(
    tts: TextToSpeech,
    text: String,
    utteranceId: String,
    engineReady: Boolean
): Int {
    if (!engineReady) return TextToSpeech.ERROR
    try {
        tts.stop()
    } catch (_: Exception) {
    }
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

private fun speakNextGuidedStep(
    context: Context,
    tts: TextToSpeech?,
    step: GuidedStretchStep,
    enabled: Boolean,
    engineReady: Boolean
) {
    if (!enabled || tts == null || !engineReady) return
    val name = titleCaseStretchLabel(step.entry.name)
    val label = when (step.side) {
        null -> name
        StretchSide.RIGHT -> "$name, right side"
        StretchSide.LEFT -> "$name, left side"
    }
    requestTtsAudioFocusForPlayback(context)
    runStretchTtsSpeak(tts, "Next: $label", "stretchNext", engineReady)
}

private fun guidedSideLabel(side: StretchSide?): String? = when (side) {
    null -> null
    StretchSide.RIGHT -> "Right side"
    StretchSide.LEFT -> "Left side"
}

private fun buildFirstStretchIntroSpeech(entry: StretchCatalogEntry): String {
    val name = entry.name.trim().ifBlank { "this stretch" }
    val bilateralNote = if (entry.requiresBothSides) {
        " We will do the right side first, then the left."
    } else {
        ""
    }
    return "Welcome to your routine. Your first stretch is the $name.$bilateralNote The hold begins after this countdown."
}

private const val FIRST_INTRO_UTTERANCE_ID = "stretchFirst"
private const val FIRST_INTRO_SPEAK_WAIT_MS = 120_000L

private const val COMPLETE_UTTERANCE_ID = "stretchComplete"
private const val COMPLETE_SPEAK_WAIT_MS = 60_000L

/**
 * Plays the first-stretch intro and suspends until the engine reports the utterance finished
 * (or failure / timeout), so the countdown can start after speech.
 */
private suspend fun awaitFirstStretchIntroUtterance(
    context: Context,
    tts: TextToSpeech,
    entry: StretchCatalogEntry,
    engineReady: Boolean
) {
    if (!engineReady) return
    val text = buildFirstStretchIntroSpeech(entry)
    if (text.isBlank()) return
    withTimeoutOrNull(FIRST_INTRO_SPEAK_WAIT_MS) {
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
                    if (utteranceId == FIRST_INTRO_UTTERANCE_ID) finishOnce()
                }
                @Deprecated("Deprecated in Android UtteranceProgressListener; kept for older API levels.")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == FIRST_INTRO_UTTERANCE_ID) finishOnce()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == FIRST_INTRO_UTTERANCE_ID) finishOnce()
                }
            }
            tts.setOnUtteranceProgressListener(listener)
            requestTtsAudioFocusForPlayback(context)
            val code = runStretchTtsSpeak(tts, text, FIRST_INTRO_UTTERANCE_ID, engineReady)
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

private suspend fun awaitStretchingCompleteUtterance(
    context: Context,
    tts: TextToSpeech,
    engineReady: Boolean
) {
    if (!engineReady) return
    withTimeoutOrNull(COMPLETE_SPEAK_WAIT_MS) {
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
                    if (utteranceId == COMPLETE_UTTERANCE_ID) finishOnce()
                }
                @Deprecated("Deprecated in Android UtteranceProgressListener; kept for older API levels.")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == COMPLETE_UTTERANCE_ID) finishOnce()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == COMPLETE_UTTERANCE_ID) finishOnce()
                }
            }
            tts.setOnUtteranceProgressListener(listener)
            requestTtsAudioFocusForPlayback(context)
            val code = runStretchTtsSpeak(tts, "Stretching complete.", COMPLETE_UTTERANCE_ID, engineReady)
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
    tts.setSpeechRate(0.95f)
    tts.setPitch(1f)
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
    unifiedRoutineRepository: UnifiedRoutineRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit
) {
    val stretchGuidedTtsVoice by userPreferences.stretchGuidedTtsVoice.collectAsState(
        initial = StretchGuidedTtsVoice.SYSTEM_DEFAULT
    )
    val state by repository.state.collectAsState(initial = StretchLibraryState())
    val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
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
            snackbarHostState.showSnackbar("Stretch routine not found — open Programs to fix this block.")
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

    val darkTheme = isSystemInDarkTheme()
    val headerColor =
        if (darkTheme) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.primary
    val onHeader =
        if (darkTheme) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onPrimary

    LaunchedEffect(guidedRoutine) {
        val r = guidedRoutine ?: return@LaunchedEffect
        if (r.stretchIds.isEmpty()) {
            snackbarHostState.showSnackbar("Add stretches to this routine first")
            guidedRoutine = null
        }
    }

    guidedRoutine?.takeIf { it.stretchIds.isNotEmpty() }?.let { routine ->
        val entries = remember(routine.id, routine.stretchIds, repository.catalog) {
            resolveStretchEntries(routine.stretchIds, repository)
        }
        val holdSec = routine.holdSecondsPerStretch.coerceIn(5, 300)
        StretchGuidedSessionOverlay(
            stretchEntries = entries,
            holdSeconds = holdSec,
            stretchGuidedTtsVoice = stretchGuidedTtsVoice,
            onFinished = { totalMinutes ->
                scope.launch {
                    val today = LocalDate.now()
                    val activeUnifiedSession = unifiedState.activeSession
                    val activeUnifiedBlockId = activeUnifiedSession?.lastLaunchedBlockId?.takeIf { blockId ->
                        unifiedState
                            .routineById(activeUnifiedSession.routineId)
                            ?.blocks
                            ?.firstOrNull { it.id == blockId }
                            ?.type == UnifiedRoutineBlockType.STRETCH
                    }
                    val unifiedLink = if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                        unifiedState.sessionById(activeUnifiedSession.sessionId)?.linkFor(activeUnifiedBlockId)
                    } else {
                        null
                    }
                    repository.logSession(
                        date = today,
                        routineId = routine.id,
                        routineName = routine.name,
                        stretchIds = routine.stretchIds,
                        totalMinutes = totalMinutes,
                        unifiedLink = unifiedLink
                    )
                    val log = repository.currentState().logFor(today)
                    if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                        val savedId = log?.sessions?.lastOrNull()?.id
                        if (!savedId.isNullOrBlank()) {
                            unifiedRoutineRepository.attachLoggedBlock(
                                routineId = activeUnifiedSession.routineId,
                                blockId = activeUnifiedBlockId,
                                logDate = today.toString(),
                                entryId = savedId
                            )
                        }
                    }
                    log?.let { syncDailyLog(it) }
                    if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                        onBack()
                    } else {
                        snackbarHostState.showSnackbar("Logged stretching session")
                    }
                }
                guidedRoutine = null
            },
            onCancel = {
                val activeUnifiedSession = unifiedState.activeSession
                val activeUnifiedBlockId = activeUnifiedSession?.lastLaunchedBlockId?.takeIf { blockId ->
                    unifiedState
                        .routineById(activeUnifiedSession.routineId)
                        ?.blocks
                        ?.firstOrNull { it.id == blockId }
                        ?.type == UnifiedRoutineBlockType.STRETCH
                }
                guidedRoutine = null
                if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                    onBack()
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = onHeader,
                    actionIconContentColor = onHeader,
                    navigationIconContentColor = onHeader
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = activeTab) {
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
            label = { Text("Search stretches") },
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
                    label = { Text("All") }
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
    onFinished: (totalMinutes: Int) -> Unit,
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

    LaunchedEffect(stretchEntries, holdSeconds, tts, ttsInitSettled, context) {
        if (!ttsInitSettled) return@LaunchedEffect
        val sessionSteps = stretchEntries.toGuidedSessionSteps()
        if (stretchEntries.isEmpty() || sessionSteps.isEmpty()) {
            onFinished(guidedSessionTotalMinutes(sessionSteps, holdSeconds))
            return@LaunchedEffect
        }
        if (ttsEngineOkState.value) {
            applyStretchingTtsLayerConfig(tts, stretchGuidedTtsVoice)
            delay(150)
        }
        firstPrepActive = true
        phaseHold = false
        index = 0
        secondsLeft = -1
        if (voiceEnabledState.value && ttsEngineOkState.value) {
            awaitFirstStretchIntroUtterance(
                context,
                tts,
                stretchEntries[0],
                ttsEngineOkState.value
            )
        }
        secondsLeft = FIRST_STRETCH_PREP_SECONDS
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        firstPrepActive = false

        var i = 0
        while (i < sessionSteps.size) {
            phaseHold = true
            index = i
            secondsLeft = holdSeconds
            playStretchStartTone()
            while (secondsLeft > 0) {
                if (secondsLeft in 1..minOf(5, holdSeconds)) {
                    playStretchCountdownTick()
                }
                delay(1000)
                secondsLeft--
            }
            playStretchEndTone()
            if (i == sessionSteps.lastIndex) break
            phaseHold = false
            secondsLeft = transitionSecondsBetweenSteps(sessionSteps[i], sessionSteps[i + 1])
            speakNextGuidedStep(
                context,
                tts,
                sessionSteps[i + 1],
                voiceEnabledState.value,
                ttsEngineOkState.value
            )
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            i++
            index = i
        }
        if (voiceEnabledState.value && ttsEngineOkState.value) {
            awaitStretchingCompleteUtterance(context, tts, ttsEngineOkState.value)
        }
        onFinished(guidedSessionTotalMinutes(sessionSteps, holdSeconds))
    }

    val gradientBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val gradientTop = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(gradientTop, gradientBottom))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (phaseHold) "Hold" else "Get ready",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.95f)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (phaseHold && currentStep != null) {
                    Text(
                        titleCaseStretchLabel(currentStep.entry.name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    guidedSideLabel(currentStep.side)?.let { sideLabel ->
                        Text(
                            sideLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }
                    Text(
                        currentStep.entry.procedure.ifBlank { "Breathe and hold the position." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!phaseHold && firstPrepActive && firstStep != null) {
                    Text(
                        "First stretch",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        titleCaseStretchLabel(firstStep.entry.name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    guidedSideLabel(firstStep.side)?.let { sideLabel ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            sideLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        firstStep.entry.procedure.ifBlank { "Get into position; the hold starts when the timer switches to Hold." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!phaseHold) {
                    Text(
                        "Next",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (nextStep != null) {
                        Text(
                            titleCaseStretchLabel(nextStep.entry.name),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        guidedSideLabel(nextStep.side)?.let { sideLabel ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                sideLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Text(
                text = if (secondsLeft < 0) "—" else "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(Color.White)
                )
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("End session")
            }
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
                tint = Color.White.copy(alpha = 0.95f)
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
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = holdSecText,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) holdSecText = it },
                    label = { Text("Hold seconds per stretch") },
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
                Text("Stretches (in order)", style = MaterialTheme.typography.labelLarge)
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
    val darkTheme = isSystemInDarkTheme()
    val headerColor =
        if (darkTheme) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.primary
    val onHeader =
        if (darkTheme) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onPrimary
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
                            syncDailyLogForDate(logDate)
                            snackbarHostState.showSnackbar("Session removed")
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerColor,
                    titleContentColor = onHeader,
                    navigationIconContentColor = onHeader
                )
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
