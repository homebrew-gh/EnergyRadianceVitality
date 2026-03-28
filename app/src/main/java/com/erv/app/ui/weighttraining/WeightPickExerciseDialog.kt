package com.erv.app.ui.weighttraining

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    onPick: (String) -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val userPreferences = remember(appContext) { UserPreferences(appContext) }
    val gymMembership by userPreferences.gymMembership.collectAsState(initial = false)
    val ownedEquipment by userPreferences.ownedEquipment.collectAsState(initial = emptyList())
    var equipmentFilter by rememberSaveable { mutableStateOf(WeightExercisePickerFilter.ALL) }
    val choices = remember(exercises, excludeIds, equipmentFilter, ownedEquipment) {
        filterWeightExercisesForPicker(
            exercises = exercises.filter { it.id !in excludeIds },
            filter = equipmentFilter,
            ownedEquipment = ownedEquipment,
        )
    }
    var selectedMuscleKey by remember { mutableStateOf<String?>(null) }
    var selectedPushPull by remember { mutableStateOf<WeightPushPull?>(null) }
    val filteredChoices = remember(choices, selectedPushPull) {
        choices.filter { ex -> selectedPushPull == null || ex.pushOrPull == selectedPushPull }
    }
    val grouped = remember(filteredChoices) { groupExercisesByMuscle(filteredChoices) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            when (val key = selectedMuscleKey) {
                null -> Text("Muscle Group", style = MaterialTheme.typography.titleLarge)
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
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        label = { Text("Home-Ready") }
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
                        Text(
                            if (equipmentFilter == WeightExercisePickerFilter.HOME_READY) {
                                "Choose push or pull if you want, then pick a muscle group from exercises that fit your home setup."
                            } else {
                                "Choose push or pull if you want, then pick a muscle group."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedPushPull == null,
                                onClick = { selectedPushPull = null },
                                label = { Text("All") }
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
                                        "${ex.name} · ${ex.pushOrPull.displayLabel()} · ${ex.equipment.displayLabel()}",
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
