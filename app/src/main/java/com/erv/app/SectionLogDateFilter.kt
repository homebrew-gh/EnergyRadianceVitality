package com.erv.app

import java.time.LocalDate

/** Filter for section log screens. Default is full history (newest first). */
sealed class SectionLogDateFilter {
    data object AllHistory : SectionLogDateFilter()

    data class SingleDay(val day: LocalDate) : SectionLogDateFilter()

    data class DateRange(val startInclusive: LocalDate, val endInclusive: LocalDate) : SectionLogDateFilter() {
        init {
            require(!startInclusive.isAfter(endInclusive)) { "Range start must be on or before end" }
        }
    }
}
