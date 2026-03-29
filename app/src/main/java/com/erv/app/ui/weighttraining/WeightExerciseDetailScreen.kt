package com.erv.app.ui.weighttraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.LocalKeyManager
import com.erv.app.nostr.RelayPool
import com.erv.app.ui.theme.ErvHeaderRed
import com.erv.app.weighttraining.WeightExerciseHistoryRow
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightSync
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.displayLabel
import com.erv.app.weighttraining.formatMuscleGroupHeader
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatSetSummaryLine
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.historyForExercise
import com.erv.app.weighttraining.maxWeightByRepBucketKg
import com.erv.app.weighttraining.kgToPounds
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class ExerciseProgressMetric { EstimatedOneRm, Volume }

private data class ExerciseProgressPoint(
    val date: String,
    val value: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightExerciseDetailScreen(
    exerciseId: String,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    repository: WeightRepository,
    relayPool: RelayPool?,
    signer: EventSigner?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = library.exerciseById(exerciseId)
    val history = remember(exerciseId, library.logs) { library.historyForExercise(exerciseId) }
    val headerMid = ErvHeaderRed
    val loadSuffix = weightLoadUnitSuffix(loadUnit)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by remember(exerciseId) { mutableStateOf(false) }
    var showDeleteConfirm by remember(exerciseId) { mutableStateOf(false) }
    val keyManager = LocalKeyManager.current
    val appContext = LocalContext.current.applicationContext

    suspend fun pushMasters() {
        if (relayPool == null || signer == null) return
        val urls = keyManager.relayUrlsForKind30078Publish()
        val s = repository.currentState()
        WeightSync.publishExercises(appContext, relayPool, signer, s.exercises, urls)
        WeightSync.publishRoutines(appContext, relayPool, signer, s.routines, urls)
    }

    var selectedProgressMetric by remember(exerciseId) {
        mutableStateOf(ExerciseProgressMetric.EstimatedOneRm)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: "Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (exercise != null) {
                        IconButton(onClick = { showEditor = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit exercise")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete exercise")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerMid,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (exercise != null) {
                    Text(
                        "${formatMuscleGroupHeader(exercise.muscleGroup)} · " +
                            "${exercise.equipment.displayLabel()} · ${exercise.pushOrPull.displayLabel()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "This exercise is not in your library anymore. Past sessions still appear below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                val sums = exercise?.sessionSummaries.orEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Summary", style = MaterialTheme.typography.titleSmall)
                        if (sums.isEmpty()) {
                            Text(
                                "No rollups yet — they fill in as you log workouts (used for trends and charts).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val totalVolKg = sums.sumOf { it.volumeKg }
                            val bestRm = sums.mapNotNull { it.bestEstOneRmKg }.maxOrNull()
                            Text(
                                "${sums.size} logged session(s) in history",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (totalVolKg > 0.0) {
                                Text(
                                    buildString {
                                        append("Total volume (reps × weight): ")
                                        append("%.0f".format(totalVolKg))
                                        append(" kg·reps")
                                        if (loadUnit == BodyWeightUnit.LB) {
                                            append(" (~")
                                            append("%.0f".format(kgToPounds(totalVolKg)))
                                            append(" lb·reps)")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (bestRm != null) {
                                Text(
                                    "Best est. 1RM (Epley/Brzycki): " +
                                        formatWeightLoadNumber(bestRm, loadUnit) + " $loadSuffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "Last: ${sums.last().date}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                val sums = exercise?.sessionSummaries.orEmpty()
                val oneRmPoints = remember(sums, loadUnit) {
                    sums.mapNotNull { summary ->
                        val valueKg = summary.bestEstOneRmKg ?: return@mapNotNull null
                        ExerciseProgressPoint(
                            date = summary.date,
                            value = when (loadUnit) {
                                BodyWeightUnit.KG -> valueKg
                                BodyWeightUnit.LB -> kgToPounds(valueKg)
                            }
                        )
                    }
                }
                val volumePoints = remember(sums, loadUnit) {
                    sums.mapNotNull { summary ->
                        if (summary.volumeKg <= 0.0) return@mapNotNull null
                        ExerciseProgressPoint(
                            date = summary.date,
                            value = when (loadUnit) {
                                BodyWeightUnit.KG -> summary.volumeKg
                                BodyWeightUnit.LB -> kgToPounds(summary.volumeKg)
                            }
                        )
                    }
                }
                val availableMetrics = buildList {
                    if (oneRmPoints.size >= 3) add(ExerciseProgressMetric.EstimatedOneRm)
                    if (volumePoints.size >= 3) add(ExerciseProgressMetric.Volume)
                }
                if (availableMetrics.isNotEmpty()) {
                    if (selectedProgressMetric !in availableMetrics) {
                        selectedProgressMetric = availableMetrics.first()
                    }
                    val chartPoints = when (selectedProgressMetric) {
                        ExerciseProgressMetric.EstimatedOneRm -> oneRmPoints
                        ExerciseProgressMetric.Volume -> volumePoints
                    }
                    val metricLabel = when (selectedProgressMetric) {
                        ExerciseProgressMetric.EstimatedOneRm -> "Estimated 1RM"
                        ExerciseProgressMetric.Volume -> "Session volume"
                    }
                    val metricUnit = when (selectedProgressMetric) {
                        ExerciseProgressMetric.EstimatedOneRm -> loadSuffix
                        ExerciseProgressMetric.Volume -> "${loadSuffix}·reps"
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Progress", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Compact trend view from your logged history. Switch metrics when a visual is more useful than scanning raw entries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (ExerciseProgressMetric.EstimatedOneRm in availableMetrics) {
                                    FilterChip(
                                        selected = selectedProgressMetric == ExerciseProgressMetric.EstimatedOneRm,
                                        onClick = { selectedProgressMetric = ExerciseProgressMetric.EstimatedOneRm },
                                        label = { Text("1RM") }
                                    )
                                }
                                if (ExerciseProgressMetric.Volume in availableMetrics) {
                                    FilterChip(
                                        selected = selectedProgressMetric == ExerciseProgressMetric.Volume,
                                        onClick = { selectedProgressMetric = ExerciseProgressMetric.Volume },
                                        label = { Text("Volume") }
                                    )
                                }
                            }
                            ExerciseProgressLineChart(
                                points = chartPoints,
                                lineColor = MaterialTheme.colorScheme.primary,
                                gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "$metricLabel over ${chartPoints.size} logged session(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Latest ${formatProgressMetricValue(chartPoints.last().value)} $metricUnit",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Best ${formatProgressMetricValue(chartPoints.maxOf { it.value })} $metricUnit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    chartDateLabel(chartPoints.first().date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    chartDateLabel(chartPoints.last().date),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            item {
                val repBuckets = remember(exerciseId, library.logs) {
                    library.maxWeightByRepBucketKg(exerciseId)
                }
                val hasAny = repBuckets.any { it.second != null }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Best weight by rep count", style = MaterialTheme.typography.titleSmall)
                        if (!hasAny) {
                            Text(
                                "No sets with weight logged yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            repBuckets.forEach { (label, kg) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (kg != null) {
                                            formatWeightLoadNumber(kg, loadUnit) + " $loadSuffix"
                                        } else {
                                            "—"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "History from your synced logs (newest first).",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "No logged workouts include this exercise yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            } else {
                items(history, key = { "${it.logDate}_${it.workout.id}_${it.entry.exerciseId}" }) { row ->
                    val addedLoad = library.exerciseById(row.entry.exerciseId)?.equipment == WeightEquipment.OTHER
                    ExerciseHistoryCard(
                        row = row,
                        loadUnit = loadUnit,
                        loadSuffix = loadSuffix,
                        weightIsAddedLoad = addedLoad
                    )
                }
            }
        }
    }

    if (showEditor && exercise != null) {
        WeightExerciseEditorDialog(
            initial = exercise,
            title = "Edit exercise",
            onDismiss = { showEditor = false },
            onSave = { draft ->
                scope.launch {
                    repository.upsertExercise(draft)
                    pushMasters()
                    snackbarHostState.showSnackbar("Exercise updated")
                }
                showEditor = false
            }
        )
    }

    if (showDeleteConfirm && exercise != null) {
        val ex = exercise
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete exercise?") },
            text = { Text("Remove “${ex.name}” from your library? Routines that include it may need editing.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteExercise(ex.id)
                            pushMasters()
                            snackbarHostState.showSnackbar("Exercise removed")
                            onBack()
                        }
                        showDeleteConfirm = false
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExerciseHistoryCard(
    row: WeightExerciseHistoryRow,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    weightIsAddedLoad: Boolean
) {
    val dateStr = row.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val src = when (row.workout.source) {
        WeightWorkoutSource.LIVE -> "Live"
        WeightWorkoutSource.MANUAL -> "Manual"
        WeightWorkoutSource.IMPORTED -> "Imported"
    }
    val timeStr = historySessionTimeLabel(row.workout)
    val routine = row.workout.routineName?.takeIf { it.isNotBlank() }
    val vol = row.entry.totalVolumeLoadTimesReps(loadUnit)
    val volPart = if (vol > 0.5) " · ~${vol.toInt()} ${loadSuffix}×reps" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                dateStr,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                buildString {
                    append(src)
                    timeStr?.let { append(" · $it") }
                    routine?.let { append(" · $it") }
                    val hb = row.entry.hiitBlock
                    if (hb != null) append(" · ${hb.intervals} intervals$volPart")
                    else append(" · ${row.entry.sets.size} sets$volPart")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.entry.hiitBlock?.let { b ->
                Text(
                    formatHiitBlockSummaryLine(b, loadUnit, loadSuffix, weightIsAddedLoad),
                    style = MaterialTheme.typography.bodyMedium
                )
            } ?: row.entry.sets.forEachIndexed { idx, set ->
                Text(
                    formatSetSummaryLine(set, idx + 1, loadUnit, loadSuffix, weightIsAddedLoad),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun historySessionTimeLabel(session: WeightWorkoutSession): String? {
    val sec = session.startedAtEpochSeconds ?: session.finishedAtEpochSeconds ?: return null
    val t = LocalTime.ofInstant(Instant.ofEpochSecond(sec), ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}

@Composable
private fun ExerciseProgressLineChart(
    points: List<ExerciseProgressPoint>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    ) {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(8.dp)
        ) {
            val w = size.width
            val h = size.height
            val padL = 4f
            val padR = 8f
            val padT = 8f
            val padB = 12f
            val chartW = (w - padL - padR).coerceAtLeast(1f)
            val chartH = (h - padT - padB).coerceAtLeast(1f)
            val minV = points.minOf { it.value }
            val maxV = points.maxOf { it.value }
            val span = (maxV - minV).coerceAtLeast(1.0)

            for (i in 0..3) {
                val gy = padT + chartH * i / 3f
                drawLine(
                    color = gridColor,
                    start = Offset(padL, gy),
                    end = Offset(padL + chartW, gy),
                    strokeWidth = 1f
                )
            }

            val path = Path()
            points.forEachIndexed { idx, point ->
                val x = if (points.size == 1) {
                    padL + chartW / 2f
                } else {
                    padL + idx.toFloat() / (points.lastIndex.coerceAtLeast(1)) * chartW
                }
                val yNorm = ((point.value - minV) / span).toFloat()
                val y = padT + chartH * (1f - yNorm)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(
                    color = lineColor,
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun chartDateLabel(date: String): String =
    runCatching {
        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M/d"))
    }.getOrDefault(date)

private fun formatProgressMetricValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.1f", value)
