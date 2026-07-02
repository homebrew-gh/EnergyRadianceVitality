package com.erv.app.hr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import com.erv.app.cardio.CardioHrSample
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a workout heart-rate chart card as PNG bytes for encrypted Blossom backup.
 */
object HeartRateShareImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 720
    private const val COLOR_TOP = 0xFF2E0808.toInt()
    private const val COLOR_MID = 0xFF6B0000.toInt()
    private const val COLOR_BOTTOM = 0xFF7A1515.toInt()

    suspend fun renderPngBytes(
        samples: List<CardioHrSample>,
        zoneInputs: HeartRateZoneInputs = HeartRateZoneInputs(),
        title: String = "Heart rate",
        avgBpm: Int? = null,
        maxBpm: Int? = null,
        minBpm: Int? = null,
    ): ByteArray? = withContext(Dispatchers.Default) {
        if (samples.size < 2) return@withContext null
        val bmp = renderBitmap(samples, zoneInputs, title, avgBpm, maxBpm, minBpm) ?: return@withContext null
        try {
            ByteArrayOutputStream().use { baos ->
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 95, baos)) return@withContext null
                baos.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }

    private fun renderBitmap(
        samples: List<CardioHrSample>,
        zoneInputs: HeartRateZoneInputs,
        title: String,
        avgBpm: Int?,
        maxBpm: Int?,
        minBpm: Int?,
    ): Bitmap? {
        val ordered = samples.sortedBy { it.epochSeconds }
        if (ordered.size < 2) return null

        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                HEIGHT.toFloat(),
                intArrayOf(COLOR_TOP, COLOR_MID, COLOR_BOTTOM),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F0F0.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 52f
        }
        canvas.drawText(title, 56f, 88f, titlePaint)

        val stats = buildList {
            avgBpm?.let { add("Avg $it") }
            maxBpm?.let { add("Max $it") }
            minBpm?.let { add("Min $it") }
        }.joinToString(" · ")
        if (stats.isNotEmpty()) {
            val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCF5F0F0.toInt()
                textSize = 34f
            }
            canvas.drawText("$stats bpm", 56f, 138f, statsPaint)
        }

        val padL = 56f
        val padR = 56f
        val padT = 180f
        val padB = 72f
        val chartW = (WIDTH - padL - padR).coerceAtLeast(1f)
        val chartH = (HEIGHT - padT - padB).coerceAtLeast(1f)

        val maxHr = resolvedMaxHrForZones(zoneInputs, ordered)
        val minB = ordered.minOf { it.bpm } - 5
        val maxB = max(maxHr, ordered.maxOf { it.bpm } + 5)
        val bSpan = (maxB - minB).coerceAtLeast(1)
        val t0 = ordered.first().epochSeconds
        val t1 = ordered.last().epochSeconds
        val tSpan = (t1 - t0).coerceAtLeast(1)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x44FFFFFF
            strokeWidth = 1f
        }
        for (i in 0..3) {
            val gy = padT + chartH * i / 3f
            canvas.drawLine(padL, gy, padL + chartW, gy, gridPaint)
        }

        val path = Path()
        ordered.forEachIndexed { idx, sample ->
            val x = padL + (sample.epochSeconds - t0).toFloat() / tSpan * chartW
            val yNorm = (sample.bpm - minB).toFloat() / bSpan
            val y = padT + chartH * (1f - yNorm)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F0F0.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, linePaint)

        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCF5F0F0.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 34f
        }
        canvas.drawText("ERV", WIDTH - 120f, HEIGHT - 40f, watermarkPaint)

        return bmp
    }
}
