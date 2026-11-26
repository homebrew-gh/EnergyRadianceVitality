@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.heatcold

import com.erv.app.ui.theme.ervSessionTopAppBarColors
import com.erv.app.ui.theme.ervTopAppBarColors
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.erv.app.heatcold.HeatColdLastSettings
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.heatcold.HeatColdModeTotals
import com.erv.app.heatcold.HeatColdWeekSummary
import com.erv.app.heatcold.convertTemperature
import com.erv.app.heatcold.weekSummary
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.heatcold.HeatColdSession
import com.erv.app.heatcold.HeatColdSync
import com.erv.app.data.TemperatureUnit
import com.erv.app.heatcold.HeatColdTimelineEntry
import com.erv.app.heatcold.formatDurationSeconds
import com.erv.app.heatcold.formatTemp
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.SectionLogDateFilter
import com.erv.app.data.UserPreferences
import com.erv.app.programs.decodeHeatColdLaunch
import com.erv.app.heatcold.displayName
import com.erv.app.heatcold.heatColdTimelineForSectionLog
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.datesWithHeatColdActivity
import com.erv.app.ui.theme.ErvColdDark
import com.erv.app.ui.theme.ErvColdGlow
import com.erv.app.ui.theme.ErvColdMid
import com.erv.app.ui.theme.ErvDarkColdDark
import com.erv.app.ui.theme.ErvDarkColdGlow
import com.erv.app.ui.theme.ErvDarkColdMid
import com.erv.app.ui.theme.ErvSessionRed
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.ui.components.CompactIntWheel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.ui.wellness.WellnessTimerController
import com.erv.app.ui.wellness.WellnessTimerFullScreen
import com.erv.app.ui.wellness.WellnessTimerKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Sauna minutes: 1 to 60. */
private val SAUNA_MINUTE_OPTIONS = (1..60).toList()

/** Cold seconds: 15 s steps to 5 min, 30 s steps to 10 min, then whole minutes to 20 min. */
private val COLD_SECOND_OPTIONS = (15..300 step 15).toList() + (330..600 step 30).toList() + (660..1200 step 60).toList()

private val SAUNA_TEMP_FAHRENHEIT = (100..230).toList()
private val SAUNA_TEMP_CELSIUS = (38..110).toList()
private val COLD_TEMP_FAHRENHEIT = (32..70).toList()
private val COLD_TEMP_CELSIUS = (0..21).toList()

private val CONTRAST_ROUND_OPTIONS = (1..6).toList()
private val SWITCH_SECOND_OPTIONS = listOf(0, 15, 30, 45, 60, 90, 120)

private enum class HeatColdTab(val label: String) { SAUNA("Sauna"), COLD_PLUNGE("Cold Plunge"), CONTRAST("Contrast") }

private fun List<Int>.nearest(value: Int): Int = minByOrNull { abs(it - value) } ?: value

private fun saunaTempOptions(unit: TemperatureUnit) =
    if (unit == TemperatureUnit.FAHRENHEIT) SAUNA_TEMP_FAHRENHEIT else SAUNA_TEMP_CELSIUS

private fun coldTempOptions(unit: TemperatureUnit) =
    if (unit == TemperatureUnit.FAHRENHEIT) COLD_TEMP_FAHRENHEIT else COLD_TEMP_CELSIUS

private fun tempSuffix(unit: TemperatureUnit) = if (unit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"

@Composable
fun HeatColdCategoryScreen(
    initialMode: HeatColdMode,
    repository: HeatColdRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit
) {
    // The wheels read their start value once, so wait for the saved settings and the unit.
    val saved by produceState<HeatColdLastSettings?>(initialValue = null, repository) {
        value = repository.currentLastSettings()
    }
    val unit by produceState<TemperatureUnit?>(initialValue = null, userPreferences) {
        userPreferences.temperatureUnit.collect { value = it }
    }
    val loaded = saved
    val tempUnit = unit
    if (loaded == null || tempUnit == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    key(tempUnit) {
        HeatColdCategoryContent(
            initialMode = initialMode,
            saved = loaded,
            tempUnit = tempUnit,
            repository = repository,
            userPreferences = userPreferences,
            relayPool = relayPool,
            signer = signer,
            onBack = onBack,
            onOpenLog = onOpenLog
        )
    }
}

@Composable
private fun HeatColdCategoryContent(
    initialMode: HeatColdMode,
    saved: HeatColdLastSettings,
    tempUnit: TemperatureUnit,
    repository: HeatColdRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    // Match Light therapy / app red accents (not brown-orange sauna-only hues).
    val hotDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val hotMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val hotGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val coldDark = if (darkTheme) ErvDarkColdDark else ErvColdDark
    val coldMid = if (darkTheme) ErvDarkColdMid else ErvColdMid
    val coldGlow = if (darkTheme) ErvDarkColdGlow else ErvColdGlow

    val savedTab = runCatching { HeatColdTab.valueOf(saved.lastTab) }.getOrNull()
    var tab by rememberSaveable {
        mutableStateOf(
            savedTab ?: if (initialMode == HeatColdMode.COLD_PLUNGE) HeatColdTab.COLD_PLUNGE else HeatColdTab.SAUNA
        )
    }
    var saunaMinutes by rememberSaveable { mutableIntStateOf(SAUNA_MINUTE_OPTIONS.nearest(saved.saunaMinutes)) }
    var coldSeconds by rememberSaveable { mutableIntStateOf(COLD_SECOND_OPTIONS.nearest(saved.coldSeconds)) }
    var saunaTemp by rememberSaveable {
        mutableIntStateOf(saunaTempOptions(tempUnit).nearest(convertTemperature(saved.saunaTempF, TemperatureUnit.FAHRENHEIT, tempUnit)))
    }
    var coldTemp by rememberSaveable {
        mutableIntStateOf(coldTempOptions(tempUnit).nearest(convertTemperature(saved.coldTempF, TemperatureUnit.FAHRENHEIT, tempUnit)))
    }
    var contrastRounds by rememberSaveable { mutableIntStateOf(CONTRAST_ROUND_OPTIONS.nearest(saved.contrastRounds)) }
    var switchSeconds by rememberSaveable { mutableIntStateOf(SWITCH_SECOND_OPTIONS.nearest(saved.switchSeconds)) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerTotalSeconds by remember { mutableIntStateOf(0) }
    var timerMode by remember { mutableStateOf(HeatColdMode.SAUNA) }
    val wellnessTimer by WellnessTimerController.state.collectAsState()
    val classicTimer by userPreferences.classicWellnessTimer.collectAsState(initial = false)
    val libraryState by repository.state.collectAsState(initial = HeatColdLibraryState())
    val weekSummary = remember(libraryState) { libraryState.weekSummary(LocalDate.now()) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    val accentMid = if (tab == HeatColdTab.COLD_PLUNGE) coldMid else hotMid
    val suffix = tempSuffix(tempUnit)

    fun rememberSettings() {
        val next = HeatColdLastSettings(
            saunaMinutes = saunaMinutes,
            coldSeconds = coldSeconds,
            saunaTempF = convertTemperature(saunaTemp, tempUnit, TemperatureUnit.FAHRENHEIT),
            coldTempF = convertTemperature(coldTemp, tempUnit, TemperatureUnit.FAHRENHEIT),
            contrastRounds = contrastRounds,
            switchSeconds = switchSeconds,
            lastTab = tab.name
        )
        scope.launch { repository.saveLastSettings(next) }
    }

    suspend fun syncDay(date: LocalDate) {
        if (relayPool != null && signer != null) {
            val urls = keyManager.relayUrlsForKind30078Publish()
            HeatColdSync.publishDay(appContext, relayPool, signer, repository.currentState(), date, urls)
        }
    }

    /** Starts [seconds] of [sessionMode] at the current temperature on the background timer. */
    fun startBackgroundTimer(sessionMode: HeatColdMode, seconds: Int): Boolean {
        val tempValue = if (sessionMode == HeatColdMode.SAUNA) saunaTemp else coldTemp
        val label = sessionMode.displayName()
        return WellnessTimerController.start(
            context = appContext,
            kind = if (sessionMode == HeatColdMode.SAUNA) WellnessTimerKind.SAUNA else WellnessTimerKind.COLD_PLUNGE,
            title = label,
            detail = "$tempValue $suffix",
            totalSeconds = seconds,
        ) { elapsed ->
            val logDate = LocalDate.now()
            repository.logSession(
                mode = sessionMode,
                date = logDate,
                durationSeconds = elapsed,
                tempValue = tempValue.toDouble(),
                tempUnit = tempUnit
            )
            syncDay(logDate)
            "Logged $label • ${formatDurationSeconds(elapsed)}"
        }
    }

    fun startSession(sessionMode: HeatColdMode, seconds: Int, classic: Boolean) {
        rememberSettings()
        if (classic) {
            timerMode = sessionMode
            timerTotalSeconds = seconds
            timerRunning = true
        } else if (!startBackgroundTimer(sessionMode, seconds)) {
            scope.launch { snackbarHostState.showSnackbar("Another timer is already running") }
        }
    }

    fun startContrast() {
        rememberSettings()
        val plan = ContrastPlan(
            rounds = contrastRounds,
            saunaSeconds = saunaMinutes * 60,
            coldSeconds = coldSeconds,
            switchSeconds = switchSeconds,
            saunaTemp = saunaTemp,
            coldTemp = coldTemp,
            unit = tempUnit
        )
        val contrastId = UUID.randomUUID().toString()
        val unitAtStart = tempUnit
        val first = plan.firstStep { mode, seconds, temp ->
            val logDate = LocalDate.now()
            repository.logSession(
                mode = mode,
                date = logDate,
                durationSeconds = seconds,
                tempValue = temp.toDouble(),
                tempUnit = unitAtStart,
                contrastId = contrastId
            )
            syncDay(logDate)
        }
        if (first == null || !WellnessTimerController.startSteps(appContext, first)) {
            scope.launch { snackbarHostState.showSnackbar("Another timer is already running") }
        }
    }

    LaunchedEffect(Unit) {
        WellnessTimerController.results.collect { result ->
            if (result.kind == WellnessTimerKind.SAUNA || result.kind == WellnessTimerKind.COLD_PLUNGE) {
                snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    LaunchedEffect(Unit) {
        val raw = userPreferences.consumeProgramDashboardHeatColdLaunchJson()
        if (raw.isNullOrBlank()) return@LaunchedEffect
        val payload = decodeHeatColdLaunch(raw) ?: return@LaunchedEffect
        val parsedMode = runCatching { HeatColdMode.valueOf(payload.mode) }.getOrNull() ?: return@LaunchedEffect
        val secs = payload.durationSeconds.coerceIn(15, 2 * 60 * 60)
        val seconds = when (parsedMode) {
            HeatColdMode.SAUNA -> {
                tab = HeatColdTab.SAUNA
                saunaMinutes = SAUNA_MINUTE_OPTIONS.nearest((secs + 59) / 60)
                saunaMinutes * 60
            }
            HeatColdMode.COLD_PLUNGE -> {
                tab = HeatColdTab.COLD_PLUNGE
                coldSeconds = COLD_SECOND_OPTIONS.nearest(secs)
                coldSeconds
            }
        }
        startSession(parsedMode, seconds, userPreferences.classicWellnessTimer.first())
    }

    val backgroundTimer = wellnessTimer?.takeIf {
        it.kind == WellnessTimerKind.SAUNA || it.kind == WellnessTimerKind.COLD_PLUNGE
    }
    if (backgroundTimer != null) {
        Box(Modifier.fillMaxSize()) {
            WellnessTimerFullScreen(state = backgroundTimer)
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        return
    }

    if (timerRunning) {
        val sauna = timerMode == HeatColdMode.SAUNA
        HeatColdTimerFullScreen(
            totalSeconds = timerTotalSeconds,
            sessionLabel = timerMode.displayName(),
            dark = if (sauna) hotDark else coldDark,
            mid = if (sauna) hotMid else coldMid,
            glow = if (sauna) hotGlow else coldGlow,
            onComplete = {
                val doneMode = timerMode
                val doneSeconds = timerTotalSeconds
                scope.launch {
                    val tempVal = (if (doneMode == HeatColdMode.SAUNA) saunaTemp else coldTemp).toDouble()
                    val logDate = LocalDate.now()
                    repository.logSession(
                        mode = doneMode,
                        date = logDate,
                        durationSeconds = doneSeconds,
                        tempValue = tempVal,
                        tempUnit = tempUnit
                    )
                    syncDay(logDate)
                    snackbarHostState.showSnackbar("Logged ${doneMode.displayName()} • ${formatDurationSeconds(doneSeconds)}")
                }
                timerRunning = false
            },
            onCancel = { timerRunning = false }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Hot + Cold") },
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
                colors = ervSessionTopAppBarColors(if (tab == HeatColdTab.COLD_PLUNGE) coldMid else ErvSessionRed)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeatColdWeekCard(weekSummary)

            FormSectionLabelSmall("Session Type")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HeatColdTab.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            rememberSettings()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, HeatColdTab.entries.size)
                    ) { Text(t.label, maxLines = 1) }
                }
            }

            HorizontalDivider()

            val showSauna = tab != HeatColdTab.COLD_PLUNGE
            val showCold = tab != HeatColdTab.SAUNA
            if (tab == HeatColdTab.CONTRAST) {
                FormSectionLabelSmall("Rounds")
                CompactIntWheel(
                    values = CONTRAST_ROUND_OPTIONS,
                    currentValue = contrastRounds,
                    onCommitted = { contrastRounds = it; rememberSettings() },
                    formatLabel = { r -> if (r == 1) "1 round" else "$r rounds" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showSauna) {
                FormSectionLabelSmall(if (tab == HeatColdTab.CONTRAST) "Sauna time" else "Duration")
                CompactIntWheel(
                    values = SAUNA_MINUTE_OPTIONS,
                    currentValue = saunaMinutes,
                    onCommitted = { saunaMinutes = it; rememberSettings() },
                    formatLabel = { m -> "$m min" },
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabelSmall(if (tab == HeatColdTab.CONTRAST) "Sauna temperature" else "Temperature")
                CompactIntWheel(
                    values = saunaTempOptions(tempUnit),
                    currentValue = saunaTemp,
                    onCommitted = { saunaTemp = it; rememberSettings() },
                    formatLabel = { t -> "$t $suffix" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showCold) {
                FormSectionLabelSmall(if (tab == HeatColdTab.CONTRAST) "Cold time" else "Duration")
                CompactIntWheel(
                    values = COLD_SECOND_OPTIONS,
                    currentValue = coldSeconds,
                    onCommitted = { coldSeconds = it; rememberSettings() },
                    formatLabel = { s -> formatDurationSeconds(s) },
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabelSmall(if (tab == HeatColdTab.CONTRAST) "Cold temperature" else "Temperature")
                CompactIntWheel(
                    values = coldTempOptions(tempUnit),
                    currentValue = coldTemp,
                    onCommitted = { coldTemp = it; rememberSettings() },
                    formatLabel = { t -> "$t $suffix" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (tab == HeatColdTab.CONTRAST) {
                FormSectionLabelSmall("Switch time")
                CompactIntWheel(
                    values = SWITCH_SECOND_OPTIONS,
                    currentValue = switchSeconds,
                    onCommitted = { switchSeconds = it; rememberSettings() },
                    formatLabel = { s -> if (s == 0) "None" else formatDurationSeconds(s) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Temperatures use the °F/°C choice in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val contrastPlanSeconds = ContrastPlan(
                rounds = contrastRounds,
                saunaSeconds = saunaMinutes * 60,
                coldSeconds = coldSeconds,
                switchSeconds = switchSeconds,
                saunaTemp = saunaTemp,
                coldTemp = coldTemp,
                unit = tempUnit
            ).totalSeconds()
            val startSeconds = when (tab) {
                HeatColdTab.SAUNA -> saunaMinutes * 60
                HeatColdTab.COLD_PLUNGE -> coldSeconds
                HeatColdTab.CONTRAST -> contrastPlanSeconds
            }
            Button(
                onClick = {
                    when (tab) {
                        HeatColdTab.SAUNA -> startSession(HeatColdMode.SAUNA, saunaMinutes * 60, classicTimer)
                        HeatColdTab.COLD_PLUNGE -> startSession(HeatColdMode.COLD_PLUNGE, coldSeconds, classicTimer)
                        HeatColdTab.CONTRAST -> startContrast()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = startSeconds > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentMid,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (tab == HeatColdTab.CONTRAST) "Start contrast · ${formatDurationSeconds(startSeconds)}"
                    else "Start ${formatDurationSeconds(startSeconds)}"
                )
            }

            Text(
                when {
                    tab == HeatColdTab.CONTRAST ->
                        "Each round is sauna, then cold plunge, with a switch timer between. End and Save finishes the current part and moves on; Discard stops the session. Every sauna and cold round is saved."
                    classicTimer ->
                        "When the timer finishes, you’ll hear a tone and the session is saved for today."
                    else ->
                        "The timer keeps running with the screen off. When it finishes you’ll hear a soft chime and the session is saved. End and Save keeps the time you did."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeatColdWeekCard(summary: HeatColdWeekSummary) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FormSectionLabelSmall("This week")
            fun line(label: String, totals: HeatColdModeTotals): String =
                if (totals.sessions == 0) "$label · none yet"
                else "$label · ${totals.sessions} session${if (totals.sessions == 1) "" else "s"}, ${formatDurationSeconds(totals.seconds)}"
            Text(line("Sauna", summary.sauna), style = MaterialTheme.typography.bodyMedium)
            Text(line("Cold Plunge", summary.cold), style = MaterialTheme.typography.bodyMedium)
            if (summary.contrastSessions > 0) {
                Text(
                    "Contrast · ${summary.contrastSessions} session${if (summary.contrastSessions == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Since Monday, ${summary.weekStart.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HeatColdTimerFullScreen(
    totalSeconds: Int,
    sessionLabel: String,
    dark: Color,
    mid: Color,
    glow: Color,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val view = LocalView.current
    var remainingSeconds by remember(totalSeconds) { mutableIntStateOf(totalSeconds) }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds <= 0) {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
                tg.release()
            } catch (_: Exception) {
            }
            onComplete()
            return@LaunchedEffect
        }
        delay(1000)
        remainingSeconds -= 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(dark, mid, glow)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Session in progress",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                Text(
                    text = "%d:%02d".format(mins, secs),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    sessionLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White))
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
fun HeatColdLogScreen(
    repository: HeatColdRepository,
    state: HeatColdLibraryState,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit
) {
    var dateFilter by remember { mutableStateOf<SectionLogDateFilter>(SectionLogDateFilter.AllHistory) }
    var showCalendar by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HeatColdTimelineEntry?>(null) }
    var editing by remember { mutableStateOf<HeatColdTimelineEntry?>(null) }
    var addingPast by remember { mutableStateOf(false) }
    val tempUnit by userPreferences.temperatureUnit.collectAsState(initial = TemperatureUnit.FAHRENHEIT)
    val timeline = remember(state, dateFilter) {
        state.heatColdTimelineForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithHeatColdActivity(state) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val headerMid = ErvSessionRed
    val keyManager = LocalKeyManager.current
    val logAppContext = LocalContext.current.applicationContext

    suspend fun syncDate(date: LocalDate) {
        if (relayPool == null || signer == null) return
        val urls = keyManager.relayUrlsForKind30078Publish()
        HeatColdSync.publishDay(logAppContext, relayPool, signer, repository.currentState(), date, urls)
    }

    if (addingPast || editing != null) {
        val entry = editing
        HeatColdSessionDialog(
            editing = entry,
            tempUnit = tempUnit,
            onDismiss = {
                addingPast = false
                editing = null
            },
            onSave = { input ->
                addingPast = false
                editing = null
                scope.launch {
                    val tempValue = input.temp?.toDouble()
                    val unit = input.temp?.let { input.tempUnit }
                    if (entry == null) {
                        repository.logSession(
                            mode = input.mode,
                            date = input.date,
                            durationSeconds = input.durationSeconds,
                            tempValue = tempValue,
                            tempUnit = unit,
                            loggedAtEpochSeconds = input.loggedAtEpochSeconds
                        )
                        syncDate(input.date)
                        snackbarHostState.showSnackbar("Session logged")
                    } else {
                        repository.updateSession(
                            mode = entry.mode,
                            date = entry.logDate,
                            sessionId = entry.session.id,
                            newMode = input.mode,
                            newDate = input.date,
                            durationSeconds = input.durationSeconds,
                            tempValue = tempValue,
                            tempUnit = unit,
                            loggedAtEpochSeconds = input.loggedAtEpochSeconds
                        )
                        syncDate(entry.logDate)
                        if (input.date != entry.logDate) syncDate(input.date)
                        snackbarHostState.showSnackbar("Session updated")
                    }
                }
            },
            onDelete = entry?.let { e ->
                {
                    editing = null
                    pendingDelete = e
                }
            }
        )
    }

    pendingDelete?.let { entry ->
        val session = entry.session
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove session?") },
            text = {
                Text(
                    "This removes the entry from your log on this device and updates your synced day log.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = session.id
                        val logDate = entry.logDate
                        pendingDelete = null
                        scope.launch {
                            repository.deleteSession(entry.mode, logDate, id)
                            syncDate(logDate)
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingPast = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log a past session") }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Hot + Cold Log") },
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
            if (timeline.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (dateFilter) {
                            SectionLogDateFilter.AllHistory -> "No hot or cold sessions logged yet."
                            is SectionLogDateFilter.SingleDay -> "No hot or cold sessions logged for this date."
                            is SectionLogDateFilter.DateRange -> "No hot or cold sessions logged in this date range."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Newest first. Sauna and cold plunge are mixed by time. Tap a session to edit it.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(timeline, key = { "${it.logDate}-${it.mode}-${it.session.id}" }) { entry ->
                        HeatColdLogEntryCard(
                            title = entry.mode.displayName(),
                            showLogDate = showLogDateOnCards,
                            logDate = entry.logDate,
                            session = entry.session,
                            onEdit = { editing = entry },
                            onDelete = { pendingDelete = entry }
                        )
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

@Composable
private fun HeatColdLogEntryCard(
    title: String,
    showLogDate: Boolean,
    logDate: LocalDate,
    session: HeatColdSession,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showLogDate) {
                    Text(
                        logDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    if (session.contrastId != null) "$title · contrast" else title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    formatDurationSeconds(session.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                session.formatTemp()?.let { t ->
                    Text(
                        t,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatHeatColdLogTime(session.loggedAtEpochSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete session")
            }
        }
    }
}

private fun formatHeatColdLogTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown time"
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}
