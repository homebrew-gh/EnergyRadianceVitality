package com.erv.app.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.ui.components.FieldLabel
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.parseWeightInputToKg
import com.erv.app.weighttraining.weightLoadUnitSuffix
import com.erv.app.workouts.WorkoutSessionStep
import com.erv.app.workouts.formatStepSeconds

/** A lift set already logged for the item on screen, with the session step it belongs to. */
internal data class WorkoutSessionLoggedSet(val stepIndex: Int, val set: WeightSet)

/** True when the set is a timed hold (a plank, say) rather than reps. */
private fun WeightSet.isTimedTarget(): Boolean =
    (targetDurationSeconds ?: 0) > 0 && (targetReps ?: 0) <= 0

/** "5 reps at 225 lb", "45 s", or "8 reps" for one set. */
internal fun describeSessionSet(set: WeightSet, loadUnit: BodyWeightUnit): String {
    val amount = when {
        set.reps > 0 -> "${set.reps} reps"
        (set.durationSeconds ?: 0) > 0 -> formatStepSeconds(set.durationSeconds ?: 0)
        else -> "—"
    }
    val load = set.weightKg?.takeIf { it > 0 }
        ?.let { "${formatWeightLoadNumber(it, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}" }
    return if (load != null) "$amount at $load" else amount
}

/** The target for a lift step, for the line under the exercise name. */
private fun describeSessionTarget(target: WeightSet, loadUnit: BodyWeightUnit): String? {
    val amount = when {
        (target.targetReps ?: 0) > 0 -> "${target.targetReps} reps"
        !target.targetRepsRangeLabel.isNullOrBlank() -> "${target.targetRepsRangeLabel} reps"
        (target.targetDurationSeconds ?: 0) > 0 -> formatStepSeconds(target.targetDurationSeconds ?: 0)
        else -> null
    }
    val load = target.targetWeightKg?.takeIf { it > 0 }
        ?.let { "${formatWeightLoadNumber(it, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}" }
    return when {
        amount != null && load != null -> "Target $amount at $load"
        amount != null -> "Target $amount"
        load != null -> "Target $load"
        else -> null
    }
}

/** Reps (or seconds for a timed hold) and load fields, as text so the athlete can clear them. */
private class SetInputState(initial: WeightSet, timed: Boolean, loadUnit: BodyWeightUnit) {
    var amount by mutableStateOf(
        when {
            timed -> (initial.durationSeconds?.takeIf { it > 0 } ?: initial.targetDurationSeconds)?.toString().orEmpty()
            initial.reps > 0 -> initial.reps.toString()
            else -> initial.targetReps?.takeIf { it > 0 }?.toString().orEmpty()
        },
    )
    var load by mutableStateOf(
        (initial.weightKg ?: initial.targetWeightKg)?.takeIf { it > 0 }
            ?.let { formatWeightLoadNumber(it, loadUnit) }.orEmpty(),
    )

    /** The set these fields describe, or null while the reps or seconds are missing. */
    fun toSet(base: WeightSet, timed: Boolean, loadUnit: BodyWeightUnit): WeightSet? {
        val value = amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
        val kg = load.takeIf { it.isNotBlank() }?.let { parseWeightInputToKg(it, loadUnit) }
        return if (timed) {
            base.copy(reps = 0, durationSeconds = value, weightKg = kg, done = null)
        } else {
            base.copy(reps = value, weightKg = kg, done = null)
        }
    }
}

@Composable
private fun SetInputsRow(state: SetInputState, timed: Boolean, loadUnit: BodyWeightUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.amount,
            onValueChange = { text -> state.amount = text.filter { it.isDigit() }.take(4) },
            label = { FieldLabel(if (timed) "Seconds" else "Reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.load,
            onValueChange = { text -> state.load = text.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
            label = { FieldLabel("Weight") },
            trailingIcon = { Text(weightLoadUnitSuffix(loadUnit), style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One set of one exercise: what to do, fields for what was done, and the sets already logged for
 * this exercise (tap one to fix it). The fields start from the last set logged, else the target.
 */
@Composable
internal fun WorkoutSessionLiftCard(
    step: WorkoutSessionStep.LiftSet,
    exerciseName: String,
    sectionTitle: String?,
    loadUnit: BodyWeightUnit,
    loggedSets: List<WorkoutSessionLoggedSet>,
    canSwap: Boolean,
    onLog: (WeightSet) -> Unit,
    onSkipSet: () -> Unit,
    onSkipExercise: () -> Unit,
    onSwap: () -> Unit,
    onEditSet: (WorkoutSessionLoggedSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timed = step.target.isTimedTarget()
    val last = loggedSets.lastOrNull()?.set
    // New fields for every step, so the next set never inherits a half-typed value.
    val inputs = remember(step.segmentId, step.itemId, step.setIndex, step.round) {
        SetInputState(
            initial = if (last != null) {
                step.target.copy(reps = last.reps, weightKg = last.weightKg, durationSeconds = last.durationSeconds)
            } else {
                step.target
            },
            timed = timed,
            loadUnit = loadUnit,
        )
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sectionTitle?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(exerciseName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = when {
                    step.inRounds -> "Round ${step.round} of ${step.setCount}"
                    step.setCount > 1 -> "Set ${step.setIndex + 1} of ${step.setCount}"
                    else -> "One set"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            describeSessionTarget(step.target, loadUnit)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SetInputsRow(inputs, timed, loadUnit)
            val set = inputs.toSet(step.target, timed, loadUnit)
            Button(
                onClick = { set?.let(onLog) },
                enabled = set != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log Set")
            }
            if (loggedSets.isNotEmpty()) {
                loggedSets.forEachIndexed { index, logged ->
                    TextButton(onClick = { onEditSet(logged) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Set ${index + 1}: ${describeSessionSet(logged.set, loadUnit)}",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onSkipSet, modifier = Modifier.weight(1f)) {
                    Text("Skip Set", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onSkipExercise, modifier = Modifier.weight(1f)) {
                    Text("Skip Exercise", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canSwap) {
                TextButton(onClick = onSwap, modifier = Modifier.fillMaxWidth()) {
                    Text("Swap Exercise")
                }
            }
        }
    }
}

/** Fix a set already logged. */
@Composable
internal fun WorkoutSessionEditSetDialog(
    title: String,
    logged: WorkoutSessionLoggedSet,
    loadUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (WeightSet) -> Unit,
) {
    val timed = logged.set.reps <= 0 && (logged.set.durationSeconds ?: 0) > 0
    val inputs = remember(logged.stepIndex) { SetInputState(logged.set, timed, loadUnit) }
    val set = inputs.toSet(logged.set, timed, loadUnit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SetInputsRow(inputs, timed, loadUnit) },
        confirmButton = {
            TextButton(onClick = { set?.let(onSave) }, enabled = set != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "1:05" for the rest clock. */
private fun clockText(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** A rest counting down, with what comes after it. */
@Composable
internal fun WorkoutSessionRestCard(
    title: String,
    secondsRemaining: Int,
    nextLabel: String?,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(clockText(secondsRemaining), style = MaterialTheme.typography.displayMedium, fontSize = 56.sp)
            nextLabel?.let {
                Text(
                    "Next: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onAddTime) { Text("+30 s") }
                Button(onClick = onSkip) { Text("Skip Rest") }
            }
        }
    }
}

/** A coach note from the builder; Continue moves on. */
@Composable
internal fun WorkoutSessionNoteCard(text: String, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Coach Note", style = MaterialTheme.typography.titleMedium)
            Text(text.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

/** Pick the exercise to do in place of the planned one, for today. */
@Composable
internal fun WorkoutSessionSwapDialog(
    options: List<Pair<String, String>>,
    currentExerciseId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (exerciseId, name) ->
                    TextButton(onClick = { onPick(exerciseId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (exerciseId == currentExerciseId) "$name (now)" else name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
