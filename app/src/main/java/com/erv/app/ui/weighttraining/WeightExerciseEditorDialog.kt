package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.displayLabel
import java.util.UUID

@Composable
fun WeightExerciseEditorDialog(
    initial: WeightExercise?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (WeightExercise) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var muscleGroup by remember(initial?.id) { mutableStateOf(initial?.muscleGroup.orEmpty()) }
    var pushOrPull by remember(initial?.id) { mutableStateOf(initial?.pushOrPull ?: WeightPushPull.PUSH) }
    var equipment by remember(initial?.id) { mutableStateOf(initial?.equipment ?: WeightEquipment.BARBELL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = muscleGroup,
                    onValueChange = { muscleGroup = it },
                    label = { Text("Muscle group") },
                    supportingText = { Text("e.g. chest, back, legs, biceps, triceps, or a custom label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pushOrPull == WeightPushPull.PUSH,
                        onClick = { pushOrPull = WeightPushPull.PUSH },
                        label = { Text("Push") }
                    )
                    FilterChip(
                        selected = pushOrPull == WeightPushPull.PULL,
                        onClick = { pushOrPull = WeightPushPull.PULL },
                        label = { Text("Pull") }
                    )
                }
                Text("Equipment", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WeightEquipment.entries.chunked(3).forEach { rowOpts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOpts.forEach { opt ->
                                FilterChip(
                                    selected = equipment == opt,
                                    onClick = { equipment = opt },
                                    label = { Text(opt.displayLabel()) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || muscleGroup.isBlank()) return@Button
                    val id = initial?.id ?: UUID.randomUUID().toString()
                    onSave(
                        WeightExercise(
                            id = id,
                            name = name.trim(),
                            muscleGroup = muscleGroup.trim().lowercase(),
                            pushOrPull = pushOrPull,
                            equipment = equipment,
                            sessionSummaries = initial?.sessionSummaries.orEmpty()
                        )
                    )
                },
                enabled = name.isNotBlank() && muscleGroup.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
