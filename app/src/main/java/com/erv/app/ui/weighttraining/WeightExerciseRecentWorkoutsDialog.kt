package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DEFAULT_MAX_SESSIONS = 5

/**
 * Read-only list of the most recent logged sessions for one exercise (from synced day logs).
 * Shown during live / manual workout editing for quick reference.
 */
@Composable
fun WeightExerciseRecentWorkoutsDialog(
    exerciseId: String,
    exerciseName: String,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    maxSessions: Int = DEFAULT_MAX_SESSIONS,
    onDismiss: () -> Unit
) {
    val rows = remember(exerciseId, library.logs) {
        library.historyForExercise(exerciseId).take(maxSessions)
    }
    val loadSuffix = weightLoadUnitSuffix(loadUnit)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Recent workouts",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Last $maxSessions sessions (newest first). Current session is not included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                if (rows.isEmpty()) {
                    Text(
                        "No past sessions for this lift in your logs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(rows, key = { "${it.logDate}_${it.workout.id}_${it.entry.exerciseId}" }) { row ->
                            val addedLoad =
                                library.exerciseById(row.entry.exerciseId)?.equipment == WeightEquipment.OTHER
                            RecentSessionBlock(
                                row = row,
                                loadUnit = loadUnit,
                                loadSuffix = loadSuffix,
                                weightIsAddedLoad = addedLoad
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun RecentSessionBlock(
    row: WeightExerciseHistoryRow,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    weightIsAddedLoad: Boolean
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
                    formatSetSummaryLine(set, idx + 1, loadUnit, loadSuffix, weightIsAddedLoad),
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
