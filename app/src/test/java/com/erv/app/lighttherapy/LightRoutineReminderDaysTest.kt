package com.erv.app.lighttherapy

import com.erv.app.lighttherapy.LightRoutine
import com.erv.app.lighttherapy.reminderDraftFollowingRepeatDays
import com.erv.app.reminders.RoutineReminderDraft
import com.erv.app.reminders.RoutineReminderFrequency
import com.erv.app.reminders.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightRoutineReminderDaysTest {

    private val draft = RoutineReminderDraft(enabled = true, hour = 7, minute = 30, isPm = false)

    @Test
    fun noRepeatDaysMeansDailyReminder() {
        val out = LightRoutine(name = "Morning red").reminderDraftFollowingRepeatDays(draft)
        assertEquals(RoutineReminderFrequency.DAILY, out.frequency)
        assertTrue(out.enabled)
        assertEquals(7, out.hour)
        assertEquals(30, out.minute)
    }

    @Test
    fun allSevenDaysMeansDailyReminder() {
        val out = LightRoutine(name = "x", repeatDays = Weekday.entries).reminderDraftFollowingRepeatDays(draft)
        assertEquals(RoutineReminderFrequency.DAILY, out.frequency)
    }

    @Test
    fun someDaysBecomeTheReminderDays() {
        val days = listOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY)
        val out = LightRoutine(name = "x", repeatDays = days).reminderDraftFollowingRepeatDays(
            draft.copy(frequency = RoutineReminderFrequency.WEEKLY, repeatDays = setOf(Weekday.SUNDAY))
        )
        assertEquals(RoutineReminderFrequency.CUSTOM_DAYS, out.frequency)
        assertEquals(days.toSet(), out.repeatDays)
    }
}
