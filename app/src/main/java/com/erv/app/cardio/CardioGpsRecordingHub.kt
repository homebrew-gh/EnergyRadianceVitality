package com.erv.app.cardio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process buffer for the active cardio GPS session. The foreground service appends points;
 * the UI observes [pointsFlow] for live distance; the screen layer calls [snapshotAndClear] on stop.
 */
object CardioGpsRecordingHub {

    private val lock = Any()
    private val points = mutableListOf<CardioGpsPoint>()
    private val _pointsFlow = MutableStateFlow<List<CardioGpsPoint>>(emptyList())

    val pointsFlow: StateFlow<List<CardioGpsPoint>> = _pointsFlow.asStateFlow()

    fun clear() {
        synchronized(lock) {
            points.clear()
            _pointsFlow.value = emptyList()
        }
    }

    fun lastOrNull(): CardioGpsPoint? = synchronized(lock) { points.lastOrNull() }

    fun append(point: CardioGpsPoint) {
        synchronized(lock) {
            points.add(point)
            _pointsFlow.value = points.toList()
        }
    }

    fun snapshotAndClear(): List<CardioGpsPoint> {
        synchronized(lock) {
            val copy = points.toList()
            points.clear()
            _pointsFlow.value = emptyList()
            return copy
        }
    }
}
