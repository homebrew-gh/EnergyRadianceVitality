package com.erv.app.ui.weighttraining

// Equipment uses FilterChips only — no MenuAnchorType / ExposedDropdownMenu (avoids Material3 API drift).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.ui.components.FieldLabel
import com.erv.app.R
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.nostr.RelayPool
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.workouts.WorkoutLibraryState
import com.erv.app.workouts.WorkoutLoggedItemKind
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.activeWorkoutWeightLaunch
import com.erv.app.workouts.finalCircuitRunPosition
import com.erv.app.workouts.isFinalLoggableStep
import com.erv.app.workouts.linkFor
import com.erv.app.workouts.sectionProgressLabel
import com.erv.app.unifiedroutines.linkFor
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.weighttraining.groupExercisesByMuscle
import com.erv.app.weighttraining.isLogged
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
import com.erv.app.weighttraining.WeightCalorieEstimator
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightExercisePickerFilter
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.buildWeightExerciseHrSegments
import com.erv.app.weighttraining.toFinishedLiveSession
import com.erv.app.weighttraining.weightNowEpochSeconds
import com.erv.app.weighttraining.exerciseIdsUsedInAnyLog
import com.erv.app.weighttraining.exercisesGroupedByMuscle
import com.erv.app.weighttraining.filterWeightExercisesForPicker
import com.erv.app.weighttraining.formatMuscleGroupHeader
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private enum class WeightTrainingTab { Exercises, Routines }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrainingCategoryScreen(
    repository: WeightRepository,
    unifiedRoutineRepository: UnifiedRoutineRepository,
    workoutRepository: WorkoutRepository,
    liveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    userPreferences: UserPreferences,
    initialTab: String = WeightTrainingTab.Exercises.name,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onReturnToUnifiedRun: (String) -> Unit = {},
    onReturnToWorkoutRun: (String) -> Unit = {},
    /** Discarding a composed-workout weight section returns straight to the workout library. */
    onReturnToWorkoutLibrary: () -> Unit = {},
    onOpenLog: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val heartRateBle = LocalHeartRateBle.current
    val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
    val workoutState by workoutRepository.state.collectAsState(initial = WorkoutLibraryState())
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val fallbackBodyWeightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val liveDraft by liveWorkoutViewModel.activeDraft.collectAsState()
    val liveWorkoutUiExpanded by liveWorkoutViewModel.liveWorkoutUiExpanded.collectAsState()
    val state by repository.state.collectAsState(initial = WeightLibraryState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // When discarding a composed-workout weight section we navigate straight to the workout
    // library, but defer clearing the draft until this screen is actually disposed. That keeps
    // the full-screen live overlay rendered through the navigation transition instead of briefly
    // unmasking the bare weight-training category screen underneath (the discard "flash").
    var clearDraftOnDispose by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (clearDraftOnDispose) {
                liveWorkoutViewModel.clearDraft()
            }
        }
    }
    val resolvedInitialTab = WeightTrainingTab.entries
        .firstOrNull { it.name.equals(initialTab, ignoreCase = true) }
        ?.name
        ?: WeightTrainingTab.Exercises.name
    var activeTab by rememberSaveable { mutableStateOf(resolvedInitialTab) }
    LaunchedEffect(resolvedInitialTab) {
        activeTab = resolvedInitialTab
    }
    val tabEnum = WeightTrainingTab.entries.firstOrNull { it.name == activeTab } ?: WeightTrainingTab.Exercises

    var showExerciseCreator by remember { mutableStateOf(false) }

    var routineBeingEdited by remember { mutableStateOf<WeightRoutine?>(null) }
    var routinePendingDelete by remember { mutableStateOf<WeightRoutine?>(null) }

    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    var showWeightFgsDialog by remember { mutableStateOf(false) }
    var pendingWeightBlankStart by remember { mutableStateOf(false) }
    var pendingWeightRoutine by remember { mutableStateOf<WeightRoutine?>(null) }

    val darkTheme = isSystemInDarkTheme()
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val headerGlow = if (darkTheme) ErvDarkTherapyRedGlow else ErvLightTherapyRedGlow
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val urls = keyManager.relayUrlsForKind30078Publish()
        val s = repository.currentState()
        WeightSync.publishExercises(appContext, relayPool, signer, s.exercises, urls)
        WeightSync.publishRoutines(appContext, relayPool, signer, s.routines, urls)
    }

    suspend fun pushDayLog(date: LocalDate) {
        if (relayPool == null || signer == null) return
        val log = repository.currentState().logFor(date) ?: return
        WeightSync.publishDayLog(appContext, relayPool, signer, log, keyManager.relayUrlsForKind30078Publish())
    }

    var completedSessionForSummary by remember { mutableStateOf<WeightWorkoutSession?>(null) }

    val summarySession = completedSessionForSummary
    if (summarySession != null) {
            WeightWorkoutSummaryFullScreen(
            session = summarySession,
            logDate = LocalDate.now(),
            library = state,
            loadUnit = loadUnit,
            userPreferences = userPreferences,
            dark = headerDark,
            mid = headerMid,
            glow = headerGlow,
            relayPool = relayPool,
            signer = signer,
            repository = repository,
            onAfterRoutineSync = { scope.launch { pushMasters() } },
            onRemoveFromLog = {
                scope.launch {
                    repository.deleteWorkout(LocalDate.now(), summarySession.id)
                    completedSessionForSummary = null
                    launch { pushDayLog(LocalDate.now()) }
                }
            },
            onOpenLog = {
                completedSessionForSummary = null
                onOpenLog()
            },
            onDone = { completedSessionForSummary = null }
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (liveDraft == null) {
                    when (tabEnum) {
                        WeightTrainingTab.Exercises -> FloatingActionButton(
                            onClick = { showExerciseCreator = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add exercise")
                        }
                        WeightTrainingTab.Routines -> FloatingActionButton(
                            onClick = {
                                routineBeingEdited = WeightRoutine(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    exerciseIds = emptyList()
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add routine")
                        }
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text("Weight Training") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (liveDraft == null) {
                            IconButton(onClick = {
                                when {
                                    cardioLiveWorkoutViewModel.hasActiveTimer -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                        }
                                    }
                                    !fgsDisclosureSeen -> {
                                        pendingWeightBlankStart = true
                                        pendingWeightRoutine = null
                                        showWeightFgsDialog = true
                                    }
                                    !liveWorkoutViewModel.tryStartBlank() -> {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Start workout")
                            }
                        }
                        IconButton(onClick = onOpenLog) {
                            Icon(Icons.Default.DateRange, contentDescription = "Open log")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ErvHeaderRed,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (liveDraft != null && !liveWorkoutUiExpanded) {
                    LiveWorkoutInProgressBanner(
                        onClick = { liveWorkoutViewModel.setLiveWorkoutUiExpanded(true) },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                TabRow(
                    selectedTabIndex = tabEnum.ordinal,
                    containerColor = headerDark,
                    contentColor = Color.White
                ) {
                    WeightTrainingTab.entries.forEach { tab ->
                        Tab(
                            selected = tabEnum == tab,
                            onClick = { activeTab = tab.name },
                            text = { Text(tab.name) }
                        )
                    }
                }

                when (tabEnum) {
                    WeightTrainingTab.Exercises -> ExercisesTabBody(
                        state = state,
                        userPreferences = userPreferences,
                        onOpenExerciseDetail = onOpenExerciseDetail
                    )

                    WeightTrainingTab.Routines -> RoutinesTabBody(
                        state = state,
                        onEdit = { routineBeingEdited = it },
                        onDeleteRequest = { routinePendingDelete = it },
                        onStartRoutine = { routine ->
                            when {
                                cardioLiveWorkoutViewModel.hasActiveTimer -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                                    }
                                }
                                !fgsDisclosureSeen -> {
                                    pendingWeightRoutine = routine
                                    pendingWeightBlankStart = false
                                    showWeightFgsDialog = true
                                }
                                !liveWorkoutViewModel.tryStartFromRoutine(routine, state) -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        val expandedDraft = liveDraft
        if (expandedDraft != null && liveWorkoutUiExpanded) {
            val activeUnifiedSession = unifiedState.activeSession
            val activeUnifiedWeightBlockId = activeUnifiedSession?.lastLaunchedBlockId?.takeIf { blockId ->
                unifiedState
                    .routineById(activeUnifiedSession.routineId)
                    ?.blocks
                    ?.firstOrNull { it.id == blockId }
                    ?.type == com.erv.app.unifiedroutines.UnifiedRoutineBlockType.WEIGHT
            }
            val activeWorkoutWeightLaunch = workoutState.activeWorkoutWeightLaunch()
            val activeWorkoutRun = workoutState.activeRun
            fun returnToParentRun(): Boolean = when {
                activeUnifiedSession != null && activeUnifiedWeightBlockId != null -> {
                    onReturnToUnifiedRun(activeUnifiedSession.routineId)
                    true
                }
                activeWorkoutWeightLaunch != null -> {
                    onReturnToWorkoutRun(activeWorkoutWeightLaunch.workoutId)
                    true
                }
                expandedDraft.circuitRun != null && activeWorkoutRun != null -> {
                    onReturnToWorkoutRun(activeWorkoutRun.workoutId)
                    true
                }
                else -> false
            }
            suspend fun persistFinishedLiveDraft() {
                val current = liveWorkoutViewModel.activeDraft.value
                if (current == null) return
                val workoutLaunch = workoutRepository.currentState().activeWorkoutWeightLaunch()
                    ?: expandedDraft.circuitRun?.let { circuit ->
                        com.erv.app.workouts.ActiveWorkoutItemLaunch(
                            workoutId = circuit.workoutId,
                            segmentId = circuit.segmentId,
                            itemId = circuit.slots.first().workoutItemId,
                        )
                    }
                val circuit = current.circuitRun
                if (circuit != null && workoutLaunch != null) {
                    val segment = workoutRepository.currentState().activeRun
                        ?.workoutSnapshot
                        ?.segments
                        ?.getOrNull(circuit.segmentIndex)
                    if (segment != null) {
                        workoutRepository.updateRunPosition(
                            segment.finalCircuitRunPosition(circuit.segmentIndex),
                        )
                    }
                }
                // Per-section HR snapshot: always capture for this section. The continuous
                // whole-workout HR is recorded separately and attached when the composed run finishes.
                val hr = heartRateBle.takeWorkoutHeartRateSummary()
                val end = weightNowEpochSeconds()
                val segments = if (hr != null) {
                    buildWeightExerciseHrSegments(
                        current.exerciseFocusMarks,
                        current.startedAtEpochSeconds,
                        end,
                        hr.samples.orEmpty(),
                    )
                } else {
                    emptyList()
                }
                val session = current.toFinishedLiveSession(
                    heartRate = hr,
                    heartRateExerciseSegments = segments,
                )
                if (session == null) {
                    return
                }
                val estimatedKcal = WeightCalorieEstimator.estimateKcal(session, fallbackBodyWeightKg)
                val today = LocalDate.now()
                val workoutRun = workoutRepository.currentState().activeRun?.takeIf { workoutLaunch != null }
                val storedSession = when {
                    activeUnifiedSession != null && activeUnifiedWeightBlockId != null -> {
                        val recap = unifiedState.sessionById(activeUnifiedSession.sessionId)
                        session.copy(
                            estimatedKcal = estimatedKcal,
                            unifiedLink = recap?.linkFor(activeUnifiedWeightBlockId),
                        )
                    }
                    workoutLaunch != null && workoutRun != null -> {
                        session.copy(
                            estimatedKcal = estimatedKcal,
                            workoutLink = workoutRun.linkFor(workoutLaunch.segmentId, workoutLaunch.itemId),
                        )
                    }
                    else -> session.copy(estimatedKcal = estimatedKcal)
                }
                repository.addWorkout(today, storedSession)
                if (activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                    unifiedRoutineRepository.attachLoggedBlock(
                        routineId = activeUnifiedSession.routineId,
                        blockId = activeUnifiedWeightBlockId,
                        logDate = today.toString(),
                        entryId = storedSession.id,
                    )
                }
                when {
                    activeUnifiedSession != null && activeUnifiedWeightBlockId != null -> {
                        liveWorkoutViewModel.clearDraft()
                        onReturnToUnifiedRun(activeUnifiedSession.routineId)
                    }
                    workoutLaunch != null -> {
                        workoutRepository.completeLaunchedItem(
                            logDate = today.toString(),
                            entryId = storedSession.id,
                            kind = WorkoutLoggedItemKind.WEIGHT,
                        )
                        liveWorkoutViewModel.clearDraft()
                        onReturnToWorkoutRun(workoutLaunch.workoutId)
                    }
                    else -> {
                        liveWorkoutViewModel.clearDraft()
                        completedSessionForSummary = storedSession
                    }
                }
                pushDayLog(today)
            }
            val composedRun = activeWorkoutRun?.takeIf {
                activeWorkoutWeightLaunch != null || expandedDraft.circuitRun != null
            }
            val isUnifiedBlock = activeUnifiedSession != null && activeUnifiedWeightBlockId != null
            val composedSectionLabel = composedRun?.takeIf { !isUnifiedBlock }?.sectionProgressLabel()
            val weightFinishLabel = when {
                isUnifiedBlock -> "Finish"
                composedRun != null ->
                    if (composedRun.isFinalLoggableStep()) "Finish workout" else "Next section"
                else -> "Finish"
            }
            WeightLiveWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                draft = expandedDraft,
                library = state,
                loadUnit = loadUnit,
                userPreferences = userPreferences,
                unifiedWorkoutStartedAtEpochSeconds =
                    if (activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                        activeUnifiedSession.startedAtEpochSeconds
                    } else {
                        null
                    },
                composedWorkoutStartedAtEpochSeconds = expandedDraft.circuitRun?.let {
                    activeWorkoutRun?.startedAtEpochSeconds
                },
                composedSectionLabel = composedSectionLabel,
                finishLabel = weightFinishLabel,
                onRecordExerciseActivity = { id -> liveWorkoutViewModel.recordExerciseFocus(id) },
                onAfterCircuitSetLogged = {
                    scope.launch {
                        val advance = liveWorkoutViewModel.tryAdvanceCircuitAfterSlotComplete() ?: return@launch
                        advance.workoutRunPosition?.let { workoutRepository.updateRunPosition(it) }
                    }
                },
                onCircuitSegmentComplete = {
                    scope.launch { persistFinishedLiveDraft() }
                },
                onLeaveWorkoutUi = {
                    fun returnToUnifiedRun(): Boolean {
                        val routineId = activeUnifiedSession?.routineId ?: return false
                        if (activeUnifiedWeightBlockId == null) return false
                        onReturnToUnifiedRun(routineId)
                        return true
                    }
                    val draft = liveWorkoutViewModel.activeDraft.value
                    if (draft != null) {
                        val noExercises = draft.exerciseOrder.isEmpty()
                        val noLoggedSets = draft.setsByExerciseId.values.all { rows ->
                            rows.isEmpty() || rows.none { it.isLogged() }
                        }
                        val noHiit = draft.hiitBlocksByExerciseId.isEmpty()
                        if (noExercises && noLoggedSets && noHiit) {
                            heartRateBle.discardWorkoutRecording()
                            liveWorkoutViewModel.clearDraft()
                            val returnedToParent = returnToParentRun()
                            if (!returnedToParent && activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                                onBack()
                            }
                        } else {
                            val returnedToParent = returnToParentRun()
                            liveWorkoutViewModel.setLiveWorkoutUiExpanded(false)
                            if (!returnedToParent && activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                                onBack()
                            }
                        }
                    }
                },
                onDiscardWorkout = {
                    val isComposedWorkout = activeUnifiedSession == null &&
                        (activeWorkoutWeightLaunch != null ||
                            (expandedDraft.circuitRun != null && activeWorkoutRun != null))
                    if (isComposedWorkout) {
                        // Composed-workout weight section: abandon the run and go straight to the
                        // workout library in a single pop. The draft is cleared on dispose (see
                        // clearDraftOnDispose) so the live overlay covers the navigation transition.
                        heartRateBle.discardWorkoutRecording()
                        heartRateBle.discardComposedWorkoutRunRecording()
                        scope.launch { workoutRepository.clearActiveRun() }
                        clearDraftOnDispose = true
                        onReturnToWorkoutLibrary()
                    } else {
                        heartRateBle.discardWorkoutRecording()
                        liveWorkoutViewModel.clearDraft()
                        val returnedToParent = returnToParentRun()
                        if (!returnedToParent && activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                            onBack()
                        }
                    }
                },
                onCannotFinishNothingLogged = {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            appContext.getString(R.string.weight_live_finish_blocked_snackbar)
                        )
                    }
                },
                onFinish = {
                    scope.launch {
                        if (liveWorkoutViewModel.activeDraft.value == null) {
                            snackbarHostState.showSnackbar(
                                appContext.getString(R.string.weight_live_finish_snackbar_no_draft)
                            )
                            return@launch
                        }
                        if (liveWorkoutViewModel.activeDraft.value?.toFinishedLiveSession() == null) {
                            snackbarHostState.showSnackbar(
                                appContext.getString(R.string.weight_live_finish_snackbar_nothing_to_save)
                            )
                            return@launch
                        }
                        persistFinishedLiveDraft()
                    }
                },
                onAddExercise = { id -> liveWorkoutViewModel.addExercise(id) },
                onCreateExercise = { exercise ->
                    scope.launch {
                        repository.upsertExercise(exercise)
                        pushMasters()
                        snackbarHostState.showSnackbar("Exercise added")
                    }
                },
                onRemoveExerciseAt = { idx -> liveWorkoutViewModel.removeExerciseAt(idx) },
                onMoveExerciseUp = { idx -> liveWorkoutViewModel.moveExerciseUp(idx) },
                onMoveExerciseDown = { idx -> liveWorkoutViewModel.moveExerciseDown(idx) },
                onSaveSets = { exerciseId, sets -> liveWorkoutViewModel.setSetsForExercise(exerciseId, sets) },
                onSaveHiitBlock = { exerciseId, block ->
                    liveWorkoutViewModel.setHiitBlockForExercise(exerciseId, block)
                },
                onClearHiitBlock = { exerciseId ->
                    liveWorkoutViewModel.clearHiitBlockForExercise(exerciseId)
                }
            )
        }
    }

    WeightLiveWorkoutFgsDisclosureDialog(
        visible = showWeightFgsDialog,
        onDismiss = {
            showWeightFgsDialog = false
            pendingWeightBlankStart = false
            pendingWeightRoutine = null
        },
        onContinue = {
            scope.launch {
                userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true)
                showWeightFgsDialog = false
                val blank = pendingWeightBlankStart
                val pendingRoutine = pendingWeightRoutine
                pendingWeightBlankStart = false
                pendingWeightRoutine = null
                when {
                    blank -> {
                        when {
                            cardioLiveWorkoutViewModel.hasActiveTimer ->
                                snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                            !liveWorkoutViewModel.tryStartBlank() ->
                                snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                        }
                    }
                    pendingRoutine != null -> {
                        when {
                            cardioLiveWorkoutViewModel.hasActiveTimer ->
                                snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.")
                            !liveWorkoutViewModel.tryStartFromRoutine(pendingRoutine, state) ->
                                snackbarHostState.showSnackbar("Finish or cancel your live workout first.")
                        }
                    }
                }
            }
        }
    )

    routinePendingDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { routinePendingDelete = null },
            title = { Text("Delete routine?") },
            text = { Text("Remove “${r.name}”?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteRoutine(r.id)
                            pushMasters()
                            snackbarHostState.showSnackbar("Routine removed")
                        }
                        routinePendingDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { routinePendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showExerciseCreator) {
        key(showExerciseCreator) {
            WeightExerciseEditorDialog(
                initial = null,
                title = "Add exercise",
                availableMuscleGroups = state.exercises.map { it.muscleGroup },
                onDismiss = { showExerciseCreator = false },
                onSave = { draft ->
                    scope.launch {
                        repository.upsertExercise(draft)
                        pushMasters()
                        snackbarHostState.showSnackbar("Exercise added")
                    }
                    showExerciseCreator = false
                }
            )
        }
    }

    val routineDraft = routineBeingEdited
    if (routineDraft != null) {
        key(routineDraft.id) {
            WeightRoutineEditorDialog(
                initial = routineDraft,
                exerciseLibrary = state.exercises.sortedBy { it.name.lowercase() },
                title = if (state.routines.none { it.id == routineDraft.id }) {
                    "New routine"
                } else {
                    "Edit routine"
                },
                onDismiss = { routineBeingEdited = null },
                onSave = { routine ->
                    scope.launch {
                        try {
                            repository.upsertRoutine(routine)
                            pushMasters()
                            snackbarHostState.showSnackbar("Routine saved")
                            routineBeingEdited = null
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar("Could not save routine")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExercisesTabBody(
    state: WeightLibraryState,
    userPreferences: UserPreferences,
    onOpenExerciseDetail: (String) -> Unit
) {
    val ownedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())
    val enabledExercisePackIds by userPreferences.enabledWeightExercisePackIds.collectAsState(initial = emptySet())
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var loggedBeforeOnly by rememberSaveable { mutableStateOf(false) }
    var equipmentFilter by rememberSaveable { mutableStateOf(WeightExercisePickerFilter.ALL) }
    val loggedIds = remember(state.logs) { state.exerciseIdsUsedInAnyLog() }
    val grouped = remember(
        state.exercises,
        state.logs,
        searchQuery,
        loggedBeforeOnly,
        loggedIds,
        equipmentFilter,
        ownedEquipment,
        enabledExercisePackIds,
    ) {
        exercisesGroupedFiltered(
            state = state,
            query = searchQuery,
            loggedBeforeOnly = loggedBeforeOnly,
            loggedIds = loggedIds,
            equipmentFilter = equipmentFilter,
            ownedEquipment = ownedEquipment,
            enabledPackIds = enabledExercisePackIds,
        )
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search exercises") },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !loggedBeforeOnly,
                onClick = { loggedBeforeOnly = false },
                label = { FieldLabel("All Exercises") }
            )
            FilterChip(
                selected = loggedBeforeOnly,
                onClick = { loggedBeforeOnly = true },
                label = { FieldLabel("Logged Before") }
            )
            FilterChip(
                selected = equipmentFilter == WeightExercisePickerFilter.HOME_READY,
                onClick = {
                    equipmentFilter =
                        if (equipmentFilter == WeightExercisePickerFilter.HOME_READY) {
                            WeightExercisePickerFilter.ALL
                        } else {
                            WeightExercisePickerFilter.HOME_READY
                        }
                },
                label = { FieldLabel("Home-Ready") }
            )
        }

        when {
            state.exercises.isEmpty() -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No exercises yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            grouped.isEmpty() -> {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            loggedBeforeOnly && loggedIds.isEmpty() ->
                                "No logged workouts yet — train and save a session to see exercises here."
                            equipmentFilter == WeightExercisePickerFilter.HOME_READY && ownedEquipment.isEmpty() ->
                                "No home-ready exercises match your search. Add equipment in Settings -> Equipment & Gym to expand this list."
                            equipmentFilter == WeightExercisePickerFilter.HOME_READY ->
                                "No home-ready exercises match your search."
                            loggedBeforeOnly ->
                                "No logged exercises match your search."
                            else ->
                                "No exercises match your search."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.forEach { (muscleKey, list) ->
                        item(key = "mg_$muscleKey") {
                            Text(
                                formatMuscleGroupHeader(muscleKey),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { it.id }) { exercise ->
                            val inRoutines = state.routines.count { r -> exercise.id in r.exerciseIds }
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenExerciseDetail(exercise.id) }
                                        .padding(16.dp)
                                ) {
                                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${exercise.equipment.displayLabel()} · ${exercise.pushOrPull.displayLabel()}" +
                                            if (inRoutines > 0) " · Used in $inRoutines routine(s)" else "",
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

private fun exercisesGroupedFiltered(
    state: WeightLibraryState,
    query: String,
    loggedBeforeOnly: Boolean,
    loggedIds: Set<String>,
    equipmentFilter: WeightExercisePickerFilter,
    ownedEquipment: List<com.erv.app.data.OwnedEquipmentItem>,
    enabledPackIds: Set<String>,
): List<Pair<String, List<WeightExercise>>> {
    val q = query.trim().lowercase()
    val visibleExercises = filterWeightExercisesForPicker(
        exercises = state.exercises,
        filter = equipmentFilter,
        ownedEquipment = ownedEquipment,
        enabledPackIds = enabledPackIds,
    )
    val filtered = visibleExercises.filter { ex ->
        val usedOk = !loggedBeforeOnly || ex.id in loggedIds
        val matchOk = q.isEmpty() ||
            ex.name.lowercase().contains(q) ||
            ex.muscleGroup.lowercase().contains(q) ||
            ex.equipment.displayLabel().lowercase().contains(q) ||
            ex.pushOrPull.displayLabel().lowercase().contains(q)
        usedOk && matchOk
    }
    return groupExercisesByMuscle(filtered)
}

@Composable
private fun RoutinesTabBody(
    state: WeightLibraryState,
    onEdit: (WeightRoutine) -> Unit,
    onDeleteRequest: (WeightRoutine) -> Unit,
    onStartRoutine: (WeightRoutine) -> Unit
) {
    var expandedRoutineIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    if (state.routines.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No routines yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.routines.sortedBy { it.name.lowercase() }, key = { it.id }) { routine ->
            val expanded = routine.id in expandedRoutineIds
            val exerciseNames = routine.exerciseIds.map { id ->
                state.exerciseById(id)?.name ?: "Missing exercise"
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${routine.exerciseIds.size} ${if (routine.exerciseIds.size == 1) "exercise" else "exercises"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                expandedRoutineIds =
                                    if (expanded) expandedRoutineIds - routine.id
                                    else expandedRoutineIds + routine.id
                            }
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = if (expanded) "Hide routine exercises" else "Show routine exercises"
                            )
                        }
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        if (exerciseNames.isEmpty()) {
                            Text(
                                "No exercises — tap Edit to add",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                exerciseNames.forEach { name ->
                                    Text(
                                        "• $name",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(onClick = { onStartRoutine(routine) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start")
                            }
                            IconButton(onClick = { onEdit(routine) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit routine")
                            }
                            IconButton(onClick = { onDeleteRequest(routine) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete routine")
                            }
                        }
                    }
                }
            }
        }
    }
}

