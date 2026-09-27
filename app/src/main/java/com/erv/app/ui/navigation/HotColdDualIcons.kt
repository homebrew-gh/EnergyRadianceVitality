package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erv.app.ui.theme.ErvAccentColdDark
import com.erv.app.ui.theme.ErvAccentColdLight
import com.erv.app.ui.theme.ErvAccentHeatDark
import com.erv.app.ui.theme.ErvAccentHeatLight
import com.erv.app.ui.theme.LocalErvDarkTheme

/**
 * Paired heat + cold glyphs (warm-tinted thermostat, cool-tinted AC) for Hot + Cold category tiles.
 */
@Composable
fun HotColdDualIcons(
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    heatIcon: ImageVector = Icons.Default.Thermostat,
    coldIcon: ImageVector = Icons.Default.AcUnit,
    heatTint: Color = if (LocalErvDarkTheme.current) ErvAccentHeatDark else ErvAccentHeatLight,
    coldTint: Color = if (LocalErvDarkTheme.current) ErvAccentColdDark else ErvAccentColdLight,
    /** Muted grey for both icons (Coming-soon category tiles). */
    muted: Boolean = false,
) {
    val mutedTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val heat = if (muted) mutedTint else heatTint
    val cold = if (muted) mutedTint else coldTint
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = heatIcon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = heat
        )
        Icon(
            imageVector = coldIcon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = cold
        )
    }
}
