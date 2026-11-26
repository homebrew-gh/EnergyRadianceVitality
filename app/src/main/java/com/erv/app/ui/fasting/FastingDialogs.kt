package com.erv.app.ui.fasting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.fasting.FASTING_MAX_TARGET_HOURS
import com.erv.app.fasting.FastingMood
import com.erv.app.fasting.FastingSession
import com.erv.app.fasting.displayName
import com.erv.app.fasting.elapsedSeconds
import com.erv.app.fasting.fastingNowEpochSeconds
import com.erv.app.fasting.formatFastingDuration
import com.erv.app.fasting.formatTargetHours
import com.erv.app.fasting.targetHoursTotal
import com.erv.app.ui.components.ClockTimePickerDialog
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.ui.components.PastDatePickerDialog
import com.erv.app.ui.components.epochSecondsAtLocal
import com.erv.app.ui.components.localDateTimeOf
import com.erv.app.ui.components.pastEntryDateFormatter
import com.erv.app.ui.components.pastEntryTimeFormatter

/** Target lengths offered as chips, in hours. */
internal val FASTING_TARGET_PRESET_HOURS = listOf(24, 36, 48, 72)

/** Date and time buttons for a moment up to now; [onChange] gets epoch seconds. */
@Composable
internal fun PastMomentButtons(
    label: String,
    epochSeconds: Long,
    onChange: (Long) -> Unit,
) {
    val (date, time) = localDateTimeOf(epochSeconds)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    FormSectionLabelSmall(label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }) { Text(date.format(pastEntryDateFormatter)) }
        OutlinedButton(onClick = { pickTime = true }) { Text(time.format(pastEntryTimeFormatter)) }
    }
    if (pickDate) {
        PastDatePickerDialog(
            initial = date,
            onDismiss = { pickDate = false },
            onPicked = {
                pickDate = false
                onChange(epochSecondsAtLocal(it, time).coerceAtMost(fastingNowEpochSeconds()))
            }
        )
    }
    if (pickTime) {
        ClockTimePickerDialog(
            initial = time,
            title = label,
            onDismiss = { pickTime = false },
            onPicked = {
                pickTime = false
                onChange(epochSecondsAtLocal(date, it).coerceAtMost(fastingNowEpochSeconds()))
            }
        )
    }
}

/** Target chips (24 h, 36 h, 48 h, 72 h) plus a custom number of hours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FastingTargetPicker(
    hours: Int,
    onHoursChange: (Int) -> Unit,
) {
    var custom by remember { mutableStateOf(hours !in FASTING_TARGET_PRESET_HOURS) }
    var customText by remember { mutableStateOf(if (hours in FASTING_TARGET_PRESET_HOURS) "" else hours.toString()) }
    FormSectionLabelSmall("Target")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FASTING_TARGET_PRESET_HOURS.forEach { h ->
            FilterChip(
                selected = !custom && hours == h,
                onClick = {
                    custom = false
                    onHoursChange(h)
                },
                label = { Text(formatTargetHours(h)) }
            )
        }
        FilterChip(
            selected = custom,
            onClick = {
                custom = true
                customText.toIntOrNull()?.let(onHoursChange)
            },
            label = { Text("Custom") }
        )
    }
    if (custom) {
        OutlinedTextField(
            value = customText,
            onValueChange = { raw ->
                customText = raw.filter { it.isDigit() }.take(3)
                customText.toIntOrNull()?.takeIf { it in 1..FASTING_MAX_TARGET_HOURS }?.let(onHoursChange)
            },
            label = { FieldLabel("Target hours") },
            supportingText = { Text("1 to $FASTING_MAX_TARGET_HOURS hours") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Change the running fast's start time (for a fast started earlier) and its target. */
@Composable
internal fun ActiveFastEditDialog(
    session: FastingSession,
    onDismiss: () -> Unit,
    onSave: (startedAtEpochSeconds: Long, targetHours: Int) -> Unit,
) {
    var start by remember { mutableStateOf(session.startedAtEpochSeconds) }
    var hours by remember { mutableStateOf(session.targetHoursTotal()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit fast") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                PastMomentButtons(label = "Started", epochSeconds = start, onChange = { start = it })
                FastingTargetPicker(hours = hours, onHoursChange = { hours = it })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(start, hours) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** What the completion dialog saves. */
internal data class FastingCompletionInput(
    val mood: FastingMood?,
    /** Weight in [weightUnit], when entered. */
    val weight: Double?,
    val notes: String,
    val saveWeightToBodyTracker: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FastingCompletionDialog(
    session: FastingSession,
    weightUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onSave: (FastingCompletionInput) -> Unit,
) {
    var mood by remember { mutableStateOf<FastingMood?>(null) }
    var weightText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var toBodyTracker by remember { mutableStateOf(true) }
    val weight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val unitLabel = if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete fast") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Duration: ${formatFastingDuration(session.elapsedSeconds())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormSectionLabel("How did it feel?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingMood.entries.forEach { option ->
                        FilterChip(
                            selected = mood == option,
                            onClick = { mood = if (mood == option) null else option },
                            label = { Text(option.displayName()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    label = { FieldLabel("Weight ($unitLabel, optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (weight != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = toBodyTracker, onCheckedChange = { toBodyTracker = it })
                        Text("Also save to Body Tracker", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { FieldLabel("Notes (optional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(FastingCompletionInput(mood, weight, notes, toBodyTracker && weight != null))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not yet") } },
    )
}

/** What the log's add or edit dialog saves. */
internal data class FastingSessionInput(
    val startedAtEpochSeconds: Long,
    val endedAtEpochSeconds: Long,
    val mood: FastingMood?,
    val weight: String,
    val notes: String,
)

/** Adds a past fast ([editing] null) or edits a logged one; [onDelete] shows when editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FastingSessionDialog(
    editing: FastingSession?,
    onDismiss: () -> Unit,
    onSave: (FastingSessionInput) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val now = remember { fastingNowEpochSeconds() }
    var start by remember { mutableStateOf(editing?.startedAtEpochSeconds ?: (now - 24 * 3600L)) }
    var end by remember { mutableStateOf(editing?.endedAtEpochSeconds ?: now) }
    var mood by remember { mutableStateOf(editing?.mood) }
    var weight by remember { mutableStateOf(editing?.weight.orEmpty()) }
    var notes by remember { mutableStateOf(editing?.notes.orEmpty()) }
    val valid = end > start
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Log a past fast" else "Edit fast") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                PastMomentButtons(label = "Started", epochSeconds = start, onChange = { start = it })
                PastMomentButtons(label = "Ended", epochSeconds = end, onChange = { end = it })
                Text(
                    if (valid) "Length: ${formatFastingDuration(end - start)}" else "The end has to be after the start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
                FormSectionLabelSmall("How did it feel?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FastingMood.entries.forEach { option ->
                        FilterChip(
                            selected = mood == option,
                            onClick = { mood = if (mood == option) null else option },
                            label = { Text(option.displayName()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.take(20) },
                    label = { FieldLabel("Weight (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { FieldLabel("Notes (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete fast") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(FastingSessionInput(start, end, mood, weight.trim(), notes.trim())) },
                enabled = valid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
