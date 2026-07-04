package com.erv.app.reminders

import com.erv.app.supplements.SupplementWeekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RoutineReminderSchedulerTest {

    @Test
    fun nextTrigger_daily_usesLaterTodayWhenTimeNotPassed() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 7, 3, 7, 0).atZone(zone).toInstant().toEpochMilli()
        val reminder = RoutineReminder(
            routineId = "r1",
            routineName = "Morning",
            enabled = true,
            hour = 8,
            minute = 30,
            frequency = RoutineReminderFrequency.DAILY,
        )
        val trigger = RoutineReminderScheduler.nextTriggerMillisForTest(reminder, now)
        assertNotNull(trigger)
        val expected = LocalDateTime.of(2026, 7, 3, 8, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun nextTrigger_daily_rollsToTomorrowWhenTimePassed() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 7, 3, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val reminder = RoutineReminder(
            routineId = "r1",
            routineName = "Morning",
            enabled = true,
            hour = 8,
            minute = 30,
            frequency = RoutineReminderFrequency.DAILY,
        )
        val trigger = RoutineReminderScheduler.nextTriggerMillisForTest(reminder, now)!!
        val expected = LocalDateTime.of(2026, 7, 4, 8, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun nextTrigger_customDays_skipsUnselectedWeekdays() {
        val zone = ZoneId.systemDefault()
        // Friday 2026-07-03 at 10:00; only Monday selected -> next is Monday 2026-07-06 08:00
        val now = LocalDateTime.of(2026, 7, 3, 10, 0).atZone(zone).toInstant().toEpochMilli()
        val reminder = RoutineReminder(
            routineId = "r1",
            routineName = "Monday stack",
            enabled = true,
            hour = 8,
            minute = 0,
            frequency = RoutineReminderFrequency.CUSTOM_DAYS,
            repeatDays = listOf(SupplementWeekday.MONDAY),
        )
        val trigger = RoutineReminderScheduler.nextTriggerMillisForTest(reminder, now)!!
        val expected = LocalDate.of(2026, 7, 6).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun nextTrigger_once_behavesLikeSingleFutureOccurrence() {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.of(2026, 7, 3, 7, 0).atZone(zone).toInstant().toEpochMilli()
        val reminder = RoutineReminder(
            routineId = "r1",
            routineName = "One-time",
            enabled = true,
            hour = 12,
            minute = 0,
            frequency = RoutineReminderFrequency.ONCE,
        )
        val trigger = RoutineReminderScheduler.nextTriggerMillisForTest(reminder, now)!!
        assertTrue(trigger > now)
    }
}
