package com.erv.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.erv.app.R
import com.erv.app.cycling.LocalCyclingCsc
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
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
import com.erv.app.cardio.isCyclingActivity
import com.erv.app.cardio.cardioActivityRowsFor
import com.erv.app.cardio.effectiveSteps
import com.erv.app.cardio.stepsSummaryLabel
import com.erv.app.cardio.summaryLine
import com.erv.app.cardio.summaryLabel
import com.erv.app.cardio.needsOutdoorRuckWeightPrompt
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.LaunchPadTileId
import com.erv.app.goals.GoalProgressRow
import com.erv.app.goals.anySelectedGoalMet
import com.erv.app.goals.computeWeeklyGoalProgress
import com.erv.app.goals.summaryLine
import com.erv.app.data.UserPreferences
import com.erv.app.data.mergeVisibleLaunchPadTileOrder
import com.erv.app.data.normalizeLaunchPadTileOrder
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
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramDashboardHeatColdLaunch
import com.erv.app.programs.ProgramDashboardStretchLaunch
import com.erv.app.programs.ProgramDashboardUnifiedRoutineLaunch
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.ProgramSync
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.cardioTimerSessionForProgramBlock
import com.erv.app.programs.encodeHeatColdLaunch
import com.erv.app.programs.encodeStretchLaunch
import com.erv.app.programs.encodeUnifiedRoutineLaunch
import com.erv.app.programs.launchPadLabelsForBlocks
import com.erv.app.programs.programBlockCompletionKey
import com.erv.app.programs.programBlocksForDate
import com.erv.app.programs.programChecklistCompletionKey
import com.erv.app.programs.ProgramStrategyMode
import com.erv.app.programs.resolveProgramStrategyForDate
import com.erv.app.programs.strategySummaryForDate
import com.erv.app.programs.weightRoutineForProgramBlock
import com.erv.app.ui.cardio.CardioElapsedTimerFullScreen
import com.erv.app.ui.cardio.drainCardioGpsIfNeeded
import com.erv.app.ui.cardio.CardioMultiLegTimerFullScreen
import com.erv.app.ui.cardio.CardioWorkoutSummaryFullScreen
import com.erv.app.ui.cardio.OutdoorRuckPackWeightDialog
import com.erv.app.ui.lighttherapy.LightTherapyTimerFullScreen
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.unifiedroutines.UnifiedRoutine
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
import com.erv.app.ui.theme.ErvHeaderRed
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
import com.erv.app.ui.weighttraining.WeightWorkoutSummaryFullScreen
import com.erv.app.weighttraining.weightActivityRowsFor
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

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
    onOpenUnifiedRun: (String) -> Unit,
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
    programRepository: ProgramRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
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
    val heartRateBle = LocalHeartRateBle.current
    val cyclingCscBle = LocalCyclingCsc.current
    val cyclingWorkoutDistanceMeters by cyclingCscBle.workoutDistanceMeters.collectAsState()
    val cyclingSpeedKmh by cyclingCscBle.currentSpeedKmh.collectAsState()
    val cyclingCadenceRpm by cyclingCscBle.currentCadenceRpm.collectAsState()
    val cyclingConnectionState by cyclingCscBle.connectionState.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val supplementState by supplementRepository.state.collectAsState(initial = SupplementLibraryState())
    val lightState by lightTherapyRepository.state.collectAsState(initial = LightLibraryState())
    val cardioState by cardioRepository.state.collectAsState(initial = CardioLibraryState())
    val weightState by weightRepository.state.collectAsState(initial = WeightLibraryState())
    val heatColdState by heatColdRepository.state.collectAsState(initial = HeatColdLibraryState())
    val stretchState by stretchingRepository.state.collectAsState(initial = StretchLibraryState())
    val programsState by programRepository.state.collectAsState(initial = ProgramsLibraryState())
    val unifiedRoutineState by unifiedRoutineRepository.state.collectAsState(initial = com.erv.app.unifiedroutines.UnifiedRoutineLibraryState())
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    val weightTrainingLoadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val goals by userPreferences.goals.collectAsState(initial = emptyList())
    val heartRateBannerExpanded by userPreferences.heartRateBannerExpanded.collectAsState(initial = true)
    val goalsAsOfDate = LocalDate.now()
    val weeklyGoalRows = remember(
        goalsAsOfDate,
        goals,
        supplementState,
        lightState,
        cardioState,
        weightState,
        stretchState,
        programsState,
    ) {
        computeWeeklyGoalProgress(
            asOfDate = goalsAsOfDate,
            goals = goals,
            supplementState = supplementState,
            lightState = lightState,
            cardioState = cardioState,
            weightState = weightState,
            stretchState = stretchState,
            programsState = programsState,
        )
    }
    val anyGoalMetThisWeek = remember(weeklyGoalRows) { anySelectedGoalMet(weeklyGoalRows) }
    val showGoalReachedIndicator = goals.isNotEmpty() && anyGoalMetThisWeek
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
    val unifiedCategory = remember { categories.first { it.id == "unified_routines" } }
    val cardioCategory = remember { categories.first { it.id == "cardio" } }
    val liveWeightDraft by weightLiveWorkoutViewModel.activeDraft.collectAsState()
    val cardioActiveTimer by cardioLiveWorkoutViewModel.activeTimer.collectAsState()
    val cardioLiveUiExpanded by cardioLiveWorkoutViewModel.cardioLiveUiExpanded.collectAsState()
    val activeUnifiedSession = unifiedRoutineState.activeSession
    val heatColdCategory = remember { categories.first { it.id == "heat_cold" } }
    val stretchingCategory = remember { categories.first { it.id == "stretching" } }
    val bodyTrackerCategory = remember { categories.first { it.id == "body_tracker" } }
    val programsCategory = remember { categories.first { it.id == "programs" } }
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
    var selectedWeightWorkout by remember { mutableStateOf<Pair<LocalDate, com.erv.app.weighttraining.WeightWorkoutSession>?>(null) }
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
    var pendingProgramWeightBlock by remember { mutableStateOf<ProgramDayBlock?>(null) }
    val savedLaunchPadTileOrder by userPreferences.launchPadTileOrder.collectAsState(initial = emptyList())
    val savedLaunchPadHiddenTileIds by userPreferences.launchPadHiddenTiles.collectAsState(initial = emptySet())
    var launchPadEditMode by remember { mutableStateOf(false) }
    // Match app ThemeMode (LIGHT/DARK/SYSTEM), not raw system — same as ErvTheme in MainActivity.
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val therapyRedDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val keyManager = LocalKeyManager.current
    val availableLaunchPadTileIds = remember(
        supplementState.routines,
        lightState.routines,
    ) {
        buildSet {
            add(LaunchPadTileId.PROGRAMS)
            add(LaunchPadTileId.WORKOUT_LAUNCHER)
            add(LaunchPadTileId.STRETCHING)
            add(LaunchPadTileId.CARDIO)
            add(LaunchPadTileId.WEIGHT_TRAINING)
            add(LaunchPadTileId.HOT_COLD)
            add(LaunchPadTileId.BODY_TRACKER)
            if (supplementState.routines.isNotEmpty()) add(LaunchPadTileId.SUPPLEMENTS)
            if (lightState.routines.isNotEmpty()) add(LaunchPadTileId.LIGHT_THERAPY)
        }
    }
    val resolvedLaunchPadTileIds = remember(savedLaunchPadTileOrder) {
        normalizeLaunchPadTileOrder(savedLaunchPadTileOrder)
    }
    var draftLaunchPadTileIds by remember { mutableStateOf(resolvedLaunchPadTileIds) }
    var draftLaunchPadHiddenTileIds by remember { mutableStateOf(savedLaunchPadHiddenTileIds) }

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
    LaunchedEffect(resolvedLaunchPadTileIds, savedLaunchPadHiddenTileIds, launchPadEditMode) {
        if (!launchPadEditMode) {
            draftLaunchPadTileIds = resolvedLaunchPadTileIds
            draftLaunchPadHiddenTileIds = savedLaunchPadHiddenTileIds
        }
    }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            val urls = keyManager.relayUrlsForKind30078Publish()
            SupplementSync.publishMaster(
                context.applicationContext,
                relayPool,
                signer,
                supplementRepository.currentState(),
                urls,
            )
        }
    }

    suspend fun syncDailyLog() {
        if (relayPool != null && signer != null) {
            val urls = keyManager.relayUrlsForKind30078Publish()
            supplementRepository.currentState().logFor(today)?.let { log ->
                SupplementSync.publishDailyLog(context.applicationContext, relayPool, signer, log, urls)
            }
            lightTherapyRepository.currentState().logFor(today)?.let { log ->
                LightSync.publishDailyLog(context.applicationContext, relayPool, signer, log, urls)
            }
            cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                CardioSync.publishDailyLog(context.applicationContext, relayPool, signer, log, urls)
            }
            heatColdRepository.currentState().saunaLogFor(today)?.let { log ->
                HeatColdSync.publishSaunaDailyLog(context.applicationContext, relayPool, signer, log, urls)
            }
            heatColdRepository.currentState().coldLogFor(today)?.let { log ->
                HeatColdSync.publishColdDailyLog(context.applicationContext, relayPool, signer, log, urls)
            }
            stretchingRepository.currentState().logFor(today)?.let { log ->
                StretchingSync.publishDailyLog(context.applicationContext, relayPool, signer, log, urls)
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

    fun startProgramBlockFromLaunchPad(block: ProgramDayBlock) {
        when (block.kind) {
            ProgramBlockKind.REST, ProgramBlockKind.CUSTOM -> {
                scope.launch {
                    snackbarHostState.showSnackbar("No live session for this block — see notes in Programs.")
                }
            }
            ProgramBlockKind.FLEX_TRAINING -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Choose Weight Training or Cardio from the Programs sheet for this flexible training block.")
                }
            }
            ProgramBlockKind.UNIFIED_ROUTINE -> {
                val routineId = block.unifiedRoutineId?.takeIf { it.isNotBlank() }
                if (routineId == null || unifiedRoutineState.routines.none { it.id == routineId }) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No unified workout linked — edit this block in Programs.")
                    }
                    return
                }
                scope.launch {
                    userPreferences.setProgramDashboardUnifiedRoutineLaunchJson(
                        encodeUnifiedRoutineLaunch(ProgramDashboardUnifiedRoutineLaunch(routineId = routineId))
                    )
                    onNavigateToCategory(categories.first { it.id == "unified_routines" })
                }
            }
            ProgramBlockKind.OTHER -> {
                scope.launch {
                    snackbarHostState.showSnackbar("Use the checklist on the Programs sheet for this day — habits are checked off there.")
                }
            }
            ProgramBlockKind.WEIGHT -> {
                val routine = weightRoutineForProgramBlock(block, weightState)
                if (routine == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Add exercises or a saved routine to this block first.")
                    }
                    return
                }
                if (cardioLiveWorkoutViewModel.hasActiveTimer) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                    }
                    return
                }
                if (!fgsDisclosureSeen) {
                    pendingProgramWeightBlock = block
                    showWeightFgsDialog = true
                    return
                }
                if (!weightLiveWorkoutViewModel.tryStartFromRoutine(routine, weightState)) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                    }
                    return
                }
                weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                onNavigateToCategory(weightTrainingCategory)
            }
            ProgramBlockKind.CARDIO -> {
                if (weightLiveWorkoutViewModel.hasLiveSession) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.")
                    }
                    return
                }
                scope.launch {
                    delay(120L)
                    val session = cardioTimerSessionForProgramBlock(block, cardioState)
                    if (session == null) {
                        snackbarHostState.showSnackbar("Could not start cardio — check activity or routine in Programs.")
                        return@launch
                    }
                    dashboardStartOrQueueCardio(session)
                }
            }
            ProgramBlockKind.STRETCH_ROUTINE -> {
                val rid = block.stretchRoutineId?.takeIf { it.isNotBlank() }
                if (rid == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No stretch routine linked — edit this block in Programs.")
                    }
                    return
                }
                scope.launch {
                    userPreferences.setProgramDashboardStretchLaunchJson(
                        encodeStretchLaunch(ProgramDashboardStretchLaunch(routineId = rid))
                    )
                    onNavigateToCategory(stretchingCategory)
                }
            }
            ProgramBlockKind.STRETCH_CATALOG -> {
                if (block.stretchCatalogIds.isEmpty()) {
                    scope.launch {
                        snackbarHostState.showSnackbar("No stretches in this block — edit it in Programs.")
                    }
                    return
                }
                scope.launch {
                    userPreferences.setProgramDashboardStretchLaunchJson(
                        encodeStretchLaunch(
                            ProgramDashboardStretchLaunch(
                                stretchIds = block.stretchCatalogIds,
                                title = block.title,
                                holdSecondsPerStretch = (block.stretchHoldSecondsPerStretch ?: 30).coerceIn(5, 300)
                            )
                        )
                    )
                    onNavigateToCategory(stretchingCategory)
                }
            }
            ProgramBlockKind.HEAT_COLD -> {
                val m = block.heatColdMode?.takeIf { it == "SAUNA" || it == "COLD_PLUNGE" } ?: "SAUNA"
                val secs = block.targetMinutes?.times(60)?.coerceIn(30, 7200)
                    ?: if (m == "COLD_PLUNGE") 120 else (15 * 60)
                scope.launch {
                    userPreferences.setProgramDashboardHeatColdLaunchJson(
                        encodeHeatColdLaunch(ProgramDashboardHeatColdLaunch(mode = m, durationSeconds = secs))
                    )
                    onNavigateToCategory(heatColdCategory)
                }
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
                    if (heartRateBle.bleHardwareAvailable) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val showHeartRateBanner = !heartRateBannerExpanded
                                    userPreferences.setHeartRateBannerExpanded(showHeartRateBanner)
                                    if (showHeartRateBanner) {
                                        heartRateBle.tryPreferredDeviceReconnectOnce()
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (heartRateBannerExpanded) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(R.string.dashboard_hr_banner_toggle_cd),
                                tint = if (heartRateBannerExpanded) Color(0xFFFF8A80) else Color.White,
                            )
                        }
                    }
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
                        if (showGoalReachedIndicator) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 2.dp, top = 2.dp),
                                containerColor = Color(0xFFFFC107),
                                contentColor = Color(0xFF1B1B1B),
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
                    containerColor = ErvHeaderRed
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

                    activeUnifiedSession?.let { unifiedSession ->
                        LiveWorkoutInProgressBanner(
                            onClick = { onOpenUnifiedRun(unifiedSession.routineId) },
                            modifier = Modifier.padding(bottom = 8.dp),
                            text = stringResource(R.string.live_unified_in_progress_banner)
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
                }

                val isSelectedDateToday = selectedDate == LocalDate.now()
                if (isSelectedDateToday) {
                    TabRow(selectedTabIndex = dashboardPagerState.currentPage) {
                        Tab(
                            selected = dashboardPagerState.currentPage == 0,
                            onClick = {
                                scope.launch { dashboardPagerState.animateScrollToPage(0) }
                            },
                            text = { Text("Launch Pad") }
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
                                    .padding(bottom = if (launchPadEditMode) 112.dp else 16.dp)
                            ) {
                                Spacer(Modifier.height(14.dp))
                                RoutinesSection(
                                    showSectionHeading = false,
                                    dashboardSelectedDate = selectedDate,
                                    userPreferences = userPreferences,
                                    programsLibraryState = programsState,
                                    programRepository = programRepository,
                                    onOpenProgramsBuilder = { onNavigateToCategory(programsCategory) },
                                    onProgramBlockStart = { block -> startProgramBlockFromLaunchPad(block) },
                                    supplementRoutines = supplementState.routines,
                                    lightRoutines = lightState.routines,
                                    unifiedRoutines = unifiedRoutineState.routines,
                                    cardioRoutines = cardioState.routines,
                                    cardioQuickLaunches = cardioState.quickLaunches,
                                    cardioDistanceUnit = cardioDistanceUnit,
                                    weightRoutines = weightState.routines,
                                    cardioCompletedCount = cardioRows.size,
                                    weightCompletedCount = weightRows.size,
                                    stretchCompletedCount = stretchRows.size,
                                    saunaCompletedCount = saunaRows.size,
                                    coldCompletedCount = coldRows.size,
                                    onSupplementRoutineSelected = { routinePreview = it },
                                    onLightRoutineSelected = { lightRoutinePreview = it },
                                    onUnifiedRoutineSelected = { routine ->
                                        scope.launch {
                                            userPreferences.setProgramDashboardUnifiedRoutineLaunchJson(
                                                encodeUnifiedRoutineLaunch(
                                                    ProgramDashboardUnifiedRoutineLaunch(routineId = routine.id)
                                                )
                                            )
                                            onNavigateToCategory(unifiedCategory)
                                        }
                                    },
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
                                    onOpenUnifiedNewWorkout = {
                                        scope.launch {
                                            userPreferences.setProgramDashboardUnifiedRoutineLaunchJson(
                                                encodeUnifiedRoutineLaunch(
                                                    ProgramDashboardUnifiedRoutineLaunch(createNew = true)
                                                )
                                            )
                                            onNavigateToCategory(unifiedCategory)
                                        }
                                    },
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
                                    onOpenStretchingCategory = { onNavigateToCategory(stretchingCategory) },
                                    onOpenBodyTrackerCategory = { onNavigateToCategory(bodyTrackerCategory) },
                                    launchPadEditMode = launchPadEditMode,
                                    draftLaunchPadTileIds = draftLaunchPadTileIds
                                        .filter { it in availableLaunchPadTileIds && it !in draftLaunchPadHiddenTileIds },
                                    draftHiddenLaunchPadTileIds = draftLaunchPadHiddenTileIds,
                                    onEnterLaunchPadEditMode = { launchPadEditMode = true },
                                    onLaunchPadTilesReordered = { reordered ->
                                        draftLaunchPadTileIds = mergeVisibleLaunchPadTileOrder(
                                            storedOrder = draftLaunchPadTileIds,
                                            visibleOrder = reordered,
                                        )
                                    },
                                    onHideLaunchPadTile = { tileId ->
                                        draftLaunchPadHiddenTileIds = draftLaunchPadHiddenTileIds + tileId
                                    },
                                    onShowLaunchPadTile = { tileId ->
                                        draftLaunchPadHiddenTileIds = draftLaunchPadHiddenTileIds - tileId
                                        draftLaunchPadTileIds =
                                            draftLaunchPadTileIds.filterNot { it == tileId } + tileId
                                    },
                                    relayPool = relayPool,
                                    signer = signer,
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
                                    supplementRows = supplementRows,
                                    lightRows = lightRows,
                                    cardioRows = cardioRows,
                                    weightRows = weightRows,
                                    saunaRows = saunaRows,
                                    coldRows = coldRows,
                                    stretchRows = stretchRows,
                                    onOpenCardioRow = { session ->
                                        cardioWorkoutSummary = CardioTimerCompletionResult(session, null)
                                    },
                                    onOpenWeightRow = { session ->
                                        selectedWeightWorkout = selectedDate to session
                                    }
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
                            supplementRows = supplementRows,
                            lightRows = lightRows,
                            cardioRows = cardioRows,
                            weightRows = weightRows,
                            saunaRows = saunaRows,
                            coldRows = coldRows,
                            stretchRows = stretchRows,
                            onOpenCardioRow = { session ->
                                cardioWorkoutSummary = CardioTimerCompletionResult(session, null)
                            },
                            onOpenWeightRow = { session ->
                                selectedWeightWorkout = selectedDate to session
                            }
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

            if (launchPadEditMode && !hideDashboardChrome) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            userPreferences.setLaunchPadTileOrder(draftLaunchPadTileIds)
                            userPreferences.setLaunchPadHiddenTiles(draftLaunchPadHiddenTileIds)
                            launchPadEditMode = false
                        }
                    },
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text("Done") },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 72.dp)
                )
            }

            WeightLiveWorkoutFgsDisclosureDialog(
                visible = showWeightFgsDialog,
                onDismiss = {
                    showWeightFgsDialog = false
                    pendingDashboardWeightBlank = false
                    pendingDashboardWeightRoutine = null
                    pendingProgramWeightBlock = null
                },
                onContinue = {
                    scope.launch {
                        userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true)
                        showWeightFgsDialog = false
                        val blank = pendingDashboardWeightBlank
                        val pendingR = pendingDashboardWeightRoutine
                        val pendingProg = pendingProgramWeightBlock
                        pendingDashboardWeightBlank = false
                        pendingDashboardWeightRoutine = null
                        pendingProgramWeightBlock = null
                        when {
                            pendingProg != null -> {
                                val routine = weightRoutineForProgramBlock(pendingProg, weightState)
                                if (cardioLiveWorkoutViewModel.hasActiveTimer) {
                                    snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                } else if (routine != null && weightLiveWorkoutViewModel.tryStartFromRoutine(routine, weightState)) {
                                    weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
                                    onNavigateToCategory(weightTrainingCategory)
                                } else {
                                    snackbarHostState.showSnackbar("Could not start this workout from Programs.")
                                }
                            }
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
                    hasGoals = goals.isNotEmpty(),
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
                        val ok = supplementRepository.logRoutineRun(today, r.id, r.name, r.steps)
                        syncDailyLog()
                        if (ok) {
                            snackbarHostState.showSnackbar("Logged ${r.name}")
                        } else {
                            snackbarHostState.showSnackbar("Nothing to log — all supplements in this routine are paused.")
                        }
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
                        val logged = supplementRepository.logRoutineRun(
                            date = today,
                            routineId = updatedRoutine.id,
                            routineName = updatedRoutine.name,
                            steps = updatedRoutine.steps
                        )
                        syncDailyLog()
                        snackbarHostState.showSnackbar(
                            when {
                                savePermanently && logged -> "Updated and logged ${updatedRoutine.name}"
                                savePermanently && !logged ->
                                    "Routine updated. Nothing logged — all steps paused or missing supplements."
                                !savePermanently && logged -> "Logged edited ${updatedRoutine.name}"
                                else -> "Nothing logged — all steps paused or missing supplements."
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
                                LightSync.publishDailyLog(
                                    context.applicationContext,
                                    relayPool,
                                    signer,
                                    log,
                                    keyManager.relayUrlsForKind30078Publish(),
                                )
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
                                LightSync.publishDailyLog(
                                    context.applicationContext,
                                    relayPool,
                                    signer,
                                    log,
                                    keyManager.relayUrlsForKind30078Publish(),
                                )
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
                                    CardioSync.publishDailyLog(
                                        context.applicationContext,
                                        relayPool,
                                        signer,
                                        log,
                                        keyManager.relayUrlsForKind30078Publish(),
                                    )
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

        val pickedWeightWorkout = selectedWeightWorkout
        if (pickedWeightWorkout != null) {
            WeightWorkoutSummaryFullScreen(
                session = pickedWeightWorkout.second,
                logDate = pickedWeightWorkout.first,
                library = weightState,
                loadUnit = weightTrainingLoadUnit,
                userPreferences = userPreferences,
                dark = therapyRedDark,
                mid = therapyRedMid,
                glow = therapyRedGlow,
                relayPool = relayPool,
                signer = signer,
                repository = weightRepository,
                onAfterRoutineSync = {},
                showPostWorkoutActions = false,
                onDone = { selectedWeightWorkout = null }
            )
        } else {
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
                    val cyclingDistanceMeters =
                        if (draft.activity.isCyclingActivity()) cyclingWorkoutDistanceMeters else null
                    val paceOnlyTimer = draft.timerStyle is CardioTimerStyle.CountDownDistance
                    val recordGps =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && dashboardLocationFineGranted && !paceOnlyTimer
                    val showGpsPermissionHint =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && !dashboardLocationFineGranted && !paceOnlyTimer
                    CardioElapsedTimerFullScreen(
                        draft = draft,
                        userPreferences = userPreferences,
                        distanceUnit = cardioDistanceUnit,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        preferredLiveDistanceMeters = cyclingDistanceMeters,
                        cyclingSensorConnected = draft.activity.isCyclingActivity() &&
                            cyclingConnectionState == com.erv.app.cycling.CyclingCscBleConnectionState.Connected,
                        cyclingSpeedKmh = if (draft.activity.isCyclingActivity()) cyclingSpeedKmh else null,
                        cyclingCadenceRpm = if (draft.activity.isCyclingActivity()) cyclingCadenceRpm else null,
                        gpsRecordingActive = recordGps,
                        showGpsPermissionHint = showGpsPermissionHint,
                        onRequestLocationPermission = {
                            requestDashboardCardioLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onLeaveTimerUi = { cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(false) },
                        onStop = { elapsedSeconds, splits ->
                            val gpsPoints = drainCardioGpsIfNeeded(recordGps, context.applicationContext)
                            scope.launch {
                                val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                                val end = nowEpochSeconds()
                                val raw = draft.toSession(
                                    durationMinutes = durationMinutes,
                                    endEpoch = end,
                                    elapsedSecondsForDistance = elapsedSeconds,
                                    gpsPoints = gpsPoints,
                                    preferredDistanceMeters = if (draft.activity.isCyclingActivity()) {
                                        cyclingCscBle.takeWorkoutSummary()?.distanceMeters
                                    } else {
                                        null
                                    },
                                    splits = splits
                                )
                                val hrSummary = heartRateBle.takeWorkoutHeartRateSummary()
                                val withHr = hrSummary?.let { raw.copy(heartRate = it) } ?: raw
                                val session = CardioMetEstimator.applyEstimatedKcal(
                                    withHr,
                                    cardioRepository.currentState(),
                                    weightKg
                                )
                                cardioLiveWorkoutViewModel.clearSession()
                                cardioWorkoutSummary = CardioTimerCompletionResult(session, elapsedSeconds)
                                cardioRepository.addSession(selectedDate, session)
                                cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                    if (relayPool != null && signer != null) {
                                        CardioSync.publishDailyLog(
                                            context.applicationContext,
                                            relayPool,
                                            signer,
                                            log,
                                            keyManager.relayUrlsForKind30078Publish(),
                                        )
                                    }
                                }
                            }
                        },
                        onCancel = {
                            drainCardioGpsIfNeeded(recordGps, context.applicationContext)
                            heartRateBle.discardWorkoutRecording()
                            cyclingCscBle.discardWorkoutRecording()
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
                        userPreferences = userPreferences,
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
                                    val hrSummary = heartRateBle.takeWorkoutHeartRateSummary()
                                    val withHr = hrSummary?.let { session.copy(heartRate = it) } ?: session
                                    val finalSession = CardioMetEstimator.applyEstimatedKcal(
                                        withHr,
                                        cardioRepository.currentState(),
                                        weightKg
                                    )
                                    cardioLiveWorkoutViewModel.clearSession()
                                    cardioWorkoutSummary = CardioTimerCompletionResult(finalSession, null)
                                    cardioRepository.addSession(selectedDate, finalSession)
                                    cardioRepository.currentState().logFor(selectedDate)?.let { log ->
                                        if (relayPool != null && signer != null) {
                                            CardioSync.publishDailyLog(
                                                context.applicationContext,
                                                relayPool,
                                                signer,
                                                log,
                                                keyManager.relayUrlsForKind30078Publish(),
                                            )
                                        }
                                    }
                                } else if (next != null) {
                                    cardioLiveWorkoutViewModel.replaceSession(CardioActiveTimerSession.Multi(next))
                                    snackbarHostState.showSnackbar("Leg saved — next leg started")
                                }
                            }
                        },
                        onCancel = {
                            heartRateBle.discardWorkoutRecording()
                            cardioLiveWorkoutViewModel.clearSession()
                        }
                    )
                }
            }
            null -> Unit
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalsOverviewSheet(
    hasGoals: Boolean,
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
            if (!hasGoals) {
                Text(
                    text = "You have not created any goals yet. Add a few abstract weekly goals and the app will track progress from your logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "This week (Mon-Sun). The trophy shows a small indicator when at least one goal reaches its target.",
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

private data class QuickLogTileSpec(
    val id: LaunchPadTileId,
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val secondaryIcon: ImageVector? = null,
    val statusBadge: QuickLogTileStatusBadge? = null,
)

private enum class QuickLogTileStatusBadge {
    INCOMPLETE,
    COMPLETE
}

private data class ProgramBlockProgressState(
    val isDone: Boolean,
    val allowsManualToggle: Boolean
)

private fun moveLaunchPadTile(
    items: List<LaunchPadTileId>,
    fromIndex: Int,
    toIndex: Int,
): List<LaunchPadTileId> {
    if (fromIndex == toIndex || fromIndex !in items.indices || toIndex !in items.indices) return items
    val mutable = items.toMutableList()
    val moved = mutable.removeAt(fromIndex)
    mutable.add(toIndex, moved)
    return mutable
}

private fun quickLogTileOffsetPx(
    index: Int,
    tileWidthPx: Float,
    tileSpacingPx: Float,
): Pair<Float, Float> {
    val x = if (index % 2 == 0) 0f else tileWidthPx + tileSpacingPx
    val y = (index / 2) * (tileWidthPx + tileSpacingPx)
    return x to y
}

private fun nearestQuickLogTileIndex(
    centerX: Float,
    centerY: Float,
    tileCount: Int,
    tileWidthPx: Float,
    tileSpacingPx: Float,
): Int {
    var nearestIndex = 0
    var nearestDistance = Float.MAX_VALUE
    for (index in 0 until tileCount) {
        val (x, y) = quickLogTileOffsetPx(index, tileWidthPx, tileSpacingPx)
        val dx = centerX - (x + tileWidthPx / 2f)
        val dy = centerY - (y + tileWidthPx / 2f)
        val distance = (dx * dx) + (dy * dy)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
        }
    }
    return nearestIndex
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLogTilesLayout(
    tiles: List<QuickLogTileSpec>,
    tileRowSpacing: Dp,
    editMode: Boolean,
    onEnterEditMode: () -> Unit,
    onTilesReordered: (List<LaunchPadTileId>) -> Unit,
    onTileHidden: (LaunchPadTileId) -> Unit,
) {
    if (tiles.isEmpty()) return
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - tileRowSpacing) / 2
        val rowCount = (tiles.size + 1) / 2
        val containerHeight = tileWidth * rowCount + tileRowSpacing * (rowCount - 1).coerceAtLeast(0)
        val wiggleTransition = rememberInfiniteTransition(label = "launchPadWiggle")
        val wiggleRotation by wiggleTransition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 140, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "launchPadWiggleRotation",
        )
        var draggedTileId by remember(editMode, tiles.map { it.id }) { mutableStateOf<LaunchPadTileId?>(null) }
        var draggedTileStartIndex by remember(editMode, tiles.map { it.id }) { mutableStateOf<Int?>(null) }
        var dragHoverIndex by remember(editMode, tiles.map { it.id }) { mutableStateOf<Int?>(null) }
        var dragPointerX by remember(editMode, tiles.map { it.id }) { mutableStateOf(0f) }
        var dragPointerY by remember(editMode, tiles.map { it.id }) { mutableStateOf(0f) }
        var dragGrabOffsetX by remember(editMode, tiles.map { it.id }) { mutableStateOf(0f) }
        var dragGrabOffsetY by remember(editMode, tiles.map { it.id }) { mutableStateOf(0f) }
        val tileWidthPx = with(LocalDensity.current) { tileWidth.toPx() }
        val tileSpacingPx = with(LocalDensity.current) { tileRowSpacing.toPx() }
        val tileIds = remember(tiles) { tiles.map { it.id } }
        val previewTileIds = remember(tileIds, draggedTileStartIndex, dragHoverIndex) {
            val startIndex = draggedTileStartIndex
            val hoverIndex = dragHoverIndex
            if (startIndex == null || hoverIndex == null) {
                tileIds
            } else {
                moveLaunchPadTile(
                    items = tileIds,
                    fromIndex = startIndex,
                    toIndex = hoverIndex,
                )
            }
        }
        val previewIndicesById = remember(previewTileIds) {
            previewTileIds.withIndex().associate { (index, id) -> id to index }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
        ) {
            tiles.forEachIndexed { index, tile ->
                val previewIndex = previewIndicesById[tile.id] ?: index
                val (baseX, baseY) = quickLogTileOffsetPx(previewIndex, tileWidthPx, tileSpacingPx)
                val isDragged = editMode && draggedTileId == tile.id
                val animatedScale by animateFloatAsState(
                    targetValue = if (isDragged) 1.06f else 1f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                    label = "launchPadTileScale",
                )
                val animatedElevation by animateDpAsState(
                    targetValue = if (isDragged) 10.dp else 4.dp,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                    label = "launchPadTileElevation",
                )
                val animatedBaseX by animateFloatAsState(
                    targetValue = baseX,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                    label = "launchPadTileBaseX",
                )
                val animatedBaseY by animateFloatAsState(
                    targetValue = baseY,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
                    label = "launchPadTileBaseY",
                )
                val tileRotation = when {
                    !editMode || isDragged -> 0f
                    index % 2 == 0 -> wiggleRotation
                    else -> -wiggleRotation
                }
                val drawX = if (isDragged) dragPointerX - dragGrabOffsetX else animatedBaseX
                val drawY = if (isDragged) dragPointerY - dragGrabOffsetY else animatedBaseY
                val interactionModifier = if (editMode) {
                    Modifier.pointerInput(tile.id, tiles) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                draggedTileId = tile.id
                                draggedTileStartIndex = index
                                dragHoverIndex = index
                                dragGrabOffsetX = offset.x
                                dragGrabOffsetY = offset.y
                                dragPointerX = animatedBaseX + offset.x
                                dragPointerY = animatedBaseY + offset.y
                            },
                            onDragCancel = {
                                draggedTileId = null
                                draggedTileStartIndex = null
                                dragHoverIndex = null
                                dragPointerX = 0f
                                dragPointerY = 0f
                                dragGrabOffsetX = 0f
                                dragGrabOffsetY = 0f
                            },
                            onDragEnd = {
                                val startIndex = draggedTileStartIndex
                                val hoverIndex = dragHoverIndex
                                if (startIndex != null && hoverIndex != null && startIndex != hoverIndex) {
                                    onTilesReordered(
                                        moveLaunchPadTile(
                                            items = tileIds,
                                            fromIndex = startIndex,
                                            toIndex = hoverIndex,
                                        )
                                    )
                                }
                                draggedTileId = null
                                draggedTileStartIndex = null
                                dragHoverIndex = null
                                dragPointerX = 0f
                                dragPointerY = 0f
                                dragGrabOffsetX = 0f
                                dragGrabOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                if (draggedTileId != tile.id) return@detectDragGestures
                                change.consume()
                                val nextPointerX = dragPointerX + dragAmount.x
                                val nextPointerY = dragPointerY + dragAmount.y
                                val drawLeft = nextPointerX - dragGrabOffsetX
                                val drawTop = nextPointerY - dragGrabOffsetY
                                val centerX = drawLeft + tileWidthPx / 2f
                                val centerY = drawTop + tileWidthPx / 2f
                                val targetIndex = nearestQuickLogTileIndex(
                                    centerX = centerX,
                                    centerY = centerY,
                                    tileCount = tiles.size,
                                    tileWidthPx = tileWidthPx,
                                    tileSpacingPx = tileSpacingPx,
                                )
                                dragHoverIndex = targetIndex
                                dragPointerX = nextPointerX
                                dragPointerY = nextPointerY
                            },
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = tile.onClick,
                        onLongClick = onEnterEditMode,
                    )
                }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(drawX.roundToInt(), drawY.roundToInt()) }
                        .zIndex(if (isDragged) 10f else 0f)
                        .width(tileWidth)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            rotationZ = tileRotation
                        }
                        .then(interactionModifier)
                ) {
                    RoutineTile(
                        icon = tile.icon,
                        label = tile.label,
                        subtitle = tile.subtitle,
                        modifier = Modifier.fillMaxWidth(),
                        secondaryIcon = tile.secondaryIcon,
                        statusBadge = tile.statusBadge,
                        elevation = animatedElevation,
                        onHide = if (editMode) ({ onTileHidden(tile.id) }) else null,
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
    routines.isEmpty() && quickLaunches.isEmpty() -> "Single sessions & timers"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinesSection(
    showSectionHeading: Boolean = true,
    dashboardSelectedDate: LocalDate,
    userPreferences: UserPreferences,
    programsLibraryState: ProgramsLibraryState,
    programRepository: ProgramRepository,
    onOpenProgramsBuilder: () -> Unit,
    onProgramBlockStart: (ProgramDayBlock) -> Unit,
    supplementRoutines: List<SupplementRoutine>,
    lightRoutines: List<LightRoutine>,
    unifiedRoutines: List<UnifiedRoutine>,
    cardioRoutines: List<CardioRoutine>,
    cardioQuickLaunches: List<CardioQuickLaunch>,
    cardioDistanceUnit: CardioDistanceUnit,
    weightRoutines: List<WeightRoutine>,
    cardioCompletedCount: Int,
    weightCompletedCount: Int,
    stretchCompletedCount: Int,
    saunaCompletedCount: Int,
    coldCompletedCount: Int,
    onSupplementRoutineSelected: (SupplementRoutine) -> Unit,
    onLightRoutineSelected: (LightRoutine) -> Unit,
    onUnifiedRoutineSelected: (UnifiedRoutine) -> Unit,
    onCardioRoutineSelected: (CardioRoutine) -> Unit,
    onCardioQuickLaunchSelected: (CardioQuickLaunch) -> Unit,
    onOpenCardioNewWorkout: () -> Unit,
    onOpenCardioLogBackfill: (LocalDate) -> Unit,
    onOpenUnifiedNewWorkout: () -> Unit,
    onOpenWeightNewWorkout: () -> Unit,
    onOpenWeightLogBackfill: (LocalDate) -> Unit,
    onWeightRoutineSelected: (WeightRoutine) -> Unit,
    onOpenHeatColdCategory: () -> Unit,
    onOpenHeatColdLog: () -> Unit,
    stretchRoutineCount: Int,
    onOpenStretchingCategory: () -> Unit,
    onOpenBodyTrackerCategory: () -> Unit,
    launchPadEditMode: Boolean,
    draftLaunchPadTileIds: List<LaunchPadTileId>,
    draftHiddenLaunchPadTileIds: Set<LaunchPadTileId>,
    onEnterLaunchPadEditMode: () -> Unit,
    onLaunchPadTilesReordered: (List<LaunchPadTileId>) -> Unit,
    onHideLaunchPadTile: (LaunchPadTileId) -> Unit,
    onShowLaunchPadTile: (LaunchPadTileId) -> Unit,
    relayPool: RelayPool?,
    signer: EventSigner?,
) {
    val context = LocalContext.current
    val keyManager = LocalKeyManager.current
    var showSupplementPicker by remember { mutableStateOf(false) }
    var showLightPicker by remember { mutableStateOf(false) }
    var showWorkoutLauncherSheet by remember { mutableStateOf(false) }
    var showCardioPicker by remember { mutableStateOf(false) }
    var showWeightPicker by remember { mutableStateOf(false) }
    var showHeatColdSheet by remember { mutableStateOf(false) }
    var showProgramSheet by remember { mutableStateOf(false) }
    var showAddLaunchPadTileMenu by remember { mutableStateOf(false) }
    val routineSectionScope = rememberCoroutineScope()
    val resolvedProgram = remember(programsLibraryState, dashboardSelectedDate) {
        programsLibraryState.resolveProgramStrategyForDate(dashboardSelectedDate)
    }
    val activeProgram = resolvedProgram.program
    val isManualProgramSelection = programsLibraryState.strategy.mode == ProgramStrategyMode.MANUAL
    val usesProgramStrategy = resolvedProgram.isUsingStrategy
    val programStrategySummary = remember(programsLibraryState, dashboardSelectedDate) {
        programsLibraryState.strategySummaryForDate(dashboardSelectedDate)
    }
    val programBlocks = remember(programsLibraryState, dashboardSelectedDate, activeProgram?.id) {
        programsLibraryState.programBlocksForDate(dashboardSelectedDate)
    }
    val programRowLabels = remember(programBlocks) { launchPadLabelsForBlocks(programBlocks) }
    val programBlockProgress = remember(
        activeProgram?.id,
        dashboardSelectedDate,
        programBlocks,
        programsLibraryState.completionState,
        cardioCompletedCount,
        weightCompletedCount,
        stretchCompletedCount,
        saunaCompletedCount,
        coldCompletedCount
    ) {
        val pid = activeProgram?.id ?: return@remember emptyMap()
        var remainingCardio = cardioCompletedCount
        var remainingWeight = weightCompletedCount
        var remainingStretch = stretchCompletedCount
        var remainingSauna = saunaCompletedCount
        var remainingCold = coldCompletedCount
        val progress = LinkedHashMap<String, ProgramBlockProgressState>()
        programBlocks.forEach { block ->
            val state = when (block.kind) {
                ProgramBlockKind.OTHER -> {
                    val done = block.checklistItems.isNotEmpty() &&
                        block.checklistItems.indices.all { itemIdx ->
                            val ck = programChecklistCompletionKey(pid, block.id, itemIdx, dashboardSelectedDate)
                            programsLibraryState.isCompletionDone(ck)
                        }
                    ProgramBlockProgressState(isDone = done, allowsManualToggle = false)
                }
                ProgramBlockKind.WEIGHT -> {
                    val done = remainingWeight > 0
                    if (done) remainingWeight--
                    ProgramBlockProgressState(isDone = done, allowsManualToggle = false)
                }
                ProgramBlockKind.CARDIO -> {
                    val done = remainingCardio > 0
                    if (done) remainingCardio--
                    ProgramBlockProgressState(isDone = done, allowsManualToggle = false)
                }
                ProgramBlockKind.UNIFIED_ROUTINE -> {
                    val key = programBlockCompletionKey(pid, block.id, dashboardSelectedDate)
                    ProgramBlockProgressState(
                        isDone = programsLibraryState.isCompletionDone(key),
                        allowsManualToggle = true
                    )
                }
                ProgramBlockKind.FLEX_TRAINING -> {
                    val done = (remainingWeight + remainingCardio) > 0
                    if (done) {
                        if (remainingWeight > 0) remainingWeight-- else remainingCardio--
                    }
                    ProgramBlockProgressState(isDone = done, allowsManualToggle = false)
                }
                ProgramBlockKind.STRETCH_ROUTINE, ProgramBlockKind.STRETCH_CATALOG -> {
                    val done = remainingStretch > 0
                    if (done) remainingStretch--
                    ProgramBlockProgressState(isDone = done, allowsManualToggle = false)
                }
                ProgramBlockKind.HEAT_COLD -> {
                    val done = when (block.heatColdMode) {
                        "COLD_PLUNGE" -> {
                            val ok = remainingCold > 0
                            if (ok) remainingCold--
                            ok
                        }
                        else -> {
                            val ok = remainingSauna > 0
                            if (ok) remainingSauna--
                            ok
                        }
                    }
                    ProgramBlockProgressState(isDone = done, allowsManualToggle = false)
                }
                ProgramBlockKind.REST, ProgramBlockKind.CUSTOM -> {
                    val key = programBlockCompletionKey(pid, block.id, dashboardSelectedDate)
                    ProgramBlockProgressState(
                        isDone = programsLibraryState.isCompletionDone(key),
                        allowsManualToggle = true
                    )
                }
            }
            progress[block.id] = state
        }
        progress
    }
    val programTileStatus = remember(
        activeProgram?.id,
        programBlocks,
        programBlockProgress
    ) {
        if (activeProgram?.id == null) return@remember null
        if (programBlocks.isEmpty()) return@remember null
        val allDone = programBlocks.all { block -> programBlockProgress[block.id]?.isDone == true }
        if (allDone) QuickLogTileStatusBadge.COMPLETE else QuickLogTileStatusBadge.INCOMPLETE
    }
    val programTileSubtitle = when {
        activeProgram == null -> programStrategySummary
        programBlocks.isEmpty() && usesProgramStrategy -> "Rest day · $programStrategySummary"
        programBlocks.isEmpty() -> "Rest day"
        usesProgramStrategy -> "${programBlocks.size} for this day · $programStrategySummary"
        else -> "${programBlocks.size} for this day"
    }

    if (showSectionHeading) {
        Text(
            text = "Launch Pad",
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
                    text = "Create supplement or light routines, or use Programs, Stretching, Cardio, Weight Training, Body Tracker, and Hot + Cold.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val availableTileSpecs = remember(
                programTileSubtitle,
                programTileStatus,
                stretchRoutineCount,
                cardioRoutines,
                cardioQuickLaunches,
                weightRoutines,
                supplementRoutines,
                lightRoutines,
            ) {
                buildMap<LaunchPadTileId, QuickLogTileSpec> {
                    put(
                        LaunchPadTileId.PROGRAMS,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.PROGRAMS,
                            icon = Icons.Default.CalendarMonth,
                            label = "Programs",
                            subtitle = programTileSubtitle,
                            onClick = { showProgramSheet = true },
                            statusBadge = programTileStatus,
                        )
                    )
                    put(
                        LaunchPadTileId.WORKOUT_LAUNCHER,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.WORKOUT_LAUNCHER,
                            icon = Icons.Default.PlaylistPlay,
                            label = "Unified Workouts",
                            subtitle = if (unifiedRoutines.isEmpty()) {
                                "Mixed workouts & routines"
                            } else {
                                "${unifiedRoutines.size} mixed routines"
                            },
                            onClick = { showWorkoutLauncherSheet = true },
                        )
                    )
                    put(
                        LaunchPadTileId.STRETCHING,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.STRETCHING,
                            icon = Icons.Default.FavoriteBorder,
                            label = "Stretching",
                            subtitle = when {
                                stretchRoutineCount == 0 -> "Browse & routines"
                                stretchRoutineCount == 1 -> "1 routine"
                                else -> "$stretchRoutineCount routines"
                            },
                            onClick = onOpenStretchingCategory,
                        )
                    )
                    put(
                        LaunchPadTileId.CARDIO,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.CARDIO,
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            label = "Cardio",
                            subtitle = cardioRoutineShortcutsSubtitle(cardioRoutines, cardioQuickLaunches),
                            onClick = { showCardioPicker = true },
                        )
                    )
                    put(
                        LaunchPadTileId.WEIGHT_TRAINING,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.WEIGHT_TRAINING,
                            icon = Icons.Default.FitnessCenter,
                            label = "Weight Training",
                            subtitle = if (weightRoutines.isEmpty()) "New workout" else "${weightRoutines.size} routines",
                            onClick = { showWeightPicker = true },
                        )
                    )
                    put(
                        LaunchPadTileId.HOT_COLD,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.HOT_COLD,
                            icon = Icons.Default.Thermostat,
                            label = "Hot + Cold",
                            subtitle = "Timer · pick session type",
                            onClick = { showHeatColdSheet = true },
                            secondaryIcon = Icons.Default.AcUnit,
                        )
                    )
                    put(
                        LaunchPadTileId.BODY_TRACKER,
                        QuickLogTileSpec(
                            id = LaunchPadTileId.BODY_TRACKER,
                            icon = Icons.Default.MonitorWeight,
                            label = "Body Tracker",
                            subtitle = "Measurements & photos",
                            onClick = onOpenBodyTrackerCategory,
                        )
                    )
                    if (supplementRoutines.isNotEmpty()) {
                        put(
                            LaunchPadTileId.SUPPLEMENTS,
                            QuickLogTileSpec(
                                id = LaunchPadTileId.SUPPLEMENTS,
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
                            )
                        )
                    }
                    if (lightRoutines.isNotEmpty()) {
                        put(
                            LaunchPadTileId.LIGHT_THERAPY,
                            QuickLogTileSpec(
                                id = LaunchPadTileId.LIGHT_THERAPY,
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
                            )
                        )
                    }
                }
            }
            val quickLogTileRowSpacing = 12.dp
            val quickLogTiles = remember(draftLaunchPadTileIds, availableTileSpecs) {
                draftLaunchPadTileIds.mapNotNull { availableTileSpecs[it] }
            }
            val hiddenQuickLogTiles = remember(availableTileSpecs, draftHiddenLaunchPadTileIds) {
                availableTileSpecs.values.filter { it.id in draftHiddenLaunchPadTileIds }
            }
            if (launchPadEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Drag tiles to rearrange your Launch Pad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (hiddenQuickLogTiles.isNotEmpty()) {
                        Box {
                            FilledTonalButton(onClick = { showAddLaunchPadTileMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add")
                            }
                            DropdownMenu(
                                expanded = showAddLaunchPadTileMenu,
                                onDismissRequest = { showAddLaunchPadTileMenu = false },
                            ) {
                                hiddenQuickLogTiles.forEach { tile ->
                                    DropdownMenuItem(
                                        text = { Text(tile.label) },
                                        onClick = {
                                            showAddLaunchPadTileMenu = false
                                            onShowLaunchPadTile(tile.id)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = tile.icon,
                                                contentDescription = null,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (quickLogTiles.isEmpty()) {
                Text(
                    text = "All Launch Pad tiles are hidden. Add back the ones you want.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onEnterLaunchPadEditMode,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit Launch Pad")
                }
            }
            QuickLogTilesLayout(
                tiles = quickLogTiles,
                tileRowSpacing = quickLogTileRowSpacing,
                editMode = launchPadEditMode,
                onEnterEditMode = onEnterLaunchPadEditMode,
                onTilesReordered = onLaunchPadTilesReordered,
                onTileHidden = onHideLaunchPadTile,
            )
            if (launchPadEditMode) {
                Text(
                    text = "Tap the minus button to hide a tile. Hidden tiles stay in the menu below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showProgramSheet) {
        val programSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showProgramSheet = false },
            sheetState = programSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = activeProgram?.name ?: resolvedProgram.title,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Planned for $dashboardSelectedDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (usesProgramStrategy && !resolvedProgram.detail.isNullOrBlank()) {
                    Text(
                        text = resolvedProgram.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    activeProgram == null -> {
                        if (programsLibraryState.programs.isEmpty()) {
                            Text(
                                "Create a program from a template or import JSON under Programs, then pick it here as your active plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = {
                                    showProgramSheet = false
                                    onOpenProgramsBuilder()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Programs")
                            }
                        } else if (!isManualProgramSelection) {
                            Text(
                                "This date is controlled by your program strategy. Open Programs to edit the rotation or challenge plan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = {
                                    showProgramSheet = false
                                    onOpenProgramsBuilder()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Manage strategy in Programs")
                            }
                        } else {
                            Text(
                                "Tap a program to use it on the Launch Pad for this device. You can change it anytime.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                items(programsLibraryState.programs, key = { it.id }) { p ->
                                    Card(
                                        onClick = {
                                            routineSectionScope.launch {
                                                programRepository.activateProgramForDate(p.id)
                                                if (relayPool != null && signer != null) {
                                                    ProgramSync.publishMaster(
                                                        appContext = context.applicationContext,
                                                        relayPool = relayPool,
                                                        signer = signer,
                                                        state = programRepository.currentState(),
                                                        dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                                    )
                                                }
                                                showProgramSheet = false
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(
                                                text = p.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            p.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                                Text(
                                                    text = desc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    showProgramSheet = false
                                    onOpenProgramsBuilder()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create or manage programs")
                            }
                        }
                    }
                    programBlocks.isEmpty() -> {
                        Text(
                            "Nothing scheduled for this day — rest or add blocks in Programs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = {
                                showProgramSheet = false
                                onOpenProgramsBuilder()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Programs")
                        }
                    }
                    else -> {
                        if (usesProgramStrategy) {
                            Text(
                                "This schedule comes from your program strategy. Edit the strategy or weekly plan in Programs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            itemsIndexed(programBlocks) { index, block ->
                                val pid = activeProgram!!.id
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        text = programRowLabels.getOrElse(index) { "Activity" },
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (block.kind == ProgramBlockKind.OTHER) {
                                        Spacer(Modifier.height(6.dp))
                                        if (block.checklistItems.isEmpty()) {
                                            Text(
                                                text = block.notes?.takeIf { it.isNotBlank() }
                                                    ?: "Add checklist lines in Programs.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            block.checklistItems.forEachIndexed { itemIdx, label ->
                                                val ck = programChecklistCompletionKey(
                                                    pid,
                                                    block.id,
                                                    itemIdx,
                                                    dashboardSelectedDate
                                                )
                                                val done = programsLibraryState.isCompletionDone(ck)
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = done,
                                                        onCheckedChange = { checked ->
                                                            routineSectionScope.launch {
                                                                programRepository.setProgramChecklistItemDone(
                                                                    programId = pid,
                                                                    blockId = block.id,
                                                                    itemIndex = itemIdx,
                                                                    date = dashboardSelectedDate,
                                                                    done = checked
                                                                )
                                                                if (relayPool != null && signer != null) {
                                                                    ProgramSync.publishDailyProgress(
                                                                        appContext = context.applicationContext,
                                                                        relayPool = relayPool,
                                                                        signer = signer,
                                                                        state = programRepository.currentState(),
                                                                        date = dashboardSelectedDate,
                                                                        dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    )
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val blockProgress = programBlockProgress[block.id]
                                            ?: ProgramBlockProgressState(false, false)
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (blockProgress.allowsManualToggle) {
                                                    Checkbox(
                                                        checked = blockProgress.isDone,
                                                        onCheckedChange = { checked ->
                                                            routineSectionScope.launch {
                                                                programRepository.setProgramBlockDone(
                                                                    programId = pid,
                                                                    blockId = block.id,
                                                                    date = dashboardSelectedDate,
                                                                    done = checked
                                                                )
                                                                if (relayPool != null && signer != null) {
                                                                    ProgramSync.publishDailyProgress(
                                                                        appContext = context.applicationContext,
                                                                        relayPool = relayPool,
                                                                        signer = signer,
                                                                        state = programRepository.currentState(),
                                                                        date = dashboardSelectedDate,
                                                                        dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    )
                                                    Text(
                                                        text = if (blockProgress.isDone) "Done" else "Not done",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 2.dp)
                                                    )
                                                } else {
                                                    Text(
                                                        text = if (blockProgress.isDone) "Logged in app" else "Not logged yet",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (blockProgress.isDone) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.error
                                                        }
                                                    )
                                                }
                                            }
                                            if (block.kind == ProgramBlockKind.FLEX_TRAINING) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            showProgramSheet = false
                                                            onOpenWeightNewWorkout()
                                                        }
                                                    ) {
                                                        Text("Weight")
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            showProgramSheet = false
                                                            onOpenCardioNewWorkout()
                                                        }
                                                    ) {
                                                        Text("Cardio")
                                                    }
                                                }
                                            } else {
                                                TextButton(
                                                    onClick = {
                                                        showProgramSheet = false
                                                        onProgramBlockStart(block)
                                                    }
                                                ) {
                                                    Text("Start")
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
    if (showWorkoutLauncherSheet) {
        UnifiedRoutinePickerSheet(
            routines = unifiedRoutines,
            onDismiss = { showWorkoutLauncherSheet = false },
            onNewWorkout = {
                onOpenUnifiedNewWorkout()
                showWorkoutLauncherSheet = false
            },
            onSelectRoutine = { routine ->
                onUnifiedRoutineSelected(routine)
                showWorkoutLauncherSheet = false
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
private fun UnifiedRoutinePickerSheet(
    routines: List<UnifiedRoutine>,
    onDismiss: () -> Unit,
    onNewWorkout: () -> Unit,
    onSelectRoutine: (UnifiedRoutine) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Unified Workouts",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                "Build mixed workouts here. Single cardio quick starts stay on the Cardio tile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Surface(
                onClick = onNewWorkout,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("New Workout", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Build a mixed weight, cardio, and stretching workout on the fly, then save it later if you want to repeat it",
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
                    "Saved mixed workouts",
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
                    items(routines.sortedBy { it.name.lowercase() }, key = { it.id }) { routine ->
                        Surface(
                            onClick = { onSelectRoutine(routine) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    routine.name.ifBlank { "Workout" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    if (routine.blocks.size == 1) "1 block" else "${routine.blocks.size} blocks",
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
                        "Start a single cardio session, launch a timer, or save a cardio-only routine",
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
                                "Quick cardio starts",
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
    supplementRows: List<SupplementActivityRow>,
    lightRows: List<LightActivityRow>,
    cardioRows: List<CardioActivityRow>,
    weightRows: List<WeightActivityRow>,
    saunaRows: List<HeatColdActivityRow>,
    coldRows: List<HeatColdActivityRow>,
    stretchRows: List<StretchActivityRow>,
    onOpenCardioRow: (com.erv.app.cardio.CardioSession) -> Unit = {},
    onOpenWeightRow: (com.erv.app.weighttraining.WeightWorkoutSession) -> Unit = {}
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
                                modifier = Modifier.clickable { onOpenCardioRow(row.session) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (hasWeight) {
                    if (hasSupplements || hasLight || hasCardio) Spacer(Modifier.height(12.dp))
                    ActivityCategorySection(title = "Weight Training") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            weightRows.forEach { row ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.clickable { onOpenWeightRow(row.session) }
                                ) {
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
    val activeSteps = remember(routine.steps) {
        routine.steps.filterNot { it.paused }
    }
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
            if (activeSteps.isEmpty()) {
                Text(
                    text = "No active supplements to log (all steps are paused).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    activeSteps.forEach { step ->
                        val supplement = supplements.firstOrNull { it.id == step.supplementId }
                        Text(
                            text = step.activityStyleSummary(supplement),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onLogAsIs, enabled = activeSteps.isNotEmpty()) {
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
    val note: String = "",
    val paused: Boolean = false
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
                            note = it.note.orEmpty(),
                            paused = it.paused
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
                                note = draft.note.trim().ifBlank { null },
                                paused = draft.paused
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

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(if (step.paused) 0.55f else 1f)
    ) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pause (skip when logging)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Switch(
                checked = step.paused,
                onCheckedChange = { onStepChange(step.copy(paused = it)) },
                enabled = step.supplementId != null
            )
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
    modifier: Modifier = Modifier,
    secondaryIcon: ImageVector? = null,
    statusBadge: QuickLogTileStatusBadge? = null,
    elevation: Dp = 4.dp,
    onHide: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier.aspectRatio(1f),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
            statusBadge?.let { badge ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Surface(
                        color = if (badge == QuickLogTileStatusBadge.COMPLETE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (badge == QuickLogTileStatusBadge.COMPLETE) "✓" else "!",
                            color = if (badge == QuickLogTileStatusBadge.COMPLETE) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onError
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            onHide?.let { hideTile ->
                FilledIconButton(
                    onClick = hideTile,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Hide $label",
                    )
                }
            }
        }
    }
}

private fun routineIconFor(index: Int) = when (index % 7) {
    0 -> Icons.Default.Medication
    1 -> Icons.Default.Bedtime
    2 -> Icons.Default.WbSunny
    3 -> Icons.Default.FitnessCenter
    4 -> Icons.AutoMirrored.Filled.DirectionsRun
    5 -> Icons.Default.Thermostat
    else -> Icons.Default.Medication
}
