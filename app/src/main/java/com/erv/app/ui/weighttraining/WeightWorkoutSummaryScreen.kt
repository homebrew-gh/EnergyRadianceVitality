@file:OptIn(ExperimentalMaterial3Api::class)

package com.erv.app.ui.weighttraining

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.UserPreferences
import com.erv.app.hr.HeartRateSessionAnalyticsSection
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.UnsignedEvent
import com.erv.app.weighttraining.WeightEquipment
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightRepository
import com.erv.app.weighttraining.WeightRoutine
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import com.erv.app.weighttraining.totalSetCount
import com.erv.app.weighttraining.formatHiitBlockSummaryLine
import com.erv.app.weighttraining.formatWeightLoadNumber
import com.erv.app.weighttraining.totalVolumeLoadTimesReps
import com.erv.app.weighttraining.weightLoadUnitSuffix
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun WeightWorkoutSession.displayElapsedSeconds(): Long {
    val a = startedAtEpochSeconds ?: return 0L
    val b = finishedAtEpochSeconds ?: return 0L
    return (b - a).coerceAtLeast(0L)
}

private fun dedupeConsecutiveExerciseIds(ids: List<String>): List<String> =
    ids.fold(emptyList()) { acc, id -> if (acc.lastOrNull() == id) acc else acc + id }

internal fun buildWeightWorkoutNoteContent(
    session: WeightWorkoutSession,
    library: WeightLibraryState,
    logDate: LocalDate,
    displayUnit: BodyWeightUnit
): String = buildString {
    val unitSfx = weightLoadUnitSuffix(displayUnit)
    val headline = when (session.source) {
        WeightWorkoutSource.LIVE -> "\uD83C\uDFCB\uFE0F Weight workout (live)"
        WeightWorkoutSource.MANUAL -> "\uD83C\uDFCB\uFE0F Weight workout"
        WeightWorkoutSource.IMPORTED -> "\uD83C\uDFCB\uFE0F Weight workout (imported)"
    }
    append("$headline\n")
    append("Date: ${logDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}\n")
    val dur = session.displayElapsedSeconds()
    if (dur > 0) {
        val m = (dur / 60).toInt()
        val s = (dur % 60).toInt()
        append("Duration: %d:%02d\n".format(m, s))
    }
    val sets = session.totalSetCount()
    if (sets > 0) append("Sets: $sets\n")
    session.heartRate?.avgBpm?.let { avg ->
        append("Heart rate (avg): $avg bpm\n")
    }
    val vol = session.totalVolumeLoadTimesReps(displayUnit)
    if (vol > 0.5) append("Volume (${unitSfx}×reps): ~${vol.toInt()}\n")
    append("Exercises:\n")
    session.entries.forEach { e ->
        val name = library.exerciseById(e.exerciseId)?.name ?: e.exerciseId
        val addedLoad = library.exerciseById(e.exerciseId)?.equipment == WeightEquipment.OTHER
        val detailLine = e.hiitBlock?.let { b ->
            formatHiitBlockSummaryLine(b, displayUnit, unitSfx, addedLoad)
        } ?: e.sets.joinToString(", ") { st ->
            buildString {
                append("${st.reps} reps")
                st.weightKg?.let { w ->
                    val num = formatWeightLoadNumber(w, displayUnit)
                    if (addedLoad) append(" @ +$num$unitSfx")
                    else append(" @ $num$unitSfx")
                }
            }
        }
        append("• $name: $detailLine\n")
    }
    append("\n#erv #workout #fitness")
}

internal suspend fun publishWeightWorkoutNote(
    relayPool: RelayPool,
    signer: EventSigner,
    session: WeightWorkoutSession,
    library: WeightLibraryState,
    logDate: LocalDate,
    displayUnit: BodyWeightUnit
): Boolean {
    val content = buildWeightWorkoutNoteContent(session, library, logDate, displayUnit)
    val unsigned = UnsignedEvent(
        pubkey = signer.publicKey,
        createdAt = System.currentTimeMillis() / 1000,
        kind = 1,
        tags = listOf(
            listOf("t", "erv"),
            listOf("t", "workout"),
            listOf("t", "fitness")
        ),
        content = content
    )
    val signed = signer.sign(unsigned)
    return relayPool.publish(signed)
}

@Composable
fun WeightWorkoutSummaryFullScreen(
    session: WeightWorkoutSession,
    logDate: LocalDate,
    library: WeightLibraryState,
    loadUnit: BodyWeightUnit,
    userPreferences: UserPreferences,
    dark: Color,
    mid: Color,
    glow: Color,
    relayPool: RelayPool?,
    signer: EventSigner?,
    repository: WeightRepository,
    onAfterRoutineSync: () -> Unit,
    /** Remove this session from [logDate] and close (e.g. user regrets saving). */
    onRemoveFromLog: (() -> Unit)? = null,
    onDone: () -> Unit
) {
    val maxHrPref by userPreferences.heartRateMaxBpm.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var sharing by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var showRoutineConfirm by remember { mutableStateOf(false) }
    var routineUpdating by remember { mutableStateOf(false) }
    var showDiscardLogConfirm by remember { mutableStateOf(false) }

    val routineToUpdate: WeightRoutine? = remember(session.routineId, library.routines) {
        session.routineId?.let { rid -> library.routines.firstOrNull { it.id == rid } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(dark, mid, glow)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Workout logged",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                text = logDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
            session.routineName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    "From routine: $name",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            val dur = session.displayElapsedSeconds()
            if (dur > 0) {
                val m = (dur / 60).toInt()
                val s = (dur % 60).toInt()
                Text(
                    "Elapsed: %d:%02d".format(m, s),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            session.heartRate?.let { hr ->
                val corr = session.heartRateExerciseSegments.map { seg ->
                    val title = library.exerciseById(seg.exerciseId)?.name ?: seg.exerciseId
                    val detail = buildList {
                        seg.avgBpm?.let { add("avg $it") }
                        seg.maxBpm?.let { add("max $it") }
                        add("${seg.sampleCount} readings")
                    }.joinToString(" · ")
                    title to detail
                }
                HeartRateSessionAnalyticsSection(
                    heartRate = hr,
                    userMaxHrBpm = maxHrPref,
                    useLightOnDarkBackground = true,
                    exerciseCorrelationLines = corr.takeIf { it.isNotEmpty() }
                )
            }
            val vol = session.totalVolumeLoadTimesReps(loadUnit)
            val sfx = weightLoadUnitSuffix(loadUnit)
            Text(
                if (vol > 0.5) {
                    "${session.totalSetCount()} sets • Volume ~${vol.toInt()} ${sfx}×reps"
                } else {
                    "${session.totalSetCount()} sets"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = Color.White.copy(alpha = 0.35f)
            )
            session.entries.forEach { e ->
                val name = library.exerciseById(e.exerciseId)?.name ?: e.exerciseId
                val addedLoad = library.exerciseById(e.exerciseId)?.equipment == WeightEquipment.OTHER
                val line = e.hiitBlock?.let { b ->
                    formatHiitBlockSummaryLine(b, loadUnit, sfx, addedLoad)
                } ?: e.sets.joinToString(" · ") { st ->
                    buildString {
                        append(st.reps)
                        st.weightKg?.let { w ->
                            val num = formatWeightLoadNumber(w, loadUnit)
                            if (addedLoad) append("×+$num$sfx")
                            else append("×$num$sfx")
                        }
                    }
                }
                Text(
                    "• $name: $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            if (routineToUpdate != null && session.routineId != null) {
                OutlinedButton(
                    onClick = { showRoutineConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !routineUpdating,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Text("Update “${routineToUpdate.name.ifBlank { "routine" }}” to match this workout")
                }
            }
            if (relayPool != null && signer != null) {
                OutlinedButton(
                    onClick = {
                        if (sharing || shared) return@OutlinedButton
                        sharing = true
                        scope.launch {
                            val ok = publishWeightWorkoutNote(
                                relayPool, signer, session, library, logDate, loadUnit
                            )
                            sharing = false
                            shared = ok
                            snackbarHostState.showSnackbar(
                                if (ok) "Shared to your relays!" else "Failed to share — check relay connection"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sharing && !shared,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            sharing -> "Sharing…"
                            shared -> "Shared"
                            else -> "Share workout"
                        }
                    )
                }
            }
            if (onRemoveFromLog != null) {
                OutlinedButton(
                    onClick = { showDiscardLogConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.9f)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White.copy(alpha = 0.6f)))
                ) {
                    Text("Discard workout (remove from log)")
                }
            }
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = mid)
            ) {
                Text("Done")
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    val removeFromLogCallback = onRemoveFromLog
    if (showDiscardLogConfirm && removeFromLogCallback != null) {
        AlertDialog(
            onDismissRequest = { showDiscardLogConfirm = false },
            title = { Text("Remove from log?") },
            text = {
                Text(
                    "This workout will be removed from ${logDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}. " +
                        "It will not stay in your history or sync as a saved session."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardLogConfirm = false
                        removeFromLogCallback()
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardLogConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRoutineConfirm && routineToUpdate != null) {
        AlertDialog(
            onDismissRequest = { if (!routineUpdating) showRoutineConfirm = false },
            title = { Text("Update routine?") },
            text = {
                Text(
                    "Replace “${routineToUpdate.name.ifBlank { "routine" }}” exercise order with this session’s order " +
                        "(same exercise twice in a row is collapsed to one entry). You can edit details later on the Routines tab."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        routineUpdating = true
                        scope.launch {
                            val newIds = dedupeConsecutiveExerciseIds(
                                session.entries.map { it.exerciseId }
                            )
                            repository.upsertRoutine(routineToUpdate.copy(exerciseIds = newIds))
                            onAfterRoutineSync()
                            routineUpdating = false
                            showRoutineConfirm = false
                            snackbarHostState.showSnackbar("Routine updated")
                        }
                    },
                    enabled = !routineUpdating
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRoutineConfirm = false },
                    enabled = !routineUpdating
                ) { Text("Cancel") }
            }
        )
    }
}
