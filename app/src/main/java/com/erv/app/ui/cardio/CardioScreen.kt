@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.cardio

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.formatCardioPackWeightFromKg
import com.erv.app.cardio.ruckLoadKgResolved
import com.erv.app.cardio.CardioCustomActivityType
import com.erv.app.R
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioGpsElevation
import com.erv.app.cardio.CardioGpsForegroundService
import com.erv.app.cardio.CardioGpsMath
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioGpsRecordingHub
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioMetEstimator
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioTrackShareImage
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.CardioQuickLaunch
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioRoutineStep
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioSessionSource
import com.erv.app.cardio.CardioSpeedUnit
import com.erv.app.cardio.CardioTreadmillParams
import com.erv.app.cardio.CardioWeekday
import com.erv.app.cardio.CardioTimerCompletionResult
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.CardioTimerStyle
import com.erv.app.cardio.eligibleForPhoneGps
import com.erv.app.cardio.supportsPhoneGpsTracking
import com.erv.app.cardio.liveDistanceMeters
import com.erv.app.cardio.supportsOutdoorPaceEstimate
import com.erv.app.cardio.chronologicalCardioLogFor
import com.erv.app.cardio.effectiveSteps
import com.erv.app.cardio.stepsSummaryLabel
import com.erv.app.cardio.derivedTreadmillDistanceMeters
import com.erv.app.cardio.distanceFieldLabelOptional
import com.erv.app.cardio.formatCardioAveragePace
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioElevationGainLoss
import com.erv.app.cardio.resolvedElevationMeters
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.metersToCardioDistanceInputString
import com.erv.app.cardio.parseCardioDistanceInputToMeters
import com.erv.app.cardio.defaultSprintIndoorTreadmillParams
import com.erv.app.cardio.displayName
import com.erv.app.cardio.label
import com.erv.app.cardio.nowEpochSeconds
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.cardio.shortLabel
import com.erv.app.cardio.summaryLine
import com.erv.app.cardio.summaryLabel
import com.erv.app.cardio.needsOutdoorRuckWeightPrompt
import com.erv.app.cardio.supportsTreadmillModality
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import com.erv.app.nostr.BlossomUploader
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.Nip96Uploader
import com.erv.app.nostr.UnsignedEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.dashboard.CalendarPopup
import com.erv.app.ui.dashboard.DateNavigator
import com.erv.app.ui.dashboard.datesWithCardioActivity
import com.erv.app.ui.weighttraining.LiveWorkoutInProgressBanner
import com.erv.app.ui.media.WorkoutMediaControlPanel
import com.erv.app.ui.weighttraining.WeightLiveWorkoutFgsDisclosureDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
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
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    initialOpenNewWorkout: Boolean = false,
    onConsumedInitialOpenNewWorkout: () -> Unit = {}
) {
    val state by repository.state.collectAsState(initial = CardioLibraryState())
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    val timerContext = LocalContext.current
    val timerAppContext = remember(timerContext) { timerContext.applicationContext }
    var locationFineGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(timerContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestCardioLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> locationFineGranted = granted }
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable(key = "cardio_tab_activities_routines") { mutableIntStateOf(0) }
    var workoutBuilder by remember { mutableStateOf<WorkoutBuilderMode?>(null) }
    var routineEditor by remember { mutableStateOf<CardioRoutine?>(null) }
    var creatingRoutine by remember { mutableStateOf(false) }
    var quickLaunchEditor by remember { mutableStateOf<CardioQuickLaunch?>(null) }
    var creatingQuickLaunch by remember { mutableStateOf(false) }
    var routinesCreateMenu by remember { mutableStateOf(false) }
    var pendingQuickLaunchRuck by remember { mutableStateOf<CardioQuickLaunch?>(null) }
    var customEditor by remember { mutableStateOf<CardioCustomActivityType?>(null) }
    var creatingCustom by remember { mutableStateOf(false) }
    val activeTimer by cardioLiveWorkoutViewModel.activeTimer.collectAsState()
    val cardioLiveUiExpanded by cardioLiveWorkoutViewModel.cardioLiveUiExpanded.collectAsState()
    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    var showCardioFgsDialog by remember { mutableStateOf(false) }
    var pendingCardioSession by remember { mutableStateOf<CardioActiveTimerSession?>(null) }
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

    fun startOrQueueCardio(session: CardioActiveTimerSession) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            scope.launch {
                snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.")
            }
            return
        }
        if (!fgsDisclosureSeen) {
            pendingCardioSession = session
            showCardioFgsDialog = true
            return
        }
        if (!cardioLiveWorkoutViewModel.tryStartSession(session)) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (cardioLiveWorkoutViewModel.hasActiveTimer) {
                        "Finish or cancel your cardio timer first."
                    } else {
                        "Could not start the cardio timer. Check notification permission and try again."
                    }
                )
            }
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
            logDate = today,
            repository = repository,
            elapsedSeconds = summary.elapsedSeconds,
            distanceUnit = distanceUnit,
            dark = therapyRedDark,
            mid = therapyRedMid,
            glow = therapyRedGlow,
            relayPool = relayPool,
            signer = signer,
            userPreferences = userPreferences,
            onDone = { completedWorkoutSummary = null }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            when (CardioTab.entries[activeTab]) {
                CardioTab.Activities -> FloatingActionButton(
                    onClick = { creatingCustom = true; customEditor = null },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add custom activity")
                }
                CardioTab.Routines -> FloatingActionButton(
                    onClick = { routinesCreateMenu = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add routine or quick start")
                }
            }
        },
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
            if (activeTimer != null && !cardioLiveUiExpanded) {
                LiveWorkoutInProgressBanner(
                    onClick = { cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = stringResource(R.string.live_cardio_in_progress_banner)
                )
            }
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
                    distanceUnit = distanceUnit,
                    onEditCustom = { customEditor = it; creatingCustom = false },
                    onDeleteCustom = { id ->
                        scope.launch {
                            repository.deleteCustomType(id)
                            syncMaster()
                            snackbarHostState.showSnackbar("Activity type removed")
                        }
                    },
                    onStartWorkout = { d ->
                        startOrQueueCardio(CardioActiveTimerSession.Single(d))
                    },
                    onLogFromSnapshot = { snap ->
                        workoutBuilder = WorkoutBuilderMode.FromActivitySnapshot(snap)
                    }
                )
                CardioTab.Routines -> RoutinesTab(
                    state = state,
                    distanceUnit = distanceUnit,
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
                            startOrQueueCardio(CardioActiveTimerSession.Single(d))
                        } ?: CardioMultiLegTimerState.fromRoutine(routine)?.let { m ->
                            startOrQueueCardio(CardioActiveTimerSession.Multi(m))
                        }
                    },
                    onEditQuickLaunch = { quickLaunchEditor = it; creatingQuickLaunch = false },
                    onDeleteQuickLaunch = { id ->
                        scope.launch {
                            repository.deleteQuickLaunch(id)
                            syncMaster()
                            snackbarHostState.showSnackbar("Quick start removed")
                        }
                    },
                    onStartQuickLaunch = { ql ->
                        if (ql.needsOutdoorRuckWeightPrompt()) {
                            pendingQuickLaunchRuck = ql
                        } else {
                            startOrQueueCardio(
                                CardioActiveTimerSession.Single(
                                    CardioTimerSessionDraft.fromQuickLaunch(ql)
                                )
                            )
                        }
                    }
                )
            }
        }
    }

        when (val timer = activeTimer) {
            is CardioActiveTimerSession.Single -> {
                if (cardioLiveUiExpanded) {
                    val draft = timer.draft
                    val paceOnlyTimer = draft.timerStyle is CardioTimerStyle.CountDownDistance
                    val recordGps =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && locationFineGranted && !paceOnlyTimer
                    val showGpsPermissionHint =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && !locationFineGranted && !paceOnlyTimer
                    CardioElapsedTimerFullScreen(
                        draft = draft,
                        distanceUnit = distanceUnit,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        gpsRecordingActive = recordGps,
                        showGpsPermissionHint = showGpsPermissionHint,
                        onRequestLocationPermission = {
                            requestCardioLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onLeaveTimerUi = { cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(false) },
                        onStop = { elapsedSeconds ->
                            val gpsPoints = drainCardioGpsIfNeeded(recordGps, timerAppContext)
                            scope.launch {
                                val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                                val end = nowEpochSeconds()
                                val raw = draft.toSession(
                                    durationMinutes = durationMinutes,
                                    endEpoch = end,
                                    elapsedSecondsForDistance = elapsedSeconds,
                                    gpsPoints = gpsPoints
                                )
                                val session = CardioMetEstimator.applyEstimatedKcal(
                                    raw,
                                    repository.currentState(),
                                    weightKg
                                )
                                cardioLiveWorkoutViewModel.clearSession()
                                completedWorkoutSummary = CardioTimerCompletionResult(session, elapsedSeconds)
                                repository.addSession(today, session)
                                repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                            }
                        },
                        onCancel = {
                            drainCardioGpsIfNeeded(recordGps, timerAppContext)
                            cardioLiveWorkoutViewModel.clearSession()
                        }
                    )
                }
            }
            is CardioActiveTimerSession.Multi -> {
                if (cardioLiveUiExpanded) {
                    val multiKey = timer.state.currentLegIndex to timer.state.completedSegments.size
                    CardioMultiLegTimerFullScreen(
                        state = timer.state,
                        stateKey = multiKey,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        onLeaveWorkoutUi = { cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(false) },
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
                                    cardioLiveWorkoutViewModel.clearSession()
                                    completedWorkoutSummary = CardioTimerCompletionResult(session, null)
                                    repository.addSession(today, session)
                                    repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                                } else if (next != null) {
                                    cardioLiveWorkoutViewModel.replaceSession(CardioActiveTimerSession.Multi(next))
                                    snackbarHostState.showSnackbar("Leg saved — start next when ready")
                                }
                            }
                        },
                        onCancel = { cardioLiveWorkoutViewModel.clearSession() }
                    )
                }
            }
            null -> Unit
        }
    }

    WeightLiveWorkoutFgsDisclosureDialog(
        visible = showCardioFgsDialog,
        onDismiss = {
            showCardioFgsDialog = false
            pendingCardioSession = null
        },
        onContinue = {
            scope.launch {
                userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true)
                showCardioFgsDialog = false
                val pending = pendingCardioSession
                pendingCardioSession = null
                if (pending != null) {
                    if (weightLiveWorkoutViewModel.hasLiveSession) {
                        snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.")
                    } else if (!cardioLiveWorkoutViewModel.tryStartSession(pending)) {
                        snackbarHostState.showSnackbar(
                            if (cardioLiveWorkoutViewModel.hasActiveTimer) {
                                "Finish or cancel your cardio timer first."
                            } else {
                                "Could not start the cardio timer. Check notification permission and try again."
                            }
                        )
                    }
                }
            }
        }
    )

    workoutBuilder?.let { mode ->
        WorkoutBuilderBottomSheet(
            mode = mode,
            state = state,
            weightKg = weightKg,
            distanceUnit = distanceUnit,
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
                startOrQueueCardio(CardioActiveTimerSession.Single(draft))
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
            distanceUnit = distanceUnit,
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

    if (routinesCreateMenu) {
        AlertDialog(
            onDismissRequest = { routinesCreateMenu = false },
            title = { Text("Create") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            creatingRoutine = true
                            routineEditor = null
                            routinesCreateMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Multi-leg routine")
                    }
                    TextButton(
                        onClick = {
                            creatingQuickLaunch = true
                            quickLaunchEditor = null
                            routinesCreateMenu = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quick start session")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { routinesCreateMenu = false }) { Text("Cancel") }
            }
        )
    }

    if (quickLaunchEditor != null || creatingQuickLaunch) {
        CardioQuickLaunchEditorDialog(
            existing = quickLaunchEditor,
            creating = creatingQuickLaunch,
            state = state,
            distanceUnit = distanceUnit,
            onDismiss = {
                quickLaunchEditor = null
                creatingQuickLaunch = false
            },
            onSave = { launch ->
                scope.launch {
                    if (quickLaunchEditor != null) repository.updateQuickLaunch(launch)
                    else repository.addQuickLaunch(launch)
                    syncMaster()
                    snackbarHostState.showSnackbar("Quick start saved")
                }
                quickLaunchEditor = null
                creatingQuickLaunch = false
            }
        )
    }

    pendingQuickLaunchRuck?.let { ql ->
        OutdoorRuckPackWeightDialog(
            quickLaunchName = ql.name,
            defaultRuckLoadKg = ql.defaultRuckLoadKg,
            onDismiss = { pendingQuickLaunchRuck = null },
            onStart = { kg ->
                startOrQueueCardio(
                    CardioActiveTimerSession.Single(
                        CardioTimerSessionDraft.fromQuickLaunch(ql, ruckLoadKg = kg)
                    )
                )
                pendingQuickLaunchRuck = null
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
    distanceUnit: CardioDistanceUnit,
    /** When non-null, "Log session" is stored on this day (e.g. log screen date). Otherwise uses sheet anchor day. */
    targetLogDate: LocalDate? = null,
    /** Hides start timer and save routine — for backdating from the log screen only. */
    logOnlyMode: Boolean = false,
    onDismiss: () -> Unit,
    onLog: (LocalDate, CardioSession) -> Unit,
    onSaveRoutine: (CardioRoutine) -> Unit,
    onStartTimer: (CardioTimerSessionDraft) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetAnchorDate = remember { LocalDate.now() }
    val dateForLog = targetLogDate ?: sheetAnchorDate
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
    var useCountDownTimer by remember(modeKey) { mutableStateOf(false) }
    var outdoorPaceStr by remember(modeKey) { mutableStateOf("") }
    var outdoorPaceUnit by remember(modeKey) { mutableStateOf(CardioSpeedUnit.MPH) }
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
                        when {
                            logOnlyMode && mode.template == null ->
                                "Log workout — ${dateForLog.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
                            mode.template == null -> "New workout"
                            else -> "Workout from ${mode.template.name}"
                        }
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
                    label = { Text(distanceUnit.distanceFieldLabelOptional()) },
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
                    label = { Text(distanceUnit.distanceFieldLabelOptional()) },
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
                val snapForPace = remember(useCustom, selectedCustomId, selectedBuiltin) {
                    if (useCustom) {
                        selectedCustomId?.let { state.resolveSnapshot(null, it) }
                    } else {
                        state.resolveSnapshot(selectedBuiltin, null)
                    }
                }
                if (!logOnlyMode && snapForPace?.supportsOutdoorPaceEstimate() == true) {
                    Text(
                        if (snapForPace.supportsPhoneGpsTracking()) {
                            "Optional avg speed — pace × time on the timer. GPS route recording is optional (Settings → Cardio GPS, with location permission)."
                        } else {
                            "Optional avg speed — pace × time on the timer."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.MPH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.MPH },
                            label = { Text("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { Text("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = outdoorPaceStr,
                        onValueChange = { outdoorPaceStr = it },
                        label = { Text("Avg speed (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OutlinedTextField(
                value = durationStr,
                onValueChange = { durationStr = it },
                label = { Text("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (!logOnlyMode) {
                Text("Live timer", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useCountDownTimer,
                        onClick = { useCountDownTimer = false },
                        label = { Text("Count up") }
                    )
                    FilterChip(
                        selected = useCountDownTimer,
                        onClick = { useCountDownTimer = true },
                        label = { Text("Count down") }
                    )
                }
                if (useCountDownTimer) {
                    Text(
                        "Countdown length uses duration above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = routineNameStr,
                    onValueChange = { routineNameStr = it },
                    label = { Text("Routine name (save only)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val validationError = remember(
                useCustom, selectedCustomId, durationStr, modality, speedStr, treadmillApplicable,
                state.customActivityTypes, outdoorPaceStr
            ) {
                val duration = durationStr.toIntOrNull()
                if (duration == null || duration <= 0) "Enter a valid duration"
                else if (useCustom && state.customActivityTypes.isEmpty()) "Add a custom activity first"
                else if (useCustom && selectedCustomId == null) "Pick a custom activity"
                else if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable) {
                    val sp = speedStr.toDoubleOrNull()
                    if (sp == null || sp <= 0) "Enter treadmill speed"
                    else null
                } else if (modality == CardioModality.OUTDOOR && outdoorPaceStr.isNotBlank()) {
                    if (outdoorPaceStr.toDoubleOrNull()?.let { it > 0 } != true) {
                        "Enter a valid avg speed or leave it blank"
                    } else null
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
                val treadDist = treadDistKmStr.toDoubleOrNull()
                    ?.let { parseCardioDistanceInputToMeters(it, distanceUnit) }
                val loadLb = if (builtinForModality == CardioBuiltinActivity.RUCK) loadStr.toDoubleOrNull() else null
                val loadKg = loadLb?.times(0.453592)
                return CardioTreadmillParams(
                    speed = speed,
                    speedUnit = speedUnit,
                    inclinePercent = inc,
                    distanceMeters = treadDist,
                    loadKg = loadKg
                )
            }

            fun buildSession(source: CardioSessionSource): CardioSession? {
                val snap = buildSnapshot() ?: return null
                val duration = durationStr.toIntOrNull() ?: return null
                if (duration <= 0) return null
                val tm = buildTreadmill()
                if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable && tm == null) return null
                var distM: Double? = outdoorDistKmStr.toDoubleOrNull()
                    ?.let { parseCardioDistanceInputToMeters(it, distanceUnit) }
                if (distM == null && tm != null) {
                    distM = tm.distanceMeters ?: derivedTreadmillDistanceMeters(tm, duration)
                }
                val outdoorRuckKg =
                    if (modality == CardioModality.OUTDOOR && snap.builtin == CardioBuiltinActivity.RUCK) {
                        loadStr.toDoubleOrNull()?.takeIf { it > 0 }?.times(0.453592)
                    } else null
                val base = CardioSession(
                    activity = snap,
                    modality = modality,
                    treadmill = tm,
                    durationMinutes = duration,
                    distanceMeters = distM,
                    source = source,
                    heartRate = CardioHrScaffolding(),
                    estimatedKcal = null,
                    ruckLoadKg = outdoorRuckKg
                )
                return CardioMetEstimator.applyEstimatedKcal(base, state, weightKg)
            }

            if (logOnlyMode) {
                Button(
                    onClick = {
                        val s = buildSession(CardioSessionSource.MANUAL) ?: return@Button
                        onLog(dateForLog, s)
                    },
                    enabled = validationError == null && buildSnapshot() != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Log session") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val s = buildSession(CardioSessionSource.MANUAL) ?: return@Button
                            onLog(dateForLog, s)
                        },
                        enabled = validationError == null && buildSnapshot() != null
                    ) { Text("Log session") }
                    OutlinedButton(
                        onClick = {
                            val snap = buildSnapshot() ?: return@OutlinedButton
                            val tm = buildTreadmill()
                            if (modality == CardioModality.INDOOR_TREADMILL && treadmillApplicable && tm == null) return@OutlinedButton
                            val durMin = durationStr.toIntOrNull() ?: return@OutlinedButton
                            val pace = if (modality == CardioModality.OUTDOOR) {
                                outdoorPaceStr.toDoubleOrNull()?.takeIf { it > 0 }
                            } else null
                            val paceUnit = pace?.let { outdoorPaceUnit }
                            val ruckKg =
                                if (modality == CardioModality.OUTDOOR && snap.builtin == CardioBuiltinActivity.RUCK) {
                                    loadStr.toDoubleOrNull()?.takeIf { it > 0 }?.times(0.453592)
                                } else null
                            onStartTimer(
                                CardioTimerSessionDraft.fromQuickSnapshot(
                                    activity = snap,
                                    modality = modality,
                                    treadmill = tm,
                                    title = snap.displayLabel,
                                    timerStyle = if (useCountDownTimer) {
                                        CardioTimerStyle.CountDown(durMin * 60)
                                    } else {
                                        CardioTimerStyle.CountUp
                                    },
                                    outdoorPaceSpeed = pace,
                                    outdoorPaceSpeedUnit = paceUnit,
                                    ruckLoadKg = ruckKg
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
            }

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
    distanceUnit: CardioDistanceUnit,
    onEditRoutine: (CardioRoutine) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onLogRoutineQuick: (CardioRoutine) -> Unit,
    onStartTimerFromRoutine: (CardioRoutine) -> Unit,
    onEditQuickLaunch: (CardioQuickLaunch) -> Unit,
    onDeleteQuickLaunch: (String) -> Unit,
    onStartQuickLaunch: (CardioQuickLaunch) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Quick start",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "One activity, timer pre-filled. Tap + → Quick start session. Outdoor rucks ask for pack weight when you start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.quickLaunches, key = { it.id }) { ql ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(ql.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            ql.summaryLabel(distanceUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onStartQuickLaunch(ql) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Start")
                            }
                            OutlinedButton(onClick = { onEditQuickLaunch(ql) }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                            OutlinedButton(onClick = { onDeleteQuickLaunch(ql.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Multi-leg routines",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Combine activities into one routine. Tap + → Multi-leg routine (e.g. bike → run).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
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
    distanceUnit: CardioDistanceUnit,
    onDismiss: () -> Unit,
    onModalityChosen: (CardioModality, CardioTreadmillParams?) -> Unit
) {
    var showTreadmillForm by remember(activity.displayLabel) { mutableStateOf(false) }
    var speedStr by remember(activity.displayLabel) { mutableStateOf("3.0") }
    var speedUnit by remember(activity.displayLabel) { mutableStateOf(CardioSpeedUnit.MPH) }
    var inclineStr by remember(activity.displayLabel) { mutableStateOf("0") }
    var treadDistKmStr by remember(activity.displayLabel) { mutableStateOf("") }
    var loadStr by remember(activity.displayLabel) { mutableStateOf("") }
    val builtin = activity.builtin

    if (!showTreadmillForm) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Start ${activity.displayLabel}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (builtin == CardioBuiltinActivity.SPRINT) {
                            "Sprinting: pick outdoor or treadmill next. Treadmill skips belt details and uses a default speed only for on-screen estimates."
                        } else {
                            "Choose outdoor or treadmill before the timer starts."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = {
                            onModalityChosen(CardioModality.OUTDOOR, null)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Outdoor") }
                    Button(
                        onClick = {
                            if (builtin == CardioBuiltinActivity.SPRINT) {
                                onModalityChosen(
                                    CardioModality.INDOOR_TREADMILL,
                                    defaultSprintIndoorTreadmillParams()
                                )
                                onDismiss()
                            } else {
                                showTreadmillForm = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Treadmill") }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    } else {
        val indoorValid = speedStr.toDoubleOrNull()?.let { it > 0 } == true
        AlertDialog(
            onDismissRequest = { showTreadmillForm = false },
            title = { Text("Treadmill — ${activity.displayLabel}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Enter speed and incline for your treadmill session.",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        label = { Text(distanceUnit.distanceFieldLabelOptional()) },
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val speed = speedStr.toDoubleOrNull() ?: return@TextButton
                        val inc = inclineStr.toDoubleOrNull() ?: 0.0
                        val distM = treadDistKmStr.toDoubleOrNull()
                            ?.let { parseCardioDistanceInputToMeters(it, distanceUnit) }
                        val lb = if (builtin == CardioBuiltinActivity.RUCK) loadStr.toDoubleOrNull() else null
                        onModalityChosen(
                            CardioModality.INDOOR_TREADMILL,
                            CardioTreadmillParams(
                                speed = speed,
                                speedUnit = speedUnit,
                                inclinePercent = inc,
                                distanceMeters = distM,
                                loadKg = lb?.times(0.453592)
                            )
                        )
                        onDismiss()
                    },
                    enabled = indoorValid
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showTreadmillForm = false }) { Text("Back") }
            }
        )
    }
}

private data class PendingCardioTimerOpts(
    val snap: CardioActivitySnapshot,
    val modality: CardioModality,
    val treadmill: CardioTreadmillParams?
)

@Composable
private fun CardioTimerStartOptionsDialog(
    activity: CardioActivitySnapshot,
    modality: CardioModality,
    distanceUnit: CardioDistanceUnit,
    onDismiss: () -> Unit,
    onConfirm: (
        style: CardioTimerStyle,
        outdoorPace: Double?,
        outdoorPaceUnit: CardioSpeedUnit?,
        ruckLoadKg: Double?
    ) -> Unit
) {
    val isSprint = activity.builtin == CardioBuiltinActivity.SPRINT
    val sprintIndoor = isSprint && modality == CardioModality.INDOOR_TREADMILL
    val sprintOutdoor = isSprint && modality == CardioModality.OUTDOOR

    val dialogKey = "${activity.displayLabel}_${modality}_${distanceUnit}"
    var countDown by remember(dialogKey) { mutableStateOf(false) }
    var sprintOutdoorByTime by remember(dialogKey) { mutableStateOf(true) }
    var countDownMinutesStr by remember(dialogKey) {
        mutableStateOf(
            when {
                sprintIndoor -> "5"
                sprintOutdoor -> "10"
                else -> "30"
            }
        )
    }
    var sprintTargetDistStr by remember(dialogKey) {
        mutableStateOf(
            if (distanceUnit == CardioDistanceUnit.MILES) "0.25" else "0.4"
        )
    }
    var outdoorPaceStr by remember(dialogKey) { mutableStateOf("") }
    var outdoorPaceUnit by remember(dialogKey) { mutableStateOf(CardioSpeedUnit.MPH) }
    var ruckLoadStr by remember(dialogKey) { mutableStateOf("") }

    val isRuckOutdoor =
        activity.builtin == CardioBuiltinActivity.RUCK && modality == CardioModality.OUTDOOR

    val showOptionalOutdoorPace =
        modality == CardioModality.OUTDOOR && activity.supportsOutdoorPaceEstimate() &&
            (!isSprint || (sprintOutdoor && sprintOutdoorByTime))
    val paceOptionalValid = outdoorPaceStr.isBlank() ||
        outdoorPaceStr.toDoubleOrNull()?.let { it > 0 } == true
    val countDownValid = !countDown || (countDownMinutesStr.toIntOrNull()?.let { it > 0 } == true)
    val sprintIndoorValid = countDownMinutesStr.toIntOrNull()?.let { it > 0 } == true
    val sprintOutdoorTimeValid =
        countDownMinutesStr.toIntOrNull()?.let { it > 0 } == true && paceOptionalValid
    val sprintOutdoorDistValid =
        sprintTargetDistStr.toDoubleOrNull()?.let { it > 0 } == true &&
            outdoorPaceStr.toDoubleOrNull()?.let { it > 0 } == true

    val confirmEnabled = when {
        sprintIndoor -> sprintIndoorValid
        sprintOutdoor && sprintOutdoorByTime -> sprintOutdoorTimeValid
        sprintOutdoor && !sprintOutdoorByTime -> sprintOutdoorDistValid
        else -> paceOptionalValid && countDownValid
    }

    val showTimeCountdownField =
        sprintIndoor || (sprintOutdoor && sprintOutdoorByTime) || (!isSprint && countDown)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timer — ${activity.displayLabel}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    when {
                        sprintIndoor ->
                            "Sprint indoors always uses a time countdown. A default belt speed is used only for estimates on the timer; adjust the logged session if needed."
                        sprintOutdoor ->
                            "Sprint outdoors: count down to a target time, or to a target distance estimated from your average pace."
                        else ->
                            "Choose whether the main clock counts up or down. On a treadmill, distance updates from speed × time when you did not enter a fixed distance."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isSprint) {
                    Text("Main clock", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !countDown,
                            onClick = { countDown = false },
                            label = { Text("Count up") }
                        )
                        FilterChip(
                            selected = countDown,
                            onClick = { countDown = true },
                            label = { Text("Count down") }
                        )
                    }
                }
                if (sprintOutdoor) {
                    Text("Sprint target", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sprintOutdoorByTime,
                            onClick = { sprintOutdoorByTime = true },
                            label = { Text("Time") }
                        )
                        FilterChip(
                            selected = !sprintOutdoorByTime,
                            onClick = { sprintOutdoorByTime = false },
                            label = { Text("Distance") }
                        )
                    }
                }
                if (showTimeCountdownField) {
                    OutlinedTextField(
                        value = countDownMinutesStr,
                        onValueChange = { countDownMinutesStr = it },
                        label = { Text("Countdown (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (sprintOutdoor && !sprintOutdoorByTime) {
                    Text(
                        "Average speed is required to estimate how far you've gone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.MPH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.MPH },
                            label = { Text("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { Text("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = sprintTargetDistStr,
                        onValueChange = { sprintTargetDistStr = it },
                        label = {
                            Text(
                                when (distanceUnit) {
                                    CardioDistanceUnit.MILES -> "Target distance (mi)"
                                    CardioDistanceUnit.KILOMETERS -> "Target distance (km)"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = outdoorPaceStr,
                        onValueChange = { outdoorPaceStr = it },
                        label = { Text("Average speed") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (showOptionalOutdoorPace) {
                    Text(
                        if (activity.supportsPhoneGpsTracking()) {
                            "Optional average speed — pace × time on the timer. GPS route recording is optional (Settings → Cardio GPS, with location permission). Leave blank if you prefer not to."
                        } else {
                            "Optional average speed — pace × time on the timer. Leave blank if you prefer not to."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.MPH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.MPH },
                            label = { Text("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { Text("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = outdoorPaceStr,
                        onValueChange = { outdoorPaceStr = it },
                        label = { Text("Avg speed (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isRuckOutdoor) {
                    OutlinedTextField(
                        value = ruckLoadStr,
                        onValueChange = { ruckLoadStr = it },
                        label = { Text("Pack weight (lb, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val style = when {
                        sprintIndoor -> {
                            val m = countDownMinutesStr.toIntOrNull() ?: return@TextButton
                            CardioTimerStyle.CountDown(m * 60)
                        }
                        sprintOutdoor && sprintOutdoorByTime -> {
                            val m = countDownMinutesStr.toIntOrNull() ?: return@TextButton
                            CardioTimerStyle.CountDown(m * 60)
                        }
                        sprintOutdoor && !sprintOutdoorByTime -> {
                            val distVal = sprintTargetDistStr.toDoubleOrNull() ?: return@TextButton
                            CardioTimerStyle.CountDownDistance(
                                parseCardioDistanceInputToMeters(distVal, distanceUnit)
                            )
                        }
                        countDown -> {
                            val m = countDownMinutesStr.toIntOrNull() ?: return@TextButton
                            CardioTimerStyle.CountDown(m * 60)
                        }
                        else -> CardioTimerStyle.CountUp
                    }
                    val pace = when {
                        sprintOutdoor && !sprintOutdoorByTime ->
                            outdoorPaceStr.toDoubleOrNull() ?: return@TextButton
                        else -> outdoorPaceStr.toDoubleOrNull()?.takeIf { it > 0 }
                    }
                    val pUnit = when {
                        sprintOutdoor && !sprintOutdoorByTime -> outdoorPaceUnit
                        pace != null -> outdoorPaceUnit
                        else -> null
                    }
                    val ruckKg = if (isRuckOutdoor) {
                        ruckLoadStr.toDoubleOrNull()?.takeIf { it > 0 }?.times(0.453592)
                    } else null
                    onConfirm(style, pace, pUnit, ruckKg)
                    onDismiss()
                },
                enabled = confirmEnabled
            ) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ActivitiesTab(
    state: CardioLibraryState,
    distanceUnit: CardioDistanceUnit,
    onEditCustom: (CardioCustomActivityType) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onStartWorkout: (CardioTimerSessionDraft) -> Unit,
    onLogFromSnapshot: (CardioActivitySnapshot) -> Unit
) {
    var pendingPick by remember { mutableStateOf<CardioActivitySnapshot?>(null) }
    var pendingModalityForStart by remember { mutableStateOf<CardioActivitySnapshot?>(null) }
    var pendingTimerOpts by remember { mutableStateOf<PendingCardioTimerOpts?>(null) }
    val builtins = remember(state) {
        CardioBuiltinActivity.entries.map { b ->
            b.displayName() to state.resolveSnapshot(b, null)
        }
    }
    val customs = state.customActivityTypes

    pendingModalityForStart?.let { snap ->
        StartCardioModalityForTimerDialog(
            activity = snap,
            distanceUnit = distanceUnit,
            onDismiss = { pendingModalityForStart = null },
            onModalityChosen = { mod, tm ->
                pendingModalityForStart = null
                pendingTimerOpts = PendingCardioTimerOpts(snap, mod, tm)
            }
        )
    }

    pendingTimerOpts?.let { p ->
        CardioTimerStartOptionsDialog(
            activity = p.snap,
            modality = p.modality,
            distanceUnit = distanceUnit,
            onDismiss = { pendingTimerOpts = null },
            onConfirm = { style, pace, paceUnit, ruckLoadKg ->
                onStartWorkout(
                    CardioTimerSessionDraft.fromQuickSnapshot(
                        activity = p.snap,
                        modality = p.modality,
                        treadmill = p.treadmill,
                        title = p.snap.displayLabel,
                        timerStyle = style,
                        outdoorPaceSpeed = pace,
                        outdoorPaceSpeedUnit = paceUnit,
                        ruckLoadKg = ruckLoadKg
                    )
                )
            }
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
                                pendingTimerOpts = PendingCardioTimerOpts(
                                    pick,
                                    CardioModality.OUTDOOR,
                                    null
                                )
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
                        Icon(
                            cardioActivityListIcon(snap),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Custom", style = MaterialTheme.typography.labelLarge)
            }
            if (customs.isEmpty()) {
                item {
                    Text(
                        "No custom activities yet — tap + in the lower corner to add one.",
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
                                Icon(
                                    cardioActivityListIcon(state.resolveSnapshot(null, t.id)),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
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
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenSessionDetail: (logDate: LocalDate, sessionId: String) -> Unit = { _, _ -> },
    /** When set (e.g. from dashboard backfill), log screen starts on this day. */
    initialSelectedDate: LocalDate? = null,
    /** When true, calendar opens immediately (e.g. backfill flow from dashboard). */
    openCalendarInitially: Boolean = false
) {
    var selectedDate by remember(initialSelectedDate) {
        mutableStateOf(initialSelectedDate ?: LocalDate.now())
    }
    var showCal by remember(openCalendarInitially) {
        mutableStateOf(openCalendarInitially)
    }
    var showManualLog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CardioSession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val entries = remember(state, selectedDate) { state.chronologicalCardioLogFor(selectedDate) }
    val datesWithActivity = remember(state) { datesWithCardioActivity(state) }
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
                title = { Text("Cardio Log") },
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
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManualLog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add workout") }
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No cardio logged for this date.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap Add workout to log a session for this day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Tap an entry for full details. Delete removes it from this day.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(entries, key = { it.id }) { s ->
                        ElevatedCard(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSessionDetail(selectedDate, s.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(s.activity.displayLabel, style = MaterialTheme.typography.titleMedium)
                                        if (!s.routeImageUrl.isNullOrBlank()) {
                                            Icon(
                                                Icons.Default.Image,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(s.summaryLine(distanceUnit), style = MaterialTheme.typography.bodySmall)
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
            onDismiss = { showCal = false },
            datesWithActivity = datesWithActivity
        )
    }

    if (showManualLog) {
        WorkoutBuilderBottomSheet(
            mode = WorkoutBuilderMode.NewSession(null),
            state = state,
            weightKg = weightKg,
            distanceUnit = distanceUnit,
            targetLogDate = selectedDate,
            logOnlyMode = true,
            onDismiss = { showManualLog = false },
            onLog = { date, session ->
                scope.launch {
                    repository.addSession(date, session)
                    if (relayPool != null && signer != null) {
                        repository.currentState().logFor(date)?.let { log ->
                            CardioSync.publishDailyLog(relayPool, signer, log)
                        }
                    }
                    showManualLog = false
                    snackbarHostState.showSnackbar("Session logged")
                }
            },
            onSaveRoutine = { },
            onStartTimer = { }
        )
    }
}

internal fun drainCardioGpsIfNeeded(wasRecording: Boolean, appContext: Context): List<CardioGpsPoint> {
    CardioGpsForegroundService.stop(appContext)
    return if (wasRecording) CardioGpsRecordingHub.snapshotAndClear()
    else {
        CardioGpsRecordingHub.clear()
        emptyList()
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
private fun CardioLiveAveragePaceBlock(
    label: String,
    elapsedSeconds: Int,
    distanceMeters: Double,
    distanceUnit: CardioDistanceUnit
) {
    val pace = formatCardioAveragePace(elapsedSeconds, distanceMeters, distanceUnit) ?: return
    Spacer(Modifier.height(8.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = pace,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = 0.92f)
    )
}

@Composable
fun CardioElapsedTimerFullScreen(
    draft: CardioTimerSessionDraft,
    distanceUnit: CardioDistanceUnit,
    dark: Color,
    mid: Color,
    glow: Color,
    gpsRecordingActive: Boolean = false,
    showGpsPermissionHint: Boolean = false,
    onRequestLocationPermission: () -> Unit = {},
    /** Back arrow: leave full-screen UI; timer and optional GPS keep running (like weight training). */
    onLeaveTimerUi: (() -> Unit)? = null,
    onStop: (elapsedSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val timeCountdownCap = (draft.timerStyle as? CardioTimerStyle.CountDown)?.totalSeconds
    val distanceCountdownTarget = (draft.timerStyle as? CardioTimerStyle.CountDownDistance)?.targetMeters
    val tickKey = draft.startEpoch
    var showMediaSheet by remember(tickKey) { mutableStateOf(false) }
    var running by remember(tickKey) { mutableStateOf(true) }
    var finished by remember(tickKey) { mutableStateOf(false) }
    var tick by remember(tickKey) { mutableIntStateOf(0) }

    val workoutElapsedSeconds = remember(tick, draft.startEpoch) {
        (nowEpochSeconds() - draft.startEpoch).coerceAtLeast(0).toInt()
    }

    fun complete(elapsed: Int) {
        if (finished) return
        finished = true
        running = false
        onStop(elapsed)
    }

    LaunchedEffect(gpsRecordingActive, tickKey) {
        if (gpsRecordingActive) {
            try {
                CardioGpsForegroundService.start(context.applicationContext, draft.title, draft.startEpoch)
            } catch (_: Exception) {
                // Avoid crashing the app if FGS start is disallowed or fails (e.g. API 31+ restrictions).
            }
        }
    }

    val gpsPoints by CardioGpsRecordingHub.pointsFlow.collectAsState(initial = emptyList())
    val liveGpsMeters = remember(gpsPoints) {
        if (gpsPoints.size >= 2) CardioGpsMath.pathLengthMeters(gpsPoints) else null
    }

    LaunchedEffect(tickKey, running, finished) {
        while (true) {
            if (!running || finished) break
            delay(1000)
            tick++
            val elapsed = (nowEpochSeconds() - draft.startEpoch).coerceAtLeast(0).toInt()
            val tCap = timeCountdownCap
            if (tCap != null && elapsed >= tCap) {
                complete(tCap)
                break
            }
            val dTarget = distanceCountdownTarget
            if (dTarget != null) {
                val covered = draft.liveDistanceMeters(elapsed) ?: 0.0
                if (covered >= dTarget) {
                    complete(elapsed)
                    break
                }
            }
        }
    }

    val coveredM = draft.liveDistanceMeters(workoutElapsedSeconds) ?: 0.0
    val remainingDistance =
        distanceCountdownTarget?.let { max(0.0, it - coveredM) }
    val mainClockSeconds =
        if (timeCountdownCap != null) max(0, timeCountdownCap - workoutElapsedSeconds)
        else workoutElapsedSeconds
    val distM = draft.liveDistanceMeters(workoutElapsedSeconds)

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
                if (onLeaveTimerUi != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onLeaveTimerUi) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Leave timer",
                                tint = Color.White
                            )
                        }
                        Text(
                            "Session in progress",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showMediaSheet = !showMediaSheet }) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = stringResource(R.string.media_control_cd_music),
                                tint = Color.White.copy(alpha = if (showMediaSheet) 1f else 0.88f)
                            )
                        }
                    }
                } else {
                    Text(
                        "Session in progress",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                if (showGpsPermissionHint) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onRequestLocationPermission,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Text(stringResource(R.string.cardio_timer_gps_allow_location))
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    when {
                        remainingDistance != null -> "Remaining (est.)"
                        timeCountdownCap != null -> "Remaining"
                        else -> "Elapsed"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(4.dp))
                if (remainingDistance != null) {
                    Text(
                        text = formatCardioDistanceFromMeters(remainingDistance, distanceUnit),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                    val em = workoutElapsedSeconds / 60
                    val es = workoutElapsedSeconds % 60
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Elapsed %d:%02d".format(em, es),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    CardioLiveAveragePaceBlock(
                        label = stringResource(R.string.cardio_timer_avg_pace),
                        elapsedSeconds = workoutElapsedSeconds,
                        distanceMeters = coveredM,
                        distanceUnit = distanceUnit
                    )
                } else {
                    val mins = mainClockSeconds / 60
                    val secs = mainClockSeconds % 60
                    Text(
                        text = "%d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    draft.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    draft.modality.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                if (remainingDistance == null) {
                    if (gpsRecordingActive) {
                        Spacer(Modifier.height(12.dp))
                        if (liveGpsMeters != null) {
                            Text(
                                formatCardioDistanceFromMeters(liveGpsMeters, distanceUnit),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                            Text(
                                stringResource(R.string.cardio_timer_gps_from_route),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            CardioLiveAveragePaceBlock(
                                label = stringResource(R.string.cardio_timer_avg_pace_gps),
                                elapsedSeconds = workoutElapsedSeconds,
                                distanceMeters = liveGpsMeters,
                                distanceUnit = distanceUnit
                            )
                        } else {
                            Text(
                                stringResource(R.string.cardio_timer_gps_recording),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                    distM?.let { d ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "~${formatCardioDistanceFromMeters(d, distanceUnit)} (est.)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            when {
                                gpsRecordingActive && liveGpsMeters != null -> "Pace × time (comparison)"
                                gpsRecordingActive -> "Pace × time while GPS locks"
                                else -> "Distance from pace × time"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                        CardioLiveAveragePaceBlock(
                            label = stringResource(R.string.cardio_timer_avg_pace_est),
                            elapsedSeconds = workoutElapsedSeconds,
                            distanceMeters = d,
                            distanceUnit = distanceUnit
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showMediaSheet) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        WorkoutMediaControlPanel(
                            useLightOnDarkBackground = true,
                            showHeaderTitle = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (running && !finished) {
                                complete(workoutElapsedSeconds)
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
                        onClick = {
                            if (running && !finished) {
                                running = false
                                onCancel()
                            }
                        },
                        enabled = running && !finished,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
fun CardioWorkoutSummaryFullScreen(
    session: CardioSession,
    logDate: LocalDate,
    repository: CardioRepository,
    elapsedSeconds: Int?,
    distanceUnit: CardioDistanceUnit,
    dark: Color,
    mid: Color,
    glow: Color,
    relayPool: RelayPool?,
    signer: EventSigner?,
    userPreferences: UserPreferences,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sharing by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    val summaryContext = LocalContext.current
    val nip96Origin by userPreferences.nip96MediaServerOrigin.collectAsState(initial = "")
    val blossomPublicOrigin by userPreferences.blossomPublicServerOrigin.collectAsState(initial = "")
    val workoutMediaBackend by userPreferences.workoutMediaUploadBackend.collectAsState(
        initial = WorkoutMediaUploadBackend.NIP96
    )
    val attachRouteImage by userPreferences.attachRouteImageToWorkoutNostrShare.collectAsState(initial = true)
    val normalizedShareMediaOrigin = remember(nip96Origin, blossomPublicOrigin, workoutMediaBackend) {
        when (workoutMediaBackend) {
            WorkoutMediaUploadBackend.NIP96 -> Nip96Uploader.normalizeMediaServerOrigin(nip96Origin)
            WorkoutMediaUploadBackend.BLOSSOM -> Nip96Uploader.normalizeMediaServerOrigin(blossomPublicOrigin)
        }
    }
    val hasGpsForShare = session.gpsTrack?.points?.isNotEmpty() == true

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
            if (session.activity.builtin == CardioBuiltinActivity.RUCK) {
                session.ruckLoadKgResolved()?.let { kg ->
                    Text(
                        "Pack: ${formatCardioPackWeightFromKg(kg)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
            elapsedSeconds?.let { sec ->
                val m = sec / 60
                val s = sec % 60
                Text(
                    "Elapsed: %d:%02d".format(m, s),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            if (elapsedSeconds == null) {
                Text(
                    "Elapsed: ~${session.durationMinutes} min (saved length)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            Text(
                "Saved as ${session.durationMinutes} min",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            session.distanceMeters?.takeIf { it > 1 }?.let { d ->
                Text(
                    "Distance: ${formatCardioDistanceFromMeters(d, distanceUnit)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            formatCardioAveragePaceForSession(session, distanceUnit, elapsedSeconds)?.let { pace ->
                Text(
                    "Avg pace: $pace",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            Text(
                "Calories (estimate)",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.78f)
            )
            val estKcal = session.estimatedKcal
            when {
                estKcal != null && estKcal > 0.5 -> {
                    Text(
                        "~${estKcal.toInt()} kcal",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                    Text(
                        "MET × duration × body weight from Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                else -> {
                    Text(
                        "Add body weight in Settings to see an estimate.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
            session.gpsTrack?.points?.takeIf { it.isNotEmpty() }?.let { pts ->
                Text(
                    stringResource(R.string.cardio_summary_gps_track_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
                CardioGpsTrackSummaryPreview(points = pts)
                Text(
                    text = if (pts.size >= 2) {
                        stringResource(R.string.cardio_summary_gps_track_subtitle_many, pts.size)
                    } else {
                        stringResource(R.string.cardio_summary_gps_track_subtitle_one)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.68f)
                )
                if (session.activity.supportsPhoneGpsTracking()) {
                    val elev = remember(session, pts) { session.resolvedElevationMeters() }
                    val altSamples = remember(pts) { pts.count { it.altitudeMeters != null } }
                    when {
                        elev != null -> {
                            val (gain, loss) = elev
                            Text(
                                formatCardioElevationGainLoss(gain, loss, distanceUnit),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        altSamples < 2 -> {
                            Text(
                                stringResource(R.string.cardio_summary_elevation_unavailable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.62f)
                            )
                        }
                    }
                }
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
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.35f)
            )
            Text(
                session.summaryLine(distanceUnit),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(16.dp))
            if (relayPool != null && signer != null) {
                if (attachRouteImage && hasGpsForShare && normalizedShareMediaOrigin.isEmpty()) {
                    Text(
                        stringResource(R.string.cardio_share_route_image_need_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedButton(
                    onClick = {
                        if (sharing || shared) return@OutlinedButton
                        sharing = true
                        scope.launch {
                            val outcome = publishWorkoutNote(
                                summaryContext,
                                relayPool,
                                signer,
                                session,
                                distanceUnit,
                                nip96Origin,
                                blossomPublicOrigin,
                                workoutMediaBackend,
                                attachRouteImage,
                                dark,
                                mid,
                                glow
                            )
                            sharing = false
                            shared = outcome.relayOk
                            if (outcome.uploadedRouteImageUrl != null) {
                                val url = outcome.uploadedRouteImageUrl
                                repository.updateSession(logDate, session.id) { it.copy(routeImageUrl = url) }
                                if (relayPool != null && signer != null) {
                                    repository.currentState().logFor(logDate)?.let { log ->
                                        CardioSync.publishDailyLog(relayPool, signer, log)
                                    }
                                }
                            }
                            snackbarHostState.showSnackbar(outcome.message)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sharing && !shared,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color.White)
                    )
                ) {
                    Icon(
                        if (shared) Icons.Default.Share else Icons.Default.Share,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            sharing -> "Sharing…"
                            shared -> "Shared"
                            else -> "Share Workout"
                        }
                    )
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = mid)
            ) {
                Text("Done")
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun buildWorkoutNoteContent(
    session: CardioSession,
    distanceUnit: CardioDistanceUnit,
    routeImageUrl: String? = null
): String = buildString {
    append("\uD83C\uDFC3 Completed: ${session.activity.displayLabel}")
    if (session.modality == CardioModality.INDOOR_TREADMILL) append(" (treadmill)")
    append("\n")
    append("\u23F1 Duration: ${session.durationMinutes} min\n")
    if (session.activity.builtin == CardioBuiltinActivity.RUCK) {
        session.ruckLoadKgResolved()?.let { kg ->
            append("\uD83C\uDF92 Pack: ${formatCardioPackWeightFromKg(kg)}\n")
        }
    }
    session.distanceMeters?.takeIf { it > 1 }?.let { d ->
        append("\uD83D\uDCCF Distance: ${formatCardioDistanceFromMeters(d, distanceUnit)}\n")
    }
    session.resolvedElevationMeters()?.let { (gain, loss) ->
        append("\u26F0\uFE0F ")
        append(formatCardioElevationGainLoss(gain, loss, distanceUnit))
        append("\n")
    }
    session.estimatedKcal?.let { k ->
        append("\uD83D\uDD25 Est. calories: ~${k.toInt()} kcal\n")
    }
    session.heartRate?.avgBpm?.let { avg ->
        append("\u2764\uFE0F Avg HR: $avg bpm\n")
    }
    if (session.segments.isNotEmpty()) {
        val labels = session.segments.sortedBy { it.orderIndex }.joinToString(" → ") { it.activity.displayLabel }
        append("\uD83D\uDD04 Segments: $labels\n")
    }
    append("\n#erv #workout #fitness")
    if (routeImageUrl != null) {
        append("\n\n")
        append(routeImageUrl)
    }
}

private data class WorkoutPublishOutcome(
    val relayOk: Boolean,
    val message: String,
    /** Set when route PNG upload succeeded (Blossom or NIP-96); saved on the session. */
    val uploadedRouteImageUrl: String?
)

private suspend fun publishWorkoutNote(
    context: Context,
    relayPool: RelayPool,
    signer: EventSigner,
    session: CardioSession,
    distanceUnit: CardioDistanceUnit,
    nip96OriginRaw: String,
    blossomPublicOriginRaw: String,
    mediaBackend: WorkoutMediaUploadBackend,
    attachRouteImage: Boolean,
    dark: Color,
    mid: Color,
    glow: Color
): WorkoutPublishOutcome {
    val normalizedOrigin = when (mediaBackend) {
        WorkoutMediaUploadBackend.NIP96 ->
            Nip96Uploader.normalizeMediaServerOrigin(nip96OriginRaw)
        WorkoutMediaUploadBackend.BLOSSOM ->
            Nip96Uploader.normalizeMediaServerOrigin(blossomPublicOriginRaw)
    }
    val hasGps = session.gpsTrack?.points?.isNotEmpty() == true
    var routeImageUrl: String? = null
    var uploadAttempted = false
    var uploadOk = false
    if (attachRouteImage && normalizedOrigin.isNotEmpty() && hasGps) {
        uploadAttempted = true
        val bytes = CardioTrackShareImage.renderRoutePngBytes(
            context.applicationContext,
            session.gpsTrack!!.points,
            dark.toArgb(),
            mid.toArgb(),
            glow.toArgb()
        )
        if (bytes != null) {
            routeImageUrl = when (mediaBackend) {
                WorkoutMediaUploadBackend.NIP96 -> {
                    val name = "erv_route_${session.id.take(8)}.png"
                    Nip96Uploader.uploadRoutePngFromOrigin(normalizedOrigin, bytes, name, signer).getOrNull()
                }
                WorkoutMediaUploadBackend.BLOSSOM ->
                    BlossomUploader.uploadBlob(normalizedOrigin, bytes, "image/png", signer).getOrNull()
            }
            uploadOk = routeImageUrl != null
        }
    }
    val tags = mutableListOf(
        listOf("t", "erv"),
        listOf("t", "workout"),
        listOf("t", "fitness")
    )
    if (routeImageUrl != null) {
        tags.add(listOf("imeta", "url $routeImageUrl", "m image/png", "dim 1080x1440"))
    }
    val content = buildWorkoutNoteContent(session, distanceUnit, routeImageUrl)
    val unsigned = UnsignedEvent(
        pubkey = signer.publicKey,
        createdAt = System.currentTimeMillis() / 1000,
        kind = 1,
        tags = tags,
        content = content
    )
    val signed = signer.sign(unsigned)
    val ok = relayPool.publish(signed)
    val message = when {
        !ok -> "Failed to share — check relay connection"
        uploadAttempted && !uploadOk ->
            "Shared! Route image was not included (upload failed)."
        else -> "Shared to your relays!"
    }
    val uploadedRouteImageUrl = routeImageUrl?.takeIf { uploadOk }
    return WorkoutPublishOutcome(ok, message, uploadedRouteImageUrl)
}

@Composable
fun CardioMultiLegTimerFullScreen(
    state: CardioMultiLegTimerState,
    stateKey: Any,
    dark: Color,
    mid: Color,
    glow: Color,
    onLeaveWorkoutUi: (() -> Unit)? = null,
    onFinishLeg: (elapsedSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    key(stateKey) {
        var showMediaSheet by remember { mutableStateOf(false) }
        var running by remember { mutableStateOf(true) }
        var tick by remember(stateKey) { mutableIntStateOf(0) }
        LaunchedEffect(stateKey, running) {
            while (running) {
                delay(1000)
                tick++
            }
        }
        val elapsed = remember(tick, state.legStartedEpoch) {
            (nowEpochSeconds() - state.legStartedEpoch).coerceAtLeast(0).toInt()
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
                    if (onLeaveWorkoutUi != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onLeaveWorkoutUi) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Leave timer",
                                    tint = Color.White
                                )
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Multi-activity workout",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(onClick = { showMediaSheet = !showMediaSheet }) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = stringResource(R.string.media_control_cd_music),
                                    tint = Color.White.copy(alpha = if (showMediaSheet) 1f else 0.88f)
                                )
                            }
                        }
                    } else {
                        Text(
                            "Multi-activity workout",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showMediaSheet) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            WorkoutMediaControlPanel(
                                useLightOnDarkBackground = true,
                                showHeaderTitle = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }
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

private const val CardioMetCompendiumUrl = "https://pacompendium.com/"

private fun openCardioMetCompendiumInBrowser(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CardioMetCompendiumUrl)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open this link", Toast.LENGTH_SHORT).show()
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
    val context = LocalContext.current
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
                TextButton(
                    onClick = { openCardioMetCompendiumInBrowser(context) },
                    modifier = Modifier
                        .padding(top = 0.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Look up MET values (HHS Compendium)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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

private fun routineStepDraftsFromRoutine(
    routine: CardioRoutine?,
    distanceUnit: CardioDistanceUnit
): List<RoutineStepDraft> {
    if (routine == null) return listOf(RoutineStepDraft())
    return routine.effectiveSteps().map { stepToRoutineDraft(it, distanceUnit) }
}

private fun stepToRoutineDraft(step: CardioRoutineStep, distanceUnit: CardioDistanceUnit): RoutineStepDraft {
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
        treadDistKmStr = t?.distanceMeters?.let { d -> metersToCardioDistanceInputString(d, distanceUnit) } ?: "",
        loadStr = t?.loadKg?.let { kg -> "%.0f".format(kg / 0.453592) } ?: "",
        targetMinutesStr = (step.targetDurationMinutes ?: 30).toString()
    )
}

private fun buildRoutineStepFromDraft(
    d: RoutineStepDraft,
    idx: Int,
    state: CardioLibraryState,
    distanceUnit: CardioDistanceUnit
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
        val distM = d.treadDistKmStr.toDoubleOrNull()
            ?.let { parseCardioDistanceInputToMeters(it, distanceUnit) }
        val lb = if (builtinForM == CardioBuiltinActivity.RUCK) d.loadStr.toDoubleOrNull() else null
        CardioTreadmillParams(
            speed = speed,
            speedUnit = d.speedUnit,
            inclinePercent = inc,
            distanceMeters = distM,
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
    distanceUnit: CardioDistanceUnit,
    onDismiss: () -> Unit,
    onSave: (CardioRoutine) -> Unit
) {
    var name by remember(routine?.id) { mutableStateOf(routine?.name ?: "") }
    var steps by remember(routine?.id, distanceUnit) {
        mutableStateOf(routineStepDraftsFromRoutine(routine, distanceUnit))
    }
    var notes by remember(routine?.id) { mutableStateOf(routine?.notes ?: "") }
    var selectedDaySet by remember(routine?.id) {
        mutableStateOf(routine?.repeatDays?.toSet() ?: emptySet())
    }

    fun updateStep(index: Int, block: (RoutineStepDraft) -> RoutineStepDraft) {
        steps = steps.mapIndexed { i, s -> if (i == index) block(s) else s }
    }

    val allStepsValid = steps.indices.all { i ->
        buildRoutineStepFromDraft(steps[i], i, state, distanceUnit) != null
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
                                    label = { Text(distanceUnit.distanceFieldLabelOptional()) },
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
                    val built = steps.mapIndexedNotNull { i, d ->
                        buildRoutineStepFromDraft(d, i, state, distanceUnit)
                    }
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
