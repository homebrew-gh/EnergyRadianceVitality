package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.groupExercisesByMuscle

@Composable
fun WeightPickExerciseDialog(
    exercises: List<WeightExercise>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val choices = remember(exercises, excludeIds) {
        exercises.filter { it.id !in excludeIds }
    }
    val grouped = remember(choices) { groupExercisesByMuscle(choices) }
    var selectedMuscleKey by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                selectedMuscleKey?.let { formatMuscleGroupHeader(it) } ?: "Body part"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    choices.isEmpty() -> {
                        Text("No more exercises to add.", style = MaterialTheme.typography.bodyMedium)
                    }
                    grouped.isEmpty() -> {
                        Text("No more exercises to add.", style = MaterialTheme.typography.bodyMedium)
                    }
                    selectedMuscleKey == null -> {
                        Text(
                            "Choose a body part, then pick an exercise.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        grouped.forEach { (muscleKey, list) ->
                            TextButton(
                                onClick = { selectedMuscleKey = muscleKey },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${formatMuscleGroupHeader(muscleKey)} (${list.size})",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = { selectedMuscleKey = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("← Change body part", modifier = Modifier.fillMaxWidth())
                        }
                        val inGroup = grouped.firstOrNull { it.first == selectedMuscleKey }?.second.orEmpty()
                        if (inGroup.isEmpty()) {
                            Text(
                                "No exercises in this group.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            inGroup.forEach { ex ->
                                TextButton(
                                    onClick = { onPick(ex.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${ex.name} · ${ex.equipment.displayLabel()}",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
