package com.erv.app.ui.navigation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * True while [MainAppShell] is pulling supplement/light/cardio/weight/etc. state from relays
 * after startup (or when the activity is recreated).
 */
val LocalRelayDataSyncInProgress = compositionLocalOf { false }

@Composable
fun RelayDataSyncTopBarIcon(
    contentColor: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    if (!LocalRelayDataSyncInProgress.current) return
    val transition = rememberInfiniteTransition(label = "relaySync")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    Icon(
        imageVector = Icons.Filled.Sync,
        contentDescription = "Syncing from relays",
        tint = contentColor,
        modifier = modifier
            .padding(end = 8.dp)
            .size(22.dp)
            .rotate(rotation)
    )
}
