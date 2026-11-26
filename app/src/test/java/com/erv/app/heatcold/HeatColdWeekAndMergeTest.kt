package com.erv.app.heatcold

import com.erv.app.data.TemperatureUnit
import com.erv.app.ui.heatcold.ContrastPlan
import com.erv.app.ui.heatcold.totalSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HeatColdWeekAndMergeTest {

    private fun session(id: String, seconds: Int, loggedAt: Long, modified: Long = 0L, contrast: String? = null) =
        HeatColdSession(id = id, durationSeconds = seconds, loggedAtEpochSeconds = loggedAt, lastModifiedEpochSeconds = modified, contrastId = contrast)

    @Test
    fun weekSummary_countsMondayToToday() {
        // 2026-11-26 is a Thursday; the week starts Monday 2026-11-23.
        val state = HeatColdLibraryState(
            saunaLogs = listOf(
                HeatColdDayLog("2026-11-22", listOf(session("old", 900, 1))),
                HeatColdDayLog("2026-11-23", listOf(session("a", 900, 2, contrast = "c1"))),
                HeatColdDayLog("2026-11-25", listOf(session("b", 1200, 3))),
            ),
            coldLogs = listOf(HeatColdDayLog("2026-11-23", listOf(session("c", 120, 4, contrast = "c1"))))
        )
        val week = state.weekSummary(LocalDate.parse("2026-11-26"))
        assertEquals(LocalDate.parse("2026-11-23"), week.weekStart)
        assertEquals(HeatColdModeTotals(2, 2100), week.sauna)
        assertEquals(HeatColdModeTotals(1, 120), week.cold)
        assertEquals(1, week.contrastSessions)
    }

    @Test
    fun merge_deleteMarkerBeatsOlderCopy_newerEditWins() {
        val local = HeatColdDayLog(
            "2026-11-25",
            sessions = listOf(session("keep", 600, 10, modified = 50)),
            deletedSessionIds = mapOf("gone" to 40L)
        )
        val remote = HeatColdDayLog(
            "2026-11-25",
            sessions = listOf(session("keep", 300, 10, modified = 20), session("gone", 300, 10, modified = 30))
        )
        val merged = mergeHeatColdDayLogs(local, remote)
        assertEquals(listOf("keep"), merged.sessions.map { it.id })
        assertEquals(600, merged.sessions.single().durationSeconds)
        assertEquals(40L, merged.deletedSessionIds["gone"])
    }

    @Test
    fun temperature_roundTripsWholeCelsius() {
        for (c in 0..110) {
            val f = convertTemperature(c, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT)
            assertEquals(c, convertTemperature(f, TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS))
        }
    }

    @Test
    fun contrastPlan_totalIncludesSwitches() {
        val plan = ContrastPlan(
            rounds = 3, saunaSeconds = 600, coldSeconds = 120, switchSeconds = 30,
            saunaTemp = 175, coldTemp = 45, unit = TemperatureUnit.FAHRENHEIT
        )
        // 3 sauna + 3 cold + 3 switches to cold + 2 switches back to sauna.
        assertEquals(3 * 600 + 3 * 120 + 5 * 30, plan.totalSeconds())
        assertTrue(plan.copy(switchSeconds = 0).totalSeconds() == 3 * 600 + 3 * 120)
    }
}
