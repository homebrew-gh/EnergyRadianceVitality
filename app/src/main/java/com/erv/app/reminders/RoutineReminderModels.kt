package com.erv.app.reminders

import com.erv.app.supplements.SupplementWeekday
import kotlinx.serialization.Serializable

@Serializable
enum class RoutineReminderFrequency {
    ONCE,
    DAILY,
    WEEKLY,
    CUSTOM_DAYS
}

@Serializable
data class RoutineReminder(
    val routineId: String,
    val routineName: String,
    val enabled: Boolean = false,
    val hour: Int = 8,
    val minute: Int = 0,
    val frequency: RoutineReminderFrequency = RoutineReminderFrequency.DAILY,
    val repeatDays: List<SupplementWeekday> = emptyList()
)

@Serializable
data class RoutineReminderState(
    val reminders: List<RoutineReminder> = emptyList()
) {
    fun reminderForRoutine(routineId: String): RoutineReminder? =
        reminders.firstOrNull { it.routineId == routineId }
}

fun RoutineReminder.displayTime(): String = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
