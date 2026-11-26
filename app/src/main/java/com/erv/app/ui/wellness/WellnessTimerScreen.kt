package com.erv.app.ui.wellness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.erv.app.ui.theme.ErvColdDark
import com.erv.app.ui.theme.ErvColdGlow
import com.erv.app.ui.theme.ErvColdMid
import com.erv.app.ui.theme.ErvDarkColdDark
import com.erv.app.ui.theme.ErvDarkColdGlow
import com.erv.app.ui.theme.ErvDarkColdMid
import com.erv.app.ui.theme.ErvDarkTherapyRedDark
import com.erv.app.ui.theme.ErvDarkTherapyRedGlow
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedDark
import com.erv.app.ui.theme.ErvLightTherapyRedGlow
import com.erv.app.ui.theme.ErvLightTherapyRedMid

/**
 * Full-screen view of the running [WellnessTimerController] timer: the same gradient and big
 * countdown as the classic timers, plus Pause / Resume, End and Save, and Discard (asks first).
 * Leaving the screen does not stop the timer; it keeps going under its notification.
 * [onHide], when given, is what Back does (the dashboard uses it to close its overlay).
 */
@Composable
fun WellnessTimerFullScreen(
    state: WellnessTimerState,
    onHide: (() -> Unit)? = null,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    if (onHide != null) BackHandler(onBack = onHide)

    val dark = isSystemInDarkTheme()
    val colors = when (state.kind) {
        WellnessTimerKind.COLD_PLUNGE ->
            if (dark) listOf(ErvDarkColdDark, ErvDarkColdMid, ErvDarkColdGlow) else listOf(ErvColdDark, ErvColdMid, ErvColdGlow)
        WellnessTimerKind.SAUNA, WellnessTimerKind.LIGHT ->
            if (dark) {
                listOf(ErvDarkTherapyRedDark, ErvDarkTherapyRedMid, ErvDarkTherapyRedGlow)
            } else {
                listOf(ErvLightTherapyRedDark, ErvLightTherapyRedMid, ErvLightTherapyRedGlow)
            }
    }
    var confirmDiscard by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = colors))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (state.paused) "Paused" else "Session in progress",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTimerClock(state.remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
                if (state.detail.isNotBlank()) {
                    Text(
                        state.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.totalSeconds <= 0) 0f else state.elapsedSeconds / state.totalSeconds.toFloat()
                    },
                    modifier = Modifier.fillMaxWidth(0.7f),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Keeps running with the screen off. A soft chime plays at the end and the session is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { WellnessTimerController.togglePause() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.White))
                    ) {
                        Icon(if (state.paused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.paused) "Resume" else "Pause")
                    }
                    Button(
                        onClick = { WellnessTimerController.endAndSave() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors[0])
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("End and Save")
                    }
                }
                TextButton(
                    onClick = { confirmDiscard = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Discard")
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this session?") },
            text = { Text("The timer stops and nothing is saved. To keep the time you did, use End and Save.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    WellnessTimerController.discard()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep Going") }
            }
        )
    }
}
