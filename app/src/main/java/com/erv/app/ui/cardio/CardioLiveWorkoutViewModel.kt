package com.erv.app.ui.cardio

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioLiveWorkoutForegroundService
import com.erv.app.cardio.timerStartEpochSeconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CardioLiveWorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        private const val TAG = "CardioLiveWorkoutVm"
    }

    private val _activeTimer = MutableStateFlow<CardioActiveTimerSession?>(null)
    val activeTimer: StateFlow<CardioActiveTimerSession?> = _activeTimer.asStateFlow()

    /** When false, category/dashboard shows normal UI + banner while timer + FGS keep running. */
    private val _cardioLiveUiExpanded = MutableStateFlow(true)
    val cardioLiveUiExpanded: StateFlow<Boolean> = _cardioLiveUiExpanded.asStateFlow()

    val hasActiveTimer: Boolean get() = _activeTimer.value != null

    fun setCardioLiveUiExpanded(expanded: Boolean) {
        _cardioLiveUiExpanded.value = expanded
    }

    /**
     * Begins tracking [session], starts the live-timer foreground service, and expands the full-screen UI.
     * Returns false if a session is already active.
     */
    fun tryStartSession(session: CardioActiveTimerSession): Boolean {
        if (_activeTimer.value != null) return false
        return try {
            CardioLiveWorkoutForegroundService.start(getApplication(), session.timerStartEpochSeconds())
            _activeTimer.value = session
            _cardioLiveUiExpanded.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start cardio live timer foreground service", e)
            false
        }
    }

    fun replaceSession(session: CardioActiveTimerSession) {
        if (_activeTimer.value == null) return
        _activeTimer.value = session
    }

    fun clearSession() {
        if (_activeTimer.value != null) {
            CardioLiveWorkoutForegroundService.stop(getApplication())
        }
        _activeTimer.value = null
        _cardioLiveUiExpanded.value = true
    }
}
