package com.erv.app.ui.training

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.displayName
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.KeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.isoDayOfWeekLabel
import com.erv.app.programs.programBlocksForDate
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.workouts.WorkoutLibraryListContent
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutLibraryState
import com.erv.app.workouts.WorkoutPlannedItemLabelContext
import com.erv.app.workouts.WorkoutRepository
import com.erv.app.workouts.WorkoutSync
import com.erv.app.workouts.plannedExerciseLabels
import com.erv.app.workouts.duplicateCopy
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class TrainingTab { Schedule, Workouts }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingCategoryScreen(
    programsState: ProgramsLibraryState,
    workoutState: WorkoutLibraryState,
    workoutRepository: WorkoutRepository,
    weightState: WeightLibraryState,
    stretchCatalog: List<StretchCatalogEntry>,
    keyManager: KeyManager,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onPullFromRelay: suspend () -> Unit,
    onBack: () -> Unit,
    onOpenWeeklyPlanner: () -> Unit,
    onOpenComposer: (String?) -> Unit,
    onRunWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val weekStart = remember(today) {
        today.minusDays((today.dayOfWeek.value - 1).toLong())
    }
    val weekDates = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    var selectedDate by remember(today) { mutableStateOf(today) }
    val selectedBlocks = remember(programsState, selectedDate) {
        programsState.safeProgramBlocksForDate(selectedDate)
    }
    val workoutById = remember(workoutState) { workoutState.workouts.associateBy { it.id } }
    val plannedItemContext = remember(weightState, stretchCatalog) {
        WorkoutPlannedItemLabelContext(
            weightExerciseName = { id -> weightState.exercises.firstOrNull { it.id == id }?.name },
            stretchName = { id -> stretchCatalog.firstOrNull { it.id == id }?.name },
            cardioActivityLabel = { activity ->
                runCatching { CardioBuiltinActivity.valueOf(activity) }.getOrNull()?.displayName()
                    ?: activity
            },
        )
    }

    var activeTab by rememberSaveable { mutableStateOf(TrainingTab.Schedule.name) }
    val tabEnum = TrainingTab.entries.firstOrNull { it.name == activeTab } ?: TrainingTab.Schedule
    var syncingWorkouts by remember { mutableStateOf(false) }
    var workoutsTabSynced by remember { mutableStateOf(false) }
    var deletingWorkout by remember { mutableStateOf<Workout?>(null) }

    LaunchedEffect(tabEnum) {
        if (tabEnum == TrainingTab.Workouts && !workoutsTabSynced) {
            syncingWorkouts = true
            try {
                onPullFromRelay()
                workoutsTabSynced = true
            } finally {
                syncingWorkouts = false
            }
        }
    }

    suspend fun publishLibrary() {
        WorkoutSync.publishLibraryIfSignedIn(
            appContext = context.applicationContext,
            relayPool = relayPool,
            signer = signer,
            state = workoutRepository.currentState(),
            dataRelayUrls = keyManager.relayUrlsForKind30078Publish(),
        )
    }

    val darkTheme = isSystemInDarkTheme()
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark

    if (deletingWorkout != null) {
        AlertDialog(
            onDismissRequest = { deletingWorkout = null },
            title = { Text("Delete workout?") },
            text = { Text("“${deletingWorkout!!.name}” will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = deletingWorkout!!.id
                    deletingWorkout = null
                    scope.launch {
                        workoutRepository.deleteWorkout(id)
                        publishLibrary()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingWorkout = null }) { Text("Cancel") }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                if (tabEnum == TrainingTab.Workouts) {
                    FloatingActionButton(onClick = { onOpenComposer(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "New workout")
                    }
                }
            },
            topBar = {
                TopAppBar(
                    title = { Text("Training") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        when (tabEnum) {
                            TrainingTab.Schedule -> {
                                IconButton(onClick = onOpenWeeklyPlanner) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Manage plan")
                                }
                            }
                            TrainingTab.Workouts -> {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            syncingWorkouts = true
                                            try {
                                                onPullFromRelay()
                                            } finally {
                                                syncingWorkouts = false
                                            }
                                        }
                                    },
                                    enabled = !syncingWorkouts && signer != null && relayPool != null,
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync from relay")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ErvHeaderRed,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                TabRow(
                    selectedTabIndex = tabEnum.ordinal,
                    containerColor = headerDark,
                    contentColor = Color.White,
                ) {
                    TrainingTab.entries.forEach { tab ->
                        Tab(
                            selected = tabEnum == tab,
                            onClick = { activeTab = tab.name },
                            text = { Text(tab.name) },
                        )
                    }
                }

                when (tabEnum) {
                    TrainingTab.Schedule -> ScheduleTabBody(
                        weekDates = weekDates,
                        selectedDate = selectedDate,
                        onSelectDate = { selectedDate = it },
                        programsState = programsState,
                        selectedBlocks = selectedBlocks,
                        workoutById = workoutById,
                        plannedItemContext = plannedItemContext,
                        onRunWorkout = onRunWorkout,
                        onOpenWeeklyPlanner = onOpenWeeklyPlanner,
                    )

                    TrainingTab.Workouts -> WorkoutLibraryListContent(
                        state = workoutState,
                        onRun = onRunWorkout,
                        onEdit = { onOpenComposer(it) },
                        onDuplicate = { workout ->
                            scope.launch {
                                workoutRepository.upsertWorkout(workout.duplicateCopy())
                                publishLibrary()
                            }
                        },
                        onDelete = { deletingWorkout = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleTabBody(
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    programsState: ProgramsLibraryState,
    selectedBlocks: List<ProgramDayBlock>,
    workoutById: Map<String, Workout>,
    plannedItemContext: WorkoutPlannedItemLabelContext,
    onRunWorkout: (String) -> Unit,
    onOpenWeeklyPlanner: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "This Week",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weekDates.forEach { date ->
                    val blocks = programsState.safeProgramBlocksForDate(date)
                    val plannedWorkoutCount = blocks.count { it.kind == ProgramBlockKind.WORKOUT }
                    FilterChip(
                        selected = date == selectedDate,
                        onClick = { onSelectDate(date) },
                        label = {
                            Text(
                                "${isoDayOfWeekLabel(date.dayOfWeek.value).take(3)} · " +
                                    if (plannedWorkoutCount == 0) "Rest" else "$plannedWorkoutCount",
                            )
                        },
                    )
                }
            }
        }

        DayOverviewCard(
            date = selectedDate,
            blocks = selectedBlocks,
            workoutById = workoutById,
            plannedItemContext = plannedItemContext,
            onRunWorkout = onRunWorkout,
            onOpenWeeklyPlanner = onOpenWeeklyPlanner,
        )
    }
}

@Composable
private fun DayOverviewCard(
    date: LocalDate,
    blocks: List<ProgramDayBlock>,
    workoutById: Map<String, Workout>,
    plannedItemContext: WorkoutPlannedItemLabelContext,
    onRunWorkout: (String) -> Unit,
    onOpenWeeklyPlanner: () -> Unit,
) {
    val workoutBlocks = blocks.filter { it.kind == ProgramBlockKind.WORKOUT }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = isoDayOfWeekLabel(date.dayOfWeek.value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (workoutBlocks.isEmpty()) {
                Text(
                    text = if (blocks.isEmpty()) {
                        "No workout planned. Keep this as a rest day or assign a saved workout."
                    } else {
                        "No saved workout is assigned for this day yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onOpenWeeklyPlanner) {
                    Text("Assign Workout")
                }
            } else {
                workoutBlocks.forEach { block ->
                    PlannedWorkoutOverview(
                        block = block,
                        workout = block.workoutId?.let(workoutById::get),
                        plannedItemContext = plannedItemContext,
                        onRunWorkout = onRunWorkout,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannedWorkoutOverview(
    block: ProgramDayBlock,
    workout: Workout?,
    plannedItemContext: WorkoutPlannedItemLabelContext,
    onRunWorkout: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = workout?.name ?: block.title ?: "Missing Workout",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        workout?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (workout == null) {
            Text(
                text = "This planned workout is not in the local workout library yet. Sync workouts from the relay or assign a different workout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val exerciseLabels = workout.plannedExerciseLabels(plannedItemContext)
            if (exerciseLabels.isEmpty()) {
                Text(
                    text = "No exercises listed in this workout yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                exerciseLabels.forEach { label ->
                    Text(
                        text = "• $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        FilledTonalButton(
            enabled = workout != null,
            onClick = { workout?.id?.let(onRunWorkout) },
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Run")
        }
    }
}

private fun ProgramsLibraryState.safeProgramBlocksForDate(date: LocalDate): List<ProgramDayBlock> =
    runCatching { programBlocksForDate(date) }.getOrDefault(emptyList())
