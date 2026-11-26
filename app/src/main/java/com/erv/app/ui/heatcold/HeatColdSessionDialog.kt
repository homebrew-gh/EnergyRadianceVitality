package com.erv.app.ui.heatcold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.erv.app.data.TemperatureUnit
import com.erv.app.heatcold.HeatColdMode
import com.erv.app.heatcold.HeatColdTimelineEntry
import com.erv.app.heatcold.convertTemperature
import com.erv.app.heatcold.displayName
import com.erv.app.ui.components.ClockTimePickerDialog
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.PastDatePickerDialog
import com.erv.app.ui.components.epochSecondsAtLocal
import com.erv.app.ui.components.localDateTimeOf
import com.erv.app.ui.components.pastEntryDateFormatter
import com.erv.app.ui.components.pastEntryTimeFormatter
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

/** What the session dialog saves. Temperature is in the Settings unit. */
internal data class HeatColdSessionInput(
    val mode: HeatColdMode,
    val date: LocalDate,
    val loggedAtEpochSeconds: Long,
    val durationSeconds: Int,
    val temp: Int?,
    val tempUnit: TemperatureUnit
)

/**
 * Adds a past sauna or cold plunge session ([editing] null) or edits one: type, date, time,
 * minutes and seconds, and temperature. [onDelete] is shown only when editing.
 */
@Composable
internal fun HeatColdSessionDialog(
    editing: HeatColdTimelineEntry?,
    tempUnit: TemperatureUnit,
    onDismiss: () -> Unit,
    onSave: (HeatColdSessionInput) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val start = remember(editing) {
        editing?.session?.loggedAtEpochSeconds?.takeIf { it > 0 }?.let { localDateTimeOf(it) }
    }
    var mode by remember(editing) { mutableStateOf(editing?.mode ?: HeatColdMode.SAUNA) }
    var date by remember(editing) { mutableStateOf(editing?.logDate ?: LocalDate.now()) }
    var time by remember(editing) {
        mutableStateOf(start?.second ?: LocalTime.now().withSecond(0).withNano(0))
    }
    val startSeconds = editing?.session?.durationSeconds ?: 15 * 60
    var minutesText by remember(editing) { mutableStateOf((startSeconds / 60).toString()) }
    var secondsText by remember(editing) { mutableStateOf((startSeconds % 60).takeIf { it > 0 }?.toString().orEmpty()) }
    var tempText by remember(editing) {
        val s = editing?.session
        val shown = s?.tempValue?.let { v ->
            val from = s.tempUnit ?: tempUnit
            convertTemperature(v.roundToInt(), from, tempUnit)
        }
        mutableStateOf(shown?.toString().orEmpty())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val minutes = minutesText.toIntOrNull() ?: 0
    val seconds = secondsText.toIntOrNull() ?: 0
    val totalSeconds = minutes * 60 + seconds
    val validDuration = seconds in 0..59 && totalSeconds in 1..(3 * 60 * 60)
    val temp = tempText.toIntOrNull()
    val validTemp = tempText.isBlank() || temp != null
    val suffix = if (tempUnit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Log a past session" else "Edit session") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeatColdMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.displayName()) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) { Text(date.format(pastEntryDateFormatter)) }
                    OutlinedButton(onClick = { showTimePicker = true }) { Text(time.format(pastEntryTimeFormatter)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { FieldLabel("Minutes") },
                        singleLine = true,
                        isError = !validDuration,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { FieldLabel("Seconds") },
                        singleLine = true,
                        isError = !validDuration,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = tempText,
                    onValueChange = { tempText = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { FieldLabel("Temperature ($suffix, optional)") },
                    singleLine = true,
                    isError = !validTemp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete session") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        HeatColdSessionInput(
                            mode = mode,
                            date = date,
                            loggedAtEpochSeconds = epochSecondsAtLocal(date, time),
                            durationSeconds = totalSeconds,
                            temp = temp,
                            tempUnit = tempUnit
                        )
                    )
                },
                enabled = validDuration && validTemp
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
