package com.erv.app.ui.lighttherapy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import com.erv.app.lighttherapy.DatedLightSession
import com.erv.app.lighttherapy.LightDevice
import com.erv.app.ui.components.ClockTimePickerDialog
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.ui.components.PastDatePickerDialog
import com.erv.app.ui.components.epochSecondsAtLocal
import com.erv.app.ui.components.localDateTimeOf
import com.erv.app.ui.components.pastEntryDateFormatter
import com.erv.app.ui.components.pastEntryTimeFormatter
import java.time.LocalDate
import java.time.LocalTime

/** What the session dialog saves. */
data class LightSessionInput(
    val date: LocalDate,
    val loggedAtEpochSeconds: Long,
    val minutes: Int,
    val device: LightDevice?
)

/**
 * Adds a past light session ([editing] null) or edits one: date, time, minutes and light.
 * [onDelete] is shown only when editing.
 */
@Composable
internal fun LightSessionDialog(
    devices: List<LightDevice>,
    editing: DatedLightSession?,
    onDismiss: () -> Unit,
    onSave: (LightSessionInput) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val start = remember(editing) {
        editing?.session?.loggedAtEpochSeconds?.takeIf { it > 0 }?.let { localDateTimeOf(it) }
    }
    var date by remember(editing) { mutableStateOf(editing?.logDate ?: LocalDate.now()) }
    var time by remember(editing) {
        mutableStateOf(start?.second ?: LocalTime.now().withSecond(0).withNano(0))
    }
    var minutesText by remember(editing) { mutableStateOf((editing?.session?.minutes ?: 15).toString()) }
    var deviceId by remember(editing) {
        mutableStateOf(editing?.session?.deviceId?.takeIf { id -> devices.any { it.id == id } })
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val minutes = minutesText.toIntOrNull()?.takeIf { it in 1..240 }
    val routineName = editing?.session?.routineName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Log a past session" else "Edit session") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (routineName != null) {
                    Text(routineName, style = MaterialTheme.typography.titleSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) { Text(date.format(pastEntryDateFormatter)) }
                    OutlinedButton(onClick = { showTimePicker = true }) { Text(time.format(pastEntryTimeFormatter)) }
                }
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { FieldLabel("Duration (minutes)") },
                    singleLine = true,
                    isError = minutes == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (devices.isNotEmpty()) {
                    FormSectionLabelSmall("Light")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(selected = deviceId == null, onClick = { deviceId = null }, label = { Text("None") })
                        devices.forEach { d ->
                            FilterChip(selected = deviceId == d.id, onClick = { deviceId = d.id }, label = { Text(d.name) })
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete session") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    minutes?.let { m ->
                        onSave(
                            LightSessionInput(
                                date = date,
                                loggedAtEpochSeconds = epochSecondsAtLocal(date, time),
                                minutes = m,
                                device = deviceId?.let { id -> devices.firstOrNull { it.id == id } }
                            )
                        )
                    }
                },
                enabled = minutes != null
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        PastDatePickerDialog(
            initial = date,
            onDismiss = { showDatePicker = false },
            onPicked = {
                date = it
                showDatePicker = false
            }
        )
    }
    if (showTimePicker) {
        ClockTimePickerDialog(
            initial = time,
            title = "Session time",
            onDismiss = { showTimePicker = false },
            onPicked = {
                time = it
                showTimePicker = false
            }
        )
    }
}
