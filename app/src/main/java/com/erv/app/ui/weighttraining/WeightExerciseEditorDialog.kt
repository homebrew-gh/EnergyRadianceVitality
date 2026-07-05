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
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabel
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
    var timePerSetCapable by remember(initial?.id) { mutableStateOf(initial?.timePerSetCapable == true) }
    var repPerSetCapable by remember(initial?.id) { mutableStateOf(initial?.repPerSetCapable != false) }
    val isBuiltin = initial?.id?.startsWith("erv-weight-exercise-") == true
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
                    label = { FieldLabel("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FormSectionLabel("Muscle group")
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
                        label = { FieldLabel("New group") }
                    )
                }
                if (useCustomMuscleGroup) {
                    OutlinedTextField(
                        value = customMuscleGroup,
                        onValueChange = { customMuscleGroup = it },
                        label = { FieldLabel("New muscle group") },
                        supportingText = { Text("This label will become available for future exercises.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pushOrPull == WeightPushPull.PUSH,
                        onClick = { pushOrPull = WeightPushPull.PUSH },
                        label = { FieldLabel("Push") }
                    )
                    FilterChip(
                        selected = pushOrPull == WeightPushPull.PULL,
                        onClick = { pushOrPull = WeightPushPull.PULL },
                        label = { FieldLabel("Pull") }
                    )
                }
                FormSectionLabel("Equipment")
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
                if (!isBuiltin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = timePerSetCapable,
                                role = Role.Checkbox,
                                onValueChange = {
                                    timePerSetCapable = it
                                    if (!it) repPerSetCapable = true
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = timePerSetCapable, onCheckedChange = null)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Timed sets",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Prescribe and log holds or carries by duration instead of reps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (timePerSetCapable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = repPerSetCapable,
                                    role = Role.Checkbox,
                                    onValueChange = { repPerSetCapable = it },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = repPerSetCapable, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Also allow rep-based sets",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "When off, this exercise is time-only (e.g. carries, planks).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            timePerSetCapable = if (isBuiltin) {
                                initial?.timePerSetCapable == true
                            } else {
                                timePerSetCapable
                            },
                            repPerSetCapable = if (isBuiltin) {
                                initial?.repPerSetCapable != false
                            } else if (timePerSetCapable) {
                                repPerSetCapable
                            } else {
                                true
                            },
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
