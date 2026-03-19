package com.erv.app.reminders

import com.erv.app.supplements.SupplementWeekday

/** UI state for editing a [RoutineReminder] (12h clock + repeat options). */
data class RoutineReminderDraft(
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val isPm: Boolean = false,
    val frequency: RoutineReminderFrequency = RoutineReminderFrequency.DAILY,
    val repeatDays: Set<SupplementWeekday> = SupplementWeekday.entries.toSet()
)

fun RoutineReminderDraft.displayTimeLabel(): String {
    val hourDisplay = hour.coerceIn(1, 12)
    val minuteDisplay = minute.coerceIn(0, 59).toString().padStart(2, '0')
    return "$hourDisplay:$minuteDisplay ${if (isPm) "PM" else "AM"}"
}

fun RoutineReminder.toDraft(): RoutineReminderDraft = RoutineReminderDraft(
    enabled = enabled,
    hour = when (val normalized = hour.coerceIn(0, 23)) {
        0 -> 12
        in 1..11 -> normalized
        12 -> 12
        else -> normalized - 12
    },
    minute = minute.coerceIn(0, 59),
    isPm = hour.coerceIn(0, 23) >= 12,
    frequency = frequency,
    repeatDays = repeatDays.toSet()
)

fun RoutineReminderDraft.toReminder(routineId: String, routineName: String): RoutineReminder = RoutineReminder(
    routineId = routineId,
    routineName = routineName,
    enabled = enabled,
    hour = if (isPm) {
        when (hour.coerceIn(1, 12)) {
            12 -> 12
            else -> hour.coerceIn(1, 12) + 12
        }
    } else {
        when (hour.coerceIn(1, 12)) {
            12 -> 0
            else -> hour.coerceIn(1, 12)
        }
    },
    minute = minute.coerceIn(0, 59),
    frequency = frequency,
    repeatDays = repeatDays.toList().sortedBy { it.ordinal }
)

fun RoutineReminderDraft.isValid(): Boolean {
    if (!enabled) return true
    if (hour !in 1..12 || minute !in 0..59) return false
    if ((frequency == RoutineReminderFrequency.WEEKLY || frequency == RoutineReminderFrequency.CUSTOM_DAYS) && repeatDays.isEmpty()) return false
    return true
}
