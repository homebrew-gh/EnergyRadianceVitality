package com.erv.app.ui.layout

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width-based grid tuning for dashboard tiles. Phone layouts (< 600 dp) keep the
 * original column counts; wider screens add columns while Launch Pad tiles continue
 * to fill the available width instead of clustering into a narrow centered block.
 */
object ErvAdaptiveGrid {
    private val compactMaxWidth = 600.dp
    private val mediumMaxWidth = 840.dp

    fun launchPadColumns(availableWidth: Dp): Int = when {
        availableWidth >= mediumMaxWidth -> 4
        availableWidth >= compactMaxWidth -> 3
        else -> 2
    }

    @Suppress("UNUSED_PARAMETER")
    fun launchPadMaxTileSize(availableWidth: Dp): Dp? = null

    fun categoryColumns(availableWidth: Dp): Int = when {
        availableWidth >= mediumMaxWidth -> 8
        availableWidth >= compactMaxWidth -> 6
        else -> 4
    }

    fun categoryMaxTileSize(availableWidth: Dp): Dp? = when {
        availableWidth >= compactMaxWidth -> 112.dp
        else -> null
    }

    fun tileSize(
        availableWidth: Dp,
        columns: Int,
        spacing: Dp,
        maxTileSize: Dp?,
    ): Dp {
        val totalSpacing = spacing * (columns - 1).coerceAtLeast(0)
        val raw = (availableWidth - totalSpacing) / columns
        return maxTileSize?.let { max -> minOf(raw, max) } ?: raw
    }

    fun gridContentWidth(tileSize: Dp, columns: Int, spacing: Dp): Dp {
        val totalSpacing = spacing * (columns - 1).coerceAtLeast(0)
        return tileSize * columns + totalSpacing
    }
}
