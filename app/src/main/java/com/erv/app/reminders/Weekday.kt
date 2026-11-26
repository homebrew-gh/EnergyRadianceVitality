package com.erv.app.reminders

import kotlinx.serialization.Serializable

/**
 * Day of the week for routine reminders (supplements and light therapy).
 * Stored by name in reminder JSON; formerly named `SupplementWeekday`.
 */
@Serializable
enum class Weekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

fun Weekday.shortLabel(): String = when (this) {
    Weekday.MONDAY -> "Mon"
    Weekday.TUESDAY -> "Tue"
    Weekday.WEDNESDAY -> "Wed"
    Weekday.THURSDAY -> "Thu"
    Weekday.FRIDAY -> "Fri"
    Weekday.SATURDAY -> "Sat"
    Weekday.SUNDAY -> "Sun"
}
