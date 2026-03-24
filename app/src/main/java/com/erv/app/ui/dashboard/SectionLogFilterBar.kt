package com.erv.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.SectionLogDateFilter
import java.time.format.DateTimeFormatter
import java.util.Locale

private val singleDaySummaryFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private val rangePartSameYear = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val rangePartWithYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

fun sectionLogFilterSummary(filter: SectionLogDateFilter): String =
    when (filter) {
        SectionLogDateFilter.AllHistory -> "All activity"
        is SectionLogDateFilter.SingleDay -> filter.day.format(singleDaySummaryFormatter)
        is SectionLogDateFilter.DateRange -> {
            val y0 = filter.startInclusive.year
            val y1 = filter.endInclusive.year
            val startFmt =
                if (y0 == y1) filter.startInclusive.format(rangePartSameYear)
                else filter.startInclusive.format(rangePartWithYear)
            val endFmt = filter.endInclusive.format(rangePartWithYear)
            "$startFmt – $endFmt"
        }
    }

@Composable
fun SectionLogFilterBar(
    filter: SectionLogDateFilter,
    onOpenCalendar: () -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = sectionLogFilterSummary(filter),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        if (filter != SectionLogDateFilter.AllHistory) {
            TextButton(onClick = onClearFilter) {
                Text("Show all")
            }
        }
        IconButton(onClick = onOpenCalendar) {
            Icon(Icons.Default.DateRange, contentDescription = "Filter by date")
        }
    }
    HorizontalDivider()
}
