package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.formatMuscleGroupHeader
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeightExerciseEditorDialog(
    initial: WeightExercise?,
    title: String,
    availableMuscleGroups: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (WeightExercise) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    val muscleGroupOptions = remember(availableMuscleGroups, initial?.muscleGroup) {
        (availableMuscleGroups + listOfNotNull(initial?.muscleGroup))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy(::formatMuscleGroupHeader)
    }
    var selectedMuscleGroup by remember(initial?.id, muscleGroupOptions) {
        mutableStateOf(initial?.muscleGroup?.trim()?.lowercase().orEmpty())
    }
    var useCustomMuscleGroup by remember(initial?.id, muscleGroupOptions) {
        mutableStateOf(selectedMuscleGroup.isNotBlank() && selectedMuscleGroup !in muscleGroupOptions)
    }
    var customMuscleGroup by remember(initial?.id) {
        mutableStateOf(if (useCustomMuscleGroup) selectedMuscleGroup else "")
    }
    var pushOrPull by remember(initial?.id) { mutableStateOf(initial?.pushOrPull ?: WeightPushPull.PUSH) }
    var equipment by remember(initial?.id) { mutableStateOf(initial?.equipment ?: WeightEquipment.BARBELL) }
    var hiitCapable by remember(initial?.id) { mutableStateOf(initial?.hiitCapable == true) }
    val resolvedMuscleGroup = if (useCustomMuscleGroup) {
        customMuscleGroup.trim()
    } else {
        selectedMuscleGroup.trim()
    }

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
                Text("Muscle group", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    muscleGroupOptions.forEach { group ->
                        FilterChip(
                            selected = !useCustomMuscleGroup && selectedMuscleGroup == group,
                            onClick = {
                                selectedMuscleGroup = group
                                customMuscleGroup = ""
                                useCustomMuscleGroup = false
                            },
                            label = { Text(formatMuscleGroupHeader(group)) }
                        )
                    }
                    FilterChip(
                        selected = useCustomMuscleGroup,
                        onClick = {
                            useCustomMuscleGroup = true
                            selectedMuscleGroup = ""
                        },
                        label = { Text("New group") }
                    )
                }
                if (useCustomMuscleGroup) {
                    OutlinedTextField(
                        value = customMuscleGroup,
                        onValueChange = { customMuscleGroup = it },
                        label = { Text("New muscle group") },
                        supportingText = { Text("This label will become available for future exercises.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WeightEquipment.entries.forEach { opt ->
                        FilterChip(
                            selected = equipment == opt,
                            onClick = { equipment = opt },
                            label = { Text(opt.displayLabel()) }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = hiitCapable,
                            role = Role.Checkbox,
                            onValueChange = { hiitCapable = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = hiitCapable, onCheckedChange = null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Allow interval (HIIT) timer",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Live workouts can use guided work/rest intervals for this exercise.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || resolvedMuscleGroup.isBlank()) return@Button
                    val id = initial?.id ?: UUID.randomUUID().toString()
                    onSave(
                        WeightExercise(
                            id = id,
                            name = name.trim(),
                            muscleGroup = resolvedMuscleGroup.lowercase(),
                            pushOrPull = pushOrPull,
                            equipment = equipment,
                            exercisePackId = initial?.exercisePackId,
                            hiitCapable = hiitCapable,
                            sessionSummaries = initial?.sessionSummaries.orEmpty()
                        )
                    )
                },
                enabled = name.isNotBlank() && resolvedMuscleGroup.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
