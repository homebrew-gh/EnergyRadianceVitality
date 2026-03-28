package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.displayLabel

/**
 * Same routine builder as Weight Training → Routines, reusable from Programs and other flows.
 * Persists via [onSave] (caller should call [WeightRepository.upsertRoutine] and optional relay sync).
 */
@Composable
fun WeightRoutineEditorDialog(
    initial: WeightRoutine,
    exerciseLibrary: List<WeightExercise>,
    title: String,
    onDismiss: () -> Unit,
    onSave: (WeightRoutine) -> Unit
) {
    var routineName by remember(initial.id) { mutableStateOf(initial.name) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes.orEmpty()) }
    var exerciseIds by remember(initial.id) { mutableStateOf(initial.exerciseIds.toMutableList()) }
    var showPickExercise by remember { mutableStateOf(false) }

    if (showPickExercise) {
        WeightPickExerciseDialog(
            exercises = exerciseLibrary,
            excludeIds = exerciseIds.toSet(),
            onDismiss = { showPickExercise = false },
            onPick = { id ->
                exerciseIds = exerciseIds.toMutableList().also { it.add(id) }
                showPickExercise = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { routineName = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Exercises (order)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showPickExercise = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Text("Add exercise from library")
                }
                Spacer(Modifier.height(8.dp))
                exerciseIds.forEachIndexed { index, id ->
                    val ex = exerciseLibrary.firstOrNull { it.id == id }
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex?.name ?: "Unknown id", style = MaterialTheme.typography.bodyLarge)
                                ex?.let {
                                    Text(
                                        "${it.muscleGroup} · ${it.equipment.displayLabel()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val m = exerciseIds.toMutableList()
                                        val t = m[index]
                                        m[index] = m[index - 1]
                                        m[index - 1] = t
                                        exerciseIds = m
                                    }
                                },
                                enabled = index > 0
                            ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Up") }
                            IconButton(
                                onClick = {
                                    if (index < exerciseIds.lastIndex) {
                                        val m = exerciseIds.toMutableList()
                                        val t = m[index]
                                        m[index] = m[index + 1]
                                        m[index + 1] = t
                                        exerciseIds = m
                                    }
                                },
                                enabled = index < exerciseIds.lastIndex
                            ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Down") }
                            IconButton(
                                onClick = {
                                    exerciseIds = exerciseIds.toMutableList().also { it.removeAt(index) }
                                }
                            ) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.padding(horizontal = 8.dp))
                    Button(
                        onClick = {
                            if (routineName.isBlank()) return@Button
                            onSave(
                                WeightRoutine(
                                    id = initial.id,
                                    name = routineName.trim(),
                                    exerciseIds = exerciseIds.toList(),
                                    notes = notes.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = routineName.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }
}
