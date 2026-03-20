package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutDraft
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

internal fun formatRpeFieldForSets(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format("%.1f", v)

internal fun repsFieldText(reps: Int): String = if (reps <= 0) "" else reps.toString()

internal fun weightFieldText(set: WeightSet, unit: BodyWeightUnit): String =
    set.weightKg?.let { formatWeightLoadNumber(it, unit) }.orEmpty()

internal fun rpeFieldText(set: WeightSet): String =
    set.rpe?.let { formatRpeFieldForSets(it) }.orEmpty()

internal fun formatSetSummaryLine(
    set: WeightSet,
    setNumber: Int,
    loadUnit: BodyWeightUnit,
    loadSuffix: String
): String {
    val repsPart = if (set.reps > 0) "${set.reps} reps" else "— reps"
    val weightPart = set.weightKg?.let { w ->
        " @ ${formatWeightLoadNumber(w, loadUnit)} $loadSuffix"
    }.orEmpty()
    val rpePart = set.rpe?.let { " · RPE ${formatRpeFieldForSets(it)}" }.orEmpty()
    return "Set $setNumber: $repsPart$weightPart$rpePart"
}

fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    mapIndexed { i, t -> if (i == index) value else t }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightExerciseInlineSetsCard(
    exerciseName: String,
    equipmentLabel: String?,
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
    onExpandSets: (() -> Unit)? = null
) {
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val canCollapseSets = onCollapseSets != null && onExpandSets != null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(exerciseName, style = MaterialTheme.typography.titleMedium)
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
            if (canCollapseSets) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (setsCollapsed) {
                        TextButton(onClick = { onExpandSets!!() }) {
                            Text("Edit")
                        }
                    }
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
                            formatSetSummaryLine(set, idx + 1, loadUnit, loadSuffix),
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
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = repsFieldText(set.reps),
                            onValueChange = { t ->
                                val r = t.trim().toIntOrNull() ?: 0
                                onSetsChange(sets.replaceAt(idx, set.copy(reps = r)))
                            },
                            label = { Text("Reps") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
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
                            label = { Text(loadSuffix) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
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
                            label = { Text("RPE") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
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
