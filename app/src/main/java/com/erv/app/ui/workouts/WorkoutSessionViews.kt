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
import androidx.compose.material3.FilterChip
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
import com.erv.app.ui.weighttraining.PlateCalculatorUiState
import com.erv.app.ui.weighttraining.PlateLoadLine
import com.erv.app.weighttraining.WeightHiitIntervalPlan
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.formatRpeFieldForSets
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.parseDecimalInput
import com.erv.app.weighttraining.parseWeightInputToKg
import com.erv.app.weighttraining.weightLoadUnitSuffix
import com.erv.app.workouts.WorkoutSessionStep
import com.erv.app.workouts.formatStepSeconds
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A lift set already logged for the item on screen, with the session step it belongs to. */
internal data class WorkoutSessionLoggedSet(val stepIndex: Int, val set: WeightSet)

/** True when the set is a timed hold (a plank, say) rather than reps. */
private fun WeightSet.isTimedTarget(): Boolean =
    (targetDurationSeconds ?: 0) > 0 && (targetReps ?: 0) <= 0

/** "5 reps at 225 lb", "45 s", or "8 reps" for one set, with " · RPE 8" when one was logged. */
internal fun describeSessionSet(set: WeightSet, loadUnit: BodyWeightUnit): String {
    val amount = when {
        set.reps > 0 -> "${set.reps} reps"
        (set.durationSeconds ?: 0) > 0 -> formatStepSeconds(set.durationSeconds ?: 0)
        else -> "—"
    }
    val load = set.weightKg?.takeIf { it > 0 }
        ?.let { "${formatWeightLoadNumber(it, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}" }
    val described = if (load != null) "$amount at $load" else amount
    return set.rpe?.let { "$described · RPE ${formatRpeFieldForSets(it)}" } ?: described
}

/** RPE as typed: 1 to 10, halves allowed; null for blank or out of range. */
internal fun parseSessionRpe(text: String): Double? =
    parseDecimalInput(text)?.takeIf { it in 1.0..10.0 }

/** "225 lb × 5", "45 s" or "8 reps": one set, short enough for a row of them. */
internal fun describeSessionSetShort(set: WeightSet, loadUnit: BodyWeightUnit): String {
    val load = set.weightKg?.takeIf { it > 0 }
        ?.let { "${formatWeightLoadNumber(it, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}" }
    return when {
        set.reps > 0 && load != null -> "$load × ${set.reps}"
        set.reps > 0 -> "${set.reps} reps"
        (set.durationSeconds ?: 0) > 0 ->
            listOfNotNull(load, formatStepSeconds(set.durationSeconds ?: 0)).joinToString(" · ")
        load != null -> load
        else -> "—"
    }
}

/** "Last time (Nov 20): 225 lb × 5, 225 lb × 5" from the newest earlier log of the exercise. */
internal fun describeLastTime(row: WeightExerciseHistoryRow, loadUnit: BodyWeightUnit, today: LocalDate): String? {
    val sets = row.entry.sets
    if (sets.isEmpty()) return null
    val day = when (row.logDate) {
        today -> "earlier today"
        today.minusDays(1) -> "yesterday"
        else -> row.logDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
    return "Last time ($day): " + sets.joinToString(", ") { describeSessionSetShort(it, loadUnit) }
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

/** Reps (or seconds for a timed hold), load and RPE fields, as text so the athlete can clear them. */
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

    /** Optional; starts blank for each new set, since effort changes set to set. */
    var rpe by mutableStateOf(initial.rpe?.let(::formatRpeFieldForSets).orEmpty())

    /** False while the RPE field holds something that isn't 1 to 10. */
    val rpeValid: Boolean get() = rpe.isBlank() || parseSessionRpe(rpe) != null

    /** The weight typed, in kilograms, or null while blank. */
    fun loadKg(loadUnit: BodyWeightUnit): Double? =
        load.takeIf { it.isNotBlank() }?.let { parseWeightInputToKg(it, loadUnit) }

    /** The set these fields describe, or null while the reps or seconds are missing. */
    fun toSet(base: WeightSet, timed: Boolean, loadUnit: BodyWeightUnit): WeightSet? {
        val value = amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
        if (!rpeValid) return null
        val kg = load.takeIf { it.isNotBlank() }?.let { parseWeightInputToKg(it, loadUnit) }
        val effort = parseSessionRpe(rpe)
        return if (timed) {
            base.copy(reps = 0, durationSeconds = value, weightKg = kg, rpe = effort, done = null)
        } else {
            base.copy(reps = value, weightKg = kg, rpe = effort, done = null)
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
        OutlinedTextField(
            value = state.rpe,
            onValueChange = { text -> state.rpe = text.filter { it.isDigit() || it == '.' || it == ',' }.take(4) },
            label = { FieldLabel("RPE") },
            isError = !state.rpeValid,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.7f),
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
    /** The sets from the last time this exercise was logged, from [describeLastTime]. */
    lastTime: String? = null,
    /** Read-only next-session target from the latest web coach review, e.g. "Coach: 185 × 8, 8, 8 lb". */
    coachTarget: String? = null,
    onShowHistory: (() -> Unit)? = null,
    canAddSet: Boolean = false,
    canRemoveSet: Boolean = false,
    onAddSet: () -> Unit = {},
    onRemoveSet: () -> Unit = {},
    /** "Barbell", "Dumbbell" and so on, under the name. */
    equipmentLabel: String? = null,
    /** Plates per side under the weight typed, for a plate-loaded lift. */
    plateCalculator: PlateCalculatorUiState? = null,
    /**
     * Opens the full-screen hold timer for a timed set, with the weight and RPE typed so far;
     * null when the set has no target time.
     */
    onStartTimer: ((WeightSet) -> Unit)? = null,
    /** Runs the exercise on the interval timer instead of as sets; null when it can't. */
    onStartIntervals: ((WeightHiitIntervalPlan) -> Unit)? = null,
) {
    val timed = step.target.isTimedTarget()
    var intervalsMode by remember(step.segmentId, step.itemId) { mutableStateOf(false) }
    val last = loggedSets.lastOrNull()?.set
    // New fields for every step, so the next set never inherits a half-typed value.
    val inputs = remember(step.segmentId, step.itemId, step.setIndex, step.round) {
        SetInputState(
            initial = if (last != null) {
                step.target.copy(reps = last.reps, weightKg = last.weightKg, durationSeconds = last.durationSeconds, rpe = null)
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
            equipmentLabel?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onStartIntervals != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !intervalsMode, onClick = { intervalsMode = false }, label = { Text("Sets") })
                    FilterChip(selected = intervalsMode, onClick = { intervalsMode = true }, label = { Text("Intervals") })
                }
            }
            if (intervalsMode && onStartIntervals != null) {
                IntervalTimerSetup(loadUnit = loadUnit, onStart = onStartIntervals)
                return@Column
            }
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
            lastTime?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            coachTarget?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            SetInputsRow(inputs, timed, loadUnit)
            if (plateCalculator != null) {
                PlateLoadLine(
                    weightKg = inputs.loadKg(loadUnit),
                    loadUnit = loadUnit,
                    calc = plateCalculator,
                    onUseWeightKg = { kg -> inputs.load = formatWeightLoadNumber(kg, loadUnit) },
                )
            }
            val set = inputs.toSet(step.target, timed, loadUnit)
            if (timed && onStartTimer != null) {
                // The hold timer counts the time; typing the seconds stays for a hold done without it.
                Button(
                    onClick = {
                        onStartTimer(
                            step.target.copy(weightKg = inputs.loadKg(loadUnit), rpe = parseSessionRpe(inputs.rpe), done = null),
                        )
                    },
                    enabled = inputs.rpeValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Timer")
                }
                OutlinedButton(
                    onClick = { set?.let(onLog) },
                    enabled = set != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Log Set")
                }
            } else {
                Button(
                    onClick = { set?.let(onLog) },
                    enabled = set != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Log Set")
                }
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
            // In a superset or circuit a set is a round of the whole section.
            val unit = if (step.inRounds) "Round" else "Set"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAddSet, enabled = canAddSet, modifier = Modifier.weight(1f)) {
                    Text("Add $unit")
                }
                OutlinedButton(onClick = onRemoveSet, enabled = canRemoveSet, modifier = Modifier.weight(1f)) {
                    Text("Remove $unit")
                }
            }
            if (canSwap || onShowHistory != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onShowHistory != null) {
                        TextButton(onClick = onShowHistory, modifier = Modifier.weight(1f)) {
                            Text("Past Workouts")
                        }
                    }
                    if (canSwap) {
                        TextButton(onClick = onSwap, modifier = Modifier.weight(1f)) {
                            Text("Swap Exercise")
                        }
                    }
                }
            }
        }
    }
}

/** How many work and rest intervals to run, and at what weight, before the interval timer starts. */
@Composable
private fun IntervalTimerSetup(loadUnit: BodyWeightUnit, onStart: (WeightHiitIntervalPlan) -> Unit) {
    var intervals by remember { mutableStateOf("8") }
    var work by remember { mutableStateOf("30") }
    var rest by remember { mutableStateOf("30") }
    var load by remember { mutableStateOf("") }
    val count = intervals.toIntOrNull()?.takeIf { it in 1..99 }
    val workSeconds = work.toIntOrNull()?.takeIf { it > 0 }
    val restSeconds = rest.toIntOrNull()?.takeIf { it >= 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = intervals,
            onValueChange = { text -> intervals = text.filter { it.isDigit() }.take(2) },
            label = { FieldLabel("Intervals") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = work,
            onValueChange = { text -> work = text.filter { it.isDigit() }.take(4) },
            label = { FieldLabel("Work (s)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = rest,
            onValueChange = { text -> rest = text.filter { it.isDigit() }.take(4) },
            label = { FieldLabel("Rest (s)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = load,
        onValueChange = { text -> load = text.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
        label = { FieldLabel("Weight (optional)") },
        trailingIcon = { Text(weightLoadUnitSuffix(loadUnit), style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            if (count != null && workSeconds != null && restSeconds != null) {
                onStart(
                    WeightHiitIntervalPlan(
                        intervals = count,
                        workSeconds = workSeconds,
                        restSeconds = restSeconds,
                        weightKg = load.takeIf { it.isNotBlank() }?.let { parseWeightInputToKg(it, loadUnit) },
                    ),
                )
            }
        },
        enabled = count != null && workSeconds != null && restSeconds != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Start Interval Timer")
    }
}

/** Fix a set already logged, or take it out with [onRemove] (null when it can't go). */
@Composable
internal fun WorkoutSessionEditSetDialog(
    title: String,
    logged: WorkoutSessionLoggedSet,
    loadUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (WeightSet) -> Unit,
    onRemove: (() -> Unit)? = null,
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
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Remove Set", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** "1:05" for the rest clock. */
private fun clockText(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * A rest counting down, with what comes after it. With the rest timer on Manual it waits at
 * full length until [onStart].
 */
@Composable
internal fun WorkoutSessionRestCard(
    title: String,
    secondsRemaining: Int,
    nextLabel: String?,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    modifier: Modifier = Modifier,
    awaitingStart: Boolean = false,
    onStart: () -> Unit = {},
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
                if (awaitingStart) {
                    OutlinedButton(onClick = onSkip) { Text("Skip Rest") }
                    Button(onClick = onStart) { Text("Start Rest") }
                } else {
                    OutlinedButton(onClick = onAddTime) { Text("+30 s") }
                    Button(onClick = onSkip) { Text("Skip Rest") }
                }
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
    /** Opens the whole exercise library instead of the builder's alternatives. */
    onChooseFromLibrary: () -> Unit,
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
                OutlinedButton(onClick = onChooseFromLibrary, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose From Library")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The big clock shared by the cardio and stretch cards: a countdown, or time so far. */
@Composable
private fun SessionStepClock(seconds: Int, caption: String) {
    Text(clockText(seconds), style = MaterialTheme.typography.displayMedium, fontSize = 56.sp)
    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One cardio leg: a steady effort, or one work or recovery leg of intervals. A new cardio item
 * waits for Start so there is time to get set; after that the clock runs by itself.
 */
@Composable
internal fun WorkoutSessionCardioCard(
    step: WorkoutSessionStep.CardioLeg,
    title: String,
    /** "Zone 2 · 130–145 bpm" or "170 bpm", when the builder set one. */
    heartRateTarget: String?,
    awaitingStart: Boolean,
    /** Seconds left when the leg has a target, otherwise seconds so far. */
    clockSeconds: Int,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onSkipLeg: () -> Unit,
    onSkipCardio: () -> Unit,
    onOpenCardioScreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** The Concept2 or cycling sensor: connect before Start, live numbers while it runs. */
    sensors: @Composable () -> Unit = {},
) {
    val timed = (step.targetSeconds ?: 0) > 0
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (step.isRecovery) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            val legLine = when {
                step.legCount <= 1 -> step.targetSeconds?.let { "${formatStepSeconds(it)} steady" } ?: "Open-ended"
                step.isRecovery -> "Recovery · leg ${step.legIndex + 1} of ${step.legCount}"
                else -> listOfNotNull(step.label?.takeIf { it.isNotBlank() }, "Leg ${step.legIndex + 1} of ${step.legCount}")
                    .joinToString(" · ")
            }
            Text(legLine, style = MaterialTheme.typography.titleMedium)
            heartRateTarget?.takeIf { !step.isRecovery }?.let {
                Text("Target $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sensors()
            if (awaitingStart) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
                TextButton(onClick = onOpenCardioScreen, modifier = Modifier.fillMaxWidth()) {
                    Text("Use Cardio Screen (GPS, Sensors)")
                }
            } else {
                SessionStepClock(clockSeconds, if (timed) "left" else "so far")
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(if (timed) "Finish Leg Now" else "Finish")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (step.legCount > 1) {
                    TextButton(onClick = onSkipLeg, modifier = Modifier.weight(1f)) {
                        Text("Skip Leg", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onSkipCardio, modifier = Modifier.weight(1f)) {
                    Text("Skip Cardio", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One stretch hold, a side at a time for a per-side stretch. */
@Composable
internal fun WorkoutSessionStretchCard(
    step: WorkoutSessionStep.StretchHold,
    name: String,
    procedure: String?,
    awaitingStart: Boolean,
    secondsLeft: Int,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onSkipHold: () -> Unit,
    onSkipStretch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = when (step.side) {
                    com.erv.app.workouts.WorkoutStretchSide.LEFT -> "Left side · ${formatStepSeconds(step.holdSeconds)}"
                    com.erv.app.workouts.WorkoutStretchSide.RIGHT -> "Right side · ${formatStepSeconds(step.holdSeconds)}"
                    null -> "Hold ${formatStepSeconds(step.holdSeconds)}"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            procedure?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (awaitingStart) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
            } else {
                SessionStepClock(secondsLeft, "left")
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (step.side != null) {
                    TextButton(onClick = onSkipHold, modifier = Modifier.weight(1f)) {
                        Text("Skip Side", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onSkipStretch, modifier = Modifier.weight(1f)) {
                    Text("Skip Stretch", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
