package com.erv.app.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erv.app.ui.theme.ErvColdMid
import com.erv.app.ui.theme.ErvDarkColdMid
import com.erv.app.ui.theme.ErvDarkTherapyRedMid
import com.erv.app.ui.theme.ErvLightTherapyRedMid

/**
 * Paired heat + cold glyphs (warm-tinted thermostat, cool-tinted AC) for Hot + Cold category tiles.
 */
@Composable
fun HotColdDualIcons(
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    heatIcon: ImageVector = Icons.Default.Thermostat,
    coldIcon: ImageVector = Icons.Default.AcUnit,
    /** Warm accent; matches Light therapy / hot session reds. */
    heatTint: Color = if (isSystemInDarkTheme()) ErvDarkTherapyRedMid else ErvLightTherapyRedMid,
    coldTint: Color = if (isSystemInDarkTheme()) ErvDarkColdMid else ErvColdMid
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = heatIcon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = heatTint
        )
        Icon(
            imageVector = coldIcon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = coldTint
        )
    }
}
