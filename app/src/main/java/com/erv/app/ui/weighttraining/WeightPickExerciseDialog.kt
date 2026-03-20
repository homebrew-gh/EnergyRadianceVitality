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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.weighttraining.WeightExercise

@Composable
fun WeightPickExerciseDialog(
    exercises: List<WeightExercise>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val choices = exercises.filter { it.id !in excludeIds }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to workout") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (choices.isEmpty()) {
                    Text("No more exercises to add.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    choices.forEach { ex ->
                        TextButton(
                            onClick = { onPick(ex.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${ex.name} (${ex.muscleGroup})", modifier = Modifier.fillMaxWidth())
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
