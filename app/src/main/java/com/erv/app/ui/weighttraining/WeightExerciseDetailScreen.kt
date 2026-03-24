package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.weighttraining.maxWeightByRepBucketKg
import com.erv.app.weighttraining.kgToPounds
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightExerciseDetailScreen(
    exerciseId: String,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    repository: WeightRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = library.exerciseById(exerciseId)
    val history = remember(exerciseId, library.logs) { library.historyForExercise(exerciseId) }
    val darkTheme = isSystemInDarkTheme()
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by remember(exerciseId) { mutableStateOf(false) }
    var showDeleteConfirm by remember(exerciseId) { mutableStateOf(false) }

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val s = repository.currentState()
        WeightSync.publishExercises(relayPool, signer, s.exercises)
        WeightSync.publishRoutines(relayPool, signer, s.routines)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (exercise != null) {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit exercise")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete exercise")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerMid,
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
                val sums = exercise?.sessionSummaries.orEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Summary", style = MaterialTheme.typography.titleSmall)
                        if (sums.isEmpty()) {
                            Text(
                                "No rollups yet — they fill in as you log workouts (used for trends and charts).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val totalVolKg = sums.sumOf { it.volumeKg }
                            val bestRm = sums.mapNotNull { it.bestEstOneRmKg }.maxOrNull()
                            Text(
                                "${sums.size} logged session(s) in history",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (totalVolKg > 0.0) {
                                Text(
                                    buildString {
                                        append("Total volume (reps × weight): ")
                                        append("%.0f".format(totalVolKg))
                                        append(" kg·reps")
                                        if (loadUnit == BodyWeightUnit.LB) {
                                            append(" (~")
                                            append("%.0f".format(kgToPounds(totalVolKg)))
                                            append(" lb·reps)")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (bestRm != null) {
                                Text(
                                    "Best est. 1RM (Epley/Brzycki): " +
                                        formatWeightLoadNumber(bestRm, loadUnit) + " $loadSuffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "Last: ${sums.last().date}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                val repBuckets = remember(exerciseId, library.logs) {
                    library.maxWeightByRepBucketKg(exerciseId)
                }
                val hasAny = repBuckets.any { it.second != null }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Best weight by rep count", style = MaterialTheme.typography.titleSmall)
                        if (!hasAny) {
                            Text(
                                "No sets with weight logged yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            repBuckets.forEach { (label, kg) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (kg != null) {
                                            formatWeightLoadNumber(kg, loadUnit) + " $loadSuffix"
                                        } else {
                                            "—"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
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
                    val addedLoad = library.exerciseById(row.entry.exerciseId)?.equipment == WeightEquipment.OTHER
                    ExerciseHistoryCard(
                        row = row,
                        loadUnit = loadUnit,
                        loadSuffix = loadSuffix,
                        weightIsAddedLoad = addedLoad
                    )
                }
            }
        }
    }

    if (showEditor && exercise != null) {
        WeightExerciseEditorDialog(
            initial = exercise,
            title = "Edit exercise",
            onDismiss = { showEditor = false },
            onSave = { draft ->
                scope.launch {
                    repository.upsertExercise(draft)
                    pushMasters()
                    snackbarHostState.showSnackbar("Exercise updated")
                }
                showEditor = false
            }
        )
    }

    if (showDeleteConfirm && exercise != null) {
        val ex = exercise
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete exercise?") },
            text = { Text("Remove “${ex.name}” from your library? Routines that include it may need editing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteExercise(ex.id)
                            pushMasters()
                            snackbarHostState.showSnackbar("Exercise removed")
                            onBack()
                        }
                        showDeleteConfirm = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExerciseHistoryCard(
    row: WeightExerciseHistoryRow,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    weightIsAddedLoad: Boolean
) {
    val dateStr = row.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val src = when (row.workout.source) {
        WeightWorkoutSource.LIVE -> "Live"
        WeightWorkoutSource.MANUAL -> "Manual"
        WeightWorkoutSource.IMPORTED -> "Imported"
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
                    val hb = row.entry.hiitBlock
                    if (hb != null) append(" · ${hb.intervals} intervals$volPart")
                    else append(" · ${row.entry.sets.size} sets$volPart")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.entry.hiitBlock?.let { b ->
                Text(
                    formatHiitBlockSummaryLine(b, loadUnit, loadSuffix, weightIsAddedLoad),
                    style = MaterialTheme.typography.bodyMedium
                )
            } ?: row.entry.sets.forEachIndexed { idx, set ->
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
