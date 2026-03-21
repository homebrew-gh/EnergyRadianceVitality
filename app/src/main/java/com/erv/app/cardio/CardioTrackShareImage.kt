package com.erv.app.cardio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.erv.app.R
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a privacy-style route card: wine gradient (therapy-red family), white track, no map.
 * Watermark: [ic_sun] + "ERV" in the lower-left corner.
 */
object CardioTrackShareImage {

    private const val DEFAULT_W = 1080
    private const val DEFAULT_H = 1440

    /**
     * @param colorTop colorMid colorBottom ARGB colors matching the cardio summary gradient.
     */
    fun renderBitmap(
        context: Context,
        points: List<CardioGpsPoint>,
        colorTop: Int,
        colorMid: Int,
        colorBottom: Int,
        width: Int = DEFAULT_W,
        height: Int = DEFAULT_H
    ): Bitmap? {
        val ordered = points.sortedBy { it.epochSeconds }
        if (ordered.isEmpty()) return null

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(colorTop, colorMid, colorBottom),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val pad = min(width, height) * 0.10f
        val innerW = width - 2 * pad
        val innerH = height - 2 * pad
        if (innerW < 8f || innerH < 8f) return bmp

        val lats = ordered.map { it.lat }
        val lons = ordered.map { it.lon }
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

        val strokeW = max(6f, width * 0.009f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F0F0.toInt()
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        if (ordered.size == 1) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF5F0F0.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(x(ordered[0].lon), y(ordered[0].lat), strokeW * 2.5f, dotPaint)
        } else {
            val path = Path().apply {
                moveTo(x(ordered[0].lon), y(ordered[0].lat))
                for (i in 1 until ordered.size) {
                    lineTo(x(ordered[i].lon), y(ordered[i].lat))
                }
            }
            canvas.drawPath(path, trackPaint)
        }

        drawWatermark(context, canvas, width, height)

        return bmp
    }

    /**
     * Same visual as [renderBitmap], as PNG bytes for Blossom / NIP-96 upload or in-app use.
     */
    suspend fun renderRoutePngBytes(
        context: Context,
        points: List<CardioGpsPoint>,
        colorTop: Int,
        colorMid: Int,
        colorBottom: Int
    ): ByteArray? = withContext(Dispatchers.Default) {
        val bmp = renderBitmap(context.applicationContext, points, colorTop, colorMid, colorBottom)
            ?: return@withContext null
        try {
            ByteArrayOutputStream().use { baos ->
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 95, baos)) return@withContext null
                baos.toByteArray()
            }
        } finally {
            bmp.recycle()
        }
    }

    private fun drawWatermark(context: Context, canvas: Canvas, w: Int, h: Int) {
        val margin = (min(w, h) * 0.035f).coerceIn(24f, 56f)
        val sunSize = (h * 0.055f).coerceIn(40f, 72f).toInt()
        val sun = ContextCompat.getDrawable(context, R.drawable.ic_sun)?.mutate() ?: return

        val baselineY = h - margin
        val sunLeft = margin.toInt()
        val sunTop = (baselineY - sunSize).toInt()
        sun.setBounds(sunLeft, sunTop, sunLeft + sunSize, sunTop + sunSize)
        sun.alpha = 230
        sun.draw(canvas)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5F0F0.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = (h * 0.038f).coerceIn(28f, 44f)
            setShadowLayer(4f, 0f, 2f, 0x80000000.toInt())
        }
        val label = "ERV"
        val textX = sunLeft + sunSize + margin * 0.35f
        val sunCenterY = sunTop + sunSize / 2f
        val textBaseline = sunCenterY - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, textX, textBaseline, textPaint)
    }
}
