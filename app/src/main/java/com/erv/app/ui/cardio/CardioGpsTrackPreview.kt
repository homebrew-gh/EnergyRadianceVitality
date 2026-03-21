package com.erv.app.ui.cardio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioGpsPoint

/**
 * Fits the track into the rectangle (north up). No basemap — shape-only preview for the summary screen.
 */
@Composable
fun CardioGpsTrackSummaryPreview(
    points: List<CardioGpsPoint>,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.92f),
    backgroundColor: Color = Color.Black.copy(alpha = 0.38f),
) {
    if (points.isEmpty()) return
    val sorted = remember(points) { points.sortedBy { it.epochSeconds } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pad = 14.dp.toPx()
            val w = size.width
            val h = size.height
            val innerW = (w - 2 * pad).coerceAtLeast(1f)
            val innerH = (h - 2 * pad).coerceAtLeast(1f)

            val lats = sorted.map { it.lat }
            val lons = sorted.map { it.lon }
            var minLat = lats.minOrNull()!!
            var maxLat = lats.maxOrNull()!!
            var minLon = lons.minOrNull()!!
            var maxLon = lons.maxOrNull()!!
            if (maxLat - minLat < 1e-7) {
                minLat -= 2e-5
                maxLat += 2e-5
            }
            if (maxLon - minLon < 1e-7) {
                minLon -= 2e-5
                maxLon += 2e-5
            }
            val latRange = (maxLat - minLat).coerceAtLeast(1e-12)
            val lonRange = (maxLon - minLon).coerceAtLeast(1e-12)

            fun x(lon: Double) = pad + ((lon - minLon) / lonRange).toFloat() * innerW
            fun y(lat: Double) = pad + (1f - ((lat - minLat) / latRange).toFloat()) * innerH

            if (sorted.size == 1) {
                val p = sorted[0]
                drawCircle(
                    color = trackColor,
                    radius = 5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(x(p.lon), y(p.lat))
                )
                return@Canvas
            }

            val path = Path().apply {
                moveTo(x(sorted[0].lon), y(sorted[0].lat))
                for (i in 1 until sorted.size) {
                    lineTo(x(sorted[i].lon), y(sorted[i].lat))
                }
            }
            drawPath(
                path,
                color = trackColor,
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
