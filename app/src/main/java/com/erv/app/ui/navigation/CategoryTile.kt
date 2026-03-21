package com.erv.app.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erv.app.ui.theme.ErvDarkCategoryMenuCard
import com.erv.app.ui.theme.ErvDarkCategoryMenuIconTint
import com.erv.app.ui.theme.ErvDarkCategoryMenuOnSurface

@Composable
fun CategoryTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, shows a heat + cold pair instead of a single [icon]. */
    secondaryIcon: ImageVector? = null
) {
    val darkMenu = isSystemInDarkTheme()
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 4.dp
        ),
        shape = MaterialTheme.shapes.medium,
        colors = if (darkMenu) {
            CardDefaults.elevatedCardColors(
                containerColor = ErvDarkCategoryMenuCard,
                contentColor = ErvDarkCategoryMenuOnSurface,
            )
        } else {
            CardDefaults.elevatedCardColors()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (secondaryIcon != null) {
                HotColdDualIcons(
                    iconSize = 22.dp,
                    heatIcon = icon,
                    coldIcon = secondaryIcon
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (darkMenu) ErvDarkCategoryMenuIconTint else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
