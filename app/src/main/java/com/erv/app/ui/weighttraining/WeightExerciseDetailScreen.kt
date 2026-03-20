package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightExerciseDetailScreen(
    exerciseId: String,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = library.exerciseById(exerciseId)
    val history = remember(exerciseId, library.logs) { library.historyForExercise(exerciseId) }
    val darkTheme = isSystemInDarkTheme()
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val loadSuffix = weightLoadUnitSuffix(loadUnit)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerMid,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (exercise != null) {
                    Text(
                        "${formatMuscleGroupHeader(exercise.muscleGroup)} · " +
                            "${exercise.equipment.displayLabel()} · ${exercise.pushOrPull.displayLabel()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "This exercise is not in your library anymore. Past sessions still appear below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Text(
                    "History from your synced logs (newest first).",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "No logged workouts include this exercise yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            } else {
                items(history, key = { "${it.logDate}_${it.workout.id}_${it.entry.exerciseId}" }) { row ->
                    ExerciseHistoryCard(row = row, loadUnit = loadUnit, loadSuffix = loadSuffix)
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryCard(
    row: WeightExerciseHistoryRow,
    loadUnit: BodyWeightUnit,
    loadSuffix: String
) {
    val dateStr = row.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val src = when (row.workout.source) {
        WeightWorkoutSource.LIVE -> "Live"
        WeightWorkoutSource.MANUAL -> "Manual"
    }
    val timeStr = historySessionTimeLabel(row.workout)
    val routine = row.workout.routineName?.takeIf { it.isNotBlank() }
    val vol = row.entry.totalVolumeLoadTimesReps(loadUnit)
    val volPart = if (vol > 0.5) " · ~${vol.toInt()} ${loadSuffix}×reps" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                dateStr,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                buildString {
                    append(src)
                    timeStr?.let { append(" · $it") }
                    routine?.let { append(" · $it") }
                    append(" · ${row.entry.sets.size} sets$volPart")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.entry.sets.forEachIndexed { idx, set ->
                Text(
                    formatSetSummaryLine(set, idx + 1, loadUnit, loadSuffix),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun historySessionTimeLabel(session: WeightWorkoutSession): String? {
    val sec = session.startedAtEpochSeconds ?: session.finishedAtEpochSeconds ?: return null
    val t = LocalTime.ofInstant(Instant.ofEpochSecond(sec), ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}
