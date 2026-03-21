package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutDraft
import com.erv.app.weighttraining.formatRpeFieldForSets
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.parseWeightInputToKg
import com.erv.app.weighttraining.weightLoadUnitSuffix

fun weightSetsDisplayedForExercise(stored: List<WeightSet>?): List<WeightSet> =
    if (stored.isNullOrEmpty()) {
        listOf(WeightSet(reps = 0, weightKg = null, rpe = null))
    } else {
        stored
    }

fun weightSetsInDraft(draft: WeightWorkoutDraft, exerciseId: String): List<WeightSet> =
    weightSetsDisplayedForExercise(draft.setsByExerciseId[exerciseId])

internal fun repsFieldText(reps: Int): String = if (reps <= 0) "" else reps.toString()

internal fun weightFieldText(set: WeightSet, unit: BodyWeightUnit): String =
    set.weightKg?.let { formatWeightLoadNumber(it, unit) }.orEmpty()

internal fun rpeFieldText(set: WeightSet): String =
    set.rpe?.let { formatRpeFieldForSets(it) }.orEmpty()

fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    mapIndexed { i, t -> if (i == index) value else t }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightExerciseInlineSetsCard(
    exerciseName: String,
    equipmentLabel: String?,
    /** Used to label the weight column as added load for bodyweight-style exercises (`OTHER`). */
    equipment: WeightEquipment? = null,
    sets: List<WeightSet>,
    loadUnit: BodyWeightUnit,
    onSetsChange: (List<WeightSet>) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemoveExercise: () -> Unit,
    setsCollapsed: Boolean = false,
    onCollapseSets: (() -> Unit)? = null,
    onExpandSets: (() -> Unit)? = null,
    /** When set, shows a control to open recent logged sessions for this exercise (e.g. live workout reference). */
    onRecentWorkouts: (() -> Unit)? = null
) {
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val weightIsAddedLoad = equipment == WeightEquipment.OTHER
    var showAddedLoadInfo by remember { mutableStateOf(false) }
    val canCollapseSets = onCollapseSets != null && onExpandSets != null
    val collapsedSummary = canCollapseSets && setsCollapsed
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 12.dp,
                    vertical = if (collapsedSummary) 8.dp else 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (collapsedSummary) 4.dp else 10.dp)
        ) {
            if (showAddedLoadInfo) {
                AlertDialog(
                    onDismissRequest = { showAddedLoadInfo = false },
                    title = { Text(stringResource(R.string.weight_added_load_info_title)) },
                    text = { Text(stringResource(R.string.weight_added_load_info_message)) },
                    confirmButton = {
                        TextButton(onClick = { showAddedLoadInfo = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            exerciseName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (collapsedSummary) {
                            IconButton(
                                onClick = { onExpandSets!!() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit sets",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    equipmentLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                    }
                    IconButton(onClick = onRemoveExercise) {
                        Icon(Icons.Default.Close, contentDescription = "Remove exercise")
                    }
                }
            }
            // Hide while collapsed to summary; "Edit" expands and brings this back.
            if (onRecentWorkouts != null && !setsCollapsed) {
                TextButton(onClick = onRecentWorkouts) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Recent workouts")
                    }
                }
            }
            if (canCollapseSets) {
                if (!setsCollapsed) {
                    Text(
                        "Sets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "Sets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canCollapseSets && setsCollapsed) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sets.forEachIndexed { idx, set ->
                        Text(
                            formatSetSummaryLine(set, idx + 1, loadUnit, loadSuffix, weightIsAddedLoad),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (!canCollapseSets || !setsCollapsed) {
                sets.forEachIndexed { idx, set ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Reps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = repsFieldText(set.reps),
                                onValueChange = { t ->
                                    val r = t.trim().toIntOrNull() ?: 0
                                    onSetsChange(sets.replaceAt(idx, set.copy(reps = r)))
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                loadSuffix,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = weightFieldText(set, loadUnit),
                                onValueChange = { t ->
                                    onSetsChange(
                                        sets.replaceAt(
                                            idx,
                                            set.copy(weightKg = parseWeightInputToKg(t, loadUnit))
                                        )
                                    )
                                },
                                trailingIcon = if (weightIsAddedLoad) {
                                    {
                                        IconButton(onClick = { showAddedLoadInfo = true }) {
                                            Icon(
                                                Icons.Outlined.Info,
                                                contentDescription = stringResource(
                                                    R.string.weight_added_load_info_icon_cd
                                                )
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "RPE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = rpeFieldText(set),
                                onValueChange = { t ->
                                    onSetsChange(
                                        sets.replaceAt(
                                            idx,
                                            set.copy(rpe = t.trim().toDoubleOrNull())
                                        )
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        IconButton(
                            onClick = {
                                if (sets.size > 1) {
                                    onSetsChange(sets.filterIndexed { i, _ -> i != idx })
                                }
                            },
                            enabled = sets.size > 1
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove set")
                        }
                    }
                }
                TextButton(
                    onClick = {
                        onSetsChange(
                            sets + WeightSet(reps = 0, weightKg = null, rpe = null)
                        )
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add set", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (canCollapseSets) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onCollapseSets!!() },
                            enabled = sets.any { it.reps > 0 }
                        ) {
                            Text("Finish")
                        }
                    }
                }
            }
        }
    }
}
