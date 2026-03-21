package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.WeightWorkoutDraft
import com.erv.app.weighttraining.weightNowEpochSeconds
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightLiveWorkoutScreen(
    draft: WeightWorkoutDraft,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
    onAddExercise: (String) -> Unit,
    onRemoveExerciseAt: (Int) -> Unit,
    onMoveExerciseUp: (Int) -> Unit,
    onMoveExerciseDown: (Int) -> Unit,
    onSaveSets: (String, List<WeightSet>) -> Unit,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(draft.startedAtEpochSeconds) {
        tick = 0
        while (true) {
            tick++
            delay(1_000L)
        }
    }
    var showPickExercise by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showFinishBlocked by remember { mutableStateOf(false) }
    var setsCollapsedIds by remember(draft.startedAtEpochSeconds) {
        mutableStateOf(emptySet<String>())
    }
    var recentWorkoutsExerciseId by remember { mutableStateOf<String?>(null) }

    val darkTheme = isSystemInDarkTheme()
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark

    if (showPickExercise) {
        WeightPickExerciseDialog(
            exercises = library.exercises.sortedBy { it.name.lowercase() },
            excludeIds = draft.exerciseOrder.toSet(),
            onDismiss = { showPickExercise = false },
            onPick = { id ->
                onAddExercise(id)
                showPickExercise = false
            }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Discard workout?") },
            text = { Text("Your session will not be saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onCancel()
                    }
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Keep going") }
            }
        )
    }

    recentWorkoutsExerciseId?.let { id ->
        val name = library.exerciseById(id)?.name ?: id
        WeightExerciseRecentWorkoutsDialog(
            exerciseId = id,
            exerciseName = name,
            library = library,
            loadUnit = loadUnit,
            onDismiss = { recentWorkoutsExerciseId = null }
        )
    }

    if (showFinishBlocked) {
        AlertDialog(
            onDismissRequest = { showFinishBlocked = false },
            title = { Text("Nothing to save") },
            text = { Text("Log at least one set on an exercise before finishing.") },
            confirmButton = {
                TextButton(onClick = { showFinishBlocked = false }) { Text("OK") }
            }
        )
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Live workout") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (draft.exerciseOrder.isNotEmpty() || draft.setsByExerciseId.isNotEmpty()) {
                                    showCancelConfirm = true
                                } else {
                                    onCancel()
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val hasLogged = draft.exerciseOrder.any { id ->
                                    draft.setsByExerciseId[id].orEmpty().any { it.reps > 0 }
                                }
                                if (!hasLogged) showFinishBlocked = true
                                else onFinish()
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Finish", color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = headerMid,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            },
            containerColor = headerDark.copy(alpha = 0.08f)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                val elapsedSec = remember(tick, draft.startedAtEpochSeconds) {
                    (weightNowEpochSeconds() - draft.startedAtEpochSeconds).coerceAtLeast(0)
                }
                Text(
                    text = formatElapsed(elapsedSec),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
                if (draft.routineName != null) {
                    Text(
                        "From routine: ${draft.routineName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = { showPickExercise = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Add exercise")
                }
                Spacer(Modifier.height(12.dp))
                if (draft.exerciseOrder.isEmpty()) {
                    Text(
                        "Empty workout — add exercises, then fill in reps, weight, and RPE under each lift. Tap + Add set for more rows.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(draft.exerciseOrder, key = { _, id -> id }) { index, exerciseId ->
                            val ex = library.exerciseById(exerciseId)
                            val sets = weightSetsInDraft(draft, exerciseId)
                            WeightExerciseInlineSetsCard(
                                exerciseName = ex?.name ?: exerciseId,
                                equipmentLabel = ex?.equipment?.displayLabel(),
                                sets = sets,
                                loadUnit = loadUnit,
                                onSetsChange = { onSaveSets(exerciseId, it) },
                                canMoveUp = index > 0,
                                canMoveDown = index < draft.exerciseOrder.lastIndex,
                                onMoveUp = { onMoveExerciseUp(index) },
                                onMoveDown = { onMoveExerciseDown(index) },
                                onRemoveExercise = {
                                    setsCollapsedIds = setsCollapsedIds - exerciseId
                                    onRemoveExerciseAt(index)
                                },
                                setsCollapsed = exerciseId in setsCollapsedIds,
                                onCollapseSets = {
                                    setsCollapsedIds = setsCollapsedIds + exerciseId
                                },
                                onExpandSets = {
                                    setsCollapsedIds = setsCollapsedIds - exerciseId
                                },
                                onRecentWorkouts = { recentWorkoutsExerciseId = exerciseId }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}
