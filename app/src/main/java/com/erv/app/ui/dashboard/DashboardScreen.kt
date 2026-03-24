package com.erv.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erv.app.R
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.navigation.Category
import com.erv.app.ui.navigation.CategorySheet
import com.erv.app.ui.navigation.HotColdDualIcons
import com.erv.app.ui.navigation.RelayDataSyncTopBarIcon
import com.erv.app.ui.navigation.categories
import com.erv.app.ui.weighttraining.LiveWorkoutInProgressBanner
import com.erv.app.ui.weighttraining.WeightLiveWorkoutFgsDisclosureDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
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
import com.erv.app.cardio.CardioQuickLaunch
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioSync
import com.erv.app.cardio.CardioTimerSessionDraft
import com.erv.app.cardio.CardioTimerStyle
import com.erv.app.cardio.eligibleForPhoneGps
import com.erv.app.cardio.cardioActivityRowsFor
import com.erv.app.cardio.effectiveSteps
import com.erv.app.cardio.stepsSummaryLabel
import com.erv.app.cardio.summaryLine
import com.erv.app.cardio.summaryLabel
import com.erv.app.cardio.needsOutdoorRuckWeightPrompt
import com.erv.app.data.BodyWeightUnit
import com.erv.app.goals.GoalProgressRow
import com.erv.app.goals.anySelectedGoalMet
import com.erv.app.goals.computeWeeklyGoalProgress
import com.erv.app.goals.summaryLine
import com.erv.app.data.UserPreferences
import com.erv.app.heatcold.HeatColdActivityRow
import com.erv.app.heatcold.HeatColdLibraryState
import com.erv.app.stretching.StretchActivityRow
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.StretchingSync
import com.erv.app.stretching.stretchActivityFor
import com.erv.app.heatcold.HeatColdRepository
import com.erv.app.heatcold.HeatColdSync
import com.erv.app.heatcold.coldActivityFor
import com.erv.app.heatcold.saunaActivityFor
import com.erv.app.heatcold.summaryLine
import com.erv.app.lighttherapy.LightTherapyRepository
import com.erv.app.ui.cardio.CardioElapsedTimerFullScreen
import com.erv.app.ui.cardio.drainCardioGpsIfNeeded
import com.erv.app.ui.cardio.CardioMultiLegTimerFullScreen
import com.erv.app.ui.cardio.CardioWorkoutSummaryFullScreen
import com.erv.app.ui.cardio.OutdoorRuckPackWeightDialog
import com.erv.app.ui.lighttherapy.LightTherapyTimerFullScreen
import com.erv.app.lighttherapy.LightTimeOfDay
import com.erv.app.lighttherapy.lightActivityFor
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvCategoryMenuMutedGold
import com.erv.app.ui.theme.ErvDarkCategoryMenuDivider
import com.erv.app.ui.theme.ErvDarkCategoryMenuHandleAccent
import com.erv.app.ui.theme.ErvDarkCategoryMenuMutedGold
import com.erv.app.ui.theme.ErvDarkCategoryMenuOnSurface
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    /** Sauna / cold plunge combined history (same pattern as other category logs). */
    onOpenHeatColdLog: () -> Unit,
    supplementRepository: SupplementRepository,
    lightTherapyRepository: LightTherapyRepository,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    heatColdRepository: HeatColdRepository,
    stretchingRepository: StretchingRepository,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
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
    val heatColdState by heatColdRepository.state.collectAsState(initial = HeatColdLibraryState())
    val stretchState by stretchingRepository.state.collectAsState(initial = StretchLibraryState())
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    val weightTrainingLoadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val selectedGoalIds by userPreferences.selectedGoalIds.collectAsState(initial = emptySet())
    val goalsAsOfDate = LocalDate.now()
    val weeklyGoalRows = remember(
        goalsAsOfDate,
        selectedGoalIds,
        supplementState,
        lightState,
        cardioState,
        weightState,
        stretchState,
    ) {
        computeWeeklyGoalProgress(
            asOfDate = goalsAsOfDate,
            selectedGoalIds = selectedGoalIds,
            supplementState = supplementState,
            lightState = lightState,
            cardioState = cardioState,
            weightState = weightState,
            stretchState = stretchState,
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
    val saunaRows = remember(heatColdState, selectedDate) {
        heatColdState.saunaActivityFor(selectedDate)
    }
    val coldRows = remember(heatColdState, selectedDate) {
        heatColdState.coldActivityFor(selectedDate)
    }
    val stretchRows = remember(stretchState, selectedDate) {
        stretchState.stretchActivityFor(selectedDate)
    }
    val datesWithActivity = remember(
        supplementState,
        lightState,
        cardioState,
        weightState,
        heatColdState,
        stretchState
    ) {
        datesWithActivityLogged(
            supplementState,
            lightState,
            cardioState,
            weightState,
            heatColdState,
            stretchState
        )
    }
    val weightTrainingCategory = remember { categories.first { it.id == "weight_training" } }
    val cardioCategory = remember { categories.first { it.id == "cardio" } }
    val liveWeightDraft by weightLiveWorkoutViewModel.activeDraft.collectAsState()
    val cardioActiveTimer by cardioLiveWorkoutViewModel.activeTimer.collectAsState()
    val cardioLiveUiExpanded by cardioLiveWorkoutViewModel.cardioLiveUiExpanded.collectAsState()
    val heatColdCategory = remember { categories.first { it.id == "heat_cold" } }
    val stretchingCategory = remember { categories.first { it.id == "stretching" } }
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    var showCalendar by remember { mutableStateOf(false) }
    var routinePreview by remember { mutableStateOf<SupplementRoutine?>(null) }
    var routineEditor by remember { mutableStateOf<SupplementRoutine?>(null) }
    var lightRoutinePreview by remember { mutableStateOf<LightRoutine?>(null) }
    var lightTimerSession by remember { mutableStateOf<LightTimerSession?>(null) }
    var cardioRoutinePreview by remember { mutableStateOf<CardioRoutine?>(null) }
    var pendingDashboardQuickLaunchRuck by remember { mutableStateOf<CardioQuickLaunch?>(null) }
    var showDashboardCardioFgsDialog by remember { mutableStateOf(false) }
    var pendingDashboardCardioSession by remember { mutableStateOf<CardioActiveTimerSession?>(null) }
    var cardioWorkoutSummary by remember { mutableStateOf<CardioTimerCompletionResult?>(null) }
    var dashboardLocationFineGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val requestDashboardCardioLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> dashboardLocationFineGranted = ok }
    var showGoalsSheet by remember { mutableStateOf(false) }
    var showErvSummarySheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    var showWeightFgsDialog by remember { mutableStateOf(false) }
    var pendingDashboardWeightBlank by remember { mutableStateOf(false) }
    var pendingDashboardWeightRoutine by remember { mutableStateOf<WeightRoutine?>(null) }
    // Match app ThemeMode (LIGHT/DARK/SYSTEM), not raw system — same as ErvTheme in MainActivity.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val therapyRedDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            confirmValueChange = { newValue -> newValue != SheetValue.Hidden },
            skipHiddenState = true,
        )
    )

    val hideDashboardChrome =
        (cardioActiveTimer != null && cardioLiveUiExpanded) || cardioWorkoutSummary != null

    LaunchedEffect(hideDashboardChrome) {
        if (!hideDashboardChrome) {
            scaffoldState.bottomSheetState.partialExpand()
        }
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
            heatColdRepository.currentState().saunaLogFor(today)?.let { log ->
                HeatColdSync.publishSaunaDailyLog(relayPool, signer, log)
            }
            heatColdRepository.currentState().coldLogFor(today)?.let { log ->
                HeatColdSync.publishColdDailyLog(relayPool, signer, log)
            }
            stretchingRepository.currentState().logFor(today)?.let { log ->
                StretchingSync.publishDailyLog(relayPool, signer, log)
            }
        }
    }

    fun dashboardStartOrQueueCardio(session: CardioActiveTimerSession) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            scope.launch {
                snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.")
            }
            return
        }
        if (!fgsDisclosureSeen) {
            pendingDashboardCardioSession = session
            showDashboardCardioFgsDialog = true
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

    Box(modifier = modifier.fillMaxSize()) {
        BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Always use a normal peek height. Do not gate topBar/sheetDragHandle/sheetPeekHeight on
        // hideDashboardChrome: Material3 BottomSheetScaffoldLayout can crash (IndexOutOfBoundsException,
        // empty subcompose slot list on Android 16) when any of those slots compose to nothing or 0.dp.
        // The live cardio / summary overlay is drawn above this scaffold and covers the chrome.
        sheetPeekHeight = 48.dp,
        sheetContainerColor = if (darkTheme) ErvDarkCategoryMenuMutedGold else ErvCategoryMenuMutedGold,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 4.dp,
        sheetContent = {
            Box(modifier = Modifier.heightIn(min = 48.dp)) {
                CategorySheet(
                    onCategoryClick = { category ->
                        scope.launch {
                            scaffoldState.bottomSheetState.partialExpand()
                            onNavigateToCategory(category)
                        }
                    },
                    modifier = Modifier.wrapContentHeight()
                )
            }
        },
        sheetDragHandle = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (darkTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(ErvDarkCategoryMenuHandleAccent)
                    )
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = ErvDarkCategoryMenuDivider
                    )
                } else {
                    // Bright hairline + saturated band so the slider edge reads clearly on light gold.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color(0xFFFFD600))
                    )
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_categories_menu),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (darkTheme) {
                            ErvDarkCategoryMenuOnSurface.copy(alpha = 0.82f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            onClick = { showErvSummarySheet = true },
                            onClickLabel = "About ERV",
                        ),
                    ) {
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
                    RelayDataSyncTopBarIcon(contentColor = Color.White)
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
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The dashboard keeps routines lightweight: select, preview, then log or edit.
            val dashboardPagerState = rememberPagerState(pageCount = { 2 })
            val routinesScrollState = rememberScrollState()
            val activityScrollState = rememberScrollState()
            LaunchedEffect(Unit) {
                dashboardPagerState.scrollToPage(0)
            }
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(6.dp))

                    if (liveWeightDraft != null) {
                        LiveWorkoutInProgressBanner(
                            onClick = {
                                weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                                onNavigateToCategory(weightTrainingCategory)
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (cardioActiveTimer != null && !cardioLiveUiExpanded) {
                        LiveWorkoutInProgressBanner(
                            onClick = {
                                cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
                                onNavigateToCategory(cardioCategory)
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                            text = stringResource(R.string.live_cardio_in_progress_banner)
                        )
                    }

                    DateNavigator(
                        selectedDate = selectedDate,
                        onPreviousDay = viewModel::previousDay,
                        onNextDay = viewModel::nextDay,
                        onPreviousWeek = viewModel::previousWeek,
                        onNextWeek = viewModel::nextWeek,
                        onTodayClick = viewModel::goToToday,
                        onCalendarClick = { showCalendar = true }
                    )

                    Spacer(Modifier.height(8.dp))

                    if (selectedGoalIds.isNotEmpty()) {
                        DashboardGoalsSection(
                            rows = weeklyGoalRows,
                            onOpenDetails = { showGoalsSheet = true },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                val isSelectedDateToday = selectedDate == LocalDate.now()
                if (isSelectedDateToday) {
                    TabRow(selectedTabIndex = dashboardPagerState.currentPage) {
                        Tab(
                            selected = dashboardPagerState.currentPage == 0,
                            onClick = {
                                scope.launch { dashboardPagerState.animateScrollToPage(0) }
                            },
                            text = { Text("Quick Log") }
                        )
                        Tab(
                            selected = dashboardPagerState.currentPage == 1,
                            onClick = {
                                scope.launch { dashboardPagerState.animateScrollToPage(1) }
                            },
                            text = { Text("Activity") }
                        )
                    }

                    HorizontalPager(
                        state = dashboardPagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(routinesScrollState)
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp)
                            ) {
                                Spacer(Modifier.height(14.dp))
                                RoutinesSection(
                                    showSectionHeading = false,
                                    dashboardSelectedDate = selectedDate,
                                    supplementRoutines = supplementState.routines,
                                    lightRoutines = lightState.routines,
                                    cardioRoutines = cardioState.routines,
                                    cardioQuickLaunches = cardioState.quickLaunches,
                                    cardioDistanceUnit = cardioDistanceUnit,
                                    weightRoutines = weightState.routines,
                                    onSupplementRoutineSelected = { routinePreview = it },
                                    onLightRoutineSelected = { lightRoutinePreview = it },
                                    onCardioRoutineSelected = { cardioRoutinePreview = it },
                                    onCardioQuickLaunchSelected = { ql ->
                                        if (ql.needsOutdoorRuckWeightPrompt()) {
                                            pendingDashboardQuickLaunchRuck = ql
                                        } else {
                                            dashboardStartOrQueueCardio(
                                                CardioActiveTimerSession.Single(
                                                    CardioTimerSessionDraft.fromQuickLaunch(ql)
                                                )
                                            )
                                        }
                                    },
                                    onOpenCardioNewWorkout = onOpenCardioNewWorkout,
                                    onOpenCardioLogBackfill = onOpenCardioLogBackfill,
                                    onOpenWeightNewWorkout = {
                                        when {
                                            cardioLiveWorkoutViewModel.hasActiveTimer -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                                }
                                            }
                                            !fgsDisclosureSeen -> {
                                                pendingDashboardWeightBlank = true
                                                pendingDashboardWeightRoutine = null
                                                showWeightFgsDialog = true
                                            }
                                            !weightLiveWorkoutViewModel.tryStartBlank() -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                                                }
                                            }
                                            else -> onNavigateToCategory(weightTrainingCategory)
                                        }
                                    },
                                    onWeightRoutineSelected = { routine ->
                                        when {
                                            cardioLiveWorkoutViewModel.hasActiveTimer -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                                }
                                            }
                                            !fgsDisclosureSeen -> {
                                                pendingDashboardWeightRoutine = routine
                                                pendingDashboardWeightBlank = false
                                                showWeightFgsDialog = true
                                            }
                                            !weightLiveWorkoutViewModel.tryStartFromRoutine(routine, weightState) -> {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                                                }
                                            }
                                            else -> onNavigateToCategory(weightTrainingCategory)
                                        }
                                    },
                                    onOpenWeightLogBackfill = onOpenWeightLogBackfill,
                                    onOpenHeatColdCategory = { onNavigateToCategory(heatColdCategory) },
                                    onOpenHeatColdLog = onOpenHeatColdLog,
                                    stretchRoutineCount = stretchState.routines.size,
                                    onOpenStretchingCategory = { onNavigateToCategory(stretchingCategory) }
                                )
                            }

                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(activityScrollState)
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp)
                            ) {
                                Spacer(Modifier.height(14.dp))
                                ActivitySection(
                                    showSectionHeading = false,
                                    selectedDate = selectedDate,
                                    supplementRows = supplementRows,
                                    lightRows = lightRows,
                                    cardioRows = cardioRows,
                                    weightRows = weightRows,
                                    saunaRows = saunaRows,
                                    coldRows = coldRows,
                                    stretchRows = stretchRows
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(activityScrollState)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        Spacer(Modifier.height(14.dp))
                        ActivitySection(
                            showSectionHeading = false,
                            selectedDate = selectedDate,
                            supplementRows = supplementRows,
                            lightRows = lightRows,
                            cardioRows = cardioRows,
                            weightRows = weightRows,
                            saunaRows = saunaRows,
                            coldRows = coldRows,
                            stretchRows = stretchRows
                        )
                    }
                }
            }

            if (showCalendar) {
                CalendarPopup(
                    selectedDate = selectedDate,
                    onDateSelected = { date ->
                        viewModel.selectDate(date)
                        showCalendar = false
                    },
                    onDismiss = { showCalendar = false },
                    datesWithActivity = datesWithActivity
                )
            }

            WeightLiveWorkoutFgsDisclosureDialog(
                visible = showWeightFgsDialog,
                onDismiss = {
                    showWeightFgsDialog = false
                    pendingDashboardWeightBlank = false
                    pendingDashboardWeightRoutine = null
                },
                onContinue = {
                    scope.launch {
                        userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true)
                        showWeightFgsDialog = false
                        val blank = pendingDashboardWeightBlank
                        val pendingR = pendingDashboardWeightRoutine
                        pendingDashboardWeightBlank = false
                        pendingDashboardWeightRoutine = null
                        when {
                            blank -> {
                                if (cardioLiveWorkoutViewModel.hasActiveTimer) {
                                    snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                } else if (weightLiveWorkoutViewModel.tryStartBlank()) {
                                    onNavigateToCategory(weightTrainingCategory)
                                } else {
                                    snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                                }
                            }
                            pendingR != null -> {
                                if (cardioLiveWorkoutViewModel.hasActiveTimer) {
                                    snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                } else if (weightLiveWorkoutViewModel.tryStartFromRoutine(pendingR, weightState)) {
                                    onNavigateToCategory(weightTrainingCategory)
                                } else {
                                    snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                                }
                            }
                        }
                    }
                }
            )

            WeightLiveWorkoutFgsDisclosureDialog(
                visible = showDashboardCardioFgsDialog,
                onDismiss = {
                    showDashboardCardioFgsDialog = false
                    pendingDashboardCardioSession = null
                },
                onContinue = {
                    scope.launch {
                        userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true)
                        showDashboardCardioFgsDialog = false
                        val pending = pendingDashboardCardioSession
                        pendingDashboardCardioSession = null
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

            if (showErvSummarySheet) {
                ErvSummarySheet(onDismiss = { showErvSummarySheet = false })
            }

            routinePreview?.let { routine ->
                RoutinePreviewSheet(
                    routine = routine,
                    supplements = supplementState.supplements,
                    onDismiss = { routinePreview = null },
                    onLogAsIs = {
                    val r = routine
                    routinePreview = null
                    scope.launch {
                        supplementRepository.logRoutineRun(today, r.id, r.name, r.steps)
                        syncDailyLog()
                        snackbarHostState.showSnackbar("Logged ${r.name}")
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
                    val base = routine
                    routineEditor = null
                    scope.launch {
                        val updatedRoutine = base.copy(
                            name = updatedName,
                            steps = steps,
                            notes = notes
                        )
                        if (savePermanently) {
                            supplementRepository.renameRoutine(
                                routineId = base.id,
                                name = updatedRoutine.name,
                                timeOfDay = updatedRoutine.timeOfDay,
                                steps = updatedRoutine.steps,
                                notes = updatedRoutine.notes
                            )
                            reminderRepository.updateRoutineName(base.id, updatedRoutine.name)
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
                    val r = routine
                    lightRoutinePreview = null
                    scope.launch {
                        val device = r.deviceId?.let { lightState.deviceById(it) }
                        lightTherapyRepository.logSession(
                            date = today,
                            minutes = r.durationMinutes,
                            deviceId = r.deviceId,
                            deviceName = device?.name,
                            routineId = r.id,
                            routineName = r.name
                        )
                        lightTherapyRepository.currentState().logFor(today)?.let { log ->
                            if (relayPool != null && signer != null) {
                                LightSync.publishDailyLog(relayPool, signer, log)
                            }
                        }
                        snackbarHostState.showSnackbar("Logged ${r.name} • ${r.durationMinutes} min")
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
                    val s = session
                    lightTimerSession = null
                    scope.launch {
                        lightTherapyRepository.logSession(
                            date = today,
                            minutes = s.minutes,
                            deviceId = s.deviceId,
                            deviceName = s.deviceName,
                            routineId = s.routineId,
                            routineName = s.routineName
                        )
                        lightTherapyRepository.currentState().logFor(today)?.let { log ->
                            if (relayPool != null && signer != null) {
                                LightSync.publishDailyLog(relayPool, signer, log)
                            }
                        }
                        snackbarHostState.showSnackbar("Logged ${s.routineName ?: "Light therapy"} • ${s.minutes} min")
                    }
                },
                    onCancel = { lightTimerSession = null }
                )
            }

            pendingDashboardQuickLaunchRuck?.let { ql ->
                OutdoorRuckPackWeightDialog(
                    quickLaunchName = ql.name,
                    defaultRuckLoadKg = ql.defaultRuckLoadKg,
                    onDismiss = { pendingDashboardQuickLaunchRuck = null },
                    onStart = { kg ->
                        dashboardStartOrQueueCardio(
                            CardioActiveTimerSession.Single(
                                CardioTimerSessionDraft.fromQuickLaunch(ql, ruckLoadKg = kg)
                            )
                        )
                        pendingDashboardQuickLaunchRuck = null
                    }
                )
            }

            cardioRoutinePreview?.let { routine ->
                CardioRoutinePreviewSheet(
                    routine = routine,
                    onDismiss = { cardioRoutinePreview = null },
                    onLogSession = {
                        val r = routine
                        cardioRoutinePreview = null
                        scope.launch {
                            val mins = r.targetDurationMinutes ?: 30
                            val session = CardioMetEstimator.buildSessionFromRoutine(
                                routine = r,
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
                            snackbarHostState.showSnackbar("Logged ${r.name}")
                        }
                    },
                    onStartTimer = { r ->
                        // Dismiss the sheet first; starting the full-screen timer + FGS while the modal is
                        // still composed can crash (same-frame overlap with ModalBottomSheet).
                        cardioRoutinePreview = null
                        scope.launch {
                            delay(120L)
                            val draft = CardioTimerSessionDraft.fromRoutine(r)
                            if (draft != null) {
                                dashboardStartOrQueueCardio(CardioActiveTimerSession.Single(draft))
                            } else {
                                CardioMultiLegTimerState.fromRoutine(r)?.let { m ->
                                    dashboardStartOrQueueCardio(CardioActiveTimerSession.Multi(m))
                                }
                            }
                        }
                    }
                )
            }
        }
    }

        val cSummary = cardioWorkoutSummary
        if (cSummary != null) {
            CardioWorkoutSummaryFullScreen(
                session = cSummary.session,
                logDate = selectedDate,
                repository = cardioRepository,
                elapsedSeconds = cSummary.elapsedSeconds,
                distanceUnit = cardioDistanceUnit,
                dark = therapyRedDark,
                mid = therapyRedMid,
                glow = therapyRedGlow,
                relayPool = relayPool,
                signer = signer,
                userPreferences = userPreferences,
                onDone = { cardioWorkoutSummary = null }
            )
        } else when (val ct = cardioActiveTimer) {
            is CardioActiveTimerSession.Single -> {
                if (cardioLiveUiExpanded) {
                    val draft = ct.draft
                    val paceOnlyTimer = draft.timerStyle is CardioTimerStyle.CountDownDistance
                    val recordGps =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && dashboardLocationFineGranted && !paceOnlyTimer
                    val showGpsPermissionHint =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && !dashboardLocationFineGranted && !paceOnlyTimer
                    CardioElapsedTimerFullScreen(
                        draft = draft,
                        distanceUnit = cardioDistanceUnit,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        gpsRecordingActive = recordGps,
                        showGpsPermissionHint = showGpsPermissionHint,
                        onRequestLocationPermission = {
                            requestDashboardCardioLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onLeaveTimerUi = { cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(false) },
                        onStop = { elapsedSeconds ->
                            val gpsPoints = drainCardioGpsIfNeeded(recordGps, context.applicationContext)
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
                                    cardioRepository.currentState(),
                                    weightKg
                                )
                                cardioLiveWorkoutViewModel.clearSession()
                                cardioWorkoutSummary = CardioTimerCompletionResult(session, elapsedSeconds)
                                cardioRepository.addSession(selectedDate, session)
                                cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                    if (relayPool != null && signer != null) {
                                        CardioSync.publishDailyLog(relayPool, signer, log)
                                    }
                                }
                            }
                        },
                        onCancel = {
                            drainCardioGpsIfNeeded(recordGps, context.applicationContext)
                            cardioLiveWorkoutViewModel.clearSession()
                        }
                    )
                }
            }
            is CardioActiveTimerSession.Multi -> {
                if (cardioLiveUiExpanded) {
                    val multiKey = ct.state.currentLegIndex to ct.state.completedSegments.size
                    CardioMultiLegTimerFullScreen(
                        state = ct.state,
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
                                    ct.state,
                                    durationMinutes,
                                    end,
                                    cardioRepository.currentState(),
                                    weightKg
                                )
                                if (session != null) {
                                    cardioLiveWorkoutViewModel.clearSession()
                                    cardioWorkoutSummary = CardioTimerCompletionResult(session, null)
                                    cardioRepository.addSession(selectedDate, session)
                                    cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                        if (relayPool != null && signer != null) {
                                            CardioSync.publishDailyLog(relayPool, signer, log)
                                        }
                                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeatColdQuickSheet(
    title: String,
    onDismiss: () -> Unit,
    onLogNewSession: () -> Unit,
    onViewLog: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Surface(
                onClick = {
                    onLogNewSession()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Log a new session", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Choose hot (sauna) or cold plunge, then run the timer (optional temperature)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                onClick = {
                    onViewLog()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("View log", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hot + Cold history by date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun cardioRoutineShortcutsSubtitle(
    routines: List<CardioRoutine>,
    quickLaunches: List<CardioQuickLaunch>
): String = when {
    routines.isEmpty() && quickLaunches.isEmpty() -> "New workout"
    else -> buildString {
        val parts = mutableListOf<String>()
        if (routines.isNotEmpty()) parts.add("${routines.size} routines")
        if (quickLaunches.isNotEmpty()) {
            val n = quickLaunches.size
            parts.add(if (n == 1) "1 quick start" else "$n quick starts")
        }
        append(parts.joinToString(" · "))
    }
}

@Composable
private fun RoutinesSection(
    showSectionHeading: Boolean = true,
    dashboardSelectedDate: LocalDate,
    supplementRoutines: List<SupplementRoutine>,
    lightRoutines: List<LightRoutine>,
    cardioRoutines: List<CardioRoutine>,
    cardioQuickLaunches: List<CardioQuickLaunch>,
    cardioDistanceUnit: CardioDistanceUnit,
    weightRoutines: List<WeightRoutine>,
    onSupplementRoutineSelected: (SupplementRoutine) -> Unit,
    onLightRoutineSelected: (LightRoutine) -> Unit,
    onCardioRoutineSelected: (CardioRoutine) -> Unit,
    onCardioQuickLaunchSelected: (CardioQuickLaunch) -> Unit,
    onOpenCardioNewWorkout: () -> Unit,
    onOpenCardioLogBackfill: (LocalDate) -> Unit,
    onOpenWeightNewWorkout: () -> Unit,
    onOpenWeightLogBackfill: (LocalDate) -> Unit,
    onWeightRoutineSelected: (WeightRoutine) -> Unit,
    onOpenHeatColdCategory: () -> Unit,
    onOpenHeatColdLog: () -> Unit,
    stretchRoutineCount: Int,
    onOpenStretchingCategory: () -> Unit,
) {
    var showSupplementPicker by remember { mutableStateOf(false) }
    var showLightPicker by remember { mutableStateOf(false) }
    var showCardioPicker by remember { mutableStateOf(false) }
    var showWeightPicker by remember { mutableStateOf(false) }
    var showHeatColdSheet by remember { mutableStateOf(false) }

    if (showSectionHeading) {
        Text(
            text = "Quick Log",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
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
                    text = "Create supplement or light routines, or use Cardio, Weight Training, and Stretching. Hot + Cold timer is below.",
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
                    subtitle = cardioRoutineShortcutsSubtitle(cardioRoutines, cardioQuickLaunches),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoutineTile(
                    icon = Icons.Default.Thermostat,
                    secondaryIcon = Icons.Default.AcUnit,
                    label = "Hot + Cold",
                    subtitle = "Timer · pick session type",
                    onClick = { showHeatColdSheet = true },
                    modifier = Modifier.weight(1f)
                )
                RoutineTile(
                    icon = Icons.Default.FavoriteBorder,
                    label = "Stretching",
                    subtitle = if (stretchRoutineCount == 0) "Browse & routines" else if (stretchRoutineCount == 1) "1 routine" else "$stretchRoutineCount routines",
                    onClick = onOpenStretchingCategory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (showHeatColdSheet) {
        HeatColdQuickSheet(
            title = "Hot + Cold",
            onDismiss = { showHeatColdSheet = false },
            onLogNewSession = onOpenHeatColdCategory,
            onViewLog = onOpenHeatColdLog
        )
    }

    if (showSupplementPicker) {
        SupplementRoutinePickerSheet(
            routines = supplementRoutines,
            onDismiss = { showSupplementPicker = false },
            onSelect = { routine ->
                showSupplementPicker = false
                onSupplementRoutineSelected(routine)
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
            quickLaunches = cardioQuickLaunches,
            distanceUnit = cardioDistanceUnit,
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
            },
            onSelectQuickLaunch = { ql ->
                onCardioQuickLaunchSelected(ql)
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
    quickLaunches: List<CardioQuickLaunch>,
    distanceUnit: CardioDistanceUnit,
    onDismiss: () -> Unit,
    onNewWorkout: () -> Unit,
    onLogPreviousWorkout: () -> Unit,
    onSelectRoutine: (CardioRoutine) -> Unit,
    onSelectQuickLaunch: (CardioQuickLaunch) -> Unit
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
            val listMaxHeight = if (quickLaunches.isNotEmpty() && routines.isNotEmpty()) 320.dp else 400.dp
            if (quickLaunches.isNotEmpty() || routines.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMaxHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (quickLaunches.isNotEmpty()) {
                        item {
                            Text(
                                "Quick start",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(quickLaunches, key = { it.id }) { ql ->
                            Surface(
                                onClick = { onSelectQuickLaunch(ql) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ql.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            ql.summaryLabel(distanceUnit),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (routines.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Multi-leg routines",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                    if (routines.isNotEmpty()) {
                        if (quickLaunches.isEmpty()) {
                            item {
                                Text(
                                    "Routines",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
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
    showSectionHeading: Boolean = true,
    selectedDate: LocalDate,
    supplementRows: List<SupplementActivityRow>,
    lightRows: List<LightActivityRow>,
    cardioRows: List<CardioActivityRow>,
    weightRows: List<WeightActivityRow>,
    saunaRows: List<HeatColdActivityRow>,
    coldRows: List<HeatColdActivityRow>,
    stretchRows: List<StretchActivityRow>
) {
    if (showSectionHeading) {
        Text(
            text = "Activity",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
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
            val hasSauna = saunaRows.isNotEmpty()
            val hasCold = coldRows.isNotEmpty()
            val hasStretch = stretchRows.isNotEmpty()

            if (!hasSupplements && !hasLight && !hasCardio && !hasWeight && !hasSauna && !hasCold && !hasStretch) {
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            weightRows.forEach { row ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = row.headerLine,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    row.exerciseBlocks.forEach { block ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = block.titleLine,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            block.setLines.forEach { line ->
                                                Text(
                                                    text = line,
                                                    modifier = Modifier.padding(start = 12.dp),
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
                if (hasSauna) {
                    if (hasSupplements || hasLight || hasCardio || hasWeight) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Sauna") {
                        saunaRows.forEach { row ->
                            Text(
                                text = row.summaryLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasCold) {
                    if (hasSupplements || hasLight || hasCardio || hasWeight || hasSauna) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Cold Plunge") {
                        coldRows.forEach { row ->
                            Text(
                                text = row.summaryLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasStretch) {
                    if (hasSupplements || hasLight || hasCardio || hasWeight || hasSauna || hasCold) {
                        Spacer(Modifier.height(12.dp))
                    }
                    ActivityCategorySection(title = "Stretching") {
                        stretchRows.forEach { row ->
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
    modifier: Modifier = Modifier,
    secondaryIcon: ImageVector? = null
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
            if (secondaryIcon != null) {
                HotColdDualIcons(
                    iconSize = 26.dp,
                    heatIcon = icon,
                    coldIcon = secondaryIcon
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
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

private fun routineIconFor(index: Int) = when (index % 7) {
    0 -> Icons.Default.Medication
    1 -> Icons.Default.Bedtime
    2 -> Icons.Default.WbSunny
    3 -> Icons.Default.FitnessCenter
    4 -> Icons.Default.DirectionsRun
    5 -> Icons.Default.Thermostat
    else -> Icons.Default.Medication
}
