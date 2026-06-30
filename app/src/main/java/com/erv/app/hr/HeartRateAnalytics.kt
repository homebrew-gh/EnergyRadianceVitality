package com.erv.app.hr

import androidx.compose.ui.graphics.Color
import com.erv.app.cardio.CardioHrLoadSummary
import com.erv.app.cardio.CardioHrSample
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.DatedCardioSession
import com.erv.app.unifiedroutines.UnifiedRoutineLibraryState
import com.erv.app.unifiedroutines.UnifiedWorkoutSession
import com.erv.app.weighttraining.WeightLibraryState
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** How Z1–Z5 boundaries are computed from max and optional resting HR. */
enum class HeartRateZoneMethod {
    /** % of max HR (Z1 &lt; 60%, Z2 60–69%, …). */
    PERCENT_MAX_HR,

    /** Heart-rate reserve (Karvonen): zones use % of (max − resting) added to resting. */
    KARVONEN_HRR,
}

data class HeartRateZoneInputs(
    val manualMaxBpm: Int? = null,
    val ageYears: Int? = null,
    val restingBpm: Int? = null,
    val method: HeartRateZoneMethod = HeartRateZoneMethod.PERCENT_MAX_HR,
)

data class HeartRateDatedWorkout(
    val logDate: LocalDate,
    val heartRate: CardioHrScaffolding,
)

data class HeartRateMonthlyZoneBucket(
    val yearMonth: YearMonth,
    /** Seconds in Z1..Z5 for workouts that month. */
    val zoneSeconds: IntArray,
)

data class HeartRateMonthlyAvgBucket(
    val yearMonth: YearMonth,
    val avgBpm: Int,
    val workoutCount: Int,
)

data class HeartRateProgressStats(
    val workoutsWithHr: Int,
    /** Total seconds in Z1..Z5 across all included workouts. */
    val totalZoneSeconds: IntArray,
    val monthlyZoneSeconds: List<HeartRateMonthlyZoneBucket>,
    val monthlyAvgBpm: List<HeartRateMonthlyAvgBucket>,
)

private const val ZONE1_MAX_PCT = 60
private const val ZONE2_MAX_PCT = 70
private const val ZONE3_MAX_PCT = 80
private const val ZONE4_MAX_PCT = 90

private const val HRR_ZONE1_MIN_PCT = 50
private const val HRR_ZONE2_MIN_PCT = 60
private const val HRR_ZONE3_MIN_PCT = 70
private const val HRR_ZONE4_MIN_PCT = 80
private const val HRR_ZONE5_MIN_PCT = 90

fun estimateMaxHrFromAge(ageYears: Int): Int =
    (220 - ageYears.coerceIn(10, 100)).coerceIn(90, 230)

/**
 * Effective max HR for zone math: manual setting, then age estimate, then workout peak proxy.
 */
fun resolvedMaxHrForZones(
    userMaxBpm: Int?,
    samples: List<CardioHrSample>,
    userAgeYears: Int? = null,
): Int {
    userMaxBpm?.takeIf { it in 90..230 }?.let { return it }
    userAgeYears?.takeIf { it in 10..100 }?.let { return estimateMaxHrFromAge(it) }
    val peak = samples.maxOfOrNull { it.bpm } ?: return 175
    return peak.coerceIn(120, 220)
}

fun resolvedMaxHrForZones(inputs: HeartRateZoneInputs, samples: List<CardioHrSample>): Int =
    resolvedMaxHrForZones(inputs.manualMaxBpm, samples, inputs.ageYears)

private fun restingForKarvonen(restingBpm: Int?, maxHr: Int): Int =
    restingBpm?.takeIf { it in 35..100 } ?: (maxHr / 4).coerceIn(40, 90)

/** 1..5 — standard five training zones. */
fun heartRateZoneIndex(
    bpm: Int,
    maxHr: Int,
    restingBpm: Int? = null,
    method: HeartRateZoneMethod = HeartRateZoneMethod.PERCENT_MAX_HR,
): Int = when (method) {
    HeartRateZoneMethod.PERCENT_MAX_HR -> heartRateZoneIndexPercentMax(bpm, maxHr)
    HeartRateZoneMethod.KARVONEN_HRR -> heartRateZoneIndexKarvonen(bpm, maxHr, restingForKarvonen(restingBpm, maxHr))
}

private fun heartRateZoneIndexPercentMax(bpm: Int, maxHr: Int): Int {
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

private fun heartRateZoneIndexKarvonen(bpm: Int, maxHr: Int, resting: Int): Int {
    val reserve = (maxHr - resting).coerceAtLeast(1)
    val pct = (bpm - resting) * 100 / reserve
    return when {
        pct < HRR_ZONE1_MIN_PCT -> 1
        pct < HRR_ZONE2_MIN_PCT -> 2
        pct < HRR_ZONE3_MIN_PCT -> 3
        pct < HRR_ZONE4_MIN_PCT -> 4
        pct < HRR_ZONE5_MIN_PCT -> 5
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

fun heartRateZoneShortName(zone: Int): String = when (zone) {
    1 -> "Recovery"
    2 -> "Endurance"
    3 -> "Tempo"
    4 -> "Threshold"
    5 -> "Max"
    else -> "Zone"
}

/** Human-readable BPM band for settings calculator preview. */
fun formatZoneBpmRange(
    zone: Int,
    maxHr: Int,
    restingBpm: Int?,
    method: HeartRateZoneMethod,
): String {
    val (low, high) = zoneBpmBounds(zone, maxHr, restingBpm, method)
    return when {
        low == null && high != null -> "≤ $high bpm"
        low != null && high == null -> "≥ $low bpm"
        low != null && high != null -> "$low–$high bpm"
        else -> "—"
    }
}

private fun zoneBpmBounds(
    zone: Int,
    maxHr: Int,
    restingBpm: Int?,
    method: HeartRateZoneMethod,
): Pair<Int?, Int?> = when (method) {
    HeartRateZoneMethod.PERCENT_MAX_HR -> percentMaxZoneBounds(zone, maxHr)
    HeartRateZoneMethod.KARVONEN_HRR -> {
        val r = restingForKarvonen(restingBpm, maxHr)
        karvonenZoneBounds(zone, maxHr, r)
    }
}

private fun percentMaxZoneBounds(zone: Int, maxHr: Int): Pair<Int?, Int?> = when (zone) {
    1 -> null to (maxHr * ZONE1_MAX_PCT / 100 - 1).coerceAtLeast(1)
    2 -> (maxHr * ZONE1_MAX_PCT / 100) to (maxHr * ZONE2_MAX_PCT / 100 - 1)
    3 -> (maxHr * ZONE2_MAX_PCT / 100) to (maxHr * ZONE3_MAX_PCT / 100 - 1)
    4 -> (maxHr * ZONE3_MAX_PCT / 100) to (maxHr * ZONE4_MAX_PCT / 100 - 1)
    5 -> (maxHr * ZONE4_MAX_PCT / 100) to null
    else -> null to null
}

private fun karvonenZoneBounds(zone: Int, maxHr: Int, resting: Int): Pair<Int?, Int?> {
    val reserve = (maxHr - resting).coerceAtLeast(1)
    fun bpmAt(pct: Int) = resting + reserve * pct / 100
    return when (zone) {
        1 -> null to bpmAt(HRR_ZONE1_MIN_PCT) - 1
        2 -> bpmAt(HRR_ZONE1_MIN_PCT) to bpmAt(HRR_ZONE2_MIN_PCT) - 1
        3 -> bpmAt(HRR_ZONE2_MIN_PCT) to bpmAt(HRR_ZONE3_MIN_PCT) - 1
        4 -> bpmAt(HRR_ZONE3_MIN_PCT) to bpmAt(HRR_ZONE4_MIN_PCT) - 1
        5 -> bpmAt(HRR_ZONE4_MIN_PCT) to bpmAt(HRR_ZONE5_MIN_PCT)
        else -> null to null
    }
}

/**
 * Assign each inter-sample interval to the zone of the sample at the start of the interval.
 */
fun zoneDurationsSeconds(
    samples: List<CardioHrSample>,
    maxHr: Int,
    restingBpm: Int? = null,
    method: HeartRateZoneMethod = HeartRateZoneMethod.PERCENT_MAX_HR,
): IntArray {
    val out = IntArray(5)
    if (samples.isEmpty()) return out
    val gaps = ArrayList<Int>(samples.size)
    for (i in 0 until samples.lastIndex) {
        val gap = (samples[i + 1].epochSeconds - samples[i].epochSeconds).toInt().coerceIn(0, 6 * 3600)
        if (gap > 0) gaps.add(gap)
    }
    // The final sample has no successor; credit it with the typical cadence so the
    // last reading isn't reduced to a single second.
    val lastGap = if (gaps.isEmpty()) 1 else gaps.sorted()[gaps.size / 2]
    for (i in samples.indices) {
        val dt = if (i < samples.lastIndex) {
            (samples[i + 1].epochSeconds - samples[i].epochSeconds).toInt().coerceIn(0, 6 * 3600)
        } else {
            lastGap
        }
        if (dt <= 0) continue
        val z = heartRateZoneIndex(samples[i].bpm, maxHr, restingBpm, method).coerceIn(1, 5) - 1
        out[z] += dt
    }
    return out
}

fun zoneDurationsSeconds(samples: List<CardioHrSample>, inputs: HeartRateZoneInputs): IntArray {
    val maxHr = resolvedMaxHrForZones(inputs, samples)
    return zoneDurationsSeconds(samples, maxHr, inputs.restingBpm, inputs.method)
}

private fun HeartRateZoneMethod.wireName(): String =
    when (this) {
        HeartRateZoneMethod.PERCENT_MAX_HR -> "percent_max_hr"
        HeartRateZoneMethod.KARVONEN_HRR -> "karvonen_hrr"
    }

fun buildHeartRateLoadSummary(
    heartRate: CardioHrScaffolding,
    inputs: HeartRateZoneInputs,
): CardioHrLoadSummary? {
    val samples = heartRate.samples
    if (samples.size < 2) return heartRate.load
    val maxHr = resolvedMaxHrForZones(inputs, samples)
    val zones = zoneDurationsSeconds(samples, maxHr, inputs.restingBpm, inputs.method).toList()
    val durationSeconds = zones.sum().takeIf { it > 0 }
    return CardioHrLoadSummary(
        sampleCount = samples.size,
        durationSeconds = durationSeconds,
        zoneSeconds = zones,
        zoneMethod = inputs.method.wireName(),
        maxBpm = maxHr,
        restingBpm = inputs.restingBpm,
    )
}

fun CardioHrScaffolding.toRelaySafeHeartRate(
    inputs: HeartRateZoneInputs,
): CardioHrScaffolding =
    copy(
        load = buildHeartRateLoadSummary(this, inputs),
        samples = emptyList(),
    )

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

fun collectHeartRateWorkouts(
    cardio: CardioLibraryState,
    weight: WeightLibraryState,
    unified: UnifiedRoutineLibraryState,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<HeartRateDatedWorkout> {
    val out = mutableListOf<HeartRateDatedWorkout>()
    for (log in cardio.logs) {
        val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: continue
        for (session in log.sessions) {
            val hr = session.heartRate?.takeIf { it.hasUsableHrData() } ?: continue
            out += HeartRateDatedWorkout(date, hr)
        }
    }
    for (log in weight.logs) {
        val date = runCatching { LocalDate.parse(log.date) }.getOrNull() ?: continue
        for (workout in log.workouts) {
            val hr = workout.heartRate?.takeIf { it.hasUsableHrData() } ?: continue
            out += HeartRateDatedWorkout(date, hr)
        }
    }
    for (session in unified.sessions) {
        if (session.finishedAtEpochSeconds == null) continue
        val hr = session.heartRate?.takeIf { it.hasUsableHrData() } ?: continue
        val date = Instant.ofEpochSecond(session.finishedAtEpochSeconds).atZone(zoneId).toLocalDate()
        out += HeartRateDatedWorkout(date, hr)
    }
    return out.sortedBy { it.logDate }
}

fun collectHeartRateWorkoutsFromCardio(entries: List<DatedCardioSession>): List<HeartRateDatedWorkout> =
    entries.mapNotNull { dated ->
        dated.session.heartRate?.takeIf { it.hasUsableHrData() }?.let {
            HeartRateDatedWorkout(dated.logDate, it)
        }
    }

private fun CardioHrScaffolding.hasUsableHrData(): Boolean {
    val samples = samples
    if (samples.size >= 2) return true
    return avgBpm != null || maxBpm != null || minBpm != null
}

fun computeHeartRateProgressStats(
    workouts: List<HeartRateDatedWorkout>,
    zoneInputs: HeartRateZoneInputs,
): HeartRateProgressStats {
    if (workouts.isEmpty()) {
        return HeartRateProgressStats(
            workoutsWithHr = 0,
            totalZoneSeconds = IntArray(5),
            monthlyZoneSeconds = emptyList(),
            monthlyAvgBpm = emptyList(),
        )
    }
    val totalZone = IntArray(5)
    val monthZones = mutableMapOf<YearMonth, IntArray>()
    val monthAvgSum = mutableMapOf<YearMonth, Int>()
    val monthAvgCount = mutableMapOf<YearMonth, Int>()

    for (w in workouts) {
        val samples = w.heartRate.samples
        if (samples.size >= 2) {
            val zones = zoneDurationsSeconds(samples, zoneInputs)
            for (i in 0 until 5) totalZone[i] += zones[i]
            val ym = YearMonth.from(w.logDate)
            val bucket = monthZones.getOrPut(ym) { IntArray(5) }
            for (i in 0 until 5) bucket[i] += zones[i]
        }
        w.heartRate.avgBpm?.let { avg ->
            val ym = YearMonth.from(w.logDate)
            monthAvgSum[ym] = (monthAvgSum[ym] ?: 0) + avg
            monthAvgCount[ym] = (monthAvgCount[ym] ?: 0) + 1
        }
    }

    val sortedMonths = monthZones.keys.union(monthAvgSum.keys).sorted()
    return HeartRateProgressStats(
        workoutsWithHr = workouts.size,
        totalZoneSeconds = totalZone,
        monthlyZoneSeconds = sortedMonths.map { m ->
            HeartRateMonthlyZoneBucket(m, monthZones[m] ?: IntArray(5))
        },
        monthlyAvgBpm = sortedMonths.mapNotNull { m ->
            val count = monthAvgCount[m] ?: return@mapNotNull null
            if (count <= 0) return@mapNotNull null
            HeartRateMonthlyAvgBucket(m, (monthAvgSum[m]!! / count), count)
        },
    )
}
