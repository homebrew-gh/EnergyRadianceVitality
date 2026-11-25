package com.erv.app.ui.cardio

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.erv.app.cardio.CardioActiveTimerSession
import com.erv.app.cardio.CardioGpsForegroundService
import com.erv.app.cardio.CardioGpsRecordingHub
import com.erv.app.cardio.CardioLiveSessionStore
import com.erv.app.cardio.CardioSavedLiveSession
import com.erv.app.cardio.CardioWorkoutSplit
import com.erv.app.cardio.isPaused
import com.erv.app.cardio.notificationClockBaseEpochSeconds
import com.erv.app.cardio.withPaused
import com.erv.app.cardio.withResumed
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

    init {
        restoreSavedSession()
    }

    /** Every change to the live timer goes through here, so the saved copy never lags. */
    private fun setActive(session: CardioActiveTimerSession?) {
        _activeTimer.value = session
        CardioLiveSessionStore.write(
            getApplication(),
            session?.let { CardioSavedLiveSession(it, suppressNotificationForActiveSession) },
        )
    }

    /**
     * Picks a live run back up after Android killed the app: the clock runs from the saved start
     * time, and the notification and GPS (if it was recording) restart. Heart rate samples from
     * before the kill are not kept; the strap records again once it reconnects.
     */
    private fun restoreSavedSession() {
        val app = getApplication<Application>()
        val saved = CardioLiveSessionStore.read(app) ?: return
        val session = saved.session
        suppressNotificationForActiveSession = saved.suppressNotification
        _activeTimer.value = session
        // Show the run straight away so it is obvious it came back.
        _cardioLiveUiExpanded.value = true
        CardioGpsRecordingHub.paused = session.isPaused()
        if (!session.isTimerRunning()) return
        val startEpoch = session.timerStartEpochSeconds()
        try {
            if (!suppressNotificationForActiveSession) {
                CardioLiveWorkoutForegroundService.start(
                    app,
                    session.notificationClockBaseEpochSeconds(),
                    paused = session.isPaused(),
                )
            }
            val single = session as? CardioActiveTimerSession.Single
            if (single != null && CardioGpsRecordingHub.hasSavedSession(app, startEpoch)) {
                CardioGpsForegroundService.start(app, single.draft.title, startEpoch)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart services for a restored cardio session", e)
        }
    }

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
            CardioGpsRecordingHub.paused = false
            if (!suppressNotification && session.isTimerRunning()) {
                CardioLiveWorkoutForegroundService.start(getApplication(), session.timerStartEpochSeconds())
            }
            setActive(session)
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
            setActive(started)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to begin cardio live timer", e)
            false
        }
    }

    fun replaceSession(session: CardioActiveTimerSession) {
        if (_activeTimer.value == null) return
        setActive(session)
    }

    /** Pauses a running single-activity timer: the clock stops and GPS fixes are dropped. */
    fun pause() {
        val current = _activeTimer.value as? CardioActiveTimerSession.Single ?: return
        if (!current.isTimerRunning() || current.draft.isPaused()) return
        val paused = current.copy(draft = current.draft.withPaused())
        CardioGpsRecordingHub.paused = true
        setActive(paused)
        refreshNotification(paused)
    }

    fun resume() {
        val current = _activeTimer.value as? CardioActiveTimerSession.Single ?: return
        if (!current.draft.isPaused()) return
        val resumed = current.copy(draft = current.draft.withResumed())
        CardioGpsRecordingHub.paused = false
        setActive(resumed)
        refreshNotification(resumed)
    }

    private fun refreshNotification(session: CardioActiveTimerSession) {
        if (suppressNotificationForActiveSession) return
        try {
            CardioLiveWorkoutForegroundService.start(
                getApplication(),
                session.notificationClockBaseEpochSeconds(),
                paused = session.isPaused(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update cardio notification", e)
        }
    }

    /** Saves the splits marked on a single-activity timer, so they outlast the screen. */
    fun updateSplits(splits: List<CardioWorkoutSplit>) {
        val current = _activeTimer.value as? CardioActiveTimerSession.Single ?: return
        if (current.draft.splits == splits) return
        setActive(current.copy(draft = current.draft.copy(splits = splits)))
    }

    fun clearSession() {
        if (_activeTimer.value != null) {
            CardioLiveWorkoutForegroundService.stop(getApplication())
        }
        suppressNotificationForActiveSession = false
        CardioGpsRecordingHub.paused = false
        setActive(null)
        _cardioLiveUiExpanded.value = true
    }
}
