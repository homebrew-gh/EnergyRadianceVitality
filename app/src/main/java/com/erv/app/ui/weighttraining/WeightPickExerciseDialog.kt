package com.erv.app.ui.weighttraining

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erv.app.ui.components.FieldLabel
import com.erv.app.data.UserPreferences
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.WeightExercisePickerFilter
import com.erv.app.weighttraining.WeightPushPull
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.filterWeightExercisesForPicker
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.groupExercisesByMuscle

@Composable
fun WeightPickExerciseDialog(
    exercises: List<WeightExercise>,
    excludeIds: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onCreateNew: (() -> Unit)? = null
) {
    val appContext = LocalContext.current.applicationContext
    val userPreferences = remember(appContext) { UserPreferences(appContext) }
    val gymMembership by userPreferences.gymMembership.collectAsState(initial = false)
    val ownedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())
    val enabledExercisePackIds by userPreferences.enabledWeightExercisePackIds.collectAsState(initial = emptySet())
    var equipmentFilter by rememberSaveable { mutableStateOf(WeightExercisePickerFilter.ALL) }
    val choices = remember(exercises, excludeIds, equipmentFilter, ownedEquipment, enabledExercisePackIds) {
        filterWeightExercisesForPicker(
            exercises = exercises.filter { it.id !in excludeIds },
            filter = equipmentFilter,
            ownedEquipment = ownedEquipment,
            enabledPackIds = enabledExercisePackIds,
        )
    }
    var selectedMuscleKey by remember { mutableStateOf<String?>(null) }
    var selectedPushPull by remember { mutableStateOf<WeightPushPull?>(null) }
    val filteredChoices = remember(choices, selectedPushPull) {
        choices.filter { ex -> selectedPushPull == null || ex.pushOrPull == selectedPushPull }
    }
    val grouped = remember(filteredChoices) { groupExercisesByMuscle(filteredChoices) }
    var query by rememberSaveable { mutableStateOf("") }
    // Search spans every muscle group and ignores push/pull, since those chips are hidden while searching.
    val searchResults = remember(choices, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else choices.filter { it.name.contains(q, ignoreCase = true) }
    }
    // Callers that keep the dialog open after a pick get feedback here; picked ids drop out via excludeIds.
    var pickedCount by remember { mutableStateOf(0) }
    val pick: (String) -> Unit = { id ->
        pickedCount++
        onPick(id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            when (val key = selectedMuscleKey) {
                null -> Text("Choose Exercise", style = MaterialTheme.typography.titleLarge)
                else -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { selectedMuscleKey = null }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Change muscle group"
                        )
                    }
                    Text(
                        text = formatMuscleGroupHeader(key),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search exercises") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = equipmentFilter == WeightExercisePickerFilter.ALL,
                        onClick = { equipmentFilter = WeightExercisePickerFilter.ALL },
                        label = { Text(if (gymMembership) "All / Gym" else "All") }
                    )
                    FilterChip(
                        selected = equipmentFilter == WeightExercisePickerFilter.HOME_READY,
                        onClick = { equipmentFilter = WeightExercisePickerFilter.HOME_READY },
                        label = { FieldLabel("Home-Ready") }
                    )
                }
                if (equipmentFilter == WeightExercisePickerFilter.HOME_READY && ownedEquipment.isEmpty()) {
                    Text(
                        "Home-Ready uses your saved equipment from Settings -> Equipment & Gym, plus no-equipment bodyweight exercises.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    choices.isEmpty() -> {
                        Text(
                            if (equipmentFilter == WeightExercisePickerFilter.HOME_READY) {
                                "No matching home-ready exercises yet."
                            } else {
                                "No more exercises to add."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    query.isNotBlank() -> {
                        if (searchResults.isEmpty()) {
                            Text(
                                "No exercises match “${query.trim()}”.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            searchResults.forEach { ex ->
                                ExercisePickRow(
                                    exercise = ex,
                                    showMuscleGroup = true,
                                    onClick = { pick(ex.id) }
                                )
                            }
                        }
                    }
                    grouped.isEmpty() -> {
                        Text(
                            if (equipmentFilter == WeightExercisePickerFilter.HOME_READY) {
                                "No matching home-ready exercises yet."
                            } else {
                                "No more exercises to add."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    selectedMuscleKey == null -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedPushPull == null,
                                onClick = { selectedPushPull = null },
                                label = { FieldLabel("All") }
                            )
                            FilterChip(
                                selected = selectedPushPull == WeightPushPull.PUSH,
                                onClick = { selectedPushPull = WeightPushPull.PUSH },
                                label = { Text(WeightPushPull.PUSH.displayLabel()) }
                            )
                            FilterChip(
                                selected = selectedPushPull == WeightPushPull.PULL,
                                onClick = { selectedPushPull = WeightPushPull.PULL },
                                label = { Text(WeightPushPull.PULL.displayLabel()) }
                            )
                        }
                        grouped.forEach { (muscleKey, list) ->
                            ListItem(
                                headlineContent = { Text(formatMuscleGroupHeader(muscleKey)) },
                                supportingContent = {
                                    Text(if (list.size == 1) "1 exercise" else "${list.size} exercises")
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable { selectedMuscleKey = muscleKey }
                            )
                        }
                    }
                    else -> {
                        val inGroup = grouped.firstOrNull { it.first == selectedMuscleKey }?.second.orEmpty()
                        if (inGroup.isEmpty()) {
                            Text(
                                "No exercises in this group.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            inGroup.forEach { ex ->
                                ExercisePickRow(
                                    exercise = ex,
                                    showMuscleGroup = false,
                                    onClick = { pick(ex.id) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (pickedCount > 0) "Done ($pickedCount added)" else "Close")
            }
        },
        dismissButton = {
            onCreateNew?.let { create ->
                TextButton(onClick = create) { Text("Create new exercise") }
            }
        }
    )
}

@Composable
private fun ExercisePickRow(
    exercise: WeightExercise,
    showMuscleGroup: Boolean,
    onClick: () -> Unit
) {
    val details = buildList {
        if (showMuscleGroup) add(formatMuscleGroupHeader(exercise.muscleGroup.trim().ifBlank { "other" }))
        add(exercise.pushOrPull.displayLabel())
        add(exercise.equipment.displayLabel())
    }
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = { Text(details.joinToString(" · ")) },
        trailingContent = {
            Icon(
                Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClickLabel = "Add ${exercise.name}", onClick = onClick)
    )
}
