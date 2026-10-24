package com.erv.app.ui.training

import com.erv.app.ui.theme.ervTopAppBarColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
                    colors = ervTopAppBarColors(),
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
                    containerColor = MaterialTheme.colorScheme.background,
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
                        today = today,
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

private val heroDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private const val HERO_EXERCISE_PREVIEW_LIMIT = 6

@Composable
private fun ScheduleTabBody(
    today: LocalDate,
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "This Week",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            WeekStrip(
                today = today,
                weekDates = weekDates,
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
                plannedWorkoutCount = { date ->
                    programsState.safeProgramBlocksForDate(date).count { it.kind == ProgramBlockKind.WORKOUT }
                },
            )
        }

        DayOverview(
            date = selectedDate,
            isToday = selectedDate == today,
            blocks = selectedBlocks,
            workoutById = workoutById,
            plannedItemContext = plannedItemContext,
            onRunWorkout = onRunWorkout,
            onOpenWeeklyPlanner = onOpenWeeklyPlanner,
        )
    }
}

/** Seven equal-width day cells: selected day is filled, today is ringed, dots mark planned workouts. */
@Composable
private fun WeekStrip(
    today: LocalDate,
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    plannedWorkoutCount: (LocalDate) -> Int,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        weekDates.forEach { date ->
            val selected = date == selectedDate
            val isToday = date == today
            val workouts = plannedWorkoutCount(date)
            val shape = RoundedCornerShape(16.dp)
            val contentColor = if (selected) scheme.onPrimary else scheme.onSurface
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) scheme.primary else scheme.surfaceContainer)
                    .then(
                        if (isToday && !selected) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier
                    )
                    .clickable { onSelectDate(date) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = buildString {
                            append(isoDayOfWeekLabel(date.dayOfWeek.value))
                            append(" ${date.dayOfMonth}")
                            if (isToday) append(", today")
                            append(
                                when (workouts) {
                                    0 -> ", no workouts"
                                    1 -> ", 1 workout"
                                    else -> ", $workouts workouts"
                                }
                            )
                        }
                    }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = isoDayOfWeekLabel(date.dayOfWeek.value).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                )
                Row(
                    modifier = Modifier.height(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(workouts.coerceAtMost(3)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (selected) scheme.onPrimary else scheme.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayOverview(
    date: LocalDate,
    isToday: Boolean,
    blocks: List<ProgramDayBlock>,
    workoutById: Map<String, Workout>,
    plannedItemContext: WorkoutPlannedItemLabelContext,
    onRunWorkout: (String) -> Unit,
    onOpenWeeklyPlanner: () -> Unit,
) {
    val dateLabel = if (isToday) "Today · ${date.format(heroDateFormatter)}" else date.format(heroDateFormatter)
    val workoutBlocks = blocks.filter { it.kind == ProgramBlockKind.WORKOUT }
    if (workoutBlocks.isEmpty()) {
        val isRestDay = blocks.all { it.kind == ProgramBlockKind.REST }
        RestDayCard(
            dateLabel = dateLabel,
            headline = if (isRestDay) "Rest Day" else "No Workout Assigned",
            icon = if (isRestDay) Icons.Default.SelfImprovement else Icons.AutoMirrored.Filled.EventNote,
            message = if (isRestDay) {
                "No workout planned. Keep this as a rest day or assign a saved workout."
            } else {
                "No saved workout is assigned for this day yet."
            },
            onOpenWeeklyPlanner = onOpenWeeklyPlanner,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            workoutBlocks.forEach { block ->
                WorkoutHeroCard(
                    dateLabel = dateLabel,
                    block = block,
                    workout = block.workoutId?.let(workoutById::get),
                    plannedItemContext = plannedItemContext,
                    onRunWorkout = onRunWorkout,
                )
            }
        }
    }
}

@Composable
private fun RestDayCard(
    dateLabel: String,
    headline: String,
    icon: ImageVector,
    message: String,
    onOpenWeeklyPlanner: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onOpenWeeklyPlanner) {
            Text("Assign Workout")
        }
    }
}

@Composable
private fun WorkoutHeroCard(
    dateLabel: String,
    block: ProgramDayBlock,
    workout: Workout?,
    plannedItemContext: WorkoutPlannedItemLabelContext,
    onRunWorkout: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary
    val onHeroMuted = onHero.copy(alpha = 0.82f)
    val exerciseLabels = remember(workout, plannedItemContext) {
        workout?.plannedExerciseLabels(plannedItemContext).orEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.labelLarge,
            color = onHeroMuted,
        )
        Text(
            text = workout?.name ?: block.title ?: "Missing Workout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = onHero,
        )
        if (workout == null) {
            Text(
                text = "This planned workout is not in the local workout library yet. Sync workouts from the relay or assign a different workout.",
                style = MaterialTheme.typography.bodyMedium,
                color = onHeroMuted,
            )
        } else {
            val meta = buildList {
                add(if (workout.segments.size == 1) "1 segment" else "${workout.segments.size} segments")
                add(if (exerciseLabels.size == 1) "1 exercise" else "${exerciseLabels.size} exercises")
                addAll(workout.tags.take(2))
            }
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = onHeroMuted,
            )
            workout.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHeroMuted,
                )
            }
            if (exerciseLabels.isEmpty()) {
                Text(
                    text = "No exercises listed in this workout yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHeroMuted,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(onHero.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    exerciseLabels.take(HERO_EXERCISE_PREVIEW_LIMIT).forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onHero,
                        )
                    }
                    val hidden = exerciseLabels.size - HERO_EXERCISE_PREVIEW_LIMIT
                    if (hidden > 0) {
                        Text(
                            text = "+$hidden more",
                            style = MaterialTheme.typography.labelMedium,
                            color = onHeroMuted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            enabled = workout != null,
            onClick = { workout?.id?.let(onRunWorkout) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = onHero,
                contentColor = scheme.primary,
                disabledContainerColor = onHero.copy(alpha = 0.3f),
                disabledContentColor = onHero.copy(alpha = 0.7f),
            ),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start Workout", style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun ProgramsLibraryState.safeProgramBlocksForDate(date: LocalDate): List<ProgramDayBlock> =
    runCatching { programBlocksForDate(date) }.getOrDefault(emptyList())
