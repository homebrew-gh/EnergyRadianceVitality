package com.erv.app.ui.weighttraining

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.ui.theme.ervTopAppBarColors
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRecords
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.allDatedWorkouts
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.exerciseTrend
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.historyForExercise
import kotlinx.coroutines.launch

/** Items above the first history card: subtitle, last time, records, trend, history header. */
private const val HISTORY_FIRST_ITEM = 5

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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by remember(exerciseId) { mutableStateOf(false) }
    var showDeleteConfirm by remember(exerciseId) { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val urls = keyManager.relayUrlsForKind30078Publish()
        val s = repository.currentState()
        WeightSync.publishExercises(appContext, relayPool, signer, s.exercises, urls)
        WeightSync.publishRoutines(appContext, relayPool, signer, s.routines, urls)
    }

    val records = remember(exerciseId, library.logs) { WeightRecords.best(library.allDatedWorkouts(), exerciseId) }
    val recordWorkoutIds = remember(records) { records.map { it.workoutId }.toSet() }
    val trend = remember(exerciseId, library.logs) { library.exerciseTrend(exerciseId) }
    val listState = rememberLazyListState()
    var highlightedWorkoutId by remember(exerciseId) { mutableStateOf<String?>(null) }

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
                colors = ervTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "subtitle") {
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
            item(key = "last-time") { ExerciseLastTimeCard(history.firstOrNull(), loadUnit) }
            item(key = "records") {
                ExerciseRecordsCard(
                    records = records,
                    loadUnit = loadUnit,
                    onRecordClick = { record ->
                        val index = history.indexOfFirst { it.workout.id == record.workoutId }
                        if (index >= 0) {
                            highlightedWorkoutId = record.workoutId
                            scope.launch { listState.animateScrollToItem(HISTORY_FIRST_ITEM + index) }
                        }
                    }
                )
            }
            item(key = "trend") {
                trend?.let { (metric, points) ->
                    if (points.size >= 2) ExerciseTrendCard(metric, points, loadUnit)
                }
            }
            item(key = "history-header") {
                FormSectionLabelSmall("History", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (history.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        "No logged workouts include this exercise yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(history, key = { "${it.logDate}_${it.workout.id}" }) { row ->
                    ExerciseSessionCard(
                        row = row,
                        loadUnit = loadUnit,
                        isRecordSession = row.workout.id in recordWorkoutIds,
                        modifier = if (row.workout.id == highlightedWorkoutId) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }

    if (showEditor && exercise != null) {
        WeightExerciseEditorDialog(
            initial = exercise,
            title = "Edit exercise",
            availableMuscleGroups = library.exercises.map { it.muscleGroup },
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
