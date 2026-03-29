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
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LibraryStateMerge
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPayloadDigestStore
import com.erv.app.nostr.RelayPool
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.unifiedroutines.linkFor
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.weighttraining.groupExercisesByMuscle
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
    liveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onReturnToUnifiedRun: (String) -> Unit = {},
    onOpenLog: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val heartRateBle = LocalHeartRateBle.current
    val unifiedState by unifiedRoutineRepository.state.collectAsState(initial = UnifiedRoutineLibraryState())
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    val fallbackBodyWeightKg by userPreferences.fallbackBodyWeightKg.collectAsState(initial = null)
    val liveDraft by liveWorkoutViewModel.activeDraft.collectAsState()
    val liveWorkoutUiExpanded by liveWorkoutViewModel.liveWorkoutUiExpanded.collectAsState()
    val state by repository.state.collectAsState(initial = WeightLibraryState())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by rememberSaveable { mutableStateOf(WeightTrainingTab.Exercises.name) }
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
                    pushDayLog(LocalDate.now())
                    completedSessionForSummary = null
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
                onRecordExerciseActivity = { id -> liveWorkoutViewModel.recordExerciseFocus(id) },
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
                            rows.isEmpty() || rows.all { it.reps <= 0 }
                        }
                        val noHiit = draft.hiitBlocksByExerciseId.isEmpty()
                        if (noExercises && noLoggedSets && noHiit) {
                            val returnedToUnified = returnToUnifiedRun()
                            heartRateBle.discardWorkoutRecording()
                            liveWorkoutViewModel.clearDraft()
                            if (!returnedToUnified && activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                                onBack()
                            }
                        } else {
                            val returnedToUnified = returnToUnifiedRun()
                            liveWorkoutViewModel.setLiveWorkoutUiExpanded(false)
                            if (!returnedToUnified && activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                                onBack()
                            }
                        }
                    }
                },
                onDiscardWorkout = {
                    val returnedToUnified =
                        if (activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                            onReturnToUnifiedRun(activeUnifiedSession.routineId)
                            true
                        } else {
                            false
                        }
                    heartRateBle.discardWorkoutRecording()
                    liveWorkoutViewModel.clearDraft()
                    if (!returnedToUnified && activeUnifiedSession != null && activeUnifiedWeightBlockId != null) {
                        onBack()
                    }
                },
                onFinish = {
                    scope.launch {
                        val current = liveWorkoutViewModel.activeDraft.value ?: return@launch
                        val hr = heartRateBle.takeWorkoutHeartRateSummary()
                        val end = weightNowEpochSeconds()
                        val segments = buildWeightExerciseHrSegments(
                            current.exerciseFocusMarks,
                            current.startedAtEpochSeconds,
                            end,
                            hr?.samples.orEmpty()
                        )
                        val session = current.toFinishedLiveSession(
                            heartRate = hr,
                            heartRateExerciseSegments = segments
                        ) ?: return@launch
                        val estimatedKcal = WeightCalorieEstimator.estimateKcal(session, fallbackBodyWeightKg)
                        val today = LocalDate.now()
                        val activeUnifiedSession = unifiedState.activeSession
                        val activeUnifiedBlockId = activeUnifiedSession?.lastLaunchedBlockId?.takeIf { blockId ->
                            unifiedState
                                .routineById(activeUnifiedSession.routineId)
                                ?.blocks
                                ?.firstOrNull { it.id == blockId }
                                ?.type == com.erv.app.unifiedroutines.UnifiedRoutineBlockType.WEIGHT
                        }
                        val storedSession = if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                            val recap = unifiedState.sessionById(activeUnifiedSession.sessionId)
                            session.copy(
                                estimatedKcal = estimatedKcal,
                                unifiedLink = recap?.linkFor(activeUnifiedBlockId)
                            )
                        } else {
                            session.copy(estimatedKcal = estimatedKcal)
                        }
                        repository.addWorkout(today, storedSession)
                        if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                            unifiedRoutineRepository.attachLoggedBlock(
                                routineId = activeUnifiedSession.routineId,
                                blockId = activeUnifiedBlockId,
                                logDate = today.toString(),
                                entryId = storedSession.id
                            )
                        }
                        pushDayLog(today)
                        if (activeUnifiedSession != null && activeUnifiedBlockId != null) {
                            onReturnToUnifiedRun(activeUnifiedSession.routineId)
                            liveWorkoutViewModel.clearDraft()
                        } else {
                            liveWorkoutViewModel.clearDraft()
                            completedSessionForSummary = storedSession
                        }
                    }
                },
                onAddExercise = { id -> liveWorkoutViewModel.addExercise(id) },
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
                label = { Text("All Exercises") }
            )
            FilterChip(
                selected = loggedBeforeOnly,
                onClick = { loggedBeforeOnly = true },
                label = { Text("Logged Before") }
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
                label = { Text("Home-Ready") }
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
            val preview = routine.exerciseIds.mapNotNull { id -> state.exerciseById(id)?.name }
                .take(4)
                .joinToString(" → ")
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (preview.isNotEmpty()) {
                            "$preview${if (routine.exerciseIds.size > 4) "…" else ""}"
                        } else {
                            "No exercises — tap Edit to add"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

