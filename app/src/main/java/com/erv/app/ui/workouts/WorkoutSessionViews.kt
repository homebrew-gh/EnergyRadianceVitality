package com.erv.app.ui.workouts

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.weighttraining.PlateCalculatorUiState
import com.erv.app.ui.weighttraining.PlateLoadLine
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightHiitIntervalPlan
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
import kotlinx.coroutines.delay

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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.amount,
            onValueChange = { text -> state.amount = text.filter { it.isDigit() }.take(4) },
            label = { FieldLabel(if (timed) "Seconds" else "Reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(0.8f),
        )
        OutlinedTextField(
            value = state.load,
            onValueChange = { text -> state.load = text.filter { it.isDigit() || it == '.' || it == ',' }.take(7) },
            label = { FieldLabel("Weight") },
            // A suffix, not a trailing icon: the icon's slot squeezed the label onto two lines.
            suffix = { Text(weightLoadUnitSuffix(loadUnit)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.3f),
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
 * One set row of the exercise on screen. [logged] is set once it is done; [isNext] marks the set
 * the check logs now (the one on screen, or the one after the rest on screen).
 */
internal data class WorkoutSessionSetRow(
    val stepIndex: Int,
    val step: WorkoutSessionStep.LiftSet,
    val logged: WeightSet?,
    val skipped: Boolean,
    val isNext: Boolean,
    val canRemove: Boolean,
)

/** What a set row shows greyed out until something is typed, and logs if the check is tapped as is. */
private data class SetSuggestion(val amount: Int?, val weightKg: Double?)

private fun WeightSet.loggedAmount(timed: Boolean): Int? =
    (if (timed) durationSeconds else reps)?.takeIf { it > 0 }

private fun WeightSet.targetAmount(timed: Boolean): Int? =
    (if (timed) targetDurationSeconds else targetReps)?.takeIf { it > 0 }

/**
 * Suggestions per row: the builder's target, else the set before it today, else the same set
 * last time.
 */
private fun setSuggestions(rows: List<WorkoutSessionSetRow>, timed: Boolean, lastTimeSets: List<WeightSet>): List<SetSuggestion> {
    var previous: WeightSet? = null
    return rows.mapIndexed { index, row ->
        val lastTime = lastTimeSets.getOrNull(index) ?: lastTimeSets.lastOrNull()
        val suggestion = SetSuggestion(
            amount = row.step.target.targetAmount(timed) ?: previous?.loggedAmount(timed) ?: lastTime?.loggedAmount(timed),
            weightKg = row.step.target.targetWeightKg?.takeIf { it > 0 }
                ?: previous?.weightKg?.takeIf { it > 0 }
                ?: lastTime?.weightKg?.takeIf { it > 0 },
        )
        row.logged?.let { previous = it }
        suggestion
    }
}

private val SetNumberWidth = 24.dp
private val SetButtonSize = 40.dp
private const val RepsColumnWeight = 1f
private const val WeightColumnWeight = 1.3f
private const val RpeColumnWeight = 0.85f

/**
 * The exercise on screen with all its sets stacked as rows, as the old weight screen had them:
 * type reps, weight and RPE on any row, check the next one off to log it (blank fields take the
 * greyed suggestion), fix a logged one by typing over it, add or take away sets freely.
 */
@Composable
internal fun WorkoutSessionExerciseCard(
    exerciseName: String,
    sectionTitle: String?,
    loadUnit: BodyWeightUnit,
    rows: List<WorkoutSessionSetRow>,
    onLog: (WeightSet) -> Unit,
    onEditSet: (stepIndex: Int, set: WeightSet) -> Unit,
    onRemoveSet: (stepIndex: Int) -> Unit,
    canSwap: Boolean,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
    /** The sets from the last time this exercise was logged, from [describeLastTime]. */
    lastTime: String? = null,
    /** The same sets, for the greyed suggestions. */
    lastTimeSets: List<WeightSet> = emptyList(),
    /** Read-only next-session target from the latest web coach review, e.g. "Coach: 185 × 8, 8, 8 lb". */
    coachTarget: String? = null,
    onShowHistory: (() -> Unit)? = null,
    canAddSet: Boolean = false,
    onAddSet: () -> Unit = {},
    /** A superset or circuit takes away a whole round; null hides the button. */
    onRemoveRound: (() -> Unit)? = null,
    /** Skip Set and Skip Exercise: only for a workout run from a training block. */
    onSkipSet: (() -> Unit)? = null,
    onSkipExercise: (() -> Unit)? = null,
    /** "Barbell", "Dumbbell" and so on, under the name. */
    equipmentLabel: String? = null,
    /** Plates per side under the weight of the next set, for a plate-loaded lift. */
    plateCalculator: PlateCalculatorUiState? = null,
    /**
     * Opens the full-screen hold timer for the next set when it is timed, with the weight and RPE
     * typed so far; null when it can't.
     */
    onStartTimer: ((WeightSet) -> Unit)? = null,
    /** Runs the exercise on the interval timer instead of as sets; null when it can't. */
    onStartIntervals: ((WeightHiitIntervalPlan) -> Unit)? = null,
    /** True: an RPE chip under the next set logs it (and starts the rest). False: a check button does. */
    rpeLogsSet: Boolean = true,
    /** The exercise before or after this one in the workout; a swipe or the arrows go there. */
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    /** "Exercise 2 of 5", under the name. */
    positionLabel: String? = null,
    /** Set while looking at an exercise other than the one being done: goes back to it. */
    onBackToCurrent: (() -> Unit)? = null,
) {
    val first = rows.firstOrNull()?.step
    val timed = first?.target?.isTimedTarget() == true
    val inRounds = first?.inRounds == true
    var intervalsMode by remember(first?.segmentId, first?.itemId) { mutableStateOf(false) }
    val suggestions = setSuggestions(rows, timed, lastTimeSets)
    val swipePrevious by rememberUpdatedState(onPrevious)
    val swipeNext by rememberUpdatedState(onNext)
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Swipe left for the next exercise, right for the one before.
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        when {
                            dragged <= -swipeThresholdPx -> swipeNext?.invoke()
                            dragged >= swipeThresholdPx -> swipePrevious?.invoke()
                        }
                    },
                ) { _, amount -> dragged += amount }
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBackToCurrent != null) {
                FilledTonalButton(onClick = onBackToCurrent, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to Current Exercise")
                }
            }
            sectionTitle?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onPrevious != null || onNext != null) {
                    IconButton(onClick = { onPrevious?.invoke() }, enabled = onPrevious != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous exercise")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(exerciseName, style = MaterialTheme.typography.titleLarge)
                    val subtitle = listOfNotNull(equipmentLabel, positionLabel).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onPrevious != null || onNext != null) {
                    IconButton(onClick = { onNext?.invoke() }, enabled = onNext != null) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next exercise")
                    }
                }
            }
            lastTime?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            coachTarget?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
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
            SetColumnHeaders(timed = timed, inRounds = inRounds, loadUnit = loadUnit)
            rows.forEachIndexed { index, row ->
                SessionSetRow(
                    number = index + 1,
                    row = row,
                    timed = timed,
                    suggestion = suggestions[index],
                    loadUnit = loadUnit,
                    plateCalculator = plateCalculator?.takeIf { row.isNext },
                    onLog = onLog,
                    onEdit = { set -> onEditSet(row.stepIndex, set) },
                    onRemove = if (row.canRemove) ({ onRemoveSet(row.stepIndex) }) else null,
                    onStartTimer = onStartTimer?.takeIf { row.isNext && timed },
                    rpeLogsSet = rpeLogsSet,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onAddSet, enabled = canAddSet) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (inRounds) "Add Round" else "Add Set")
                }
                if (onRemoveRound != null) {
                    TextButton(onClick = onRemoveRound) { Text("Remove Round") }
                }
            }
            if (onSkipSet != null || onSkipExercise != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    onSkipSet?.let {
                        TextButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Text("Skip Set", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    onSkipExercise?.let {
                        TextButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Text("Skip Exercise", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
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

/** Captions over the set rows, once, so the fields themselves stay wide enough for a number. */
@Composable
private fun SetColumnHeaders(timed: Boolean, inRounds: Boolean, loadUnit: BodyWeightUnit) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (inRounds) "Rd" else "Set", style = style, color = color, modifier = Modifier.width(SetNumberWidth))
        Text(if (timed) "Seconds" else "Reps", style = style, color = color, modifier = Modifier.weight(RepsColumnWeight))
        Text(
            "Weight (${weightLoadUnitSuffix(loadUnit)})",
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier.weight(WeightColumnWeight),
        )
        Text("RPE", style = style, color = color, modifier = Modifier.weight(RpeColumnWeight))
        Spacer(Modifier.width(SetButtonSize * 2 + 6.dp))
    }
}

@Composable
private fun SessionSetRow(
    number: Int,
    row: WorkoutSessionSetRow,
    timed: Boolean,
    suggestion: SetSuggestion,
    loadUnit: BodyWeightUnit,
    plateCalculator: PlateCalculatorUiState?,
    onLog: (WeightSet) -> Unit,
    onEdit: (WeightSet) -> Unit,
    onRemove: (() -> Unit)?,
    onStartTimer: ((WeightSet) -> Unit)?,
    rpeLogsSet: Boolean,
) {
    val logged = row.logged
    // Fields for a set still to come start blank, with the suggestion greyed in; a logged set's
    // fields hold what was logged.
    val inputs = remember(row.stepIndex, logged != null) {
        SetInputState(logged ?: WeightSet(), timed, loadUnit)
    }
    var touched by remember(row.stepIndex, logged != null) { mutableStateOf(false) }
    val amount = inputs.amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: suggestion.amount
    val weightKg = if (inputs.load.isBlank()) suggestion.weightKg else inputs.loadKg(loadUnit)
    /** The set as it would be logged with [effort], or null while there is no amount to log. */
    fun setWith(effort: Double?): WeightSet? = amount?.let {
        if (timed) {
            row.step.target.copy(reps = 0, durationSeconds = it, weightKg = weightKg, rpe = effort, done = null)
        } else {
            row.step.target.copy(reps = it, weightKg = weightKg, rpe = effort, done = null)
        }
    }
    val toLog = if (inputs.rpeValid) setWith(parseSessionRpe(inputs.rpe)) else null
    // With RPE chips on, the chips log the next set; the timer still does for a timed hold.
    val chipsLog = rpeLogsSet && row.isNext && logged == null && !(timed && onStartTimer != null)
    // A logged set fixed by typing over it saves itself once the typing stops.
    if (logged != null) {
        val edited = inputs.toSet(logged, timed, loadUnit)
        LaunchedEffect(edited, touched) {
            if (touched && edited != null && edited != logged) {
                delay(700)
                onEdit(edited)
            }
        }
    }
    val numberColor = if (row.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "$number",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
            color = numberColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(SetNumberWidth),
        )
        if (row.skipped) {
            Text(
                "Skipped",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(RepsColumnWeight + WeightColumnWeight + RpeColumnWeight),
            )
        } else {
            SetNumberField(
                value = inputs.amount,
                onValueChange = { text ->
                    touched = true
                    inputs.amount = text.filter { it.isDigit() }.take(4)
                },
                placeholder = suggestion.amount?.toString(),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(RepsColumnWeight),
            )
            SetNumberField(
                value = inputs.load,
                onValueChange = { text ->
                    touched = true
                    inputs.load = text.filter { it.isDigit() || it == '.' || it == ',' }.take(7)
                },
                placeholder = suggestion.weightKg?.let { formatWeightLoadNumber(it, loadUnit) },
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(WeightColumnWeight),
            )
            SetNumberField(
                value = inputs.rpe,
                onValueChange = { text ->
                    touched = true
                    inputs.rpe = text.filter { it.isDigit() || it == '.' || it == ',' }.take(4)
                },
                placeholder = null,
                keyboardType = KeyboardType.Decimal,
                isError = !inputs.rpeValid,
                modifier = Modifier.weight(RpeColumnWeight),
            )
        }
        when {
            logged != null -> FilledIconButton(onClick = {}, modifier = Modifier.size(SetButtonSize)) {
                Icon(Icons.Default.Check, contentDescription = "Set $number logged")
            }
            row.isNext && onStartTimer != null -> FilledIconButton(
                onClick = { onStartTimer(row.step.target.copy(weightKg = weightKg, rpe = parseSessionRpe(inputs.rpe), done = null)) },
                enabled = inputs.rpeValid,
                modifier = Modifier.size(SetButtonSize),
            ) {
                Icon(Icons.Default.Timer, contentDescription = "Start timer for set $number")
            }
            chipsLog -> Spacer(Modifier.width(SetButtonSize))
            else -> OutlinedIconButton(
                onClick = { toLog?.let(onLog) },
                enabled = row.isNext && toLog != null,
                modifier = Modifier.size(SetButtonSize),
            ) {
                Icon(Icons.Default.Check, contentDescription = "Log set $number")
            }
        }
        IconButton(onClick = { onRemove?.invoke() }, enabled = onRemove != null, modifier = Modifier.size(SetButtonSize)) {
            Icon(Icons.Default.Close, contentDescription = "Remove set $number")
        }
    }
    if (chipsLog) {
        RpeChipStrip(
            enabled = amount != null,
            onPick = { effort -> setWith(effort)?.let(onLog) },
            modifier = Modifier.padding(start = SetNumberWidth + 6.dp),
        )
    }
    if (row.isNext && timed && onStartTimer != null && toLog != null) {
        // The hold timer counts the time; this logs a hold done without it.
        TextButton(onClick = { onLog(toLog) }, modifier = Modifier.padding(start = SetNumberWidth)) {
            Text("Log Without Timer")
        }
    }
    if (plateCalculator != null) {
        PlateLoadLine(
            weightKg = weightKg,
            loadUnit = loadUnit,
            calc = plateCalculator,
            onUseWeightKg = { kg ->
                touched = true
                inputs.load = formatWeightLoadNumber(kg, loadUnit)
            },
            modifier = Modifier.padding(start = SetNumberWidth + 6.dp),
        )
    }
}

private val RPE_CHOICES = listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

/** RPE choices under the next set: tapping one logs the set with it, and the rest starts. */
@Composable
private fun RpeChipStrip(enabled: Boolean, onPick: (Double?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (enabled) "Pick RPE to log the set" else "Enter reps to log the set",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            RPE_CHOICES.forEach { effort ->
                FilterChip(
                    selected = false,
                    enabled = enabled,
                    onClick = { onPick(effort) },
                    label = { Text(formatRpeFieldForSets(effort)) },
                )
            }
            FilterChip(
                selected = false,
                enabled = enabled,
                onClick = { onPick(null) },
                label = { Text("No RPE") },
            )
        }
    }
}

/** A compact number field for a set row; the column caption sits above the rows instead of a label. */
@Composable
private fun SetNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = if (placeholder != null) {
            { Text(placeholder, maxLines = 1) }
        } else {
            null
        },
        isError = isError,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
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
 * The rest, as a bar over the workout instead of a screen of its own: the clock, what is next,
 * and +30 s or Skip. With the rest timer on Manual it waits at full length until [onStart].
 */
@Composable
internal fun WorkoutSessionRestBar(
    title: String,
    secondsRemaining: Int,
    totalSeconds: Int,
    nextLabel: String?,
    onSkip: () -> Unit,
    onAddTime: () -> Unit,
    modifier: Modifier = Modifier,
    awaitingStart: Boolean = false,
    onStart: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    nextLabel?.let {
                        Text(
                            "Next: $it",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    clockText(secondsRemaining),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (awaitingStart) {
                    TextButton(onClick = onStart) { Text("Start") }
                } else {
                    TextButton(onClick = onAddTime) { Text("+30 s") }
                }
                TextButton(onClick = onSkip) { Text("Skip") }
            }
            if (!awaitingStart && totalSeconds > 0) {
                LinearProgressIndicator(
                    progress = { (secondsRemaining.toFloat() / totalSeconds).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, top = 4.dp),
                )
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
    /** While it runs: live GPS distance and pace, or why GPS isn't recording. */
    gpsStatus: String? = null,
    /** Before Start: the Record GPS Route switch for outdoor activities. */
    gpsControls: @Composable () -> Unit = {},
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
                gpsControls()
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start") }
                TextButton(onClick = onOpenCardioScreen, modifier = Modifier.fillMaxWidth()) {
                    Text("Use Cardio Screen")
                }
            } else {
                SessionStepClock(clockSeconds, if (timed) "left" else "so far")
                gpsStatus?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
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
