package com.erv.app.ui.weighttraining

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedMid
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.buildSessionFromLogEditor
import com.erv.app.weighttraining.displayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightManualWorkoutEditorScreen(
    existingSession: WeightWorkoutSession?,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    onLoadUnitChange: (BodyWeightUnit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (WeightWorkoutSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionKey = existingSession?.id ?: "new"
    var exerciseOrder by remember(sessionKey) {
        mutableStateOf(existingSession?.entries?.map { it.exerciseId }.orEmpty())
    }
    var setsByExerciseId by remember(sessionKey) {
        mutableStateOf(
            existingSession?.entries?.associate { it.exerciseId to it.sets }.orEmpty()
        )
    }
    var showPickExercise by remember { mutableStateOf(false) }
    var showNothingLogged by remember { mutableStateOf(false) }

    if (showPickExercise) {
        WeightPickExerciseDialog(
            exercises = library.exercises.sortedBy { it.name.lowercase() },
            excludeIds = exerciseOrder.toSet(),
            onDismiss = { showPickExercise = false },
            onPick = { id ->
                if (id !in exerciseOrder) {
                    exerciseOrder = exerciseOrder + id
                    setsByExerciseId = setsByExerciseId + (
                        id to listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
                        )
                }
                showPickExercise = false
            }
        )
    }

    if (showNothingLogged) {
        AlertDialog(
            onDismissRequest = { showNothingLogged = false },
            title = { Text("Nothing to save") },
            text = { Text("Add at least one exercise with reps logged for one or more sets.") },
            confirmButton = {
                TextButton(onClick = { showNothingLogged = false }) { Text("OK") }
            }
        )
    }

    val darkTheme = isSystemInDarkTheme()
    val headerMid = if (darkTheme) ErvDarkTherapyRedMid else ErvLightTherapyRedMid
    val headerDark = if (darkTheme) ErvDarkTherapyRedDark else ErvLightTherapyRedDark

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (existingSession == null) "Add workout" else "Edit workout") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            FilterChip(
                                selected = loadUnit == BodyWeightUnit.KG,
                                onClick = { onLoadUnitChange(BodyWeightUnit.KG) },
                                label = { Text("kg") }
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = loadUnit == BodyWeightUnit.LB,
                                onClick = { onLoadUnitChange(BodyWeightUnit.LB) },
                                label = { Text("lb") }
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val built = buildSessionFromLogEditor(
                                        existingSession,
                                        exerciseOrder,
                                        setsByExerciseId
                                    )
                                    if (built == null) showNothingLogged = true
                                    else onSave(built)
                                }
                            ) {
                                Text("Save", color = Color.White)
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
            },
            containerColor = headerDark.copy(alpha = 0.08f)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    "Log sets below. Changes apply when you tap Save in the header.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Button(
                    onClick = { showPickExercise = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Add exercise")
                }
                Spacer(Modifier.height(12.dp))
                if (exerciseOrder.isEmpty()) {
                    Text(
                        "Add exercises from your library, then enter reps (required), weight, and RPE.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(exerciseOrder, key = { _, id -> id }) { index, exerciseId ->
                            val ex = library.exerciseById(exerciseId)
                            val sets = weightSetsDisplayedForExercise(setsByExerciseId[exerciseId])
                            WeightExerciseInlineSetsCard(
                                exerciseName = ex?.name ?: exerciseId,
                                equipmentLabel = ex?.equipment?.displayLabel(),
                                sets = sets,
                                loadUnit = loadUnit,
                                onSetsChange = { newSets ->
                                    setsByExerciseId = setsByExerciseId + (exerciseId to newSets)
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < exerciseOrder.lastIndex,
                                onMoveUp = {
                                    val m = exerciseOrder.toMutableList()
                                    val t = m[index]
                                    m[index] = m[index - 1]
                                    m[index - 1] = t
                                    exerciseOrder = m
                                },
                                onMoveDown = {
                                    val m = exerciseOrder.toMutableList()
                                    val t = m[index]
                                    m[index] = m[index + 1]
                                    m[index + 1] = t
                                    exerciseOrder = m
                                },
                                onRemoveExercise = {
                                    exerciseOrder = exerciseOrder.filterIndexed { i, _ -> i != index }
                                    setsByExerciseId = setsByExerciseId - exerciseId
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
