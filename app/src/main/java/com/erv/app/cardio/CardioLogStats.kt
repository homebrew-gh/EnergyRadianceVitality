package com.erv.app.cardio

import java.time.YearMonth

/** Best-effort distance for one logged session (rollup or sum of segment distances). */
fun CardioSession.totalLoggedDistanceMeters(): Double? {
    val top = distanceMeters?.takeIf { it > 1.0 && it.isFinite() }
    if (top != null) return top
    if (segments.isEmpty()) return null
    val sum = segments.sumOf { seg ->
        seg.distanceMeters?.takeIf { it > 1.0 && it.isFinite() } ?: 0.0
    }
    return sum.takeIf { it > 1.0 }
}

private fun CardioActivitySnapshot.statsIdentityKey(): String = when {
    customTypeId != null -> "c:$customTypeId"
    builtin != null -> "b:${builtin.name}"
    else -> "l:$displayLabel"
}

private fun collectActivityKeys(session: CardioSession): Set<String> {
    val keys = mutableSetOf<String>()
    keys.add(session.activity.statsIdentityKey())
    session.segments.forEach { keys.add(it.activity.statsIdentityKey()) }
    return keys
}

data class CardioMonthlyWorkoutBucket(val yearMonth: YearMonth, val workoutCount: Int)

data class CardioMonthlyDistanceBucket(val yearMonth: YearMonth, val distanceMeters: Double)

data class CardioLogAggregatedStats(
    val workoutCount: Int,
    val activeDays: Int,
    val totalDurationMinutes: Long,
    val totalDistanceMeters: Double,
    val workoutsWithDistance: Int,
    val estimatedKcalTotal: Double?,
    val distinctActivityTypeCount: Int,
    /** Display label → number of sessions where that activity appears as primary [CardioSession.activity]. */
    val primaryActivityCounts: List<Pair<String, Int>>,
    val monthlyWorkouts: List<CardioMonthlyWorkoutBucket>,
    val monthlyDistanceMeters: List<CardioMonthlyDistanceBucket>,
)

fun computeCardioLogStats(entries: List<DatedCardioSession>): CardioLogAggregatedStats {
    if (entries.isEmpty()) {
        return CardioLogAggregatedStats(
            workoutCount = 0,
            activeDays = 0,
            totalDurationMinutes = 0L,
            totalDistanceMeters = 0.0,
            workoutsWithDistance = 0,
            estimatedKcalTotal = null,
            distinctActivityTypeCount = 0,
            primaryActivityCounts = emptyList(),
            monthlyWorkouts = emptyList(),
            monthlyDistanceMeters = emptyList(),
        )
    }

    val distinctDays = entries.map { it.logDate }.toSet()
    var totalDuration = 0L
    var totalDist = 0.0
    var withDistance = 0
    var kcalSum = 0.0
    var kcalAny = false
    val allActivityKeys = mutableSetOf<String>()
    val primaryLabelCounts = mutableMapOf<String, Int>()
    val monthWorkouts = mutableMapOf<YearMonth, Int>()
    val monthDistance = mutableMapOf<YearMonth, Double>()

    for (dated in entries) {
        val s = dated.session
        totalDuration += s.durationMinutes.coerceAtLeast(0)
        s.totalLoggedDistanceMeters()?.let { d ->
            totalDist += d
            withDistance++
            val ym = YearMonth.from(dated.logDate)
            monthDistance[ym] = (monthDistance[ym] ?: 0.0) + d
        }
        s.estimatedKcal?.takeIf { it > 0 && it.isFinite() }?.let {
            kcalSum += it
            kcalAny = true
        }
        allActivityKeys.addAll(collectActivityKeys(s))
        val label = s.activity.displayLabel
        primaryLabelCounts[label] = (primaryLabelCounts[label] ?: 0) + 1
        val ym = YearMonth.from(dated.logDate)
        monthWorkouts[ym] = (monthWorkouts[ym] ?: 0) + 1
    }

    val topPrimary = primaryLabelCounts.entries
        .sortedByDescending { it.value }
        .take(8)
        .map { it.key to it.value }

    val sortedMonths = monthWorkouts.keys.union(monthDistance.keys).sorted()
    val monthlyW = sortedMonths.map { m ->
        CardioMonthlyWorkoutBucket(m, monthWorkouts[m] ?: 0)
    }
    val monthlyD = sortedMonths.map { m ->
        CardioMonthlyDistanceBucket(m, monthDistance[m] ?: 0.0)
    }

    return CardioLogAggregatedStats(
        workoutCount = entries.size,
        activeDays = distinctDays.size,
        totalDurationMinutes = totalDuration,
        totalDistanceMeters = totalDist,
        workoutsWithDistance = withDistance,
        estimatedKcalTotal = if (kcalAny) kcalSum else null,
        distinctActivityTypeCount = allActivityKeys.size,
        primaryActivityCounts = topPrimary,
        monthlyWorkouts = monthlyW,
        monthlyDistanceMeters = monthlyD,
    )
}

fun formatCardioTotalDuration(minutes: Long): String {
    if (minutes <= 0L) return "0 min"
    val h = minutes / 60
    val m = (minutes % 60).toInt()
    return when {
        h <= 0L -> "$m min"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
