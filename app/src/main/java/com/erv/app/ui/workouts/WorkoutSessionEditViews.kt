package com.erv.app.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.ui.components.FieldLabel

/** What the "Add to Workout" menu offers. */
internal enum class WorkoutSessionAddKind { EXERCISE, CARDIO, STRETCH }

/** "Add to Workout": a lift, cardio or stretch goes in after the item on screen, for today. */
@Composable
internal fun WorkoutSessionAddButton(onPick: (WorkoutSessionAddKind) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add to Workout", modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Exercise") }, onClick = { open = false; onPick(WorkoutSessionAddKind.EXERCISE) })
            DropdownMenuItem(text = { Text("Cardio") }, onClick = { open = false; onPick(WorkoutSessionAddKind.CARDIO) })
            DropdownMenuItem(text = { Text("Stretch") }, onClick = { open = false; onPick(WorkoutSessionAddKind.STRETCH) })
        }
    }
}

/** One entry of a storyboard row's menu; shown only when [enabled]. */
internal data class WorkoutSessionMenuAction(val label: String, val enabled: Boolean, val onClick: () -> Unit)

/** The ⋮ menu on a storyboard item or section. Draws nothing when no action applies. */
@Composable
internal fun WorkoutSessionRowMenu(actions: List<WorkoutSessionMenuAction>, contentDescription: String) {
    val shown = actions.filter { it.enabled }
    if (shown.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = contentDescription)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            shown.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        open = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** Add a steady cardio effort: pick the activity, and minutes if it has a set length. */
@Composable
internal fun WorkoutSessionAddCardioDialog(
    /** Activity id to label, in the order to list them. */
    activities: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onAdd: (activity: String, minutes: Int?) -> Unit,
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var minutesText by remember { mutableStateOf("") }
    val minutes = minutesText.trim().toIntOrNull()?.takeIf { it in 1..600 }
    val minutesValid = minutesText.isBlank() || minutes != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Cardio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(activities, key = { it.first }) { (id, label) ->
                        Text(
                            text = if (id == selected) "▸ $label" else label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = id }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(3) },
                    label = { FieldLabel("Minutes") },
                    supportingText = { Text("Leave blank to stop it yourself.") },
                    isError = !minutesValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let { onAdd(it, minutes) } },
                enabled = selected != null && minutesValid,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add a stretch from the catalog, with its hold time. */
@Composable
internal fun WorkoutSessionAddStretchDialog(
    catalog: List<StretchCatalogEntry>,
    onDismiss: () -> Unit,
    onAdd: (entry: StretchCatalogEntry, holdSeconds: Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<StretchCatalogEntry?>(null) }
    var holdText by remember { mutableStateOf("30") }
    val hold = holdText.trim().toIntOrNull()?.takeIf { it in 5..300 }
    val shown = remember(catalog, query) {
        val q = query.trim().lowercase()
        catalog.sortedBy { it.name.lowercase() }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Stretch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { FieldLabel("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(shown, key = { it.id }) { entry ->
                        val isSelected = entry.id == selected?.id
                        Text(
                            text = buildString {
                                if (isSelected) append("▸ ")
                                append(entry.name)
                                if (entry.requiresBothSides) append(" (each side)")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = entry }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = holdText,
                    onValueChange = { holdText = it.filter(Char::isDigit).take(3) },
                    label = { FieldLabel("Hold seconds") },
                    supportingText = { Text("5 to 300 seconds" + if (selected?.requiresBothSides == true) ", each side" else "") },
                    isError = hold == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val entry = selected
                    if (entry != null && hold != null) onAdd(entry, hold)
                },
                enabled = selected != null && hold != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms taking an item out of today's run. */
@Composable
internal fun WorkoutSessionRemoveItemDialog(name: String, onDismiss: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove $name?") },
        text = { Text("It comes out of today's workout. The saved workout stays as it is unless you save today's changes at the end.") },
        confirmButton = {
            TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
