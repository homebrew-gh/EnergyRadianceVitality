package com.erv.app.fasting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FastingScheduleAndMergeTest {

    private val zone = ZoneId.of("America/New_York")

    private fun at(date: String, time: String): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toEpochSecond()

    @Test
    fun sevenPmToTenThirtyIsAFifteenAndAHalfHourFast() {
        val plan = IntermittentFastingPlan().withFastWindow(19 * 60, 10 * 60 + 30)
        assertEquals(19 * 60, plan.fastStartMinutes())
        assertEquals(10 * 60 + 30, plan.fastEndMinutes())
        assertEquals(930, plan.fastingLengthMinutes())
        assertEquals(510, plan.eatingLengthMinutes())
        assertEquals("Custom", plan.protocolLabel)
    }

    @Test
    fun windowTimesRoundToFifteenMinutes() {
        val plan = IntermittentFastingPlan().withFastWindow(19 * 60 + 8, 10 * 60 + 22)
        assertEquals(19 * 60 + 15, plan.fastStartMinutes())
        assertEquals(10 * 60 + 15, plan.fastEndMinutes())
    }

    @Test
    fun presetLengthIsNamedAndKeepsTheEndTime() {
        val plan = IntermittentFastingPlan().withFastWindow(20 * 60, 12 * 60)
        assertEquals("16:8", plan.protocolLabel)
        val longer = plan.withPreset("18:6", 18)
        assertEquals(12 * 60, longer.fastEndMinutes())
        assertEquals(18 * 60, longer.fastStartMinutes())
    }

    @Test
    fun oldHourOnlyPlansStillWork() {
        val old = IntermittentFastingPlan(protocolLabel = "16:8", fastingHours = 16, eatingHours = 8, eatingStartMinutes = 12 * 60)
        assertEquals(480, old.eatingLengthMinutes())
        assertEquals(20 * 60, old.fastStartMinutes())
    }

    @Test
    fun finishedWindowsListEachFastThatEnded() {
        val plan = IntermittentFastingPlan().withFastWindow(19 * 60, 10 * 60 + 30)
        val windows = plan.finishedFastWindows(
            afterEpochSeconds = at("2026-11-24", "12:00"),
            nowEpochSeconds = at("2026-11-26", "11:00"),
            zone = zone,
        )
        assertEquals(2, windows.size)
        assertEquals(at("2026-11-24", "19:00") to at("2026-11-25", "10:30"), windows[0])
        assertEquals(at("2026-11-25", "19:00") to at("2026-11-26", "10:30"), windows[1])
    }

    @Test
    fun aWindowStillRunningIsNotListed() {
        val plan = IntermittentFastingPlan().withFastWindow(19 * 60, 10 * 60 + 30)
        val windows = plan.finishedFastWindows(
            afterEpochSeconds = at("2026-11-25", "12:00"),
            nowEpochSeconds = at("2026-11-26", "09:00"),
            zone = zone,
        )
        assertTrue(windows.isEmpty())
    }

    private fun session(id: String, modified: Long, notes: String = "") = FastingSession(
        id = id,
        targetDays = 1,
        targetHours = 24,
        startedAtEpochSeconds = 1_000L,
        targetEndEpochSeconds = 90_000L,
        status = FastingStatus.COMPLETED,
        endedAtEpochSeconds = 90_000L,
        notes = notes,
        lastModifiedEpochSeconds = modified,
    )

    @Test
    fun mergeKeepsTheNewerEdit() {
        val local = FastingLibraryState(history = listOf(session("a", 10, "local")))
        val remote = FastingLibraryState(history = listOf(session("a", 20, "remote"), session("b", 5)))
        val merged = mergeFastingStates(local, remote, nowEpochSeconds = 100)
        assertEquals(2, merged.history.size)
        assertEquals("remote", merged.history.first { it.id == "a" }.notes)
    }

    @Test
    fun aDeleteMarkerRemovesOlderCopies() {
        val local = FastingLibraryState(deletedSessionIds = mapOf("a" to 50L))
        val remote = FastingLibraryState(history = listOf(session("a", 20)))
        val merged = mergeFastingStates(local, remote, nowEpochSeconds = 100)
        assertTrue(merged.history.isEmpty())
        assertEquals(50L, merged.deletedSessionIds["a"])
    }

    @Test
    fun anEditAfterTheDeleteWins() {
        val local = FastingLibraryState(deletedSessionIds = mapOf("a" to 50L))
        val remote = FastingLibraryState(history = listOf(session("a", 60)))
        val merged = mergeFastingStates(local, remote, nowEpochSeconds = 100)
        assertEquals(1, merged.history.size)
    }

    @Test
    fun anActiveFastFinishedElsewhereIsDropped() {
        val active = session("a", 10).copy(status = FastingStatus.ACTIVE, endedAtEpochSeconds = null)
        val local = FastingLibraryState(activeSession = active)
        val remote = FastingLibraryState(history = listOf(session("a", 20)))
        val merged = mergeFastingStates(local, remote, nowEpochSeconds = 100)
        assertNull(merged.activeSession)
        assertFalse(merged.history.isEmpty())
    }

    @Test
    fun theNewerPlanWins() {
        val local = FastingLibraryState(intermittentPlan = IntermittentFastingPlan(protocolLabel = "16:8", lastModifiedEpochSeconds = 5))
        val remote = FastingLibraryState(intermittentPlan = IntermittentFastingPlan(protocolLabel = "18:6", lastModifiedEpochSeconds = 9))
        assertEquals("18:6", mergeFastingStates(local, remote, nowEpochSeconds = 100).intermittentPlan.protocolLabel)
    }

    @Test
    fun targetHoursFallBackToDays() {
        assertEquals(48, FastingSession(targetDays = 2, startedAtEpochSeconds = 0, targetEndEpochSeconds = 0).targetHoursTotal())
        assertEquals(36, FastingSession(targetDays = 2, targetHours = 36, startedAtEpochSeconds = 0, targetEndEpochSeconds = 0).targetHoursTotal())
        assertEquals("2 days", formatTargetHours(48))
        assertEquals("36 h", formatTargetHours(36))
    }
}
