package com.erv.app.ui.weighttraining

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.R
import com.erv.app.data.WeightLiveRestTimerMode
import com.erv.app.ui.components.CompactIntWheel
import kotlin.math.abs

private val WEIGHT_REST_DURATION_OPTIONS: List<Int> = (10..600 step 5).toList()

private fun nearestRestDurationSeconds(preferred: Int): Int {
    val p = preferred.coerceIn(10, 600)
    return WEIGHT_REST_DURATION_OPTIONS.minByOrNull { abs(it - p) } ?: p
}

private fun formatRestDurationWheelLabel(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) String.format("%d:%02d", m, s) else "${seconds}s"
}

internal fun formatWeightRestCountdown(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format("%d:%02d", m, sec)
}

@Composable
fun WeightLiveRestTimerSettingsDialog(
    initialMode: WeightLiveRestTimerMode,
    initialSeconds: Int,
    initialCountdownSoundEnabled: Boolean,
    initialEndSoundEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (WeightLiveRestTimerMode, Int, Boolean, Boolean) -> Unit,
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var pickedSeconds by remember(initialSeconds) {
        mutableIntStateOf(nearestRestDurationSeconds(initialSeconds))
    }
    var countdownSoundEnabled by remember(initialCountdownSoundEnabled) {
        mutableStateOf(initialCountdownSoundEnabled)
    }
    var endSoundEnabled by remember(initialEndSoundEnabled) {
        mutableStateOf(initialEndSoundEnabled)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_live_rest_timer_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.weight_live_rest_timer_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = mode == WeightLiveRestTimerMode.OFF,
                        onClick = { mode = WeightLiveRestTimerMode.OFF },
                        label = { Text(stringResource(R.string.weight_live_rest_timer_mode_off)) }
                    )
                    FilterChip(
                        selected = mode == WeightLiveRestTimerMode.AUTO,
                        onClick = { mode = WeightLiveRestTimerMode.AUTO },
                        label = { Text(stringResource(R.string.weight_live_rest_timer_mode_auto)) }
                    )
                    FilterChip(
                        selected = mode == WeightLiveRestTimerMode.MANUAL,
                        onClick = { mode = WeightLiveRestTimerMode.MANUAL },
                        label = { Text(stringResource(R.string.weight_live_rest_timer_mode_manual)) }
                    )
                }
                Text(
                    stringResource(R.string.weight_live_rest_timer_seconds_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompactIntWheel(
                    values = WEIGHT_REST_DURATION_OPTIONS,
                    currentValue = pickedSeconds,
                    onCommitted = { pickedSeconds = it },
                    formatLabel = { formatRestDurationWheelLabel(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.weight_live_rest_timer_wheel_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.weight_live_rest_timer_countdown_sound_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.weight_live_rest_timer_countdown_sound_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = countdownSoundEnabled,
                        onCheckedChange = { countdownSoundEnabled = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.weight_live_rest_timer_end_sound_label),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.weight_live_rest_timer_end_sound_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = endSoundEnabled,
                        onCheckedChange = { endSoundEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(mode, pickedSeconds, countdownSoundEnabled, endSoundEnabled)
                    onDismiss()
                }
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeightLiveRestTimerHeaderRow(
    restMode: WeightLiveRestTimerMode,
    workoutElapsedText: String,
    restSecondsRemaining: Int?,
    restManualPending: Boolean,
    onStartManualRest: () -> Unit,
    onSkipRest: () -> Unit,
    onRestZoneLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val restSettingsCd = stringResource(R.string.weight_live_rest_timer_long_press_cd)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .semantics { contentDescription = restSettingsCd }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onRestZoneLongPress
                    )
            ) {
                Text(
                    text = stringResource(R.string.weight_live_rest_timer_display_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    restSecondsRemaining != null -> {
                        Text(
                            text = formatWeightRestCountdown(restSecondsRemaining),
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                    restManualPending && restMode == WeightLiveRestTimerMode.MANUAL -> {
                        Text(
                            text = stringResource(R.string.weight_live_rest_timer_manual_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    restMode == WeightLiveRestTimerMode.OFF -> {
                        Text(
                            text = stringResource(R.string.weight_live_rest_timer_mode_off_display),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.weight_live_rest_timer_idle),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            when {
                restSecondsRemaining != null -> {
                    TextButton(onClick = onSkipRest) {
                        Text(stringResource(R.string.weight_live_rest_timer_skip))
                    }
                }
                restManualPending && restMode == WeightLiveRestTimerMode.MANUAL -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onStartManualRest) {
                            Text(stringResource(R.string.weight_live_rest_timer_start))
                        }
                        TextButton(onClick = onSkipRest) {
                            Text(stringResource(R.string.weight_live_rest_timer_dismiss))
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.weight_live_workout_timer_display_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = workoutElapsedText,
                style = MaterialTheme.typography.displaySmall
            )
        }
    }
}
