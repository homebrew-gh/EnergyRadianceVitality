package com.erv.app.cardio

import java.io.ByteArrayOutputStream
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioFileImportTest {

    private fun parsed(bytes: ByteArray, name: String? = null): CardioFileActivity {
        val result = CardioFileImport.parse(bytes, name)
        assertTrue("expected a parse, got $result", result is CardioFileParse.Parsed)
        return (result as CardioFileParse.Parsed).activity
    }

    private val gpx = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="StravaGPX" xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
          <metadata><time>2026-11-20T14:00:00Z</time></metadata>
          <trk>
            <name>Morning Ride</name>
            <type>cycling</type>
            <trkseg>
              <trkpt lat="40.0000" lon="-75.0000"><ele>10.0</ele><time>2026-11-20T14:00:00Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>120</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
              <trkpt lat="40.0010" lon="-75.0000"><ele>12.0</ele><time>2026-11-20T14:00:30Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>130</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
              <trkpt lat="40.0020" lon="-75.0000"><ele>11.0</ele><time>2026-11-20T14:01:00Z</time></trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent().toByteArray()

    @Test
    fun gpx_readsTheRouteHeartRateAndSport() {
        val a = parsed(gpx)
        assertEquals(CardioFileFormat.GPX, a.format)
        assertEquals(Instant.parse("2026-11-20T14:00:00Z").epochSecond, a.startEpochSeconds)
        assertEquals(60, a.elapsedSeconds)
        assertEquals(3, a.points.size)
        assertEquals(listOf(120, 130), a.heartRate.map { it.bpm })
        assertEquals(CardioBuiltinActivity.BIKE, CardioFileImport.guessActivity(a))
        // 0.002 degrees of latitude is about 222 m.
        assertEquals(222.0, a.resolvedDistanceMeters!!, 2.0)
    }

    @Test
    fun gpx_toASessionAndBackOut() {
        val a = parsed(gpx)
        val session = CardioFileImport.toSession(
            a,
            CardioActivitySnapshot(builtin = CardioBuiltinActivity.BIKE, displayLabel = "Bike"),
        )
        assertEquals(CardioSessionSource.IMPORTED, session.source)
        assertEquals(1, session.durationMinutes)
        assertEquals(125, session.heartRate?.avgBpm)
        assertEquals("import_gpx", session.gpsTrack?.source)
        val out = CardioFileImport.toGpx(session)!!
        assertTrue(out.contains("<gpxtpx:hr>130</gpxtpx:hr>"))
        // What we write reads back the same.
        val again = parsed(out.toByteArray())
        assertEquals(a.points.size, again.points.size)
        assertEquals(a.heartRate.map { it.bpm }, again.heartRate.map { it.bpm })
        assertEquals(CardioBuiltinActivity.BIKE, CardioFileImport.guessActivity(again))
    }

    @Test
    fun gpx_refusesADoctype() {
        val evil = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <gpx><trk><trkseg><trkpt lat="1" lon="1"><time>2026-11-20T14:00:00Z</time><name>&x;</name></trkpt></trkseg></trk></gpx>"""
        assertTrue(CardioFileImport.parse(evil.toByteArray()) is CardioFileParse.Failed)
    }

    @Test
    fun tcx_readsLapTotalsAndTrackpoints() {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-11-21T10:00:00.000Z</Id>
                  <Lap StartTime="2026-11-21T10:00:00.000Z">
                    <TotalTimeSeconds>1500.0</TotalTimeSeconds>
                    <DistanceMeters>5000.0</DistanceMeters>
                    <Track>
                      <Trackpoint><Time>2026-11-21T10:00:00.000Z</Time>
                        <Position><LatitudeDegrees>40.0</LatitudeDegrees><LongitudeDegrees>-75.0</LongitudeDegrees></Position>
                        <HeartRateBpm><Value>140</Value></HeartRateBpm></Trackpoint>
                      <Trackpoint><Time>2026-11-21T10:26:00.000Z</Time>
                        <Position><LatitudeDegrees>40.01</LatitudeDegrees><LongitudeDegrees>-75.0</LongitudeDegrees></Position>
                        <HeartRateBpm><Value>160</Value></HeartRateBpm></Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent().toByteArray()
        val a = parsed(tcx)
        assertEquals(CardioFileFormat.TCX, a.format)
        assertEquals(1500, a.movingSeconds)
        assertEquals(1560, a.elapsedSeconds)
        assertEquals(5000.0, a.distanceMeters!!, 0.01)
        assertEquals(listOf(140, 160), a.heartRate.map { it.bpm })
        assertEquals(CardioBuiltinActivity.RUN, CardioFileImport.guessActivity(a))
        assertEquals(25, CardioFileImport.toSession(a, CardioActivitySnapshot(builtin = CardioBuiltinActivity.RUN, displayLabel = "Run")).durationMinutes)
    }

    /** A FIT file with a record definition, three records (one with a compressed timestamp) and a session. */
    private fun fitFile(): ByteArray {
        val data = ByteArrayOutputStream()
        fun u8(v: Int) = data.write(v and 0xFF)
        fun u16(v: Int) { u8(v); u8(v shr 8) }
        fun u32(v: Long) { u16((v and 0xFFFF).toInt()); u16(((v shr 16) and 0xFFFF).toInt()) }
        val start = Instant.parse("2026-11-22T12:00:00Z").epochSecond - FitDecoder.FIT_EPOCH
        // Definition, local 0 = record: timestamp, lat, long, heart rate, distance.
        u8(0x40); u8(0); u8(0); u16(20); u8(5)
        u8(253); u8(4); u8(0x86)
        u8(0); u8(4); u8(0x85)
        u8(1); u8(4); u8(0x85)
        u8(3); u8(1); u8(0x02)
        u8(5); u8(4); u8(0x86)
        val semis = { degrees: Double -> (degrees / FitDecoder.SEMICIRCLE_TO_DEGREES).toLong() and 0xFFFFFFFFL }
        u8(0x00); u32(start); u32(semis(40.0)); u32(semis(-75.0)); u8(110); u32(0)
        u8(0x00); u32(start + 10); u32(semis(40.001)); u32(semis(-75.0)); u8(0xFF); u32(11_100)
        // Definition, local 1 = record without timestamp, used with a compressed header.
        u8(0x41); u8(0); u8(0); u16(20); u8(2)
        u8(3); u8(1); u8(0x02)
        u8(5); u8(4); u8(0x86)
        // Compressed header: local 1, offset = (start + 20) & 0x1F.
        u8(0x80 or (1 shl 5) or (((start + 20) and 0x1F).toInt())); u8(130); u32(22_200)
        // Definition, local 2 = session: start, sport, sub sport, elapsed, timer, distance.
        u8(0x42); u8(0); u8(0); u16(18); u8(6)
        u8(2); u8(4); u8(0x86)
        u8(5); u8(1); u8(0x00)
        u8(6); u8(1); u8(0x00)
        u8(7); u8(4); u8(0x86)
        u8(8); u8(4); u8(0x86)
        u8(9); u8(4); u8(0x86)
        u8(0x02); u32(start); u8(2); u8(6); u32(20_000); u32(18_000); u32(22_200)
        val body = data.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(12, 0x10, 0, 0))
        out.write(byteArrayOf(body.size.toByte(), (body.size shr 8).toByte(), (body.size shr 16).toByte(), (body.size shr 24).toByte()))
        out.write(".FIT".toByteArray())
        out.write(body)
        out.write(byteArrayOf(0, 0)) // CRC, not checked
        return out.toByteArray()
    }

    @Test
    fun fit_readsRecordsCompressedTimestampsAndTheSession() {
        val a = parsed(fitFile(), "ride.fit")
        assertEquals(CardioFileFormat.FIT, a.format)
        assertEquals(Instant.parse("2026-11-22T12:00:00Z").epochSecond, a.startEpochSeconds)
        assertEquals(20, a.elapsedSeconds)
        assertEquals(18, a.movingSeconds)
        assertEquals(222.0, a.distanceMeters!!, 0.01)
        assertEquals(2, a.points.size)
        assertEquals(40.001, a.points[1].lat, 0.00001)
        assertEquals(-75.0, a.points[1].lon, 0.00001)
        // 0xFF is "no reading"; the compressed record's 130 is kept.
        assertEquals(listOf(110, 130), a.heartRate.map { it.bpm })
        assertEquals(CardioBuiltinActivity.STATIONARY_BIKE, CardioFileImport.guessActivity(a))
    }

    @Test
    fun findDuplicate_matchesAStartWithinTwoMinutes() {
        val a = parsed(gpx)
        val logged = CardioSession(
            activity = CardioActivitySnapshot(builtin = CardioBuiltinActivity.BIKE, displayLabel = "Bike"),
            durationMinutes = 1,
            startEpochSeconds = a.startEpochSeconds + 90,
        )
        val state = CardioLibraryState(logs = listOf(CardioDayLog("2026-11-20", listOf(logged))))
        assertNotNull(CardioFileImport.findDuplicate(state, a))
        val later = state.copy(logs = listOf(CardioDayLog("2026-11-20", listOf(logged.copy(startEpochSeconds = a.startEpochSeconds + 600)))))
        assertNull(CardioFileImport.findDuplicate(later, a))
    }

    @Test
    fun parse_refusesOtherFiles() {
        assertTrue(CardioFileImport.parse("hello".toByteArray(), "notes.txt") is CardioFileParse.Failed)
    }
}
