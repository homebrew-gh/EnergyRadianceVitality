@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.erv.app.ui.stretching

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioRepository
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramRepository
import com.erv.app.programs.programBlocksForDate
import com.erv.app.stretching.BUILD_FOR_TODAY_MINUTES
import com.erv.app.stretching.BuiltStretchRoutine
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.TodayTraining
import com.erv.app.stretching.TrainingPiece
import com.erv.app.stretching.buildStretchesForToday
import com.erv.app.stretching.describe
import com.erv.app.stretching.todayTraining
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.workouts.Workout
import com.erv.app.workouts.WorkoutItem
import com.erv.app.workouts.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/** Set by the workout finish summary; the Stretching screen opens Build For Today and clears it. */
object StretchBuildForTodayRequests {
    val pending = MutableStateFlow(false)

    fun request() {
        pending.value = true
    }
}

private fun Workout.trainingPiece(): TrainingPiece {
    val items = segments.flatMap { it.items }
    return TrainingPiece(
        weightExerciseIds = items.filterIsInstance<WorkoutItem.Weight>().map { it.exerciseId },
        cardioActivities = items.filterIsInstance<WorkoutItem.Cardio>().map { it.cardio.activity },
    )
}

/**
 * Today's training for Build For Today: what was logged today (lifting and cardio), the workout
 * running now, and today's plan (Weekly Planner blocks). Any store that can't be read just adds
 * nothing.
 */
internal suspend fun gatherTodayTraining(
    weightRepository: WeightRepository?,
    cardioRepository: CardioRepository?,
    workoutRepository: WorkoutRepository,
    programRepository: ProgramRepository?,
    today: LocalDate = LocalDate.now(),
): TodayTraining {
    val weightState = runCatching { weightRepository?.currentState() }.getOrNull()
    val cardioState = runCatching { cardioRepository?.currentState() }.getOrNull()
    val workoutState = runCatching { workoutRepository.currentState() }.getOrNull()
    val programs = runCatching { programRepository?.currentState() }.getOrNull()

    val logged = TrainingPiece(
        weightExerciseIds = weightState?.logFor(today)?.workouts.orEmpty()
            .flatMap { it.entries }
            .filter { it.sets.isNotEmpty() || it.hiitBlock != null }
            .map { it.exerciseId },
        cardioActivities = cardioState?.logFor(today)?.sessions.orEmpty()
            .mapNotNull { it.activity.builtin?.name },
    )
    val run = workoutState?.activeRun?.takeIf { it.startedAtEpochSeconds != null }
    val running = run?.workoutSnapshot?.trainingPiece() ?: TrainingPiece()

    val blocks = programs?.let { p -> runCatching { p.programBlocksForDate(today) }.getOrDefault(emptyList()) }.orEmpty()
    val plannedWeight = mutableListOf<String>()
    val plannedCardio = mutableListOf<String>()
    for (b in blocks) {
        when (b.kind) {
            ProgramBlockKind.WORKOUT -> b.workoutId?.let { id -> workoutState?.workoutById(id) }?.trainingPiece()?.let {
                plannedWeight += it.weightExerciseIds
                plannedCardio += it.cardioActivities
            }
            ProgramBlockKind.WEIGHT -> {
                plannedWeight += b.weightExerciseIds
                b.weightRoutineId?.let { rid -> weightState?.routines?.firstOrNull { it.id == rid } }?.let {
                    plannedWeight += it.exerciseIds
                }
            }
            ProgramBlockKind.CARDIO -> b.cardioActivity?.let { plannedCardio += it }
            else -> Unit
        }
    }
    return todayTraining(
        logged = logged,
        running = running,
        planned = TrainingPiece(plannedWeight, plannedCardio),
        weightGroupOf = { id -> weightState?.exerciseById(id)?.muscleGroup },
    )
}

/**
 * Pick a length, see the stretches Build For Today chose for [today], then play them now or save
 * them as a routine.
 */
@Composable
internal fun BuildForTodayDialog(
    today: TodayTraining,
    catalog: List<StretchCatalogEntry>,
    onDismiss: () -> Unit,
    onPlay: (BuiltStretchRoutine) -> Unit,
    onSave: (BuiltStretchRoutine) -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(10) }
    val built = remember(today, catalog, minutes) { buildStretchesForToday(catalog, today, minutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Build for today") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    today.describe(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FormSectionLabel("How long")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BUILD_FOR_TODAY_MINUTES.forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text("$m min") }
                        )
                    }
                }
                if (built == null) {
                    Text(
                        "No stretches in the catalog fit today yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(built.name, style = MaterialTheme.typography.titleMedium)
                    val total = built.totalSeconds
                    Text(
                        buildString {
                            append("About ${(total + 59) / 60} min")
                            if (built.settleAndRest) append(" with Settle In and Rest")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    built.entries.forEachIndexed { i, e ->
                        val hold = built.holds.getOrNull(i) ?: 30
                        val holdLabel = if (hold >= 90 && hold % 60 == 0) "${hold / 60} min" else "${hold}s"
                        Text(
                            buildString {
                                append(titleCaseStretchLabel(e.name))
                                append(" · ").append(holdLabel)
                                if (e.requiresBothSides) append(" each side")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { built?.let(onPlay) }, enabled = built != null) { Text("Play") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = { built?.let(onSave) }, enabled = built != null) { Text("Save as routine") }
            }
        }
    )
}
