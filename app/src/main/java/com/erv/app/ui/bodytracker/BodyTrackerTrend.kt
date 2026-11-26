package com.erv.app.ui.bodytracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.bodytracker.BodyMeasurementKey
import com.erv.app.bodytracker.BodyMeasurementLengthUnit
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.BodyTrendPoint
import com.erv.app.bodytracker.displayLabel
import com.erv.app.bodytracker.measurementTrend
import com.erv.app.bodytracker.trackedMeasurementKeys
import com.erv.app.bodytracker.weightTrend
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.kgToLb
import com.erv.app.ui.components.FormSectionLabelSmall
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

private const val CM_PER_INCH = 2.54

private val trendDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val trendDateWithYearFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private fun trendDateLabel(date: LocalDate): String =
    if (date.year == LocalDate.now().year) date.format(trendDateFormatter) else date.format(trendDateWithYearFormatter)

private enum class BodyTrendRange(val label: String, val months: Long?) {
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("All", null),
}

/** What the chart can show: body weight, or one measurement. */
private sealed class BodyTrendMetric(val label: String) {
    data object Weight : BodyTrendMetric("Weight")
    data class Measurement(val key: BodyMeasurementKey) : BodyTrendMetric(key.displayLabel())
}

private fun formatTrendValue(v: Double): String =
    String.format(Locale.US, "%.1f", v).removeSuffix(".0")

/**
 * Weight and measurement trends, spaced by real dates. Weight first, then the tracked
 * measurements that have at least one value (waist comes before the rest after weight).
 */
@Composable
fun BodyTrackerTrendCard(
    state: BodyTrackerLibraryState,
    weightUnit: BodyWeightUnit,
    lengthUnit: BodyMeasurementLengthUnit,
) {
    val measurementMetrics = remember(state) {
        val tracked = state.trackedMeasurementKeys()
        val withData = BodyMeasurementKey.entries.filter { key ->
            (key in tracked || key == BodyMeasurementKey.WAIST) && state.measurementTrend(key).isNotEmpty()
        }
        withData.sortedBy { if (it == BodyMeasurementKey.WAIST) 0 else 1 }
            .map { BodyTrendMetric.Measurement(it) }
    }
    val metrics: List<BodyTrendMetric> = listOf(BodyTrendMetric.Weight) + measurementMetrics
    var metricLabel by rememberSaveable { mutableStateOf(BodyTrendMetric.Weight.label) }
    val metric = metrics.firstOrNull { it.label == metricLabel } ?: BodyTrendMetric.Weight
    var range by rememberSaveable { mutableStateOf(BodyTrendRange.SIX_MONTHS) }

    val weightSuffix = if (weightUnit == BodyWeightUnit.KG) "kg" else "lb"
    val lengthSuffix = if (lengthUnit == BodyMeasurementLengthUnit.INCHES) "in" else "cm"
    val (allPoints, suffix) = when (metric) {
        BodyTrendMetric.Weight -> state.weightTrend().map {
            it.copy(value = if (weightUnit == BodyWeightUnit.KG) it.value else kgToLb(it.value))
        } to weightSuffix
        is BodyTrendMetric.Measurement -> state.measurementTrend(metric.key).map {
            it.copy(value = if (lengthUnit == BodyMeasurementLengthUnit.INCHES) it.value / CM_PER_INCH else it.value)
        } to lengthSuffix
    }
    val today = LocalDate.now()
    val shown = range.months?.let { m -> allPoints.filter { !it.date.isBefore(today.minusMonths(m)) } } ?: allPoints

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormSectionLabelSmall("Trend")
            if (metrics.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(metrics, key = { it.label }) { m ->
                        FilterChip(
                            selected = m == metric,
                            onClick = { metricLabel = m.label },
                            label = { Text(m.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BodyTrendRange.entries.forEach { r ->
                    FilterChip(
                        selected = r == range,
                        onClick = { range = r },
                        label = { Text(r.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (shown.size < 2) {
                Text(
                    when {
                        allPoints.isEmpty() -> "Log ${metric.label.lowercase()} on two days to see a trend."
                        shown.isEmpty() -> "Nothing logged in this range."
                        else -> "One entry in this range. The trend needs two."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }
            val minV = shown.minOf { it.value }
            val maxV = shown.maxOf { it.value }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.width(48.dp).height(150.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatTrendValue(maxV), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTrendValue(minV), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BodyTrendCanvas(
                    points = shown,
                    lineColor = MaterialTheme.colorScheme.primary,
                    gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    modifier = Modifier.weight(1f).height(150.dp)
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 48.dp)) {
                Text(trendDateLabel(shown.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(trendDateLabel(shown.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
            val first = shown.first()
            val last = shown.last()
            val change = last.value - first.value
            val changeText = when {
                abs(change) < 0.05 -> "no change"
                change > 0 -> "up ${formatTrendValue(change)} $suffix"
                else -> "down ${formatTrendValue(-change)} $suffix"
            }
            Text(
                "Latest ${formatTrendValue(last.value)} $suffix on ${trendDateLabel(last.date)}, $changeText since ${trendDateLabel(first.date)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BodyTrendCanvas(
    points: List<BodyTrendPoint>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val padY = 10f
        val padX = 8f
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val first = points.first().date
        val spanDays = ChronoUnit.DAYS.between(first, points.last().date).coerceAtLeast(1).toFloat()
        val minV = points.minOf { it.value }
        val spanV = (points.maxOf { it.value } - minV).takeIf { it > 0 } ?: 1.0
        fun x(p: BodyTrendPoint) = padX + ChronoUnit.DAYS.between(first, p.date) / spanDays * w
        fun y(p: BodyTrendPoint) = padY + h * (1f - ((p.value - minV) / spanV).toFloat())
        drawLine(gridColor, Offset(padX, padY), Offset(padX + w, padY), 1f)
        drawLine(gridColor, Offset(padX, padY + h), Offset(padX + w, padY + h), 1f)
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p)) else path.lineTo(x(p), y(p)) }
        drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        if (points.size <= 60) {
            points.forEach { p -> drawCircle(lineColor, radius = 5f, center = Offset(x(p), y(p))) }
        }
        drawCircle(lineColor, radius = 9f, center = Offset(x(points.last()), y(points.last())))
    }
}

/** Checklist of every measurement; the picked ones show on the form. */
@Composable
fun BodyTrackerMeasurementPickerDialog(
    initial: List<BodyMeasurementKey>,
    onDismiss: () -> Unit,
    onSave: (List<BodyMeasurementKey>) -> Unit,
) {
    val picked = remember { mutableStateListOf<BodyMeasurementKey>().apply { addAll(initial) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose measurements") },
        text = {
            Column {
                Text(
                    "Picked measurements show on the form. A measurement you already logged on a day still shows for that day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(BodyMeasurementKey.entries, key = { it.name }) { key ->
                        val checked = key in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (checked) picked.remove(key) else picked.add(key) }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on -> if (on) picked.add(key) else picked.remove(key) }
                            )
                            Text(key.displayLabel(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
