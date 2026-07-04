@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)


package com.erv.app.ui.cardio

import android.Manifest
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.ui.components.FormSectionLabelMedium
import com.erv.app.ui.components.FormSectionLabelSmall
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.erv.app.ui.components.FieldLabel
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.cardioBuiltinActivitiesForUserSelection
import com.erv.app.cardio.cardioBuiltinHiitSectionOrder
import com.erv.app.cardio.cardioBuiltinHybridSectionOrder
import com.erv.app.cardio.cardioBuiltinSteadySectionOrder
import com.erv.app.cardio.isHybridMachineSection
import com.erv.app.cardio.offersHiitIntervalTemplate
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
import com.erv.app.cardio.CardioRouteMediaBackup
import com.erv.app.cardio.isPendingStart
import com.erv.app.cardio.CardioMultiLegTimerState
import com.erv.app.cardio.CardioTrackShareImage
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.CardioSync
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.ui.components.SectionLogRelayResyncIconButton
import com.erv.app.cardio.CardioQuickLaunch
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioRoutineStep
import com.erv.app.cardio.CardioErgMetrics
import com.erv.app.cardio.CardioSession
import com.erv.app.cardio.CardioSessionSource
import com.erv.app.cardio.CardioWorkoutSplit
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
import com.erv.app.SectionLogDateFilter
import com.erv.app.cardio.DatedCardioSession
import com.erv.app.cardio.datedCardioSessionsForSectionLog
import com.erv.app.cardio.effectiveSteps
import com.erv.app.cardio.stepsSummaryLabel
import com.erv.app.cardio.derivedTreadmillDistanceMeters
import com.erv.app.cardio.distanceFieldLabelOptional
import com.erv.app.cardio.formatCardioAveragePace
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioElevationGainLoss
import com.erv.app.cardio.resolvedElevationMeters
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.formatCardioElapsedClock
import com.erv.app.cardio.metersToCardioDistanceInputString
import com.erv.app.cardio.parseCardioDistanceInputToMeters
import com.erv.app.cardio.defaultSprintIndoorTreadmillParams
import com.erv.app.cardio.displayName
import com.erv.app.cardio.isCyclingActivity
import com.erv.app.cardio.isErgMonitorActivity
import com.erv.app.cardio.isStrokeErgActivity
import com.erv.app.cardio.label
import com.erv.app.cardio.nowEpochSeconds
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.cardio.segmentPace
import com.erv.app.cardio.shortLabel
import com.erv.app.cardio.summaryLine
import com.erv.app.cardio.summaryLabel
import com.erv.app.cardio.needsOutdoorRuckWeightPrompt
import com.erv.app.cardio.supportsTreadmillModality
import com.erv.app.data.CardioLiveSplitMode
import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import com.erv.app.nostr.BlossomUploader
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.Nip96Uploader
import com.erv.app.nostr.UnsignedEvent
import com.erv.app.nostr.buildWorkoutShareHashtagContentLine
import com.erv.app.nostr.parseExtraWorkoutHashtagTopics
import com.erv.app.nostr.workoutShareKind1TopicTags
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import com.erv.app.ui.media.playHiitWorkSegmentEndCue
import com.erv.app.ui.media.playHiitWorkSegmentStartCue
import com.erv.app.ui.media.playHiitSoftSegmentStartCue
import com.erv.app.ui.dashboard.SectionLogCalendarSheet
import com.erv.app.ui.dashboard.SectionLogFilterBar
import com.erv.app.ui.dashboard.sectionLogFilterSummary
import com.erv.app.ui.dashboard.datesWithCardioActivity
import com.erv.app.ui.weighttraining.LiveWorkoutInProgressBanner
import com.erv.app.ui.media.WorkoutMediaControlPanel
import com.erv.app.cycling.Concept2BleConnectionState
import com.erv.app.cycling.Concept2ScanRow
import com.erv.app.cycling.CyclingCscBleConnectionState
import com.erv.app.cycling.CyclingCscScanRow
import com.erv.app.cycling.LocalConcept2Pm
import com.erv.app.cycling.LocalCyclingCsc
import com.erv.app.data.SavedBluetoothDevice
import com.erv.app.data.displayName
import com.erv.app.hr.HeartRateTopBar
import com.erv.app.hr.HeartRateSessionAnalyticsSection
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.hr.requiredBlePermissionsForHeartRate
import com.erv.app.ui.weighttraining.WeightLiveWorkoutFgsDisclosureDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.unifiedroutines.UnifiedRoutineBlockType
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.unifiedroutines.linkFor
import com.erv.app.workouts.ActiveWorkoutItemLaunch
import com.erv.app.workouts.WorkoutActiveRun
import com.erv.app.workouts.WorkoutLibraryState
import com.erv.app.workouts.WorkoutLoggedItemKind
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.activeWorkoutCardioLaunch
import com.erv.app.workouts.isFinalLoggableStep
import com.erv.app.workouts.linkFor
import kotlin.math.min
import kotlin.math.roundToInt
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

private data class CardioParentRunContext(
    val unifiedRoutineId: String?,
    val unifiedBlockId: String?,
    val workoutLaunch: ActiveWorkoutItemLaunch?,
    val workoutRun: WorkoutActiveRun?,
)

private fun resolveCardioParentRunContext(
    unifiedState: UnifiedRoutineLibraryState,
    workoutState: WorkoutLibraryState,
): CardioParentRunContext {
    val activeUnifiedSession = unifiedState.activeSession
    val activeUnifiedBlockId = activeUnifiedSession?.lastLaunchedBlockId?.takeIf { blockId ->
        unifiedState
            .routineById(activeUnifiedSession.routineId)
            ?.blocks
            ?.firstOrNull { it.id == blockId }
            ?.type == UnifiedRoutineBlockType.CARDIO
    }
    val workoutLaunch = workoutState.activeWorkoutCardioLaunch()
    val workoutRun = workoutState.activeRun?.takeIf { workoutLaunch != null }
    return CardioParentRunContext(
        unifiedRoutineId = activeUnifiedSession?.routineId,
        unifiedBlockId = activeUnifiedBlockId,
        workoutLaunch = workoutLaunch,
        workoutRun = workoutRun,
    )
}

private fun CardioSession.withParentRunLink(
    ctx: CardioParentRunContext,
    unifiedState: UnifiedRoutineLibraryState,
): CardioSession = when {
    ctx.unifiedRoutineId != null && ctx.unifiedBlockId != null -> {
        val recap = unifiedState.sessionById(unifiedState.activeSession!!.sessionId)
        copy(unifiedLink = recap?.linkFor(ctx.unifiedBlockId))
    }
    ctx.workoutLaunch != null && ctx.workoutRun != null -> {
        copy(workoutLink = ctx.workoutRun.linkFor(ctx.workoutLaunch.segmentId, ctx.workoutLaunch.itemId))
    }
    else -> this
}

private suspend fun persistCardioSessionAfterLiveTimer(
    session: CardioSession,
    elapsedSeconds: Int?,
    ctx: CardioParentRunContext,
    today: LocalDate,
    cardioRepository: CardioRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    workoutRepository: WorkoutRepository,
    syncDailyLog: suspend (com.erv.app.cardio.CardioDayLog) -> Unit,
    onReturnToUnifiedRun: (String) -> Unit,
    onReturnToWorkoutRun: (String) -> Unit,
    clearCardioSession: () -> Unit,
    showStandaloneSummary: (CardioTimerCompletionResult) -> Unit,
) {
    cardioRepository.addSession(today, session)
    when {
        ctx.unifiedRoutineId != null && ctx.unifiedBlockId != null -> {
            unifiedRoutineRepository.attachLoggedBlock(
                routineId = ctx.unifiedRoutineId,
                blockId = ctx.unifiedBlockId,
                logDate = today.toString(),
                entryId = session.id,
            )
            cardioRepository.currentState().logFor(today)?.let { syncDailyLog(it) }
            clearCardioSession()
            onReturnToUnifiedRun(ctx.unifiedRoutineId)
        }
        ctx.workoutLaunch != null -> {
            workoutRepository.completeLaunchedItem(
                logDate = today.toString(),
                entryId = session.id,
                kind = WorkoutLoggedItemKind.CARDIO,
            )
            cardioRepository.currentState().logFor(today)?.let { syncDailyLog(it) }
            clearCardioSession()
            onReturnToWorkoutRun(ctx.workoutLaunch.workoutId)
        }
        else -> {
            cardioRepository.currentState().logFor(today)?.let { syncDailyLog(it) }
            clearCardioSession()
            showStandaloneSummary(CardioTimerCompletionResult(session, elapsedSeconds))
        }
    }
}

private fun CardioParentRunContext.returnToParentRun(
    onReturnToUnifiedRun: (String) -> Unit,
    onReturnToWorkoutRun: (String) -> Unit,
): Boolean = when {
    unifiedRoutineId != null && unifiedBlockId != null -> {
        onReturnToUnifiedRun(unifiedRoutineId)
        true
    }
    workoutLaunch != null -> {
        onReturnToWorkoutRun(workoutLaunch.workoutId)
        true
    }
    else -> false
}

private val cardioAutoSplitQuarterMileOptions = (1..12).toList()

private fun formatCardioSplitDistanceMiles(quarterMiles: Int): String =
    String.format(Locale.US, "%.2f mi", quarterMiles.coerceIn(1, 12) / 4.0)

private fun formatCardioSpeedFromKmh(speedKmh: Double, distanceUnit: CardioDistanceUnit): String =
    when (distanceUnit) {
        CardioDistanceUnit.MILES -> String.format(Locale.US, "%.1f mph", speedKmh * 0.621371)
        CardioDistanceUnit.KILOMETERS -> String.format(Locale.US, "%.1f km/h", speedKmh)
    }

private fun formatCardioAverageSpeed(
    elapsedSeconds: Int?,
    distanceMeters: Double?,
    distanceUnit: CardioDistanceUnit
): String? {
    val sec = elapsedSeconds?.takeIf { it > 0 } ?: return null
    val meters = distanceMeters?.takeIf { it > 1.0 && it.isFinite() } ?: return null
    val kmh = (meters / sec) * 3.6
    return formatCardioSpeedFromKmh(kmh, distanceUnit)
}

private fun cardioSplitModeLabel(mode: CardioLiveSplitMode): Int = when (mode) {
    CardioLiveSplitMode.OFF -> R.string.cardio_split_mode_off
    CardioLiveSplitMode.MANUAL -> R.string.cardio_split_mode_manual
    CardioLiveSplitMode.AUTO -> R.string.cardio_split_mode_auto
}

@Composable
fun CardioCategoryScreen(
    repository: CardioRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    workoutRepository: WorkoutRepository,
    userPreferences: UserPreferences,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onReturnToUnifiedRun: (String) -> Unit = {},
    onReturnToWorkoutRun: (String) -> Unit = {},
    onOpenLog: () -> Unit,
    initialTab: String = CardioTab.Activities.name,
    initialOpenNewWorkout: Boolean = false,
    onConsumedInitialOpenNewWorkout: () -> Unit = {}
) {
    val state by repository.state.collectAsState(initial = CardioLibraryState())
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(
        initial = com.erv.app.hr.HeartRateZoneInputs(),
    )
    val timerContext = LocalContext.current
    val timerAppContext = remember(timerContext) { timerContext.applicationContext }
    val heartRateBle = LocalHeartRateBle.current
    val cyclingCscBle = LocalCyclingCsc.current
    val concept2Ble = LocalConcept2Pm.current
    val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
    val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
    val cyclingWorkoutDistanceMeters by cyclingCscBle.workoutDistanceMeters.collectAsState()
    val cyclingSpeedKmh by cyclingCscBle.currentSpeedKmh.collectAsState()
    val cyclingCadenceRpm by cyclingCscBle.currentCadenceRpm.collectAsState()
    val cyclingConnectionState by cyclingCscBle.connectionState.collectAsState()
    val ergConnectionState by concept2Ble.connectionState.collectAsState()
    val ergWorkoutDistanceMeters by concept2Ble.workoutDistanceMeters.collectAsState()
    val ergSpeedKmh by concept2Ble.currentSpeedKmh.collectAsState()
    val ergCadenceRpm by concept2Ble.currentCadenceRpm.collectAsState()
    val ergPowerWatts by concept2Ble.currentPowerWatts.collectAsState()
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
    val resolvedInitialTab = CardioTab.entries
        .firstOrNull { it.name.equals(initialTab, ignoreCase = true) }
        ?.ordinal
        ?: CardioTab.Activities.ordinal
    var activeTab by rememberSaveable(key = "cardio_tab_activities_routines") { mutableIntStateOf(resolvedInitialTab) }
    LaunchedEffect(resolvedInitialTab) {
        activeTab = resolvedInitialTab
    }
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
    var completedWorkoutSummaryLogged by remember { mutableStateOf(true) }
    var completedWorkoutUnifiedRoutineId by remember { mutableStateOf<String?>(null) }
    var completedWorkoutUnifiedBlockId by remember { mutableStateOf<String?>(null) }
    var showCardioStatsSheet by remember { mutableStateOf(false) }
    val allTimeCardioLogEntries = remember(state) {
        state.datedCardioSessionsForSectionLog(SectionLogDateFilter.AllHistory)
    }
    val darkTheme = isSystemInDarkTheme()
    val therapyRedDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val therapyRedGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val keyManager = LocalKeyManager.current

    LaunchedEffect(initialOpenNewWorkout) {
        if (initialOpenNewWorkout) {
            workoutBuilder = WorkoutBuilderMode.NewSession(null)
            onConsumedInitialOpenNewWorkout()
        }
    }

    suspend fun syncMaster() {
        if (relayPool != null && signer != null) {
            val urls = keyManager.relayUrlsForKind30078Publish()
            CardioSync.publishMaster(timerAppContext, relayPool, signer, repository.currentState(), urls)
        }
    }

    suspend fun syncDailyLog(log: CardioDayLog) {
        if (relayPool != null && signer != null) {
            CardioSync.publishDailyLog(
                timerAppContext,
                relayPool,
                signer,
                log,
                keyManager.relayUrlsForKind30078Publish(),
            )
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

    fun saveSession(date: LocalDate, session: CardioSession) {
        scope.launch {
            repository.addSession(date, session)
            launch {
                repository.currentState().logFor(date)?.let { syncDailyLog(it) }
            }
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
            logged = completedWorkoutSummaryLogged,
            onLogWorkout = if (!completedWorkoutSummaryLogged) {
                {
                    scope.launch {
                        repository.addSession(today, summary.session)
                        val routineId = completedWorkoutUnifiedRoutineId
                        val blockId = completedWorkoutUnifiedBlockId
                        if (routineId != null && blockId != null) {
                            unifiedRoutineRepository.attachLoggedBlock(
                                routineId = routineId,
                                blockId = blockId,
                                logDate = today.toString(),
                                entryId = summary.session.id
                            )
                        }
                        // Update UI before relay push: kickDrain can block when relays are down.
                        completedWorkoutSummaryLogged = true
                        if (routineId != null) {
                            completedWorkoutSummary = null
                            completedWorkoutUnifiedRoutineId = null
                            completedWorkoutUnifiedBlockId = null
                            onReturnToUnifiedRun(routineId)
                        } else {
                            snackbarHostState.showSnackbar("Workout logged")
                        }
                        launch {
                            repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                        }
                    }
                }
            } else {
                null
            },
            onDone = {
                completedWorkoutSummary = null
                completedWorkoutSummaryLogged = true
                completedWorkoutUnifiedRoutineId = null
                completedWorkoutUnifiedBlockId = null
            }
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
                    IconButton(onClick = { showCardioStatsSheet = true }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Stats and graphs")
                    }
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.DateRange, contentDescription = "Open log")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
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
                    onStartIntervalWorkout = { m ->
                        startOrQueueCardio(CardioActiveTimerSession.Multi(m))
                    },
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
                            snackbarHostState.showSnackbar("Logged ${routine.name}")
                            launch {
                                repository.currentState().logFor(today)?.let { syncDailyLog(it) }
                            }
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
                    val isCycling = draft.activity.isCyclingActivity()
                    val isErg = draft.activity.isErgMonitorActivity()
                    val ergConnected = isErg &&
                        ergConnectionState == Concept2BleConnectionState.Connected
                    val cscConnected = isCycling &&
                        cyclingConnectionState == CyclingCscBleConnectionState.Connected
                    // Concept2 erg is the richer source (power); fall back to a CSC speed sensor.
                    val cyclingDistanceMeters = when {
                        ergConnected -> ergWorkoutDistanceMeters
                        cscConnected -> cyclingWorkoutDistanceMeters
                        else -> null
                    }
                    val paceOnlyTimer = draft.timerStyle is CardioTimerStyle.CountDownDistance
                    val recordGps =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && locationFineGranted && !paceOnlyTimer
                    val showGpsPermissionHint =
                        draft.eligibleForPhoneGps() && cardioGpsPreferred && !locationFineGranted && !paceOnlyTimer
                    val parentRunCtx = resolveCardioParentRunContext(unifiedState, workoutState)
                    val cardioFinishLabel = parentRunCtx.workoutRun
                        ?.takeIf { parentRunCtx.workoutLaunch != null }
                        ?.let { if (it.isFinalLoggableStep()) "Finish workout" else "Next section" }
                        ?: "Finish"
                    CardioElapsedTimerFullScreen(
                        draft = draft,
                        userPreferences = userPreferences,
                        distanceUnit = distanceUnit,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        finishLabel = cardioFinishLabel,
                        composedWorkoutStartedAtEpochSeconds = parentRunCtx.workoutRun
                            ?.takeIf { parentRunCtx.workoutLaunch != null }
                            ?.startedAtEpochSeconds,
                        preferredLiveDistanceMeters = cyclingDistanceMeters,
                        cyclingSensorConnected = ergConnected || cscConnected,
                        cyclingSpeedKmh = when {
                            ergConnected -> ergSpeedKmh
                            cscConnected -> cyclingSpeedKmh
                            else -> null
                        },
                        cyclingCadenceRpm = when {
                            ergConnected -> ergCadenceRpm
                            cscConnected -> cyclingCadenceRpm
                            else -> null
                        },
                        ergPowerWatts = if (ergConnected) ergPowerWatts else null,
                        gpsRecordingActive = recordGps,
                        showGpsPermissionHint = showGpsPermissionHint,
                        onRequestLocationPermission = {
                            requestCardioLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        },
                        onLeaveTimerUi = {
                            val returnedToParent = parentRunCtx.returnToParentRun(
                                onReturnToUnifiedRun = onReturnToUnifiedRun,
                                onReturnToWorkoutRun = onReturnToWorkoutRun,
                            )
                            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(false)
                            if (!returnedToParent && parentRunCtx.unifiedRoutineId != null) {
                                onBack()
                            }
                        },
                        onBeginWorkout = { cardioLiveWorkoutViewModel.beginTimer() },
                        onStop = { elapsedSeconds, splits ->
                            val gpsPoints = drainCardioGpsIfNeeded(recordGps, timerAppContext)
                            val durationMinutes = max(1, (elapsedSeconds + 59) / 60)
                            val end = nowEpochSeconds()
                            // Take both erg and CSC summaries to clear their buffers; prefer the erg.
                            val ergSummary = if (isErg) concept2Ble.takeWorkoutSummary() else null
                            val cscDistance = if (isCycling) {
                                cyclingCscBle.takeWorkoutSummary()?.distanceMeters
                            } else {
                                null
                            }
                            val ergMetrics = ergSummary?.let { s ->
                                CardioErgMetrics(
                                    avgPowerWatts = s.avgPowerWatts,
                                    maxPowerWatts = s.maxPowerWatts,
                                    avgCadenceRpm = s.avgCadenceRpm,
                                    maxCadenceRpm = s.maxCadenceRpm,
                                )
                            }
                            val raw = draft.toSession(
                                durationMinutes = durationMinutes,
                                endEpoch = end,
                                elapsedSecondsForDistance = elapsedSeconds,
                                gpsPoints = gpsPoints,
                                preferredDistanceMeters = if (isErg) {
                                    ergSummary?.distanceMeters ?: cscDistance
                                } else {
                                    null
                                },
                                splits = splits,
                                ergMetrics = ergMetrics
                            )
                            val completionCtx = resolveCardioParentRunContext(unifiedState, workoutState)
                            // Per-section HR snapshot: always capture for this leg/section. The
                            // continuous whole-workout HR is recorded separately and attached when
                            // the composed run finishes.
                            val hrSummary = heartRateBle.takeWorkoutHeartRateSummary()
                            val withHr = hrSummary?.let { raw.copy(heartRate = it) } ?: raw
                            val session = CardioMetEstimator.applyEstimatedKcal(
                                withHr,
                                state,
                                weightKg
                            )
                            val storedSession = session.withParentRunLink(completionCtx, unifiedState)
                            scope.launch {
                                persistCardioSessionAfterLiveTimer(
                                    session = storedSession,
                                    elapsedSeconds = elapsedSeconds,
                                    ctx = completionCtx,
                                    today = today,
                                    cardioRepository = repository,
                                    unifiedRoutineRepository = unifiedRoutineRepository,
                                    workoutRepository = workoutRepository,
                                    syncDailyLog = { log -> syncDailyLog(log) },
                                    onReturnToUnifiedRun = onReturnToUnifiedRun,
                                    onReturnToWorkoutRun = onReturnToWorkoutRun,
                                    clearCardioSession = { cardioLiveWorkoutViewModel.clearSession() },
                                    showStandaloneSummary = { result ->
                                        completedWorkoutSummary = result
                                        completedWorkoutSummaryLogged = true
                                    },
                                )
                            }
                        },
                        onCancel = {
                            val returnedToParent = parentRunCtx.returnToParentRun(
                                onReturnToUnifiedRun = onReturnToUnifiedRun,
                                onReturnToWorkoutRun = onReturnToWorkoutRun,
                            )
                            drainCardioGpsIfNeeded(recordGps, timerAppContext)
                            heartRateBle.discardWorkoutRecording()
                            cyclingCscBle.discardWorkoutRecording()
                            concept2Ble.discardWorkoutRecording()
                            cardioLiveWorkoutViewModel.clearSession()
                            if (!returnedToParent && parentRunCtx.unifiedRoutineId != null) {
                                onBack()
                            }
                        }
                    )
                }
            }
            is CardioActiveTimerSession.Multi -> {
                if (cardioLiveUiExpanded) {
                    val parentRunCtx = resolveCardioParentRunContext(unifiedState, workoutState)
                    val multiKey = Triple(
                        timer.state.workoutStartEpoch,
                        timer.state.currentLegIndex,
                        timer.state.completedSegments.size,
                    )
                    CardioMultiLegTimerFullScreen(
                        state = timer.state,
                        stateKey = multiKey,
                        userPreferences = userPreferences,
                        dark = therapyRedDark,
                        mid = therapyRedMid,
                        glow = therapyRedGlow,
                        onLeaveWorkoutUi = {
                            val returnedToParent = parentRunCtx.returnToParentRun(
                                onReturnToUnifiedRun = onReturnToUnifiedRun,
                                onReturnToWorkoutRun = onReturnToWorkoutRun,
                            )
                            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(false)
                            if (!returnedToParent && parentRunCtx.unifiedRoutineId != null) {
                                onBack()
                            }
                        },
                        onBeginWorkout = { cardioLiveWorkoutViewModel.beginTimer() },
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
                                    val completionCtx = resolveCardioParentRunContext(unifiedState, workoutState)
                                    val isErg = timer.state.legs.any { it.activity.isErgMonitorActivity() }
                                    val ergSummary = if (isErg) concept2Ble.takeWorkoutSummary() else null
                                    // Per-section HR snapshot for this interval block.
                                    val hrSummary = heartRateBle.takeWorkoutHeartRateSummary()
                                    val withHr = hrSummary?.let { session.copy(heartRate = it) } ?: session
                                    val withErg = ergSummary?.let { summary ->
                                        withHr.copy(
                                            distanceMeters = summary.distanceMeters ?: withHr.distanceMeters,
                                            erg = CardioErgMetrics(
                                                avgPowerWatts = summary.avgPowerWatts,
                                                maxPowerWatts = summary.maxPowerWatts,
                                                avgCadenceRpm = summary.avgCadenceRpm,
                                                maxCadenceRpm = summary.maxCadenceRpm,
                                            ),
                                        )
                                    } ?: withHr
                                    val finalSession = CardioMetEstimator.applyEstimatedKcal(
                                        withErg,
                                        repository.currentState(),
                                        weightKg
                                    )
                                    val storedSession = finalSession.withParentRunLink(completionCtx, unifiedState)
                                    persistCardioSessionAfterLiveTimer(
                                        session = storedSession,
                                        elapsedSeconds = null,
                                        ctx = completionCtx,
                                        today = today,
                                        cardioRepository = repository,
                                        unifiedRoutineRepository = unifiedRoutineRepository,
                                        workoutRepository = workoutRepository,
                                        syncDailyLog = { log -> syncDailyLog(log) },
                                        onReturnToUnifiedRun = onReturnToUnifiedRun,
                                        onReturnToWorkoutRun = onReturnToWorkoutRun,
                                        clearCardioSession = { cardioLiveWorkoutViewModel.clearSession() },
                                        showStandaloneSummary = { result ->
                                            completedWorkoutSummary = result
                                        },
                                    )
                                } else if (next != null) {
                                    cardioLiveWorkoutViewModel.replaceSession(CardioActiveTimerSession.Multi(next))
                                    snackbarHostState.showSnackbar("Leg saved — next leg started")
                                }
                            }
                        },
                        onCancel = {
                            val returnedToParent = parentRunCtx.returnToParentRun(
                                onReturnToUnifiedRun = onReturnToUnifiedRun,
                                onReturnToWorkoutRun = onReturnToWorkoutRun,
                            )
                            heartRateBle.discardWorkoutRecording()
                            cyclingCscBle.discardWorkoutRecording()
                            concept2Ble.discardWorkoutRecording()
                            cardioLiveWorkoutViewModel.clearSession()
                            if (!returnedToParent && parentRunCtx.unifiedRoutineId != null) {
                                onBack()
                            }
                        }
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

    if (showCardioStatsSheet) {
        CardioLogStatsBottomSheet(
            onDismiss = { showCardioStatsSheet = false },
            entries = allTimeCardioLogEntries,
            distanceUnit = distanceUnit,
            periodLabel = sectionLogFilterSummary(SectionLogDateFilter.AllHistory),
            zoneInputs = heartRateZoneInputs,
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
            FormSectionLabel("Activity")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !useCustom,
                    onClick = { useCustom = false },
                    label = { FieldLabel("Built-in") }
                )
                FilterChip(
                    selected = useCustom,
                    onClick = { useCustom = true },
                    label = { FieldLabel("Custom") }
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
                    cardioBuiltinActivitiesForUserSelection().forEach { b ->
                        FilterChip(
                            selected = selectedBuiltin == b,
                            onClick = { selectedBuiltin = b },
                            label = { Text(b.displayName()) }
                        )
                    }
                }
            }

            if (treadmillApplicable) {
                FormSectionLabel("Where")
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
                FormSectionLabel("Belt speed & incline")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = speedUnit == CardioSpeedUnit.MPH,
                        onClick = { speedUnit = CardioSpeedUnit.MPH },
                        label = { FieldLabel("mph") }
                    )
                    FilterChip(
                        selected = speedUnit == CardioSpeedUnit.KMH,
                        onClick = { speedUnit = CardioSpeedUnit.KMH },
                        label = { FieldLabel("km/h") }
                    )
                }
                OutlinedTextField(
                    value = speedStr,
                    onValueChange = { speedStr = it },
                    label = { FieldLabel("Speed") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = inclineStr,
                    onValueChange = { inclineStr = it },
                    label = { FieldLabel("Incline %") },
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
                        label = { FieldLabel("Pack weight (lb)") },
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
                        label = { FieldLabel("Pack weight (lb)") },
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
                            label = { FieldLabel("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { FieldLabel("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = outdoorPaceStr,
                        onValueChange = { outdoorPaceStr = it },
                        label = { FieldLabel("Avg speed (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OutlinedTextField(
                value = durationStr,
                onValueChange = { durationStr = it },
                label = { FieldLabel("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (!logOnlyMode) {
                FormSectionLabel("Live timer")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useCountDownTimer,
                        onClick = { useCountDownTimer = false },
                        label = { FieldLabel("Count up") }
                    )
                    FilterChip(
                        selected = useCountDownTimer,
                        onClick = { useCountDownTimer = true },
                        label = { FieldLabel("Count down") }
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
                    label = { FieldLabel("Routine name (save only)") },
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
                            "Sprinting: pick outdoor or indoor next. Indoor skips belt details and uses a default speed for on-screen estimates (motorized treadmills; manual belts may differ)."
                        } else {
                            "Choose outdoor or indoor before the timer starts."
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
                    ) { Text(CardioModality.INDOOR_TREADMILL.label()) }
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
                        "Enter belt speed and incline for this indoor session.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = speedUnit == CardioSpeedUnit.MPH,
                            onClick = { speedUnit = CardioSpeedUnit.MPH },
                            label = { FieldLabel("mph") }
                        )
                        FilterChip(
                            selected = speedUnit == CardioSpeedUnit.KMH,
                            onClick = { speedUnit = CardioSpeedUnit.KMH },
                            label = { FieldLabel("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = speedStr,
                        onValueChange = { speedStr = it },
                        label = { FieldLabel("Speed") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inclineStr,
                        onValueChange = { inclineStr = it },
                        label = { FieldLabel("Incline %") },
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
                            label = { FieldLabel("Pack weight (lb)") },
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
                            "Sprint indoors always uses a time countdown. A default belt speed is for on-screen estimates (suited to a motorized treadmill; manual or self-powered belts may not match—edit the saved session if needed)."
                        sprintOutdoor ->
                            "Sprint outdoors: count down to a target time, or to a target distance estimated from your average pace."
                        else ->
                            if (modality == CardioModality.INDOOR_TREADMILL) {
                                "Choose whether the main clock counts up or down. Indoors, distance updates from speed × time when you did not enter a fixed distance."
                            } else {
                                "Choose whether the main clock counts up or down."
                            }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isSprint) {
                    FormSectionLabel("Main clock")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !countDown,
                            onClick = { countDown = false },
                            label = { FieldLabel("Count up") }
                        )
                        FilterChip(
                            selected = countDown,
                            onClick = { countDown = true },
                            label = { FieldLabel("Count down") }
                        )
                    }
                }
                if (sprintOutdoor) {
                    FormSectionLabel("Sprint target")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sprintOutdoorByTime,
                            onClick = { sprintOutdoorByTime = true },
                            label = { FieldLabel("Time") }
                        )
                        FilterChip(
                            selected = !sprintOutdoorByTime,
                            onClick = { sprintOutdoorByTime = false },
                            label = { FieldLabel("Distance") }
                        )
                    }
                }
                if (showTimeCountdownField) {
                    OutlinedTextField(
                        value = countDownMinutesStr,
                        onValueChange = { countDownMinutesStr = it },
                        label = { FieldLabel("Countdown (minutes)") },
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
                            label = { FieldLabel("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { FieldLabel("km/h") }
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
                        label = { FieldLabel("Average speed") },
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
                            label = { FieldLabel("mph") }
                        )
                        FilterChip(
                            selected = outdoorPaceUnit == CardioSpeedUnit.KMH,
                            onClick = { outdoorPaceUnit = CardioSpeedUnit.KMH },
                            label = { FieldLabel("km/h") }
                        )
                    }
                    OutlinedTextField(
                        value = outdoorPaceStr,
                        onValueChange = { outdoorPaceStr = it },
                        label = { FieldLabel("Avg speed (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isRuckOutdoor) {
                    OutlinedTextField(
                        value = ruckLoadStr,
                        onValueChange = { ruckLoadStr = it },
                        label = { FieldLabel("Pack weight (lb, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (activity.supportsBikeErgSensorConnect()) {
                    CardioBikeErgConnectInlineSection(
                        activitySupportsErg = true,
                        sessionKey = dialogKey,
                        compact = true,
                        cyclingSensorApplicable = activity.isCyclingActivity(),
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

private data class HiitTimerPending(
    val snap: CardioActivitySnapshot,
    val modality: CardioModality,
    val treadmill: CardioTreadmillParams?,
    /** True when started from the hybrid erg section — copy emphasizes steady vs interval choice. */
    val useHybridMachineCopy: Boolean,
)

@Composable
private fun CardioHiitLoggingChoiceDialog(
    activityLabel: String,
    useHybridMachineCopy: Boolean,
    onDismiss: () -> Unit,
    onSimpleTimer: () -> Unit,
    onIntervalRounds: () -> Unit,
) {
    val body = if (useHybridMachineCopy) {
        "Rowers, bikes, and skiers are often used for a long steady session or for short hard rounds with easy recovery. " +
            "Pick a single timer for endurance-style work, or work / active recovery legs for interval-style sessions " +
            "(you tap stop when each leg is done)."
    } else {
        "Log as one continuous timer, or use work / active recovery legs (e.g. Nordic 4×4: " +
            "several hard rounds with recovery between — you finish each leg when you are done)."
    }
    val intervalLabel = if (useHybridMachineCopy) "Work & recovery rounds" else "Interval rounds"
    val steadyLabel = if (useHybridMachineCopy) "Steady session (one timer)" else "Simple timer"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(activityLabel) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        onIntervalRounds()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(intervalLabel) }
                OutlinedButton(
                    onClick = {
                        onSimpleTimer()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(steadyLabel) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CardioHiitIntervalParamsDialog(
    activity: CardioActivitySnapshot,
    activityLabel: String,
    onDismiss: () -> Unit,
    onStart: (rounds: Int, workMinutes: Int, restMinutes: Int) -> Unit,
) {
    var roundsStr by remember(activityLabel) { mutableStateOf("4") }
    var workStr by remember(activityLabel) { mutableStateOf("4") }
    var restStr by remember(activityLabel) { mutableStateOf("3") }
    val valid = roundsStr.toIntOrNull()?.let { it in 1..99 } == true &&
        workStr.toIntOrNull()?.let { it in 1..180 } == true &&
        restStr.toIntOrNull()?.let { it in 1..180 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Interval rounds — $activityLabel") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Preset: Nordic-style 4×4 uses 4 rounds, 4 min hard, 3 min active recovery between rounds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            roundsStr = "4"
                            workStr = "4"
                            restStr = "3"
                        }
                    ) { Text("4×4 (3 min recovery)") }
                }
                OutlinedTextField(
                    value = roundsStr,
                    onValueChange = { roundsStr = it },
                    label = { FieldLabel("Work rounds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = workStr,
                    onValueChange = { workStr = it },
                    label = { FieldLabel("Minutes per work round") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = restStr,
                    onValueChange = { restStr = it },
                    label = { FieldLabel("Minutes active recovery between rounds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (activity.supportsBikeErgSensorConnect()) {
                    Spacer(Modifier.height(4.dp))
                    CardioBikeErgConnectInlineSection(
                        activitySupportsErg = true,
                        sessionKey = activityLabel,
                        compact = true,
                        cyclingSensorApplicable = activity.isCyclingActivity(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val r = roundsStr.toIntOrNull() ?: return@TextButton
                    val w = workStr.toIntOrNull() ?: return@TextButton
                    val rest = restStr.toIntOrNull() ?: return@TextButton
                    onStart(r, w, rest)
                    onDismiss()
                },
                enabled = valid
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
    onStartIntervalWorkout: (CardioMultiLegTimerState) -> Unit,
) {
    var pendingModalityForStart by remember { mutableStateOf<CardioActivitySnapshot?>(null) }
    var pendingTimerOpts by remember { mutableStateOf<PendingCardioTimerOpts?>(null) }
    var pendingHiitChoice by remember { mutableStateOf<HiitTimerPending?>(null) }
    var pendingHiitParams by remember { mutableStateOf<HiitTimerPending?>(null) }
    val steadyBuiltins = remember(state) {
        cardioBuiltinSteadySectionOrder.map { b -> b to state.resolveSnapshot(b, null) }
    }
    val hybridBuiltins = remember(state) {
        cardioBuiltinHybridSectionOrder.map { b -> b to state.resolveSnapshot(b, null) }
    }
    val hiitBuiltins = remember(state) {
        cardioBuiltinHiitSectionOrder.map { b -> b to state.resolveSnapshot(b, null) }
    }
    val customs = state.customActivityTypes

    pendingModalityForStart?.let { snap ->
        StartCardioModalityForTimerDialog(
            activity = snap,
            distanceUnit = distanceUnit,
            onDismiss = { pendingModalityForStart = null },
            onModalityChosen = { mod, tm ->
                pendingModalityForStart = null
                if (snap.builtin?.offersHiitIntervalTemplate() == true) {
                    val hybrid = snap.builtin?.isHybridMachineSection() == true
                    pendingHiitChoice = HiitTimerPending(snap, mod, tm, hybrid)
                } else {
                    pendingTimerOpts = PendingCardioTimerOpts(snap, mod, tm)
                }
            }
        )
    }

    pendingHiitChoice?.let { p ->
        CardioHiitLoggingChoiceDialog(
            activityLabel = p.snap.displayLabel,
            useHybridMachineCopy = p.useHybridMachineCopy,
            onDismiss = { pendingHiitChoice = null },
            onSimpleTimer = {
                pendingTimerOpts = PendingCardioTimerOpts(p.snap, p.modality, p.treadmill)
            },
            onIntervalRounds = {
                pendingHiitParams = p
            }
        )
    }

    pendingHiitParams?.let { p ->
        CardioHiitIntervalParamsDialog(
            activity = p.snap,
            activityLabel = p.snap.displayLabel,
            onDismiss = { pendingHiitParams = null },
            onStart = { rounds, workMin, restMin ->
                val name = "${p.snap.displayLabel} ${rounds}×${workMin}m / ${restMin}m recovery"
                onStartIntervalWorkout(
                    CardioMultiLegTimerState.fromIntervalTemplate(
                        workActivity = p.snap,
                        workModality = p.modality,
                        workTreadmill = p.treadmill,
                        routineName = name,
                        rounds = rounds,
                        workMinutes = workMin,
                        restMinutes = restMin,
                    )
                )
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

    fun startWorkoutFromActivity(snap: CardioActivitySnapshot) {
        if (snap.builtin?.supportsTreadmillModality() == true) {
            pendingModalityForStart = snap
        } else if (snap.builtin?.offersHiitIntervalTemplate() == true) {
            val hybrid = snap.builtin?.isHybridMachineSection() == true
            pendingHiitChoice = HiitTimerPending(snap, CardioModality.OUTDOOR, null, hybrid)
        } else {
            pendingTimerOpts = PendingCardioTimerOpts(
                snap,
                CardioModality.OUTDOOR,
                null
            )
        }
    }

    @Composable
    fun BuiltinActivityRow(snap: CardioActivitySnapshot) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { startWorkoutFromActivity(snap) }
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Distance & steady cardio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(steadyBuiltins, key = { it.second.displayLabel }) { (_, snap) ->
                BuiltinActivityRow(snap)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Erg-style machines",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Rower, bikes, SkiErg — good for steady endurance or hard / easy intervals. Choose how you want to log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(hybridBuiltins, key = { it.second.displayLabel }) { (_, snap) ->
                BuiltinActivityRow(snap)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Intervals & circuits",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Sprints, ropes, and bodyweight moves — optional work / recovery legs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(hiitBuiltins, key = { it.second.displayLabel }) { (_, snap) ->
                BuiltinActivityRow(snap)
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
                                        startWorkoutFromActivity(state.resolveSnapshot(null, t.id))
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    t.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
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
    var dateFilter by remember(initialSelectedDate) {
        mutableStateOf<SectionLogDateFilter>(
            if (initialSelectedDate != null) SectionLogDateFilter.SingleDay(initialSelectedDate)
            else SectionLogDateFilter.AllHistory
        )
    }
    var showCal by remember(openCalendarInitially) {
        mutableStateOf(openCalendarInitially)
    }
    var showManualLog by remember { mutableStateOf(false) }
    var showCardioLogStatsSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DatedCardioSession?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val logAppContext = LocalContext.current.applicationContext
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val weightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(
        initial = com.erv.app.hr.HeartRateZoneInputs(),
    )
    val datedEntries = remember(state, dateFilter) {
        state.datedCardioSessionsForSectionLog(dateFilter)
    }
    val showLogDateOnCards = dateFilter !is SectionLogDateFilter.SingleDay
    val datesWithActivity = remember(state) { datesWithCardioActivity(state) }
    val darkTheme = isSystemInDarkTheme()
    val therapyRedMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val keyManager = LocalKeyManager.current
    val dayLogRelayEntries = remember(state) { CardioSync.dayLogOutboxEntries(state) }

    suspend fun syncDailyLogForDate(date: LocalDate) {
        if (relayPool != null && signer != null) {
            repository.currentState().logFor(date)?.let { log ->
                CardioSync.publishDailyLog(
                    logAppContext,
                    relayPool,
                    signer,
                    log,
                    keyManager.relayUrlsForKind30078Publish(),
                )
            }
        }
    }

    pendingDelete?.let { dated ->
        val toRemove = dated.session
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
                        val logDate = dated.logDate
                        pendingDelete = null
                        scope.launch {
                            repository.deleteSession(logDate, id)
                            snackbarHostState.showSnackbar("Workout removed")
                            launch { syncDailyLogForDate(logDate) }
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
                actions = {
                    SectionLogRelayResyncIconButton(
                        appContext = logAppContext,
                        relayPool = relayPool,
                        signer = signer,
                        dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                        dayLogEntries = dayLogRelayEntries,
                        snackbarHostState = snackbarHostState,
                        scope = scope,
                    )
                    IconButton(onClick = { showCardioLogStatsSheet = true }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Stats and graphs")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
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
            SectionLogFilterBar(
                filter = dateFilter,
                onOpenCalendar = { showCal = true },
                onClearFilter = { dateFilter = SectionLogDateFilter.AllHistory }
            )
            if (datedEntries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (dateFilter) {
                                SectionLogDateFilter.AllHistory -> "No cardio logged yet."
                                is SectionLogDateFilter.SingleDay -> "No cardio logged for this date."
                                is SectionLogDateFilter.DateRange -> "No cardio logged in this date range."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap Add workout to log a session for today.",
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
                            "Newest first. Tap an entry for full details.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    items(datedEntries, key = { "${it.logDate}-${it.session.id}" }) { dated ->
                        val s = dated.session
                        ElevatedCard(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSessionDetail(dated.logDate, s.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    if (showLogDateOnCards) {
                                        Text(
                                            dated.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
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
                                IconButton(onClick = { pendingDelete = dated }) {
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
        SectionLogCalendarSheet(
            filter = dateFilter,
            onDismiss = { showCal = false },
            datesWithActivity = datesWithActivity,
            onApplyFilter = { dateFilter = it }
        )
    }

    if (showCardioLogStatsSheet) {
        CardioLogStatsBottomSheet(
            onDismiss = { showCardioLogStatsSheet = false },
            entries = datedEntries,
            distanceUnit = distanceUnit,
            periodLabel = sectionLogFilterSummary(dateFilter),
            zoneInputs = heartRateZoneInputs,
        )
    }

    if (showManualLog) {
        WorkoutBuilderBottomSheet(
            mode = WorkoutBuilderMode.NewSession(null),
            state = state,
            weightKg = weightKg,
            distanceUnit = distanceUnit,
            targetLogDate = LocalDate.now(),
            logOnlyMode = true,
            onDismiss = { showManualLog = false },
            onLog = { date, session ->
                scope.launch {
                    repository.addSession(date, session)
                    showManualLog = false
                    snackbarHostState.showSnackbar("Session logged")
                    launch {
                        if (relayPool != null && signer != null) {
                            repository.currentState().logFor(date)?.let { log ->
                                CardioSync.publishDailyLog(
                                    logAppContext,
                                    relayPool,
                                    signer,
                                    log,
                                    keyManager.relayUrlsForKind30078Publish(),
                                )
                            }
                        }
                    }
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
private fun CardioLatestSplitCard(
    split: CardioWorkoutSplit,
    distanceUnit: CardioDistanceUnit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.cardio_split_latest_title),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                text = stringResource(R.string.cardio_split_label, split.orderIndex + 1),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = cardioSplitSummaryLine(split, distanceUnit),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f)
            )
            Text(
                text = stringResource(
                    R.string.cardio_split_elapsed_summary,
                    formatCardioElapsedClock(split.elapsedSeconds)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun cardioSplitSummaryLine(split: CardioWorkoutSplit, distanceUnit: CardioDistanceUnit): String =
    if (split.segmentDistanceMeters != null) {
        stringResource(
            R.string.cardio_split_summary,
            formatCardioElapsedClock(split.segmentElapsedSeconds),
            formatCardioDistanceFromMeters(split.segmentDistanceMeters, distanceUnit),
            split.segmentPace(distanceUnit) ?: "—"
        )
    } else {
        stringResource(
            R.string.cardio_split_summary_time_only,
            formatCardioElapsedClock(split.segmentElapsedSeconds)
        )
    }

@Composable
private fun CardioSplitHistoryDialog(
    splits: List<CardioWorkoutSplit>,
    distanceUnit: CardioDistanceUnit,
    splitMode: CardioLiveSplitMode,
    autoSplitQuarterMiles: Int,
    gpsRecordingActive: Boolean,
    waitingForGps: Boolean,
    onModeSelected: (CardioLiveSplitMode) -> Unit,
    onAutoSplitDistanceSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cardio_split_history_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.cardio_split_settings_title),
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardioLiveSplitMode.entries.forEach { mode ->
                        FilterChip(
                            selected = splitMode == mode,
                            onClick = { onModeSelected(mode) },
                            label = { Text(stringResource(cardioSplitModeLabel(mode))) }
                        )
                    }
                }
                when (splitMode) {
                    CardioLiveSplitMode.OFF -> {
                        Text(
                            text = stringResource(R.string.cardio_split_mode_off_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CardioLiveSplitMode.MANUAL -> {
                        Text(
                            text = stringResource(R.string.cardio_split_mode_manual_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CardioLiveSplitMode.AUTO -> {
                        Text(
                            text = if (gpsRecordingActive) {
                                stringResource(
                                    R.string.cardio_split_auto_distance_helper,
                                    formatCardioSplitDistanceMiles(autoSplitQuarterMiles)
                                )
                            } else {
                                stringResource(R.string.cardio_split_auto_requires_gps)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (gpsRecordingActive) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                cardioAutoSplitQuarterMileOptions.forEach { option ->
                                    FilterChip(
                                        selected = autoSplitQuarterMiles == option,
                                        onClick = { onAutoSplitDistanceSelected(option) },
                                        label = { Text(formatCardioSplitDistanceMiles(option)) }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                if (splits.isEmpty()) {
                    Text(
                        text = stringResource(
                            when {
                                splitMode == CardioLiveSplitMode.OFF -> R.string.cardio_split_history_off
                                splitMode == CardioLiveSplitMode.AUTO && !gpsRecordingActive ->
                                    R.string.cardio_split_auto_requires_gps
                                waitingForGps -> R.string.cardio_split_waiting_for_gps
                                else -> R.string.cardio_split_history_empty
                            }
                        )
                    )
                } else {
                    splits.sortedBy { it.orderIndex }.forEach { split ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.cardio_split_label, split.orderIndex + 1),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = cardioSplitSummaryLine(split, distanceUnit),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(
                                    R.string.cardio_split_elapsed_summary,
                                    formatCardioElapsedClock(split.elapsedSeconds)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.hr_dialog_close))
            }
        }
    )
}

@Composable
private fun CardioStartWorkoutButton(
    onClick: () -> Unit,
    accentBackground: Color,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(0.85f)
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = accentBackground,
        ),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text("Start Workout", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun CardioElapsedTimerFullScreen(
    draft: CardioTimerSessionDraft,
    userPreferences: UserPreferences,
    distanceUnit: CardioDistanceUnit,
    dark: Color,
    mid: Color,
    glow: Color,
    preferredLiveDistanceMeters: Double? = null,
    cyclingSensorConnected: Boolean = false,
    cyclingSpeedKmh: Double? = null,
    cyclingCadenceRpm: Int? = null,
    ergPowerWatts: Int? = null,
    gpsRecordingActive: Boolean = false,
    showGpsPermissionHint: Boolean = false,
    onRequestLocationPermission: () -> Unit = {},
    /**
     * Start time of the overall composed workout, when this cardio leg is one section of a
     * larger workout. Used to show the total workout clock alongside the section elapsed time.
     */
    composedWorkoutStartedAtEpochSeconds: Long? = null,
    /** Top-bar/stop action label; section-aware when run from a composed workout. */
    finishLabel: String = "Finish",
    /** Back arrow: leave full-screen UI; timer and optional GPS keep running (like weight training). */
    onLeaveTimerUi: (() -> Unit)? = null,
    onBeginWorkout: () -> Unit = {},
    onStop: (elapsedSeconds: Int, splits: List<CardioWorkoutSplit>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val cyclingCscBle = LocalCyclingCsc.current
    val concept2Ble = LocalConcept2Pm.current
    val heartRateBle = LocalHeartRateBle.current
    val isCyclingWorkout = draft.activity.isCyclingActivity()
    // Erg = Concept2-pairable (BikeErg / RowErg / SkiErg); stroke ergs use spm + /500m pace.
    val isErgWorkout = draft.activity.isErgMonitorActivity()
    val isStrokeErgWorkout = draft.activity.isStrokeErgActivity()
    val heartRateBannerExpanded by userPreferences.heartRateBannerExpanded.collectAsState(initial = true)
    val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(
        initial = com.erv.app.hr.HeartRateZoneInputs(),
    )
    val splitMode by userPreferences.cardioLiveSplitMode.collectAsState(initial = CardioLiveSplitMode.OFF)
    val autoSplitQuarterMiles by userPreferences.cardioLiveAutoSplitQuarterMiles.collectAsState(initial = 4)
    val cyclingBleConnectionState by cyclingCscBle.connectionState.collectAsState()
    val savedCyclingDevices by cyclingCscBle.savedDevices.collectAsState()
    val preferredCyclingAddress by cyclingCscBle.preferredDeviceAddress.collectAsState()
    val activeCyclingAddress by cyclingCscBle.activeDeviceAddress.collectAsState()
    val cyclingConnectedLabel by cyclingCscBle.connectedLabel.collectAsState()
    val cyclingBleStatusMessage by cyclingCscBle.statusMessage.collectAsState()
    val cyclingScanRows by cyclingCscBle.scanRows.collectAsState()
    val ergBleConnectionState by concept2Ble.connectionState.collectAsState()
    val savedErgDevices by concept2Ble.savedDevices.collectAsState()
    val preferredErgAddress by concept2Ble.preferredDeviceAddress.collectAsState()
    val activeErgAddress by concept2Ble.activeDeviceAddress.collectAsState()
    val ergConnectedLabel by concept2Ble.connectedLabel.collectAsState()
    val ergBleStatusMessage by concept2Ble.statusMessage.collectAsState()
    val ergScanRows by concept2Ble.scanRows.collectAsState()
    val scope = rememberCoroutineScope()
    val timeCountdownCap = (draft.timerStyle as? CardioTimerStyle.CountDown)?.totalSeconds
    val distanceCountdownTarget = (draft.timerStyle as? CardioTimerStyle.CountDownDistance)?.targetMeters
    val tickKey = draft.startEpoch
    val bikeErgHandle = rememberCardioBikeErgSensorConnect(
        enabled = isErgWorkout,
        sessionKey = tickKey,
        cyclingSensorApplicable = isCyclingWorkout,
    )
    val awaitingStart = draft.isPendingStart()
    var showMediaSheet by remember(tickKey) { mutableStateOf(false) }
    var showCyclingSensorDialog by remember(tickKey) { mutableStateOf(false) }
    var showCyclingScanDialog by remember(tickKey) { mutableStateOf(false) }
    var showErgSensorDialog by remember(tickKey) { mutableStateOf(false) }
    var showErgScanDialog by remember(tickKey) { mutableStateOf(false) }
    var showSplitHistoryDialog by remember(tickKey) { mutableStateOf(false) }
    var running by remember(tickKey) { mutableStateOf(!awaitingStart) }
    var finished by remember(tickKey) { mutableStateOf(false) }
    var tick by remember(tickKey) { mutableIntStateOf(0) }
    var workoutSplits by remember(tickKey) { mutableStateOf(emptyList<CardioWorkoutSplit>()) }
    var pendingCyclingConnectDevice by remember { mutableStateOf<SavedBluetoothDevice?>(null) }
    var pendingCyclingScan by remember { mutableStateOf(false) }
    var pendingErgConnectDevice by remember { mutableStateOf<SavedBluetoothDevice?>(null) }
    var pendingErgScan by remember { mutableStateOf(false) }

    val requestCyclingBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val pendingConnect = pendingCyclingConnectDevice
        val pendingScan = pendingCyclingScan
        pendingCyclingConnectDevice = null
        pendingCyclingScan = false
        when {
            pendingConnect != null && cyclingCscBle.hasConnectPermission() ->
                cyclingCscBle.connectToSavedDevice(pendingConnect)
            pendingScan && cyclingCscBle.hasScanPermission() && cyclingCscBle.hasConnectPermission() -> {
                showCyclingSensorDialog = false
                showCyclingScanDialog = true
                cyclingCscBle.startScanForSensors()
            }
        }
    }
    val requestHeartRateBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun openCyclingSensorScan() {
        pendingCyclingConnectDevice = null
        pendingCyclingScan = true
        if (!cyclingCscBle.hasScanPermission() || !cyclingCscBle.hasConnectPermission()) {
            requestCyclingBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            showCyclingSensorDialog = false
            showCyclingScanDialog = true
            cyclingCscBle.startScanForSensors()
        }
    }

    fun connectSavedCyclingSensor(device: SavedBluetoothDevice) {
        pendingCyclingScan = false
        if (!cyclingCscBle.hasConnectPermission()) {
            pendingCyclingConnectDevice = device
            requestCyclingBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            pendingCyclingConnectDevice = null
            showCyclingSensorDialog = false
            cyclingCscBle.connectToSavedDevice(device)
        }
    }

    val requestErgBlePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val pendingConnect = pendingErgConnectDevice
        val pendingScan = pendingErgScan
        pendingErgConnectDevice = null
        pendingErgScan = false
        when {
            pendingConnect != null && concept2Ble.hasConnectPermission() ->
                concept2Ble.connectToSavedDevice(pendingConnect)
            pendingScan && concept2Ble.hasScanPermission() && concept2Ble.hasConnectPermission() -> {
                showErgSensorDialog = false
                showErgScanDialog = true
                concept2Ble.startScanForSensors()
            }
        }
    }

    fun openErgSensorScan() {
        pendingErgConnectDevice = null
        pendingErgScan = true
        if (!concept2Ble.hasScanPermission() || !concept2Ble.hasConnectPermission()) {
            requestErgBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            showErgSensorDialog = false
            showErgScanDialog = true
            concept2Ble.startScanForSensors()
        }
    }

    fun connectSavedErg(device: SavedBluetoothDevice) {
        pendingErgScan = false
        if (!concept2Ble.hasConnectPermission()) {
            pendingErgConnectDevice = device
            requestErgBlePermissions.launch(requiredBlePermissionsForHeartRate())
        } else {
            pendingErgConnectDevice = null
            showErgSensorDialog = false
            concept2Ble.connectToSavedDevice(device)
        }
    }

    val workoutElapsedSeconds = remember(tick, draft.startEpoch, awaitingStart) {
        if (awaitingStart) 0 else (nowEpochSeconds() - draft.startEpoch).coerceAtLeast(0).toInt()
    }

    fun complete(elapsed: Int) {
        if (finished) return
        finished = true
        running = false
        onStop(elapsed, workoutSplits)
    }

    LaunchedEffect(gpsRecordingActive, tickKey, running) {
        if (gpsRecordingActive && running) {
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
                val covered = draft.liveDistanceMeters(elapsed, preferredLiveDistanceMeters) ?: 0.0
                if (covered >= dTarget) {
                    complete(elapsed)
                    break
                }
            }
        }
    }

    val coveredM = draft.liveDistanceMeters(workoutElapsedSeconds, preferredLiveDistanceMeters) ?: 0.0
    val remainingDistance =
        distanceCountdownTarget?.let { max(0.0, it - coveredM) }
    val mainClockSeconds =
        if (timeCountdownCap != null) max(0, timeCountdownCap - workoutElapsedSeconds)
        else workoutElapsedSeconds
    val distM = draft.liveDistanceMeters(workoutElapsedSeconds, preferredLiveDistanceMeters)
    val latestSplit = workoutSplits.lastOrNull()
    val autoSplitMeters = remember(autoSplitQuarterMiles) { (autoSplitQuarterMiles / 4.0) * 1609.344 }
    val splitControlsEnabled = when (splitMode) {
        CardioLiveSplitMode.OFF -> false
        CardioLiveSplitMode.MANUAL -> true
        CardioLiveSplitMode.AUTO -> gpsRecordingActive
    }
    val splitWaitingForGps = splitMode == CardioLiveSplitMode.AUTO && gpsRecordingActive &&
        (liveGpsMeters == null || liveGpsMeters <= 1.0)

    fun recordSplit(totalMetersInput: Double? = null): Boolean {
        val previousSplit = workoutSplits.lastOrNull()
        val elapsedAtLastSplit = previousSplit?.elapsedSeconds ?: 0
        val segmentElapsed = workoutElapsedSeconds - elapsedAtLastSplit
        if (segmentElapsed <= 0) return false
        val totalMeters = when {
            totalMetersInput != null -> totalMetersInput
            gpsRecordingActive -> liveGpsMeters ?: return false
            else -> null
        }
        if (totalMeters != null && (!totalMeters.isFinite() || totalMeters <= 1.0)) return false
        val distanceAtLastSplit = previousSplit?.totalDistanceMeters ?: 0.0
        val segmentDistance = totalMeters?.let { (it - distanceAtLastSplit).coerceAtLeast(0.0) }
        if (gpsRecordingActive && (segmentDistance == null || segmentDistance <= 0.0)) return false
        workoutSplits = workoutSplits + CardioWorkoutSplit(
            orderIndex = workoutSplits.size,
            elapsedSeconds = workoutElapsedSeconds,
            segmentElapsedSeconds = segmentElapsed,
            totalDistanceMeters = totalMeters,
            segmentDistanceMeters = segmentDistance
        )
        return true
    }

    val nextAutoSplitMeters = remember(splitMode, autoSplitMeters, workoutSplits) {
        if (splitMode != CardioLiveSplitMode.AUTO) {
            null
        } else {
            (workoutSplits.lastOrNull()?.totalDistanceMeters ?: 0.0) + autoSplitMeters
        }
    }

    LaunchedEffect(
        gpsRecordingActive,
        splitMode,
        nextAutoSplitMeters,
        liveGpsMeters,
        workoutElapsedSeconds,
        running,
        finished
    ) {
        if (!gpsRecordingActive || splitMode != CardioLiveSplitMode.AUTO || !running || finished) return@LaunchedEffect
        val threshold = nextAutoSplitMeters ?: return@LaunchedEffect
        val totalMeters = liveGpsMeters ?: return@LaunchedEffect
        if (totalMeters >= threshold) {
            recordSplit(totalMetersInput = threshold)
        }
    }

    LaunchedEffect(tickKey, isCyclingWorkout) {
        if (isCyclingWorkout) {
            concept2Ble.tryPreferredDeviceReconnectOnce()
            cyclingCscBle.tryPreferredDeviceReconnectOnce()
        }
    }
    LaunchedEffect(tickKey, heartRateBannerExpanded) {
        if (heartRateBannerExpanded) {
            heartRateBle.tryPreferredDeviceReconnectOnce()
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
                            if (awaitingStart) "Ready to start" else "Session in progress",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val showHeartRateBanner = !heartRateBannerExpanded
                                    userPreferences.setHeartRateBannerExpanded(showHeartRateBanner)
                                    if (showHeartRateBanner) {
                                        heartRateBle.tryPreferredDeviceReconnectOnce()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (heartRateBannerExpanded) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Heart rate monitor",
                                tint = if (heartRateBannerExpanded) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.88f)
                            )
                        }
                        if (isErgWorkout) {
                            with(bikeErgHandle) {
                                CardioBikeErgSensorToolbarActions(handle = this, lightOnDark = true)
                            }
                        }
                        IconButton(onClick = { showMediaSheet = !showMediaSheet }) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = stringResource(R.string.media_control_cd_music),
                                tint = Color.White.copy(alpha = if (showMediaSheet) 1f else 0.88f)
                            )
                        }
                        IconButton(onClick = { showSplitHistoryDialog = true }) {
                            Icon(
                                Icons.Filled.AccessTime,
                                contentDescription = stringResource(R.string.cardio_split_stopwatch_cd),
                                tint = Color.White.copy(
                                    alpha = if (
                                        showSplitHistoryDialog ||
                                        workoutSplits.isNotEmpty() ||
                                        splitMode != CardioLiveSplitMode.OFF
                                    ) 1f else 0.88f
                                )
                            )
                        }
                    }
                } else {
                    Text(
                        if (awaitingStart) "Ready to start" else "Session in progress",
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
                if (heartRateBannerExpanded) {
                    Spacer(Modifier.height(8.dp))
                    HeartRateTopBar(
                        viewModel = heartRateBle,
                        onRequestBlePermissions = {
                            requestHeartRateBlePermissions.launch(requiredBlePermissionsForHeartRate())
                        },
                        zoneInputs = heartRateZoneInputs,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (awaitingStart) {
                    Text(
                        draft.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        draft.modality.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    if (isErgWorkout) {
                        Spacer(Modifier.height(16.dp))
                        CardioBikeErgSensorPreStartPanel(
                            handle = bikeErgHandle,
                            lightOnDark = true,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    CardioStartWorkoutButton(
                        onClick = onBeginWorkout,
                        accentBackground = dark,
                    )
                } else {
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
                if (composedWorkoutStartedAtEpochSeconds != null) {
                    val totalWorkoutSeconds = remember(tick, composedWorkoutStartedAtEpochSeconds) {
                        (nowEpochSeconds() - composedWorkoutStartedAtEpochSeconds).coerceAtLeast(0).toInt()
                    }
                    val totalMins = totalWorkoutSeconds / 60
                    val totalSecs = totalWorkoutSeconds % 60
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Total workout %d:%02d".format(totalMins, totalSecs),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
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
                if (isErgWorkout && cyclingSensorConnected) {
                    CardioLiveErgSensorStats(
                        distanceUnit = distanceUnit,
                        speedKmh = cyclingSpeedKmh,
                        cadenceRpm = cyclingCadenceRpm,
                        distanceMeters = preferredLiveDistanceMeters,
                        powerWatts = ergPowerWatts,
                        strokeBased = isStrokeErgWorkout,
                    )
                }
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
                                isErgWorkout && cyclingSensorConnected ->
                                    if (isStrokeErgWorkout) "Distance from Concept2 erg" else "Distance from cycling speed sensor"
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
                if (splitMode != CardioLiveSplitMode.OFF || latestSplit != null) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 88.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        latestSplit?.let { split ->
                            CardioLatestSplitCard(
                                split = split,
                                distanceUnit = distanceUnit
                            )
                        }
                    }
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
                            compact = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                if (splitMode == CardioLiveSplitMode.MANUAL) {
                    OutlinedButton(
                        onClick = { recordSplit() },
                        enabled = running && !finished && (!gpsRecordingActive || !splitWaitingForGps),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cardio_split_record))
                    }
                }
                if (splitControlsEnabled) {
                    Text(
                        text = when (splitMode) {
                            CardioLiveSplitMode.AUTO -> stringResource(
                                R.string.cardio_split_auto_status,
                                formatCardioSplitDistanceMiles(autoSplitQuarterMiles)
                            )
                            CardioLiveSplitMode.MANUAL -> stringResource(
                                if (gpsRecordingActive) {
                                    R.string.cardio_split_manual_status
                                } else {
                                    R.string.cardio_split_manual_status_time_only
                                }
                            )
                            CardioLiveSplitMode.OFF -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                    if (splitWaitingForGps) {
                        Text(
                            text = stringResource(R.string.cardio_split_waiting_for_gps),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                }
                if (!awaitingStart) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (running && !finished) {
                                    complete(workoutElapsedSeconds)
                                }
                            },
                            enabled = running && !finished,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(finishLabel)
                        }
                        OutlinedButton(
                            onClick = {
                                running = false
                                onCancel()
                            },
                            enabled = running && !finished,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    if (showSplitHistoryDialog) {
        CardioSplitHistoryDialog(
            splits = workoutSplits,
            distanceUnit = distanceUnit,
            splitMode = splitMode,
            autoSplitQuarterMiles = autoSplitQuarterMiles,
            gpsRecordingActive = gpsRecordingActive,
            waitingForGps = splitWaitingForGps,
            onModeSelected = { mode ->
                scope.launch { userPreferences.setCardioLiveSplitMode(mode) }
            },
            onAutoSplitDistanceSelected = { quarterMiles ->
                scope.launch { userPreferences.setCardioLiveAutoSplitQuarterMiles(quarterMiles) }
            },
            onDismiss = { showSplitHistoryDialog = false }
        )
    }

    if (showCyclingSensorDialog && isCyclingWorkout) {
        CyclingSensorSessionDialog(
            connectionState = cyclingBleConnectionState,
            connectedLabel = cyclingConnectedLabel,
            statusMessage = cyclingBleStatusMessage,
            savedDevices = savedCyclingDevices,
            preferredAddress = preferredCyclingAddress,
            activeAddress = activeCyclingAddress,
            onDismiss = { showCyclingSensorDialog = false },
            onConnectSavedDevice = { connectSavedCyclingSensor(it) },
            onDisconnect = {
                showCyclingSensorDialog = false
                cyclingCscBle.disconnectUser()
            },
            onScan = { openCyclingSensorScan() }
        )
    }

    if (showCyclingScanDialog && isCyclingWorkout) {
        CyclingSensorScanDialog(
            scanRows = cyclingScanRows,
            onDismiss = {
                showCyclingScanDialog = false
                cyclingCscBle.stopScanInternal()
            },
            onSelect = { row ->
                showCyclingScanDialog = false
                cyclingCscBle.connectToScannedRow(row)
            }
        )
    }

    if (showErgSensorDialog && isCyclingWorkout) {
        Concept2SensorSessionDialog(
            connectionState = ergBleConnectionState,
            connectedLabel = ergConnectedLabel,
            statusMessage = ergBleStatusMessage,
            savedDevices = savedErgDevices,
            preferredAddress = preferredErgAddress,
            activeAddress = activeErgAddress,
            onDismiss = { showErgSensorDialog = false },
            onConnectSavedDevice = { connectSavedErg(it) },
            onDisconnect = {
                showErgSensorDialog = false
                concept2Ble.disconnectUser()
            },
            onScan = { openErgSensorScan() }
        )
    }

    if (showErgScanDialog && isCyclingWorkout) {
        Concept2SensorScanDialog(
            scanRows = ergScanRows,
            onDismiss = {
                showErgScanDialog = false
                concept2Ble.stopScanInternal()
            },
            onSelect = { row ->
                showErgScanDialog = false
                concept2Ble.connectToScannedRow(row)
            }
        )
    }
}

@Composable
private fun CyclingSensorSessionDialog(
    connectionState: CyclingCscBleConnectionState,
    connectedLabel: String?,
    statusMessage: String?,
    savedDevices: List<SavedBluetoothDevice>,
    preferredAddress: String?,
    activeAddress: String?,
    onDismiss: () -> Unit,
    onConnectSavedDevice: (SavedBluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cycling sensor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (connectionState) {
                    CyclingCscBleConnectionState.Connected ->
                        Text(
                            "Connected to ${connectedLabel ?: "cycling sensor"}.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    CyclingCscBleConnectionState.Connecting ->
                        Text(
                            "Connecting to ${connectedLabel ?: "cycling sensor"}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    CyclingCscBleConnectionState.Scanning ->
                        Text(
                            "Scanning for cycling sensors...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    CyclingCscBleConnectionState.Idle,
                    CyclingCscBleConnectionState.Error ->
                        Text(
                            "Connect a saved CSC sensor or scan for a new one without leaving this workout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
                statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (savedDevices.isEmpty()) {
                    Text(
                        "No saved cycling sensors yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FormSectionLabel("Saved sensors")
                    savedDevices.forEachIndexed { index, device ->
                        TextButton(
                            onClick = { onConnectSavedDevice(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(device.displayName())
                                val labels = buildList {
                                    if (preferredAddress == device.address) add("Preferred")
                                    if (activeAddress == device.address &&
                                        connectionState == CyclingCscBleConnectionState.Connected
                                    ) {
                                        add("Connected")
                                    }
                                }
                                if (labels.isNotEmpty()) {
                                    Text(
                                        labels.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (index < savedDevices.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onScan) {
                Text("Scan")
            }
        },
        dismissButton = {
            Row {
                if (connectionState == CyclingCscBleConnectionState.Connected) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun CyclingSensorScanDialog(
    scanRows: List<CyclingCscScanRow>,
    onDismiss: () -> Unit,
    onSelect: (CyclingCscScanRow) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a cycling sensor") },
        text = {
            if (scanRows.isEmpty()) {
                Text("No CSC sensors found yet. Wake the sensor and spin the wheel or crank to advertise.")
            } else {
                Column {
                    scanRows.forEachIndexed { index, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(row) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = row.name?.takeIf { it.isNotBlank() } ?: "Cycling sensor",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = row.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < scanRows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun Concept2SensorSessionDialog(
    connectionState: Concept2BleConnectionState,
    connectedLabel: String?,
    statusMessage: String?,
    savedDevices: List<SavedBluetoothDevice>,
    preferredAddress: String?,
    activeAddress: String?,
    onDismiss: () -> Unit,
    onConnectSavedDevice: (SavedBluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onScan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Concept2 erg") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (connectionState) {
                    Concept2BleConnectionState.Connected ->
                        Text(
                            "Connected to ${connectedLabel ?: "Concept2 monitor"}.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    Concept2BleConnectionState.Connecting ->
                        Text(
                            "Connecting to ${connectedLabel ?: "Concept2 monitor"}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    Concept2BleConnectionState.Scanning ->
                        Text(
                            "Scanning for Concept2 monitors...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    Concept2BleConnectionState.Idle,
                    Concept2BleConnectionState.Error ->
                        Text(
                            "Connect a saved Concept2 PM (BikeErg / RowErg / SkiErg) or scan for a new one " +
                                "to stream power, cadence, and distance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
                statusMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (savedDevices.isEmpty()) {
                    Text(
                        "No saved Concept2 monitors yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FormSectionLabel("Saved monitors")
                    savedDevices.forEachIndexed { index, device ->
                        TextButton(
                            onClick = { onConnectSavedDevice(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(device.displayName())
                                val labels = buildList {
                                    if (preferredAddress == device.address) add("Preferred")
                                    if (activeAddress == device.address &&
                                        connectionState == Concept2BleConnectionState.Connected
                                    ) {
                                        add("Connected")
                                    }
                                }
                                if (labels.isNotEmpty()) {
                                    Text(
                                        labels.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (index < savedDevices.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onScan) {
                Text("Scan")
            }
        },
        dismissButton = {
            Row {
                if (connectionState == Concept2BleConnectionState.Connected) {
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun Concept2SensorScanDialog(
    scanRows: List<Concept2ScanRow>,
    onDismiss: () -> Unit,
    onSelect: (Concept2ScanRow) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Concept2 monitor") },
        text = {
            if (scanRows.isEmpty()) {
                Text("No Concept2 monitors found yet. Wake the PM5 (press a button or start pedaling) so it advertises.")
            } else {
                Column {
                    scanRows.forEachIndexed { index, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(row) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = row.name?.takeIf { it.isNotBlank() } ?: "Concept2 monitor",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = row.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (index < scanRows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
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
    logged: Boolean = true,
    onLogWorkout: (() -> Unit)? = null,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sharing by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var backingUpRoute by remember { mutableStateOf(false) }
    var shareExtraHashtags by remember { mutableStateOf("") }
    val summaryContext = LocalContext.current
    val nip96Origin by userPreferences.nip96MediaServerOrigin.collectAsState(initial = "")
    val blossomPublicOrigin by userPreferences.blossomPublicServerOrigin.collectAsState(initial = "")
    val blossomPrivateOrigin by userPreferences.blossomPrivateServerOrigin.collectAsState(initial = "")
    val trustSelfSignedLanTls by userPreferences.trustSelfSignedLanTls.collectAsState(initial = false)
    val workoutMediaBackend by userPreferences.workoutMediaUploadBackend.collectAsState(
        initial = WorkoutMediaUploadBackend.NIP96
    )
    val attachRouteImage by userPreferences.attachRouteImageToWorkoutNostrShare.collectAsState(initial = true)
    val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(
        initial = com.erv.app.hr.HeartRateZoneInputs(),
    )
    val normalizedShareMediaOrigin = remember(nip96Origin, blossomPublicOrigin, workoutMediaBackend) {
        when (workoutMediaBackend) {
            WorkoutMediaUploadBackend.NIP96 -> Nip96Uploader.normalizeMediaServerOrigin(nip96Origin)
            WorkoutMediaUploadBackend.BLOSSOM -> Nip96Uploader.normalizeMediaServerOrigin(blossomPublicOrigin)
        }
    }
    val hasGpsForShare = session.gpsTrack?.points?.isNotEmpty() == true
    val keyManager = LocalKeyManager.current

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
                if (logged) "Workout logged" else "Review workout",
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
            if (session.activity.isErgMonitorActivity()) {
                val strokeErg = session.activity.isStrokeErgActivity()
                if (!strokeErg) {
                    formatCardioAverageSpeed(elapsedSeconds, session.distanceMeters, distanceUnit)?.let { speed ->
                        Text(
                            "Avg speed: $speed",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                }
                val erg = session.erg
                if (erg != null) {
                    erg.avgPowerWatts?.let { avg ->
                        val maxSuffix = erg.maxPowerWatts?.let { " (max $it W)" } ?: ""
                        Text(
                            "Avg power: $avg W$maxSuffix",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    erg.avgCadenceRpm?.let { avg ->
                        val unit = if (strokeErg) "spm" else "rpm"
                        val label = if (strokeErg) "Avg stroke rate" else "Avg cadence"
                        val maxSuffix = erg.maxCadenceRpm?.let { " (max $it $unit)" } ?: ""
                        Text(
                            "$label: $avg $unit$maxSuffix",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Text(
                        if (strokeErg) {
                            "Power, stroke rate, and distance captured from the Concept2 monitor."
                        } else {
                            "Power, cadence, and distance captured from the Concept2 monitor."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                } else {
                    session.distanceMeters?.takeIf { it > 1 }?.let {
                        Text(
                            if (strokeErg) {
                                "Erg distance uses a connected Concept2 monitor when available."
                            } else {
                                "Cycling distance uses a connected speed sensor (CSC or Concept2) when available."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }
            if (session.splits.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.cardio_split_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    session.splits.sortedBy { it.orderIndex }.forEach { split ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.cardio_split_label, split.orderIndex + 1),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = cardioSplitSummaryLine(split, distanceUnit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                                Text(
                                    text = stringResource(
                                        R.string.cardio_split_elapsed_summary,
                                        formatCardioElapsedClock(split.elapsedSeconds)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
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
                if (logged && relayPool != null && signer != null) {
                    val backupRelayPool = relayPool
                    val backupSigner = signer
                    OutlinedButton(
                        onClick = {
                            if (backingUpRoute) return@OutlinedButton
                            backingUpRoute = true
                            scope.launch {
                                try {
                                    val result = CardioRouteMediaBackup.backupRouteImage(
                                        appContext = summaryContext.applicationContext,
                                        session = session,
                                        dateIso = logDate.toString(),
                                        relayPool = backupRelayPool,
                                        signer = backupSigner,
                                        dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
                                        explicitPrivateBlossomOrigin = blossomPrivateOrigin,
                                        trustSelfSignedLanTls = trustSelfSignedLanTls,
                                        colorTop = dark.toArgb(),
                                        colorMid = mid.toArgb(),
                                        colorBottom = glow.toArgb(),
                                        zoneInputs = heartRateZoneInputs,
                                    )
                                    val message = when {
                                        result.origin == null ->
                                            "Set a private Blossom server or use Haven as your Data relay."
                                        result.failed ->
                                            "Route image backup failed."
                                        result.reused ->
                                            "Route image already backed up. Manifest ${if (result.manifestQueued) "queued" else "not queued"}."
                                        result.uploaded ->
                                            "Route image backed up. Manifest ${if (result.manifestQueued) "queued" else "not queued"}."
                                        else ->
                                            "Route image backup finished."
                                    }
                                    snackbarHostState.showSnackbar(message)
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Route backup failed: ${e.message ?: "unknown error"}")
                                } finally {
                                    backingUpRoute = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !backingUpRoute,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = SolidColor(Color.White)
                        )
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (backingUpRoute) "Backing up route…" else "Back Up Route Image")
                    }
                }
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
                hr != null && (hr.samples.size >= 2 || hr.avgBpm != null || hr.maxBpm != null || hr.minBpm != null) -> {
                    HeartRateSessionAnalyticsSection(
                        heartRate = hr,
                        zoneInputs = heartRateZoneInputs,
                        useLightOnDarkBackground = true,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
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
            if (logged && relayPool != null && signer != null) {
                val shareRelayPool = relayPool
                val shareSigner = signer
                if (attachRouteImage && hasGpsForShare && normalizedShareMediaOrigin.isEmpty()) {
                    Text(
                        stringResource(R.string.cardio_share_route_image_need_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = shareExtraHashtags,
                    onValueChange = { shareExtraHashtags = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sharing && !shared,
                    label = { Text(stringResource(R.string.workout_share_extra_hashtags_label)) },
                    placeholder = { Text(stringResource(R.string.workout_share_extra_hashtags_placeholder)) },
                    supportingText = {
                        Text(
                            stringResource(R.string.workout_share_extra_hashtags_helper),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White.copy(alpha = 0.6f),
                        focusedLabelColor = Color.White.copy(alpha = 0.85f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.72f),
                        disabledLabelColor = Color.White.copy(alpha = 0.5f),
                        focusedBorderColor = Color.White.copy(alpha = 0.9f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        disabledBorderColor = Color.White.copy(alpha = 0.35f),
                        cursorColor = Color.White,
                        focusedSupportingTextColor = Color.White.copy(alpha = 0.65f),
                        unfocusedSupportingTextColor = Color.White.copy(alpha = 0.6f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.45f),
                    )
                )
                OutlinedButton(
                    onClick = {
                        if (sharing || shared) return@OutlinedButton
                        sharing = true
                        scope.launch {
                            val outcome = publishWorkoutNote(
                                summaryContext,
                                shareRelayPool,
                                shareSigner,
                                session,
                                distanceUnit,
                                nip96Origin,
                                blossomPublicOrigin,
                                workoutMediaBackend,
                                attachRouteImage,
                                dark,
                                mid,
                                glow,
                                shareExtraHashtags
                            )
                            sharing = false
                            shared = outcome.relayOk
                            if (outcome.uploadedRouteImageUrl != null) {
                                val url = outcome.uploadedRouteImageUrl
                                repository.updateSession(logDate, session.id) { it.copy(routeImageUrl = url) }
                                repository.currentState().logFor(logDate)?.let { log ->
                                    CardioSync.publishDailyLog(
                                        summaryContext.applicationContext,
                                        shareRelayPool,
                                        shareSigner,
                                        log,
                                        keyManager.relayUrlsForKind30078Publish(),
                                    )
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
                onClick = {
                    if (logged) onDone() else onLogWorkout?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = mid)
            ) {
                Text(if (logged) "Done" else "Log")
            }
            if (!logged) {
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = SolidColor(Color.White)
                    )
                ) {
                    Text("Discard")
                }
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
    routeImageUrl: String? = null,
    extraTopics: List<String> = emptyList()
): String = buildString {
    append("\uD83C\uDFC3 Completed: ${session.activity.displayLabel}")
    if (session.modality == CardioModality.INDOOR_TREADMILL) append(" (indoor)")
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
        append("\u2764\uFE0F Heart rate (avg): $avg bpm\n")
    }
    session.erg?.let { erg ->
        erg.avgPowerWatts?.let { avg ->
            append("\u26A1 Power (avg): $avg W")
            erg.maxPowerWatts?.let { append(" \u2022 max $it W") }
            append("\n")
        }
        erg.avgCadenceRpm?.let { avg ->
            append("\uD83D\uDD01 Cadence (avg): $avg rpm\n")
        }
    }
    if (session.segments.isNotEmpty()) {
        val labels = session.segments.sortedBy { it.orderIndex }.joinToString(" → ") { it.activity.displayLabel }
        append("\uD83D\uDD04 Segments: $labels\n")
    }
    append("\n${buildWorkoutShareHashtagContentLine(extraTopics)}")
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
    glow: Color,
    extraHashtagInput: String = ""
): WorkoutPublishOutcome {
    val extraTopics = parseExtraWorkoutHashtagTopics(extraHashtagInput)
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
    val tags = workoutShareKind1TopicTags(extraTopics).toMutableList()
    if (routeImageUrl != null) {
        tags.add(listOf("imeta", "url $routeImageUrl", "m image/png", "dim 1080x1440"))
    }
    val content = buildWorkoutNoteContent(session, distanceUnit, routeImageUrl, extraTopics)
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

/** Prep countdown at the start of a guided multi-leg interval workout (before leg 1). */
private const val CardioIntervalWorkoutPrepSeconds = 20

@Composable
private fun CardioLiveErgSensorStats(
    distanceUnit: CardioDistanceUnit,
    speedKmh: Double?,
    cadenceRpm: Int?,
    distanceMeters: Double?,
    powerWatts: Int?,
    strokeBased: Boolean = false,
) {
    val hasLiveSensorMetric =
        speedKmh != null || cadenceRpm != null || distanceMeters != null || powerWatts != null
    Spacer(Modifier.height(12.dp))
    if (powerWatts != null) {
        Text(
            text = "$powerWatts W",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Text(
            text = "Power",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(6.dp))
    }
    Text(
        text = if (strokeBased) {
            speedKmh?.let { speed -> "Pace ${format500mPaceFromKmh(speed)}" } ?: "Pace --"
        } else {
            speedKmh?.let { speed -> "Speed ${formatCardioSpeedFromKmh(speed, distanceUnit)}" } ?: "Speed --"
        },
        style = MaterialTheme.typography.titleMedium,
        color = Color.White.copy(alpha = if (speedKmh != null) 0.95f else 0.65f)
    )
    Text(
        text = distanceMeters?.let { distance ->
            "Distance ${formatCardioDistanceFromMeters(distance, distanceUnit)}"
        } ?: "Distance --",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = if (distanceMeters != null) 0.9f else 0.65f)
    )
    Text(
        text = if (strokeBased) {
            cadenceRpm?.let { rate -> "$rate spm stroke rate" } ?: "Stroke rate --"
        } else {
            cadenceRpm?.let { cadence -> "$cadence rpm cadence" } ?: "Cadence --"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = if (cadenceRpm != null) 0.9f else 0.65f)
    )
    Text(
        when {
            hasLiveSensorMetric && strokeBased -> "Live erg sensor data"
            hasLiveSensorMetric -> "Live cycling sensor data"
            strokeBased -> "Start the erg to stream sensor stats"
            else -> "Start pedaling to stream sensor stats"
        },
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.65f)
    )
}

/** Concept2-style rowing/ski pace: time per 500 m derived from the monitor's simulated speed. */
private fun format500mPaceFromKmh(speedKmh: Double): String {
    val metersPerSecond = speedKmh / 3.6
    if (metersPerSecond <= 0.0) return "--"
    val secondsPer500 = (500.0 / metersPerSecond).roundToInt()
    val minutes = secondsPer500 / 60
    val seconds = secondsPer500 % 60
    return "%d:%02d /500m".format(minutes, seconds)
}

@Composable
fun CardioMultiLegTimerFullScreen(
    state: CardioMultiLegTimerState,
    stateKey: Any,
    userPreferences: UserPreferences,
    dark: Color,
    mid: Color,
    glow: Color,
    onLeaveWorkoutUi: (() -> Unit)? = null,
    onBeginWorkout: () -> Unit = {},
    onFinishLeg: (elapsedSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    key(stateKey) {
        val heartRateBle = LocalHeartRateBle.current
        val cyclingCscBle = LocalCyclingCsc.current
        val concept2Ble = LocalConcept2Pm.current
        val heartRateBannerExpanded by userPreferences.heartRateBannerExpanded.collectAsState(initial = true)
        val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(
            initial = com.erv.app.hr.HeartRateZoneInputs(),
        )
        val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
        val scope = rememberCoroutineScope()
        val requestHeartRateBlePermissions = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        // Live erg/sensor stats for the current leg (Concept2 erg preferred over a CSC speed sensor).
        val currentLegIsCycling = state.currentLeg.activity.isCyclingActivity()
        val currentLegIsErg = state.currentLeg.activity.isErgMonitorActivity()
        val currentLegIsStrokeErg = state.currentLeg.activity.isStrokeErgActivity()
        val ergConnectionState by concept2Ble.connectionState.collectAsState()
        val cyclingConnectionState by cyclingCscBle.connectionState.collectAsState()
        val ergPowerWatts by concept2Ble.currentPowerWatts.collectAsState()
        val ergSpeedKmh by concept2Ble.currentSpeedKmh.collectAsState()
        val ergCadenceRpm by concept2Ble.currentCadenceRpm.collectAsState()
        val ergWorkoutDistanceMeters by concept2Ble.workoutDistanceMeters.collectAsState()
        val cyclingSpeedKmh by cyclingCscBle.currentSpeedKmh.collectAsState()
        val cyclingCadenceRpm by cyclingCscBle.currentCadenceRpm.collectAsState()
        val cyclingWorkoutDistanceMeters by cyclingCscBle.workoutDistanceMeters.collectAsState()
        val ergLegConnected = currentLegIsErg &&
            ergConnectionState == Concept2BleConnectionState.Connected
        val cscLegConnected = currentLegIsCycling &&
            cyclingConnectionState == CyclingCscBleConnectionState.Connected
        val targetMinutes = state.currentLeg.targetDurationMinutes?.takeIf { it > 0 }
        val guided = targetMinutes != null
        val awaitingStart = state.isPendingStart()
        val bikeErgEnabled = remember(state.legs) {
            state.legs.any { it.activity.supportsBikeErgSensorConnect() }
        }
        val anyCyclingLeg = remember(state.legs) {
            state.legs.any { it.activity.isCyclingActivity() }
        }
        val bikeErgHandle = rememberCardioBikeErgSensorConnect(
            enabled = bikeErgEnabled,
            sessionKey = stateKey,
            cyclingSensorApplicable = anyCyclingLeg,
        )
        var showMediaSheet by remember { mutableStateOf(false) }
        var running by remember(stateKey) { mutableStateOf(!awaitingStart) }
        var tick by remember(stateKey) { mutableIntStateOf(0) }
        val atWorkoutStart = state.currentLegIndex == 0 && state.completedSegments.isEmpty()
        val initialPrep = guided && atWorkoutStart && !awaitingStart
        var guidedRemainingSec by remember(stateKey) {
            mutableIntStateOf(
                when {
                    initialPrep -> CardioIntervalWorkoutPrepSeconds
                    guided -> targetMinutes!! * 60
                    else -> 0
                }
            )
        }
        var guidedInPrep by remember(stateKey) { mutableStateOf(initialPrep) }

        LaunchedEffect(stateKey, heartRateBannerExpanded) {
            if (heartRateBannerExpanded) {
                heartRateBle.tryPreferredDeviceReconnectOnce()
            }
        }

        if (guided) {
            LaunchedEffect(stateKey) {
                if (state.isPendingStart()) return@LaunchedEffect
                val targetMin = state.currentLeg.targetDurationMinutes?.takeIf { it > 0 } ?: return@LaunchedEffect
                val targetSec = targetMin * 60
                val prepFirst = state.currentLegIndex == 0 && state.completedSegments.isEmpty()
                if (prepFirst) {
                    guidedInPrep = true
                    playHiitSoftSegmentStartCue()
                    var p = CardioIntervalWorkoutPrepSeconds
                    while (p > 0) {
                        guidedRemainingSec = p
                        if (p in 1..min(5, CardioIntervalWorkoutPrepSeconds)) {
                            playHiitWorkCountdownTickCue()
                        }
                        delay(1_000L)
                        p--
                    }
                    guidedInPrep = false
                }
                playHiitWorkSegmentStartCue()
                var s = targetSec
                while (s > 0) {
                    guidedRemainingSec = s
                    if (s in 1..min(5, targetSec)) {
                        playHiitWorkCountdownTickCue()
                    }
                    delay(1_000L)
                    s--
                }
                playHiitWorkSegmentEndCue()
                onFinishLeg(targetSec)
            }
        } else {
            LaunchedEffect(stateKey, running) {
                while (running) {
                    delay(1000)
                    tick++
                }
            }
        }
        val elapsed = remember(tick, state.legStartedEpoch, awaitingStart) {
            if (awaitingStart) 0 else (nowEpochSeconds() - state.legStartedEpoch).coerceAtLeast(0).toInt()
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
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val showHeartRateBanner = !heartRateBannerExpanded
                                        userPreferences.setHeartRateBannerExpanded(showHeartRateBanner)
                                        if (showHeartRateBanner) {
                                            heartRateBle.tryPreferredDeviceReconnectOnce()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (heartRateBannerExpanded) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Heart rate monitor",
                                    tint = if (heartRateBannerExpanded) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.88f)
                                )
                            }
                            if (bikeErgEnabled) {
                                with(bikeErgHandle) {
                                    CardioBikeErgSensorToolbarActions(handle = this, lightOnDark = true)
                                }
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
                    if (heartRateBannerExpanded) {
                        Spacer(Modifier.height(8.dp))
                        HeartRateTopBar(
                            viewModel = heartRateBle,
                            onRequestBlePermissions = {
                                requestHeartRateBlePermissions.launch(requiredBlePermissionsForHeartRate())
                            },
                            zoneInputs = heartRateZoneInputs,
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
                    if (guided) {
                        Text(
                            if (guidedInPrep) {
                                "GET READY — first leg starts after countdown"
                            } else {
                                "Guided timer — rounds advance automatically"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    } else {
                        state.currentLeg.targetDurationMinutes?.takeIf { it > 0 }?.let { target ->
                            Text(
                                "Suggested target: $target min — tap stop when this leg is done",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )
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
                    if (awaitingStart) {
                        if (bikeErgEnabled) {
                            Spacer(Modifier.height(12.dp))
                            CardioBikeErgSensorPreStartPanel(
                                handle = bikeErgHandle,
                                lightOnDark = true,
                            )
                            Spacer(Modifier.height(20.dp))
                        }
                        CardioStartWorkoutButton(
                            onClick = onBeginWorkout,
                            accentBackground = dark,
                        )
                    } else {
                    val displaySec = if (guided) guidedRemainingSec.coerceAtLeast(0) else elapsed
                    val mins = displaySec / 60
                    val secs = displaySec % 60
                    Text(
                        text = "%d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (guided) {
                            if (guidedInPrep) "Get ready" else "Time remaining"
                        } else {
                            "Time on this leg"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    if (ergLegConnected || cscLegConnected) {
                        CardioLiveErgSensorStats(
                            distanceUnit = distanceUnit,
                            speedKmh = if (ergLegConnected) ergSpeedKmh else cyclingSpeedKmh,
                            cadenceRpm = if (ergLegConnected) ergCadenceRpm else cyclingCadenceRpm,
                            distanceMeters = if (ergLegConnected) {
                                ergWorkoutDistanceMeters
                            } else {
                                cyclingWorkoutDistanceMeters
                            },
                            powerWatts = if (ergLegConnected) ergPowerWatts else null,
                            strokeBased = currentLegIsStrokeErg,
                        )
                    }
                    }
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
                                compact = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (!guided && !awaitingStart) {
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
                    }
                    OutlinedButton(
                        onClick = onCancel,
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
                    label = { FieldLabel("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = metStr,
                    onValueChange = { metStr = it },
                    label = { FieldLabel("MET (optional)") },
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
                    "Add one activity for a simple workout, or several for bricks / tri training. Belt speed and incline apply only to legs that are walk, run, sprint, or ruck when you pick Indoor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { FieldLabel("Routine name") },
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
                                FormSectionLabelSmall("Activity ${index + 1}")
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
                                    label = { FieldLabel("Built-in") }
                                )
                                FilterChip(
                                    selected = draft.useCustom,
                                    onClick = { updateStep(index) { it.copy(useCustom = true, modality = CardioModality.OUTDOOR) } },
                                    label = { FieldLabel("Custom") }
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
                                    cardioBuiltinActivitiesForUserSelection().forEach { b ->
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
                                        label = { FieldLabel("Outdoor") }
                                    )
                                    FilterChip(
                                        selected = draft.modality == CardioModality.INDOOR_TREADMILL,
                                        onClick = { updateStep(index) { it.copy(modality = CardioModality.INDOOR_TREADMILL) } },
                                        label = { Text(CardioModality.INDOOR_TREADMILL.label()) }
                                    )
                                }
                            }
                            if (draft.modality == CardioModality.INDOOR_TREADMILL && treadmillApp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = draft.speedUnit == CardioSpeedUnit.MPH,
                                        onClick = { updateStep(index) { it.copy(speedUnit = CardioSpeedUnit.MPH) } },
                                        label = { FieldLabel("mph") }
                                    )
                                    FilterChip(
                                        selected = draft.speedUnit == CardioSpeedUnit.KMH,
                                        onClick = { updateStep(index) { it.copy(speedUnit = CardioSpeedUnit.KMH) } },
                                        label = { FieldLabel("km/h") }
                                    )
                                }
                                OutlinedTextField(
                                    draft.speedStr,
                                    { v -> updateStep(index) { s -> s.copy(speedStr = v) } },
                                    label = { FieldLabel("Speed") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    draft.inclineStr,
                                    { v -> updateStep(index) { s -> s.copy(inclineStr = v) } },
                                    label = { FieldLabel("Incline %") },
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
                                        label = { FieldLabel("Pack lb") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            OutlinedTextField(
                                draft.targetMinutesStr,
                                { v -> updateStep(index) { s -> s.copy(targetMinutesStr = v) } },
                                label = { FieldLabel("Target minutes (this leg)") },
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
                    label = { FieldLabel("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabelMedium("Repeat days")
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
