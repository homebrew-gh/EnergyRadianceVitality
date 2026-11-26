package com.erv.app.ui.supplements

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.supplements.SupplementLibraryState
import com.erv.app.supplements.SupplementLogEntry
import com.erv.app.supplements.SupplementRoutine
import com.erv.app.supplements.activeSteps
import com.erv.app.supplements.activityStyleSummary
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.ui.reminders.TimeWheelPickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val supplementTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val supplementDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

internal fun formatSupplementTime(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalTime().format(supplementTimeFormatter)

internal fun epochSecondsAt(date: LocalDate, time: LocalTime): Long =
    date.atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()

/**
 * Checklist shown when logging a routine: each step starts ticked unless it is paused,
 * its supplement is gone, or it was already logged today through this routine.
 */
@Composable
internal fun RoutineLogChecklistDialog(
    routine: SupplementRoutine,
    state: SupplementLibraryState,
    takenToday: Map<String, Long>,
    onDismiss: () -> Unit,
    onLog: (Set<String>) -> Unit
) {
    val steps = remember(routine) { routine.steps.distinctBy { it.supplementId } }
    val activeIds = remember(routine, state) { state.activeSteps(routine).map { it.supplementId }.toSet() }
    var selected by remember(routine.id) {
        mutableStateOf(activeIds.filterNot { it in takenToday }.toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log ${routine.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Untick anything you're skipping today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                steps.forEach { step ->
                    val supplement = state.supplementById(step.supplementId)
                    val enabled = supplement != null
                    val checked = step.supplementId in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                selected = if (checked) selected - step.supplementId else selected + step.supplementId
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { on ->
                                selected = if (on) selected + step.supplementId else selected - step.supplementId
                            },
                            enabled = enabled
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.activityStyleSummary(supplement), style = MaterialTheme.typography.bodyMedium)
                            val note = when {
                                supplement == null -> "Supplement was deleted"
                                takenToday[step.supplementId] != null ->
                                    "Already taken today at ${formatSupplementTime(takenToday.getValue(step.supplementId))}"
                                step.paused -> "Paused in this routine"
                                else -> null
                            }
                            if (note != null) {
                                Text(
                                    note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onLog(selected) }, enabled = selected.isNotEmpty()) {
                Text(if (selected.isEmpty()) "Log" else "Log ${selected.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** What a past-day entry logs: one supplement, or a routine's active steps. */
internal sealed interface SupplementPastEntryTarget {
    data class Supplement(val supplementId: String) : SupplementPastEntryTarget
    data class Routine(val routineId: String) : SupplementPastEntryTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupplementPastEntryDialog(
    state: SupplementLibraryState,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (date: LocalDate, takenAtEpochSeconds: Long, target: SupplementPastEntryTarget) -> Unit
) {
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var logRoutine by remember { mutableStateOf(state.routines.isNotEmpty()) }
    var selectedSupplementId by remember { mutableStateOf<String?>(null) }
    var selectedRoutineId by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val target: SupplementPastEntryTarget? = if (logRoutine) {
        selectedRoutineId?.let { SupplementPastEntryTarget.Routine(it) }
    } else {
        selectedSupplementId?.let { SupplementPastEntryTarget.Supplement(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a past day") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) { Text(date.format(supplementDateFormatter)) }
                    OutlinedButton(onClick = { showTimePicker = true }) { Text(time.format(supplementTimeFormatter)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = logRoutine,
                        onClick = { logRoutine = true },
                        label = { Text("Routine") },
                        enabled = state.routines.isNotEmpty()
                    )
                    FilterChip(
                        selected = !logRoutine,
                        onClick = { logRoutine = false },
                        label = { Text("One Supplement") }
                    )
                }
                if (logRoutine) {
                    FormSectionLabelSmall("Routine")
                    state.routines.forEach { routine ->
                        PastEntryOptionRow(
                            label = routine.name,
                            selected = selectedRoutineId == routine.id,
                            onClick = { selectedRoutineId = routine.id }
                        )
                    }
                    Text(
                        "Logs every supplement in the routine that isn't paused.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FormSectionLabelSmall("Supplement")
                    if (state.supplements.isEmpty()) {
                        Text("Add a supplement first.", style = MaterialTheme.typography.bodySmall)
                    }
                    state.supplements.forEach { supplement ->
                        PastEntryOptionRow(
                            label = supplement.name,
                            selected = selectedSupplementId == supplement.id,
                            onClick = { selectedSupplementId = supplement.id }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let { onSave(date, epochSecondsAt(date, time), it) } },
                enabled = target != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            val today = LocalDate.now()
                            date = if (picked.isAfter(today)) today else picked
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
    if (showTimePicker) {
        SupplementTimePicker(
            initial = time,
            onDismiss = { showTimePicker = false },
            onPicked = {
                time = it
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun PastEntryOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Edits the dose text and time of one logged intake; the day stays the same. */
@Composable
internal fun SupplementIntakeEditDialog(
    entry: SupplementLogEntry,
    logDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (dosageTaken: String, takenAtEpochSeconds: Long) -> Unit
) {
    var dosage by remember(entry) { mutableStateOf(entry.dosageTaken) }
    var time by remember(entry) {
        mutableStateOf(
            if (entry.takenAtEpochSeconds > 0L) {
                Instant.ofEpochSecond(entry.takenAtEpochSeconds).atZone(ZoneId.systemDefault()).toLocalTime()
                    .withSecond(0).withNano(0)
            } else {
                LocalTime.of(8, 0)
            }
        )
    }
    var showTimePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${entry.supplementName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    logDate.format(supplementDateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { FieldLabel("Dose taken") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { showTimePicker = true }) { Text(time.format(supplementTimeFormatter)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(dosage.trim(), epochSecondsAt(logDate, time)) },
                enabled = dosage.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
    if (showTimePicker) {
        SupplementTimePicker(
            initial = time,
            onDismiss = { showTimePicker = false },
            onPicked = {
                time = it
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun SupplementTimePicker(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit
) {
    val hour12 = (initial.hour % 12).let { if (it == 0) 12 else it }
    TimeWheelPickerDialog(
        initialHour = hour12,
        initialMinute = initial.minute,
        initialIsPm = initial.hour >= 12,
        title = "Time taken",
        onDismiss = onDismiss,
        onConfirm = { hour, minute, isPm ->
            val h24 = (hour % 12) + if (isPm) 12 else 0
            onPicked(LocalTime.of(h24, minute))
        }
    )
}
