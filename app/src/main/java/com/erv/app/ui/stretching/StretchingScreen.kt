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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.erv.app.nostr.RelayPool
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.StretchDayLog
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchRoutine
import com.erv.app.stretching.StretchSession
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.StretchingSync
import com.erv.app.stretching.applyStretchGuidedTtsVoice
import com.erv.app.stretching.chronologicalStretchLogFor
import com.erv.app.ui.dashboard.CalendarPopup
import com.erv.app.ui.dashboard.DateNavigator
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

private const val TRANSITION_SECONDS = 5

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
        @Suppress("DEPRECATION")
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

private fun speakNextStretchName(
    context: Context,
    tts: TextToSpeech?,
    name: String,
    enabled: Boolean,
    engineReady: Boolean
) {
    if (!enabled || name.isBlank() || tts == null || !engineReady) return
    requestTtsAudioFocusForPlayback(context)
    runStretchTtsSpeak(tts, "Next: $name", "stretchNext", engineReady)
}

private fun buildFirstStretchIntroSpeech(entry: StretchCatalogEntry): String {
    val name = entry.name.trim().ifBlank { "this stretch" }
    return "Welcome to your routine. Your first stretch is the $name. The hold begins after this countdown."
}

private const val FIRST_INTRO_UTTERANCE_ID = "stretchFirst"
private const val FIRST_INTRO_SPEAK_WAIT_MS = 120_000L

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

private fun guidedSessionTotalMinutes(nStretches: Int, holdSeconds: Int): Int {
    if (nStretches <= 0) return 0
    val holdTotal = nStretches * holdSeconds
    val transitions = max(0, nStretches - 1) * TRANSITION_SECONDS
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
                procedure = "This stretch is no longer in the catalog."
            )
    }

@Composable
fun StretchingCategoryScreen(
    repository: StretchingRepository,
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var guidedRoutine by remember { mutableStateOf<StretchRoutine?>(null) }
    var routineEditor by remember { mutableStateOf<StretchRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }

    suspend fun syncRoutines() {
        if (relayPool != null && signer != null) {
            StretchingSync.publishRoutinesMaster(relayPool, signer, repository.currentState())
        }
    }

    suspend fun syncDailyLog(log: StretchDayLog) {
        if (relayPool != null && signer != null) {
            StretchingSync.publishDailyLog(relayPool, signer, log)
        }
    }

    LaunchedEffect(relayPool, signer?.publicKey) {
        if (relayPool != null && signer != null) {
            StretchingSync.fetchFromNetwork(relayPool, signer, signer.publicKey)?.let { remote ->
                repository.replaceAll(remote)
            }
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
                    repository.logSession(
                        date = today,
                        routineId = routine.id,
                        routineName = routine.name,
                        stretchIds = routine.stretchIds,
                        totalMinutes = totalMinutes
                    )
                    repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                    snackbarHostState.showSnackbar("Logged stretching session")
                }
                guidedRoutine = null
            },
            onCancel = { guidedRoutine = null }
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
    val q = filter.trim().lowercase(Locale.getDefault())
    val filtered = remember(repository.catalog, q) {
        if (q.isEmpty()) repository.catalog
        else repository.catalog.filter { entry ->
            entry.name.lowercase(Locale.getDefault()).contains(q) ||
                entry.targetBodyParts.any { it.lowercase(Locale.getDefault()).contains(q) }
        }
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
        Spacer(Modifier.height(12.dp))
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
                items(filtered, key = { it.id }) { entry ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailEntry = entry }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(titleCaseStretchLabel(entry.name), style = MaterialTheme.typography.titleMedium)
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
                                "${routine.stretchIds.size} stretches · ${routine.holdSecondsPerStretch}s hold each",
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
    val totalMinutes = remember(stretchEntries.size, holdSeconds) {
        guidedSessionTotalMinutes(stretchEntries.size, holdSeconds)
    }

    val current = stretchEntries.getOrNull(index)
    val next = stretchEntries.getOrNull(index + 1)
    val firstStretch = stretchEntries.firstOrNull()

    LaunchedEffect(stretchEntries, holdSeconds, tts, ttsInitSettled, context) {
        if (!ttsInitSettled) return@LaunchedEffect
        if (stretchEntries.isEmpty()) {
            onFinished(totalMinutes)
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
        while (i < stretchEntries.size) {
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
            if (i == stretchEntries.lastIndex) break
            phaseHold = false
            secondsLeft = TRANSITION_SECONDS
            val nextName = stretchEntries.getOrNull(i + 1)?.name.orEmpty()
            speakNextStretchName(context, tts, nextName, voiceEnabledState.value, ttsEngineOkState.value)
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            i++
            index = i
        }
        onFinished(totalMinutes)
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
                if (phaseHold && current != null) {
                    Text(
                        titleCaseStretchLabel(current.name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    Text(
                        current.procedure.ifBlank { "Breathe and hold the position." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (!phaseHold && firstPrepActive && firstStretch != null) {
                    Text(
                        "First stretch",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        titleCaseStretchLabel(firstStretch.name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        firstStretch.procedure.ifBlank { "Get into position; the hold starts when the timer switches to Hold." },
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
                    Text(
                        titleCaseStretchLabel(next?.name.orEmpty()),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                imageVector = if (voiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
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
                            Text(
                                "Add a stretch",
                                style = MaterialTheme.typography.titleLarge
                            )
                            TextButton(onClick = { showPicker = false }) {
                                Text("Close")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(catalog, key = { it.id }) { entry ->
                                TextButton(
                                    onClick = {
                                        if (!orderedIds.contains(entry.id)) orderedIds.add(entry.id)
                                        showPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(titleCaseStretchLabel(entry.name), modifier = Modifier.fillMaxWidth())
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
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCalendar by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StretchSession?>(null) }
    val entries = remember(state, selectedDate) { state.chronologicalStretchLogFor(selectedDate) }
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

    suspend fun syncDailyLogForSelected() {
        if (relayPool != null && signer != null) {
            repository.currentState().logFor(selectedDate)?.let { log ->
                StretchingSync.publishDailyLog(relayPool, signer, log)
            }
        }
    }

    pendingDelete?.let { session ->
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
                        pendingDelete = null
                        scope.launch {
                            repository.deleteSession(selectedDate, id)
                            syncDailyLogForSelected()
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
            DateNavigator(
                selectedDate = selectedDate,
                onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                onNextDay = { selectedDate = selectedDate.plusDays(1) },
                onPreviousWeek = { selectedDate = selectedDate.minusWeeks(1) },
                onNextWeek = { selectedDate = selectedDate.plusWeeks(1) },
                onTodayClick = { selectedDate = LocalDate.now() },
                onCalendarClick = { showCalendar = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No stretching logged for this date.",
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
                    items(entries, key = { it.id }) { session ->
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
                                IconButton(onClick = { pendingDelete = session }) {
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
        CalendarPopup(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it; showCalendar = false },
            onDismiss = { showCalendar = false },
            datesWithActivity = datesWithActivity
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
