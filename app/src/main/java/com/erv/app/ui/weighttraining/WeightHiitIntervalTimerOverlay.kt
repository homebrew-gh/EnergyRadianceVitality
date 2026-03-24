package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import com.erv.app.ui.media.playHiitWorkSegmentEndCue
import com.erv.app.ui.media.playHiitWorkSegmentStartCue
import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightHiitIntervalPlan
import kotlin.math.min
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private enum class HiitPhase { WORK, REST }

/**
 * Full-screen guided work/rest intervals. On completion, optional RPE is collected before [onFinished].
 */
@Composable
fun WeightHiitIntervalTimerOverlay(
    exerciseName: String,
    plan: WeightHiitIntervalPlan,
    onFinished: (WeightHiitBlockLog) -> Unit,
    onDismiss: () -> Unit,
) {
    var timerDone by remember(plan) { mutableStateOf(false) }
    var showRpeDialog by remember(plan) { mutableStateOf(false) }

    if (!timerDone) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                HiitTimerBody(
                    exerciseName = exerciseName,
                    plan = plan,
                    onAllIntervalsComplete = {
                        timerDone = true
                        showRpeDialog = true
                    },
                    onCancel = onDismiss
                )
            }
        }
    }

    if (showRpeDialog) {
        var rpeText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                onFinished(
                    WeightHiitBlockLog(
                        intervals = plan.intervals,
                        workSeconds = plan.workSeconds,
                        restSeconds = plan.restSeconds,
                        weightKg = plan.weightKg,
                        rpe = null
                    )
                )
                showRpeDialog = false
            },
            title = { Text("Interval block done") },
            text = {
                Column {
                    Text(
                        "${plan.intervals} rounds · ${plan.workSeconds}s work / ${plan.restSeconds}s rest",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rpeText,
                        onValueChange = { rpeText = it },
                        label = { Text("RPE (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rpe = rpeText.trim().toDoubleOrNull()
                        onFinished(
                            WeightHiitBlockLog(
                                intervals = plan.intervals,
                                workSeconds = plan.workSeconds,
                                restSeconds = plan.restSeconds,
                                weightKg = plan.weightKg,
                                rpe = rpe
                            )
                        )
                        showRpeDialog = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onFinished(
                            WeightHiitBlockLog(
                                intervals = plan.intervals,
                                workSeconds = plan.workSeconds,
                                restSeconds = plan.restSeconds,
                                weightKg = plan.weightKg,
                                rpe = null
                            )
                        )
                        showRpeDialog = false
                    }
                ) { Text("Skip") }
            }
        )
    }
}

@Composable
private fun HiitTimerBody(
    exerciseName: String,
    plan: WeightHiitIntervalPlan,
    onAllIntervalsComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    var phase by remember(plan) { mutableStateOf(HiitPhase.WORK) }
    var intervalIndex by remember(plan) { mutableIntStateOf(1) }
    var remaining by remember(plan) { mutableIntStateOf(plan.workSeconds) }
    val skipRest = remember(plan) { AtomicBoolean(false) }

    LaunchedEffect(plan) {
        var interval = 1
        while (interval <= plan.intervals) {
            phase = HiitPhase.WORK
            intervalIndex = interval
            playHiitWorkSegmentStartCue()
            var s = plan.workSeconds
            while (s > 0) {
                remaining = s
                if (s in 1..min(5, plan.workSeconds)) {
                    playHiitWorkCountdownTickCue()
                }
                delay(1_000L)
                s--
            }
            playHiitWorkSegmentEndCue()
            if (interval < plan.intervals && plan.restSeconds > 0) {
                phase = HiitPhase.REST
                intervalIndex = interval
                var r = plan.restSeconds
                while (r > 0) {
                    remaining = r
                    delay(1_000L)
                    if (skipRest.getAndSet(false)) break
                    r--
                }
            }
            interval++
        }
        onAllIntervalsComplete()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(exerciseName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Text(
            when (phase) {
                HiitPhase.WORK -> "WORK"
                HiitPhase.REST -> "REST"
            },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            remaining.coerceAtLeast(0).toString(),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Interval $intervalIndex of ${plan.intervals}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(48.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = { skipRest.set(true) },
                enabled = phase == HiitPhase.REST,
                modifier = Modifier.weight(1f)
            ) { Text("Skip rest") }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("Cancel") }
        }
    }
}
