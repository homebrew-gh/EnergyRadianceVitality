package com.erv.app.cardio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/**
 * Buffer for the active cardio GPS session. The foreground service appends points;
 * the UI observes [pointsFlow] for live distance; the screen layer calls [snapshotAndClear] on stop.
 *
 * Points are also appended to a file in app storage, tagged with the timer's start epoch, so a run
 * whose app was killed keeps the route recorded before the kill ([beginSession] reloads it).
 */
object CardioGpsRecordingHub {

    private const val TAG = "CardioGpsRecordingHub"
    private const val FILE_NAME = "cardio_live_gps.csv"
    private const val HEADER_PREFIX = "epoch="

    private val lock = Any()
    private val points = mutableListOf<CardioGpsPoint>()
    private val _pointsFlow = MutableStateFlow<List<CardioGpsPoint>>(emptyList())
    private val io = Executors.newSingleThreadExecutor()

    /** While the timer is paused, fixes are dropped so a pause adds no route or distance. */
    @Volatile
    var paused: Boolean = false

    /** Start epoch of the timer these points belong to; [Long.MIN_VALUE] when none. */
    private var sessionEpoch: Long = Long.MIN_VALUE
    private var file: File? = null

    val pointsFlow: StateFlow<List<CardioGpsPoint>> = _pointsFlow.asStateFlow()

    private fun fileFor(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    /**
     * Ties the buffer to the timer that started at [startEpoch]. The same timer keeps its points;
     * after a restart they are read back from the file; a new timer starts empty.
     */
    fun beginSession(context: Context, startEpoch: Long) {
        synchronized(lock) {
            if (startEpoch >= 0L && startEpoch == sessionEpoch) return
            points.clear()
            val f = fileFor(context)
            if (startEpoch < 0L) {
                sessionEpoch = Long.MIN_VALUE
                file = null
                io.execute { f.delete() }
            } else {
                val restored = runCatching { readSavedPoints(f, startEpoch) }.getOrNull()
                if (restored != null) {
                    points.addAll(restored)
                } else {
                    io.execute {
                        try {
                            f.writeText("$HEADER_PREFIX$startEpoch\n")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not start GPS file", e)
                        }
                    }
                }
                sessionEpoch = startEpoch
                file = f
            }
            _pointsFlow.value = points.toList()
        }
    }

    /** True when the file holds a route for the timer that started at [startEpoch]. */
    fun hasSavedSession(context: Context, startEpoch: Long): Boolean =
        runCatching { readHeaderEpoch(fileFor(context)) == startEpoch }.getOrDefault(false)

    fun clear() {
        synchronized(lock) {
            points.clear()
            _pointsFlow.value = emptyList()
            forgetFileLocked()
        }
    }

    fun lastOrNull(): CardioGpsPoint? = synchronized(lock) { points.lastOrNull() }

    fun append(point: CardioGpsPoint) {
        if (paused) return
        synchronized(lock) {
            points.add(point)
            _pointsFlow.value = points.toList()
            val f = file ?: return
            val line = encodePoint(point)
            io.execute {
                try {
                    f.appendText(line)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save GPS point", e)
                }
            }
        }
    }

    fun snapshotAndClear(): List<CardioGpsPoint> {
        synchronized(lock) {
            val copy = points.toList()
            points.clear()
            _pointsFlow.value = emptyList()
            forgetFileLocked()
            return copy
        }
    }

    private fun forgetFileLocked() {
        val f = file
        sessionEpoch = Long.MIN_VALUE
        file = null
        if (f != null) io.execute { f.delete() }
    }

    private fun readHeaderEpoch(f: File): Long? {
        if (!f.isFile) return null
        val first = f.bufferedReader().use { it.readLine() } ?: return null
        return first.removePrefix(HEADER_PREFIX).takeIf { first.startsWith(HEADER_PREFIX) }?.toLongOrNull()
    }

    /** Points saved for [startEpoch], or null when the file is missing or for another timer. */
    private fun readSavedPoints(f: File, startEpoch: Long): List<CardioGpsPoint>? {
        if (readHeaderEpoch(f) != startEpoch) return null
        return decodePoints(f.readLines().drop(1))
    }

    /** One line per point: lat,lon,epochSeconds,altitude (altitude blank when unknown). */
    internal fun encodePoint(p: CardioGpsPoint): String =
        "${p.lat},${p.lon},${p.epochSeconds},${p.altitudeMeters ?: ""}\n"

    /** Skips lines it can't read (a half-written last line after a kill). */
    internal fun decodePoints(lines: List<String>): List<CardioGpsPoint> = lines.mapNotNull { line ->
        val parts = line.split(',')
        if (parts.size != 4) return@mapNotNull null
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val epoch = parts[2].toLongOrNull() ?: return@mapNotNull null
        CardioGpsPoint(lat = lat, lon = lon, epochSeconds = epoch, altitudeMeters = parts[3].toDoubleOrNull())
    }
}
