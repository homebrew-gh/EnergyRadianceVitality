package com.erv.app.cycling

import java.util.UUID

/**
 * Bluetooth Fitness Machine Service (FTMS, 0x1826): the standard most smart trainers (Wahoo,
 * Tacx, Zwift Hub, Elite) and newer treadmills use to stream live data. Only reading is used here;
 * the app never takes control of the machine.
 */
object FtmsUuids {
    val SERVICE: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    val INDOOR_BIKE_DATA: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805f9b34fb")
    val TREADMILL_DATA: UUID = UUID.fromString("00002ACD-0000-1000-8000-00805f9b34fb")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

enum class FtmsMachineType { INDOOR_BIKE, TREADMILL }

/**
 * One FTMS notification. A field is null when the packet didn't carry it: machines split their
 * data over several notifications, so callers keep the last value of each field.
 */
data class FtmsReading(
    val speedKmh: Double? = null,
    val cadenceRpm: Int? = null,
    val powerWatts: Int? = null,
    /** The machine's own running total since it was switched on or reset. */
    val totalDistanceMeters: Int? = null,
    val inclinePercent: Double? = null,
    val heartRateBpm: Int? = null,
)

object FtmsParser {

    /** Indoor Bike Data (0x2AD2). Returns null for a packet too short for its flags. */
    fun indoorBike(data: ByteArray): FtmsReading? {
        val r = Reader(data)
        val flags = r.u16() ?: return null
        var speed: Double? = null
        var cadence: Int? = null
        var power: Int? = null
        var distance: Int? = null
        var heartRate: Int? = null
        // Bit 0 is "More Data": when it is clear, the packet carries the instantaneous speed.
        if (flags and 0x0001 == 0) speed = (r.u16() ?: return null) / 100.0
        if (flags and 0x0002 != 0) r.skip(2) ?: return null // average speed
        if (flags and 0x0004 != 0) cadence = ((r.u16() ?: return null) / 2.0).toInt()
        if (flags and 0x0008 != 0) r.skip(2) ?: return null // average cadence
        if (flags and 0x0010 != 0) distance = r.u24() ?: return null
        if (flags and 0x0020 != 0) r.skip(2) ?: return null // resistance level
        if (flags and 0x0040 != 0) power = r.s16() ?: return null
        if (flags and 0x0080 != 0) r.skip(2) ?: return null // average power
        if (flags and 0x0100 != 0) r.skip(5) ?: return null // expended energy
        if (flags and 0x0200 != 0) heartRate = r.u8() ?: return null
        return FtmsReading(
            speedKmh = speed,
            cadenceRpm = cadence,
            powerWatts = power?.takeIf { it >= 0 },
            totalDistanceMeters = distance,
            heartRateBpm = heartRate?.takeIf { it > 0 },
        )
    }

    /** Treadmill Data (0x2ACD). Returns null for a packet too short for its flags. */
    fun treadmill(data: ByteArray): FtmsReading? {
        val r = Reader(data)
        val flags = r.u16() ?: return null
        var speed: Double? = null
        var distance: Int? = null
        var incline: Double? = null
        var heartRate: Int? = null
        var power: Int? = null
        if (flags and 0x0001 == 0) speed = (r.u16() ?: return null) / 100.0
        if (flags and 0x0002 != 0) r.skip(2) ?: return null // average speed
        if (flags and 0x0004 != 0) distance = r.u24() ?: return null
        if (flags and 0x0008 != 0) {
            incline = (r.s16() ?: return null) / 10.0
            r.skip(2) ?: return null // ramp angle
        }
        if (flags and 0x0010 != 0) r.skip(4) ?: return null // elevation gain, positive and negative
        if (flags and 0x0020 != 0) r.skip(1) ?: return null // instantaneous pace
        if (flags and 0x0040 != 0) r.skip(1) ?: return null // average pace
        if (flags and 0x0080 != 0) r.skip(5) ?: return null // expended energy
        if (flags and 0x0100 != 0) heartRate = r.u8() ?: return null
        if (flags and 0x0200 != 0) r.skip(1) ?: return null // metabolic equivalent
        if (flags and 0x0400 != 0) r.skip(2) ?: return null // elapsed time
        if (flags and 0x0800 != 0) r.skip(2) ?: return null // remaining time
        if (flags and 0x1000 != 0) {
            r.skip(2) ?: return null // force on belt
            power = r.s16() ?: return null
        }
        return FtmsReading(
            speedKmh = speed,
            powerWatts = power?.takeIf { it >= 0 },
            totalDistanceMeters = distance,
            // 0x7FFF means "not available".
            inclinePercent = incline?.takeIf { it > -3276.0 && it < 3276.0 },
            heartRateBpm = heartRate?.takeIf { it > 0 },
        )
    }

    private class Reader(private val data: ByteArray) {
        private var index = 0
        private fun has(n: Int) = index + n <= data.size
        fun u8(): Int? = if (has(1)) (data[index].toInt() and 0xFF).also { index += 1 } else null
        fun u16(): Int? = if (has(2)) {
            ((data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)).also { index += 2 }
        } else {
            null
        }
        fun s16(): Int? = u16()?.let { if (it >= 0x8000) it - 0x10000 else it }
        fun u24(): Int? = if (has(3)) {
            ((data[index].toInt() and 0xFF) or
                ((data[index + 1].toInt() and 0xFF) shl 8) or
                ((data[index + 2].toInt() and 0xFF) shl 16)).also { index += 3 }
        } else {
            null
        }
        fun skip(n: Int): Unit? {
            if (!has(n)) return null
            index += n
            return Unit
        }
    }
}

/**
 * Distance for one workout from FTMS readings. Uses the machine's total distance when it sends one
 * (and keeps counting if the machine resets mid-workout); otherwise adds up speed over time.
 */
class FtmsDistanceTracker {
    var meters: Double = 0.0
        private set
    private var lastTotal: Int? = null
    private var lastSpeedKmh: Double? = null
    private var lastSpeedAtMillis: Long? = null
    private var machineSendsTotal = false

    fun reset() {
        meters = 0.0
        lastTotal = null
        lastSpeedKmh = null
        lastSpeedAtMillis = null
        machineSendsTotal = false
    }

    /** Folds in one reading taken at [atMillis]; [paused] readings move the baseline but add nothing. */
    fun add(reading: FtmsReading, atMillis: Long, paused: Boolean = false) {
        val total = reading.totalDistanceMeters
        if (total != null) {
            machineSendsTotal = true
            val previous = lastTotal
            if (previous != null && !paused) {
                // A smaller total means the machine was reset: count from zero again.
                meters += if (total >= previous) (total - previous).toDouble() else total.toDouble()
            }
            lastTotal = total
        }
        val speed = reading.speedKmh
        if (speed != null) {
            val prevSpeed = lastSpeedKmh
            val prevAt = lastSpeedAtMillis
            if (!machineSendsTotal && !paused && prevSpeed != null && prevAt != null) {
                val seconds = (atMillis - prevAt) / 1000.0
                if (seconds in 0.0..MAX_GAP_SECONDS) meters += prevSpeed / 3.6 * seconds
            }
            lastSpeedKmh = speed
            lastSpeedAtMillis = atMillis
        }
    }

    private companion object {
        /** A gap longer than this (lost connection) adds no distance. */
        const val MAX_GAP_SECONDS = 5.0
    }
}
