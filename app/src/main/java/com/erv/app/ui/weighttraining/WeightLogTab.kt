package com.erv.app.ui.weighttraining

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.erv.app.data.BodyWeightUnit
import com.erv.app.weighttraining.DatedWeightWorkout
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import com.erv.app.workouts.WorkoutLibraryState
import com.erv.app.workouts.displayTitle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A row in the weight log: either a single standalone session card, or an expandable block that
 * groups the multiple section sessions of one composed Training-section run (shared
 * [com.erv.app.workouts.WorkoutSessionLink.sessionId]).
 */
private sealed interface WeightLogRow {
    val sortEpoch: Long

    data class Single(val dated: DatedWeightWorkout) : WeightLogRow {
        override val sortEpoch: Long
            get() = dated.workout.startedAtEpochSeconds ?: dated.workout.finishedAtEpochSeconds ?: 0L
    }

    data class Block(
        val logDate: LocalDate,
        val sessionId: String,
        /** Sections in performed order (oldest first). */
        val sections: List<DatedWeightWorkout>,
    ) : WeightLogRow {
        override val sortEpoch: Long
            get() = sections.maxOf {
                it.workout.startedAtEpochSeconds ?: it.workout.finishedAtEpochSeconds ?: 0L
            }
    }
}

/**
 * Group composed sessions sharing a `(logDate, workoutLink.sessionId)` into expandable blocks.
 * Sessions without a link — and composed runs that only produced a single section — stay as
 * standalone cards so they look exactly as before. Newest-first, matching the incoming order.
 */
private fun buildWeightLogRows(datedWorkouts: List<DatedWeightWorkout>): List<WeightLogRow> {
    val grouped = LinkedHashMap<String, MutableList<DatedWeightWorkout>>()
    datedWorkouts.forEach { dated ->
        val link = dated.workout.workoutLink
        val key = if (link != null) {
            "L|${dated.logDate}|${link.sessionId}"
        } else {
            "S|${dated.logDate}|${dated.workout.id}"
        }
        grouped.getOrPut(key) { mutableListOf() }.add(dated)
    }
    return grouped.values
        .map { sessions ->
            val link = sessions.first().workout.workoutLink
            if (sessions.size >= 2 && link != null) {
                WeightLogRow.Block(
                    logDate = sessions.first().logDate,
                    sessionId = link.sessionId,
                    sections = sessions.sortedBy {
                        it.workout.startedAtEpochSeconds ?: it.workout.finishedAtEpochSeconds ?: 0L
                    },
                )
            } else {
                WeightLogRow.Single(sessions.first())
            }
        }
        .sortedByDescending { it.sortEpoch }
}

@Composable
fun WeightLogTabContent(
    datedWorkouts: List<DatedWeightWorkout>,
    showLogDateOnCards: Boolean,
    emptyRangeLabel: String,
    library: WeightLibraryState,
    workoutLibrary: WorkoutLibraryState,
    loadUnit: BodyWeightUnit,
    onOpen: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onEdit: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onDelete: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onShare: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val sfx = weightLoadUnitSuffix(loadUnit)

    if (datedWorkouts.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                emptyRangeLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(8.dp))
            Text(
                "Tap Add workout to log training for today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val rows = buildWeightLogRows(datedWorkouts)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                "Newest first.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
        items(
            rows,
            key = { row ->
                when (row) {
                    is WeightLogRow.Single -> "single-${row.dated.logDate}-${row.dated.workout.id}"
                    is WeightLogRow.Block -> "block-${row.logDate}-${row.sessionId}"
                }
            }
        ) { row ->
            when (row) {
                is WeightLogRow.Single -> WeightSessionCard(
                    dated = row.dated,
                    showLogDate = showLogDateOnCards,
                    library = library,
                    loadUnit = loadUnit,
                    sfx = sfx,
                    onOpen = onOpen,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onShare = onShare,
                )

                is WeightLogRow.Block -> WeightWorkoutBlockCard(
                    block = row,
                    workoutLibrary = workoutLibrary,
                    library = library,
                    loadUnit = loadUnit,
                    sfx = sfx,
                    onOpen = onOpen,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onShare = onShare,
                )
            }
        }
    }
}

/** Expandable header for one composed run plus its per-section sub-cards. */
@Composable
private fun WeightWorkoutBlockCard(
    block: WeightLogRow.Block,
    workoutLibrary: WorkoutLibraryState,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    sfx: String,
    onOpen: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onEdit: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onDelete: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onShare: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
) {
    var expanded by rememberSaveable(block.logDate, block.sessionId) { mutableStateOf(false) }
    val link = block.sections.first().workout.workoutLink
    val workout = link?.workoutId?.let { workoutLibrary.workoutById(it) }
    val title = workout?.name ?: link?.displayRef ?: "Workout"
    val sectionCount = block.sections.size

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            block.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "$sectionCount sections",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse workout" else "Expand workout"
                )
            }
        }
        if (expanded) {
            block.sections.forEach { section ->
                val segmentId = section.workout.workoutLink?.segmentId
                val sectionLabel = workout?.segments
                    ?.firstOrNull { it.id == segmentId }
                    ?.displayTitle()
                WeightSessionCard(
                    dated = section,
                    showLogDate = false,
                    library = library,
                    loadUnit = loadUnit,
                    sfx = sfx,
                    sectionLabel = sectionLabel,
                    modifier = Modifier.padding(start = 12.dp),
                    onOpen = onOpen,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onShare = onShare,
                )
            }
        }
    }
}

/** The standalone session card; also reused for each section sub-row inside a block. */
@Composable
private fun WeightSessionCard(
    dated: DatedWeightWorkout,
    showLogDate: Boolean,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    sfx: String,
    modifier: Modifier = Modifier,
    /** Optional segment title shown for composed-run sub-rows (e.g. "Superset", "Warm-up"). */
    sectionLabel: String? = null,
    onOpen: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onEdit: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onDelete: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
    onShare: (logDate: LocalDate, session: WeightWorkoutSession) -> Unit,
) {
    val session = dated.workout
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(dated.logDate, session) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    sectionLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (showLogDate) {
                        Text(
                            dated.logDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            when (session.source) {
                                WeightWorkoutSource.LIVE -> "Live"
                                WeightWorkoutSource.MANUAL -> "Manual"
                                WeightWorkoutSource.IMPORTED -> "Imported"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        sessionTimeLabel(session)?.let { t ->
                            Text(
                                t,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val vol = session.totalVolumeLoadTimesReps(loadUnit)
                    Text(
                        buildString {
                            append("${session.totalSetCount()} sets")
                            if (vol > 0.5) append(" · ~${vol.toInt()} ${sfx}×reps")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        sessionExerciseSummary(session, library),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Row {
                    IconButton(onClick = { onShare(dated.logDate, session) }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { onEdit(dated.logDate, session) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { onDelete(dated.logDate, session) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

private fun sessionTimeLabel(session: WeightWorkoutSession): String? {
    val sec = session.startedAtEpochSeconds ?: session.finishedAtEpochSeconds ?: return null
    val t = LocalTime.ofInstant(Instant.ofEpochSecond(sec), ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
}

private fun sessionExerciseSummary(session: WeightWorkoutSession, library: WeightLibraryState): String =
    session.entries.take(4).joinToString(" · ") { e ->
        val name = library.exerciseById(e.exerciseId)?.name ?: e.exerciseId
        val count = e.hiitBlock?.intervals ?: e.sets.size
        "$name ($count)"
    } + if (session.entries.size > 4) "…" else ""
