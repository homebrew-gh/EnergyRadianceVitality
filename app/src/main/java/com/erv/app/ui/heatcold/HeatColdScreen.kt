@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.heatcold

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.heatcold.HeatColdSession
import com.erv.app.heatcold.HeatColdSync
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.heatcold.TemperatureUnit
import com.erv.app.heatcold.HeatColdTimelineEntry
import com.erv.app.heatcold.chronologicalHeatColdTimelineFor
import com.erv.app.heatcold.chronologicalHeatColdTimelineForRange
import com.erv.app.heatcold.formatDurationSeconds
import com.erv.app.heatcold.formatTemp
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.SectionLogDateFilter
import com.erv.app.data.UserPreferences
import com.erv.app.programs.decodeHeatColdLaunch
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
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.ui.components.CompactIntWheel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SAUNA_MINUTE_OPTIONS = (5..30 step 5).toList()
private val COLD_SECOND_OPTIONS = (30..300 step 30).toList()

private val SAUNA_TEMP_FAHRENHEIT = (130..200).toList()
private val SAUNA_TEMP_CELSIUS = (54..93).toList()
private val COLD_TEMP_FAHRENHEIT = (33..55).toList()
private val COLD_TEMP_CELSIUS = (1..13).toList()

/** Defaults when toggling °F/°C (avoids wheel desync from converted values). */
private const val SAUNA_TEMP_DEFAULT_F = 155
private const val SAUNA_TEMP_DEFAULT_C = 68
private const val COLD_TEMP_DEFAULT_F = 44
private const val COLD_TEMP_DEFAULT_C = 7

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
    val darkTheme = isSystemInDarkTheme()
    // Match Light therapy / app red accents (not brown-orange sauna-only hues).
    val hotDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val hotMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val hotGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val coldDark = if (darkTheme) ErvDarkColdDark else ErvColdDark
    val coldMid = if (darkTheme) ErvDarkColdMid else ErvColdMid
    val coldGlow = if (darkTheme) ErvDarkColdGlow else ErvColdGlow

    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var saunaMinutes by rememberSaveable { mutableIntStateOf(15) }
    var coldSeconds by rememberSaveable { mutableIntStateOf(120) }
    var saunaTemp by rememberSaveable { mutableIntStateOf(155) }
    var coldTemp by rememberSaveable { mutableIntStateOf(44) }
    var tempUnit by rememberSaveable { mutableStateOf(TemperatureUnit.FAHRENHEIT) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerTotalSeconds by remember { mutableIntStateOf(0) }

    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    val headerDark = if (mode == HeatColdMode.SAUNA) hotDark else coldDark
    val headerMid = if (mode == HeatColdMode.SAUNA) hotMid else coldMid
    val timerDark = headerDark
    val timerMid = if (mode == HeatColdMode.SAUNA) hotMid else coldMid
    val timerGlow = if (mode == HeatColdMode.SAUNA) hotGlow else coldGlow
    val sessionLabel = if (mode == HeatColdMode.SAUNA) "Sauna" else "Cold Plunge"

    suspend fun syncSaunaLog() {
        if (relayPool != null && signer != null) {
            val urls = keyManager.relayUrlsForKind30078Publish()
            repository.currentState().saunaLogFor(today)?.let { log ->
                HeatColdSync.publishSaunaDailyLog(appContext, relayPool, signer, log, urls)
            }
        }
    }

    suspend fun syncColdLog() {
        if (relayPool != null && signer != null) {
            val urls = keyManager.relayUrlsForKind30078Publish()
            repository.currentState().coldLogFor(today)?.let { log ->
                HeatColdSync.publishColdDailyLog(appContext, relayPool, signer, log, urls)
            }
        }
    }

    LaunchedEffect(Unit) {
        val raw = userPreferences.consumeProgramDashboardHeatColdLaunchJson()
        if (!raw.isNullOrBlank()) {
            val payload = decodeHeatColdLaunch(raw)
            val parsedMode = payload?.let { p -> runCatching { HeatColdMode.valueOf(p.mode) }.getOrNull() }
            if (payload != null && parsedMode != null) {
                mode = parsedMode
                val secs = payload.durationSeconds.coerceIn(30, 2 * 60 * 60)
                when (parsedMode) {
                    HeatColdMode.SAUNA -> {
                        val minuteOptions = (5..30 step 5).toList()
                        val wantMin = ((secs + 59) / 60).coerceIn(5, 30)
                        val snappedMin = minuteOptions.minByOrNull { kotlin.math.abs(it - wantMin) } ?: wantMin
                        saunaMinutes = snappedMin
                        timerTotalSeconds = snappedMin * 60
                    }
                    HeatColdMode.COLD_PLUNGE -> {
                        val secondOptions = (30..300 step 30).toList()
                        val snappedSec =
                            secondOptions.minByOrNull { kotlin.math.abs(it - secs) } ?: secs.coerceIn(30, 300)
                        coldSeconds = snappedSec
                        timerTotalSeconds = snappedSec
                    }
                }
                timerRunning = true
                return@LaunchedEffect
            }
        }
        coldSeconds = COLD_SECOND_OPTIONS.minByOrNull { abs(it - coldSeconds) } ?: coldSeconds
        saunaMinutes = SAUNA_MINUTE_OPTIONS.minByOrNull { abs(it - saunaMinutes) } ?: saunaMinutes
        when (tempUnit) {
            TemperatureUnit.FAHRENHEIT -> {
                saunaTemp = saunaTemp.coerceIn(SAUNA_TEMP_FAHRENHEIT.first(), SAUNA_TEMP_FAHRENHEIT.last())
                coldTemp = coldTemp.coerceIn(COLD_TEMP_FAHRENHEIT.first(), COLD_TEMP_FAHRENHEIT.last())
            }
            TemperatureUnit.CELSIUS -> {
                saunaTemp = saunaTemp.coerceIn(SAUNA_TEMP_CELSIUS.first(), SAUNA_TEMP_CELSIUS.last())
                coldTemp = coldTemp.coerceIn(COLD_TEMP_CELSIUS.first(), COLD_TEMP_CELSIUS.last())
            }
        }
    }

    fun selectFahrenheit() {
        if (tempUnit == TemperatureUnit.CELSIUS) {
            saunaTemp = SAUNA_TEMP_DEFAULT_F
            coldTemp = COLD_TEMP_DEFAULT_F
            tempUnit = TemperatureUnit.FAHRENHEIT
        }
    }

    fun selectCelsius() {
        if (tempUnit == TemperatureUnit.FAHRENHEIT) {
            saunaTemp = SAUNA_TEMP_DEFAULT_C
            coldTemp = COLD_TEMP_DEFAULT_C
            tempUnit = TemperatureUnit.CELSIUS
        }
    }

    if (timerRunning) {
        HeatColdTimerFullScreen(
            totalSeconds = timerTotalSeconds,
            sessionLabel = sessionLabel,
            dark = timerDark,
            mid = timerMid,
            glow = timerGlow,
            onComplete = {
                scope.launch {
                    val tempVal = (if (mode == HeatColdMode.SAUNA) saunaTemp else coldTemp).toDouble()
                    val tempU = tempUnit
                    if (mode == HeatColdMode.SAUNA) {
                        repository.logSaunaSession(
                            date = today,
                            durationSeconds = timerTotalSeconds,
                            tempValue = tempVal,
                            tempUnit = tempU
                        )
                        syncSaunaLog()
                    } else {
                        repository.logColdSession(
                            date = today,
                            durationSeconds = timerTotalSeconds,
                            tempValue = tempVal,
                            tempUnit = tempU
                        )
                        syncColdLog()
                    }
                    snackbarHostState.showSnackbar("Logged $sessionLabel • ${formatDurationSeconds(timerTotalSeconds)}")
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (mode == HeatColdMode.SAUNA) ErvHeaderRed else coldMid,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
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
            Text("Session Type", style = MaterialTheme.typography.titleSmall)
            BinarySegmentedSlider(
                selectedLeft = mode == HeatColdMode.SAUNA,
                onSelectLeft = { mode = HeatColdMode.SAUNA },
                onSelectRight = { mode = HeatColdMode.COLD_PLUNGE },
                leftLabel = "Sauna",
                rightLabel = "Cold Plunge",
                accentColor = headerMid
            )

            HorizontalDivider()

            Text("Duration", style = MaterialTheme.typography.titleSmall)
            if (mode == HeatColdMode.SAUNA) {
                CompactIntWheel(
                    values = SAUNA_MINUTE_OPTIONS,
                    currentValue = saunaMinutes,
                    onCommitted = { saunaMinutes = it },
                    formatLabel = { m -> "$m min" },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CompactIntWheel(
                    values = COLD_SECOND_OPTIONS,
                    currentValue = coldSeconds,
                    onCommitted = { coldSeconds = it },
                    formatLabel = { s -> formatDurationSeconds(s) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text("Temperature", style = MaterialTheme.typography.titleSmall)
            BinarySegmentedSlider(
                selectedLeft = tempUnit == TemperatureUnit.FAHRENHEIT,
                onSelectLeft = { selectFahrenheit() },
                onSelectRight = { selectCelsius() },
                leftLabel = "°F",
                rightLabel = "°C",
                accentColor = headerMid
            )

            val saunaTempOptions =
                if (tempUnit == TemperatureUnit.FAHRENHEIT) SAUNA_TEMP_FAHRENHEIT else SAUNA_TEMP_CELSIUS
            val coldTempOptions =
                if (tempUnit == TemperatureUnit.FAHRENHEIT) COLD_TEMP_FAHRENHEIT else COLD_TEMP_CELSIUS
            val tempSuffix = if (tempUnit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"

            if (mode == HeatColdMode.SAUNA) {
                CompactIntWheel(
                    values = saunaTempOptions,
                    currentValue = saunaTemp,
                    onCommitted = { saunaTemp = it },
                    formatLabel = { t -> "$t $tempSuffix" },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CompactIntWheel(
                    values = coldTempOptions,
                    currentValue = coldTemp,
                    onCommitted = { coldTemp = it },
                    formatLabel = { t -> "$t $tempSuffix" },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            val startSeconds = if (mode == HeatColdMode.SAUNA) saunaMinutes * 60 else coldSeconds
            Button(
                onClick = {
                    timerTotalSeconds = startSeconds
                    timerRunning = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = startSeconds > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = headerMid,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Start ${formatDurationSeconds(startSeconds)}")
            }

            Text(
                "When the timer finishes, you’ll hear a tone and the session is saved for today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BinarySegmentedSlider(
    selectedLeft: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit,
    leftLabel: String,
    rightLabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val inset = 4.dp
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val onSelectLeftLatest = rememberUpdatedState(onSelectLeft)
    val onSelectRightLatest = rememberUpdatedState(onSelectRight)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val insetPx = with(density) { inset.toPx() }
        val maxWpx = with(density) { maxWidth.toPx() }
        val thumbWpx = (maxWpx - 2f * insetPx) / 2f
        val leftXPx = insetPx
        val rightXPx = insetPx + thumbWpx
        val thumbWidthDp = (maxWidth - inset * 2) / 2

        val thumbX = remember { Animatable(Float.NaN) }
        var dragging by remember { mutableStateOf(false) }

        LaunchedEffect(leftXPx, rightXPx, selectedLeft, dragging) {
            val target = if (selectedLeft) leftXPx else rightXPx
            if (thumbX.value.isNaN()) {
                thumbX.snapTo(target)
                return@LaunchedEffect
            }
            if (!dragging) {
                thumbX.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 380f))
            }
        }

        val thumbPosPx =
            if (thumbX.value.isNaN()) {
                if (selectedLeft) leftXPx else rightXPx
            } else {
                thumbX.value
            }

        Box(
            Modifier
                .padding(top = inset, bottom = inset)
                .offset(x = with(density) { thumbPosPx.toDp() })
                .width(thumbWidthDp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(18.dp))
                .background(accentColor.copy(alpha = 0.38f))
        )

        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    leftLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selectedLeft) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    rightLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!selectedLeft) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(
                    leftXPx,
                    rightXPx,
                    selectedLeft,
                    maxWpx,
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        val tapX = down.position.x
                        var dragStarted = false
                        var thumbAtGestureStart = 0f
                        var cumulativeDx = 0f
                        var dragCallbacks = 0
                        try {
                            horizontalDrag(pointerId) { change ->
                                dragCallbacks++
                                val dx = change.positionChange().x
                                if (!dragStarted) {
                                    dragStarted = true
                                    dragging = true
                                    thumbAtGestureStart =
                                        if (thumbX.value.isNaN()) {
                                            if (selectedLeft) leftXPx else rightXPx
                                        } else {
                                            thumbX.value
                                        }
                                    scope.launch { thumbX.stop() }
                                }
                                cumulativeDx += dx
                                change.consume()
                                val next =
                                    (thumbAtGestureStart + cumulativeDx).coerceIn(leftXPx, rightXPx)
                                scope.launch { thumbX.snapTo(next) }
                            }
                        } finally {
                            dragging = false
                        }
                        if (dragCallbacks == 0) {
                            val halfW = size.width / 2f
                            if (tapX < halfW) {
                                onSelectLeftLatest.value()
                            } else {
                                onSelectRightLatest.value()
                            }
                        } else {
                            val distL = abs(thumbX.value - leftXPx)
                            val distR = abs(thumbX.value - rightXPx)
                            if (distL <= distR) {
                                onSelectLeftLatest.value()
                            } else {
                                onSelectRightLatest.value()
                            }
                        }
                    }
                }
        )
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
    var remainingSeconds by remember(totalSeconds) { mutableIntStateOf(totalSeconds) }

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
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit
) {
    var dateFilter by remember { mutableStateOf<SectionLogDateFilter>(SectionLogDateFilter.AllHistory) }
    var showCalendar by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<HeatColdTimelineEntry?>(null) }
    val timeline = remember(state, dateFilter) {
        state.heatColdTimelineForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithHeatColdActivity(state) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val headerMid = ErvHeaderRed
    val keyManager = LocalKeyManager.current
    val logAppContext = LocalContext.current.applicationContext

    suspend fun syncAfterDeleteForDate(date: LocalDate) {
        if (relayPool == null || signer == null) return
        val urls = keyManager.relayUrlsForKind30078Publish()
        repository.currentState().saunaLogFor(date)?.let {
            HeatColdSync.publishSaunaDailyLog(logAppContext, relayPool, signer, it, urls)
        }
        repository.currentState().coldLogFor(date)?.let {
            HeatColdSync.publishColdDailyLog(logAppContext, relayPool, signer, it, urls)
        }
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
                            when (entry.mode) {
                                HeatColdMode.SAUNA -> repository.deleteSaunaSession(logDate, id)
                                HeatColdMode.COLD_PLUNGE -> repository.deleteColdSession(logDate, id)
                            }
                            syncAfterDeleteForDate(logDate)
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
                title = { Text("Hot + Cold log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerMid,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Newest first. Sauna and cold plunge are mixed by time. Tap delete to remove from that day.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(timeline, key = { "${it.logDate}-${it.mode}-${it.session.id}" }) { entry ->
                        val title = when (entry.mode) {
                            HeatColdMode.SAUNA -> "Sauna"
                            HeatColdMode.COLD_PLUNGE -> "Cold Plunge"
                        }
                        HeatColdLogEntryCard(
                            title = title,
                            showLogDate = showLogDateOnCards,
                            logDate = entry.logDate,
                            session = entry.session,
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
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
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
                        logDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
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
