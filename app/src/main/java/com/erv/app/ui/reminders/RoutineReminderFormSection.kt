@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.erv.app.ui.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erv.app.reminders.RoutineReminderDraft
import com.erv.app.reminders.RoutineReminderFrequency
import com.erv.app.reminders.RoutineReminderScheduler
import com.erv.app.reminders.displayTimeLabel
import com.erv.app.supplements.SupplementWeekday
import com.erv.app.supplements.shortLabel

/**
 * Shared reminder UI: enable toggle, wheel time picker, repeat frequency, weekdays, exact-alarm hint.
 */
@Composable
fun RoutineReminderFormSection(
    reminderDraft: RoutineReminderDraft,
    onReminderDraftChange: (RoutineReminderDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text("Reminder", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = reminderDraft.enabled,
                onCheckedChange = { enabled ->
                    onReminderDraftChange(reminderDraft.copy(enabled = enabled))
                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
            Text("Enable reminder")
        }
        if (reminderDraft.enabled && !notificationsGranted) {
            Text(
                "Notifications are off for this app. Reminders will not appear until you allow them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = { RoutineReminderScheduler.openAppNotificationSettings(context) }) {
                Text("Open notification settings")
            }
        }
        OutlinedButton(
            onClick = { showTimePicker = true },
            enabled = reminderDraft.enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reminder time: ${reminderDraft.displayTimeLabel()}")
        }
        if (showTimePicker) {
            TimeWheelPickerDialog(
                initialHour = reminderDraft.hour,
                initialMinute = reminderDraft.minute,
                initialIsPm = reminderDraft.isPm,
                onDismiss = { showTimePicker = false },
                onConfirm = { hour, minute, isPm ->
                    onReminderDraftChange(reminderDraft.copy(hour = hour, minute = minute, isPm = isPm))
                    showTimePicker = false
                }
            )
        }
        ReminderFrequencyDropdown(
            value = reminderDraft.frequency,
            onSelected = { selected ->
                onReminderDraftChange(
                    reminderDraft.copy(
                        frequency = selected,
                        repeatDays = if (selected == RoutineReminderFrequency.DAILY || selected == RoutineReminderFrequency.ONCE) {
                            emptySet()
                        } else reminderDraft.repeatDays.ifEmpty { SupplementWeekday.entries.toSet() }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (reminderDraft.frequency == RoutineReminderFrequency.WEEKLY ||
            reminderDraft.frequency == RoutineReminderFrequency.CUSTOM_DAYS
        ) {
            Text("Select days", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SupplementWeekday.entries.forEach { weekday ->
                    FilterChip(
                        selected = weekday in reminderDraft.repeatDays,
                        onClick = {
                            val updated = reminderDraft.repeatDays.toMutableSet()
                            if (!updated.add(weekday)) updated.remove(weekday)
                            onReminderDraftChange(reminderDraft.copy(repeatDays = updated))
                        },
                        label = { Text(weekday.shortLabel()) }
                    )
                }
            }
        }
        if (!RoutineReminderScheduler.canScheduleExactAlarms(context)) {
            Text(
                "Enable exact alarms on this device to keep reminder times precise.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Allow exact alarms")
            }
        }
    }
}

@Composable
private fun ReminderFrequencyDropdown(
    value: RoutineReminderFrequency,
    onSelected: (RoutineReminderFrequency) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label: (RoutineReminderFrequency) -> String = {
        when (it) {
            RoutineReminderFrequency.ONCE -> "Once"
            RoutineReminderFrequency.DAILY -> "Daily"
            RoutineReminderFrequency.WEEKLY -> "Weekly"
            RoutineReminderFrequency.CUSTOM_DAYS -> "Specific days"
        }
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label(value),
            onValueChange = {},
            readOnly = true,
            label = { Text("Repeat") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RoutineReminderFrequency.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}
