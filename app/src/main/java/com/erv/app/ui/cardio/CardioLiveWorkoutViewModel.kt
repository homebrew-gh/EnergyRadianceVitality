package com.erv.app.ui.cardio

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioLiveWorkoutForegroundService
import com.erv.app.cardio.isTimerRunning
import com.erv.app.cardio.timerStartEpochSeconds
import com.erv.app.cardio.withStartedNow
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

    private var suppressNotificationForActiveSession = false

    val hasActiveTimer: Boolean get() = _activeTimer.value != null

    val isTimerRunning: Boolean get() = _activeTimer.value?.isTimerRunning() == true

    fun setCardioLiveUiExpanded(expanded: Boolean) {
        _cardioLiveUiExpanded.value = expanded
    }

    /**
     * Opens the live workout UI for [session]. The timer, GPS, and foreground notification begin only
     * after [beginTimer] (Start Workout) unless the session was already started elsewhere.
     */
    fun tryStartSession(
        session: CardioActiveTimerSession,
        suppressNotification: Boolean = false,
    ): Boolean {
        if (_activeTimer.value != null) return false
        return try {
            suppressNotificationForActiveSession = suppressNotification
            if (!suppressNotification && session.isTimerRunning()) {
                CardioLiveWorkoutForegroundService.start(getApplication(), session.timerStartEpochSeconds())
            }
            _activeTimer.value = session
            _cardioLiveUiExpanded.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start cardio live timer foreground service", e)
            false
        }
    }

    /** Starts the session clock, GPS, and optional foreground notification after Start Workout. */
    fun beginTimer(): Boolean {
        val current = _activeTimer.value ?: return false
        if (current.isTimerRunning()) return true
        val started = current.withStartedNow()
        return try {
            if (!suppressNotificationForActiveSession) {
                CardioLiveWorkoutForegroundService.start(getApplication(), started.timerStartEpochSeconds())
            }
            _activeTimer.value = started
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to begin cardio live timer", e)
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
        suppressNotificationForActiveSession = false
    }
}
