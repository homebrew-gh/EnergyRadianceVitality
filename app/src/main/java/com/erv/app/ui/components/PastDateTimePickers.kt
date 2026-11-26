@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.erv.app.ui.reminders.TimeWheelPickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Tue, Nov 26" for date buttons on past-entry forms. */
val pastEntryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

/** "7:30 PM" for time buttons on past-entry forms. */
val pastEntryTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/** Epoch seconds for [time] on [date] in the phone's time zone. */
fun epochSecondsAtLocal(date: LocalDate, time: LocalTime): Long =
    date.atTime(time).atZone(ZoneId.systemDefault()).toEpochSecond()

/** The local date and time of [epochSeconds]. */
fun localDateTimeOf(epochSeconds: Long): Pair<LocalDate, LocalTime> {
    val zoned = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())
    return zoned.toLocalDate() to zoned.toLocalTime().withSecond(0).withNano(0)
}

/** Material date picker for a day up to today (a later pick is pulled back to today). */
@Composable
fun PastDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                    } else {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val today = LocalDate.now()
                        onPicked(if (picked.isAfter(today)) today else picked)
                    }
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState)
    }
}

/** The app's wheel time picker, in and out as a [LocalTime]. */
@Composable
fun ClockTimePickerDialog(
    initial: LocalTime,
    title: String,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit
) {
    val hour12 = (initial.hour % 12).let { if (it == 0) 12 else it }
    TimeWheelPickerDialog(
        initialHour = hour12,
        initialMinute = initial.minute,
        initialIsPm = initial.hour >= 12,
        title = title,
        onDismiss = onDismiss,
        onConfirm = { hour, minute, isPm ->
            val h24 = (hour % 12) + if (isPm) 12 else 0
            onPicked(LocalTime.of(h24, minute))
        }
    )
}
