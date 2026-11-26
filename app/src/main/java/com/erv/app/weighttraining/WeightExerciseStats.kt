package com.erv.app.weighttraining

import com.erv.app.data.BodyWeightUnit
import java.time.LocalDate
import java.util.Locale

/** What the exercise trend chart plots: est. 1RM for loaded lifts, else reps, else hold time. */
enum class WeightTrendMetric(val title: String) {
    EST_ONE_RM("Est. 1RM Trend"),
    MOST_REPS("Most Reps Trend"),
    LONGEST_HOLD("Longest Hold Trend"),
}

/** One workout's point on the trend: its best for [WeightTrendMetric], on its day. */
data class WeightTrendPoint(val date: LocalDate, val value: Double, val workoutId: String)

/**
 * The trend for [exerciseId], oldest first: each workout's best est. 1RM when the lift has ever
 * been loaded, else its most reps in a set, else its longest hold. Warm-ups never count.
 */
fun WeightLibraryState.exerciseTrend(exerciseId: String): Pair<WeightTrendMetric, List<WeightTrendPoint>>? {
    val perWorkout = allDatedWorkouts()
        .map { dated -> dated to WeightRecords.recordsOf(dated.workout, dated.logDate, exerciseId).associateBy { it.kind } }
        .filter { it.second.isNotEmpty() }
    if (perWorkout.isEmpty()) return null
    val metric = when {
        perWorkout.any { WeightRecordKind.EST_ONE_RM in it.second } -> WeightTrendMetric.EST_ONE_RM
        perWorkout.any { WeightRecordKind.MOST_REPS in it.second } -> WeightTrendMetric.MOST_REPS
        perWorkout.any { WeightRecordKind.LONGEST_HOLD in it.second } -> WeightTrendMetric.LONGEST_HOLD
        else -> return null
    }
    val kind = when (metric) {
        WeightTrendMetric.EST_ONE_RM -> WeightRecordKind.EST_ONE_RM
        WeightTrendMetric.MOST_REPS -> WeightRecordKind.MOST_REPS
        WeightTrendMetric.LONGEST_HOLD -> WeightRecordKind.LONGEST_HOLD
    }
    val points = perWorkout.mapNotNull { (dated, records) ->
        records[kind]?.let { WeightTrendPoint(dated.logDate, it.value, dated.workout.id) }
    }.sortedWith(compareBy({ it.date }, { it.workoutId }))
    return metric to points
}

/** "1:30" for a hold, from seconds. */
fun formatHoldClock(seconds: Int): String = "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)

/** The record's headline value, e.g. "268 lb", "245 lb × 2", "15 @ 135 lb", "1:30". */
fun WeightRecord.formatValue(loadUnit: BodyWeightUnit): String {
    val suffix = weightLoadUnitSuffix(loadUnit)
    fun load(kg: Double) = "${formatWeightLoadNumber(kg, loadUnit)} $suffix"
    return when (kind) {
        WeightRecordKind.EST_ONE_RM -> "${formatWeightLoadNumber(value, loadUnit).toWholeLoad()} $suffix"
        WeightRecordKind.TEN_REP_MAX ->
            if ((reps ?: 10) > 10) "${load(value)} × $reps" else load(value)
        WeightRecordKind.HEAVIEST -> reps?.let { "${load(value)} × $it" } ?: load(value)
        WeightRecordKind.BEST_VOLUME -> {
            val v = when (loadUnit) {
                BodyWeightUnit.KG -> value
                BodyWeightUnit.LB -> kgToPounds(value)
            }
            "${"%,.0f".format(Locale.US, v)} $suffix"
        }
        WeightRecordKind.MOST_REPS -> weightKg?.let { "${value.toInt()} @ ${load(it)}" } ?: "${value.toInt()} reps"
        WeightRecordKind.LONGEST_HOLD -> formatHoldClock(value.toInt())
    }
}

/** An est. 1RM shows whole numbers: 262.5 lb reads as 263 lb. */
private fun String.toWholeLoad(): String =
    toDoubleOrNull()?.let { if (it >= 20) "%.0f".format(Locale.US, it) else this } ?: this

/**
 * Sets as one short line, consecutive sets at the same weight joined:
 * "225 lb × 5, 5, 5 · 215 lb × 8", "12, 10, 8 reps", "1:30, 1:15 hold".
 * [warmups] true lists only the warm-ups, false only the working sets.
 */
fun WeightWorkoutEntry.compactSetsLine(loadUnit: BodyWeightUnit, warmups: Boolean): String? {
    hiitBlock?.let { b ->
        if (warmups) return null
        val w = b.weightKg?.let { " @ ${formatWeightLoadNumber(it, loadUnit)} ${weightLoadUnitSuffix(loadUnit)}" }.orEmpty()
        return "${b.intervals} intervals × ${b.workSeconds}s$w"
    }
    val chosen = sets.filter { it.isLogged() && it.isWarmup() == warmups }
    if (chosen.isEmpty()) return null
    val suffix = weightLoadUnitSuffix(loadUnit)
    val groups = mutableListOf<Pair<Double?, MutableList<WeightSet>>>()
    for (set in chosen) {
        val kg = set.weightKg?.takeIf { it > 0 }
        val last = groups.lastOrNull()
        if (last != null && last.first == kg) last.second += set else groups += kg to mutableListOf(set)
    }
    return groups.joinToString(" · ") { (kg, group) ->
        val timed = group.all { (it.durationSeconds ?: 0) > 0 && it.reps <= 0 }
        val amounts = group.joinToString(", ") { s ->
            if (timed) formatHoldClock(s.durationSeconds ?: 0) else s.reps.toString()
        }
        when {
            kg != null -> "${formatWeightLoadNumber(kg, loadUnit)} $suffix × $amounts"
            timed -> "$amounts hold"
            else -> "$amounts reps"
        }
    }
}
