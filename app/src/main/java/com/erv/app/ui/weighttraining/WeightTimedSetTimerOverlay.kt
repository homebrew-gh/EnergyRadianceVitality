package com.erv.app.ui.weighttraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.erv.app.ui.media.playHiitSoftSegmentStartCue
import com.erv.app.ui.media.playHiitWorkCountdownTickCue
import com.erv.app.ui.media.playHiitWorkSegmentEndCue
import com.erv.app.ui.media.playHiitWorkSegmentStartCue
import kotlin.math.min
import kotlinx.coroutines.delay

private enum class TimedSetPhase { PREP, WORK }

/**
 * Full-screen get-ready + countdown timer for a single timed set (carries, holds, etc.).
 */
@Composable
fun WeightTimedSetTimerOverlay(
    exerciseName: String,
    goalSeconds: Int,
    prepSeconds: Int,
    countdownBeeps: Boolean = true,
    onFinished: (durationSeconds: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            TimedSetTimerBody(
                exerciseName = exerciseName,
                goalSeconds = goalSeconds,
                prepSeconds = prepSeconds.coerceAtLeast(0),
                countdownBeeps = countdownBeeps,
                onFinished = onFinished,
                onCancel = onDismiss,
            )
        }
    }
}

@Composable
private fun TimedSetTimerBody(
    exerciseName: String,
    goalSeconds: Int,
    prepSeconds: Int,
    countdownBeeps: Boolean,
    onFinished: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var phase by remember(goalSeconds, prepSeconds) {
        mutableStateOf(if (prepSeconds > 0) TimedSetPhase.PREP else TimedSetPhase.WORK)
    }
    var remaining by remember(goalSeconds, prepSeconds) {
        mutableIntStateOf(if (prepSeconds > 0) prepSeconds else goalSeconds)
    }

    LaunchedEffect(goalSeconds, prepSeconds) {
        if (prepSeconds > 0) {
            phase = TimedSetPhase.PREP
            playHiitSoftSegmentStartCue()
            var prep = prepSeconds
            while (prep > 0) {
                remaining = prep
                if (countdownBeeps && prep in 1..min(5, prepSeconds)) {
                    playHiitWorkCountdownTickCue()
                }
                delay(1_000L)
                prep--
            }
        }
        phase = TimedSetPhase.WORK
        playHiitWorkSegmentStartCue()
        var work = goalSeconds
        while (work > 0) {
            remaining = work
            if (countdownBeeps && work in 1..min(5, goalSeconds)) {
                playHiitWorkCountdownTickCue()
            }
            delay(1_000L)
            work--
        }
        if (countdownBeeps) {
            playHiitWorkSegmentEndCue()
        }
        onFinished(goalSeconds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(exerciseName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Text(
            when (phase) {
                TimedSetPhase.PREP -> "GET READY"
                TimedSetPhase.WORK -> "GO"
            },
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            remaining.coerceAtLeast(0).toString(),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            when (phase) {
                TimedSetPhase.PREP -> "Set starts after countdown"
                TimedSetPhase.WORK -> formatTimedSetWorkSubtitle(goalSeconds)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun formatTimedSetWorkSubtitle(goalSeconds: Int): String {
    val mins = goalSeconds / 60
    val secs = goalSeconds % 60
    return if (mins > 0) {
        "%d:%02d target".format(mins, secs)
    } else {
        "${goalSeconds}s target"
    }
}

data class WeightTimedSetTimerTarget(
    val exerciseId: String,
    val setIndex: Int,
    val goalSeconds: Int,
    val prepSeconds: Int,
    val countdownBeeps: Boolean = true,
)
