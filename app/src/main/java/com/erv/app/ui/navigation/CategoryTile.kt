package com.erv.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, shows a heat + cold pair instead of a single [icon]. */
    secondaryIcon: ImageVector? = null,
    /** False = Coming-soon tile: muted card and glyphs (still tappable). */
    implemented: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val cardColors = if (implemented) {
        CardDefaults.elevatedCardColors()
    } else {
        CardDefaults.elevatedCardColors(
            containerColor = scheme.surfaceVariant.copy(alpha = 0.42f),
            contentColor = scheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
    val iconTint = if (implemented) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.55f)
    val labelColor = if (implemented) Color.Unspecified else scheme.onSurfaceVariant.copy(alpha = 0.65f)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (implemented) 4.dp else 1.dp
        ),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (secondaryIcon != null) {
                HotColdDualIcons(
                    iconSize = 24.dp,
                    heatIcon = icon,
                    coldIcon = secondaryIcon,
                    muted = !implemented,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 0.sp
                ),
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
