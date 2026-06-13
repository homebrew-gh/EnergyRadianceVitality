package com.erv.app.fasting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FastingCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FastingCompletionScheduler.ACTION_FASTING_COMPLETE) return
        val sessionId = intent.getStringExtra(FastingCompletionScheduler.EXTRA_SESSION_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val active = FastingRepository(context).currentState().activeSession ?: return@launch
                if (active.id == sessionId) {
                    FastingCompletionScheduler.showCompletionNotification(context, active)
                    FastingForegroundService.stop(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class FastingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FastingCompletionScheduler.restoreActiveSchedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
