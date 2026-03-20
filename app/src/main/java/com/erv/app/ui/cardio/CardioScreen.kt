@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.cardio

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.key
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
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioRoutineStep
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioSessionSource
import com.erv.app.cardio.CardioSpeedUnit
import com.erv.app.cardio.CardioTreadmillParams
import com.erv.app.cardio.CardioWeekday
import com.erv.app.cardio.CardioTimerCompletionResult
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.chronologicalCardioLogFor
import com.erv.app.cardio.effectiveSteps
import com.erv.app.cardio.stepsSummaryLabel
import com.erv.app.cardio.derivedTreadmillDistanceMeters
import com.erv.app.cardio.displayName
import com.erv.app.cardio.label
import com.erv.app.cardio.nowEpochSeconds
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.cardio.shortLabel
import com.erv.app.cardio.summaryLine
import com.erv.app.cardio.supportsTreadmillModality
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.dashboard.CalendarPopup
import com.erv.app.ui.dashboard.DateNavigator
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max

private enum class CardioTab { Activities, Routines }

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
    var activeTab by rememberSaveable(key = "cardio_tab_activities_routines") { mutableIntStateOf(0) }
    var workoutBuilder by remember { mutableStateOf<WorkoutBuilderMode?>(null) }
    var routineEditor by remember { mutableStateOf<CardioRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    var customEditor by remember { mutableStateOf<CardioCustomActivityType?>(null) }
    var creatingCustom by remember { mutableStateOf(false) }
    var activeTimer by remember { mutableStateOf<CardioActiveTimerSession?>(null) }
    var completedWorkoutSummary by remember { mutableStateOf<CardioTimerCompletionResult?>(null) }
    val darkTheme = isSystemInDarkTheme()
    val therapyRedDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow

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

    val summary = completedWorkoutSummary
    if (summary != null) {
        CardioWorkoutSummaryFullScreen(
            session = summary.session,
            elapsedSeconds = summary.elapsedSeconds,
            dark = therapyRedDark,
            mid = therapyRedMid,
            glow = therapyRedGlow,
            onDone = { completedWorkoutSummary = null }
        )
        return
    }

    when (val timer = activeTimer) {
        is CardioActiveTimerSession.Single -> {
            val draft = timer.draft
            CardioElapsedTimerFullScreen(
                titleLabel = draft.title,
                subtitle = draft.modality.label(),
                dark = therapyRedDark,
                mid = therapyRedMid,
                glow = therapyRedGlow,
                onStop = { elapsedSeconds ->
                    scope.launch {
                        val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                        val end = nowEpochSeconds()
                        val raw = draft.toSession(durationMinutes = durationMinutes, endEpoch = end)
                        val session = CardioMetEstimator.applyEstimatedKcal(raw, repository.currentState(), weightKg)
                        activeTimer = null
                        completedWorkoutSummary = CardioTimerCompletionResult(session, elapsedSeconds)
                        repository.addSession(today, session)
                        repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                    }
                },
                onCancel = { activeTimer = null }
            )
            return
        }
        is CardioActiveTimerSession.Multi -> {
            val multiKey = timer.state.currentLegIndex to timer.state.completedSegments.size
            CardioMultiLegTimerFullScreen(
                state = timer.state,
                stateKey = multiKey,
                dark = therapyRedDark,
                mid = therapyRedMid,
                glow = therapyRedGlow,
                onFinishLeg = { elapsedSeconds ->
                    scope.launch {
                        val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                        val end = nowEpochSeconds()
                        val (next, session) = CardioMetEstimator.advanceMultiLegTimer(
                            timer.state,
                            durationMinutes,
                            end,
                            repository.currentState(),
                            weightKg
                        )
                        if (session != null) {
                            activeTimer = null
                            completedWorkoutSummary = CardioTimerCompletionResult(session, null)
                            repository.addSession(today, session)
                            repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                        } else if (next != null) {
                            activeTimer = CardioActiveTimerSession.Multi(next)
                            snackbarHostState.showSnackbar("Leg saved — start next when ready")
                        }
                    }
                },
                onCancel = { activeTimer = null }
            )
            return
        }
        null -> Unit
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
                    containerColor = therapyRedMid,
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
                containerColor = therapyRedDark,
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
                CardioTab.Activities -> ActivitiesTab(
                    state = state,
                    onAddCustom = { creatingCustom = true; customEditor = null },
                    onEditCustom = { customEditor = it; creatingCustom = false },
                    onDeleteCustom = { id ->
                        scope.launch {
                            repository.deleteCustomType(id)
                            syncMaster()
                            snackbarHostState.showSnackbar("Activity type removed")
                        }
                    },
                    onStartWorkout = { snap, modality, treadmill ->
                        activeTimer = CardioActiveTimerSession.Single(
                            CardioTimerSessionDraft.fromQuickSnapshot(
                                activity = snap,
                                modality = modality,
                                treadmill = treadmill,
                                title = snap.displayLabel
                            )
                        )
                    },
                    onLogFromSnapshot = { snap ->
                        workoutBuilder = WorkoutBuilderMode.FromActivitySnapshot(snap)
                    }
                )
                CardioTab.Routines -> RoutinesTab(
                    state = state,
                    onCreateRoutine = { creatingRoutine = true; routineEditor = null },
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
                        CardioTimerSessionDraft.fromRoutine(routine)?.let { d ->
                            activeTimer = CardioActiveTimerSession.Single(d)
                        } ?: CardioMultiLegTimerState.fromRoutine(routine)?.let { m ->
                            activeTimer = CardioActiveTimerSession.Multi(m)
                        }
                    }
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
                activeTimer = CardioActiveTimerSession.Single(draft)
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
    data class FromActivitySnapshot(val snapshot: CardioActivitySnapshot) : WorkoutBuilderMode()
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
    val fromActivity = (mode as? WorkoutBuilderMode.FromActivitySnapshot)?.snapshot
    val modeKey = when (mode) {
        is WorkoutBuilderMode.NewSession -> "ns_" + (template?.id ?: "none")
        is WorkoutBuilderMode.FromActivitySnapshot -> "fa_" + fromActivity!!.displayLabel
    }
    val seedStep = template?.effectiveSteps()?.firstOrNull()
    val seedActivity = fromActivity
        ?: seedStep?.activity
        ?: template?.activity

    var useCustom by remember(modeKey) {
        mutableStateOf(seedActivity?.customTypeId != null && seedActivity.builtin == null)
    }
    var selectedCustomId by remember(modeKey) {
        mutableStateOf(
            seedActivity?.customTypeId ?: state.customActivityTypes.firstOrNull()?.id
        )
    }
    var selectedBuiltin by remember(modeKey) {
        mutableStateOf(seedActivity?.builtin ?: CardioBuiltinActivity.WALK)
    }
    var modality by remember(modeKey) {
        mutableStateOf(
            when {
                fromActivity != null -> CardioModality.OUTDOOR
                else -> seedStep?.modality ?: template?.modality ?: CardioModality.OUTDOOR
            }
        )
    }
    var speedStr by remember(modeKey) {
        mutableStateOf(
            seedStep?.treadmill?.speed?.toString()
                ?: template?.treadmill?.speed?.toString()
                ?: "3.0"
        )
    }
    var speedUnit by remember(modeKey) {
        mutableStateOf(
            seedStep?.treadmill?.speedUnit ?: template?.treadmill?.speedUnit ?: CardioSpeedUnit.MPH
        )
    }
    var inclineStr by remember(modeKey) {
        mutableStateOf(
            seedStep?.treadmill?.inclinePercent?.toString()
                ?: template?.treadmill?.inclinePercent?.toString()
                ?: "0"
        )
    }
    var treadDistKmStr by remember(modeKey) { mutableStateOf("") }
    var loadStr by remember(modeKey) { mutableStateOf("") }
    var outdoorDistKmStr by remember(modeKey) { mutableStateOf("") }
    var durationStr by remember(modeKey) {
        mutableStateOf(
            (seedStep?.targetDurationMinutes ?: template?.targetDurationMinutes ?: 30).toString()
        )
    }
    var routineNameStr by remember(modeKey) { mutableStateOf(template?.name ?: "") }

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
                when (mode) {
                    is WorkoutBuilderMode.FromActivitySnapshot ->
                        "Log completed — ${mode.snapshot.displayLabel}"
                    is WorkoutBuilderMode.NewSession ->
                        if (mode.template == null) "New workout"
                        else "Workout from ${mode.template.name}"
                },
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
                            steps = emptyList(),
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
                        Column(Modifier.weight(1f)) {
                            Text("New routine", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "One activity or several (e.g. bike → run)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                }
            }
            items(state.routines, key = { it.id }) { routine ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                        val legs = routine.effectiveSteps()
                        val sub = buildString {
                            append(routine.stepsSummaryLabel())
                            append(" • ")
                            append(legs.firstOrNull()?.modality?.label() ?: routine.modality.label())
                            val tgt = if (legs.size > 1) {
                                legs.mapNotNull { it.targetDurationMinutes }.takeIf { it.size == legs.size }?.sum()
                            } else null
                            val minHint = tgt ?: routine.targetDurationMinutes
                            minHint?.let { append(" • $it min target") }
                        }
                        Text(
                            sub,
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
private fun StartCardioModalityForTimerDialog(
    activity: CardioActivitySnapshot,
    onDismiss: () -> Unit,
    onStart: (CardioModality, CardioTreadmillParams?) -> Unit
) {
    var modality by remember(activity.displayLabel) { mutableStateOf(CardioModality.OUTDOOR) }
    var speedStr by remember(activity.displayLabel) { mutableStateOf("3.0") }
    var speedUnit by remember(activity.displayLabel) { mutableStateOf(CardioSpeedUnit.MPH) }
    var inclineStr by remember(activity.displayLabel) { mutableStateOf("0") }
    var treadDistKmStr by remember(activity.displayLabel) { mutableStateOf("") }
    var loadStr by remember(activity.displayLabel) { mutableStateOf("") }
    val builtin = activity.builtin
    val indoorValid =
        speedStr.toDoubleOrNull()?.let { it > 0 } == true
    val canStart = when (modality) {
        CardioModality.OUTDOOR -> true
        CardioModality.INDOOR_TREADMILL -> indoorValid
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start ${activity.displayLabel}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Choose outdoor or treadmill before the timer starts. You can enter speed and incline for indoor sessions.",
                    style = MaterialTheme.typography.bodyMedium
                )
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
                if (modality == CardioModality.INDOOR_TREADMILL) {
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
                        label = { Text("Distance km (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (builtin == CardioBuiltinActivity.RUCK) {
                        OutlinedTextField(
                            value = loadStr,
                            onValueChange = { loadStr = it },
                            label = { Text("Pack weight (lb)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (modality) {
                        CardioModality.OUTDOOR -> onStart(CardioModality.OUTDOOR, null)
                        CardioModality.INDOOR_TREADMILL -> {
                            val speed = speedStr.toDoubleOrNull() ?: return@TextButton
                            val inc = inclineStr.toDoubleOrNull() ?: 0.0
                            val km = treadDistKmStr.toDoubleOrNull()
                            val lb = if (builtin == CardioBuiltinActivity.RUCK) loadStr.toDoubleOrNull() else null
                            onStart(
                                CardioModality.INDOOR_TREADMILL,
                                CardioTreadmillParams(
                                    speed = speed,
                                    speedUnit = speedUnit,
                                    inclinePercent = inc,
                                    distanceMeters = km?.times(1000.0),
                                    loadKg = lb?.times(0.453592)
                                )
                            )
                        }
                    }
                    onDismiss()
                },
                enabled = canStart
            ) { Text("Start timer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActivitiesTab(
    state: CardioLibraryState,
    onAddCustom: () -> Unit,
    onEditCustom: (CardioCustomActivityType) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onStartWorkout: (CardioActivitySnapshot, CardioModality, CardioTreadmillParams?) -> Unit,
    onLogFromSnapshot: (CardioActivitySnapshot) -> Unit
) {
    var pendingPick by remember { mutableStateOf<CardioActivitySnapshot?>(null) }
    var pendingModalityForStart by remember { mutableStateOf<CardioActivitySnapshot?>(null) }
    val builtins = remember(state) {
        CardioBuiltinActivity.entries.map { b ->
            b.displayName() to state.resolveSnapshot(b, null)
        }
    }
    val customs = state.customActivityTypes

    pendingModalityForStart?.let { snap ->
        StartCardioModalityForTimerDialog(
            activity = snap,
            onDismiss = { pendingModalityForStart = null },
            onStart = { mod, tm -> onStartWorkout(snap, mod, tm) }
        )
    }

    pendingPick?.let { pick ->
        AlertDialog(
            onDismissRequest = { pendingPick = null },
            title = { Text(pick.displayLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Start a live timer, or log a session you already finished. Walking, running, sprinting, and rucking will ask outdoor vs treadmill first.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            if (pick.builtin?.supportsTreadmillModality() == true) {
                                pendingModalityForStart = pick
                            } else {
                                onStartWorkout(pick, CardioModality.OUTDOOR, null)
                            }
                            pendingPick = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Start workout") }
                    OutlinedButton(
                        onClick = {
                            onLogFromSnapshot(pick)
                            pendingPick = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Log completed") }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingPick = null }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Pick an activity. Combine several into one saved routine on the Routines tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddCustom),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Create custom activity", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Name and optional MET for anything not in the list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }
            item {
                Text("Built-in", style = MaterialTheme.typography.labelLarge)
            }
            items(builtins, key = { it.second.displayLabel }) { (_, snap) ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pendingPick = snap }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(snap.displayLabel, style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Your custom types", style = MaterialTheme.typography.labelLarge)
            }
            if (customs.isEmpty()) {
                item {
                    Text(
                        "No custom activities yet — use the button above to add one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(customs, key = { it.id }) { t ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pendingPick = state.resolveSnapshot(null, t.id)
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.titleSmall)
                                    t.optionalMet?.let {
                                        Text("MET ~$it", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onEditCustom(t) }) { Text("Edit") }
                                OutlinedButton(onClick = { onDeleteCustom(t.id) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CardioLogScreen(
    repository: CardioRepository,
    state: CardioLibraryState,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showCal by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CardioSession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val entries = remember(state, selectedDate) { state.chronologicalCardioLogFor(selectedDate) }
    val darkTheme = isSystemInDarkTheme()
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid

    suspend fun syncDailyLogForSelected() {
        if (relayPool != null && signer != null) {
            repository.currentState().logFor(selectedDate)?.let { log ->
                CardioSync.publishDailyLog(relayPool, signer, log)
            }
        }
    }

    pendingDelete?.let { toRemove ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove workout?") },
            text = {
                Text(
                    "This removes the entry from your log on this device and updates your synced day log.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = toRemove.id
                        pendingDelete = null
                        scope.launch {
                            repository.deleteSession(selectedDate, id)
                            syncDailyLogForSelected()
                            snackbarHostState.showSnackbar("Workout removed")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Cardio log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = therapyRedMid,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
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
                    item {
                        Text(
                            "Tap delete on an entry to remove it from this day.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(entries, key = { it.id }) { s ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.activity.displayLabel, style = MaterialTheme.typography.titleMedium)
                                    Text(s.summaryLine(), style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatCardioLogTime(s.loggedAtEpochSeconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { pendingDelete = s }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete workout")
                                }
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
    subtitle: String? = null,
    dark: Color,
    mid: Color,
    glow: Color,
    onStop: (elapsedSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    var running by remember { mutableStateOf(true) }
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
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
                subtitle?.let { s ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (running) {
                            running = false
                            onStop(elapsed)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop & log")
                }
                OutlinedButton(
                    onClick = onCancel,
                    enabled = running,
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
fun CardioWorkoutSummaryFullScreen(
    session: CardioSession,
    elapsedSeconds: Int?,
    dark: Color,
    mid: Color,
    glow: Color,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(dark, mid, glow)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Workout logged",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                session.activity.displayLabel,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.95f)
            )
            elapsedSeconds?.let { sec ->
                val m = sec / 60
                val s = sec % 60
                Text(
                    "Timer: %d:%02d".format(m, s),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Text(
                "Saved as ${session.durationMinutes} min",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            session.distanceMeters?.takeIf { it > 1 }?.let { d ->
                Text(
                    "Distance: " + String.format("%.2f km", d / 1000.0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            val hr = session.heartRate
            when {
                hr != null && (hr.avgBpm != null || hr.maxBpm != null || hr.minBpm != null) -> {
                    hr.avgBpm?.let {
                        Text(
                            "Avg heart rate: $it bpm",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    val extras = listOfNotNull(
                        hr.maxBpm?.let { "Max $it" },
                        hr.minBpm?.let { "Min $it" }
                    )
                    if (extras.isNotEmpty()) {
                        Text(
                            extras.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
                else -> {
                    Text(
                        "Heart rate: not recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
            session.estimatedKcal?.let { k ->
                Text(
                    "Est. calories: ~${k.toInt()} kcal",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.35f)
            )
            Text(
                session.summaryLine(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = mid)
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
fun CardioMultiLegTimerFullScreen(
    state: CardioMultiLegTimerState,
    stateKey: Any,
    dark: Color,
    mid: Color,
    glow: Color,
    onFinishLeg: (elapsedSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    key(stateKey) {
        var running by remember { mutableStateOf(true) }
        var elapsed by remember { mutableIntStateOf(0) }
        LaunchedEffect(stateKey, running) {
            if (!running) return@LaunchedEffect
            while (true) {
                delay(1000)
                elapsed++
            }
        }
        val isLast = state.currentLegIndex >= state.legs.lastIndex
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Multi-activity workout",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.routineName ?: "Routine",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                    Text(
                        state.legProgressLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
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
                        "Time on this leg",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (running) {
                                running = false
                                onFinishLeg(elapsed)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isLast) "Finish & log workout" else "Finish leg & next")
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = running,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Text("Cancel workout")
                    }
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
                    val trimmed = name.trim().ifBlank { "Custom" }
                    onSave(
                        existing?.copy(name = trimmed, optionalMet = met?.takeIf { it > 0 })
                            ?: CardioCustomActivityType(name = trimmed, optionalMet = met?.takeIf { it > 0 })
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private data class RoutineStepDraft(
    val localId: String = UUID.randomUUID().toString(),
    val useCustom: Boolean = false,
    val selectedCustomId: String? = null,
    val selectedBuiltin: CardioBuiltinActivity = CardioBuiltinActivity.WALK,
    val modality: CardioModality = CardioModality.OUTDOOR,
    val speedStr: String = "3.0",
    val speedUnit: CardioSpeedUnit = CardioSpeedUnit.MPH,
    val inclineStr: String = "0",
    val treadDistKmStr: String = "",
    val loadStr: String = "",
    val targetMinutesStr: String = "30"
)

private fun routineStepDraftsFromRoutine(routine: CardioRoutine?): List<RoutineStepDraft> {
    if (routine == null) return listOf(RoutineStepDraft())
    return routine.effectiveSteps().map { stepToRoutineDraft(it) }
}

private fun stepToRoutineDraft(step: CardioRoutineStep): RoutineStepDraft {
    val snap = step.activity
    val useCustom = snap.customTypeId != null
    val t = step.treadmill
    return RoutineStepDraft(
        useCustom = useCustom,
        selectedCustomId = snap.customTypeId,
        selectedBuiltin = snap.builtin ?: CardioBuiltinActivity.OTHER,
        modality = step.modality,
        speedStr = t?.speed?.toString() ?: "3.0",
        speedUnit = t?.speedUnit ?: CardioSpeedUnit.MPH,
        inclineStr = t?.inclinePercent?.toString() ?: "0",
        treadDistKmStr = t?.distanceMeters?.let { d -> "%.2f".format(d / 1000.0) } ?: "",
        loadStr = t?.loadKg?.let { kg -> "%.0f".format(kg / 0.453592) } ?: "",
        targetMinutesStr = (step.targetDurationMinutes ?: 30).toString()
    )
}

private fun buildRoutineStepFromDraft(
    d: RoutineStepDraft,
    idx: Int,
    state: CardioLibraryState
): CardioRoutineStep? {
    val snap = if (d.useCustom) {
        val id = d.selectedCustomId ?: return null
        state.resolveSnapshot(null, id)
    } else {
        state.resolveSnapshot(d.selectedBuiltin, null)
    }
    val builtinForM = if (d.useCustom) null else d.selectedBuiltin
    val treadmillApp = builtinForM?.supportsTreadmillModality() == true
    val modalityEff = if (treadmillApp) d.modality else CardioModality.OUTDOOR
    val tm = if (modalityEff == CardioModality.INDOOR_TREADMILL && treadmillApp) {
        val speed = d.speedStr.toDoubleOrNull() ?: return null
        val inc = d.inclineStr.toDoubleOrNull() ?: 0.0
        val km = d.treadDistKmStr.toDoubleOrNull()
        val lb = if (builtinForM == CardioBuiltinActivity.RUCK) d.loadStr.toDoubleOrNull() else null
        CardioTreadmillParams(
            speed = speed,
            speedUnit = d.speedUnit,
            inclinePercent = inc,
            distanceMeters = km?.times(1000.0),
            loadKg = lb?.times(0.453592)
        )
    } else null
    val target = d.targetMinutesStr.toIntOrNull() ?: return null
    if (target <= 0) return null
    return CardioRoutineStep(
        activity = snap,
        modality = modalityEff,
        treadmill = tm,
        targetDurationMinutes = target,
        orderIndex = idx
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
    var steps by remember(routine?.id) { mutableStateOf(routineStepDraftsFromRoutine(routine)) }
    var notes by remember(routine?.id) { mutableStateOf(routine?.notes ?: "") }
    var selectedDaySet by remember(routine?.id) {
        mutableStateOf(routine?.repeatDays?.toSet() ?: emptySet())
    }

    fun updateStep(index: Int, block: (RoutineStepDraft) -> RoutineStepDraft) {
        steps = steps.mapIndexed { i, s -> if (i == index) block(s) else s }
    }

    val allStepsValid = steps.indices.all { i ->
        buildRoutineStepFromDraft(steps[i], i, state) != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New routine" else "Edit routine") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add one activity for a simple workout, or several for bricks / tri training. Each leg can use outdoor or treadmill where supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    modifier = Modifier.fillMaxWidth()
                )
                steps.forEachIndexed { index, draft ->
                    val builtinForM = if (draft.useCustom) null else draft.selectedBuiltin
                    val treadmillApp = builtinForM?.supportsTreadmillModality() == true
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Activity ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                Row {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val m = steps.toMutableList()
                                                m[index - 1] = m[index].also { m[index] = m[index - 1] }
                                                steps = m
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < steps.lastIndex) {
                                                val m = steps.toMutableList()
                                                m[index + 1] = m[index].also { m[index] = m[index + 1] }
                                                steps = m
                                            }
                                        },
                                        enabled = index < steps.lastIndex
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                                    }
                                    if (steps.size > 1) {
                                        IconButton(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove leg")
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !draft.useCustom,
                                    onClick = {
                                        updateStep(index) { s ->
                                            val mod =
                                                if (s.selectedBuiltin.supportsTreadmillModality()) s.modality
                                                else CardioModality.OUTDOOR
                                            s.copy(useCustom = false, modality = mod)
                                        }
                                    },
                                    label = { Text("Built-in") }
                                )
                                FilterChip(
                                    selected = draft.useCustom,
                                    onClick = { updateStep(index) { it.copy(useCustom = true, modality = CardioModality.OUTDOOR) } },
                                    label = { Text("Custom") }
                                )
                            }
                            if (draft.useCustom) {
                                if (state.customActivityTypes.isEmpty()) {
                                    Text("Add custom activities on the Activities tab.", color = MaterialTheme.colorScheme.error)
                                } else {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        state.customActivityTypes.forEach { t ->
                                            FilterChip(
                                                selected = draft.selectedCustomId == t.id,
                                                onClick = { updateStep(index) { it.copy(selectedCustomId = t.id) } },
                                                label = { Text(t.name) }
                                            )
                                        }
                                    }
                                }
                            } else {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    CardioBuiltinActivity.entries.forEach { b ->
                                        FilterChip(
                                            selected = draft.selectedBuiltin == b,
                                            onClick = {
                                                updateStep(index) { s ->
                                                    val mod =
                                                        if (b.supportsTreadmillModality()) s.modality
                                                        else CardioModality.OUTDOOR
                                                    s.copy(selectedBuiltin = b, modality = mod)
                                                }
                                            },
                                            label = { Text(b.displayName()) }
                                        )
                                    }
                                }
                            }
                            if (treadmillApp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = draft.modality == CardioModality.OUTDOOR,
                                        onClick = { updateStep(index) { it.copy(modality = CardioModality.OUTDOOR) } },
                                        label = { Text("Outdoor") }
                                    )
                                    FilterChip(
                                        selected = draft.modality == CardioModality.INDOOR_TREADMILL,
                                        onClick = { updateStep(index) { it.copy(modality = CardioModality.INDOOR_TREADMILL) } },
                                        label = { Text("Treadmill") }
                                    )
                                }
                            }
                            if (draft.modality == CardioModality.INDOOR_TREADMILL && treadmillApp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = draft.speedUnit == CardioSpeedUnit.MPH,
                                        onClick = { updateStep(index) { it.copy(speedUnit = CardioSpeedUnit.MPH) } },
                                        label = { Text("mph") }
                                    )
                                    FilterChip(
                                        selected = draft.speedUnit == CardioSpeedUnit.KMH,
                                        onClick = { updateStep(index) { it.copy(speedUnit = CardioSpeedUnit.KMH) } },
                                        label = { Text("km/h") }
                                    )
                                }
                                OutlinedTextField(
                                    draft.speedStr,
                                    { v -> updateStep(index) { s -> s.copy(speedStr = v) } },
                                    label = { Text("Speed") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    draft.inclineStr,
                                    { v -> updateStep(index) { s -> s.copy(inclineStr = v) } },
                                    label = { Text("Incline %") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    draft.treadDistKmStr,
                                    { v -> updateStep(index) { s -> s.copy(treadDistKmStr = v) } },
                                    label = { Text("Distance km (opt)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (builtinForM == CardioBuiltinActivity.RUCK) {
                                    OutlinedTextField(
                                        draft.loadStr,
                                        { v -> updateStep(index) { s -> s.copy(loadStr = v) } },
                                        label = { Text("Pack lb") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            OutlinedTextField(
                                draft.targetMinutesStr,
                                { v -> updateStep(index) { s -> s.copy(targetMinutesStr = v) } },
                                label = { Text("Target minutes (this leg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { steps = steps + RoutineStepDraft() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add activity to this workout")
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                    val routineName = name.trim().ifBlank { "Routine" }
                    val days = selectedDaySet.toList().sortedBy { it.ordinal }
                    val built = steps.mapIndexedNotNull { i, d -> buildRoutineStepFromDraft(d, i, state) }
                    if (built.size != steps.size) return@TextButton
                    val first = built.first()
                    val stepsToPersist = if (built.size > 1) built else emptyList()
                    val totalTarget = built.sumOf { it.targetDurationMinutes ?: 0 }.takeIf { it > 0 }
                    val out = routine?.copy(
                        name = routineName,
                        steps = stepsToPersist,
                        activity = first.activity,
                        modality = first.modality,
                        treadmill = first.treadmill,
                        targetDurationMinutes = totalTarget ?: first.targetDurationMinutes,
                        repeatDays = days,
                        notes = notes
                    ) ?: CardioRoutine(
                        name = routineName,
                        steps = stepsToPersist,
                        activity = first.activity,
                        modality = first.modality,
                        treadmill = first.treadmill,
                        targetDurationMinutes = totalTarget ?: first.targetDurationMinutes,
                        repeatDays = days,
                        notes = notes
                    )
                    onSave(out)
                },
                enabled = name.isNotBlank() && steps.isNotEmpty() && allStepsValid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
