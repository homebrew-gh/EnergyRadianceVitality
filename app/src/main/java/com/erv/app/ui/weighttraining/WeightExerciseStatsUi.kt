package com.erv.app.ui.weighttraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.ui.components.FormSectionLabelSmall
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRecord
import com.erv.app.weighttraining.WeightRecords
import com.erv.app.weighttraining.WeightTrendMetric
import com.erv.app.weighttraining.WeightTrendPoint
import com.erv.app.weighttraining.allDatedWorkouts
import com.erv.app.weighttraining.compactSetsLine
import com.erv.app.weighttraining.estimatedOneRmKg
import com.erv.app.weighttraining.formatHoldClock
import com.erv.app.weighttraining.formatValue
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.weighttraining.isWorkingSet
import com.erv.app.weighttraining.kgToPounds
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "Mon, Nov 24", with the year when it isn't this year. */
internal fun sessionDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val pattern = if (date.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/** "Nov 24", or "Nov 24, 2025" for another year. */
internal fun shortDateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/** The routine or workout the session came from, for the line under its date. */
private fun WeightExerciseHistoryRow.sourceLabel(): String? =
    workout.routineName?.takeIf { it.isNotBlank() }

/** Best est. 1RM among the row's working sets, in kg. */
private fun WeightExerciseHistoryRow.topEstOneRmKg(): Double? =
    entry.sets.filter { it.isWorkingSet() }.mapNotNull { s -> s.weightKg?.let { estimatedOneRmKg(it, s.reps) } }.maxOrNull()

@Composable
private fun StatsCard(highlight: Boolean = false, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** What was done last session: date, where it came from, the working sets and the top set's 1RM. */
@Composable
fun ExerciseLastTimeCard(row: WeightExerciseHistoryRow?, loadUnit: BodyWeightUnit) {
    StatsCard {
        FormSectionLabelSmall("Last Time")
        if (row == null) {
            Text(
                "Not logged yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatsCard
        }
        Text(
            listOfNotNull(sessionDateLabel(row.logDate), row.sourceLabel()).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            row.entry.compactSetsLine(loadUnit, warmups = false) ?: "Warm-ups only",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        row.topEstOneRmKg()?.let { kg ->
            Text(
                "Top set est. 1RM ${WeightTrendMetric.EST_ONE_RM.format(kg, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The records as two-across tiles, each with its date. Tapping one calls [onRecordClick]. */
@Composable
fun ExerciseRecordsCard(
    records: List<WeightRecord>,
    loadUnit: BodyWeightUnit,
    onRecordClick: ((WeightRecord) -> Unit)? = null,
) {
    StatsCard(highlight = records.isNotEmpty()) {
        FormSectionLabelSmall("Personal Records")
        if (records.isEmpty()) {
            Text(
                "Records show up after your first logged working set.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatsCard
        }
        records.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { record ->
                    RecordTile(record, loadUnit, onRecordClick, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RecordTile(
    record: WeightRecord,
    loadUnit: BodyWeightUnit,
    onClick: ((WeightRecord) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), shape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Show this session") { onClick(record) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                record.kind.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                shortDateLabel(record.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            record.formatValue(loadUnit),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private enum class TrendRange(val label: String, val months: Long?) {
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("All", null),
}

private fun WeightTrendMetric.format(value: Double, loadUnit: BodyWeightUnit): String = when (this) {
    WeightTrendMetric.EST_ONE_RM -> {
        val v = when (loadUnit) {
            BodyWeightUnit.KG -> value
            BodyWeightUnit.LB -> kgToPounds(value)
        }
        "%.0f".format(Locale.US, v)
    }
    WeightTrendMetric.MOST_REPS -> value.toInt().toString()
    WeightTrendMetric.LONGEST_HOLD -> formatHoldClock(value.toInt())
}

/**
 * The trend spaced by real dates, so a three-month gap looks like one. Values at the top and
 * bottom lines, the range's first and last dates under it, the best point marked.
 */
@Composable
fun ExerciseTrendCard(metric: WeightTrendMetric, points: List<WeightTrendPoint>, loadUnit: BodyWeightUnit) {
    var range by rememberSaveable { mutableStateOf(TrendRange.ONE_YEAR) }
    val today = LocalDate.now()
    val shown = range.months?.let { m -> points.filter { !it.date.isBefore(today.minusMonths(m)) } } ?: points
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FormSectionLabelSmall(metric.title, modifier = Modifier.weight(1f))
            TrendRange.entries.forEach { r ->
                FilterChip(
                    selected = r == range,
                    onClick = { range = r },
                    label = { Text(r.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (shown.size < 2) {
            Text(
                if (shown.isEmpty()) "Nothing logged in this range." else "One session in this range. The trend needs two.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StatsCard
        }
        val minV = shown.minOf { it.value }
        val maxV = shown.maxOf { it.value }
        val best = shown.maxByOrNull { it.value }
        val unit = if (metric == WeightTrendMetric.EST_ONE_RM) " ${weightLoadUnitSuffix(loadUnit)}" else ""
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(44.dp).height(160.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(metric.format(maxV, loadUnit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(metric.format(minV, loadUnit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TrendCanvas(
                points = shown,
                best = best,
                lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                accent = MaterialTheme.colorScheme.primary,
                gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.weight(1f).height(160.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = 44.dp)) {
            Text(shortDateLabel(shown.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(shortDateLabel(shown.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
        best?.let {
            Text(
                "Best ${metric.format(it.value, loadUnit)}$unit on ${shortDateLabel(it.date)} · latest ${metric.format(shown.last().value, loadUnit)}$unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrendCanvas(
    points: List<WeightTrendPoint>,
    best: WeightTrendPoint?,
    lineColor: Color,
    accent: Color,
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
        fun x(p: WeightTrendPoint) = padX + ChronoUnit.DAYS.between(first, p.date) / spanDays * w
        fun y(p: WeightTrendPoint) = padY + h * (1f - ((p.value - minV) / spanV).toFloat())
        drawLine(gridColor, Offset(padX, padY), Offset(padX + w, padY), 1f)
        drawLine(gridColor, Offset(padX, padY + h), Offset(padX + w, padY + h), 1f)
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p), y(p)) else path.lineTo(x(p), y(p)) }
        drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        points.forEach { p -> drawCircle(lineColor, radius = 5f, center = Offset(x(p), y(p))) }
        best?.let { drawCircle(accent, radius = 10f, center = Offset(x(it), y(it))) }
    }
}

/** One session: date, where it came from, a PR tag when a record was set, working sets, warm-ups dimmed. */
@Composable
fun ExerciseSessionCard(
    row: WeightExerciseHistoryRow,
    loadUnit: BodyWeightUnit,
    isRecordSession: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sessionDateLabel(row.logDate),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (isRecordSession) RecordBadge()
            }
            row.sourceLabel()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            row.entry.compactSetsLine(loadUnit, warmups = false)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            row.entry.compactSetsLine(loadUnit, warmups = true)?.let {
                Text(
                    "Warm-up: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun RecordBadge() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("PR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The exercise's stats in a sheet over the workout session, so the rest bar and the place in the
 * workout stay put: last time, records and the last three sessions, with a way to the full page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightExerciseStatsSheet(
    exerciseId: String,
    exerciseName: String,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    onDismiss: () -> Unit,
    onOpenFullPage: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val history = remember(exerciseId, library.logs) { library.historyForExercise(exerciseId) }
    val records = remember(exerciseId, library.logs) { WeightRecords.best(library.allDatedWorkouts(), exerciseId) }
    val recordWorkoutIds = remember(records) { records.map { it.workoutId }.toSet() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(exerciseName, style = MaterialTheme.typography.titleLarge)
            ExerciseLastTimeCard(history.firstOrNull(), loadUnit)
            ExerciseRecordsCard(records, loadUnit)
            if (history.size > 1) {
                FormSectionLabelSmall("Recent Sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                history.drop(1).take(3).forEach { row ->
                    ExerciseSessionCard(row, loadUnit, isRecordSession = row.workout.id in recordWorkoutIds)
                }
            }
            if (onOpenFullPage != null) {
                FilledTonalButton(onClick = onOpenFullPage, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Full Page")
                }
            }
        }
    }
}
