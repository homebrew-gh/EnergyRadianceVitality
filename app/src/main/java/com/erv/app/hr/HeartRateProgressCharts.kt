package com.erv.app.hr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

private val monthChartLabelFormatter =
    DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())

@Composable
fun HeartRateMonthlyZoneStackedChart(
    buckets: List<HeartRateMonthlyZoneBucket>,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val maxTotal = buckets.maxOf { it.zoneSeconds.sum() }.coerceAtLeast(60)
    val stepped = (ceil(maxTotal / 300.0) * 300.0).toInt()
    val niceMax = maxOf(maxTotal, stepped)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)) {
                val w = size.width
                val h = size.height
                val padB = 28f
                val padT = 8f
                val chartH = (h - padT - padB).coerceAtLeast(1f)
                val n = buckets.size.coerceAtLeast(1)
                val slotW = w / n
                val barW = (slotW * 0.55f).coerceAtLeast(4f)

                for (i in 0..4) {
                    val gy = padT + chartH * i / 4f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1f,
                    )
                }

                buckets.forEachIndexed { idx, bucket ->
                    val total = bucket.zoneSeconds.sum().coerceAtLeast(1)
                    val cx = slotW * idx + slotW / 2f
                    val x0 = cx - barW / 2f
                    var yBottom = padT + chartH
                    for (z in 5 downTo 1) {
                        val sec = bucket.zoneSeconds[z - 1]
                        if (sec <= 0) continue
                        val frac = sec.toFloat() / niceMax.toFloat()
                        val barH = chartH * frac.coerceIn(0f, 1f)
                        yBottom -= barH
                        drawRoundRect(
                            color = zoneColor(z),
                            topLeft = Offset(x0, yBottom),
                            size = Size(barW, barH.coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(3f, 3f),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                buckets.forEach { b ->
                    Text(
                        b.yearMonth.format(monthChartLabelFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (z in 1..5) {
                    Text(
                        "Z$z",
                        style = MaterialTheme.typography.labelSmall,
                        color = zoneColor(z),
                    )
                }
            }
        }
    }
}

@Composable
fun HeartRateMonthlyAvgLineChart(
    buckets: List<HeartRateMonthlyAvgBucket>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val minBpm = buckets.minOf { it.avgBpm }
    val maxBpm = buckets.maxOf { it.avgBpm }
    val span = (maxBpm - minBpm).coerceAtLeast(10)
    val niceMin = (minBpm - span / 10).coerceAtLeast(40)
    val niceMax = maxBpm + span / 10

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)) {
                val w = size.width
                val h = size.height
                val padL = 8f
                val padR = 8f
                val padB = 24f
                val padT = 12f
                val chartW = (w - padL - padR).coerceAtLeast(1f)
                val chartH = (h - padT - padB).coerceAtLeast(1f)
                val range = (niceMax - niceMin).coerceAtLeast(1)

                for (i in 0..4) {
                    val gy = padT + chartH * i / 4f
                    drawLine(gridColor, Offset(padL, gy), Offset(w - padR, gy), strokeWidth = 1f)
                }

                val n = buckets.size
                if (n >= 2) {
                    val points = buckets.mapIndexed { idx, b ->
                        val x = padL + chartW * idx / (n - 1).coerceAtLeast(1)
                        val y = padT + chartH * (1f - (b.avgBpm - niceMin).toFloat() / range)
                        Offset(x, y)
                    }
                    for (i in 0 until points.lastIndex) {
                        drawLine(
                            color = lineColor,
                            start = points[i],
                            end = points[i + 1],
                            strokeWidth = 3f,
                        )
                    }
                    points.forEach { p ->
                        drawCircle(color = lineColor, radius = 5f, center = p)
                    }
                } else if (n == 1) {
                    val b = buckets[0]
                    val x = padL + chartW / 2f
                    val y = padT + chartH * (1f - (b.avgBpm - niceMin).toFloat() / range)
                    drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                buckets.forEach { b ->
                    Text(
                        b.yearMonth.format(monthChartLabelFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
