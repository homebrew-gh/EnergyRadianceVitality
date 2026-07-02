package com.erv.app.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.isoDayOfWeekLabel
import com.erv.app.programs.programBlocksForDate
import com.erv.app.programs.strategySummaryForDate
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutItem
import com.erv.app.workouts.WorkoutLibraryState
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingCategoryScreen(
    programsState: ProgramsLibraryState,
    workoutState: WorkoutLibraryState,
    onBack: () -> Unit,
    onOpenWeeklyPlanner: () -> Unit,
    onOpenWorkoutLibrary: () -> Unit,
    onRunWorkout: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val weekStart = remember(today) {
        today.minusDays((today.dayOfWeek.value - 1).toLong())
    }
    val weekDates = remember(weekStart) { (0..6).map { weekStart.plusDays(it.toLong()) } }
    var selectedDate by remember(today) { mutableStateOf(today) }
    val selectedBlocks = remember(programsState, selectedDate) {
        programsState.safeProgramBlocksForDate(selectedDate)
    }
    val selectedWorkoutBlocks = remember(selectedBlocks) {
        selectedBlocks.filter { it.kind == ProgramBlockKind.WORKOUT }
    }
    val activeProgramName = remember(programsState) {
        programsState.activeProgramId
            ?.let { id -> programsState.programs.firstOrNull { it.id == id }?.name }
    }
    val workoutById = remember(workoutState) { workoutState.workouts.associateBy { it.id } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Training") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ErvHeaderRed,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Lay out your training week, preview planned workouts, then run the session when you're ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = activeProgramName ?: "Weekly Plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = programsState.safeStrategySummaryForDate(selectedDate),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenWeeklyPlanner) {
                            Text("Manage Plan")
                        }
                        OutlinedButton(onClick = onOpenWorkoutLibrary) {
                            Text("Workout Library")
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This Week",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
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
                            onClick = { selectedDate = date },
                            label = {
                                Text(
                                    "${isoDayOfWeekLabel(date.dayOfWeek.value).take(3)} · " +
                                        if (plannedWorkoutCount == 0) "Rest" else "$plannedWorkoutCount"
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
                onRunWorkout = onRunWorkout,
                onOpenWorkoutLibrary = onOpenWorkoutLibrary,
                onOpenWeeklyPlanner = onOpenWeeklyPlanner,
            )

            if (selectedWorkoutBlocks.isNotEmpty()) {
                Text(
                    text = "Available Workouts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TrainingHubRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    title = "Workout Library",
                    subtitle = when (workoutState.workouts.size) {
                        0 -> "No saved workouts yet"
                        1 -> "1 workout available"
                        else -> "${workoutState.workouts.size} workouts available"
                    },
                    onClick = onOpenWorkoutLibrary,
                )
            }
        }
    }
}

@Composable
private fun DayOverviewCard(
    date: LocalDate,
    blocks: List<ProgramDayBlock>,
    workoutById: Map<String, Workout>,
    onRunWorkout: (String) -> Unit,
    onOpenWorkoutLibrary: () -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenWeeklyPlanner) {
                        Text("Assign Workout")
                    }
                    TextButton(onClick = onOpenWorkoutLibrary) {
                        Text("View Library")
                    }
                }
            } else {
                workoutBlocks.forEach { block ->
                    PlannedWorkoutOverview(
                        block = block,
                        workout = block.workoutId?.let(workoutById::get),
                        onRunWorkout = onRunWorkout,
                        onOpenWorkoutLibrary = onOpenWorkoutLibrary,
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
    onRunWorkout: (String) -> Unit,
    onOpenWorkoutLibrary: () -> Unit,
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
        Text(
            text = workout?.overviewLine() ?: "This planned workout is not in the local workout library yet. Sync workouts from the relay or swap it in the library.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        workout?.segments
            ?.take(3)
            ?.filter { it.title?.isNotBlank() == true || it.items.isNotEmpty() }
            ?.forEach { segment ->
                Text(
                    text = "• ${segment.title ?: segment.kind.name.lowercase().replaceFirstChar { it.titlecase() }} · ${segment.items.size} item${if (segment.items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                enabled = workout != null,
                onClick = { workout?.id?.let(onRunWorkout) },
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Run")
            }
            TextButton(onClick = onOpenWorkoutLibrary) {
                Text("View Library")
            }
        }
    }
}

private fun Workout.overviewLine(): String {
    val itemCount = segments.sumOf { it.items.size }
    val weightCount = segments.sumOf { segment -> segment.items.count { it is WorkoutItem.Weight } }
    val cardioCount = segments.sumOf { segment -> segment.items.count { it is WorkoutItem.Cardio } }
    val mobilityCount = segments.sumOf { segment -> segment.items.count { it is WorkoutItem.Mobility } }
    val parts = buildList {
        add("${segments.size} segment${if (segments.size == 1) "" else "s"}")
        add("$itemCount item${if (itemCount == 1) "" else "s"}")
        if (weightCount > 0) add("$weightCount strength")
        if (cardioCount > 0) add("$cardioCount cardio")
        if (mobilityCount > 0) add("$mobilityCount mobility")
    }
    return parts.joinToString(" · ")
}

private fun ProgramsLibraryState.safeProgramBlocksForDate(date: LocalDate): List<ProgramDayBlock> =
    runCatching { programBlocksForDate(date) }.getOrDefault(emptyList())

private fun ProgramsLibraryState.safeStrategySummaryForDate(date: LocalDate): String =
    runCatching { strategySummaryForDate(date) }.getOrDefault("Weekly plan")

@Composable
private fun TrainingHubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
