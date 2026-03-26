package com.erv.app.hr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.erv.app.cardio.CardioHrSample

@Composable
fun HeartRateWorkoutChart(
    samples: List<CardioHrSample>,
    lineColor: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
    heightDp: Int = 168
) {
    if (samples.size < 2) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
    ) {
        val w = size.width
        val h = size.height
        val padL = 4f
        val padR = 8f
        val padT = 8f
        val padB = 20f
        val chartW = (w - padL - padR).coerceAtLeast(1f)
        val chartH = (h - padT - padB).coerceAtLeast(1f)
        val minB = samples.minOf { it.bpm } - 5
        val maxB = samples.maxOf { it.bpm } + 5
        val bSpan = (maxB - minB).coerceAtLeast(1)
        val t0 = samples.first().epochSeconds
        val t1 = samples.last().epochSeconds
        val tSpan = (t1 - t0).coerceAtLeast(1)

        // Horizontal grid
        for (i in 0..3) {
            val gy = padT + chartH * i / 3f
            drawLine(
                color = gridColor,
                start = Offset(padL, gy),
                end = Offset(padL + chartW, gy),
                strokeWidth = 1f
            )
        }

        val path = Path()
        samples.forEachIndexed { idx, s ->
            val x = padL + (s.epochSeconds - t0).toFloat() / tSpan * chartW
            val yNorm = (s.bpm - minB).toFloat() / bSpan
            val y = padT + chartH * (1f - yNorm)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}
