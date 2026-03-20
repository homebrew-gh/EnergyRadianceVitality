package com.erv.app.ui.dashboard

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erv.app.R
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.navigation.Category
import com.erv.app.ui.navigation.CategorySheet
import com.erv.app.ui.navigation.categories
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementActivityRow
import com.erv.app.supplements.SupplementRepository
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.SupplementRoutineStep
import com.erv.app.lighttherapy.LightActivityRow
import com.erv.app.lighttherapy.LightLibraryState
import com.erv.app.lighttherapy.LightRoutine
import com.erv.app.lighttherapy.LightSync
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioActivityRow
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioTimerCompletionResult
import com.erv.app.cardio.CardioMetEstimator
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioSessionSource
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.cardioActivityRowsFor
import com.erv.app.cardio.effectiveSteps
import com.erv.app.cardio.stepsSummaryLabel
import com.erv.app.cardio.summaryLine
import com.erv.app.data.BodyWeightUnit
import com.erv.app.goals.GoalProgressRow
import com.erv.app.goals.anySelectedGoalMet
import com.erv.app.goals.computeWeeklyGoalProgress
import com.erv.app.goals.summaryLine
import com.erv.app.data.UserPreferences
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.ui.cardio.CardioElapsedTimerFullScreen
import com.erv.app.ui.cardio.CardioMultiLegTimerFullScreen
import com.erv.app.ui.cardio.CardioWorkoutSummaryFullScreen
import com.erv.app.ui.lighttherapy.LightTherapyTimerFullScreen
import com.erv.app.lighttherapy.LightTimeOfDay
import com.erv.app.lighttherapy.lightActivityFor
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.supplements.SupplementSync
import com.erv.app.supplements.SupplementTimeOfDay
import com.erv.app.supplements.activityStyleSummary
import com.erv.app.supplements.groupedSupplementActivityFor
import com.erv.app.reminders.RoutineReminderRepository
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.erv.app.cardio.label
import com.erv.app.cardio.nowEpochSeconds
import com.erv.app.weighttraining.WeightActivityRow
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.weightActivityRowsFor
import java.time.LocalDate
import kotlin.math.max

private data class LightTimerSession(
    val minutes: Int,
    val routineId: String?,
    val routineName: String?,
    val deviceId: String?,
    val deviceName: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToEditGoals: () -> Unit,
    onNavigateToCategory: (Category) -> Unit,
    onOpenCardioNewWorkout: () -> Unit,
    /** Cardio manual log: opens cardio log with calendar (dashboard date pre-selected). */
    onOpenCardioLogBackfill: (LocalDate) -> Unit,
    /** Weight training manual log: opens log screen with calendar (dashboard date pre-selected). */
    onOpenWeightLogBackfill: (LocalDate) -> Unit,
    supplementRepository: SupplementRepository,
    lightTherapyRepository: LightTherapyRepository,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    pendingReminderRoutineId: String?,
    onConsumePendingReminderRoutineId: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val supplementState by supplementRepository.state.collectAsState(initial = SupplementLibraryState())
    val lightState by lightTherapyRepository.state.collectAsState(initial = LightLibraryState())
    val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
    val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val weightTrainingLoadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.KG)
    val selectedGoalIds by userPreferences.selectedGoalIds.collectAsState(initial = emptySet())
    val goalsAsOfDate = LocalDate.now()
    val weeklyGoalRows = remember(
        goalsAsOfDate,
        selectedGoalIds,
        supplementState,
        lightState,
        cardioState,
        weightState,
    ) {
        computeWeeklyGoalProgress(
            asOfDate = goalsAsOfDate,
            selectedGoalIds = selectedGoalIds,
            supplementState = supplementState,
            lightState = lightState,
            cardioState = cardioState,
            weightState = weightState,
        )
    }
    val anyGoalMetThisWeek = remember(weeklyGoalRows) { anySelectedGoalMet(weeklyGoalRows) }
    val reminderRepository = remember(context) { RoutineReminderRepository(context) }
    val supplementRows = remember(supplementState, selectedDate) {
        supplementState.groupedSupplementActivityFor(selectedDate)
    }
    val lightRows = remember(lightState, selectedDate) {
        lightState.lightActivityFor(selectedDate)
    }
    val cardioRows = remember(cardioState, selectedDate, cardioDistanceUnit) {
        cardioState.cardioActivityRowsFor(selectedDate, cardioDistanceUnit)
    }
    val weightRows = remember(weightState, selectedDate, weightTrainingLoadUnit) {
        weightState.weightActivityRowsFor(selectedDate, weightTrainingLoadUnit)
    }
    val weightTrainingCategory = remember { categories.first { it.id == "weight_training" } }
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    var showCalendar by remember { mutableStateOf(false) }
    var routinePreview by remember { mutableStateOf<SupplementRoutine?>(null) }
    var routineEditor by remember { mutableStateOf<SupplementRoutine?>(null) }
    var lightRoutinePreview by remember { mutableStateOf<LightRoutine?>(null) }
    var lightTimerSession by remember { mutableStateOf<LightTimerSession?>(null) }
    var cardioRoutinePreview by remember { mutableStateOf<CardioRoutine?>(null) }
    var cardioActiveTimer by remember { mutableStateOf<CardioActiveTimerSession?>(null) }
    var cardioWorkoutSummary by remember { mutableStateOf<CardioTimerCompletionResult?>(null) }
    var showGoalsSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val darkTheme = isSystemInDarkTheme()
    val therapyRedDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    LaunchedEffect(Unit) {
        scaffoldState.bottomSheetState.partialExpand()
    }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            SupplementSync.publishMaster(relayPool, signer, supplementRepository.currentState())
        }
    }

    suspend fun syncDailyLog() {
        if (relayPool != null && signer != null) {
            supplementRepository.currentState().logFor(today)?.let { log ->
                SupplementSync.publishDailyLog(relayPool, signer, log)
            }
            lightTherapyRepository.currentState().logFor(today)?.let { log ->
                LightSync.publishDailyLog(relayPool, signer, log)
            }
            cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                CardioSync.publishDailyLog(relayPool, signer, log)
            }
        }
    }

    LaunchedEffect(pendingReminderRoutineId, supplementState.routines) {
        val routineId = pendingReminderRoutineId ?: return@LaunchedEffect
        for (attempt in 0 until 20) {
            val routine = supplementState.routines.firstOrNull { it.id == routineId }
            if (routine != null) {
                routinePreview = routine
                onConsumePendingReminderRoutineId()
                return@LaunchedEffect
            }
            if (supplementState.routines.isNotEmpty()) break
            delay(100)
        }
        onConsumePendingReminderRoutineId()
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = 56.dp,
        sheetContent = {
            CategorySheet(
                onCategoryClick = { category ->
                    scope.launch {
                        scaffoldState.bottomSheetState.partialExpand()
                        onNavigateToCategory(category)
                    }
                }
            )
        },
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dashboard_categories_menu),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sun),
                            contentDescription = null,
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "ERV",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.size(48.dp)) {
                        IconButton(
                            onClick = { showGoalsSheet = true },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = "Goals",
                                tint = Color.White,
                            )
                        }
                        if (anyGoalMetThisWeek) {
                            Text(
                                text = "★",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFFFEA00),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 4.dp, top = 2.dp),
                            )
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFC62828)
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The dashboard keeps routines lightweight: select, preview, then log or edit.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
            Spacer(Modifier.height(8.dp))

            DateNavigator(
                selectedDate = selectedDate,
                onPreviousDay = viewModel::previousDay,
                onNextDay = viewModel::nextDay,
                onPreviousWeek = viewModel::previousWeek,
                onNextWeek = viewModel::nextWeek,
                onTodayClick = viewModel::goToToday,
                onCalendarClick = { showCalendar = true }
            )

            Spacer(Modifier.height(20.dp))

            if (selectedGoalIds.isNotEmpty()) {
                DashboardGoalsSection(
                    rows = weeklyGoalRows,
                    onOpenDetails = { showGoalsSheet = true },
                )
                Spacer(Modifier.height(16.dp))
            }

            RoutinesSection(
                dashboardSelectedDate = selectedDate,
                supplementRoutines = supplementState.routines,
                lightRoutines = lightState.routines,
                cardioRoutines = cardioState.routines,
                weightRoutines = weightState.routines,
                onSupplementRoutineSelected = { routinePreview = it },
                onLightRoutineSelected = { lightRoutinePreview = it },
                onCardioRoutineSelected = { cardioRoutinePreview = it },
                onOpenCardioNewWorkout = onOpenCardioNewWorkout,
                onOpenCardioLogBackfill = onOpenCardioLogBackfill,
                onOpenWeightNewWorkout = {
                    if (!weightLiveWorkoutViewModel.tryStartBlank()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                        }
                    } else {
                        onNavigateToCategory(weightTrainingCategory)
                    }
                },
                onWeightRoutineSelected = { routine ->
                    if (!weightLiveWorkoutViewModel.tryStartFromRoutine(routine, weightState)) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                        }
                    } else {
                        onNavigateToCategory(weightTrainingCategory)
                    }
                },
                onOpenWeightLogBackfill = onOpenWeightLogBackfill
            )

            Spacer(Modifier.height(16.dp))

            ActivitySection(
                selectedDate = selectedDate,
                supplementRows = supplementRows,
                lightRows = lightRows,
                cardioRows = cardioRows,
                weightRows = weightRows
            )

            Spacer(Modifier.height(16.dp))
            }

            if (showCalendar) {
                CalendarPopup(
                    selectedDate = selectedDate,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                        showCalendar = false
                    },
                    onDismiss = { showCalendar = false }
                )
            }

            if (showGoalsSheet) {
                GoalsOverviewSheet(
                    selectedGoalIds = selectedGoalIds,
                    progressRows = weeklyGoalRows,
                    onDismiss = { showGoalsSheet = false },
                    onEditGoals = {
                        showGoalsSheet = false
                        onNavigateToEditGoals()
                    },
                )
            }

            routinePreview?.let { routine ->
                RoutinePreviewSheet(
                    routine = routine,
                    supplements = supplementState.supplements,
                    onDismiss = { routinePreview = null },
                    onLogAsIs = {
                    scope.launch {
                        supplementRepository.logRoutineRun(today, routine.id, routine.name, routine.steps)
                        syncDailyLog()
                        snackbarHostState.showSnackbar("Logged ${routine.name}")
                        routinePreview = null
                    }
                },
                    onModify = {
                        routineEditor = routine
                        routinePreview = null
                    }
                )
            }

            routineEditor?.let { routine ->
                RoutineModifyDialog(
                    routine = routine,
                    supplements = supplementState.supplements,
                    onDismiss = { routineEditor = null },
                    onSave = { updatedName, steps, notes, savePermanently ->
                    scope.launch {
                        val updatedRoutine = routine.copy(
                            name = updatedName,
                            steps = steps,
                            notes = notes
                        )
                        if (savePermanently) {
                            supplementRepository.renameRoutine(
                                routineId = routine.id,
                                name = updatedRoutine.name,
                                timeOfDay = updatedRoutine.timeOfDay,
                                steps = updatedRoutine.steps,
                                notes = updatedRoutine.notes
                            )
                            reminderRepository.updateRoutineName(routine.id, updatedRoutine.name)
                            syncMaster()
                        }
                        supplementRepository.logRoutineRun(
                            date = today,
                            routineId = updatedRoutine.id,
                            routineName = updatedRoutine.name,
                            steps = updatedRoutine.steps
                        )
                        syncDailyLog()
                        snackbarHostState.showSnackbar(
                            if (savePermanently) {
                                "Updated and logged ${updatedRoutine.name}"
                            } else {
                                "Logged edited ${updatedRoutine.name}"
                            }
                        )
                        routineEditor = null
                    }
                }
                )
            }

            lightRoutinePreview?.let { routine ->
                LightRoutinePreviewSheet(
                    routine = routine,
                    deviceName = routine.deviceId?.let { lightState.deviceById(it)?.name },
                    onDismiss = { lightRoutinePreview = null },
                    onLogSession = {
                    scope.launch {
                        val device = routine.deviceId?.let { lightState.deviceById(it) }
                        lightTherapyRepository.logSession(
                            date = today,
                            minutes = routine.durationMinutes,
                            deviceId = routine.deviceId,
                            deviceName = device?.name,
                            routineId = routine.id,
                            routineName = routine.name
                        )
                        lightTherapyRepository.currentState().logFor(today)?.let { log ->
                            if (relayPool != null && signer != null) {
                                LightSync.publishDailyLog(relayPool, signer, log)
                            }
                        }
                        snackbarHostState.showSnackbar("Logged ${routine.name} • ${routine.durationMinutes} min")
                        lightRoutinePreview = null
                    }
                },
                    onStartTimer = { r ->
                        val device = r.deviceId?.let { lightState.deviceById(it) }
                        lightTimerSession = LightTimerSession(
                            minutes = r.durationMinutes,
                            routineId = r.id,
                            routineName = r.name,
                            deviceId = r.deviceId,
                            deviceName = device?.name
                        )
                        lightRoutinePreview = null
                    }
                )
            }

            lightTimerSession?.let { session ->
                LightTherapyTimerFullScreen(
                    durationMinutes = session.minutes,
                    deviceName = session.routineName ?: session.deviceName ?: "Light therapy",
                    redDark = therapyRedDark,
                    redMid = therapyRedMid,
                    redGlow = therapyRedGlow,
                    onComplete = {
                    scope.launch {
                        lightTherapyRepository.logSession(
                            date = today,
                            minutes = session.minutes,
                            deviceId = session.deviceId,
                            deviceName = session.deviceName,
                            routineId = session.routineId,
                            routineName = session.routineName
                        )
                        lightTherapyRepository.currentState().logFor(today)?.let { log ->
                            if (relayPool != null && signer != null) {
                                LightSync.publishDailyLog(relayPool, signer, log)
                            }
                        }
                        snackbarHostState.showSnackbar("Logged ${session.routineName ?: "Light therapy"} • ${session.minutes} min")
                        lightTimerSession = null
                    }
                },
                    onCancel = { lightTimerSession = null }
                )
            }

            cardioRoutinePreview?.let { routine ->
                CardioRoutinePreviewSheet(
                    routine = routine,
                    onDismiss = { cardioRoutinePreview = null },
                    onLogSession = {
                        scope.launch {
                            val mins = routine.targetDurationMinutes ?: 30
                            val session = CardioMetEstimator.buildSessionFromRoutine(
                                routine = routine,
                                durationMinutes = mins,
                                source = CardioSessionSource.MANUAL,
                                weightKg = weightKg,
                                library = cardioRepository.currentState()
                            )
                            cardioRepository.addSession(selectedDate, session)
                            cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                if (relayPool != null && signer != null) {
                                    CardioSync.publishDailyLog(relayPool, signer, log)
                                }
                            }
                            snackbarHostState.showSnackbar("Logged ${routine.name}")
                            cardioRoutinePreview = null
                        }
                    },
                    onStartTimer = { r ->
                        CardioTimerSessionDraft.fromRoutine(r)?.let { d ->
                            cardioActiveTimer = CardioActiveTimerSession.Single(d)
                        } ?: CardioMultiLegTimerState.fromRoutine(r)?.let { m ->
                            cardioActiveTimer = CardioActiveTimerSession.Multi(m)
                        }
                        cardioRoutinePreview = null
                    }
                )
            }

            val cSummary = cardioWorkoutSummary
            if (cSummary != null) {
                CardioWorkoutSummaryFullScreen(
                    session = cSummary.session,
                    elapsedSeconds = cSummary.elapsedSeconds,
                    distanceUnit = cardioDistanceUnit,
                    dark = therapyRedDark,
                    mid = therapyRedMid,
                    glow = therapyRedGlow,
                    relayPool = relayPool,
                    signer = signer,
                    onDone = { cardioWorkoutSummary = null }
                )
            } else when (val ct = cardioActiveTimer) {
                is CardioActiveTimerSession.Single -> {
                    val draft = ct.draft
                    CardioElapsedTimerFullScreen(
                        draft = draft,
                        distanceUnit = cardioDistanceUnit,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        onStop = { elapsedSeconds ->
                            scope.launch {
                                val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                                val end = nowEpochSeconds()
                                val raw = draft.toSession(
                                    durationMinutes = durationMinutes,
                                    endEpoch = end,
                                    elapsedSecondsForDistance = elapsedSeconds
                                )
                                val session = CardioMetEstimator.applyEstimatedKcal(
                                    raw,
                                    cardioRepository.currentState(),
                                    weightKg
                                )
                                cardioActiveTimer = null
                                cardioWorkoutSummary = CardioTimerCompletionResult(session, elapsedSeconds)
                                cardioRepository.addSession(selectedDate, session)
                                cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                    if (relayPool != null && signer != null) {
                                        CardioSync.publishDailyLog(relayPool, signer, log)
                                    }
                                }
                            }
                        },
                        onCancel = { cardioActiveTimer = null }
                    )
                }
                is CardioActiveTimerSession.Multi -> {
                    val multiKey = ct.state.currentLegIndex to ct.state.completedSegments.size
                    CardioMultiLegTimerFullScreen(
                        state = ct.state,
                        stateKey = multiKey,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        onFinishLeg = { elapsedSeconds ->
                            scope.launch {
                                val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                                val end = nowEpochSeconds()
                                val (next, session) = CardioMetEstimator.advanceMultiLegTimer(
                                    ct.state,
                                    durationMinutes,
                                    end,
                                    cardioRepository.currentState(),
                                    weightKg
                                )
                                if (session != null) {
                                    cardioActiveTimer = null
                                    cardioWorkoutSummary = CardioTimerCompletionResult(session, null)
                                    cardioRepository.addSession(selectedDate, session)
                                    cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                        if (relayPool != null && signer != null) {
                                            CardioSync.publishDailyLog(relayPool, signer, log)
                                        }
                                    }
                                } else if (next != null) {
                                    cardioActiveTimer = CardioActiveTimerSession.Multi(next)
                                    snackbarHostState.showSnackbar("Leg saved — start next when ready")
                                }
                            }
                        },
                        onCancel = { cardioActiveTimer = null }
                    )
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun DashboardGoalsSection(
    rows: List<GoalProgressRow>,
    onOpenDetails: () -> Unit,
) {
    Text(
        text = "Goals",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "This week",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Mon–Sun · default targets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenDetails) {
                    Text("Details")
                }
            }
            rows.forEach { row ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.summaryLine(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.met) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = row.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { row.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (row.met) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsOverviewSheet(
    selectedGoalIds: Set<String>,
    progressRows: List<GoalProgressRow>,
    onDismiss: () -> Unit,
    onEditGoals: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Goals",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (selectedGoalIds.isEmpty()) {
                Text(
                    text = "You have not chosen any goals yet. Use Edit goals to pick areas to focus on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "This week (Mon–Sun). A ★ on the trophy appears when at least one goal reaches its default target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                progressRows.forEach { row ->
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        text = row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.summaryLine(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (row.met) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (row.met) {
                            Text(
                                text = "★",
                                color = Color(0xFFFFA000),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { row.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (row.met) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onEditGoals,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Edit goals")
            }
        }
    }
}

@Composable
private fun RoutinesSection(
    dashboardSelectedDate: LocalDate,
    supplementRoutines: List<SupplementRoutine>,
    lightRoutines: List<LightRoutine>,
    cardioRoutines: List<CardioRoutine>,
    weightRoutines: List<WeightRoutine>,
    onSupplementRoutineSelected: (SupplementRoutine) -> Unit,
    onLightRoutineSelected: (LightRoutine) -> Unit,
    onCardioRoutineSelected: (CardioRoutine) -> Unit,
    onOpenCardioNewWorkout: () -> Unit,
    onOpenCardioLogBackfill: (LocalDate) -> Unit,
    onOpenWeightNewWorkout: () -> Unit,
    onOpenWeightLogBackfill: (LocalDate) -> Unit,
    onWeightRoutineSelected: (WeightRoutine) -> Unit
) {
    var showSupplementPicker by remember { mutableStateOf(false) }
    var showLightPicker by remember { mutableStateOf(false) }
    var showCardioPicker by remember { mutableStateOf(false) }
    var showWeightPicker by remember { mutableStateOf(false) }

    Text(
        text = "Routines",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (supplementRoutines.isEmpty() && lightRoutines.isEmpty()) {
                Text(
                    text = "Create supplement or light routines, or use Cardio / Weight Training to log a workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (supplementRoutines.isNotEmpty() || lightRoutines.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (supplementRoutines.isNotEmpty()) {
                        RoutineTile(
                            icon = Icons.Default.Medication,
                            label = "Supplements",
                            subtitle = if (supplementRoutines.size == 1) "1 routine" else "${supplementRoutines.size} routines",
                            onClick = {
                                if (supplementRoutines.size == 1) {
                                    onSupplementRoutineSelected(supplementRoutines.first())
                                } else {
                                    showSupplementPicker = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (lightRoutines.isNotEmpty()) {
                        RoutineTile(
                            icon = Icons.Default.WbSunny,
                            label = "Light Therapy",
                            subtitle = if (lightRoutines.size == 1) "1 routine" else "${lightRoutines.size} routines",
                            onClick = {
                                if (lightRoutines.size == 1) {
                                    onLightRoutineSelected(lightRoutines.first())
                                } else {
                                    showLightPicker = true
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoutineTile(
                    icon = Icons.Default.DirectionsRun,
                    label = "Cardio",
                    subtitle = if (cardioRoutines.isEmpty()) "New workout" else "${cardioRoutines.size} routines",
                    onClick = { showCardioPicker = true },
                    modifier = Modifier.weight(1f)
                )
                RoutineTile(
                    icon = Icons.Default.FitnessCenter,
                    label = "Weight Training",
                    subtitle = if (weightRoutines.isEmpty()) "New workout" else "${weightRoutines.size} routines",
                    onClick = { showWeightPicker = true },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showSupplementPicker) {
        SupplementRoutinePickerSheet(
            routines = supplementRoutines,
            onDismiss = { showSupplementPicker = false },
            onSelect = { routine ->
                onSupplementRoutineSelected(routine)
                showSupplementPicker = false
            }
        )
    }
    if (showLightPicker) {
        LightRoutinePickerSheet(
            routines = lightRoutines,
            onDismiss = { showLightPicker = false },
            onSelect = { routine ->
                onLightRoutineSelected(routine)
                showLightPicker = false
            }
        )
    }
    if (showCardioPicker) {
        CardioRoutinePickerSheet(
            routines = cardioRoutines,
            onDismiss = { showCardioPicker = false },
            onNewWorkout = {
                onOpenCardioNewWorkout()
                showCardioPicker = false
            },
            onLogPreviousWorkout = {
                onOpenCardioLogBackfill(dashboardSelectedDate)
                showCardioPicker = false
            },
            onSelectRoutine = { routine ->
                onCardioRoutineSelected(routine)
                showCardioPicker = false
            }
        )
    }
    if (showWeightPicker) {
        WeightRoutinePickerSheet(
            routines = weightRoutines,
            onDismiss = { showWeightPicker = false },
            onNewWorkout = {
                onOpenWeightNewWorkout()
                showWeightPicker = false
            },
            onLogPreviousWorkout = {
                onOpenWeightLogBackfill(dashboardSelectedDate)
                showWeightPicker = false
            },
            onSelectRoutine = { routine ->
                onWeightRoutineSelected(routine)
                showWeightPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightRoutinePickerSheet(
    routines: List<WeightRoutine>,
    onDismiss: () -> Unit,
    onNewWorkout: () -> Unit,
    onLogPreviousWorkout: () -> Unit,
    onSelectRoutine: (WeightRoutine) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Weight Training",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // Always first: quick path to live workout (same pattern as Cardio sheet).
            Surface(
                onClick = onNewWorkout,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("New workout", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Open the live workout screen to log sets, or pick a saved routine below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                onClick = onLogPreviousWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Log previous workout", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Open the weight log with the calendar — pick a day, then add or edit a manual entry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (routines.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    "Routines",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(routines, key = { it.id }) { routine ->
                        Surface(
                            onClick = { onSelectRoutine(routine) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    routine.name.ifBlank { "Routine" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                val n = routine.exerciseIds.size
                                Text(
                                    if (n == 1) "1 exercise" else "$n exercises",
                                    style = MaterialTheme.typography.bodySmall,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardioRoutinePickerSheet(
    routines: List<CardioRoutine>,
    onDismiss: () -> Unit,
    onNewWorkout: () -> Unit,
    onLogPreviousWorkout: () -> Unit,
    onSelectRoutine: (CardioRoutine) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Cardio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Surface(
                onClick = onNewWorkout,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("New workout", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Log a session, save a routine, or start a timer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                onClick = onLogPreviousWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Log previous workout", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Open the cardio log with the calendar — pick a day, then add a session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (routines.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    "Routines",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(routines, key = { it.id }) { routine ->
                        Surface(
                            onClick = { onSelectRoutine(routine) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(routine.name, style = MaterialTheme.typography.bodyLarge)
                                val legs = routine.effectiveSteps()
                                val sub = buildString {
                                    append(routine.stepsSummaryLabel())
                                    append(" • ")
                                    append(legs.firstOrNull()?.modality?.label() ?: routine.modality.label())
                                    val tgt = if (legs.size > 1) {
                                        legs.mapNotNull { it.targetDurationMinutes }.takeIf { it.size == legs.size }?.sum()
                                    } else null
                                    val minHint = tgt ?: routine.targetDurationMinutes
                                    minHint?.let { append(" • $it min") }
                                }
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodySmall,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardioRoutinePreviewSheet(
    routine: CardioRoutine,
    onDismiss: () -> Unit,
    onLogSession: () -> Unit,
    onStartTimer: (CardioRoutine) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (routine.notes.isNotBlank()) {
                Text(routine.notes, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLogSession) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log session")
                }
                OutlinedButton(onClick = { onStartTimer(routine) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start timer")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplementRoutinePickerSheet(
    routines: List<SupplementRoutine>,
    onDismiss: () -> Unit,
    onSelect: (SupplementRoutine) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Choose a supplement routine",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            routines.forEach { routine ->
                Surface(
                    onClick = { onSelect(routine) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightRoutinePickerSheet(
    routines: List<LightRoutine>,
    onDismiss: () -> Unit,
    onSelect: (LightRoutine) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Choose a light therapy routine",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            routines.forEach { routine ->
                Surface(
                    onClick = { onSelect(routine) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = routine.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${when (routine.timeOfDay) { LightTimeOfDay.MORNING -> "Morning"; LightTimeOfDay.AFTERNOON -> "Afternoon"; LightTimeOfDay.NIGHT -> "Night" }} • ${routine.durationMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySection(
    selectedDate: LocalDate,
    supplementRows: List<SupplementActivityRow>,
    lightRows: List<LightActivityRow>,
    cardioRows: List<CardioActivityRow>,
    weightRows: List<WeightActivityRow>
) {
    Text(
        text = "Activity",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val hasSupplements = supplementRows.isNotEmpty()
            val hasLight = lightRows.isNotEmpty()
            val hasCardio = cardioRows.isNotEmpty()
            val hasWeight = weightRows.isNotEmpty()

            if (!hasSupplements && !hasLight && !hasCardio && !hasWeight) {
                Text(
                    text = "No activity logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (hasSupplements) {
                    ActivityCategorySection(title = "Supplements") {
                        supplementRows.forEach { row ->
                            Text(
                                text = "${row.supplementName} • ${row.amountDisplay}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasLight) {
                    if (hasSupplements) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Light therapy") {
                        lightRows.forEach { row ->
                            Text(
                                text = "${row.displayName} • ${row.minutes} min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasCardio) {
                    if (hasSupplements || hasLight) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Cardio") {
                        cardioRows.forEach { row ->
                            Text(
                                text = row.summaryLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasWeight) {
                    if (hasSupplements || hasLight || hasCardio) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Weight training") {
                        weightRows.forEach { row ->
                            Text(
                                text = row.summaryLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCategorySection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightRoutinePreviewSheet(
    routine: LightRoutine,
    deviceName: String?,
    onDismiss: () -> Unit,
    onLogSession: () -> Unit,
    onStartTimer: (LightRoutine) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${when (routine.timeOfDay) { LightTimeOfDay.MORNING -> "Morning"; LightTimeOfDay.AFTERNOON -> "Afternoon"; LightTimeOfDay.NIGHT -> "Night" }} • ${routine.durationMinutes} min${deviceName?.let { " • $it" }.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (routine.notes.isNotBlank()) {
                Text(
                    routine.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLogSession) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log session")
                }
                OutlinedButton(onClick = { onStartTimer(routine) }) {
                    Icon(Icons.Default.WbSunny, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start timer")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinePreviewSheet(
    routine: SupplementRoutine,
    supplements: List<com.erv.app.supplements.SupplementEntry>,
    onDismiss: () -> Unit,
    onLogAsIs: () -> Unit,
    onModify: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            if (routine.notes.isNotBlank()) {
                Text(
                    routine.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                routine.steps.forEach { step ->
                    val supplement = supplements.firstOrNull { it.id == step.supplementId }
                    Text(
                        text = step.activityStyleSummary(supplement),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLogAsIs) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log as is")
                }
                OutlinedButton(onClick = onModify) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Modify")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class RoutineStepDraft(
    val supplementId: String? = null,
    val timeOfDay: SupplementTimeOfDay = SupplementTimeOfDay.MORNING,
    val quantity: String = "1",
    val dosageOverride: String = "",
    val note: String = ""
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineModifyDialog(
    routine: SupplementRoutine,
    supplements: List<com.erv.app.supplements.SupplementEntry>,
    onDismiss: () -> Unit,
    onSave: (String, List<SupplementRoutineStep>, String, Boolean) -> Unit
) {
    var name by remember(routine.id) { mutableStateOf(routine.name) }
    var notes by remember(routine.id) { mutableStateOf(routine.notes) }
    var savePermanently by remember(routine.id) { mutableStateOf(false) }
    val steps = remember(routine.id) {
        mutableStateListOf<RoutineStepDraft>().apply {
            if (routine.steps.isEmpty()) {
                add(RoutineStepDraft())
            } else {
                routine.steps.forEach {
                    add(
                        RoutineStepDraft(
                            supplementId = it.supplementId,
                            timeOfDay = it.timeOfDay ?: SupplementTimeOfDay.MORNING,
                            quantity = it.quantity?.toString() ?: "1",
                            dosageOverride = it.dosageOverride.orEmpty(),
                            note = it.note.orEmpty()
                        )
                    )
                }
            }
        }
    }
    val timeSlots = remember { listOf(SupplementTimeOfDay.MORNING, SupplementTimeOfDay.MIDDAY, SupplementTimeOfDay.NIGHT, SupplementTimeOfDay.OTHER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modify routine") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Schedule", style = MaterialTheme.typography.titleSmall)
                timeSlots.forEach { timeOfDay ->
                    val slotSteps = steps.withIndex().filter { it.value.timeOfDay == timeOfDay }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(timeOfDay.label(), style = MaterialTheme.typography.labelLarge)
                        if (slotSteps.isEmpty()) {
                            Text(
                                text = "No supplements added for this time yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            slotSteps.forEach { indexedStep ->
                                RoutineStepRow(
                                    step = indexedStep.value,
                                    supplements = supplements,
                                    onStepChange = { updated -> steps[indexedStep.index] = updated },
                                    onRemove = { if (steps.size > 1) steps.removeAt(indexedStep.index) }
                                )
                            }
                        }
                        TextButton(onClick = { steps.add(RoutineStepDraft(timeOfDay = timeOfDay)) }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add ${timeOfDay.label().lowercase()}")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = savePermanently, onCheckedChange = { savePermanently = it })
                    Text("Update routine permanently")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        steps.mapNotNull { draft ->
                            val supplementId = draft.supplementId ?: return@mapNotNull null
                            SupplementRoutineStep(
                                supplementId = supplementId,
                                timeOfDay = draft.timeOfDay,
                                quantity = draft.quantity.toIntOrNull() ?: 1,
                                dosageOverride = draft.dosageOverride.trim().ifBlank { null },
                                note = draft.note.trim().ifBlank { null }
                            )
                        },
                        notes.trim(),
                        savePermanently
                    )
                },
                enabled = name.isNotBlank() && steps.any { it.supplementId != null }
            ) {
                Text("Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineStepRow(
    step: RoutineStepDraft,
    supplements: List<com.erv.app.supplements.SupplementEntry>,
    onStepChange: (RoutineStepDraft) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSupplement = supplements.firstOrNull { it.id == step.supplementId }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedSupplement?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Supplement") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                supplements.forEach { supplement ->
                    DropdownMenuItem(
                        text = { Text(supplement.name) },
                        onClick = {
                            expanded = false
                            onStepChange(step.copy(supplementId = supplement.id))
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = step.quantity,
            onValueChange = { onStepChange(step.copy(quantity = it.filter { ch -> ch.isDigit() })) },
            label = { Text("Quantity (multiplier)") },
            supportingText = { Text("e.g. 2 = 2× the serving size above") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = step.note,
            onValueChange = { onStepChange(step.copy(note = it)) },
            label = { Text("Step note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        TextButton(
            onClick = onRemove,
            enabled = supplements.size > 1
        ) {
            Text("Remove step")
        }
    }
}

private fun SupplementTimeOfDay.label(): String = when (this) {
    SupplementTimeOfDay.MORNING -> "Morning"
    SupplementTimeOfDay.MIDDAY -> "Midday"
    SupplementTimeOfDay.NIGHT -> "Night"
    SupplementTimeOfDay.OTHER -> "Other"
}

@Composable
private fun RoutineTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                minLines = 2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun routineIconFor(index: Int) = when (index % 8) {
    0 -> Icons.Default.Medication
    1 -> Icons.Default.Bedtime
    2 -> Icons.Default.WbSunny
    3 -> Icons.Default.FitnessCenter
    4 -> Icons.Default.DirectionsRun
    5 -> Icons.Default.Spa
    6 -> Icons.Default.AcUnit
    else -> Icons.Default.Medication
}
