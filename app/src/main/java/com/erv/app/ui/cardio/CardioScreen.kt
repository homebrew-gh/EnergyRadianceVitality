@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.cardio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioCustomActivityType
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioMetEstimator
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioSessionSource
import com.erv.app.cardio.CardioSpeedUnit
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.CardioTreadmillParams
import com.erv.app.cardio.CardioWeekday
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.chronologicalCardioLogFor
import com.erv.app.cardio.derivedTreadmillDistanceMeters
import com.erv.app.cardio.displayName
import com.erv.app.cardio.label
import com.erv.app.cardio.nowEpochSeconds
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.cardio.supportsTreadmillModality
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.dashboard.CalendarPopup
import com.erv.app.ui.dashboard.DateNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val CardioHeaderMid = Color(0xFF1565C0)
private val CardioHeaderDark = Color(0xFF0D47A1)
private val CardioHeaderGlow = Color(0xFF42A5F5)

private enum class CardioTab { Routines, Activities, Log }

@Composable
fun CardioCategoryScreen(
    repository: CardioRepository,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    initialOpenNewWorkout: Boolean = false,
    onConsumedInitialOpenNewWorkout: () -> Unit = {}
) {
    val state by repository.state.collectAsState(initial = CardioLibraryState())
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableIntStateOf(0) }
    var workoutBuilder by remember { mutableStateOf<WorkoutBuilderMode?>(null) }
    var routineEditor by remember { mutableStateOf<CardioRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    var customEditor by remember { mutableStateOf<CardioCustomActivityType?>(null) }
    var creatingCustom by remember { mutableStateOf(false) }
    var timerSession by remember { mutableStateOf<CardioTimerSessionDraft?>(null) }

    LaunchedEffect(initialOpenNewWorkout) {
        if (initialOpenNewWorkout) {
            workoutBuilder = WorkoutBuilderMode.NewSession(null)
            onConsumedInitialOpenNewWorkout()
        }
    }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            CardioSync.publishMaster(relayPool, signer, repository.currentState())
        }
    }

    suspend fun syncDailyLog(log: CardioDayLog) {
        if (relayPool != null && signer != null) {
            CardioSync.publishDailyLog(relayPool, signer, log)
        }
    }

    LaunchedEffect(relayPool, signer?.publicKey) {
        if (relayPool != null && signer != null) {
            CardioSync.fetchFromNetwork(relayPool, signer, signer.publicKey)?.let { remote ->
                repository.replaceAll(remote)
            }
        }
    }

    fun saveSession(date: LocalDate, session: CardioSession) {
        scope.launch {
            repository.addSession(date, session)
            repository.currentState().logFor(date)?.let { syncDailyLog(it) }
        }
    }

    if (timerSession != null) {
        val draft = timerSession!!
        CardioElapsedTimerFullScreen(
            titleLabel = draft.title,
            dark = CardioHeaderDark,
            mid = CardioHeaderMid,
            glow = CardioHeaderGlow,
            onStop = { elapsedSeconds ->
                scope.launch {
                    val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                    val raw = draft.toSession(durationMinutes = durationMinutes, endEpoch = nowEpochSeconds())
                    val session = CardioMetEstimator.applyEstimatedKcal(raw, repository.currentState(), weightKg)
                    repository.addSession(today, session)
                    repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                    snackbarHostState.showSnackbar("Logged ${session.activity.displayLabel} • ${durationMinutes} min")
                    timerSession = null
                }
            },
            onCancel = { timerSession = null }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cardio") },
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
                    containerColor = CardioHeaderMid,
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
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = CardioHeaderDark,
                contentColor = Color.White
            ) {
                CardioTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            when (CardioTab.entries[activeTab]) {
                CardioTab.Routines -> RoutinesTab(
                    state = state,
                    onCreateRoutine = { creatingRoutine = true; routineEditor = null },
                    onNewWorkout = { workoutBuilder = WorkoutBuilderMode.NewSession(null) },
                    onEditRoutine = { routineEditor = it; creatingRoutine = false },
                    onDeleteRoutine = { id ->
                        scope.launch {
                            repository.deleteRoutine(id)
                            syncMaster()
                            snackbarHostState.showSnackbar("Routine removed")
                        }
                    },
                    onLogRoutineQuick = { routine ->
                        val mins = routine.targetDurationMinutes ?: 30
                        scope.launch {
                            val session = CardioMetEstimator.buildSessionFromRoutine(
                                routine = routine,
                                durationMinutes = mins,
                                source = CardioSessionSource.MANUAL,
                                weightKg = weightKg,
                                library = repository.currentState()
                            )
                            repository.addSession(today, session)
                            repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                            snackbarHostState.showSnackbar("Logged ${routine.name}")
                        }
                    },
                    onStartTimerFromRoutine = { routine ->
                        timerSession = CardioTimerSessionDraft.fromRoutine(routine)
                    }
                )
                CardioTab.Activities -> ActivitiesTab(
                    types = state.customActivityTypes,
                    onAdd = { creatingCustom = true; customEditor = null },
                    onEdit = { customEditor = it; creatingCustom = false },
                    onDelete = { id ->
                        scope.launch {
                            repository.deleteCustomType(id)
                            syncMaster()
                            snackbarHostState.showSnackbar("Activity type removed")
                        }
                    }
                )
                CardioTab.Log -> CardioLogTabEmbedded(
                    state = state,
                    onOpenFullLog = onOpenLog
                )
            }
        }
    }

    workoutBuilder?.let { mode ->
        WorkoutBuilderBottomSheet(
            mode = mode,
            state = state,
            weightKg = weightKg,
            onDismiss = { workoutBuilder = null },
            onLog = { date, session ->
                saveSession(date, session)
                scope.launch { snackbarHostState.showSnackbar("Session logged") }
                workoutBuilder = null
            },
            onSaveRoutine = { routine ->
                scope.launch {
                    repository.addRoutine(routine)
                    syncMaster()
                    snackbarHostState.showSnackbar("Routine saved")
                }
                workoutBuilder = null
            },
            onStartTimer = { draft: CardioTimerSessionDraft ->
                timerSession = draft
                workoutBuilder = null
            }
        )
    }

    if (creatingCustom || customEditor != null) {
        CustomActivityDialog(
            existing = customEditor,
            creating = creatingCustom,
            onDismiss = {
                customEditor = null
                creatingCustom = false
            },
            onSave = { type ->
                scope.launch {
                    if (customEditor != null) repository.updateCustomType(type)
                    else repository.addCustomType(type)
                    syncMaster()
                    snackbarHostState.showSnackbar("Saved")
                }
                customEditor = null
                creatingCustom = false
            }
        )
    }

    if (routineEditor != null || creatingRoutine) {
        RoutineEditorDialog(
            routine = routineEditor,
            creating = creatingRoutine,
            state = state,
            onDismiss = {
                routineEditor = null
                creatingRoutine = false
            },
            onSave = { routine ->
                scope.launch {
                    if (routineEditor != null) repository.updateRoutine(routine)
                    else repository.addRoutine(routine)
                    syncMaster()
                    snackbarHostState.showSnackbar("Routine saved")
                }
                routineEditor = null
                creatingRoutine = false
            }
        )
    }
}

private sealed class WorkoutBuilderMode {
    data class NewSession(val template: CardioRoutine?) : WorkoutBuilderMode()
}

@Composable
private fun WorkoutBuilderBottomSheet(
    mode: WorkoutBuilderMode,
    state: CardioLibraryState,
    weightKg: Double?,
    onDismiss: () -> Unit,
    onLog: (LocalDate, CardioSession) -> Unit,
    onSaveRoutine: (CardioRoutine) -> Unit,
    onStartTimer: (CardioTimerSessionDraft) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }
    val template = (mode as? WorkoutBuilderMode.NewSession)?.template

    var useCustom by remember(template) {
        mutableStateOf(template?.activity?.customTypeId != null && template.activity.builtin == null)
    }
    var selectedCustomId by remember(template) {
        mutableStateOf(template?.activity?.customTypeId ?: state.customActivityTypes.firstOrNull()?.id)
    }
    var selectedBuiltin by remember(template) {
        mutableStateOf(template?.activity?.builtin ?: CardioBuiltinActivity.WALK)
    }
    var modality by remember(template) { mutableStateOf(template?.modality ?: CardioModality.OUTDOOR) }
    var speedStr by remember(template) {
        mutableStateOf(template?.treadmill?.speed?.toString() ?: "3.0")
    }
    var speedUnit by remember(template) {
        mutableStateOf(template?.treadmill?.speedUnit ?: CardioSpeedUnit.MPH)
    }
    var inclineStr by remember(template) {
        mutableStateOf(template?.treadmill?.inclinePercent?.toString() ?: "0")
    }
    var treadDistKmStr by remember(template) { mutableStateOf("") }
    var loadStr by remember(template) { mutableStateOf("") }
    var outdoorDistKmStr by remember { mutableStateOf("") }
    var durationStr by remember(template) {
        mutableStateOf((template?.targetDurationMinutes ?: 30).toString())
    }
    var routineNameStr by remember(template) { mutableStateOf(template?.name ?: "") }

    val builtinForModality = if (useCustom) null else selectedBuiltin
    val treadmillApplicable = builtinForModality?.supportsTreadmillModality() == true

    LaunchedEffect(treadmillApplicable, modality) {
        if (!treadmillApplicable && modality == CardioModality.INDOOR_TREADMILL) {
            modality = CardioModality.OUTDOOR
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (template == null) "New workout" else "Workout from ${template.name}",
                style = MaterialTheme.typography.titleLarge
            )
            Text("Activity", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useCustom,
                    onClick = { useCustom = false },
                    label = { Text("Built-in") }
                )
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { Text("Custom") }
                )
            }
            if (useCustom) {
                if (state.customActivityTypes.isEmpty()) {
                    Text("Add a custom activity on the Activities tab first.", color = MaterialTheme.colorScheme.error)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.customActivityTypes.forEach { t ->
                            FilterChip(
                                selected = selectedCustomId == t.id,
                                onClick = { selectedCustomId = t.id },
                                label = { Text(t.name) }
                            )
                        }
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardioBuiltinActivity.entries.forEach { b ->
                        FilterChip(
                            selected = selectedBuiltin == b,
                            onClick = { selectedBuiltin = b },
                            label = { Text(b.displayName()) }
                        )
                    }
                }
            }

            if (treadmillApplicable) {
                Text("Where", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = modality == CardioModality.OUTDOOR,
                        onClick = { modality = CardioModality.OUTDOOR },
                        label = { Text(CardioModality.OUTDOOR.label()) }
                    )
                    FilterChip(
                        selected = modality == CardioModality.INDOOR_TREADMILL,
                        onClick = { modality = CardioModality.INDOOR_TREADMILL },
                        label = { Text(CardioModality.INDOOR_TREADMILL.label()) }
                    )
                }
            }

            if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable) {
                Text("Treadmill", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = speedUnit == CardioSpeedUnit.MPH,
                        onClick = { speedUnit = CardioSpeedUnit.MPH },
                        label = { Text("mph") }
                    )
                    FilterChip(
                        selected = speedUnit == CardioSpeedUnit.KMH,
                        onClick = { speedUnit = CardioSpeedUnit.KMH },
                        label = { Text("km/h") }
                    )
                }
                OutlinedTextField(
                    value = speedStr,
                    onValueChange = { speedStr = it },
                    label = { Text("Speed") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inclineStr,
                    onValueChange = { inclineStr = it },
                    label = { Text("Incline %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = treadDistKmStr,
                    onValueChange = { treadDistKmStr = it },
                    label = { Text("Distance (km, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (builtinForModality == CardioBuiltinActivity.RUCK) {
                    OutlinedTextField(
                        value = loadStr,
                        onValueChange = { loadStr = it },
                        label = { Text("Pack weight (lb)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (modality == CardioModality.OUTDOOR) {
                OutlinedTextField(
                    value = outdoorDistKmStr,
                    onValueChange = { outdoorDistKmStr = it },
                    label = { Text("Distance (km, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = durationStr,
                onValueChange = { durationStr = it },
                label = { Text("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = routineNameStr,
                onValueChange = { routineNameStr = it },
                label = { Text("Routine name (save only)") },
                modifier = Modifier.fillMaxWidth()
            )

            val validationError = remember(
                useCustom, selectedCustomId, durationStr, modality, speedStr, treadmillApplicable, state.customActivityTypes
            ) {
                val duration = durationStr.toIntOrNull()
                if (duration == null || duration <= 0) "Enter a valid duration"
                else if (useCustom && state.customActivityTypes.isEmpty()) "Add a custom activity first"
                else if (useCustom && selectedCustomId == null) "Pick a custom activity"
                else if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable) {
                    val sp = speedStr.toDoubleOrNull()
                    if (sp == null || sp <= 0) "Enter treadmill speed"
                    else null
                } else null
            }

            if (validationError != null) {
                Text(validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            fun buildSnapshot(): CardioActivitySnapshot? {
                return if (useCustom) {
                    val id = selectedCustomId ?: return null
                    state.resolveSnapshot(null, id)
                } else {
                    state.resolveSnapshot(selectedBuiltin, null)
                }
            }

            fun buildTreadmill(): CardioTreadmillParams? {
                if (modality != CardioModality.INDOOR_TREADMILL || !treadmillApplicable) return null
                val speed = speedStr.toDoubleOrNull() ?: return null
                val inc = inclineStr.toDoubleOrNull() ?: 0.0
                val treadKm = treadDistKmStr.toDoubleOrNull()
                val loadLb = if (builtinForModality == CardioBuiltinActivity.RUCK) loadStr.toDoubleOrNull() else null
                val loadKg = loadLb?.times(0.453592)
                return CardioTreadmillParams(
                    speed = speed,
                    speedUnit = speedUnit,
                    inclinePercent = inc,
                    distanceMeters = treadKm?.times(1000.0),
                    loadKg = loadKg
                )
            }

            fun buildSession(source: CardioSessionSource): CardioSession? {
                val snap = buildSnapshot() ?: return null
                val duration = durationStr.toIntOrNull() ?: return null
                if (duration <= 0) return null
                val tm = buildTreadmill()
                if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable && tm == null) return null
                var distM: Double? = outdoorDistKmStr.toDoubleOrNull()?.times(1000.0)
                if (distM == null && tm != null) {
                    distM = tm.distanceMeters ?: derivedTreadmillDistanceMeters(tm, duration)
                }
                val base = CardioSession(
                    activity = snap,
                    modality = modality,
                    treadmill = tm,
                    durationMinutes = duration,
                    distanceMeters = distM,
                    source = source,
                    heartRate = CardioHrScaffolding(),
                    estimatedKcal = null
                )
                return CardioMetEstimator.applyEstimatedKcal(base, state, weightKg)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val s = buildSession(CardioSessionSource.MANUAL) ?: return@Button
                        onLog(today, s)
                    },
                    enabled = validationError == null && buildSnapshot() != null
                ) { Text("Log session") }
                OutlinedButton(
                    onClick = {
                        val snap = buildSnapshot() ?: return@OutlinedButton
                        val tm = buildTreadmill()
                        if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable && tm == null) return@OutlinedButton
                        onStartTimer(
                            CardioTimerSessionDraft.fromQuickSnapshot(
                                activity = snap,
                                modality = modality,
                                treadmill = tm,
                                title = snap.displayLabel
                            )
                        )
                    },
                    enabled = validationError == null && buildSnapshot() != null
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Start timer")
                }
            }
            Button(
                onClick = {
                    val snap = buildSnapshot() ?: return@Button
                    val name = routineNameStr.trim().ifBlank { snap.displayLabel }
                    val tm = buildTreadmill()
                    val duration = durationStr.toIntOrNull() ?: return@Button
                    onSaveRoutine(
                        CardioRoutine(
                            name = name,
                            activity = snap,
                            modality = modality,
                            treadmill = tm,
                            targetDurationMinutes = duration
                        )
                    )
                },
                enabled = validationError == null && buildSnapshot() != null
            ) { Text("Save routine") }

            if (weightKg == null) {
                Text(
                    "Set body weight in Settings for calorie estimates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoutinesTab(
    state: CardioLibraryState,
    onCreateRoutine: () -> Unit,
    onNewWorkout: () -> Unit,
    onEditRoutine: (CardioRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onLogRoutineQuick: (CardioRoutine) -> Unit,
    onStartTimerFromRoutine: (CardioRoutine) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clickable(onClick = onCreateRoutine),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New routine (form)", style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                }
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewWorkout),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("New workout", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Log, save a routine, or start a timer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(state.routines, key = { it.id }) { routine ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${routine.activity.displayLabel} • ${routine.modality.label()}" +
                                (routine.targetDurationMinutes?.let { " • $it min target" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onLogRoutineQuick(routine) }) {
                                Text("Log")
                            }
                            OutlinedButton(onClick = { onStartTimerFromRoutine(routine) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Timer")
                            }
                            OutlinedButton(onClick = { onEditRoutine(routine) }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                            OutlinedButton(onClick = { onDeleteRoutine(routine.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitiesTab(
    types: List<CardioCustomActivityType>,
    onAdd: () -> Unit,
    onEdit: (CardioCustomActivityType) -> Unit,
    onDelete: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (types.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No custom activities yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(types, key = { it.id }) { t ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            t.optionalMet?.let {
                                Text("MET ~$it", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEdit(t) }) { Text("Edit") }
                                OutlinedButton(onClick = { onDelete(t.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}

@Composable
private fun CardioLogTabEmbedded(
    state: CardioLibraryState,
    onOpenFullLog: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onOpenFullLog, modifier = Modifier.align(Alignment.End).padding(8.dp)) {
            Text("Full-screen log")
        }
        var selectedDate by remember { mutableStateOf(LocalDate.now()) }
        var showCal by remember { mutableStateOf(false) }
        val entries = remember(state, selectedDate) { state.chronologicalCardioLogFor(selectedDate) }
        DateNavigator(
            selectedDate = selectedDate,
            onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
            onNextDay = { selectedDate = selectedDate.plusDays(1) },
            onPreviousWeek = { selectedDate = selectedDate.minusWeeks(1) },
            onNextWeek = { selectedDate = selectedDate.plusWeeks(1) },
            onTodayClick = { selectedDate = LocalDate.now() },
            onCalendarClick = { showCal = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider()
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No sessions this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { s ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(s.summaryLine(), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                formatCardioLogTime(s.loggedAtEpochSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
    if (showCal) {
        CalendarPopup(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it; showCal = false },
            onDismiss = { showCal = false }
        )
    }
}

@Composable
fun CardioLogScreen(
    state: CardioLibraryState,
    onBack: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCal by remember { mutableStateOf(false) }
    val entries = remember(state, selectedDate) { state.chronologicalCardioLogFor(selectedDate) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cardio log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            DateNavigator(
                selectedDate = selectedDate,
                onPreviousDay = { selectedDate = selectedDate.minusDays(1) },
                onNextDay = { selectedDate = selectedDate.plusDays(1) },
                onPreviousWeek = { selectedDate = selectedDate.minusWeeks(1) },
                onNextWeek = { selectedDate = selectedDate.plusWeeks(1) },
                onTodayClick = { selectedDate = LocalDate.now() },
                onCalendarClick = { showCal = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider()
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No cardio logged for this date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(entries, key = { it.id }) { s ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(s.activity.displayLabel, style = MaterialTheme.typography.titleMedium)
                                Text(s.summaryLine(), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatCardioLogTime(s.loggedAtEpochSeconds),
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
    if (showCal) {
        CalendarPopup(
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it; showCal = false },
            onDismiss = { showCal = false }
        )
    }
}

private fun formatCardioLogTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(formatter)
}

@Composable
fun CardioElapsedTimerFullScreen(
    titleLabel: String,
    dark: Color,
    mid: Color,
    glow: Color,
    onStop: (elapsedSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
        }
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
                val mins = elapsed / 60
                val secs = elapsed % 60
                Text(
                    text = "%d:%02d".format(mins, secs),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    titleLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onStop(elapsed) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop & log")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun CustomActivityDialog(
    existing: CardioCustomActivityType?,
    creating: Boolean,
    onDismiss: () -> Unit,
    onSave: (CardioCustomActivityType) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var metStr by remember(existing?.id) { mutableStateOf(existing?.optionalMet?.toString() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New activity" else "Edit activity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = metStr,
                    onValueChange = { metStr = it },
                    label = { Text("MET (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val met = metStr.toDoubleOrNull()
                    onSave(
                        (existing ?: CardioCustomActivityType()).copy(
                            name = name.trim().ifBlank { "Custom" },
                            optionalMet = met?.takeIf { it > 0 }
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RoutineEditorDialog(
    routine: CardioRoutine?,
    creating: Boolean,
    state: CardioLibraryState,
    onDismiss: () -> Unit,
    onSave: (CardioRoutine) -> Unit
) {
    var name by remember(routine?.id) { mutableStateOf(routine?.name ?: "") }
    var useCustom by remember(routine?.id) {
        mutableStateOf(routine?.activity?.customTypeId != null)
    }
    var selectedCustomId by remember(routine?.id) {
        mutableStateOf(routine?.activity?.customTypeId)
    }
    var selectedBuiltin by remember(routine?.id) {
        mutableStateOf(routine?.activity?.builtin ?: CardioBuiltinActivity.WALK)
    }
    var modality by remember(routine?.id) { mutableStateOf(routine?.modality ?: CardioModality.OUTDOOR) }
    var speedStr by remember(routine?.id) { mutableStateOf(routine?.treadmill?.speed?.toString() ?: "3.0") }
    var speedUnit by remember(routine?.id) {
        mutableStateOf(routine?.treadmill?.speedUnit ?: CardioSpeedUnit.MPH)
    }
    var inclineStr by remember(routine?.id) {
        mutableStateOf(routine?.treadmill?.inclinePercent?.toString() ?: "0")
    }
    var treadDistKmStr by remember(routine?.id) { mutableStateOf("") }
    var loadStr by remember(routine?.id) { mutableStateOf("") }
    var durationStr by remember(routine?.id) {
        mutableStateOf((routine?.targetDurationMinutes ?: 30).toString())
    }
    var notes by remember(routine?.id) { mutableStateOf(routine?.notes ?: "") }
    var selectedDaySet by remember(routine?.id) {
        mutableStateOf(routine?.repeatDays?.toSet() ?: emptySet())
    }

    val builtinForM = if (useCustom) null else selectedBuiltin
    val treadmillApp = builtinForM?.supportsTreadmillModality() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New routine" else "Edit routine") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !useCustom, onClick = { useCustom = false }, label = { Text("Built-in") })
                    FilterChip(selected = useCustom, onClick = { useCustom = true }, label = { Text("Custom") })
                }
                if (useCustom) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.customActivityTypes.forEach { t ->
                            FilterChip(
                                selected = selectedCustomId == t.id,
                                onClick = { selectedCustomId = t.id },
                                label = { Text(t.name) }
                            )
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CardioBuiltinActivity.entries.forEach { b ->
                            FilterChip(
                                selected = selectedBuiltin == b,
                                onClick = { selectedBuiltin = b },
                                label = { Text(b.displayName()) }
                            )
                        }
                    }
                }
                if (treadmillApp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = modality == CardioModality.OUTDOOR,
                            onClick = { modality = CardioModality.OUTDOOR },
                            label = { Text("Outdoor") }
                        )
                        FilterChip(
                            selected = modality == CardioModality.INDOOR_TREADMILL,
                            onClick = { modality = CardioModality.INDOOR_TREADMILL },
                            label = { Text("Treadmill") }
                        )
                    }
                }
                if (modality == CardioModality.INDOOR_TREADMILL && treadmillApp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = speedUnit == CardioSpeedUnit.MPH,
                            onClick = { speedUnit = CardioSpeedUnit.MPH },
                            label = { Text("mph") }
                        )
                        FilterChip(
                            selected = speedUnit == CardioSpeedUnit.KMH,
                            onClick = { speedUnit = CardioSpeedUnit.KMH },
                            label = { Text("km/h") }
                        )
                    }
                    OutlinedTextField(speedStr, { speedStr = it }, label = { Text("Speed") })
                    OutlinedTextField(inclineStr, { inclineStr = it }, label = { Text("Incline %") })
                    OutlinedTextField(treadDistKmStr, { treadDistKmStr = it }, label = { Text("Distance km (opt)") })
                    if (builtinForM == CardioBuiltinActivity.RUCK) {
                        OutlinedTextField(loadStr, { loadStr = it }, label = { Text("Pack lb") })
                    }
                }
                OutlinedTextField(durationStr, { durationStr = it }, label = { Text("Target minutes") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                Text("Repeat days", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CardioWeekday.entries.forEach { d ->
                        val sel = d in selectedDaySet
                        FilterChip(
                            selected = sel,
                            onClick = {
                                selectedDaySet =
                                    if (sel) selectedDaySet - d else selectedDaySet + d
                            },
                            label = { Text(d.shortLabel()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val snap = if (useCustom) {
                        val id = selectedCustomId ?: return@TextButton
                        state.resolveSnapshot(null, id)
                    } else {
                        state.resolveSnapshot(selectedBuiltin, null)
                    }
                    val tm = if (modality == CardioModality.INDOOR_TREADMILL && treadmillApp) {
                        val speed = speedStr.toDoubleOrNull() ?: return@TextButton
                        val inc = inclineStr.toDoubleOrNull() ?: 0.0
                        val km = treadDistKmStr.toDoubleOrNull()
                        val lb = if (builtinForM == CardioBuiltinActivity.RUCK) loadStr.toDoubleOrNull() else null
                        CardioTreadmillParams(
                            speed = speed,
                            speedUnit = speedUnit,
                            inclinePercent = inc,
                            distanceMeters = km?.times(1000.0),
                            loadKg = lb?.times(0.453592)
                        )
                    } else null
                    val dur = durationStr.toIntOrNull() ?: return@TextButton
                    onSave(
                        (routine ?: CardioRoutine()).copy(
                            name = name.trim().ifBlank { "Routine" },
                            activity = snap,
                            modality = modality,
                            treadmill = tm,
                            targetDurationMinutes = dur,
                            repeatDays = selectedDaySet.toList().sortedBy { it.ordinal },
                            notes = notes
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
