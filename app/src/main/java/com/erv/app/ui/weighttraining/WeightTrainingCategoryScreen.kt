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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.toFinishedLiveSession
import com.erv.app.weighttraining.exerciseIdsUsedInAnyLog
import com.erv.app.weighttraining.exercisesGroupedByMuscle
import com.erv.app.weighttraining.formatMuscleGroupHeader
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private enum class WeightTrainingTab { Exercises, Routines }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrainingCategoryScreen(
    repository: WeightRepository,
    liveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    userPreferences: UserPreferences,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenExerciseDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val loadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
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

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val s = repository.currentState()
        WeightSync.publishExercises(relayPool, signer, s.exercises)
        WeightSync.publishRoutines(relayPool, signer, s.routines)
    }

    suspend fun pushDayLog(date: LocalDate) {
        if (relayPool == null || signer == null) return
        val log = repository.currentState().logFor(date) ?: return
        WeightSync.publishDayLog(relayPool, signer, log)
    }

    LaunchedEffect(relayPool, signer?.publicKey) {
        if (relayPool != null && signer != null) {
            WeightSync.fetchFromNetwork(relayPool, signer, signer.publicKey)?.let { remote ->
                repository.replaceAll(remote)
            }
        }
    }

    var completedSessionForSummary by remember { mutableStateOf<WeightWorkoutSession?>(null) }

    val summarySession = completedSessionForSummary
    if (summarySession != null) {
        WeightWorkoutSummaryFullScreen(
            session = summarySession,
            logDate = LocalDate.now(),
            library = state,
            loadUnit = loadUnit,
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
                        containerColor = headerMid,
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
            WeightLiveWorkoutScreen(
                modifier = Modifier.fillMaxSize(),
                draft = expandedDraft,
                library = state,
                loadUnit = loadUnit,
                onLeaveWorkoutUi = {
                    val draft = liveWorkoutViewModel.activeDraft.value
                    if (draft != null) {
                        val noExercises = draft.exerciseOrder.isEmpty()
                        val noLoggedSets = draft.setsByExerciseId.values.all { rows ->
                            rows.isEmpty() || rows.all { it.reps <= 0 }
                        }
                        if (noExercises && noLoggedSets) {
                            liveWorkoutViewModel.clearDraft()
                        } else {
                            liveWorkoutViewModel.setLiveWorkoutUiExpanded(false)
                        }
                    }
                },
                onDiscardWorkout = { liveWorkoutViewModel.clearDraft() },
                onFinish = {
                    scope.launch {
                        val current = liveWorkoutViewModel.activeDraft.value ?: return@launch
                        val session = current.toFinishedLiveSession() ?: return@launch
                        repository.addWorkout(LocalDate.now(), session)
                        liveWorkoutViewModel.clearDraft()
                        pushDayLog(LocalDate.now())
                        completedSessionForSummary = session
                    }
                },
                onAddExercise = { id -> liveWorkoutViewModel.addExercise(id) },
                onRemoveExerciseAt = { idx -> liveWorkoutViewModel.removeExerciseAt(idx) },
                onMoveExerciseUp = { idx -> liveWorkoutViewModel.moveExerciseUp(idx) },
                onMoveExerciseDown = { idx -> liveWorkoutViewModel.moveExerciseDown(idx) },
                onSaveSets = { exerciseId, sets -> liveWorkoutViewModel.setSetsForExercise(exerciseId, sets) }
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
            RoutineEditorDialog(
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
                        repository.upsertRoutine(routine)
                        pushMasters()
                        snackbarHostState.showSnackbar("Routine saved")
                    }
                    routineBeingEdited = null
                }
            )
        }
    }
}

@Composable
private fun ExercisesTabBody(
    state: WeightLibraryState,
    onOpenExerciseDetail: (String) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var loggedBeforeOnly by rememberSaveable { mutableStateOf(false) }
    val loggedIds = remember(state.logs) { state.exerciseIdsUsedInAnyLog() }
    val grouped = remember(state.exercises, state.logs, searchQuery, loggedBeforeOnly, loggedIds) {
        exercisesGroupedFiltered(state, searchQuery, loggedBeforeOnly, loggedIds)
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
                label = { Text("All exercises") }
            )
            FilterChip(
                selected = loggedBeforeOnly,
                onClick = { loggedBeforeOnly = true },
                label = { Text("Logged before") }
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
    loggedIds: Set<String>
): List<Pair<String, List<WeightExercise>>> {
    val q = query.trim().lowercase()
    return state.exercisesGroupedByMuscle()
        .map { (muscle, exercises) ->
            muscle to exercises.filter { ex ->
                val usedOk = !loggedBeforeOnly || ex.id in loggedIds
                val matchOk = q.isEmpty() ||
                    ex.name.lowercase().contains(q) ||
                    ex.muscleGroup.lowercase().contains(q) ||
                    ex.equipment.displayLabel().lowercase().contains(q) ||
                    ex.pushOrPull.displayLabel().lowercase().contains(q)
                usedOk && matchOk
            }
        }
        .filter { (_, list) -> list.isNotEmpty() }
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

@Composable
private fun RoutineEditorDialog(
    initial: WeightRoutine,
    exerciseLibrary: List<WeightExercise>,
    title: String,
    onDismiss: () -> Unit,
    onSave: (WeightRoutine) -> Unit
) {
    var routineName by remember(initial.id) { mutableStateOf(initial.name) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var exerciseIds by remember(initial.id) { mutableStateOf(initial.exerciseIds.toMutableList()) }
    var showPickExercise by remember { mutableStateOf(false) }

    if (showPickExercise) {
        WeightPickExerciseDialog(
            exercises = exerciseLibrary,
            excludeIds = exerciseIds.toSet(),
            onDismiss = { showPickExercise = false },
            onPick = { id ->
                exerciseIds = exerciseIds.toMutableList().also { it.add(id) }
                showPickExercise = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { routineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Exercises (order)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showPickExercise = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Add exercise from library")
                }
                Spacer(Modifier.height(8.dp))
                exerciseIds.forEachIndexed { index, id ->
                    val ex = exerciseLibrary.firstOrNull { it.id == id }
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex?.name ?: "Unknown id", style = MaterialTheme.typography.bodyLarge)
                                ex?.let {
                                    Text(
                                        "${it.muscleGroup} · ${it.equipment.displayLabel()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val m = exerciseIds.toMutableList()
                                        val t = m[index]
                                        m[index] = m[index - 1]
                                        m[index - 1] = t
                                        exerciseIds = m
                                    }
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Up") }
                            IconButton(
                                onClick = {
                                    if (index < exerciseIds.lastIndex) {
                                        val m = exerciseIds.toMutableList()
                                        val t = m[index]
                                        m[index] = m[index + 1]
                                        m[index + 1] = t
                                        exerciseIds = m
                                    }
                                },
                                enabled = index < exerciseIds.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Down") }
                            IconButton(
                                onClick = {
                                    exerciseIds = exerciseIds.toMutableList().also { it.removeAt(index) }
                                }
                            ) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Button(
                        onClick = {
                            if (routineName.isBlank()) return@Button
                            onSave(
                                WeightRoutine(
                                    id = initial.id,
                                    name = routineName.trim(),
                                    exerciseIds = exerciseIds.toList(),
                                    notes = notes.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = routineName.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }
}
