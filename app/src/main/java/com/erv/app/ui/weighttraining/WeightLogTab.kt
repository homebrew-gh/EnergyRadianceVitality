package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeightLogTabContent(
    selectedDate: LocalDate,
    workouts: List<WeightWorkoutSession>,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    onEdit: (WeightWorkoutSession) -> Unit,
    onDelete: (WeightWorkoutSession) -> Unit,
    onShare: (WeightWorkoutSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val sorted = rememberSortedWorkouts(workouts)
    val dateLabel = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val sfx = weightLoadUnitSuffix(loadUnit)

    if (sorted.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No workouts for $dateLabel",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(8.dp))
            Text(
                "Tap Add workout to log training for this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(sorted, key = { it.id }) { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    when (session.source) {
                                        WeightWorkoutSource.LIVE -> "Live"
                                        WeightWorkoutSource.MANUAL -> "Manual"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                sessionTimeLabel(session)?.let { t ->
                                    Text(
                                        t,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            val vol = session.totalVolumeLoadTimesReps(loadUnit)
                            Text(
                                buildString {
                                    append("${session.totalSetCount()} sets")
                                    if (vol > 0.5) append(" · ~${vol.toInt()} ${sfx}×reps")
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                sessionExerciseSummary(session, library),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Row {
                            IconButton(onClick = { onShare(session) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = { onEdit(session) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { onDelete(session) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSortedWorkouts(workouts: List<WeightWorkoutSession>): List<WeightWorkoutSession> {
    return androidx.compose.runtime.remember(workouts) {
        workouts.sortedWith(
            compareBy<WeightWorkoutSession> { it.startedAtEpochSeconds ?: it.finishedAtEpochSeconds ?: 0L }
        )
    }
}

private fun sessionTimeLabel(session: WeightWorkoutSession): String? {
    val sec = session.startedAtEpochSeconds ?: session.finishedAtEpochSeconds ?: return null
    val t = LocalTime.ofInstant(Instant.ofEpochSecond(sec), ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}

private fun sessionExerciseSummary(session: WeightWorkoutSession, library: WeightLibraryState): String =
    session.entries.take(4).joinToString(" · ") { e ->
        val name = library.exerciseById(e.exerciseId)?.name ?: e.exerciseId
        "$name (${e.sets.size})"
    } + if (session.entries.size > 4) "…" else ""
