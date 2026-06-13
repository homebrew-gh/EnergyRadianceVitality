@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.cardio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioDistanceUnit
import com.erv.app.cardio.CardioLogAggregatedStats
import com.erv.app.cardio.CardioMonthlyWorkoutBucket
import com.erv.app.cardio.DatedCardioSession
import com.erv.app.cardio.computeCardioLogStats
import com.erv.app.cardio.formatCardioDistanceFromMeters
import com.erv.app.cardio.formatCardioTotalDuration
import com.erv.app.cardio.formatCardioZoneSummary
import com.erv.app.hr.HeartRateZoneInputs
import com.erv.app.hr.formatDurationSeconds
import com.erv.app.hr.zoneColor
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

@Composable
fun CardioLogStatsBottomSheet(
    onDismiss: () -> Unit,
    entries: List<DatedCardioSession>,
    distanceUnit: CardioDistanceUnit,
    periodLabel: String,
    zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val stats = remember(entries, zoneInputs) { computeCardioLogStats(entries, zoneInputs) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        CardioLogStatsSheetContent(
            stats = stats,
            distanceUnit = distanceUnit,
            periodLabel = periodLabel,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
        )
    }
}

@Composable
fun CardioLogStatsSheetContent(
    stats: CardioLogAggregatedStats,
    distanceUnit: CardioDistanceUnit,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onVar = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Cardio stats", style = MaterialTheme.typography.headlineSmall)
        Text(periodLabel, style = MaterialTheme.typography.bodyMedium, color = onVar)
        if (stats.workoutCount == 0) {
            Text(
                "No workouts in this period — log a session to see stats here.",
                style = MaterialTheme.typography.bodyLarge,
                color = onVar,
            )
        } else {
        StatRow(
            label = "Workouts logged",
            value = stats.workoutCount.toString(),
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        StatRow(
            label = "Active days",
            value = stats.activeDays.toString(),
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        StatRow(
            label = "Total time",
            value = formatCardioTotalDuration(stats.totalDurationMinutes),
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        StatRow(
            label = "Distance logged",
            value = if (stats.workoutsWithDistance > 0) {
                formatCardioDistanceFromMeters(stats.totalDistanceMeters, distanceUnit) +
                    " (${stats.workoutsWithDistance} with distance)"
            } else {
                "— (no distance on entries)"
            },
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        StatRow(
            label = "Activity types (all legs)",
            value = stats.distinctActivityTypeCount.toString(),
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        stats.estimatedKcalTotal?.let { k ->
            StatRow(
                label = "Est. energy (logged)",
                value = "~${k.toInt()} kcal",
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (stats.workoutsWithHeartRate > 0) {
            Text("Heart rate (this period)", style = MaterialTheme.typography.titleSmall, color = onVar)
            StatRow(
                label = "Workouts with HR",
                value = stats.workoutsWithHeartRate.toString(),
                valueColor = MaterialTheme.colorScheme.onSurface,
            )
            stats.avgHeartRateBpm?.let { avg ->
                StatRow(
                    label = "Average HR",
                    value = "$avg bpm",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            formatCardioZoneSummary(stats.heartRateZoneSeconds)?.let { summary ->
                StatRow(
                    label = "Dominant zone",
                    value = summary,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                )
            }
            val totalZone = stats.heartRateZoneSeconds.sum().coerceAtLeast(1)
            for (z in 1..5) {
                val sec = stats.heartRateZoneSeconds[z - 1]
                if (sec <= 0) continue
                StatRow(
                    label = "Z$z",
                    value = formatDurationSeconds(sec),
                    valueColor = zoneColor(z),
                )
            }
        }
        if (stats.primaryActivityCounts.isNotEmpty()) {
            Text("Most common workouts", style = MaterialTheme.typography.titleSmall, color = onVar)
            stats.primaryActivityCounts.forEach { (label, count) ->
                StatRow(
                    label = label,
                    value = count.toString(),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        val chartBuckets = stats.monthlyWorkouts.takeLast(12)
        if (chartBuckets.isNotEmpty()) {
            Text("Workouts by month", style = MaterialTheme.typography.titleSmall, color = onVar)
            Text(
                if (stats.monthlyWorkouts.size > chartBuckets.size) {
                    "Showing last ${chartBuckets.size} months"
                } else {
                    "Each bar is how many workouts you logged that calendar month."
                },
                style = MaterialTheme.typography.bodySmall,
                color = onVar,
            )
            CardioMonthlyWorkoutBarChart(
                buckets = chartBuckets,
                barColor = primary,
                gridColor = grid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
        }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

private val monthChartLabelFormatter =
    DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

@Composable
private fun CardioMonthlyWorkoutBarChart(
    buckets: List<CardioMonthlyWorkoutBucket>,
    barColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val maxCount = buckets.maxOf { it.workoutCount }.coerceAtLeast(1)
    val stepped = (ceil(maxCount / 5.0) * 5.0).toInt()
    val niceMax = maxOf(maxCount, stepped)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val padB = 28f
                val padT = 8f
                val chartH = (h - padT - padB).coerceAtLeast(1f)
                val n = buckets.size.coerceAtLeast(1)
                val slotW = w / n
                val barW = (slotW * 0.55f).coerceAtLeast(4f)

                for (i in 0..4) {
                    val gy = padT + chartH * i / 4f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1f,
                    )
                }

                buckets.forEachIndexed { idx, bucket ->
                    val cx = slotW * idx + slotW / 2f
                    val x0 = cx - barW / 2f
                    val frac = bucket.workoutCount.toFloat() / niceMax.toFloat()
                    val barH: Float = chartH * frac.coerceIn(0f, 1f)
                    val y0: Float = padT + chartH - barH
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x0, y0),
                        size = Size(barW, barH.coerceAtLeast(2f)),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                buckets.forEach { b ->
                    Text(
                        b.yearMonth.format(monthChartLabelFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
