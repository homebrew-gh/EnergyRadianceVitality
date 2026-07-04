package com.erv.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoutineReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoutineReminderScheduler.ACTION_ROUTINE_REMINDER) return
        val routineId = intent.getStringExtra(RoutineReminderScheduler.EXTRA_ROUTINE_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = RoutineReminderRepository(context)
                val reminder = repository.reminderForRoutine(routineId)
                if (reminder == null) {
                    RoutineReminderScheduler.cancel(context, routineId)
                    return@launch
                }
                if (!reminder.enabled) return@launch
                RoutineReminderScheduler.showNotification(context, reminder)
                when (reminder.frequency) {
                    RoutineReminderFrequency.ONCE ->
                        repository.upsertReminder(reminder.copy(enabled = false))
                    else ->
                        RoutineReminderScheduler.schedule(context, reminder)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class RoutineReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RoutineReminderRepository(context).restoreAllSchedules()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
