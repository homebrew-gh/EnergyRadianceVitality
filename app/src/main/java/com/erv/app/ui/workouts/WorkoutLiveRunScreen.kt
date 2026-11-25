package com.erv.app.ui.workouts

import com.erv.app.data.coachLineFor
import androidx.activity.compose.BackHandler
import com.erv.app.cardio.CardioGpsSplits
import com.erv.app.cardio.formatCardioElapsedClock
import com.erv.app.data.CardioLiveSplitMode
import com.erv.app.workouts.activeSessionPause
import com.erv.app.workouts.sessionStepElapsedSeconds
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Switch
import com.erv.app.cardio.CardioGpsMath
import com.erv.app.cardio.CardioGpsPoint
import com.erv.app.cardio.CardioGpsRecordingHub
import com.erv.app.cardio.formatCardioAveragePace
import com.erv.app.workouts.WorkoutSessionGps
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.theme.ervTopAppBarColors
import com.erv.app.ui.theme.ervSessionTopAppBarColors
import com.erv.app.hr.HeartRatePill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import com.erv.app.ui.media.WorkoutMediaControlPanel
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.ui.components.WorkoutKind1SharePreviewDialog
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.ui.theme.ErvSessionRed
import com.erv.app.ui.weighttraining.WeightWorkoutSummaryFullScreen
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.ui.weighttraining.WeightExerciseRecentWorkoutsDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutFgsDisclosureDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.ui.weighttraining.PlateCalculatorUiState
import com.erv.app.ui.weighttraining.WeightHiitIntervalTimerOverlay
import com.erv.app.ui.weighttraining.WeightTimedSetTimerOverlay
import com.erv.app.weighttraining.PlateLoadingStyle
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightHiitIntervalPlan
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightSetLoggingStyle
import com.erv.app.weighttraining.defaultBarForExercise
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.plateCalculatorBars
import com.erv.app.weighttraining.plateCalculatorPlates
import com.erv.app.weighttraining.plateLoading
import com.erv.app.weighttraining.setLoggingStyle
import com.erv.app.weighttraining.usesTimedHoldCountdownBeeps
import com.erv.app.workouts.DEFAULT_TIMED_PREP_SECONDS
import com.erv.app.workouts.canLogSessionHiitBlock
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutActiveRun
import com.erv.app.workouts.WorkoutItem
import com.erv.app.workouts.nextAlternativeChoiceIndex
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.WorkoutRunEngine
import com.erv.app.workouts.WorkoutRunPosition
import com.erv.app.workouts.WorkoutSegment
import com.erv.app.workouts.WorkoutSegmentKind
import com.erv.app.workouts.displayExerciseName
import com.erv.app.workouts.displaySummary
import com.erv.app.workouts.isFreshWithNothingLogged
import com.erv.app.workouts.isStarted
import com.erv.app.workouts.nowWorkoutEpochSeconds
import com.erv.app.weighttraining.RestTimerAlarm
import com.erv.app.weighttraining.restEndCueIsFresh
import com.erv.app.weighttraining.restSecondsRemaining
import com.erv.app.ui.media.playHiitWorkSegmentEndCue
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioMetEstimator
import com.erv.app.stretching.StretchingSync
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioRepository
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.isCyclingActivity
import com.erv.app.cardio.isErgMonitorActivity
import com.erv.app.cardio.CardioErgMetrics
import com.erv.app.cycling.Concept2BleConnectionState
import com.erv.app.cycling.Concept2Pm5BleViewModel
import com.erv.app.cycling.CyclingCscBleConnectionState
import com.erv.app.cycling.CyclingCscBleViewModel
import com.erv.app.cycling.LocalConcept2Pm
import com.erv.app.cycling.LocalCyclingCsc
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.data.WeightLiveRestTimerMode
import com.erv.app.programs.encodeStretchLaunch
import com.erv.app.hr.HeartRateSessionAnalyticsSection
import com.erv.app.hr.HeartRateZoneInputs
import com.erv.app.stretching.StretchingRepository
import com.erv.app.ui.cardio.CardioBikeErgConnectInlineSection
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
import com.erv.app.ui.cardio.supportsBikeErgSensorConnect
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.buildWorkoutShareHashtagContentLineFromTopics
import com.erv.app.nostr.workoutShareBaseTopicTags
import com.erv.app.workouts.ComposedWorkoutHrSection
import com.erv.app.workouts.ComposedWorkoutHrSummary
import com.erv.app.workouts.summaryLabel
import com.erv.app.workouts.attachComposedWorkoutHeartRateToLinkedLogs
import com.erv.app.workouts.backupComposedWorkoutSessionMedia
import com.erv.app.workouts.buildComposedWorkoutHrSummary
import com.erv.app.workouts.buildComposedWorkoutKind1Draft
import com.erv.app.workouts.publishComposedWorkoutNote
import com.erv.app.workouts.resolveCardioLaunch
import com.erv.app.workouts.resolveStretchLaunch
import com.erv.app.workouts.weightItems
import com.erv.app.workouts.WorkoutSync
import com.erv.app.workouts.WorkoutTargetWeightChange
import com.erv.app.workouts.withTodaysWeights
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.WeightCalorieEstimator
import com.erv.app.workouts.WorkoutSessionEngine
import com.erv.app.workouts.WorkoutSessionEvent
import com.erv.app.workouts.WorkoutSessionNames
import com.erv.app.workouts.WorkoutSessionNotifier
import com.erv.app.workouts.WorkoutSessionRestKind
import com.erv.app.workouts.WorkoutSessionStep
import com.erv.app.workouts.WorkoutStepOutcome
import com.erv.app.workouts.displayTitle
import com.erv.app.workouts.isSessionComplete
import com.erv.app.workouts.sessionCountdownEndsAtEpochSeconds
import com.erv.app.workouts.attachSectionHeartRateToLiftLogs
import com.erv.app.workouts.canAddSessionSet
import com.erv.app.workouts.countdownSeconds
import com.erv.app.workouts.cardioSessionFor
import com.erv.app.cardio.CardioModality
import com.erv.app.workouts.standaloneCardioDraftFor
import com.erv.app.workouts.isSessionCardioActivity
import com.erv.app.workouts.linkFor
import com.erv.app.workouts.sessionCardioItemsToLog
import com.erv.app.workouts.sessionStretchSectionsToLog
import com.erv.app.workouts.canRemoveSessionSet
import com.erv.app.workouts.canRemoveSessionSetAt
import com.erv.app.workouts.isSessionWaitingForMore
import com.erv.app.workouts.isStandaloneWorkoutId
import com.erv.app.workouts.WorkoutSessionRestMode
import com.erv.app.workouts.WorkoutSessionRestOptions
import com.erv.app.workouts.sessionExerciseFor
import com.erv.app.workouts.sessionHasNothingDone
import com.erv.app.workouts.sessionItemKey
import com.erv.app.workouts.sessionLiftSectionsToLog
import com.erv.app.workouts.sessionPlan
import com.erv.app.workouts.sessionProgressLabel
import com.erv.app.workouts.sessionUpNextLabel
import com.erv.app.workouts.sessionFocusLiftIndex
import com.erv.app.workouts.weightSessionFor
import com.erv.app.workouts.WorkoutSessionAddition
import com.erv.app.workouts.adjustedWorkout
import com.erv.app.workouts.canMoveSessionItem
import com.erv.app.workouts.canMoveSessionSection
import com.erv.app.workouts.canRemoveSessionItem
import com.erv.app.workouts.describeAdjustmentsFrom
import com.erv.app.cardio.cardioBuiltinActivitiesForUserSelection
import com.erv.app.ui.weighttraining.WeightPickExerciseDialog
import com.erv.app.ui.weighttraining.WeightExerciseEditorDialog
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.recentExerciseIds
import java.time.LocalDate
import androidx.compose.foundation.isSystemInDarkTheme
import com.erv.app.workouts.WorkoutLoggedItemKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLiveRunScreen(
    workoutId: String,
    repository: WorkoutRepository,
    cardioRepository: CardioRepository,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    activeRun: WorkoutActiveRun?,
    /** A started run of a different workout; this screen must not replace it without asking. */
    otherRunInProgress: WorkoutActiveRun? = null,
    workout: Workout?,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    userPreferences: UserPreferences,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    relayPool: RelayPool? = null,
    signer: EventSigner? = null,
    onBack: () -> Unit,
    /** Open the run screen of the workout already in progress. */
    onResumeOtherRun: (String) -> Unit = {},
    onOpenWeightCategory: () -> Unit,
    /** Opens the weight log, from a Quick Start workout's summary. */
    onOpenWeightLog: () -> Unit = onOpenWeightCategory,
    onOpenCardioCategory: () -> Unit,
    onOpenStretchCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    // A standalone workout's only copy is its run, which is cleared when the athlete is done:
    // hold the last one seen so the summary and its last frame still have a workout.
    val lastWorkout = remember(workoutId) { mutableStateOf(workout) }
    SideEffect { if (workout != null) lastWorkout.value = workout }
    @Suppress("NAME_SHADOWING")
    val workout = workout ?: lastWorkout.value
    val isStandalone = isStandaloneWorkoutId(workoutId)
    val heartRateBle = LocalHeartRateBle.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    val heartRateZoneInputs by userPreferences.heartRateZoneInputs.collectAsState(initial = HeartRateZoneInputs())
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val distanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val fallbackBodyWeightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    // Rest timer: null until the settings load, so a run never starts on a guessed setting.
    val restTimerMode by userPreferences.workoutSessionRestTimerMode.collectAsState(initial = null)
    val restTimerSeconds by userPreferences.weightLiveRestTimerSeconds.collectAsState(initial = null)
    val restCountdownSound by userPreferences.weightLiveRestTimerCountdownSoundEnabled.collectAsState(initial = true)
    val restEndSound by userPreferences.weightLiveRestTimerEndSoundEnabled.collectAsState(initial = true)
    // Picking an RPE logs the set and starts the rest; off, each set has a check button instead.
    val rpeLogsSet by userPreferences.workoutSessionRpeLogsSet.collectAsState(initial = true)
    var showRestSettings by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var composedSummary by remember { mutableStateOf<ComposedWorkoutHrSummary?>(null) }
    // A Quick Start workout that was only lifting finishes on the weight screen's summary.
    var weightSummary by remember { mutableStateOf<StandaloneWeightSummary?>(null) }
    var runFinalized by remember { mutableStateOf(false) }
    var finishingRun by remember { mutableStateOf(false) }
    // Set if building the finish summary failed, so the screen offers a way out instead.
    var finalizeFailed by remember { mutableStateOf(false) }
    var historyFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Label of the item the athlete asked to skip; non-null while the confirmation dialog is up.
    var pendingSkipLabel by remember { mutableStateOf<String?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    // Storyboard item (segment id to item id) being swapped, or picked from the whole library.
    var swapItem by remember { mutableStateOf<Pair<String, String>?>(null) }
    // The full-screen hold timer for a timed set, and the interval timer, while one is open.
    var holdTimer by remember { mutableStateOf<SessionHoldTimer?>(null) }
    var intervalTimer by remember { mutableStateOf<SessionIntervalTimer?>(null) }
    val ownedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())
    val plateBarByExercise by userPreferences.plateBarByExercise.collectAsState(initial = emptyMap())
    val plateBars = remember(ownedEquipment, loadUnit) { plateCalculatorBars(ownedEquipment, loadUnit) }
    val platePlates = remember(ownedEquipment, loadUnit) { plateCalculatorPlates(ownedEquipment, loadUnit) }

    /** Plates per side for a plate-loaded lift, as the weight screen shows them; null otherwise. */
    fun plateCalculatorFor(exercise: WeightExercise?): PlateCalculatorUiState? {
        if (exercise == null) return null
        val loading = exercise.plateLoading() ?: return null
        if (loading == PlateLoadingStyle.NONE) return null
        val bar = defaultBarForExercise(exercise.name, plateBars.bars, plateBarByExercise[exercise.id]) ?: return null
        return PlateCalculatorUiState(
            loading = loading,
            bars = plateBars.bars,
            selectedBar = bar,
            plates = platePlates.plates,
            assumedInventory = plateBars.assumed || platePlates.assumed,
            onSelectBar = { barId -> scope.launch { userPreferences.setPlateBarForExercise(exercise.id, barId) } },
        )
    }
    var swapFromLibrary by remember { mutableStateOf<Pair<String, String>?>(null) }
    var addKind by remember { mutableStateOf<WorkoutSessionAddKind?>(null) }
    // Name typed in the picker for a new exercise, and the item it swaps in for (null: add it).
    var createExercise by remember { mutableStateOf<Pair<String, Pair<String, String>?>?>(null) }
    // Segment id, item id and name of the item the athlete asked to remove.
    var removeTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var editingSet by remember { mutableStateOf<WorkoutSessionLoggedSet?>(null) }
    var disclosureDismissed by remember { mutableStateOf(false) }
    // The music bar under the workout, from the top bar's note button (as on the old weight screen).
    var mediaControlsShown by rememberSaveable { mutableStateOf(false) }
    // One section is logged at a time, so two quick taps can't save it twice.
    val liftLogMutex = remember { Mutex() }

    val plan = remember(activeRun?.sessionId, activeRun?.workoutSnapshot, activeRun?.sessionRestOptions) { activeRun?.sessionPlan() }
    val session = activeRun?.session
    val currentStep = if (plan != null && session != null) WorkoutSessionEngine.currentStep(plan, session) else null
    // The lift set whose exercise is on screen; during a rest, the one just done.
    val focusLiftIndex = if (plan != null) activeRun?.sessionFocusLiftIndex(plan) else null
    // Every exercise of today's run in order, so the lifting card can page back to a finished
    // one (or ahead) to look at or fix its sets.
    val liftItemKeys = plan?.steps.orEmpty()
        .filterIsInstance<WorkoutSessionStep.LiftSet>()
        .map { it.segmentId to it.itemId }
        .distinct()
    val focusLiftKey = (focusLiftIndex?.let { plan?.steps?.getOrNull(it) } as? WorkoutSessionStep.LiftSet)
        ?.let { it.segmentId to it.itemId }
    var viewedLiftKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Moving on to another exercise brings the card back to it.
    LaunchedEffect(focusLiftKey) { viewedLiftKey = null }
    val shownLiftKey = viewedLiftKey?.takeIf { it in liftItemKeys } ?: focusLiftKey
    val runListState = rememberLazyListState()

    val runKeyManager = LocalKeyManager.current
    val concept2Ble = LocalConcept2Pm.current
    val cyclingCscBle = LocalCyclingCsc.current

    val splitMode by userPreferences.cardioLiveSplitMode.collectAsState(initial = CardioLiveSplitMode.OFF)
    val autoSplitQuarterMiles by userPreferences.cardioLiveAutoSplitQuarterMiles.collectAsState(initial = 4)
    // Splits on this screen come from the route: one per split distance, unless splits are Off.
    val sessionSplitMeters = if (splitMode == CardioLiveSplitMode.OFF) {
        null
    } else {
        CardioGpsSplits.splitMeters(autoSplitQuarterMiles, distanceUnit)
    }

    /**
     * Saves what the session has finished to the silo logs: a weight entry per lifting section, a
     * cardio entry per cardio item, a stretch entry per stretching section.
     */
    suspend fun logFinishedSections() = liftLogMutex.withLock {
        val run = repository.currentState().activeRun?.takeIf { it.workoutId == workoutId } ?: return@withLock
        val runPlan = run.sessionPlan()
        for (item in run.sessionCardioItemsToLog(runPlan)) {
            val today = LocalDate.now()
            val cardioLibrary = cardioRepository.currentState()
            // The route recorded on this screen for the item, if any (also after a restart).
            val gpsPoints = WorkoutSessionGps.take(
                appContext,
                WorkoutSessionGps.keyFor(run.sessionId, item.segmentId, item.itemId),
            )
            val routed = run.cardioSessionFor(item, cardioLibrary, gpsPoints) ?: continue
            val splitMeters = sessionSplitMeters
            val base = if (splitMeters != null && gpsPoints.size >= 2) {
                routed.copy(splits = CardioGpsSplits.fromTrack(gpsPoints, splitMeters))
            } else {
                routed
            }
            // The per-item heart rate and sensor totals began when the item's first leg started.
            // Take both erg and cycling summaries to clear their buffers; prefer the erg's, as the
            // cardio screen does.
            val ergSummary = if (base.activity.isErgMonitorActivity()) concept2Ble.takeWorkoutSummary() else null
            val cscDistance = if (base.activity.isCyclingActivity()) {
                cyclingCscBle.takeWorkoutSummary()?.distanceMeters
            } else {
                null
            }
            val withSensors = base.copy(
                distanceMeters = ergSummary?.distanceMeters ?: cscDistance ?: base.distanceMeters,
                erg = ergSummary?.let { summary ->
                    CardioErgMetrics(
                        avgPowerWatts = summary.avgPowerWatts,
                        maxPowerWatts = summary.maxPowerWatts,
                        avgCadenceRpm = summary.avgCadenceRpm,
                        maxCadenceRpm = summary.maxCadenceRpm,
                    )
                },
            )
            val withHr = heartRateBle.takeWorkoutHeartRateSummary()?.let { withSensors.copy(heartRate = it) } ?: withSensors
            val stored = CardioMetEstimator.applyEstimatedKcal(withHr, cardioLibrary, fallbackBodyWeightKg)
            // addSession uploads the day log in the background.
            cardioRepository.addSession(today, stored)
            repository.recordSessionCardioLog(item, today.toString(), stored.id)
        }
        for (section in run.sessionStretchSectionsToLog(runPlan)) {
            val today = LocalDate.now()
            val link = run.linkFor(section.segmentId, section.entries.first().itemId)
            stretchingRepository.logSession(
                date = today,
                stretchIds = section.entries.map { it.catalogId },
                totalMinutes = section.totalMinutes,
                workoutLink = link,
            )
            val log = stretchingRepository.currentState().logFor(today)
            val saved = log?.sessions?.lastOrNull { it.workoutLink == link } ?: continue
            repository.recordSessionStretchLog(section, today.toString(), saved.id)
            if (relayPool != null && signer != null) {
                // Off the save path, so the relays never hold up the workout.
                scope.launch {
                    runCatching {
                        StretchingSync.publishDailyLog(
                            appContext,
                            relayPool,
                            signer,
                            log,
                            runKeyManager.relayUrlsForKind30078Publish(),
                        )
                    }
                }
            }
        }
        for (section in run.sessionLiftSectionsToLog(runPlan)) {
            val today = LocalDate.now()
            val base = run.weightSessionFor(section)
            val stored = base.copy(estimatedKcal = WeightCalorieEstimator.estimateKcal(base, fallbackBodyWeightKg))
            weightRepository.addWorkout(today, stored)
            // addWorkout uploads the day log in the background; waiting on the relays here held
            // up the finish summary.
            repository.recordSessionLiftLog(section, today.toString(), stored.id)
        }
    }

    /** Sends [event] for the step on screen; ignored if the session has already moved on. */
    fun send(event: WorkoutSessionEvent) {
        val atStep = session?.stepIndex
        scope.launch {
            repository.applySessionEvent(event, atStepIndex = atStep)
            logFinishedSections()
        }
    }

    /** Adds a set to the exercise on screen (1) or takes one away (-1), for today. */
    fun changeSetCount(delta: Int) {
        val atStep = session?.stepIndex
        scope.launch { repository.changeSessionSetCount(delta, atStepIndex = atStep) }
    }

    val coachReview by userPreferences.coachReview.collectAsState(initial = null)

    /** Sets logged for [exerciseId] the last time, so a standalone workout starts with as many. */
    fun lastSetCountFor(exerciseId: String): Int? =
        weightState.historyForExercise(exerciseId).firstOrNull()?.entry?.sets?.size?.takeIf { it > 0 }

    // The run's own copy: the plan is built from it, and sets added today live in it.
    fun itemOf(step: WorkoutSessionStep): WorkoutItem? =
        (activeRun?.workoutSnapshot ?: workout)?.segments?.getOrNull(step.segmentIndex)
            ?.items?.firstOrNull { it.id == step.itemId }

    fun liftName(step: WorkoutSessionStep.LiftSet): String {
        val exerciseId = activeRun?.sessionExerciseFor(step) ?: step.exerciseId
        val libraryName = weightState.exerciseById(exerciseId)?.name
        if (exerciseId != step.exerciseId) return libraryName ?: exerciseId
        return (itemOf(step) as? WorkoutItem.Weight)?.displayExerciseName(libraryName) ?: libraryName ?: exerciseId
    }

    val stepNames = WorkoutSessionNames { step ->
        when (step) {
            is WorkoutSessionStep.LiftSet -> liftName(step)
            is WorkoutSessionStep.CardioLeg -> (itemOf(step) as? WorkoutItem.Cardio)?.title?.takeIf { it.isNotBlank() }
            is WorkoutSessionStep.StretchHold ->
                (itemOf(step) as? WorkoutItem.Mobility)?.title?.takeIf { it.isNotBlank() }
                    ?: stretchingRepository.catalog.firstOrNull { it.id == step.catalogId }?.name
            else -> null
        }
    }

    pendingSkipLabel?.let { label ->
        AlertDialog(
            onDismissRequest = { pendingSkipLabel = null },
            title = { Text("Skip $label?") },
            text = {
                Text("Nothing more will be logged for it. It will be listed as skipped in the workout summary and the workout moves on.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSkipLabel = null
                        send(WorkoutSessionEvent.SkipItem)
                    },
                ) {
                    Text("Skip")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSkipLabel = null }) { Text("Keep going") }
            },
        )
    }

    // Tapping Start Workout on the Training tab is the start: opening the run begins the clock
    // and heart-rate recording, with no second Start tap on this screen.
    LaunchedEffect(workoutId, workout, otherRunInProgress?.sessionId) {
        if (
            !finishingRun &&
            workout != null &&
            otherRunInProgress == null &&
            (activeRun == null || activeRun.workoutId != workoutId || !activeRun.isStarted())
        ) {
            repository.beginRun(workoutId)
        }
    }

    // The run's steps live in the session; a run started before it existed joins it at its section.
    LaunchedEffect(activeRun?.sessionId, activeRun?.isStarted(), session == null) {
        if (!finishingRun && activeRun?.isStarted() == true && activeRun.session == null) {
            repository.startSession(workoutId)
        }
    }

    // The rest timer setting follows the athlete's choice, also when it changes mid-run.
    LaunchedEffect(restTimerMode, restTimerSeconds, session == null) {
        val mode = restTimerMode ?: return@LaunchedEffect
        val seconds = restTimerSeconds ?: return@LaunchedEffect
        if (session == null) return@LaunchedEffect
        repository.setSessionRestOptions(WorkoutSessionRestOptions(mode.toSessionRestMode(), seconds))
    }

    // Opening a workout while a different one is running used to replace it silently.
    if (otherRunInProgress != null && activeRun == null && !finishingRun) {
        val otherName = otherRunInProgress.workoutSnapshot.name.ifBlank { "Another workout" }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Workout in progress") },
            text = {
                Text(
                    "“$otherName” is still running. Resume it, or discard it and start " +
                        "${workout?.name?.let { "“$it”" } ?: "this workout"}. Sections already " +
                        "logged in it stay in your history.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onResumeOtherRun(otherRunInProgress.workoutId) }) {
                        Text("Resume $otherName")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (
                                    otherRunInProgress.lastLaunchedSegmentId != null &&
                                    weightLiveWorkoutViewModel.hasLiveSession
                                ) {
                                    // The open weight session belongs to the run being discarded.
                                    weightLiveWorkoutViewModel.clearDraft()
                                }
                                heartRateBle.discardComposedWorkoutRunRecording()
                                repository.clearActiveRun()
                            }
                        },
                    ) {
                        Text("Discard It and Start This One")
                    }
                    TextButton(onClick = onBack) { Text("Cancel") }
                }
            },
        )
    }

    LaunchedEffect(activeRun?.startedAtEpochSeconds, workoutId) {
        if (activeRun?.workoutId == workoutId && activeRun.isStarted()) {
            heartRateBle.startComposedWorkoutRunRecording()
            heartRateBle.tryPreferredDeviceReconnectOnce()
        }
    }

    fun launchCardioItem(item: WorkoutItem.Cardio, segmentId: String) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            scope.launch {
                snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.")
            }
            return
        }
        if (cardioLiveWorkoutViewModel.hasActiveTimer) {
            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
            onOpenCardioCategory()
            return
        }
        val timerSession = item.resolveCardioLaunch(cardioState, workout?.name) ?: run {
            scope.launch {
                snackbarHostState.showSnackbar("This cardio step is missing a valid activity or interval setup.")
            }
            return
        }
        // The workout's own notification covers the cardio timer too.
        if (!cardioLiveWorkoutViewModel.tryStartSession(timerSession, suppressNotification = true)) {
            scope.launch {
                snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
            }
            return
        }
        heartRateBle.resetWorkoutRecordingOnLiveStart()
        scope.launch {
            repository.setLastLaunchedItem(segmentId, item.id)
            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
            onOpenCardioCategory()
        }
    }

    fun launchMobilityItem(item: WorkoutItem.Mobility, segmentId: String) {
        val payload = item.resolveStretchLaunch() ?: run {
            scope.launch {
                snackbarHostState.showSnackbar("This stretch step is missing a catalog pose.")
            }
            return
        }
        scope.launch {
            repository.setLastLaunchedItem(segmentId, item.id)
            userPreferences.setProgramDashboardStretchLaunchJson(encodeStretchLaunch(payload))
            onOpenStretchCategory()
        }
    }

    /** Opens the cardio or stretching screen for [step] (saved cardio routines run there). */
    fun launchOwnScreenStep(step: WorkoutSessionStep?) {
        if (step == null) return
        when (val item = itemOf(step)) {
            is WorkoutItem.Cardio -> launchCardioItem(item, step.segmentId)
            is WorkoutItem.Mobility -> launchMobilityItem(item, step.segmentId)
            else -> Unit
        }
    }

    WeightLiveWorkoutFgsDisclosureDialog(
        visible = activeRun?.isStarted() == true && session != null && !fgsDisclosureSeen && !disclosureDismissed,
        onDismiss = { disclosureDismissed = true },
        onContinue = { scope.launch { userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true) } },
    )

    if (workout == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = ervTopAppBarColors(),
                    title = { Text("Workout") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Text("Workout not found.", modifier = Modifier.padding(padding).padding(24.dp))
        }
        return
    }

    /** A storyboard item's name as the builder gave it (no swap applied). */
    fun storyboardItemName(item: WorkoutItem): String = when (item) {
        is WorkoutItem.Weight -> item.displayExerciseName(weightState.exerciseById(item.exerciseId)?.name)
        is WorkoutItem.Cardio -> item.title?.takeIf { it.isNotBlank() }
            ?: runCatching { CardioBuiltinActivity.valueOf(item.cardio.activity) }.getOrNull()
                ?.let { cardioState.resolveSnapshot(it, null).displayLabel }
            ?: item.cardio.activity
        is WorkoutItem.Mobility -> item.title?.takeIf { it.isNotBlank() }
            ?: stretchingRepository.catalog.firstOrNull { it.id == item.mobility.catalogId }?.name
            ?: "Stretch"
        is WorkoutItem.Rest -> "Rest"
        is WorkoutItem.Note -> "Note"
    }

    val runStarted = activeRun?.isStarted() == true
    val isComplete = runStarted && plan != null && activeRun?.isSessionComplete(plan) == true

    // Finalize once the run completes, however it got there: log any lifting section still
    // waiting, stamp the whole-workout HR onto each linked log and build the finish summary.
    LaunchedEffect(isComplete) {
        if (!isComplete || runFinalized) return@LaunchedEffect
        runCatching { logFinishedSections() }
        WorkoutSessionNotifier.stop(appContext)
        val finished = repository.currentState().activeRun
        if (finished == null) {
            finalizeFailed = true
            return@LaunchedEffect
        }
        val run: WorkoutActiveRun = finished
        val wholeRunHeartRate = heartRateBle.takeComposedWorkoutRunHeartRateSummary()
        if (wholeRunHeartRate != null) {
            runCatching {
                attachComposedWorkoutHeartRateToLinkedLogs(
                    run = run,
                    heartRate = wholeRunHeartRate,
                    cardioRepository = cardioRepository,
                    weightRepository = weightRepository,
                    stretchingRepository = stretchingRepository,
                )
            }
            runCatching { attachSectionHeartRateToLiftLogs(run, wholeRunHeartRate, weightRepository) }
        }
        runCatching {
            backupComposedWorkoutSessionMedia(
                appContext = appContext,
                run = run,
                cardioRepository = cardioRepository,
                weightRepository = weightRepository,
            )
        }
        // A Quick Start workout that was only lifting is one weight workout: it gets the weight
        // screen's summary, with calories, Update Routine, Remove from Log and Open Log.
        if (run.standalone != null) {
            val logged = run.itemRecaps.filter { !it.skipped && it.linkedEntryId != null }
            val entryIds = logged.mapNotNull { it.linkedEntryId }.distinct()
            if (logged.isNotEmpty() && logged.all { it.kind == WorkoutLoggedItemKind.WEIGHT } && entryIds.size == 1) {
                val logDate = logged.first().linkedLogDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val session = logDate?.let { date ->
                    weightRepository.currentState().logFor(date)?.workouts?.firstOrNull { it.id == entryIds.single() }
                }
                if (logDate != null && session != null) {
                    weightSummary = StandaloneWeightSummary(session, logDate)
                    runFinalized = true
                    return@LaunchedEffect
                }
            }
        }
        val built = runCatching {
            buildComposedWorkoutHrSummary(
                run = run,
                wholeRun = wholeRunHeartRate,
                cardioRepository = cardioRepository,
                weightRepository = weightRepository,
                stretchingRepository = stretchingRepository,
            )
        }.getOrNull()
        if (built == null) {
            finalizeFailed = true
            return@LaunchedEffect
        }
        // What today's adjustments would change in the library copy, for "Save Changes to Workout".
        // A standalone workout has no library copy to change.
        val adjusted = run.adjustedWorkout()
        val adjustments = if (run.standalone != null) {
            emptyList()
        } else {
            runCatching { adjusted.describeAdjustmentsFrom(workout, ::storyboardItemName) }
                .getOrDefault(emptyList())
        }
        composedSummary = if (adjustments.isNotEmpty()) {
            built.copy(adjustedWorkout = adjusted, workoutAdjustments = adjustments)
        } else {
            built
        }
        runFinalized = true
    }

    // Saving should take a moment; if it hangs, offer the way out rather than a stuck screen.
    LaunchedEffect(isComplete) {
        if (!isComplete) return@LaunchedEffect
        delay(15_000L)
        if (composedSummary == null && weightSummary == null) finalizeFailed = true
    }

    // Keep the ongoing notification in step with the session. Heart rate is folded in every 15
    // seconds rather than on every beat.
    var notifiedBpm by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            notifiedBpm = heartRateBle.displayBpm.value
            delay(15_000L)
        }
    }
    LaunchedEffect(
        session,
        activeRun?.sessionStepStartedAtEpochSeconds,
        activeRun?.sessionStepAwaitingStart,
        activeRun?.sessionExerciseSwaps,
        notifiedBpm,
        fgsDisclosureSeen,
        loadUnit,
        isComplete,
    ) {
        val run = activeRun ?: return@LaunchedEffect
        if (!fgsDisclosureSeen || run.session == null || finishingRun || isComplete) return@LaunchedEffect
        WorkoutSessionNotifier.sync(appContext, run, stepNames, loadUnit, notifiedBpm)
    }

    // Moving onto a saved cardio routine opens the cardio screen, which runs those.
    LaunchedEffect(activeRun?.autoAdvanceRequested, session?.stepIndex) {
        if (activeRun?.autoAdvanceRequested != true || isComplete) return@LaunchedEffect
        repository.clearAutoAdvance()
        launchOwnScreenStep(currentStep)
    }

    // The step clock: a rest, a timed cardio leg or a stretch hold counts down from when it
    // started and then moves on by itself; an open-ended cardio leg counts up until Finish.
    var clockTick by remember { mutableIntStateOf(0) }
    val countdownEnd = if (plan != null) activeRun?.sessionCountdownEndsAtEpochSeconds(plan) else null
    val awaitingStart = activeRun?.sessionStepAwaitingStart == true
    LaunchedEffect(session?.stepIndex, activeRun?.sessionStepStartedAtEpochSeconds, awaitingStart, clockTick) {
        val step = currentStep ?: return@LaunchedEffect
        val clocked = step is WorkoutSessionStep.Rest || step is WorkoutSessionStep.StretchHold ||
            (step is WorkoutSessionStep.CardioLeg && step.cardioRoutineId == null)
        if (!clocked || awaitingStart) return@LaunchedEffect
        val end = countdownEnd
        val now = nowWorkoutEpochSeconds()
        if (end != null && now >= end) {
            // One that ended while ERV was in the background already alerted from the alarm.
            if (restEndCueIsFresh(end, now)) {
                // The rest timer's sound setting covers rests; holds and legs always cue.
                if (step !is WorkoutSessionStep.Rest || restEndSound) playHiitWorkSegmentEndCue(appContext)
                RestTimerAlarm.vibrateRestOver(appContext)
            }
            send(WorkoutSessionEvent.TimedStepDone(step.countdownSeconds() ?: 0))
            return@LaunchedEffect
        }
        // One beep a second over the last five seconds of a rest.
        if (end != null && step is WorkoutSessionStep.Rest && restCountdownSound && (end - now) in 1..5) {
            playHiitWorkCountdownTickCue(appContext)
        }
        delay(1_000L)
        clockTick++
    }

    /** Seconds the step on screen has run, not counting pauses. */
    fun stepElapsedSeconds(): Int = activeRun?.sessionStepElapsedSeconds(nowWorkoutEpochSeconds()) ?: 0
    val sessionPaused = activeRun?.activeSessionPause() != null

    // GPS for outdoor cardio legs run here: on while a leg of the item runs (recovery legs too),
    // off between items. The route is taken when the item is logged.
    val cardioGpsPreferred by userPreferences.cardioGpsRecordingPreferred.collectAsState(initial = true)
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = it
    }
    // Items the athlete turned GPS off for (a treadmill ruck in a workout, say).
    var gpsOffItemIds by remember { mutableStateOf(setOf<String>()) }
    val gpsLeg = (currentStep as? WorkoutSessionStep.CardioLeg)?.takeIf { it.cardioRoutineId == null }
    val gpsItem = gpsLeg?.let { itemOf(it) as? WorkoutItem.Cardio }
        ?.takeIf { isSessionCardioActivity(it.cardio.activity) }
    val gpsActivity = gpsItem?.let { item ->
        runCatching { CardioBuiltinActivity.valueOf(item.cardio.activity) }.getOrNull()
            ?.let { cardioState.resolveSnapshot(it, null) }
    }
    // A run started from Cardio indoors (treadmill, trainer) records no route.
    val gpsIndoor = gpsItem != null &&
        activeRun?.standaloneCardioDraftFor(gpsItem.id)?.let { it.modality != CardioModality.OUTDOOR } == true
    val gpsAvailable = gpsItem != null && cardioGpsPreferred && !gpsIndoor && WorkoutSessionGps.supports(gpsActivity)
    val gpsWanted = gpsAvailable && gpsItem?.id !in gpsOffItemIds
    val gpsKey = if (gpsLeg != null && gpsItem != null && activeRun != null) {
        WorkoutSessionGps.keyFor(activeRun.sessionId, gpsLeg.segmentId, gpsItem.id)
    } else {
        null
    }
    val recordingGpsKey = gpsKey?.takeIf { gpsWanted && locationGranted && !awaitingStart && !isComplete }
    LaunchedEffect(recordingGpsKey) {
        val key = recordingGpsKey
        if (key != null) {
            WorkoutSessionGps.start(appContext, gpsItem?.title?.takeIf { it.isNotBlank() } ?: "Cardio", key)
        } else if (!cardioLiveWorkoutViewModel.hasActiveTimer) {
            // Between items: stop, keeping the points for when the item is logged.
            WorkoutSessionGps.stopIfRecording(appContext)
        }
    }
    // A paused leg drops GPS fixes, so a stop adds no route or distance.
    LaunchedEffect(sessionPaused, recordingGpsKey) {
        if (recordingGpsKey != null) CardioGpsRecordingHub.paused = sessionPaused
    }
    val hubPoints by CardioGpsRecordingHub.pointsFlow.collectAsState()
    val liveRoute = if (recordingGpsKey != null && CardioGpsRecordingHub.sessionKey() == recordingGpsKey) hubPoints else null
    val liveGpsLine = liveRoute?.let { sessionGpsLiveLine(it, distanceUnit) }
    val liveCardioStats = liveRoute?.let { sessionCardioLiveStats(it, distanceUnit, sessionSplitMeters) }.orEmpty()

    /** Start on a cardio or stretch step; a new cardio item starts its own heart rate summary. */
    fun startStep(step: WorkoutSessionStep) {
        if (step is WorkoutSessionStep.CardioLeg) {
            heartRateBle.resetWorkoutRecordingOnLiveStart()
            concept2Ble.resetWorkoutRecordingOnLiveStart()
            cyclingCscBle.resetWorkoutRecordingOnLiveStart()
        }
        val atStep = session?.stepIndex
        scope.launch { repository.startSessionStep(atStepIndex = atStep) }
    }

    // Cardio's Start already said go: its run starts the cardio leg straight away, once.
    var cardioAutoStarted by rememberSaveable(workoutId) { mutableStateOf(false) }
    LaunchedEffect(workoutId, awaitingStart, currentStep) {
        val step = currentStep as? WorkoutSessionStep.CardioLeg ?: return@LaunchedEffect
        val run = activeRun ?: return@LaunchedEffect
        if (cardioAutoStarted || !awaitingStart || run.workoutId != workoutId) return@LaunchedEffect
        if (run.standaloneCardioDraftFor(step.itemId) == null || session?.records?.isNotEmpty() == true) return@LaunchedEffect
        cardioAutoStarted = true
        startStep(step)
    }

    weightSummary?.let { finished ->
        val summaryKeyManager = LocalKeyManager.current
        val darkTheme = isSystemInDarkTheme()

        /** Clears the finished run, does [first] (still on this screen), then [then]. */
        fun leave(first: suspend () -> Unit = {}, then: () -> Unit) {
            finishingRun = true
            scope.launch {
                heartRateBle.discardComposedWorkoutRunRecording()
                repository.clearActiveRun()
                first()
                then()
            }
        }
        WeightWorkoutSummaryFullScreen(
            session = finished.session,
            logDate = finished.logDate,
            library = weightState,
            loadUnit = loadUnit,
            userPreferences = userPreferences,
            dark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark,
            mid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid,
            glow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow,
            relayPool = relayPool,
            signer = signer,
            repository = weightRepository,
            onAfterRoutineSync = {
                val pool = relayPool
                val s = signer
                if (pool != null && s != null) {
                    scope.launch {
                        WeightSync.publishRoutines(
                            appContext, pool, s, weightRepository.currentState().routines,
                            summaryKeyManager.relayUrlsForKind30078Publish(),
                        )
                    }
                }
            },
            // The weight log queues the day's sync itself.
            onRemoveFromLog = {
                leave(first = { weightRepository.deleteWorkout(finished.logDate, finished.session.id) }) { onBack() }
            },
            onOpenLog = { leave { onOpenWeightLog() } },
            onDone = { leave { onBack() } },
        )
        return
    }

    val summary = composedSummary
    if (summary != null) {
        val summaryKeyManager = LocalKeyManager.current
        // Only offered while the workout is still in the library and today changed a target.
        val canSaveTodaysWeights = summary.weightTargetChanges.isNotEmpty() && !isStandalone &&
            summary.workoutId != null && workout.id == summary.workoutId
        val adjustedWorkout = summary.adjustedWorkout
            ?.takeIf { summary.workoutId != null && workout.id == summary.workoutId }

        /** Applies [update] to the library copy and publishes it; false when the workout is gone. */
        suspend fun updateLibraryWorkout(update: (Workout) -> Workout): Boolean {
            val current = repository.currentState().workouts.firstOrNull { it.id == summary.workoutId }
                ?: return false
            val updated = update(current)
            if (updated != current) {
                repository.upsertWorkout(updated)
                WorkoutSync.publishLibraryIfSignedIn(
                    appContext = appContext,
                    relayPool = relayPool,
                    signer = signer,
                    state = repository.currentState(),
                    dataRelayUrls = summaryKeyManager.relayUrlsForKind30078Publish(),
                )
            }
            return true
        }
        ComposedWorkoutSummaryScreen(
            summary = summary,
            weightState = weightState,
            loadUnit = loadUnit,
            distanceUnit = distanceUnit,
            zoneInputs = heartRateZoneInputs,
            relayPool = relayPool,
            signer = signer,
            onSaveTodaysWeights = if (canSaveTodaysWeights) {
                { updateLibraryWorkout { it.withTodaysWeights(summary.weightTargetChanges) } }
            } else {
                null
            },
            onSaveWorkoutChanges = adjustedWorkout?.let { adjusted ->
                val save: suspend (Boolean) -> Boolean = { alsoTodaysWeights ->
                    updateLibraryWorkout { current ->
                        val changed = current.copy(segments = adjusted.segments)
                        // Saving the changes replaces the sections, so weights already saved
                        // as targets are written again on top.
                        if (alsoTodaysWeights) changed.withTodaysWeights(summary.weightTargetChanges) else changed
                    }
                }
                save
            },
            onDone = {
                finishingRun = true
                scope.launch {
                    heartRateBle.discardComposedWorkoutRunRecording()
                    repository.clearActiveRun()
                    onBack()
                }
            },
        )
        return
    }

    fun discardRunAndLeave() {
        finishingRun = true
        if (!cardioLiveWorkoutViewModel.hasActiveTimer) WorkoutSessionGps.stopIfRecording(appContext)
        scope.launch {
            heartRateBle.discardComposedWorkoutRunRecording()
            repository.clearActiveRun()
            onBack()
        }
    }

    // A section open on the cardio or stretch screen has to be finished or discarded there.
    val ownScreenSectionOpen = cardioLiveWorkoutViewModel.hasActiveTimer && activeRun?.lastLaunchedItemId != null

    // A run under way is never thrown away by a stray back tap: the arrow and the system back
    // gesture both ask first. Backing out within a minute of opening, before anything is done,
    // is a quick look and just leaves.
    fun requestLeave() {
        val quickLook = activeRun?.isFreshWithNothingLogged() != false &&
            activeRun?.sessionHasNothingDone() != false &&
            !ownScreenSectionOpen
        when {
            runStarted && !isComplete && !quickLook -> showLeaveDialog = true
            // Nothing of this workout's is running (another run is waiting): leave it alone.
            activeRun == null -> onBack()
            else -> discardRunAndLeave()
        }
    }

    BackHandler(enabled = runStarted && !isComplete) { requestLeave() }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave this workout?") },
            text = {
                Text(
                    buildString {
                        append("Keep Running leaves it in progress; open it again from Training to continue. ")
                        if (ownScreenSectionOpen) {
                            append("To end it now, finish or discard the open section first. ")
                        } else {
                            append("End Workout Now logs what you've done, marks the rest as skipped and shows the summary. ")
                        }
                        append("Discard stops it without a summary; sections already logged stay in your history.")
                    },
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            showLeaveDialog = false
                            onBack()
                        },
                    ) {
                        Text("Keep Running")
                    }
                    if (!ownScreenSectionOpen) {
                        TextButton(
                            onClick = {
                                showLeaveDialog = false
                                scope.launch {
                                    repository.endRunEarly()
                                    logFinishedSections()
                                }
                            },
                        ) {
                            Text("End Workout Now")
                        }
                    }
                    TextButton(
                        onClick = {
                            showLeaveDialog = false
                            discardRunAndLeave()
                        },
                    ) {
                        Text("Discard Workout", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmFinish) {
        val nothingDone = activeRun?.sessionHasNothingDone() != false
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(if (nothingDone) "Nothing Logged Yet" else "Finish Workout?") },
            text = {
                Text(
                    if (nothingDone) {
                        "No sets are logged, so there's nothing to save. Discard this workout?"
                    } else {
                        "Logs the sets you've done and shows the summary. Sets not done yet are left out."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFinish = false
                        if (nothingDone) {
                            discardRunAndLeave()
                        } else {
                            scope.launch {
                                repository.endRunEarly()
                                logFinishedSections()
                            }
                        }
                    },
                ) {
                    if (nothingDone) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Finish")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep Going") } },
        )
    }

    holdTimer?.let { timer ->
        WeightTimedSetTimerOverlay(
            exerciseName = timer.exerciseName,
            goalSeconds = timer.goalSeconds,
            prepSeconds = timer.prepSeconds,
            countdownBeeps = timer.countdownBeeps,
            onFinished = { seconds ->
                holdTimer = null
                val set = timer.base.copy(reps = 0, durationSeconds = seconds.coerceAtLeast(1), done = null)
                scope.launch {
                    repository.applySessionEvent(WorkoutSessionEvent.SetLogged(set), atStepIndex = timer.stepIndex)
                    logFinishedSections()
                }
            },
            onDismiss = { holdTimer = null },
        )
    }

    intervalTimer?.let { timer ->
        WeightHiitIntervalTimerOverlay(
            exerciseName = timer.exerciseName,
            plan = timer.plan,
            onFinished = { block ->
                intervalTimer = null
                scope.launch {
                    if (session?.stepIndex == timer.stepIndex) repository.logSessionHiitBlock(block)
                    logFinishedSections()
                }
            },
            onDismiss = { intervalTimer = null },
        )
    }

    if (showRestSettings) {
        WorkoutSessionRestSettingsDialog(
            initialMode = (restTimerMode ?: WeightLiveRestTimerMode.AUTO).toSessionRestMode(),
            initialSeconds = restTimerSeconds ?: 90,
            initialCountdownSound = restCountdownSound,
            initialEndSound = restEndSound,
            initialRpeLogsSet = rpeLogsSet,
            onDismiss = { showRestSettings = false },
            onSave = { mode, seconds, countdown, end, rpeLogs ->
                showRestSettings = false
                // Saving the setting is enough: the run follows it (see the effect above).
                scope.launch {
                    userPreferences.setWorkoutSessionRestTimerMode(mode.toSavedRestMode())
                    userPreferences.setWeightLiveRestTimerSeconds(seconds)
                    userPreferences.setWeightLiveRestTimerCountdownSoundEnabled(countdown)
                    userPreferences.setWeightLiveRestTimerEndSoundEnabled(end)
                    userPreferences.setWorkoutSessionRpeLogsSet(rpeLogs)
                }
            },
        )
    }

    val runWorkout = activeRun?.workoutSnapshot ?: workout
    fun runItem(segmentId: String, itemId: String): WorkoutItem? =
        runWorkout.segments.firstOrNull { it.id == segmentId }?.items?.firstOrNull { it.id == itemId }
    fun todaysExerciseOf(segmentId: String, item: WorkoutItem.Weight): String =
        activeRun?.sessionExerciseSwaps?.get(sessionItemKey(segmentId, item.id)) ?: item.exerciseId

    swapItem?.let { (swapSegmentId, swapItemId) ->
        val item = runItem(swapSegmentId, swapItemId) as? WorkoutItem.Weight
        if (item == null) {
            swapItem = null
        } else {
            val options = (listOf(item.exerciseId) + item.alternativeExerciseIds)
                .distinct()
                .map { id -> id to (weightState.exerciseById(id)?.name ?: id) }
            WorkoutSessionSwapDialog(
                options = options,
                currentExerciseId = todaysExerciseOf(swapSegmentId, item),
                onDismiss = { swapItem = null },
                onPick = { exerciseId ->
                    swapItem = null
                    scope.launch { repository.swapSessionExercise(swapSegmentId, swapItemId, exerciseId) }
                },
                onChooseFromLibrary = {
                    swapItem = null
                    swapFromLibrary = swapSegmentId to swapItemId
                },
            )
        }
    }

    // The run screen redraws every second; work out the recent list only when the logs change.
    val recentExerciseIds = remember(weightState.logs, weightState.exercises) { weightState.recentExerciseIds() }

    swapFromLibrary?.let { (swapSegmentId, swapItemId) ->
        val item = runItem(swapSegmentId, swapItemId) as? WorkoutItem.Weight
        WeightPickExerciseDialog(
            exercises = weightState.exercises,
            excludeIds = setOfNotNull(item?.let { todaysExerciseOf(swapSegmentId, it) }),
            onDismiss = { swapFromLibrary = null },
            recentExerciseIds = recentExerciseIds,
            onPick = { exerciseId ->
                swapFromLibrary = null
                scope.launch { repository.swapSessionExercise(swapSegmentId, swapItemId, exerciseId) }
            },
            onCreateNew = { typed ->
                swapFromLibrary = null
                createExercise = typed to (swapSegmentId to swapItemId)
            },
        )
    }

    createExercise?.let { (typedName, swapTarget) ->
        WeightExerciseEditorDialog(
            initial = null,
            title = "New Exercise",
            availableMuscleGroups = weightState.exercises.map { it.muscleGroup },
            initialName = typedName,
            onDismiss = { createExercise = null },
            onSave = { exercise ->
                createExercise = null
                scope.launch {
                    weightRepository.upsertExercise(exercise)
                    if (swapTarget != null) {
                        repository.swapSessionExercise(swapTarget.first, swapTarget.second, exercise.id)
                    } else {
                        repository.addSessionItem(WorkoutSessionAddition.Lift(exercise.id, exercise))
                    }
                }
                // Publish the library in the background; never hold the screen on relays.
                if (relayPool != null && signer != null) {
                    val urls = runKeyManager.relayUrlsForKind30078Publish()
                    scope.launch {
                        runCatching {
                            WeightSync.publishExercises(
                                appContext,
                                relayPool,
                                signer,
                                weightRepository.currentState().exercises,
                                urls,
                            )
                        }
                    }
                }
            },
        )
    }

    when (addKind) {
        WorkoutSessionAddKind.EXERCISE -> WeightPickExerciseDialog(
            exercises = weightState.exercises,
            excludeIds = emptySet(),
            onDismiss = { addKind = null },
            recentExerciseIds = recentExerciseIds,
            onPickMany = { exerciseIds ->
                addKind = null
                val additions = exerciseIds.map {
                    WorkoutSessionAddition.Lift(it, weightState.exerciseById(it), lastSetCount = lastSetCountFor(it))
                }
                scope.launch { repository.addSessionItems(additions) }
            },
            onCreateNew = { typed ->
                addKind = null
                createExercise = typed to null
            },
        )
        WorkoutSessionAddKind.CARDIO -> WorkoutSessionAddCardioDialog(
            activities = cardioBuiltinActivitiesForUserSelection()
                .filter { isSessionCardioActivity(it.name) }
                .map { it.name to cardioState.resolveSnapshot(it, null).displayLabel },
            onDismiss = { addKind = null },
            onAdd = { activity, minutes ->
                addKind = null
                scope.launch { repository.addSessionItem(WorkoutSessionAddition.Cardio(activity, minutes)) }
            },
        )
        WorkoutSessionAddKind.STRETCH -> WorkoutSessionAddStretchDialog(
            catalog = stretchingRepository.catalog,
            onDismiss = { addKind = null },
            onAdd = { entry, holdSeconds ->
                addKind = null
                val addition = WorkoutSessionAddition.Stretch(entry.id, holdSeconds, entry.requiresBothSides)
                scope.launch { repository.addSessionItem(addition) }
            },
        )
        null -> Unit
    }

    removeTarget?.let { (removeSegmentId, removeItemId, name) ->
        WorkoutSessionRemoveItemDialog(
            name = name,
            onDismiss = { removeTarget = null },
            onRemove = {
                removeTarget = null
                scope.launch { repository.removeSessionItem(removeSegmentId, removeItemId) }
            },
        )
    }

    val editing = editingSet
    if (editing != null && plan != null) {
        val editStep = plan.steps.getOrNull(editing.stepIndex) as? WorkoutSessionStep.LiftSet
        WorkoutSessionEditSetDialog(
            title = editStep?.let { liftName(it) } ?: "Edit set",
            logged = editing,
            loadUnit = loadUnit,
            onDismiss = { editingSet = null },
            onSave = { set ->
                editingSet = null
                scope.launch { repository.applySessionEvent(WorkoutSessionEvent.EditSet(editing.stepIndex, set)) }
            },
            onRemove = if (activeRun?.canRemoveSessionSetAt(editing.stepIndex, plan) == true) {
                {
                    editingSet = null
                    scope.launch { repository.removeSessionSet(editing.stepIndex) }
                }
            } else {
                null
            },
        )
    }

    historyFor?.let { (exerciseId, name) ->
        WeightExerciseRecentWorkoutsDialog(
            exerciseId = exerciseId,
            exerciseName = name,
            library = weightState,
            loadUnit = loadUnit,
            onDismiss = { historyFor = null },
        )
    }

    /** The sets logged so far for one storyboard item, labelled "Set 2" or "Round 2". */
    fun loggedSetsOf(segmentId: String, itemId: String): List<Pair<String, WorkoutSessionLoggedSet>> {
        if (plan == null || session == null) return emptyList()
        return plan.steps.indices.mapNotNull { index ->
            val step = plan.steps[index] as? WorkoutSessionStep.LiftSet ?: return@mapNotNull null
            if (step.segmentId != segmentId || step.itemId != itemId) return@mapNotNull null
            val set = session.recordFor(index)?.takeIf { it.outcome == WorkoutStepOutcome.DONE }?.set
                ?: return@mapNotNull null
            val label = if (step.inRounds) "Round ${step.round}" else "Set ${step.setIndex + 1}"
            label to WorkoutSessionLoggedSet(index, set)
        }
    }

    /** Done, skipped, or null while some of the item is still to do. */
    fun itemStatus(segmentId: String, itemId: String): String? {
        if (plan == null || session == null) return null
        val records = plan.steps.indices
            .filter { plan.steps[it].segmentId == segmentId && plan.steps[it].itemId == itemId }
            .map { session.recordFor(it) }
        if (records.isEmpty() || records.any { it == null }) return null
        return if (records.any { it?.outcome == WorkoutStepOutcome.DONE }) "done" else "skipped"
    }

    Box(modifier = modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (mediaControlsShown) {
                Surface(tonalElevation = 3.dp, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    WorkoutMediaControlPanel(
                        showHeaderTitle = false,
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(workout.name) },
                navigationIcon = {
                    IconButton(onClick = { requestLeave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    HeartRatePill(
                        viewModel = heartRateBle,
                        zoneInputs = heartRateZoneInputs,
                        contentColor = Color.White,
                    )
                    IconButton(onClick = { mediaControlsShown = !mediaControlsShown }) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = stringResource(R.string.media_control_cd_music),
                            tint = Color.White.copy(alpha = if (mediaControlsShown) 1f else 0.7f),
                        )
                    }
                    if (runStarted && !isComplete) {
                        IconButton(onClick = { showRestSettings = true }) {
                            Icon(Icons.Filled.Timer, contentDescription = "Rest timer", tint = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    // A standalone workout ends when you say so; a library one ends with its last step.
                    if (isStandalone && runStarted && !isComplete) {
                        TextButton(onClick = { confirmFinish = true }) {
                            Text("Finish", color = Color.White)
                        }
                    }
                },
                colors = ervSessionTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        // A rest is a bar over the workout, not a screen of its own: the exercise stays in view.
        val restStep = currentStep as? WorkoutSessionStep.Rest
        if (runStarted && !isComplete && restStep != null) {
            val remaining = countdownEnd?.let { (it - nowWorkoutEpochSeconds()).toInt() } ?: restStep.seconds
            WorkoutSessionRestBar(
                title = if (restStep.kind == WorkoutSessionRestKind.AFTER_SECTION) "Rest Before the Next Section" else "Rest",
                secondsRemaining = remaining,
                totalSeconds = restStep.seconds,
                nextLabel = plan?.let { activeRun?.sessionUpNextLabel(stepNames, loadUnit, it) },
                onSkip = { send(WorkoutSessionEvent.StepDone) },
                onAddTime = { scope.launch { repository.extendSessionCountdown(30) } },
                awaitingStart = awaitingStart,
                onStart = { startStep(restStep) },
            )
        }
        LazyColumn(
            state = runListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val progress = if (plan != null && session != null) sessionProgressLabel(plan, session) else null
                val sectionTitle = currentStep?.let { runWorkout.segments.getOrNull(it.segmentIndex)?.displayTitle() }
                Text(
                    text = when {
                        !runStarted || session == null -> "Starting…"
                        isComplete && !finalizeFailed -> "Workout complete. Saving…"
                        isComplete -> "Workout complete"
                        else -> listOfNotNull(sectionTitle, progress).joinToString(" · ").ifBlank { "Ready" }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            val step = currentStep
            if (runStarted && !isComplete && step != null && step !is WorkoutSessionStep.LiftSet && step !is WorkoutSessionStep.Rest) {
                item(key = "step-${session?.stepIndex}") {
                    when (step) {
                        // Lifts and the rests between them are drawn by the exercise card and the rest bar.
                        is WorkoutSessionStep.LiftSet, is WorkoutSessionStep.Rest -> Unit
                        is WorkoutSessionStep.Note -> WorkoutSessionNoteCard(
                            text = step.text,
                            onContinue = { send(WorkoutSessionEvent.StepDone) },
                        )
                        is WorkoutSessionStep.CardioLeg, is WorkoutSessionStep.StretchHold -> {
                            val item = itemOf(step)
                            val remaining = countdownEnd?.let { (it - nowWorkoutEpochSeconds()).toInt().coerceAtLeast(0) }
                            when {
                                step is WorkoutSessionStep.CardioLeg && item is WorkoutItem.Cardio &&
                                    step.cardioRoutineId == null && isSessionCardioActivity(item.cardio.activity) -> {
                                    val activitySnapshot = runCatching { CardioBuiltinActivity.valueOf(item.cardio.activity) }
                                        .getOrNull()?.let { cardioState.resolveSnapshot(it, null) }
                                    val activityLabel = activitySnapshot?.displayLabel
                                    WorkoutSessionCardioCard(
                                        step = step,
                                        title = item.title?.takeIf { it.isNotBlank() } ?: activityLabel ?: "Cardio",
                                        heartRateTarget = cardioHeartRateTarget(item, step),
                                        awaitingStart = awaitingStart,
                                        clockSeconds = if ((step.targetSeconds ?: 0) > 0) {
                                            // From the step's own clock, so it holds still while paused.
                                            ((step.targetSeconds ?: 0) - stepElapsedSeconds()).coerceAtLeast(0)
                                        } else {
                                            stepElapsedSeconds()
                                        },
                                        paused = sessionPaused,
                                        onPauseChange = { pause -> scope.launch { repository.setSessionStepPaused(pause) } },
                                        liveStats = liveCardioStats,
                                        onStart = { startStep(step) },
                                        onFinish = { send(WorkoutSessionEvent.TimedStepDone(stepElapsedSeconds())) },
                                        onSkipLeg = { send(WorkoutSessionEvent.SkipStep) },
                                        onSkipCardio = { pendingSkipLabel = item.title?.takeIf { it.isNotBlank() } ?: "this cardio" },
                                        onOpenCardioScreen = { launchCardioItem(item, step.segmentId) },
                                        gpsStatus = when {
                                            !gpsWanted || awaitingStart -> null
                                            !locationGranted -> "GPS off: location not allowed"
                                            liveGpsLine == null -> "GPS: waiting for a fix"
                                            else -> null
                                        },
                                        gpsControls = {
                                            if (gpsAvailable) {
                                                SessionGpsControls(
                                                    on = gpsWanted,
                                                    locationGranted = locationGranted,
                                                    onToggle = { on ->
                                                        gpsOffItemIds = if (on) gpsOffItemIds - item.id else gpsOffItemIds + item.id
                                                    },
                                                    onAllowLocation = {
                                                        requestLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                                    },
                                                )
                                            }
                                        },
                                        sensors = {
                                            if (activitySnapshot?.supportsBikeErgSensorConnect() == true) {
                                                CardioBikeErgConnectInlineSection(
                                                    activitySupportsErg = true,
                                                    sessionKey = item.id,
                                                    compact = true,
                                                    cyclingSensorApplicable = activitySnapshot.isCyclingActivity(),
                                                )
                                                if (!awaitingStart) {
                                                    SessionSensorReadout(concept2Ble, cyclingCscBle, distanceUnit)
                                                }
                                            }
                                        },
                                    )
                                }
                                step is WorkoutSessionStep.StretchHold && item is WorkoutItem.Mobility -> {
                                    val entry = stretchingRepository.catalog.firstOrNull { it.id == step.catalogId }
                                    WorkoutSessionStretchCard(
                                        step = step,
                                        name = stepNames.nameFor(step) ?: entry?.name ?: "Stretch",
                                        procedure = entry?.procedure,
                                        awaitingStart = awaitingStart,
                                        secondsLeft = remaining ?: step.holdSeconds,
                                        onStart = { startStep(step) },
                                        onDone = { send(WorkoutSessionEvent.TimedStepDone(stepElapsedSeconds())) },
                                        onSkipHold = { send(WorkoutSessionEvent.SkipStep) },
                                        onSkipStretch = { pendingSkipLabel = stepNames.nameFor(step) ?: "this stretch" },
                                    )
                                }
                                // A saved cardio routine, or an activity only the cardio screen knows.
                                item is WorkoutItem.Cardio -> OwnScreenCardioCard(
                                    item = item,
                                    cardioState = cardioState,
                                    onStart = { launchCardioItem(item, step.segmentId) },
                                    onSkip = { pendingSkipLabel = item.title?.takeIf { it.isNotBlank() } ?: "this cardio" },
                                )
                                item is WorkoutItem.Mobility -> OwnScreenStretchCard(
                                    item = item,
                                    onStart = { launchMobilityItem(item, step.segmentId) },
                                    onSkip = { pendingSkipLabel = item.title?.takeIf { it.isNotBlank() } ?: "this stretch" },
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
            // The exercise on screen, with all its sets; it stays put through the rests after them.
            // Swiping (or the arrows, or a storyboard tap) shows another exercise instead.
            val viewingOther = shownLiftKey != focusLiftKey
            val shownIndex = when {
                shownLiftKey == null -> null
                !viewingOther -> focusLiftIndex
                else -> plan?.steps?.indexOfFirst {
                    it is WorkoutSessionStep.LiftSet && it.segmentId == shownLiftKey.first && it.itemId == shownLiftKey.second
                }?.takeIf { it >= 0 }
            }
            val focusStep = shownIndex?.let { plan?.steps?.getOrNull(it) } as? WorkoutSessionStep.LiftSet
            if (runStarted && !isComplete && focusStep != null && shownIndex != null && plan != null && session != null) {
                item(key = "lift-card") {
                    val run = activeRun
                    val item = itemOf(focusStep) as? WorkoutItem.Weight
                    val onLift = step is WorkoutSessionStep.LiftSet && !viewingOther
                    val restIndex = session.stepIndex.takeIf { step is WorkoutSessionStep.Rest }
                    // The set logged now: the one on screen, or the one right after the rest on screen.
                    val nextIndex = when {
                        viewingOther -> null
                        onLift -> session.stepIndex
                        restIndex != null -> (restIndex + 1).takeIf { index ->
                            val after = plan.steps.getOrNull(index) as? WorkoutSessionStep.LiftSet
                            after != null && after.segmentId == focusStep.segmentId && after.itemId == focusStep.itemId
                        }
                        else -> null
                    }
                    val position = liftItemKeys.indexOf(focusStep.segmentId to focusStep.itemId)
                    val rows = plan.steps.indices.mapNotNull { index ->
                        val other = plan.steps[index] as? WorkoutSessionStep.LiftSet ?: return@mapNotNull null
                        if (other.segmentId != focusStep.segmentId || other.itemId != focusStep.itemId) return@mapNotNull null
                        val record = session.recordFor(index)
                        WorkoutSessionSetRow(
                            stepIndex = index,
                            step = other,
                            logged = record?.takeIf { it.outcome == WorkoutStepOutcome.DONE }?.set,
                            skipped = record?.outcome == WorkoutStepOutcome.SKIPPED,
                            isNext = index == nextIndex,
                            canRemove = run?.canRemoveSessionSetAt(index, plan) == true,
                        )
                    }
                    val segment = runWorkout.segments.getOrNull(focusStep.segmentIndex)
                    val exerciseId = run?.sessionExerciseFor(focusStep) ?: focusStep.exerciseId
                    val exercise = weightState.exerciseById(exerciseId)
                    val lastLog = weightState.historyForExercise(exerciseId)
                        .firstOrNull { it.workout.workoutLink?.sessionId != run?.sessionId }
                    val liftStep = (step as? WorkoutSessionStep.LiftSet)?.takeIf { !viewingOther }
                    val goalSeconds = liftStep?.target?.takeIf { (it.targetReps ?: 0) <= 0 }?.targetDurationSeconds?.takeIf { it > 0 }
                    val fromBlock = run?.fromTrainingBlock == true && liftStep != null
                    WorkoutSessionExerciseCard(
                        exerciseName = liftName(focusStep),
                        sectionTitle = segment?.takeIf { focusStep.inRounds }?.displayTitle(),
                        loadUnit = loadUnit,
                        rows = rows,
                        onLog = { set ->
                            if (restIndex != null) {
                                scope.launch {
                                    repository.logSessionSetOverRest(restIndex, set)
                                    logFinishedSections()
                                }
                            } else {
                                send(WorkoutSessionEvent.SetLogged(set))
                            }
                        },
                        onEditSet = { index, set ->
                            scope.launch { repository.applySessionEvent(WorkoutSessionEvent.EditSet(index, set)) }
                        },
                        onRemoveSet = { index -> scope.launch { repository.removeSessionSet(index) } },
                        canSwap = item != null && rows.none { it.logged != null },
                        onSwap = { swapItem = focusStep.segmentId to focusStep.itemId },
                        lastTime = lastLog?.let { describeLastTime(it, loadUnit, LocalDate.now()) },
                        lastTimeSets = lastLog?.entry?.sets.orEmpty(),
                        coachTarget = coachReview?.coachLineFor(exerciseId, System.currentTimeMillis() / 1000),
                        onShowHistory = { historyFor = exerciseId to liftName(focusStep) },
                        canAddSet = run?.canAddSessionSet(plan, shownIndex) == true,
                        onAddSet = {
                            val atStep = session.stepIndex
                            scope.launch { repository.changeSessionSetCount(1, atStepIndex = atStep, liftStepIndex = shownIndex) }
                        },
                        // In a superset or circuit a set is a round of the whole section.
                        onRemoveRound = if (focusStep.inRounds && onLift && run?.canRemoveSessionSet(plan) == true) {
                            { changeSetCount(-1) }
                        } else {
                            null
                        },
                        onSkipSet = if (fromBlock) ({ send(WorkoutSessionEvent.SkipStep) }) else null,
                        onSkipExercise = if (fromBlock) ({ pendingSkipLabel = liftName(focusStep) }) else null,
                        equipmentLabel = exercise?.equipment?.displayLabel(),
                        plateCalculator = plateCalculatorFor(exercise),
                        onStartTimer = if (goalSeconds != null && liftStep != null) {
                            { base ->
                                holdTimer = SessionHoldTimer(
                                    stepIndex = session.stepIndex,
                                    exerciseName = liftName(liftStep),
                                    goalSeconds = goalSeconds,
                                    // The workout's get-ready time, else the weight screen's for a hold.
                                    prepSeconds = item?.prescription?.timedPrepSeconds
                                        ?: if (exercise?.setLoggingStyle() == WeightSetLoggingStyle.TIME_ONLY) DEFAULT_TIMED_PREP_SECONDS else 0,
                                    countdownBeeps = exercise?.usesTimedHoldCountdownBeeps() == true,
                                    base = base,
                                )
                            }
                        } else {
                            null
                        },
                        onStartIntervals = if (
                            exercise?.hiitCapable == true && liftStep != null &&
                            run?.canLogSessionHiitBlock(plan) == true
                        ) {
                            { intervals ->
                                intervalTimer = SessionIntervalTimer(session.stepIndex, liftName(liftStep), intervals)
                            }
                        } else {
                            null
                        },
                        rpeLogsSet = rpeLogsSet,
                        onPrevious = liftItemKeys.getOrNull(position - 1)?.let { key -> { viewedLiftKey = key } },
                        onNext = liftItemKeys.getOrNull(position + 1)?.takeIf { position >= 0 }?.let { key -> { viewedLiftKey = key } },
                        positionLabel = if (liftItemKeys.size > 1 && position >= 0) "Exercise ${position + 1} of ${liftItemKeys.size}" else null,
                        onBackToCurrent = if (viewingOther) ({ viewedLiftKey = null }) else null,
                    )
                }
            }
            // A standalone workout that has done everything so far waits here for more, or Finish.
            if (runStarted && !isComplete && step == null && plan != null && activeRun?.isSessionWaitingForMore(plan) == true) {
                item(key = "standalone-next") {
                    WorkoutSessionStandaloneNextCard(
                        empty = plan.size == 0,
                        onAddExercise = { addKind = WorkoutSessionAddKind.EXERCISE },
                        onFinish = { confirmFinish = true },
                    )
                }
            }
            // Today's run can be changed while it goes: swap, add, remove and reorder. None of it
            // touches the saved workout unless the athlete saves the changes at the end.
            val editRun = activeRun?.takeIf { runStarted && !isComplete && plan != null && session != null }
            val editPlan = plan
            item {
                FormSectionLabelSmall("Storyboard")
            }
            if (editRun != null) {
                item(key = "add-to-workout") {
                    WorkoutSessionAddButton(onPick = { addKind = it })
                }
            }
            // The run's own copy, so sets added or taken away today show here too.
            val storyboard = activeRun?.workoutSnapshot?.takeIf { runStarted } ?: workout
            itemsIndexed(storyboard.segments, key = { _, segment -> segment.id }) { index, segment ->
                val isCurrent = runStarted && !isComplete && currentStep?.segmentIndex == index
                val isRounds = segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = segment.displayTitle() + if (isCurrent) " · now" else "",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (editRun != null && editPlan != null) {
                                WorkoutSessionRowMenu(
                                    contentDescription = "Section options",
                                    actions = listOf(
                                        WorkoutSessionMenuAction(
                                            "Move Section Up",
                                            editRun.canMoveSessionSection(segment.id, -1, editPlan),
                                        ) { scope.launch { repository.moveSessionSection(segment.id, -1) } },
                                        WorkoutSessionMenuAction(
                                            "Move Section Down",
                                            editRun.canMoveSessionSection(segment.id, 1, editPlan),
                                        ) { scope.launch { repository.moveSessionSection(segment.id, 1) } },
                                    ),
                                )
                            }
                        }
                        if (isRounds) {
                            Text(
                                text = buildString {
                                    append("${segment.rounds} rounds")
                                    if (segment.restPolicy.restBetweenItemsSeconds > 0) {
                                        append(" · ${segment.restPolicy.restBetweenItemsSeconds}s between exercises")
                                    }
                                    if (segment.restPolicy.restAfterRoundSeconds > 0) {
                                        append(" · ${segment.restPolicy.restAfterRoundSeconds}s between rounds")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        segment.restAfterSeconds?.takeIf { it > 0 }?.let { seconds ->
                            Text(
                                text = "${seconds}s rest after section",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val items = if (isRounds) segment.weightItems() else segment.items
                        items.forEach { item ->
                            val isCurrentItem = isCurrent && currentStep?.itemId == item.id
                            val status = itemStatus(segment.id, item.id)?.let { " · $it" }.orEmpty()
                            val text = when (item) {
                                is WorkoutItem.Weight -> {
                                    val swapped = activeRun?.sessionExerciseSwaps?.get(sessionItemKey(segment.id, item.id))
                                    val name = swapped?.let { weightState.exerciseById(it)?.name ?: it }
                                        ?: item.displayExerciseName(weightState.exerciseById(item.exerciseId)?.name)
                                    "$name (${item.prescription.displaySummary(loadUnit)})"
                                }
                                is WorkoutItem.Cardio -> item.displaySummary()
                                is WorkoutItem.Mobility -> item.displaySummary()
                                is WorkoutItem.Rest -> "Rest ${item.durationSeconds}s"
                                is WorkoutItem.Note -> "Note: " + item.text.take(48).ifBlank { "…" }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (if (isCurrentItem) "▸ " else "· ") + text + status,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isCurrentItem -> MaterialTheme.colorScheme.primary
                                        status.isNotEmpty() || item is WorkoutItem.Rest || item is WorkoutItem.Note ->
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(
                                            // A lift opens on the card above, finished or still to come.
                                            if ((segment.id to item.id) in liftItemKeys) {
                                                Modifier.clickable {
                                                    viewedLiftKey = segment.id to item.id
                                                    scope.launch { runListState.animateScrollToItem(0) }
                                                }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                                if (editRun != null && editPlan != null) {
                                    val name = (item as? WorkoutItem.Weight)
                                        ?.let { weightState.exerciseById(todaysExerciseOf(segment.id, it))?.name }
                                        ?: storyboardItemName(item)
                                    WorkoutSessionRowMenu(
                                        contentDescription = "Options for $name",
                                        actions = listOf(
                                            WorkoutSessionMenuAction(
                                                "Swap Exercise",
                                                item is WorkoutItem.Weight && status.isEmpty(),
                                            ) { swapItem = segment.id to item.id },
                                            WorkoutSessionMenuAction(
                                                "Move Up",
                                                editRun.canMoveSessionItem(segment.id, item.id, -1, editPlan),
                                            ) { scope.launch { repository.moveSessionItem(segment.id, item.id, -1) } },
                                            WorkoutSessionMenuAction(
                                                "Move Down",
                                                editRun.canMoveSessionItem(segment.id, item.id, 1, editPlan),
                                            ) { scope.launch { repository.moveSessionItem(segment.id, item.id, 1) } },
                                            WorkoutSessionMenuAction(
                                                "Remove",
                                                editRun.canRemoveSessionItem(segment.id, item.id, editPlan),
                                            ) { removeTarget = Triple(segment.id, item.id, name) },
                                        ),
                                    )
                                }
                            }
                            if (item is WorkoutItem.Weight) {
                                // What was logged, so finished sets and sections stay readable;
                                // tap one to fix it.
                                loggedSetsOf(segment.id, item.id).forEach { (label, logged) ->
                                    Text(
                                        text = "$label: ${describeSessionSet(logged.set, loadUnit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { editingSet = logged }
                                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isComplete && finalizeFailed) {
                // The summary shows by itself once the run is saved; this is the way out if it
                // could not be built.
                item {
                    TextButton(
                        onClick = { discardRunAndLeave() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Finish")
                    }
                }
            }
        }
        }
    }
    }
}

/**
 * Live sensor numbers under the cardio clock: the Concept2 monitor's power, cadence, speed and
 * distance when it is connected, otherwise a cycling speed and cadence sensor's.
 */
@Composable
private fun SessionSensorReadout(
    concept2Ble: Concept2Pm5BleViewModel,
    cyclingCscBle: CyclingCscBleViewModel,
    distanceUnit: CardioDistanceUnit,
) {
    val ergConnection by concept2Ble.connectionState.collectAsState()
    val ergPower by concept2Ble.currentPowerWatts.collectAsState()
    val ergCadence by concept2Ble.currentCadenceRpm.collectAsState()
    val ergSpeed by concept2Ble.currentSpeedKmh.collectAsState()
    val ergDistance by concept2Ble.workoutDistanceMeters.collectAsState()
    val cscConnection by cyclingCscBle.connectionState.collectAsState()
    val cscSpeed by cyclingCscBle.currentSpeedKmh.collectAsState()
    val cscCadence by cyclingCscBle.currentCadenceRpm.collectAsState()
    val cscDistance by cyclingCscBle.workoutDistanceMeters.collectAsState()
    fun speed(kmh: Double?): String? = kmh?.takeIf { it > 0.0 }?.let {
        when (distanceUnit) {
            CardioDistanceUnit.MILES -> "%.1f mph".format(it * 0.621371)
            CardioDistanceUnit.KILOMETERS -> "%.1f km/h".format(it)
        }
    }
    fun distance(meters: Double?): String? =
        meters?.takeIf { it > 0.0 }?.let { formatCardioDistanceFromMeters(it, distanceUnit) }
    val parts = when {
        ergConnection == Concept2BleConnectionState.Connected -> listOfNotNull(
            ergPower?.takeIf { it > 0 }?.let { "$it W" },
            ergCadence?.takeIf { it > 0 }?.let { "$it rpm" },
            speed(ergSpeed),
            distance(ergDistance),
        )
        cscConnection == CyclingCscBleConnectionState.Connected -> listOfNotNull(
            speed(cscSpeed),
            cscCadence?.takeIf { it > 0 }?.let { "$it rpm" },
            distance(cscDistance),
        )
        else -> emptyList()
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** "Zone 2 · 130–145 bpm" or "170 bpm" for a cardio leg, when the builder set a target. */
private fun cardioHeartRateTarget(item: WorkoutItem.Cardio, step: WorkoutSessionStep.CardioLeg): String? {
    val cardio = item.cardio
    val min = cardio.hrTargetMinBpm
    val max = cardio.hrTargetMaxBpm
    val bpm = if (min != null && max != null) {
        "$min–$max bpm"
    } else {
        (step.hrTargetBpm ?: cardio.hrTargetBpm)?.let { "$it bpm" }
    }
    return listOfNotNull(cardio.hrZoneLabel?.takeIf { it.isNotBlank() }, bpm).joinToString(" · ").ifBlank { null }
}

/** A saved cardio routine (or an activity the session can't log), which runs on the cardio screen. */
@Composable
private fun OwnScreenCardioCard(
    item: WorkoutItem.Cardio,
    cardioState: CardioLibraryState,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    val ergSnapshot = remember(item.cardio.activity) {
        runCatching { CardioBuiltinActivity.valueOf(item.cardio.activity) }.getOrNull()
            ?.let { builtin -> cardioState.resolveSnapshot(builtin, null) }
    }
    val supportsErg = ergSnapshot?.supportsBikeErgSensorConnect() ?: false
    val cyclingApplicable = ergSnapshot?.isCyclingActivity() ?: false
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = item.title?.takeIf { it.isNotBlank() } ?: "Cardio",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(text = item.displaySummary(), style = MaterialTheme.typography.bodyMedium)
            if (supportsErg) {
                CardioBikeErgConnectInlineSection(
                    activitySupportsErg = true,
                    sessionKey = item.id,
                    compact = true,
                    cyclingSensorApplicable = cyclingApplicable,
                )
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start timer") }
            WorkoutSkipStepButton(label = "Skip cardio", onClick = onSkip)
        }
    }
}

/** A stretch with no catalog pose to hold, which opens the stretching screen. */
@Composable
private fun OwnScreenStretchCard(
    item: WorkoutItem.Mobility,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = item.title?.takeIf { it.isNotBlank() } ?: "Mobility",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(text = item.displaySummary(), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start stretch") }
            WorkoutSkipStepButton(label = "Skip stretch", onClick = onSkip)
        }
    }
}

/** "Save Changes to Workout": what today's run changed in the workout, with one button to keep it. */
@Composable
private fun WorkoutChangesCard(
    changes: List<String>,
    saving: Boolean,
    saved: Boolean,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (saved) {
                    "Saved. The workout now runs the way you did it today."
                } else {
                    "You changed this workout today. Keep the changes for next time?"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            changes.forEach { line ->
                Text(
                    text = "· $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!saved) {
                Button(
                    onClick = onSave,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Saving…" else "Save Changes to Workout")
                }
            }
        }
    }
}

/** "Save today's weights": lists each target today's lifting would change, with one button to save them all. */
@Composable
private fun TodaysWeightsCard(
    changes: List<WorkoutTargetWeightChange>,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    saving: Boolean,
    saved: Boolean,
    onSave: () -> Unit,
) {
    val suffix = weightLoadUnitSuffix(loadUnit)
    fun weights(values: List<Double?>): String =
        values.joinToString(", ") { kg -> kg?.let { formatWeightLoadNumber(it, loadUnit) } ?: "none" }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (saved) {
                    "Saved. Next time this workout starts from today's weights."
                } else {
                    "Today's weights differ from the workout's targets. Save them as the new targets?"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            changes.forEach { change ->
                val name = weightState.exerciseById(change.exerciseId)?.name ?: change.exerciseId
                Text(
                    text = "$name: ${weights(change.beforeKg)} → ${weights(change.afterKg)} $suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!saved) {
                Button(
                    onClick = onSave,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Saving…" else "Save Today's Weights")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposedWorkoutSummaryScreen(
    summary: ComposedWorkoutHrSummary,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    distanceUnit: CardioDistanceUnit,
    zoneInputs: HeartRateZoneInputs,
    relayPool: RelayPool?,
    signer: EventSigner?,
    /** Writes today's weights back as the workout's targets; null hides the card. Returns false if the workout is gone. */
    onSaveTodaysWeights: (suspend () -> Boolean)? = null,
    /**
     * Writes today's swaps, additions, removals and order into the saved workout; null hides the
     * card. Its argument is true when today's weights were already saved as targets.
     */
    onSaveWorkoutChanges: (suspend (Boolean) -> Boolean)? = null,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var savingWeights by remember { mutableStateOf(false) }
    var savedWeights by remember { mutableStateOf(false) }
    var savingChanges by remember { mutableStateOf(false) }
    var savedChanges by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var sharePersonalMessage by remember { mutableStateOf("") }
    var shareHashtags by remember {
        mutableStateOf(buildWorkoutShareHashtagContentLineFromTopics(workoutShareBaseTopicTags))
    }
    var showSharePreview by remember { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current
    val sharePreviewContent = remember(summary, sharePersonalMessage, shareHashtags) {
        buildComposedWorkoutKind1Draft(
            summary = summary,
            personalMessage = sharePersonalMessage,
            hashtagsInput = shareHashtags,
        ).content
    }
    val kind1ShareSuccessMsg = stringResource(R.string.kind1_share_success)
    val kind1NoSocialMsg = stringResource(R.string.kind1_share_no_social_relays)
    val kind1FailureMsg = stringResource(R.string.kind1_share_failed)
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val weightSessions = summary.sections.mapNotNull { it.weightSession }
    val totalSets = weightSessions.sumOf { it.totalSetCount() }
    val totalVolume = weightSessions.sumOf { it.totalVolumeLoadTimesReps(loadUnit) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout complete") },
                colors = ervSessionTopAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(summary.workoutName, style = MaterialTheme.typography.headlineSmall)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        summary.totalElapsedSeconds?.takeIf { it > 0 }?.let { seconds ->
                            Text(
                                text = "Total time  ${seconds / 60}:${"%02d".format(seconds % 60)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (summary.sectionCount > 0) {
                            Text(
                                text = buildString {
                                    append("${summary.sectionCount} section(s) completed")
                                    if (summary.skippedItems.isNotEmpty()) {
                                        append(" · ${summary.skippedItems.size} step(s) skipped")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (weightSessions.isNotEmpty()) {
                            Text(
                                text = buildString {
                                    append("${weightSessions.sumOf { it.entries.size }} exercises • $totalSets sets")
                                    if (totalVolume > 0.5) {
                                        append(" • Volume ~${totalVolume.toInt()} ${loadSuffix}×reps")
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val liftingKcal = weightSessions.sumOf { it.estimatedKcal ?: 0.0 }
                            if (liftingKcal > 0.5) {
                                Text(
                                    text = "Est. calories from lifting: ~${liftingKcal.toInt()} kcal",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        summary.wholeRun?.let { hr ->
                            Text(
                                text = "Heart rate  avg ${hr.avgBpm} · max ${hr.maxBpm} bpm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (summary.wholeRun != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Heart rate", style = MaterialTheme.typography.titleMedium)
                            HeartRateSessionAnalyticsSection(
                                heartRate = summary.wholeRun,
                                zoneInputs = zoneInputs,
                                useLightOnDarkBackground = false,
                                sectionMarkers = summary.sectionChartMarkers(),
                            )
                        }
                    }
                }
            } else if (!summary.hasAnyHeartRate) {
                item {
                    Text(
                        "No heart rate was recorded for this workout. Connect a heart rate monitor " +
                            "before your next session to see effort analysis.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (summary.sections.isNotEmpty()) {
                item {
                    FormSectionLabel("Workout log")
                }
                // Two sections can share a title, so they are keyed by position.
                summary.sections.forEachIndexed { index, section ->
                    item(key = "section_log_$index") {
                        ComposedWorkoutSectionLogCard(
                            section = section,
                            weightState = weightState,
                            loadUnit = loadUnit,
                            loadSuffix = loadSuffix,
                            distanceUnit = distanceUnit,
                        )
                    }
                }
            }
            if (onSaveTodaysWeights != null || onSaveWorkoutChanges != null) {
                item {
                    FormSectionLabel("Next time")
                }
            }
            if (onSaveWorkoutChanges != null) {
                item {
                    WorkoutChangesCard(
                        changes = summary.workoutAdjustments,
                        saving = savingChanges,
                        saved = savedChanges,
                        onSave = {
                            if (savingChanges || savedChanges) return@WorkoutChangesCard
                            savingChanges = true
                            scope.launch {
                                val ok = runCatching { onSaveWorkoutChanges(savedWeights) }.getOrDefault(false)
                                savingChanges = false
                                if (ok) {
                                    savedChanges = true
                                } else {
                                    snackbarHostState.showSnackbar("Couldn't update the workout. It may have been deleted.")
                                }
                            }
                        },
                    )
                }
            }
            if (onSaveTodaysWeights != null) {
                item {
                    TodaysWeightsCard(
                        changes = summary.weightTargetChanges,
                        weightState = weightState,
                        loadUnit = loadUnit,
                        saving = savingWeights,
                        saved = savedWeights,
                        onSave = {
                            if (savingWeights || savedWeights) return@TodaysWeightsCard
                            savingWeights = true
                            scope.launch {
                                val ok = runCatching { onSaveTodaysWeights() }.getOrDefault(false)
                                savingWeights = false
                                if (ok) {
                                    savedWeights = true
                                } else {
                                    snackbarHostState.showSnackbar("Couldn't update the workout. It may have been deleted.")
                                }
                            }
                        },
                    )
                }
            }
            if (summary.skippedItems.isNotEmpty()) {
                item {
                    FormSectionLabel("Skipped steps")
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            summary.skippedItems.forEach { label ->
                                Text(
                                    text = "· $label",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (relayPool != null && signer != null) {
                item {
                    OutlinedTextField(
                        value = sharePersonalMessage,
                        onValueChange = { sharePersonalMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sharing && !shared,
                        label = { Text("Add a note (optional)") },
                        minLines = 2,
                        maxLines = 5,
                    )
                }
                item {
                    OutlinedTextField(
                        value = shareHashtags,
                        onValueChange = { shareHashtags = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sharing && !shared,
                        label = { Text("Hashtags") },
                        minLines = 1,
                        maxLines = 3,
                    )
                }
                item {
                    OutlinedButton(
                        onClick = {
                            if (sharing || shared) return@OutlinedButton
                            showSharePreview = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !sharing && !shared,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                sharing -> "Sharing…"
                                shared -> "Shared"
                                else -> "Share workout"
                            },
                        )
                    }
                }
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
    if (showSharePreview && relayPool != null && signer != null) {
        WorkoutKind1SharePreviewDialog(
            content = sharePreviewContent,
            socialRelayUrls = keyManager.relayUrlsForKind1Publish(),
            confirming = sharing,
            onDismiss = { if (!sharing) showSharePreview = false },
            onConfirm = {
                sharing = true
                scope.launch {
                    val result = publishComposedWorkoutNote(
                        relayPool = relayPool,
                        keyManager = keyManager,
                        signer = signer,
                        summary = summary,
                        personalMessage = sharePersonalMessage,
                        hashtagsInput = shareHashtags,
                        successMessage = kind1ShareSuccessMsg,
                        noSocialRelaysMessage = kind1NoSocialMsg,
                        failureMessage = kind1FailureMsg,
                    )
                    sharing = false
                    if (result.ok) {
                        shared = true
                        showSharePreview = false
                    }
                    snackbarHostState.showSnackbar(result.userMessage)
                }
            },
        )
    }
}

/** Secondary "skip this step" action under a step card's primary button; opens the confirm dialog. */
@Composable
private fun WorkoutSkipStepButton(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComposedWorkoutSectionLogCard(
    section: ComposedWorkoutHrSection,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    distanceUnit: CardioDistanceUnit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            Text(
                section.kind.summaryLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                section.weightSession != null -> {
                    section.weightSession.entries.forEach { entry ->
                        val exercise = weightState.exerciseById(entry.exerciseId)
                        val addedLoad = exercise?.equipment == WeightEquipment.OTHER
                        Text(
                            "• ${exercise?.name ?: entry.exerciseId}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        entry.hiitBlock?.let { block ->
                            Text(
                                formatHiitBlockSummaryLine(block, loadUnit, loadSuffix, addedLoad),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        } ?: entry.sets.forEachIndexed { idx, set ->
                            Text(
                                formatSetSummaryLine(
                                    set = set,
                                    setNumber = idx + 1,
                                    loadUnit = loadUnit,
                                    loadSuffix = loadSuffix,
                                    weightIsAddedLoad = addedLoad,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
                section.cardioSession != null -> {
                    val session = section.cardioSession
                    Text(
                        session.activity.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        buildString {
                            append("${session.durationMinutes} min")
                            session.distanceMeters?.takeIf { it > 1.0 }?.let { meters ->
                                append(" • ${formatCardioDistanceFromMeters(meters, distanceUnit)}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    formatCardioAveragePaceForSession(session, distanceUnit, null)?.let { pace ->
                        Text(
                            "Avg pace: $pace",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                section.stretchSession != null -> {
                    val session = section.stretchSession
                    Text(
                        buildString {
                            append("${session.totalMinutes} min")
                            session.routineName?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                            if (session.routineName.isNullOrBlank() && session.stretchIds.isNotEmpty()) {
                                append(" • ${session.stretchIds.size} stretches")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        "No logged details for this section.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The saved rest timer choice as the session engine names it. */
private fun WeightLiveRestTimerMode.toSessionRestMode(): WorkoutSessionRestMode = when (this) {
    WeightLiveRestTimerMode.OFF -> WorkoutSessionRestMode.OFF
    WeightLiveRestTimerMode.AUTO -> WorkoutSessionRestMode.AUTO
    WeightLiveRestTimerMode.MANUAL -> WorkoutSessionRestMode.MANUAL
}

private fun WorkoutSessionRestMode.toSavedRestMode(): WeightLiveRestTimerMode = when (this) {
    WorkoutSessionRestMode.OFF -> WeightLiveRestTimerMode.OFF
    WorkoutSessionRestMode.AUTO -> WeightLiveRestTimerMode.AUTO
    WorkoutSessionRestMode.MANUAL -> WeightLiveRestTimerMode.MANUAL
}

/** The hold timer open for the timed set at [stepIndex]; [base] has the weight and RPE typed. */
private data class SessionHoldTimer(
    val stepIndex: Int,
    val exerciseName: String,
    val goalSeconds: Int,
    val prepSeconds: Int,
    val countdownBeeps: Boolean,
    val base: WeightSet,
)

/** The interval timer open for the exercise on screen at [stepIndex]. */
private data class SessionIntervalTimer(
    val stepIndex: Int,
    val exerciseName: String,
    val plan: WeightHiitIntervalPlan,
)

/** The weight workout a Quick Start run was logged as, shown on the weight screen's summary. */
private data class StandaloneWeightSummary(
    val session: com.erv.app.weighttraining.WeightWorkoutSession,
    val logDate: LocalDate,
)

/** "1.24 mi · 9:48 /mi" from the route so far, or null before there is any distance. */
private fun sessionGpsLiveLine(points: List<CardioGpsPoint>, unit: CardioDistanceUnit): String? {
    if (points.size < 2) return null
    val meters = CardioGpsMath.pathLengthMeters(points)
    if (meters <= 1.0) return null
    val pace = formatCardioAveragePace(movingSecondsOf(points), meters, unit)
    return listOfNotNull(formatCardioDistanceFromMeters(meters, unit), pace).joinToString(" · ")
}

/** Before Start on an outdoor cardio leg: record the route or not, and the location prompt. */
@Composable
private fun SessionGpsControls(
    on: Boolean,
    locationGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onAllowLocation: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldLabel("Record GPS route", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = on, onCheckedChange = onToggle)
        }
        if (on && !locationGranted) {
            TextButton(onClick = onAllowLocation, modifier = Modifier.fillMaxWidth()) {
                Text("Allow Location for GPS")
            }
        }
    }
}

/** Distance, pace and the last split as tiles for the live cardio card, from the route so far. */
private fun sessionCardioLiveStats(
    points: List<CardioGpsPoint>,
    unit: CardioDistanceUnit,
    splitMeters: Double?,
): List<Pair<String, String>> {
    if (points.size < 2) return emptyList()
    val meters = CardioGpsMath.pathLengthMeters(points)
    if (meters <= 1.0) return emptyList()
    val splits = splitMeters?.let { CardioGpsSplits.fromTrack(points, it) }.orEmpty()
    val movingSeconds = movingSecondsOf(points)
    return listOfNotNull(
        "Distance" to formatCardioDistanceFromMeters(meters, unit),
        formatCardioAveragePace(movingSeconds, meters, unit)?.let { "Avg Pace" to it },
        splits.lastOrNull()?.let { split ->
            "Split ${split.orderIndex + 1}" to formatCardioElapsedClock(split.segmentElapsedSeconds)
        },
    )
}

/** Time between fixes, leaving out gaps (pauses, lost GPS) the way the splits do. */
private fun movingSecondsOf(points: List<CardioGpsPoint>): Int =
    points.zipWithNext { a, b -> (b.epochSeconds - a.epochSeconds).coerceAtLeast(0L) }
        .filter { it <= CardioGpsSplits.DEFAULT_MAX_GAP_SECONDS }
        .sum()
        .toInt()
