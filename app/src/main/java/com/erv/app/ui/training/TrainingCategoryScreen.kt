package com.erv.app.ui.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.programBlocksForDate
import com.erv.app.programs.strategySummaryForDate
import com.erv.app.ui.theme.ErvHeaderRed
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingCategoryScreen(
    programsState: ProgramsLibraryState,
    workoutLibraryCount: Int,
    weightRoutineCount: Int,
    stretchRoutineCount: Int,
    cardioRoutineCount: Int,
    onBack: () -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenUnifiedWorkouts: () -> Unit,
    onOpenWeightTraining: () -> Unit,
    onOpenStretching: () -> Unit,
    onOpenCardio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val todaySummary = remember(programsState, today) {
        programsState.strategySummaryForDate(today)
    }
    val todayBlockCount = remember(programsState, today) {
        programsState.programBlocksForDate(today).size
    }
    val activeProgramName = remember(programsState) {
        programsState.activeProgramId
            ?.let { id -> programsState.programs.firstOrNull { it.id == id }?.name }
    }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Plan sessions, build workouts, and run them from one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = todaySummary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = when (todayBlockCount) {
                                0 -> "No scheduled blocks for today."
                                1 -> "1 scheduled block today."
                                else -> "$todayBlockCount scheduled blocks today."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Sessions & plans",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            item {
                TrainingHubRow(
                    icon = Icons.Default.PlaylistPlay,
                    title = "Workouts",
                    subtitle = when (workoutLibraryCount) {
                        0 -> "Compose sessions · circuits & straight sets"
                        1 -> "1 workout in library"
                        else -> "$workoutLibraryCount workouts in library"
                    },
                    onClick = onOpenUnifiedWorkouts,
                )
            }

            item {
                TrainingHubRow(
                    icon = Icons.Default.CalendarMonth,
                    title = "Programs",
                    subtitle = activeProgramName?.let { "Active: $it" }
                        ?: "Weekly plans — week view coming soon",
                    onClick = onOpenPrograms,
                )
            }

            item {
                Text(
                    text = "Building blocks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Silo routines and catalogs feed the workout composer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }

            item {
                TrainingHubRow(
                    icon = Icons.Default.FitnessCenter,
                    title = "Weight training",
                    subtitle = when (weightRoutineCount) {
                        0 -> "Exercises & routines"
                        1 -> "1 routine"
                        else -> "$weightRoutineCount routines"
                    },
                    onClick = onOpenWeightTraining,
                )
            }

            item {
                TrainingHubRow(
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    title = "Cardio",
                    subtitle = when (cardioRoutineCount) {
                        0 -> "Activities & routines"
                        1 -> "1 routine"
                        else -> "$cardioRoutineCount routines"
                    },
                    onClick = onOpenCardio,
                )
            }

            item {
                TrainingHubRow(
                    icon = Icons.Default.FavoriteBorder,
                    title = "Stretching",
                    subtitle = when (stretchRoutineCount) {
                        0 -> "Mobility catalog & routines"
                        1 -> "1 routine"
                        else -> "$stretchRoutineCount routines"
                    },
                    onClick = onOpenStretching,
                )
            }
        }
    }
}

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
