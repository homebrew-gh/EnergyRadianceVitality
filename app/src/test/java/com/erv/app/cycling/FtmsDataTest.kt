package com.erv.app.cycling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtmsDataTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun indoorBike_readsSpeedCadencePowerAndDistance() {
        // Flags 0x0054: cadence, total distance, power; bit 0 clear so speed is present.
        val packet = bytes(
            0x54, 0x00,
            0xC4, 0x09, // 25.00 km/h
            0xB4, 0x00, // 180 half-rpm = 90 rpm
            0xE8, 0x03, 0x00, // 1000 m
            0xC8, 0x00, // 200 W
        )
        val reading = FtmsParser.indoorBike(packet)!!
        assertEquals(25.0, reading.speedKmh!!, 0.001)
        assertEquals(90, reading.cadenceRpm)
        assertEquals(1000, reading.totalDistanceMeters)
        assertEquals(200, reading.powerWatts)
        assertNull(reading.heartRateBpm)
    }

    @Test
    fun indoorBike_moreDataPacketHasNoSpeed() {
        // Flags 0x0041: More Data set (no speed), power present.
        val reading = FtmsParser.indoorBike(bytes(0x41, 0x00, 0x96, 0x00))!!
        assertNull(reading.speedKmh)
        assertEquals(150, reading.powerWatts)
    }

    @Test
    fun indoorBike_skipsAverageAndResistanceFields() {
        // Flags 0x02E6: avg speed, cadence, resistance, power, avg power, heart rate.
        val packet = bytes(
            0xE6, 0x02,
            0x10, 0x27, // speed 100.00 km/h
            0x00, 0x00, // average speed
            0x78, 0x00, // 60 rpm
            0x05, 0x00, // resistance
            0x64, 0x00, // 100 W
            0x00, 0x00, // average power
            0x8C, // 140 bpm
        )
        val reading = FtmsParser.indoorBike(packet)!!
        assertEquals(100.0, reading.speedKmh!!, 0.001)
        assertEquals(60, reading.cadenceRpm)
        assertEquals(100, reading.powerWatts)
        assertEquals(140, reading.heartRateBpm)
    }

    @Test
    fun indoorBike_truncatedPacketIsNull() {
        assertNull(FtmsParser.indoorBike(bytes(0x54, 0x00, 0xC4)))
        assertNull(FtmsParser.indoorBike(bytes(0x00)))
    }

    @Test
    fun treadmill_readsSpeedDistanceAndIncline() {
        // Flags 0x000C: total distance and inclination; speed present.
        val packet = bytes(
            0x0C, 0x00,
            0x84, 0x03, // 9.00 km/h
            0xF4, 0x01, 0x00, // 500 m
            0x0F, 0x00, // 1.5 %
            0x00, 0x00, // ramp angle
        )
        val reading = FtmsParser.treadmill(packet)!!
        assertEquals(9.0, reading.speedKmh!!, 0.001)
        assertEquals(500, reading.totalDistanceMeters)
        assertEquals(1.5, reading.inclinePercent!!, 0.001)
        assertNull(reading.cadenceRpm)
    }

    @Test
    fun treadmill_negativeInclineAndHeartRate() {
        // Flags 0x0108: inclination and heart rate.
        val packet = bytes(0x08, 0x01, 0x00, 0x00, 0xEC, 0xFF, 0x00, 0x00, 0x96)
        val reading = FtmsParser.treadmill(packet)!!
        assertEquals(0.0, reading.speedKmh!!, 0.001)
        assertEquals(-2.0, reading.inclinePercent!!, 0.001)
        assertEquals(150, reading.heartRateBpm)
    }

    @Test
    fun distance_usesMachineTotalAndSurvivesReset() {
        val tracker = FtmsDistanceTracker()
        tracker.add(FtmsReading(totalDistanceMeters = 1200, speedKmh = 30.0), 0)
        tracker.add(FtmsReading(totalDistanceMeters = 1300, speedKmh = 30.0), 1_000)
        assertEquals(100.0, tracker.meters, 0.001)
        // Machine reset to zero, then moves on.
        tracker.add(FtmsReading(totalDistanceMeters = 20), 2_000)
        tracker.add(FtmsReading(totalDistanceMeters = 70), 3_000)
        assertEquals(170.0, tracker.meters, 0.001)
    }

    @Test
    fun distance_pausedReadingsAddNothing() {
        val tracker = FtmsDistanceTracker()
        tracker.add(FtmsReading(totalDistanceMeters = 0), 0)
        tracker.add(FtmsReading(totalDistanceMeters = 50), 1_000, paused = true)
        tracker.add(FtmsReading(totalDistanceMeters = 60), 2_000)
        assertEquals(10.0, tracker.meters, 0.001)
    }

    @Test
    fun distance_fromSpeedWhenNoTotal() {
        val tracker = FtmsDistanceTracker()
        tracker.add(FtmsReading(speedKmh = 36.0), 0) // 10 m/s
        tracker.add(FtmsReading(speedKmh = 36.0), 2_000)
        assertEquals(20.0, tracker.meters, 0.001)
        // A long gap (dropped connection) adds nothing.
        tracker.add(FtmsReading(speedKmh = 36.0), 60_000)
        assertEquals(20.0, tracker.meters, 0.001)
        tracker.reset()
        assertEquals(0.0, tracker.meters, 0.001)
    }
}
