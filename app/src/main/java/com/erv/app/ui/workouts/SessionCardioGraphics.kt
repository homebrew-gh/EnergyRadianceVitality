package com.erv.app.ui.workouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erv.app.hr.heartRateZoneShortName
import com.erv.app.hr.zoneColor
import com.erv.app.workouts.IntervalStrip

/**
 * A ring around the leg's clock that fills as a countdown runs down or a distance leg is covered.
 */
@Composable
internal fun SessionClockRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "clock_ring")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            drawArc(
                fill,
                startAngle = -90f,
                sweepAngle = 360f * shown,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/**
 * Intervals as a strip of blocks, work dark and recovery light, done ones full, the running one
 * filling as it goes.
 */
@Composable
internal fun SessionIntervalStrip(
    strip: IntervalStrip,
    /** How far through the running leg, 0 to 1. */
    currentProgress: Float,
    modifier: Modifier = Modifier,
) {
    val work = MaterialTheme.colorScheme.primary
    val recovery = MaterialTheme.colorScheme.tertiary
    val shown by animateFloatAsState(currentProgress.coerceIn(0f, 1f), label = "interval_strip")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp),
        ) {
            strip.blocks.forEachIndexed { index, block ->
                val color = if (block.isRecovery) recovery.copy(alpha = 0.55f) else work
                val filled = when {
                    index < strip.currentIndex -> 1f
                    index == strip.currentIndex -> shown
                    else -> 0f
                }
                Box(
                    Modifier
                        .weight(block.weight.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .then(
                            if (index == strip.currentIndex) {
                                Modifier.border(2.dp, color, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    if (filled > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(filled)
                                .background(color, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
        val workTotal = strip.blocks.count { !it.isRecovery }
        val workDone = strip.blocks.take(strip.currentIndex).count { !it.isRecovery }
        Text(
            "Interval ${(workDone + if (strip.blocks[strip.currentIndex].isRecovery) 0 else 1).coerceAtMost(workTotal)} of $workTotal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * For open-ended cardio with no distance to race: the five heart rate zones as a bar with a
 * heart where the strap's reading sits.
 */
@Composable
internal fun SessionHeartRateZoneBar(
    bpm: Int,
    zone: Int,
    /** Where the reading sits inside its zone, 0 to 1. */
    withinZone: Float,
    modifier: Modifier = Modifier,
) {
    val color = zoneColor(zone)
    val position by animateFloatAsState(((zone - 1) + withinZone.coerceIn(0f, 1f)) / 5f, label = "hr_zone")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Zone $zone · ${heartRateZoneShortName(zone)} · $bpm bpm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp),
            ) {
                val marker = 28.dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    (1..5).forEach { z ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    zoneColor(z).copy(alpha = if (z == zone) 1f else 0.35f),
                                    RoundedCornerShape(4.dp),
                                ),
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = (maxWidth - marker) * position)
                        .size(marker)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
