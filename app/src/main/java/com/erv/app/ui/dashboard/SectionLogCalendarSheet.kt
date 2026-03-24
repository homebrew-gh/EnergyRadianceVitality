package com.erv.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.SectionLogDateFilter
import com.erv.app.ui.theme.ErvCalendarActivityDayDark
import com.erv.app.ui.theme.ErvCalendarActivityDayLight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class SectionCalendarMode { OneDay, DateRange }

/**
 * Section log calendar: optional single day, date range, or return to full history.
 * Dashboard continues to use [CalendarPopup].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionLogCalendarSheet(
    filter: SectionLogDateFilter,
    onDismiss: () -> Unit,
    datesWithActivity: Set<LocalDate> = emptySet(),
    onApplyFilter: (SectionLogDateFilter) -> Unit
) {
    var mode by remember(filter) {
        mutableStateOf(
            when (filter) {
                is SectionLogDateFilter.DateRange -> SectionCalendarMode.DateRange
                else -> SectionCalendarMode.OneDay
            }
        )
    }
    var displayedMonth by remember(filter) {
        mutableStateOf(
            when (filter) {
                is SectionLogDateFilter.SingleDay -> YearMonth.from(filter.day)
                is SectionLogDateFilter.DateRange -> YearMonth.from(filter.startInclusive)
                SectionLogDateFilter.AllHistory -> YearMonth.now()
            }
        )
    }
    var rangeStart by remember(filter) {
        mutableStateOf<LocalDate?>(
            when (filter) {
                is SectionLogDateFilter.DateRange -> filter.startInclusive
                else -> null
            }
        )
    }
    var rangeEnd by remember(filter) {
        mutableStateOf<LocalDate?>(
            when (filter) {
                is SectionLogDateFilter.DateRange -> filter.endInclusive
                else -> null
            }
        )
    }

    val darkCalendar = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val activityDayFill = if (darkCalendar) ErvCalendarActivityDayDark else ErvCalendarActivityDayLight

    val singleFocus = when (filter) {
        is SectionLogDateFilter.SingleDay -> filter.day
        else -> LocalDate.now()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Filter log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            TextButton(
                onClick = {
                    onApplyFilter(SectionLogDateFilter.AllHistory)
                    onDismiss()
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text("Show all history")
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == SectionCalendarMode.OneDay,
                    onClick = {
                        mode = SectionCalendarMode.OneDay
                        rangeStart = null
                        rangeEnd = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("One day") }
                SegmentedButton(
                    selected = mode == SectionCalendarMode.DateRange,
                    onClick = {
                        mode = SectionCalendarMode.DateRange
                        rangeStart = null
                        rangeEnd = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Date range") }
            }
            Spacer(Modifier.height(12.dp))
            when (mode) {
                SectionCalendarMode.OneDay -> {
                    Text(
                        "Tap a day to show only that day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SectionCalendarMode.DateRange -> {
                    Text(
                        "Tap the start date, then the end date. Days in between are included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayedMonth.year}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month"
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in DayOfWeek.entries) {
                    Text(
                        text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            SectionLogCalendarDayGrid(
                displayedMonth = displayedMonth,
                mode = mode,
                singleSelected = singleFocus,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                datesWithActivity = datesWithActivity,
                activityDayFill = activityDayFill,
                onDayClick = { date ->
                    when (mode) {
                        SectionCalendarMode.OneDay -> {
                            onApplyFilter(SectionLogDateFilter.SingleDay(date))
                            onDismiss()
                        }
                        SectionCalendarMode.DateRange -> {
                            when {
                                rangeStart == null -> rangeStart = date
                                rangeEnd == null -> {
                                    val a = rangeStart!!
                                    val (from, to) = if (a <= date) a to date else date to a
                                    rangeStart = from
                                    rangeEnd = to
                                }
                                else -> {
                                    rangeStart = date
                                    rangeEnd = null
                                }
                            }
                        }
                    }
                }
            )

            if (mode == SectionCalendarMode.DateRange) {
                Spacer(Modifier.height(16.dp))
                val ready = rangeStart != null && rangeEnd != null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = {
                            rangeStart = null
                            rangeEnd = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Clear") }
                    Button(
                        onClick = {
                            val s = rangeStart!!
                            val e = rangeEnd!!
                            onApplyFilter(SectionLogDateFilter.DateRange(s, e))
                            onDismiss()
                        },
                        enabled = ready,
                        modifier = Modifier.weight(1f)
                    ) { Text("Apply range") }
                }
            }
        }
    }
}

@Composable
private fun SectionLogCalendarDayGrid(
    displayedMonth: YearMonth,
    mode: SectionCalendarMode,
    singleSelected: LocalDate,
    rangeStart: LocalDate?,
    rangeEnd: LocalDate?,
    datesWithActivity: Set<LocalDate>,
    activityDayFill: androidx.compose.ui.graphics.Color,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOfMonth = displayedMonth.atDay(1)
    val startOffset = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = displayedMonth.lengthOfMonth()
    val totalCells = startOffset + daysInMonth
    val rows = (totalCells + 6) / 7

    fun inSelectedRange(d: LocalDate): Boolean {
        if (mode != SectionCalendarMode.DateRange) return false
        val s = rangeStart ?: return false
        val e = rangeEnd ?: return d == s
        return !d.isBefore(s) && !d.isAfter(e)
    }

    for (row in 0 until rows) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                val dayNumber = cellIndex - startOffset + 1
                if (dayNumber in 1..daysInMonth) {
                    val date = displayedMonth.atDay(dayNumber)
                    val isToday = date == LocalDate.now()
                    val hasActivity = date in datesWithActivity
                    val isSinglePick = mode == SectionCalendarMode.OneDay && date == singleSelected
                    val isRangeEndpoint = mode == SectionCalendarMode.DateRange &&
                        (date == rangeStart || (rangeEnd != null && date == rangeEnd))
                    val inRangeFill = mode == SectionCalendarMode.DateRange && inSelectedRange(date)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .then(
                                when {
                                    isSinglePick -> Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    isRangeEndpoint -> Modifier.background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                                    inRangeFill -> Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    )
                                    isToday -> Modifier.background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape
                                    )
                                    hasActivity -> Modifier.background(activityDayFill, CircleShape)
                                    else -> Modifier
                                }
                            )
                            .clickable { onDayClick(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isSinglePick -> MaterialTheme.colorScheme.onPrimary
                                isRangeEndpoint -> MaterialTheme.colorScheme.onPrimary
                                inRangeFill -> MaterialTheme.colorScheme.onPrimaryContainer
                                isToday -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}
