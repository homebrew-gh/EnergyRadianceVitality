package com.erv.app.cardio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioRecordsTest {

    private val day = LocalDate.of(2026, 11, 20)

    private fun run(id: String, meters: Double?, seconds: Long, activity: CardioBuiltinActivity = CardioBuiltinActivity.RUN, track: CardioGpsTrack? = null) =
        CardioSession(
            id = id,
            activity = CardioActivitySnapshot(builtin = activity, displayLabel = activity.name),
            durationMinutes = (seconds / 60).toInt(),
            distanceMeters = meters,
            startEpochSeconds = 1_000,
            endEpochSeconds = 1_000 + seconds,
            gpsTrack = track,
        )

    /** A straight route north, one point every [stepSeconds] covering [stepMeters]. */
    private fun route(stepMeters: List<Double>, stepSeconds: Long): CardioGpsTrack {
        val metersPerDegree = 111_195.0
        var lat = 40.0
        var t = 1_000L
        val points = mutableListOf(CardioGpsPoint(lat, -75.0, t))
        for (m in stepMeters) {
            lat += m / metersPerDegree
            t += stepSeconds
            points += CardioGpsPoint(lat, -75.0, t)
        }
        return CardioGpsTrack(points)
    }

    @Test
    fun fastest_withoutARoute_usesTheAveragePace() {
        // 10K in 50:00: a 5K at that pace is 25:00; no 21.1K.
        val session = run("a", 10_000.0, 3_000)
        assertEquals(1_500.0, CardioRecords.fastestSeconds(session, 5_000.0)!!, 0.01)
        assertNull(CardioRecords.fastestSeconds(session, 21_097.5))
    }

    @Test
    fun fastest_onARoute_findsTheQuickestStretch() {
        // 100 m every 30 s, then 100 m every 20 s for the last 500 m.
        val steps = List(10) { 100.0 }
        val slow = route(steps, 30)
        val fast = slow.points.toMutableList()
        var t = fast.last().epochSeconds
        var lat = fast.last().lat
        repeat(5) {
            lat += 100.0 / 111_195.0
            t += 20
            fast += CardioGpsPoint(lat, -75.0, t)
        }
        val session = run("a", 1_500.0, t - 1_000, track = CardioGpsTrack(fast))
        // The fastest 499 m is inside the last five steps: about 100 s.
        assertEquals(100.0, CardioRecords.fastestSeconds(session, 499.0)!!, 1.0)
    }

    @Test
    fun best_keepsTheBestOfEachKind() {
        val entries = listOf(
            DatedCardioSession(day, run("a", 5_000.0, 1_600)),
            DatedCardioSession(day.plusDays(1), run("b", 5_000.0, 1_500)),
            DatedCardioSession(day.plusDays(2), run("c", 8_000.0, 3_000)),
        )
        val best = CardioRecords.best(entries).associateBy { it.kind }
        assertEquals("b", best[CardioRecordKind.Fastest("5K", 5_000.0)]?.sessionId)
        assertEquals("c", best[CardioRecordKind.LongestDistance]?.sessionId)
        assertEquals("c", best[CardioRecordKind.LongestTime]?.sessionId)
        assertEquals(CardioRecordGroup.RUN, best[CardioRecordKind.LongestDistance]?.group)
    }

    @Test
    fun newRecords_againstEverythingElse() {
        val old = DatedCardioSession(day, run("a", 5_000.0, 1_600))
        val today = run("b", 10_000.0, 3_100)
        val records = CardioRecords.newRecords(listOf(old, DatedCardioSession(day.plusDays(1), today)), today, day.plusDays(1))
        val titles = records.map { it.record.kind.title }
        // 5K at 25:50 average beats 26:40; first 10K counts; longer and longer.
        assertTrue(titles.containsAll(listOf("Fastest 5K", "Fastest 10K", "Longest Distance", "Longest Time")))
        assertEquals(1_600.0, records.first { it.record.kind.title == "Fastest 5K" }.previous!!.value, 0.01)
        assertNull(records.first { it.record.kind.title == "Fastest 10K" }.previous)
    }

    @Test
    fun newRecords_aFirstEverSessionAnnouncesNothing_andGroupsStaySeparate() {
        val ride = run("r", 30_000.0, 3_600, activity = CardioBuiltinActivity.BIKE)
        assertTrue(CardioRecords.newRecords(listOf(DatedCardioSession(day, ride)), ride, day).isEmpty())
        // A long run doesn't beat a ride's distance.
        val runToday = run("x", 10_000.0, 3_000)
        val records = CardioRecords.newRecords(
            listOf(DatedCardioSession(day, ride), DatedCardioSession(day, run("y", 5_000.0, 1_500)), DatedCardioSession(day, runToday)),
            runToday,
            day,
        )
        assertTrue(records.all { it.record.group == CardioRecordGroup.RUN })
        assertTrue(records.any { it.record.kind == CardioRecordKind.LongestDistance })
    }
}
