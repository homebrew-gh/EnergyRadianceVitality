package com.erv.app.ui.unifiedroutines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioQuickLaunch
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioModality
import com.erv.app.cardio.CardioRoutine
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioQuickTimerMode
import com.erv.app.cardio.displayName
import com.erv.app.cardio.formatCardioAveragePaceForSession
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.resolveSnapshot
import com.erv.app.cardio.summaryLabel
import com.erv.app.data.UserPreferences
import com.erv.app.data.BodyWeightUnit
import com.erv.app.hr.LocalHeartRateBle
import com.erv.app.programs.decodeUnifiedRoutineLaunch
import com.erv.app.programs.encodeStretchLaunch
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.StretchSession
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchRoutine
import com.erv.app.stretching.StretchingRepository
import com.erv.app.stretching.stretchCategoryDisplayLabel
import com.erv.app.ui.cardio.OutdoorRuckPackWeightDialog
import com.erv.app.ui.cardio.CardioQuickLaunchEditorDialog
import com.erv.app.ui.stretching.StretchPickStretchDialog
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.weighttraining.WeightPickExerciseDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutFgsDisclosureDialog
import com.erv.app.ui.weighttraining.WeightLiveWorkoutViewModel
import com.erv.app.ui.cardio.CardioLiveWorkoutViewModel
import com.erv.app.unifiedroutines.UnifiedRoutine
import com.erv.app.unifiedroutines.UnifiedRoutineBlock
import com.erv.app.unifiedroutines.UnifiedRoutineBlockType
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedRoutineRepository
import com.erv.app.unifiedroutines.UnifiedWorkoutSession
import com.erv.app.unifiedroutines.isBlockCompleted
import com.erv.app.unifiedroutines.nowUnifiedRoutineEpochSeconds
import com.erv.app.unifiedroutines.resolveCardioLaunch
import com.erv.app.unifiedroutines.resolveStretchLaunch
import com.erv.app.unifiedroutines.resolveWeightRoutine
import com.erv.app.unifiedroutines.toUnifiedRoutine
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedRoutineCategoryScreen(
    repository: UnifiedRoutineRepository,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    unifiedState: UnifiedRoutineLibraryState,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    userPreferences: UserPreferences,
    onBack: () -> Unit,
    onOpenRun: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val heartRateBle = LocalHeartRateBle.current
    var editorTarget by remember { mutableStateOf<UnifiedRoutine?>(null) }
    var editorStartsAdHoc by remember { mutableStateOf(false) }
    var deletingRoutine by remember { mutableStateOf<UnifiedRoutine?>(null) }
    var importType by remember { mutableStateOf<UnifiedRoutineBlockType?>(null) }

    LaunchedEffect(Unit) {
        val raw = userPreferences.consumeProgramDashboardUnifiedRoutineLaunchJson() ?: return@LaunchedEffect
        val payload = decodeUnifiedRoutineLaunch(raw) ?: return@LaunchedEffect
        if (payload.createNew) {
            editorStartsAdHoc = true
            editorTarget = UnifiedRoutine(name = "")
            return@LaunchedEffect
        }
        val targetRoutineId = payload.routineId ?: return@LaunchedEffect
        for (attempt in 0..20) {
            val routine = repository.currentState().routineById(targetRoutineId)
            if (routine != null) {
                onOpenRun(routine.id)
                return@LaunchedEffect
            }
            delay(80)
        }
        snackbarHostState.showSnackbar("Unified workout not found.")
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editorStartsAdHoc = false
                editorTarget = UnifiedRoutine(name = "")
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add routine")
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Unified Workouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            unifiedState.activeSession?.let { session ->
                val activeRoutine = session.routineSnapshot ?: unifiedState.routineById(session.routineId)
                if (activeRoutine != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Active session", style = MaterialTheme.typography.titleMedium)
                                Text(activeRoutine.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${session.completedBlockIds.size} / ${activeRoutine.blocks.size} blocks complete",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { onOpenRun(activeRoutine.id) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.size(8.dp))
                                        Text("Resume")
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                heartRateBle.discardUnifiedWorkoutRecording()
                                                repository.clearActiveSession()
                                                snackbarHostState.showSnackbar("Active session cleared")
                                            }
                                        }
                                    ) { Text("Clear") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Build from existing routines", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Use saved weight, cardio, or stretch routines as migration shortcuts while moving away from the siloed workflow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { importType = UnifiedRoutineBlockType.WEIGHT },
                                enabled = weightState.routines.isNotEmpty()
                            ) { Text("Import Weight") }
                            FilledTonalButton(
                                onClick = { importType = UnifiedRoutineBlockType.CARDIO },
                                enabled = cardioState.routines.isNotEmpty()
                            ) { Text("Import Cardio") }
                            FilledTonalButton(
                                onClick = { importType = UnifiedRoutineBlockType.STRETCH },
                                enabled = stretchState.routines.isNotEmpty()
                            ) { Text("Import Stretch") }
                        }
                    }
                }
            }
            if (unifiedState.routines.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No unified workouts yet.", style = MaterialTheme.typography.titleMedium)
                            Text(
                            "Create one to combine weight training, cardio, and stretching into a single launchable workout flow.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(unifiedState.routines.sortedBy { it.name.lowercase() }, key = { it.id }) { routine ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                unifiedRoutinePreviewLine(routine, weightState, cardioState, stretchState, stretchCatalog),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            routine.notes?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalButton(onClick = { onOpenRun(routine.id) }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Start")
                                }
                                IconButton(onClick = { editorTarget = routine }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit routine")
                                }
                                IconButton(onClick = { deletingRoutine = routine }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete routine")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editorTarget?.let { target ->
        UnifiedRoutineEditorDialog(
            initial = target.takeIf { it.name.isNotBlank() || it.blocks.isNotEmpty() || !it.notes.isNullOrBlank() },
            weightRepository = weightRepository,
            stretchingRepository = stretchingRepository,
            weightState = weightState,
            cardioState = cardioState,
            stretchState = stretchState,
            stretchCatalog = stretchCatalog,
            onNotify = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
            onDismiss = {
                editorTarget = null
                editorStartsAdHoc = false
            },
            onSave = { routine ->
                scope.launch {
                    repository.upsertRoutine(routine)
                    snackbarHostState.showSnackbar(
                        if (target.id == routine.id && unifiedState.routines.any { it.id == routine.id }) {
                            "Workout updated"
                        } else {
                            "Workout saved"
                        }
                    )
                }
                editorTarget = null
                editorStartsAdHoc = false
            },
            onStartNow = if (editorStartsAdHoc) { routine ->
                scope.launch {
                    repository.startAdHocSession(routine)
                    heartRateBle.startUnifiedWorkoutRecording()
                    onOpenRun(routine.id)
                }
                editorTarget = null
                editorStartsAdHoc = false
            } else null
        )
    }

    deletingRoutine?.let { routine ->
        AlertDialog(
            onDismissRequest = { deletingRoutine = null },
            title = { Text("Delete workout?") },
            text = { Text("Remove \"${routine.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteRoutine(routine.id)
                            snackbarHostState.showSnackbar("Workout deleted")
                        }
                        deletingRoutine = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingRoutine = null }) { Text("Cancel") }
            }
        )
    }

    importType?.let { type ->
        ImportExistingRoutineDialog(
            type = type,
            weightRoutines = weightState.routines,
            cardioRoutines = cardioState.routines,
            stretchRoutines = stretchState.routines,
            onDismiss = { importType = null },
            onImport = { imported ->
                scope.launch {
                    repository.upsertRoutine(imported)
                    snackbarHostState.showSnackbar("Imported into unified workouts")
                }
                importType = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedRoutineRunScreen(
    routineId: String,
    repository: UnifiedRoutineRepository,
    unifiedState: UnifiedRoutineLibraryState,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    userPreferences: UserPreferences,
    weightLiveWorkoutViewModel: WeightLiveWorkoutViewModel,
    cardioLiveWorkoutViewModel: CardioLiveWorkoutViewModel,
    onBack: () -> Unit,
    onOpenSummary: (String) -> Unit,
    onOpenWeightCategory: () -> Unit,
    onOpenCardioCategory: () -> Unit,
    onOpenStretchCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val heartRateBle = LocalHeartRateBle.current
    val heartRateBannerExpanded by userPreferences.heartRateBannerExpanded.collectAsState(initial = true)
    val session = unifiedState.activeSession?.takeIf { it.routineId == routineId }
    val recapSession = session?.let { unifiedState.sessionById(it.sessionId) }
    val routine = session?.routineSnapshot ?: unifiedState.routineById(routineId)
    val fgsDisclosureSeen by userPreferences.weightLiveWorkoutFgsDisclosureSeen.collectAsState(initial = false)
    val cardioDistanceUnit by userPreferences.cardioDistanceUnit.collectAsState(initial = CardioDistanceUnit.MILES)
    val weightLoadUnit by userPreferences.weightTrainingLoadUnit.collectAsState(initial = BodyWeightUnit.LB)
    var pendingWeightBlock by remember { mutableStateOf<UnifiedRoutineBlock?>(null) }
    var pendingRuckCardioSession by remember { mutableStateOf<CardioActiveTimerSession.Single?>(null) }
    var pendingRuckCardioBlockId by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(routineId, routine) {
        if (routine != null && (session == null || session.routineId != routineId)) {
            repository.startSession(routineId)
            heartRateBle.startUnifiedWorkoutRecording()
        }
    }
    LaunchedEffect(session?.sessionId) {
        tick = 0
        while (session != null) {
            delay(1_000L)
            tick++
        }
    }

    if (routine == null) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Unified Workout") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ErvHeaderRed,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Routine not found.")
            }
        }
        return
    }

    fun launchWeightBlock(block: UnifiedRoutineBlock) {
        if (cardioLiveWorkoutViewModel.hasActiveTimer) {
            scope.launch { snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.") }
            return
        }
        if (!fgsDisclosureSeen) {
            pendingWeightBlock = block
            return
        }
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
            scope.launch { repository.setLastLaunchedBlock(routine.id, block.id) }
            onOpenWeightCategory()
            return
        }
        val resolved = block.resolveWeightRoutine(weightState)
        if (resolved == null) {
            scope.launch { snackbarHostState.showSnackbar("This weight block does not have any usable exercises.") }
            return
        }
        if (!weightLiveWorkoutViewModel.tryStartFromRoutine(
                routine = resolved,
                library = weightState,
                suppressNotification = true,
            )
        ) {
            scope.launch { snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.") }
            return
        }
        weightLiveWorkoutViewModel.setLiveWorkoutUiExpanded(true)
        scope.launch { repository.setLastLaunchedBlock(routine.id, block.id) }
        onOpenWeightCategory()
    }

    fun startOrQueueUnifiedCardio(sessionToStart: CardioActiveTimerSession, blockId: String) {
        if (weightLiveWorkoutViewModel.hasLiveSession) {
            scope.launch { snackbarHostState.showSnackbar("Finish or cancel your live weight workout first.") }
            return
        }
        if (cardioLiveWorkoutViewModel.hasActiveTimer) {
            cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
            scope.launch { repository.setLastLaunchedBlock(routine.id, blockId) }
            onOpenCardioCategory()
            return
        }
        if (!cardioLiveWorkoutViewModel.tryStartSession(
                session = sessionToStart,
                suppressNotification = true,
            )
        ) {
            scope.launch { snackbarHostState.showSnackbar("Finish or cancel your cardio timer first.") }
            return
        }
        cardioLiveWorkoutViewModel.setCardioLiveUiExpanded(true)
        scope.launch { repository.setLastLaunchedBlock(routine.id, blockId) }
        onOpenCardioCategory()
    }

    fun launchCardioBlock(block: UnifiedRoutineBlock) {
        val sessionToStart = block.resolveCardioLaunch(cardioState)
        if (sessionToStart == null) {
            scope.launch { snackbarHostState.showSnackbar("This cardio block is missing an activity or routine.") }
            return
        }
        if (
            sessionToStart is CardioActiveTimerSession.Single &&
            sessionToStart.draft.activity.builtin == CardioBuiltinActivity.RUCK &&
            sessionToStart.draft.modality == CardioModality.OUTDOOR
        ) {
            pendingRuckCardioSession = sessionToStart
            pendingRuckCardioBlockId = block.id
            return
        }
        startOrQueueUnifiedCardio(sessionToStart, block.id)
    }

    fun launchStretchBlock(block: UnifiedRoutineBlock) {
        val payload = block.resolveStretchLaunch()
        if (payload == null) {
            scope.launch { snackbarHostState.showSnackbar("This stretch block is missing a routine or stretches.") }
            return
        }
        scope.launch {
            repository.setLastLaunchedBlock(routine.id, block.id)
            userPreferences.setProgramDashboardStretchLaunchJson(encodeStretchLaunch(payload))
            onOpenStretchCategory()
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard workout?") },
            text = { Text("Your unified workout will be cleared and not saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        scope.launch {
                            heartRateBle.discardUnifiedWorkoutRecording()
                            repository.clearActiveSession()
                            onBack()
                        }
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(routine.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                            tint = if (heartRateBannerExpanded) Color(0xFFFF8A80) else Color.White
                        )
                    }
                    TextButton(onClick = { showDiscardConfirm = true }) {
                        Text("Discard", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Progress", style = MaterialTheme.typography.titleSmall)
                        session?.startedAtEpochSeconds?.let { startedAt ->
                            val totalElapsedText = formatUnifiedElapsedClock(
                                remember(tick, startedAt) {
                                    (nowUnifiedRoutineEpochSeconds() - startedAt).coerceAtLeast(0)
                                }
                            )
                            Text(
                                "Total workout: $totalElapsedText",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "${session?.completedBlockIds?.size ?: 0} / ${routine.blocks.size} blocks completed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        routine.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            items(routine.blocks, key = { it.id }) { block ->
                val completed = session?.isBlockCompleted(block.id) == true
                val completedSummary = if (completed) {
                    completedUnifiedBlockSummary(
                        blockId = block.id,
                        recapSession = recapSession,
                        weightState = weightState,
                        cardioState = cardioState,
                        stretchState = stretchState,
                        cardioDistanceUnit = cardioDistanceUnit,
                        weightLoadUnit = weightLoadUnit
                    )
                } else {
                    null
                }
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            block.title?.takeIf { it.isNotBlank() } ?: unifiedBlockTypeLabel(block.type),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            completedSummary ?: unifiedBlockSummary(block, weightState, cardioState, stretchState, stretchCatalog),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (completedSummary != null) {
                            Text(
                                "Planned: ${unifiedBlockSummary(block, weightState, cardioState, stretchState, stretchCatalog)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        block.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (completed) "Completed" else "Pending",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        when (block.type) {
                                            UnifiedRoutineBlockType.WEIGHT -> launchWeightBlock(block)
                                            UnifiedRoutineBlockType.CARDIO -> launchCardioBlock(block)
                                            UnifiedRoutineBlockType.STRETCH -> launchStretchBlock(block)
                                        }
                                    }
                                ) { Text("Open") }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            repository.markBlockCompleted(routine.id, block.id, !completed)
                                        }
                                    }
                                ) {
                                    Text(if (completed) "Undo" else "Mark Done")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val activeSessionId = unifiedState.activeSession?.sessionId
                                repository.finishSession(
                                    routineId = routine.id,
                                    heartRate = heartRateBle.takeUnifiedWorkoutHeartRateSummary()
                                )
                                if (activeSessionId != null) {
                                    onOpenSummary(activeSessionId)
                                } else {
                                    snackbarHostState.showSnackbar("No active unified session found.")
                                }
                            }
                        }
                    ) { Text("Finish Session") }
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.startSession(routine.id)
                                heartRateBle.startUnifiedWorkoutRecording()
                                snackbarHostState.showSnackbar("Progress reset")
                            }
                        }
                    ) { Text("Reset Progress") }
                }
            }
        }
    }

    WeightLiveWorkoutFgsDisclosureDialog(
        visible = pendingWeightBlock != null,
        onDismiss = { pendingWeightBlock = null },
        onContinue = {
            val block = pendingWeightBlock ?: return@WeightLiveWorkoutFgsDisclosureDialog
            scope.launch { userPreferences.setWeightLiveWorkoutFgsDisclosureSeen(true) }
            pendingWeightBlock = null
            launchWeightBlock(block)
        }
    )

    pendingRuckCardioSession?.let { pendingSession ->
        OutdoorRuckPackWeightDialog(
            quickLaunchName = pendingSession.draft.title,
            defaultRuckLoadKg = pendingSession.draft.ruckLoadKg,
            onDismiss = {
                pendingRuckCardioSession = null
                pendingRuckCardioBlockId = null
            },
            onStart = { kg ->
                pendingRuckCardioSession = null
                val blockId = pendingRuckCardioBlockId
                pendingRuckCardioBlockId = null
                if (blockId != null) {
                    startOrQueueUnifiedCardio(
                        pendingSession.copy(draft = pendingSession.draft.copy(ruckLoadKg = kg)),
                        blockId
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UnifiedRoutineEditorDialog(
    initial: UnifiedRoutine?,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (UnifiedRoutine) -> Unit,
    onStartNow: ((UnifiedRoutine) -> Unit)? = null,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    val blocks = remember(initial?.id) { mutableStateListOf<UnifiedRoutineBlock>().apply { addAll(initial?.blocks.orEmpty()) } }
    var blockBeingEdited by remember { mutableStateOf<UnifiedRoutineBlock?>(null) }
    var blockTypeToAdd by remember { mutableStateOf<UnifiedRoutineBlockType?>(null) }

    fun buildRoutine(): UnifiedRoutine {
        val trimmedName = name.trim().ifBlank { "Unified Workout Session" }
        return UnifiedRoutine(
            id = initial?.id ?: UUID.randomUUID().toString(),
            name = trimmedName,
            notes = notes.trim().ifBlank { null },
            blocks = blocks.toList(),
            createdAtEpochSeconds = initial?.createdAtEpochSeconds ?: System.currentTimeMillis() / 1000,
            lastModifiedEpochSeconds = System.currentTimeMillis() / 1000
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Unified Workout" else "Edit unified workout") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Text("Blocks", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { blockTypeToAdd = UnifiedRoutineBlockType.WEIGHT }) { Text("Add Weight") }
                    FilledTonalButton(onClick = { blockTypeToAdd = UnifiedRoutineBlockType.CARDIO }) { Text("Add Cardio") }
                    FilledTonalButton(onClick = { blockTypeToAdd = UnifiedRoutineBlockType.STRETCH }) { Text("Add Stretch") }
                }
                if (blocks.isEmpty()) {
                    Text(
                        "No blocks yet. Mix weight, cardio, and stretch sections in the order you want to run them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                blocks.forEachIndexed { index, block ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                block.title?.takeIf { it.isNotBlank() } ?: unifiedBlockTypeLabel(block.type),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                unifiedBlockSummary(block, weightState, cardioState, stretchState, stretchCatalog),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val swap = blocks[index]
                                        blocks[index] = blocks[index - 1]
                                        blocks[index - 1] = swap
                                    },
                                    enabled = index > 0
                                ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                                IconButton(
                                    onClick = {
                                        val swap = blocks[index]
                                        blocks[index] = blocks[index + 1]
                                        blocks[index + 1] = swap
                                    },
                                    enabled = index < blocks.lastIndex
                                ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                                IconButton(onClick = { blockBeingEdited = block }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit block")
                                }
                                IconButton(onClick = { blocks.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete block")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(buildRoutine())
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                onStartNow?.let { startNow ->
                    TextButton(onClick = { startNow(buildRoutine()) }) { Text("Start Now") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )

    val editing = blockBeingEdited
    if (editing != null) {
        UnifiedRoutineBlockEditorDialog(
            initial = editing,
            weightRepository = weightRepository,
            stretchingRepository = stretchingRepository,
            weightState = weightState,
            cardioState = cardioState,
            stretchState = stretchState,
            stretchCatalog = stretchCatalog,
            onNotify = onNotify,
            onDismiss = { blockBeingEdited = null },
            onSave = { updated ->
                val idx = blocks.indexOfFirst { it.id == editing.id }
                if (idx >= 0) blocks[idx] = updated
                blockBeingEdited = null
            }
        )
    }

    blockTypeToAdd?.let { type ->
        UnifiedRoutineBlockEditorDialog(
            initial = UnifiedRoutineBlock(type = type),
            weightRepository = weightRepository,
            stretchingRepository = stretchingRepository,
            weightState = weightState,
            cardioState = cardioState,
            stretchState = stretchState,
            stretchCatalog = stretchCatalog,
            onNotify = onNotify,
            onDismiss = { blockTypeToAdd = null },
            onSave = { added ->
                blocks += added
                blockTypeToAdd = null
            }
        )
    }
}

/** Resolves the editable exercise list when opening the weight block editor (routine ref and/or inline ids). */
private fun initialWeightListForUnifiedBlock(
    initial: UnifiedRoutineBlock,
    weightState: WeightLibraryState,
): List<String> {
    val rid = initial.weightRoutineId?.takeIf { it.isNotBlank() }
    if (rid != null) {
        val r = weightState.routines.firstOrNull { it.id == rid }
        if (r != null) {
            return r.exerciseIds.filter { weightState.exerciseById(it) != null }
        }
    }
    return initial.weightExerciseIds.filter { weightState.exerciseById(it) != null }
}

private fun initialStretchListForUnifiedBlock(
    initial: UnifiedRoutineBlock,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
): List<String> {
    val valid = stretchCatalog.map { it.id }.toSet()
    val rid = initial.stretchRoutineId?.takeIf { it.isNotBlank() }
    if (rid != null) {
        val r = stretchState.routines.firstOrNull { it.id == rid }
        if (r != null) return r.stretchIds.filter { it in valid }
    }
    return initial.stretchCatalogIds.filter { it in valid }
}

private fun initialHoldSecondsForStretchBlock(
    initial: UnifiedRoutineBlock,
    stretchState: StretchLibraryState,
): String {
    val rid = initial.stretchRoutineId?.takeIf { it.isNotBlank() }
    if (rid != null) {
        val r = stretchState.routines.firstOrNull { it.id == rid }
        if (r != null) return r.holdSecondsPerStretch.coerceIn(5, 300).toString()
    }
    return initial.stretchHoldSecondsPerStretch.coerceIn(5, 300).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedRoutineBlockEditorDialog(
    initial: UnifiedRoutineBlock,
    weightRepository: WeightRepository,
    stretchingRepository: StretchingRepository,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (UnifiedRoutineBlock) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val blockWeightSignature = remember(initial.weightRoutineId, initial.weightExerciseIds) {
        "${initial.weightRoutineId.orEmpty()}|${initial.weightExerciseIds.joinToString(",")}"
    }
    val blockStretchSignature = remember(initial.stretchRoutineId, initial.stretchCatalogIds) {
        "${initial.stretchRoutineId.orEmpty()}|${initial.stretchCatalogIds.joinToString(",")}"
    }
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title.orEmpty()) }
    var notes by rememberSaveable(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var weightRoutineId by rememberSaveable(initial.id, blockWeightSignature) {
        mutableStateOf(initial.weightRoutineId.orEmpty())
    }
    var weightIds by rememberSaveable(initial.id, blockWeightSignature) {
        mutableStateOf(initialWeightListForUnifiedBlock(initial, weightState))
    }
    var showSaveAsWeightRoutineDialog by remember { mutableStateOf(false) }
    var saveAsRoutineName by remember { mutableStateOf("") }
    var cardioRoutineId by rememberSaveable(initial.id) { mutableStateOf(initial.cardioRoutineId.orEmpty()) }
    var cardioQuickLaunchId by rememberSaveable(initial.id) { mutableStateOf(initial.cardioQuickLaunchId.orEmpty()) }
    var cardioInlineQuickLaunch by remember(initial.id) {
        mutableStateOf(initial.cardioInlineQuickLaunch ?: initial.toInlineCardioQuickLaunch(cardioState))
    }
    var stretchRoutineId by rememberSaveable(initial.id, blockStretchSignature) {
        mutableStateOf(initial.stretchRoutineId.orEmpty())
    }
    var stretchIds by rememberSaveable(initial.id, blockStretchSignature) {
        mutableStateOf(initialStretchListForUnifiedBlock(initial, stretchState, stretchCatalog))
    }
    var holdSeconds by rememberSaveable(initial.id, blockStretchSignature) {
        mutableStateOf(initialHoldSecondsForStretchBlock(initial, stretchState))
    }
    var showStretchPicker by remember { mutableStateOf(false) }
    var showSaveAsStretchRoutineDialog by remember { mutableStateOf(false) }
    var saveAsStretchRoutineName by remember { mutableStateOf("") }
    var showWeightPicker by remember { mutableStateOf(false) }
    var showInlineCardioEditor by remember { mutableStateOf(false) }

    val linkedRoutineRef = weightRoutineId.takeIf { it.isNotBlank() }?.let { lid ->
        weightState.routines.firstOrNull { it.id == lid }
    }
    val linkedRoutineCanonicalIds = linkedRoutineRef?.exerciseIds
        ?.filter { id -> weightState.exerciseById(id) != null }
        .orEmpty()
    val linkedDirty = linkedRoutineRef != null && weightIds != linkedRoutineCanonicalIds

    val catalogIdSet = remember(stretchCatalog) { stretchCatalog.map { it.id }.toSet() }
    val linkedStretchRoutineRef = stretchRoutineId.takeIf { it.isNotBlank() }?.let { lid ->
        stretchState.routines.firstOrNull { it.id == lid }
    }
    val holdParsedForStretchUi = holdSeconds.toIntOrNull()?.coerceIn(5, 300) ?: 30
    val linkedStretchCanonicalIds = linkedStretchRoutineRef?.stretchIds
        ?.filter { it in catalogIdSet }
        .orEmpty()
    val linkedStretchDirty = linkedStretchRoutineRef != null && (
        stretchIds != linkedStretchCanonicalIds ||
            holdParsedForStretchUi != linkedStretchRoutineRef.holdSecondsPerStretch.coerceIn(5, 300)
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${unifiedBlockTypeLabel(initial.type)} Block") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                when (initial.type) {
                    UnifiedRoutineBlockType.WEIGHT -> {
                        Text(
                            "Pick a saved routine to load its exercises below, or add exercises yourself. " +
                                "Changing the list does not change the saved routine unless you choose update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RoutineDropdown(
                            label = "Weight routine",
                            currentValue = weightRoutineId,
                            displayValue = weightState.routines.firstOrNull { it.id == weightRoutineId }?.name
                                ?: if (weightRoutineId.isBlank()) "None" else "Unknown routine",
                            options = weightState.routines.map { it.id to it.name },
                            onValueSelected = {
                                weightRoutineId = it
                                if (it.isNotBlank()) {
                                    weightIds = weightState.routines.firstOrNull { r -> r.id == it }
                                        ?.exerciseIds
                                        ?.filter { id -> weightState.exerciseById(id) != null }
                                        .orEmpty()
                                }
                            }
                        )
                        if (linkedRoutineRef != null && linkedDirty) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "This list no longer matches \"${linkedRoutineRef.name}\" in Weight Training.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val basis = linkedRoutineRef
                                            scope.launch {
                                                weightRepository.upsertRoutine(
                                                    basis.copy(exerciseIds = weightIds.toList())
                                                )
                                                onNotify("Updated \"${basis.name}\" in Weight Training.")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Update saved routine") }
                                    TextButton(
                                        onClick = {
                                            weightRoutineId = ""
                                            onNotify(
                                                "Detached from saved routine. Tap Save on this screen to keep this block as its own exercise list."
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Keep changes on this workout only") }
                                }
                            }
                        }
                        FilledTonalButton(
                            onClick = { showWeightPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add Exercise") }
                        if (weightIds.isNotEmpty()) {
                            Text(
                                "Exercises run top to bottom when you start this block.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        weightIds.forEachIndexed { index, id ->
                            val exercise = weightState.exerciseById(id)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(exercise?.name ?: "Unknown exercise")
                                    exercise?.let {
                                        Text(
                                            "${it.pushOrPull.displayLabel()} · ${it.equipment.displayLabel()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val m = weightIds.toMutableList()
                                                val t = m[index]
                                                m[index] = m[index - 1]
                                                m[index - 1] = t
                                                weightIds = m
                                            }
                                        },
                                        enabled = index > 0
                                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move exercise up") }
                                    IconButton(
                                        onClick = {
                                            if (index < weightIds.lastIndex) {
                                                val m = weightIds.toMutableList()
                                                val t = m[index]
                                                m[index] = m[index + 1]
                                                m[index + 1] = t
                                                weightIds = m
                                            }
                                        },
                                        enabled = index < weightIds.lastIndex
                                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move exercise down") }
                                    IconButton(onClick = {
                                        weightIds = weightIds.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove exercise")
                                    }
                                }
                            }
                        }
                        if (weightRoutineId.isBlank() && weightIds.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    saveAsRoutineName = title.trim().ifBlank {
                                        weightIds.firstOrNull()?.let { exId ->
                                            weightState.exerciseById(exId)?.name
                                        }.orEmpty()
                                    }
                                    showSaveAsWeightRoutineDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save exercise order as weight routine…") }
                        }
                    }
                    UnifiedRoutineBlockType.CARDIO -> {
                        RoutineDropdown(
                            label = "Cardio saved source",
                            currentValue = when {
                                cardioInlineQuickLaunch != null -> "inline"
                                cardioQuickLaunchId.isNotBlank() -> "quick:$cardioQuickLaunchId"
                                cardioRoutineId.isNotBlank() -> "routine:$cardioRoutineId"
                                else -> ""
                            },
                            displayValue = cardioSavedSourceLabel(
                                cardioRoutineId = cardioRoutineId,
                                cardioQuickLaunchId = cardioQuickLaunchId,
                                cardioInlineQuickLaunch = cardioInlineQuickLaunch,
                                cardioState = cardioState
                            ),
                            options = cardioSavedSourceOptions(
                                cardioState = cardioState,
                                cardioInlineQuickLaunch = cardioInlineQuickLaunch
                            ),
                            onValueSelected = { selected ->
                                when {
                                    selected == "inline" -> {
                                        cardioRoutineId = ""
                                        cardioQuickLaunchId = ""
                                    }
                                    selected.startsWith("routine:") -> {
                                        cardioRoutineId = selected.removePrefix("routine:")
                                        cardioQuickLaunchId = ""
                                        cardioInlineQuickLaunch = null
                                    }
                                    selected.startsWith("quick:") -> {
                                        cardioQuickLaunchId = selected.removePrefix("quick:")
                                        cardioRoutineId = ""
                                        cardioInlineQuickLaunch = null
                                    }
                                    else -> {
                                        cardioRoutineId = ""
                                        cardioQuickLaunchId = ""
                                        cardioInlineQuickLaunch = null
                                    }
                                }
                            }
                        )
                        Text(
                            "Need timer details like indoor/outdoor, count up vs down, pace, or ruck weight? Configure a block-specific cardio setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = { showInlineCardioEditor = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (cardioInlineQuickLaunch == null) "Configure Direct Cardio Setup" else "Edit Direct Cardio Setup")
                        }
                        cardioInlineQuickLaunch?.let { inlineQuickLaunch ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Direct setup", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        inlineQuickLaunch.summaryLabel(CardioDistanceUnit.MILES),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(
                                        onClick = {
                                            cardioInlineQuickLaunch = null
                                        }
                                    ) { Text("Clear direct setup") }
                                }
                            }
                        }
                    }
                    UnifiedRoutineBlockType.STRETCH -> {
                        Text(
                            "Pick a saved routine to load stretches below, or tap Add stretch. " +
                                "Edits do not change Stretching routines unless you update the saved routine.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        RoutineDropdown(
                            label = "Stretch routine",
                            currentValue = stretchRoutineId,
                            displayValue = stretchState.routines.firstOrNull { it.id == stretchRoutineId }?.name
                                ?: if (stretchRoutineId.isBlank()) "None" else "Unknown routine",
                            options = stretchState.routines.map { it.id to it.name },
                            onValueSelected = {
                                stretchRoutineId = it
                                if (it.isNotBlank()) {
                                    val r = stretchState.routines.firstOrNull { ro -> ro.id == it }
                                    if (r != null) {
                                        stretchIds = r.stretchIds.filter { sid -> sid in catalogIdSet }
                                        holdSeconds = r.holdSecondsPerStretch.coerceIn(5, 300).toString()
                                    }
                                }
                            }
                        )
                        if (linkedStretchRoutineRef != null && linkedStretchDirty) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "This setup no longer matches \"${linkedStretchRoutineRef.name}\" in Stretching.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val basis = linkedStretchRoutineRef
                                            scope.launch {
                                                stretchingRepository.updateRoutine(
                                                    basis.copy(
                                                        stretchIds = stretchIds.toList(),
                                                        holdSecondsPerStretch = holdParsedForStretchUi
                                                    )
                                                )
                                                onNotify("Updated \"${basis.name}\" in Stretching.")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Update saved routine") }
                                    TextButton(
                                        onClick = {
                                            stretchRoutineId = ""
                                            onNotify(
                                                "Detached from saved routine. Tap Save on this screen to keep this block as its own stretch list."
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Keep changes on this workout only") }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = holdSeconds,
                            onValueChange = { holdSeconds = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Hold seconds per stretch") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = { showStretchPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Add stretch") }
                        if (stretchIds.isNotEmpty()) {
                            Text(
                                "Stretches run top to bottom when you start this block.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        stretchIds.forEachIndexed { index, sid ->
                            val entry = stretchCatalog.firstOrNull { it.id == sid }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry?.name ?: "Unknown stretch")
                                    entry?.let {
                                        val side =
                                            if (it.requiresBothSides) "Both sides" else "Single hold"
                                        Text(
                                            "${stretchCategoryDisplayLabel(it.category)} · $side",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val m = stretchIds.toMutableList()
                                                val t = m[index]
                                                m[index] = m[index - 1]
                                                m[index - 1] = t
                                                stretchIds = m
                                            }
                                        },
                                        enabled = index > 0
                                    ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move stretch up") }
                                    IconButton(
                                        onClick = {
                                            if (index < stretchIds.lastIndex) {
                                                val m = stretchIds.toMutableList()
                                                val t = m[index]
                                                m[index] = m[index + 1]
                                                m[index + 1] = t
                                                stretchIds = m
                                            }
                                        },
                                        enabled = index < stretchIds.lastIndex
                                    ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move stretch down") }
                                    IconButton(onClick = {
                                        stretchIds = stretchIds.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove stretch")
                                    }
                                }
                            }
                        }
                        if (stretchRoutineId.isBlank() && stretchIds.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    saveAsStretchRoutineName = title.trim().ifBlank {
                                        stretchIds.firstOrNull()?.let { id ->
                                            stretchCatalog.firstOrNull { it.id == id }?.name
                                        }.orEmpty()
                                    }
                                    showSaveAsStretchRoutineDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save stretch order as routine…") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val inlineQuickLaunch =
                        if (initial.type == UnifiedRoutineBlockType.CARDIO &&
                            cardioRoutineId.isBlank() &&
                            cardioQuickLaunchId.isBlank()
                        ) {
                            cardioInlineQuickLaunch
                        } else {
                            null
                        }
                    val linkedId = weightRoutineId.takeIf { it.isNotBlank() }
                    val libRoutine = linkedId?.let { lid ->
                        weightState.routines.firstOrNull { it.id == lid }
                    }
                    val canonicalLib = libRoutine?.exerciseIds
                        ?.filter { id -> weightState.exerciseById(id) != null }
                        .orEmpty()
                    val useLinkedRef = libRoutine != null && weightIds == canonicalLib
                    val holdParsedBlock = holdSeconds.toIntOrNull()?.coerceIn(5, 300) ?: 30
                    val (persistStretchRoutineId, persistStretchCatalogIds, persistStretchHold) =
                        if (initial.type == UnifiedRoutineBlockType.STRETCH) {
                            val libSt = stretchRoutineId.takeIf { it.isNotBlank() }?.let { lid ->
                                stretchState.routines.firstOrNull { it.id == lid }
                            }
                            val canonicalSt = libSt?.stretchIds
                                ?.filter { it in catalogIdSet }
                                .orEmpty()
                            val useLinkedSt = libSt != null && stretchIds == canonicalSt &&
                                holdParsedBlock == libSt.holdSecondsPerStretch.coerceIn(5, 300)
                            Triple(
                                libSt?.id.takeIf { useLinkedSt },
                                if (useLinkedSt) emptyList() else stretchIds,
                                if (useLinkedSt && libSt != null) {
                                    libSt.holdSecondsPerStretch.coerceIn(5, 300)
                                } else {
                                    holdParsedBlock
                                }
                            )
                        } else {
                            Triple(
                                initial.stretchRoutineId,
                                initial.stretchCatalogIds,
                                initial.stretchHoldSecondsPerStretch
                            )
                        }
                    onSave(
                        initial.copy(
                            title = title.trim().ifBlank { null },
                            notes = notes.trim().ifBlank { null },
                            weightRoutineId = libRoutine?.id.takeIf { useLinkedRef },
                            weightExerciseIds = if (useLinkedRef) emptyList() else weightIds,
                            cardioActivity = inlineQuickLaunch?.activity?.builtin?.name,
                            cardioRoutineId = cardioRoutineId.ifBlank { null },
                            cardioQuickLaunchId = cardioQuickLaunchId.ifBlank { null },
                            cardioInlineQuickLaunch = inlineQuickLaunch,
                            stretchRoutineId = persistStretchRoutineId,
                            stretchCatalogIds = persistStretchCatalogIds,
                            stretchHoldSecondsPerStretch = persistStretchHold,
                            targetMinutes = if (initial.type == UnifiedRoutineBlockType.CARDIO) {
                                if (inlineQuickLaunch?.timerMode == CardioQuickTimerMode.COUNT_DOWN) {
                                    inlineQuickLaunch.countDownMinutes
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showSaveAsWeightRoutineDialog && initial.type == UnifiedRoutineBlockType.WEIGHT) {
        AlertDialog(
            onDismissRequest = { showSaveAsWeightRoutineDialog = false },
            title = { Text("Save as weight routine") },
            text = {
                OutlinedTextField(
                    value = saveAsRoutineName,
                    onValueChange = { saveAsRoutineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = saveAsRoutineName.trim()
                        if (trimmed.isEmpty() || weightIds.isEmpty()) return@TextButton
                        scope.launch {
                            val routine = WeightRoutine(
                                id = UUID.randomUUID().toString(),
                                name = trimmed,
                                exerciseIds = weightIds.toList()
                            )
                            weightRepository.upsertRoutine(routine)
                            weightRoutineId = routine.id
                            weightIds = emptyList()
                            saveAsRoutineName = ""
                            showSaveAsWeightRoutineDialog = false
                            onNotify("Saved to Weight Training — open Routines there to run or edit it.")
                        }
                    },
                    enabled = saveAsRoutineName.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsWeightRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSaveAsStretchRoutineDialog && initial.type == UnifiedRoutineBlockType.STRETCH) {
        AlertDialog(
            onDismissRequest = { showSaveAsStretchRoutineDialog = false },
            title = { Text("Save as stretch routine") },
            text = {
                OutlinedTextField(
                    value = saveAsStretchRoutineName,
                    onValueChange = { saveAsStretchRoutineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = saveAsStretchRoutineName.trim()
                        if (trimmed.isEmpty() || stretchIds.isEmpty()) return@TextButton
                        scope.launch {
                            val holdSec = holdSeconds.toIntOrNull()?.coerceIn(5, 300) ?: 30
                            val routine = StretchRoutine(
                                id = UUID.randomUUID().toString(),
                                name = trimmed,
                                stretchIds = stretchIds.toList(),
                                holdSecondsPerStretch = holdSec
                            )
                            stretchingRepository.addRoutine(routine)
                            stretchRoutineId = routine.id
                            stretchIds = emptyList()
                            saveAsStretchRoutineName = ""
                            showSaveAsStretchRoutineDialog = false
                            onNotify("Saved to Stretching — open Routines there to run or edit it.")
                        }
                    },
                    enabled = saveAsStretchRoutineName.trim().isNotEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsStretchRoutineDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStretchPicker && initial.type == UnifiedRoutineBlockType.STRETCH) {
        StretchPickStretchDialog(
            catalog = stretchCatalog,
            excludeIds = stretchIds.toSet(),
            onDismiss = { showStretchPicker = false },
            onPick = { id ->
                stretchIds = stretchIds + id
                showStretchPicker = false
            }
        )
    }

    if (showWeightPicker) {
        WeightPickExerciseDialog(
            exercises = weightState.exercises,
            excludeIds = weightIds.toSet(),
            onDismiss = { showWeightPicker = false },
            onPick = { id ->
                weightIds = weightIds + id
                showWeightPicker = false
            }
        )
    }

    if (showInlineCardioEditor) {
        CardioQuickLaunchEditorDialog(
            existing = cardioInlineQuickLaunch,
            creating = cardioInlineQuickLaunch == null,
            state = cardioState,
            distanceUnit = CardioDistanceUnit.MILES,
            onDismiss = { showInlineCardioEditor = false },
            onSave = { quickLaunch ->
                cardioInlineQuickLaunch = quickLaunch
                cardioRoutineId = ""
                cardioQuickLaunchId = ""
                showInlineCardioEditor = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineDropdown(
    label: String,
    currentValue: String,
    displayValue: String,
    options: List<Pair<String, String>>,
    onValueSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onValueSelected("")
                    expanded = false
                }
            )
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onValueSelected(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportExistingRoutineDialog(
    type: UnifiedRoutineBlockType,
    weightRoutines: List<WeightRoutine>,
    cardioRoutines: List<CardioRoutine>,
    stretchRoutines: List<StretchRoutine>,
    onDismiss: () -> Unit,
    onImport: (UnifiedRoutine) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${unifiedBlockTypeLabel(type)} Routine") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (type) {
                    UnifiedRoutineBlockType.WEIGHT -> weightRoutines.sortedBy { it.name.lowercase() }.forEach { routine ->
                        TextButton(
                            onClick = { onImport(routine.toUnifiedRoutine()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(routine.name, modifier = Modifier.fillMaxWidth()) }
                    }
                    UnifiedRoutineBlockType.CARDIO -> cardioRoutines.sortedBy { it.name.lowercase() }.forEach { routine ->
                        TextButton(
                            onClick = { onImport(routine.toUnifiedRoutine()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(routine.name, modifier = Modifier.fillMaxWidth()) }
                    }
                    UnifiedRoutineBlockType.STRETCH -> stretchRoutines.sortedBy { it.name.lowercase() }.forEach { routine ->
                        TextButton(
                            onClick = { onImport(routine.toUnifiedRoutine()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(routine.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun unifiedRoutinePreviewLine(
    routine: UnifiedRoutine,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
): String =
    routine.blocks.take(3).joinToString(" -> ") {
        unifiedBlockSummary(it, weightState, cardioState, stretchState, stretchCatalog)
    }.ifBlank { "No blocks yet." } + if (routine.blocks.size > 3) "..." else ""

private fun completedUnifiedBlockSummary(
    blockId: String,
    recapSession: UnifiedWorkoutSession?,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    cardioDistanceUnit: CardioDistanceUnit,
    weightLoadUnit: BodyWeightUnit,
): String? {
    val recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap =
        recapSession?.blocks?.firstOrNull { recapBlock -> recapBlock.blockId == blockId } ?: return null
    return resolveCompletedWeightSummary(recap, weightState, weightLoadUnit)
        ?: resolveCompletedCardioSummary(recap, cardioState, cardioDistanceUnit)
        ?: resolveCompletedStretchSummary(recap, stretchState)
}

private fun resolveCompletedWeightSummary(
    recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap,
    weightState: WeightLibraryState,
    loadUnit: BodyWeightUnit,
): String? {
    val session = resolveCompletedWeightSession(recap, weightState) ?: return null
    val volume = session.totalVolumeLoadTimesReps(loadUnit)
    return buildString {
        append("Completed: ${session.entries.size} exercises · ${session.totalSetCount()} sets")
        if (volume > 0.5) {
            append(" · Volume ~${volume.toInt()} ${weightLoadUnitSuffix(loadUnit)}×reps")
        }
    }
}

private fun resolveCompletedCardioSummary(
    recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap,
    cardioState: CardioLibraryState,
    distanceUnit: CardioDistanceUnit,
): String? {
    val session = resolveCompletedCardioSession(recap, cardioState) ?: return null
    return buildString {
        append("Completed: ${session.activity.displayLabel} · ${session.durationMinutes} min")
        session.distanceMeters?.takeIf { it > 1.0 }?.let {
            append(" · ${formatCardioDistanceFromMeters(it, distanceUnit)}")
        }
        formatCardioAveragePaceForSession(session, distanceUnit, null)?.let { pace ->
            append(" · $pace")
        }
    }
}

private fun resolveCompletedStretchSummary(
    recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap,
    stretchState: StretchLibraryState,
): String? {
    val session = resolveCompletedStretchSession(recap, stretchState) ?: return null
    return buildString {
        append("Completed: ${session.totalMinutes} min")
        session.routineName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        if (session.routineName.isNullOrBlank() && session.stretchIds.isNotEmpty()) {
            append(" · ${session.stretchIds.size} stretches")
        }
    }
}

private fun resolveCompletedWeightSession(
    recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap,
    weightState: WeightLibraryState,
): WeightWorkoutSession? {
    val date = runCatching { LocalDate.parse(recap.linkedLogDate) }.getOrNull() ?: return null
    return weightState.logFor(date)?.workouts?.firstOrNull { it.id == recap.linkedEntryId }
}

private fun resolveCompletedCardioSession(
    recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap,
    cardioState: CardioLibraryState,
): com.erv.app.cardio.CardioSession? {
    val date = runCatching { LocalDate.parse(recap.linkedLogDate) }.getOrNull() ?: return null
    return cardioState.logFor(date)?.sessions?.firstOrNull { it.id == recap.linkedEntryId }
}

private fun resolveCompletedStretchSession(
    recap: com.erv.app.unifiedroutines.UnifiedWorkoutBlockRecap,
    stretchState: StretchLibraryState,
): StretchSession? {
    val date = runCatching { LocalDate.parse(recap.linkedLogDate) }.getOrNull() ?: return null
    return stretchState.logFor(date)?.sessions?.firstOrNull { it.id == recap.linkedEntryId }
}

private fun formatUnifiedElapsedClock(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun unifiedBlockTypeLabel(type: UnifiedRoutineBlockType): String = when (type) {
    UnifiedRoutineBlockType.WEIGHT -> "Weight Training"
    UnifiedRoutineBlockType.CARDIO -> "Cardio"
    UnifiedRoutineBlockType.STRETCH -> "Stretch"
}

private fun unifiedBlockSummary(
    block: UnifiedRoutineBlock,
    weightState: WeightLibraryState,
    cardioState: CardioLibraryState,
    stretchState: StretchLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
): String = when (block.type) {
    UnifiedRoutineBlockType.WEIGHT -> {
        val routineName = block.weightRoutineId?.let { id -> weightState.routines.firstOrNull { it.id == id }?.name }
        val names = block.weightExerciseIds.mapNotNull { id -> weightState.exerciseById(id)?.name }
        when {
            routineName != null -> "Routine: $routineName"
            names.isNotEmpty() -> names.take(3).joinToString(", ") + if (names.size > 3) "..." else ""
            else -> "No exercises selected"
        }
    }
    UnifiedRoutineBlockType.CARDIO -> {
        val routineName = block.cardioRoutineId?.let { id -> cardioState.routines.firstOrNull { it.id == id }?.name }
        val quickLaunchName = block.cardioQuickLaunchId?.let { id ->
            cardioState.quickLaunches.firstOrNull { it.id == id }?.name
        }
        val inlineQuickLaunch = block.cardioInlineQuickLaunch
        val activityName = block.cardioActivity?.let { raw ->
            runCatching { CardioBuiltinActivity.valueOf(raw) }.getOrNull()?.displayName() ?: raw
        }
        buildString {
            append(
                when {
                    inlineQuickLaunch != null -> inlineQuickLaunch.summaryLabel(CardioDistanceUnit.MILES)
                    quickLaunchName != null -> "Quick Start: $quickLaunchName"
                    routineName != null -> "Routine: $routineName"
                    activityName != null -> activityName
                    else -> "No cardio selected"
                }
            )
            if (inlineQuickLaunch == null && quickLaunchName == null) {
                block.targetMinutes?.let { append(" · ~${it} min") }
            }
        }
    }
    UnifiedRoutineBlockType.STRETCH -> {
        val routineName = block.stretchRoutineId?.let { id -> stretchState.routines.firstOrNull { it.id == id }?.name }
        val stretchById = stretchCatalog.associateBy { it.id }
        val names = block.stretchCatalogIds.mapNotNull { stretchById[it]?.name }
        when {
            routineName != null -> "Routine: $routineName"
            names.isNotEmpty() -> buildString {
                append(names.take(3).joinToString(", "))
                if (names.size > 3) append("...")
            }
            else -> "No stretches selected"
        }
    }
}

private fun cardioSavedSourceLabel(
    cardioRoutineId: String,
    cardioQuickLaunchId: String,
    cardioInlineQuickLaunch: CardioQuickLaunch?,
    cardioState: CardioLibraryState,
): String = when {
    cardioInlineQuickLaunch != null -> "Direct setup: ${cardioInlineQuickLaunch.name}"
    cardioQuickLaunchId.isNotBlank() ->
        cardioState.quickLaunches.firstOrNull { it.id == cardioQuickLaunchId }?.let { "Quick Start: ${it.name}" }
            ?: "Unknown quick start"
    cardioRoutineId.isNotBlank() ->
        cardioState.routines.firstOrNull { it.id == cardioRoutineId }?.let { "Routine: ${it.name}" }
            ?: "Unknown routine"
    else -> "None"
}

private fun cardioSavedSourceOptions(
    cardioState: CardioLibraryState,
    cardioInlineQuickLaunch: CardioQuickLaunch?,
): List<Pair<String, String>> =
    buildList {
        cardioInlineQuickLaunch?.let { add("inline" to "Direct setup: ${it.name}") }
        cardioState.routines
            .sortedBy { it.name.lowercase() }
            .forEach { add("routine:${it.id}" to "Routine: ${it.name}") }
        cardioState.quickLaunches
            .sortedBy { it.name.lowercase() }
            .forEach { add("quick:${it.id}" to "Quick Start: ${it.name}") }
    }

private fun UnifiedRoutineBlock.toInlineCardioQuickLaunch(
    cardioState: CardioLibraryState,
): CardioQuickLaunch? {
    if (!cardioRoutineId.isNullOrBlank() || !cardioQuickLaunchId.isNullOrBlank()) return null
    val builtin = cardioActivity
        ?.let { runCatching { CardioBuiltinActivity.valueOf(it) }.getOrNull() }
        ?: return null
    val snapshot = cardioState.resolveSnapshot(builtin = builtin, customId = null)
    val timerMode = if ((targetMinutes ?: 0) > 0) {
        CardioQuickTimerMode.COUNT_DOWN
    } else {
        CardioQuickTimerMode.COUNT_UP
    }
    return CardioQuickLaunch(
        id = UUID.randomUUID().toString(),
        name = title?.takeIf { it.isNotBlank() } ?: snapshot.displayLabel,
        activity = snapshot,
        modality = CardioModality.OUTDOOR,
        treadmill = null,
        timerMode = timerMode,
        countDownMinutes = if (timerMode == CardioQuickTimerMode.COUNT_DOWN) {
            targetMinutes?.coerceIn(1, 24 * 60)
        } else {
            null
        },
        outdoorPaceSpeed = null,
        outdoorPaceSpeedUnit = null,
        defaultRuckLoadKg = null
    )
}
