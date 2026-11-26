@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.erv.app.ui.lighttherapy

import com.erv.app.ui.theme.ervTopAppBarColors
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.lighttherapy.*
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.reminders.RoutineReminder
import com.erv.app.reminders.RoutineReminderFrequency
import com.erv.app.reminders.Weekday
import com.erv.app.reminders.shortLabel
import com.erv.app.reminders.RoutineReminderDraft
import com.erv.app.reminders.RoutineReminderRepository
import com.erv.app.reminders.RoutineReminderState
import com.erv.app.reminders.isValid
import com.erv.app.reminders.toDraft
import com.erv.app.reminders.toReminder
import com.erv.app.ui.reminders.RoutineReminderFormSection
import com.erv.app.SectionLogDateFilter
import com.erv.app.lighttherapy.datedLightSessionsForSectionLog
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.datesWithLightActivity
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.KeyManager
import com.erv.app.ui.wellness.WellnessTimerController
import com.erv.app.ui.wellness.WellnessTimerFullScreen
import com.erv.app.ui.wellness.WellnessTimerKind
import com.erv.app.ui.wellness.lightMinutesForSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class LightTab { Timer, Routines, Lights }

@Composable
fun LightTherapyCategoryScreen(
    repository: LightTherapyRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val therapyRedDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val state by repository.state.collectAsState(initial = LightLibraryState())
    val reminderRepository = remember(context) { RoutineReminderRepository(context) }
    val reminderState by reminderRepository.state.collectAsState(initial = RoutineReminderState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerDurationMinutes by remember { mutableStateOf(5) }
    var timerRoutineId by remember { mutableStateOf<String?>(null) }
    var timerRoutineName by remember { mutableStateOf<String?>(null) }
    var timerDeviceId by remember { mutableStateOf<String?>(null) }
    var timerDeviceName by remember { mutableStateOf<String?>(null) }
    val wellnessTimer by WellnessTimerController.state.collectAsState()
    var freeTimerDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val classicTimer by userPreferences.classicWellnessTimer.collectAsState(initial = false)
    var deviceEditor by remember { mutableStateOf<LightDevice?>(null) }
    var creatingDevice by remember { mutableStateOf(false) }
    var routineEditor by remember { mutableStateOf<LightRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            LightSync.publishMaster(
                context.applicationContext,
                relayPool,
                signer,
                repository.currentState(),
                keyManager.relayUrlsForKind30078Publish(),
            )
        }
    }

    suspend fun syncDailyLog(log: LightDayLog) {
        if (relayPool != null && signer != null) {
            LightSync.publishDailyLog(
                context.applicationContext,
                relayPool,
                signer,
                log,
                keyManager.relayUrlsForKind30078Publish(),
            )
        }
    }

    LaunchedEffect(reminderRepository) {
        reminderRepository.restoreAllSchedules()
    }

    LaunchedEffect(Unit) {
        WellnessTimerController.results.collect { result ->
            if (result.kind == WellnessTimerKind.LIGHT) snackbarHostState.showSnackbar(result.message)
        }
    }

    /** Starts a light session on the background timer, or the classic in-screen one. */
    fun startSession(minutes: Int, routineId: String?, routineName: String?, deviceId: String?, deviceName: String?) {
        if (classicTimer) {
            timerDurationMinutes = minutes
            timerRoutineId = routineId
            timerRoutineName = routineName
            timerDeviceId = deviceId
            timerDeviceName = deviceName
            timerRunning = true
        } else {
            timerDurationMinutes = minutes
            val started = startLightWellnessTimer(
                context = context,
                repository = repository,
                relayPool = relayPool,
                signer = signer,
                keyManager = keyManager,
                minutes = minutes,
                routineId = routineId,
                routineName = routineName,
                deviceId = deviceId,
                deviceName = deviceName,
            )
            if (!started) scope.launch { snackbarHostState.showSnackbar("Another timer is already running") }
        }
    }

    val backgroundTimer = wellnessTimer?.takeIf { it.kind == WellnessTimerKind.LIGHT }
    if (backgroundTimer != null) {
        Box(Modifier.fillMaxSize()) {
            WellnessTimerFullScreen(state = backgroundTimer)
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        return
    }

    if (timerRunning) {
        LightTherapyTimerFullScreen(
            durationMinutes = timerDurationMinutes,
            deviceName = timerDeviceName ?: "Light therapy",
            redDark = therapyRedDark,
            redMid = therapyRedMid,
            redGlow = therapyRedGlow,
            onComplete = {
                scope.launch {
                    val today = LocalDate.now()
                    repository.logSession(
                        date = today,
                        minutes = timerDurationMinutes,
                        deviceId = timerDeviceId,
                        deviceName = timerDeviceName,
                        routineId = timerRoutineId,
                        routineName = timerRoutineName
                    )
                    repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                    snackbarHostState.showSnackbar("Logged ${timerDurationMinutes} min")
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
                title = { Text("Light Therapy") },
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
                LightTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            when (LightTab.entries[activeTab]) {
                LightTab.Timer -> TimerTabContent(
                    defaultMinutes = timerDurationMinutes,
                    devices = state.devices,
                    selectedDeviceId = freeTimerDeviceId?.takeIf { id -> state.deviceById(id) != null },
                    onSelectDevice = { freeTimerDeviceId = it },
                    onStartTimer = { minutes, device ->
                        startSession(minutes, routineId = null, routineName = null, deviceId = device?.id, deviceName = device?.name)
                    }
                )
                LightTab.Routines -> RoutinesTabContent(
                    state = state,
                    onAddRoutine = {
                        creatingRoutine = true
                        routineEditor = null
                    },
                    onEditRoutine = { routineEditor = it },
                    onDeleteRoutine = { routineId ->
                        scope.launch {
                            repository.deleteRoutine(routineId)
                            reminderRepository.deleteReminder(routineId)
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine deleted")
                        }
                    },
                    onRunRoutine = { routine ->
                        val device = routine.deviceId?.let { state.deviceById(it) }
                        startSession(
                            routine.durationMinutes,
                            routineId = routine.id,
                            routineName = routine.name,
                            deviceId = routine.deviceId,
                            deviceName = device?.name
                        )
                    }
                )
                LightTab.Lights -> LightsTabContent(
                    state = state,
                    onAddDevice = {
                        creatingDevice = true
                        deviceEditor = null
                    },
                    onEditDevice = { deviceEditor = it },
                    onDeleteDevice = { deviceId ->
                        scope.launch {
                            repository.deleteDevice(deviceId)
                            syncMaster()
                            snackbarHostState.showSnackbar("Device removed")
                        }
                    }
                )
            }
        }
    }

    if (creatingRoutine || routineEditor != null) {
        LightRoutineEditorDialog(
            routine = routineEditor,
            creating = creatingRoutine,
            devices = state.devices,
            existingReminder = routineEditor?.id?.let { reminderState.reminderForRoutine(it) },
            onDismiss = {
                routineEditor = null
                creatingRoutine = false
            },
            onSave = { routine, reminderDraft ->
                if (routine.id == routineEditor?.id) {
                    scope.launch {
                        repository.updateRoutine(routine)
                        val scheduled = reminderRepository.upsertReminder(reminderDraft.toReminder(routine.id, routine.name))
                        syncMaster()
                        snackbarHostState.showSnackbar("Routine updated")
                        if (reminderDraft.enabled && !scheduled) {
                            snackbarHostState.showSnackbar("Enable exact alarms for reminder notifications")
                        }
                    }
                } else {
                    scope.launch {
                        repository.addRoutine(routine)
                        val scheduled = reminderRepository.upsertReminder(reminderDraft.toReminder(routine.id, routine.name))
                        syncMaster()
                        snackbarHostState.showSnackbar("Routine saved")
                        if (reminderDraft.enabled && !scheduled) {
                            snackbarHostState.showSnackbar("Enable exact alarms for reminder notifications")
                        }
                    }
                }
                routineEditor = null
                creatingRoutine = false
            }
        )
    }

    if (creatingDevice || deviceEditor != null) {
        LightDeviceEditorDialog(
            device = deviceEditor,
            creating = creatingDevice,
            onDismiss = {
                deviceEditor = null
                creatingDevice = false
            },
            onSave = { device ->
                if (device.id == deviceEditor?.id) {
                    scope.launch {
                        repository.updateDevice(device)
                        syncMaster()
                        snackbarHostState.showSnackbar("Device updated")
                    }
                } else {
                    scope.launch {
                        repository.addDevice(device)
                        syncMaster()
                        snackbarHostState.showSnackbar("Device saved")
                    }
                }
                deviceEditor = null
                creatingDevice = false
            }
        )
    }
}

/**
 * Starts a light session on the shared background timer (lock-screen notification, soft chime,
 * End and Save). On finish it logs the minutes done for today and syncs the day. Used by the Light
 * Therapy screen and the dashboard. Returns false if another wellness timer is already running.
 */
fun startLightWellnessTimer(
    context: android.content.Context,
    repository: LightTherapyRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    keyManager: KeyManager,
    minutes: Int,
    routineId: String?,
    routineName: String?,
    deviceId: String?,
    deviceName: String?,
): Boolean {
    val appContext = context.applicationContext
    val title = routineName ?: "Light Therapy"
    val detail = deviceName?.takeIf { it != title }.orEmpty()
    return WellnessTimerController.start(
        context = appContext,
        kind = WellnessTimerKind.LIGHT,
        title = title,
        detail = detail,
        totalSeconds = minutes * 60,
    ) { elapsed ->
        val loggedMinutes = lightMinutesForSeconds(elapsed)
        val today = LocalDate.now()
        repository.logSession(
            date = today,
            minutes = loggedMinutes,
            deviceId = deviceId,
            deviceName = deviceName,
            routineId = routineId,
            routineName = routineName
        )
        repository.currentState().logFor(today)?.let { log ->
            if (relayPool != null && signer != null) {
                LightSync.publishDailyLog(appContext, relayPool, signer, log, keyManager.relayUrlsForKind30078Publish())
            }
        }
        "Logged ${routineName ?: "light therapy"} • $loggedMinutes min"
    }
}

/** Classic full-screen countdown timer; can be used from Light Therapy screen or Dashboard. */
@Composable
fun LightTherapyTimerFullScreen(
    durationMinutes: Int,
    deviceName: String,
    redDark: Color,
    redMid: Color,
    redGlow: Color,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val view = LocalView.current
    var remainingSeconds by remember(durationMinutes) { mutableIntStateOf(durationMinutes * 60) }

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
            } catch (_: Exception) { }
            onComplete()
            return@LaunchedEffect
        }
        delay(1000)
        remainingSeconds -= 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(redDark, redMid, redGlow)
                )
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
                    deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f)
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

private val MINUTE_OPTIONS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10) + (15..60 step 5).toList()
private const val WHEEL_ITEM_HEIGHT_DP = 56
private const val WHEEL_VISIBLE_ITEMS = 5

@Composable
private fun TimerTabContent(
    defaultMinutes: Int,
    devices: List<LightDevice>,
    selectedDeviceId: String?,
    onSelectDevice: (String?) -> Unit,
    onStartTimer: (Int, LightDevice?) -> Unit
) {
    val selectedDevice = selectedDeviceId?.let { id -> devices.firstOrNull { it.id == id } }
    val darkTheme = isSystemInDarkTheme()
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val initialIndex = MINUTE_OPTIONS.indexOf(
        MINUTE_OPTIONS.minByOrNull { kotlin.math.abs(it - defaultMinutes.coerceIn(1, 60)) } ?: 5
    ).coerceIn(0, MINUTE_OPTIONS.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { WHEEL_ITEM_HEIGHT_DP.dp.toPx() }
    val selectedIndex by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset.toFloat()
            val index = listState.firstVisibleItemIndex + (offset / itemHeightPx).roundToInt()
            index.coerceIn(0, MINUTE_OPTIONS.lastIndex)
        }
    }
    val selectedMinutes = MINUTE_OPTIONS[selectedIndex]
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val iconCenter = Offset(40.dp.toPx(), 40.dp.toPx())
            val radius = size.maxDimension * 0.72f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        therapyRedGlow.copy(alpha = 0.55f),
                        therapyRedMid.copy(alpha = 0.28f),
                        therapyRedGlow.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = iconCenter,
                    radius = radius
                ),
                radius = radius,
                center = iconCenter
            )
        }
        Icon(
            imageVector = Icons.Default.WbIncandescent,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp),
            tint = therapyRedMid
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (devices.isEmpty()) {
                Spacer(Modifier.height(8.dp))
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 56.dp)
                ) {
                    FormSectionLabelSmall("Light")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = selectedDevice == null,
                            onClick = { onSelectDevice(null) },
                            label = { Text("None") }
                        )
                        devices.forEach { device ->
                            FilterChip(
                                selected = selectedDevice?.id == device.id,
                                onClick = {
                                    onSelectDevice(device.id)
                                    device.recommendedDurationMinutes?.let { rec ->
                                        val idx = MINUTE_OPTIONS.indexOf(
                                            MINUTE_OPTIONS.minByOrNull { kotlin.math.abs(it - rec) } ?: rec
                                        )
                                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                                    }
                                },
                                label = { Text(device.name) }
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .height((WHEEL_ITEM_HEIGHT_DP * WHEEL_VISIBLE_ITEMS).dp)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        vertical = (WHEEL_ITEM_HEIGHT_DP * (WHEEL_VISIBLE_ITEMS / 2)).dp
                    )
                ) {
                    items(MINUTE_OPTIONS.size, key = { MINUTE_OPTIONS[it] }) { index ->
                        val minutes = MINUTE_OPTIONS[index]
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier
                                .height(WHEEL_ITEM_HEIGHT_DP.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$minutes",
                                style = if (isSelected) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onStartTimer(selectedMinutes, selectedDevice) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Start $selectedMinutes min")
            }
        }
    }
}

@Composable
private fun RoutinesTabContent(
    state: LightLibraryState,
    onAddRoutine: () -> Unit,
    onEditRoutine: (LightRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onRunRoutine: (LightRoutine) -> Unit
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
                        "Create a routine for a part of the day and assign a device. You can set which days it applies to.",
                        style = MaterialTheme.typography.bodySmall,
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
                items(state.routines, key = { it.id }) { routine ->
                    val device = routine.deviceId?.let { state.deviceById(it) }
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${routine.timeOfDay.label()} • ${routine.durationMinutes} min${device?.let { " • ${it.name}" }.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (routine.repeatDays.isNotEmpty()) {
                                Text(
                                    "Days: ${routine.repeatDays.joinToString { it.shortLabel() }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalButton(onClick = { onRunRoutine(routine) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start")
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
        FloatingActionButton(
            onClick = onAddRoutine,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add routine")
        }
    }
}

@Composable
fun LightLogScreen(
    repository: LightTherapyRepository,
    state: LightLibraryState,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit
) {
    var dateFilter by remember { mutableStateOf<SectionLogDateFilter>(SectionLogDateFilter.AllHistory) }
    var showCalendar by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DatedLightSession?>(null) }
    var editing by remember { mutableStateOf<DatedLightSession?>(null) }
    var addingPast by remember { mutableStateOf(false) }
    val datedEntries = remember(state, dateFilter) {
        state.datedLightSessionsForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithLightActivity(state) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyManager = LocalKeyManager.current
    val logAppContext = LocalContext.current.applicationContext

    suspend fun syncDailyLogForDate(date: LocalDate) {
        if (relayPool != null && signer != null) {
            repository.currentState().logFor(date)?.let { log ->
                LightSync.publishDailyLog(
                    logAppContext,
                    relayPool,
                    signer,
                    log,
                    keyManager.relayUrlsForKind30078Publish(),
                )
            }
        }
    }

    if (addingPast || editing != null) {
        val target = editing
        LightSessionDialog(
            devices = state.devices,
            editing = target,
            onDismiss = {
                addingPast = false
                editing = null
            },
            onSave = { input ->
                addingPast = false
                editing = null
                scope.launch {
                    if (target == null) {
                        repository.logSession(
                            date = input.date,
                            minutes = input.minutes,
                            deviceId = input.device?.id,
                            deviceName = input.device?.name,
                            loggedAtEpochSeconds = input.loggedAtEpochSeconds
                        )
                        syncDailyLogForDate(input.date)
                        snackbarHostState.showSnackbar("Session logged")
                    } else {
                        repository.updateSession(
                            date = target.logDate,
                            sessionId = target.session.id,
                            newDate = input.date,
                            minutes = input.minutes,
                            deviceId = input.device?.id,
                            loggedAtEpochSeconds = input.loggedAtEpochSeconds
                        )
                        syncDailyLogForDate(target.logDate)
                        if (input.date != target.logDate) syncDailyLogForDate(input.date)
                        snackbarHostState.showSnackbar("Session updated")
                    }
                }
            },
            onDelete = target?.let { t ->
                {
                    editing = null
                    pendingDelete = t
                }
            }
        )
    }

    pendingDelete?.let { dated ->
        val session = dated.session
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingPast = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Log a past session") }
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Light Therapy Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = ervTopAppBarColors()
            )
        }
    ) { padding ->
        LightLogContent(
            dateFilter = dateFilter,
            onOpenCalendar = { showCalendar = true },
            onClearFilter = { dateFilter = SectionLogDateFilter.AllHistory },
            datedEntries = datedEntries,
            showLogDateOnCards = showLogDateOnCards,
            onRequestDelete = { pendingDelete = it },
            onRequestEdit = { editing = it },
            modifier = Modifier.padding(padding)
        )
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
private fun LightLogContent(
    dateFilter: SectionLogDateFilter,
    onOpenCalendar: () -> Unit,
    onClearFilter: () -> Unit,
    datedEntries: List<DatedLightSession>,
    showLogDateOnCards: Boolean,
    onRequestDelete: (DatedLightSession) -> Unit,
    onRequestEdit: (DatedLightSession) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SectionLogFilterBar(
            filter = dateFilter,
            onOpenCalendar = onOpenCalendar,
            onClearFilter = onClearFilter
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
                            SectionLogDateFilter.AllHistory -> "No light therapy logged yet."
                            is SectionLogDateFilter.SingleDay -> "No light therapy logged for this date."
                            is SectionLogDateFilter.DateRange -> "No light therapy logged in this date range."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Start a session from the Light Therapy timer, or use Log a past session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Newest first. Tap edit to change a session or delete to remove it.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(datedEntries, key = { "${it.logDate}-${it.session.id}" }) { dated ->
                    val session = dated.session
                    val name = session.routineName ?: session.deviceName ?: "Light therapy"
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
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${session.minutes} min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    formatLogTime(session.loggedAtEpochSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onRequestEdit(dated) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit session")
                                }
                                IconButton(onClick = { onRequestDelete(dated) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete session")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatLogTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown time"
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}

@Composable
private fun LightsTabContent(
    state: LightLibraryState,
    onAddDevice: () -> Unit,
    onEditDevice: (LightDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No lights yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Add your red light panels, SAD lamps, or other devices. Include wavelengths and recommended duration.",
                        style = MaterialTheme.typography.bodySmall,
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
                items(state.devices, key = { it.id }) { device ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            if (device.brand.isNotBlank()) {
                                Text(device.brand, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(device.deviceType.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (device.wavelengths.isNotBlank()) {
                                Text("Wavelengths: ${device.wavelengths}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (device.powerOutput.isNotBlank()) {
                                Text("Power: ${device.powerOutput}", style = MaterialTheme.typography.bodySmall)
                            }
                            device.recommendedDurationMinutes?.let { Text("Recommended: $it min", style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { onEditDevice(device) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit device")
                                }
                                IconButton(onClick = { onDeleteDevice(device.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete device")
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAddDevice,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add device")
        }
    }
}

@Composable
private fun LightRoutineEditorDialog(
    routine: LightRoutine?,
    creating: Boolean,
    devices: List<LightDevice>,
    existingReminder: RoutineReminder?,
    onDismiss: () -> Unit,
    onSave: (LightRoutine, RoutineReminderDraft) -> Unit
) {
    var name by remember(routine?.id) { mutableStateOf(routine?.name.orEmpty()) }
    var timeOfDay by remember(routine?.id) { mutableStateOf(routine?.timeOfDay ?: LightTimeOfDay.MORNING) }
    var durationMinutes by remember(routine?.id) { mutableStateOf(routine?.durationMinutes?.toString() ?: "15") }
    var deviceId by remember(routine?.id) { mutableStateOf(routine?.deviceId) }
    var repeatDays by remember(routine?.id) {
        mutableStateOf(routine?.repeatDaysSet() ?: emptySet())
    }
    var notes by remember(routine?.id) { mutableStateOf(routine?.notes.orEmpty()) }
    var reminderDraft by remember(routine?.id, creating, existingReminder) {
        mutableStateOf(existingReminder?.toDraft() ?: RoutineReminderDraft())
    }
    var deviceExpanded by remember { mutableStateOf(false) }
    val selectedDevice = deviceId?.let { id -> devices.firstOrNull { it.id == id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Add routine" else "Edit routine") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { FieldLabel("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabelSmall("Time of day")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LightTimeOfDay.entries.forEach { slot ->
                        FilterChip(
                            selected = timeOfDay == slot,
                            onClick = { timeOfDay = slot },
                            label = { Text(slot.label()) }
                        )
                    }
                }
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { FieldLabel("Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabelSmall("Device (optional)")
                ExposedDropdownMenuBox(
                    expanded = deviceExpanded,
                    onExpandedChange = { deviceExpanded = !deviceExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDevice?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceExpanded) }
                    )
                    ExposedDropdownMenu(expanded = deviceExpanded, onDismissRequest = { deviceExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = { deviceId = null; deviceExpanded = false }
                        )
                        devices.forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d.name) },
                                onClick = { deviceId = d.id; deviceExpanded = false }
                            )
                        }
                    }
                }
                FormSectionLabelSmall("Repeat days")
                Text(
                    "Pick none for every day.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Weekday.entries.forEach { day ->
                        FilterChip(
                            selected = day in repeatDays,
                            onClick = {
                                repeatDays = if (day in repeatDays) repeatDays - day else repeatDays + day
                            },
                            label = { Text(day.shortLabel()) }
                        )
                    }
                }
                RoutineReminderFormSection(
                    reminderDraft = reminderDraft,
                    onReminderDraftChange = { reminderDraft = it },
                    showSchedule = false,
                    scheduleNote = if (repeatDays.isEmpty() || repeatDays.size == Weekday.entries.size) {
                        "Reminds you every day."
                    } else {
                        "Reminds you on ${Weekday.entries.filter { it in repeatDays }.joinToString { it.shortLabel() }}."
                    }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { FieldLabel("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = routine?.id ?: java.util.UUID.randomUUID().toString()
                    val mins = durationMinutes.toIntOrNull()?.coerceIn(1, 120) ?: 15
                    val saved = LightRoutine(
                        id = id,
                        name = name.trim().ifBlank { "Light routine" },
                        timeOfDay = timeOfDay,
                        durationMinutes = mins,
                        deviceId = deviceId,
                        repeatDays = Weekday.entries.filter { it in repeatDays },
                        notes = notes.trim()
                    )
                    onSave(saved, saved.reminderDraftFollowingRepeatDays(reminderDraft))
                },
                enabled = name.isNotBlank() && reminderDraft.copy(frequency = RoutineReminderFrequency.DAILY).isValid()
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LightDeviceEditorDialog(
    device: LightDevice?,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSave: (LightDevice) -> Unit
) {
    var name by remember(device?.id) { mutableStateOf(device?.name.orEmpty()) }
    var brand by remember(device?.id) { mutableStateOf(device?.brand.orEmpty()) }
    var deviceType by remember(device?.id) { mutableStateOf(device?.deviceType ?: LightDeviceType.RED_NIR) }
    var wavelengths by remember(device?.id) { mutableStateOf(device?.wavelengths.orEmpty()) }
    var powerOutput by remember(device?.id) { mutableStateOf(device?.powerOutput.orEmpty()) }
    var recommendedMinutes by remember(device?.id) {
        mutableStateOf(device?.recommendedDurationMinutes?.toString().orEmpty())
    }
    var notes by remember(device?.id) { mutableStateOf(device?.notes.orEmpty()) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Add light device" else "Edit device") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { FieldLabel("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { FieldLabel("Brand") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabelSmall("Type")
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = deviceType.label(),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) }
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        LightDeviceType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label()) },
                                onClick = { deviceType = t; typeExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = wavelengths,
                    onValueChange = { wavelengths = it },
                    label = { FieldLabel("Wavelengths (e.g. 660nm, 850nm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = powerOutput,
                    onValueChange = { powerOutput = it },
                    label = { FieldLabel("Power output") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recommendedMinutes,
                    onValueChange = { recommendedMinutes = it.filter { c -> c.isDigit() }.take(3) },
                    label = { FieldLabel("Recommended duration (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { FieldLabel("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = device?.id ?: java.util.UUID.randomUUID().toString()
                    onSave(
                        LightDevice(
                            id = id,
                            name = name.trim().ifBlank { "Light device" },
                            brand = brand.trim(),
                            deviceType = deviceType,
                            wavelengths = wavelengths.trim(),
                            powerOutput = powerOutput.trim(),
                            recommendedDurationMinutes = recommendedMinutes.toIntOrNull()?.takeIf { it in 1..120 },
                            notes = notes.trim()
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
