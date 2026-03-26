package com.erv.app.hr

import androidx.compose.ui.graphics.Color
import com.erv.app.cardio.CardioHrSample

private const val ZONE1_MAX_PCT = 60
private const val ZONE2_MAX_PCT = 70
private const val ZONE3_MAX_PCT = 80
private const val ZONE4_MAX_PCT = 90

/** 1..5 — standard %maxHR zones (Z1 recovery … Z5 max). */
fun heartRateZoneIndex(bpm: Int, maxHr: Int): Int {
    if (maxHr <= 0) return 1
    val pct = bpm * 100 / maxHr
    return when {
        pct < ZONE1_MAX_PCT -> 1
        pct < ZONE2_MAX_PCT -> 2
        pct < ZONE3_MAX_PCT -> 3
        pct < ZONE4_MAX_PCT -> 4
        else -> 5
    }
}

fun zoneColor(zone: Int): Color = when (zone) {
    1 -> Color(0xFF64B5F6)
    2 -> Color(0xFF4CAF50)
    3 -> Color(0xFFFFEB3B)
    4 -> Color(0xFFFF9800)
    else -> Color(0xFFE53935)
}

/**
 * User setting wins if in a sane range; otherwise use workout peak (clamped) as a proxy for % zones.
 */
fun resolvedMaxHrForZones(userMaxBpm: Int?, samples: List<CardioHrSample>): Int {
    userMaxBpm?.takeIf { it in 90..230 }?.let { return it }
    val peak = samples.maxOfOrNull { it.bpm } ?: return 175
    return peak.coerceIn(120, 220)
}

/**
 * Assign each inter-sample interval to the zone of the sample at the start of the interval.
 */
fun zoneDurationsSeconds(samples: List<CardioHrSample>, maxHr: Int): IntArray {
    val out = IntArray(5)
    if (samples.isEmpty()) return out
    for (i in samples.indices) {
        val t0 = samples[i].epochSeconds
        val t1 = if (i < samples.lastIndex) samples[i + 1].epochSeconds else t0 + 1L
        val dt = (t1 - t0).toInt().coerceIn(0, 6 * 3600)
        if (dt <= 0) continue
        val z = heartRateZoneIndex(samples[i].bpm, maxHr).coerceIn(1, 5) - 1
        out[z] += dt
    }
    return out
}

fun formatDurationSeconds(totalSec: Int): String {
    if (totalSec <= 0) return "0:00"
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m >= 60) {
        val h = m / 60
        val mm = m % 60
        String.format("%d:%02d:%02d", h, mm, s)
    } else {
        String.format("%d:%02d", m, s)
    }
}
