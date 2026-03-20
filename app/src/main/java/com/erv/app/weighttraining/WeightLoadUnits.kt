package com.erv.app.weighttraining

import com.erv.app.data.BodyWeightUnit
import java.time.LocalDate

fun WeightWorkoutSession.totalSetCount(): Int =
    entries.sumOf { it.sets.size }

fun WeightWorkoutSession.totalVolumeKg(): Double =
    entries.sumOf { e -> e.sets.sumOf { s -> (s.weightKg ?: 0.0) * s.reps } }

/** Matches [com.erv.app.data.UserPreferences] body-weight conversion. */
const val KG_PER_POUND: Double = 0.453592

fun poundsToKg(lb: Double): Double = lb * KG_PER_POUND

fun kgToPounds(kg: Double): Double = kg / KG_PER_POUND

fun formatWeightLoadNumber(kg: Double, unit: BodyWeightUnit): String {
    val v = when (unit) {
        BodyWeightUnit.KG -> kg
        BodyWeightUnit.LB -> kgToPounds(kg)
    }
    return if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format("%.1f", v)
}

fun weightLoadUnitSuffix(unit: BodyWeightUnit): String = when (unit) {
    BodyWeightUnit.KG -> "kg"
    BodyWeightUnit.LB -> "lb"
}

/** Parse user input in the chosen unit to stored kg, or `null` if blank/invalid. */
fun parseWeightInputToKg(raw: String, unit: BodyWeightUnit): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    val v = t.toDoubleOrNull() ?: return null
    return when (unit) {
        BodyWeightUnit.KG -> v
        BodyWeightUnit.LB -> poundsToKg(v)
    }
}

fun WeightWorkoutSession.totalVolumeLoadTimesReps(unit: BodyWeightUnit): Double =
    entries.sumOf { e ->
        e.sets.sumOf { s ->
            val kg = s.weightKg ?: 0.0
            val perRep = when (unit) {
                BodyWeightUnit.KG -> kg
                BodyWeightUnit.LB -> kgToPounds(kg)
            }
            perRep * s.reps
        }
    }

data class WeightActivityRow(val summaryLine: String)

private fun WeightWorkoutSession.elapsedSecondsForSummary(): Long {
    val a = startedAtEpochSeconds ?: return 0L
    val b = finishedAtEpochSeconds ?: return 0L
    return (b - a).coerceAtLeast(0L)
}

fun WeightWorkoutSession.dashboardSummaryLine(library: WeightLibraryState, unit: BodyWeightUnit): String {
    val src = when (source) {
        WeightWorkoutSource.LIVE -> "Live"
        WeightWorkoutSource.MANUAL -> "Manual"
    }
    val bits = mutableListOf(src)
    val elapsed = elapsedSecondsForSummary()
    if (elapsed > 0) {
        val m = (elapsed / 60).toInt()
        val s = (elapsed % 60).toInt()
        bits += "%d:%02d".format(m, s)
    }
    bits += "${totalSetCount()} sets"
    val vol = totalVolumeLoadTimesReps(unit)
    if (vol > 0.5) bits += "~${vol.toInt()} ${weightLoadUnitSuffix(unit)}×reps"
    val nameHint = entries.take(2).joinToString(", ") { e ->
        library.exerciseById(e.exerciseId)?.name ?: "Exercise"
    } + if (entries.size > 2) "…" else ""
    return bits.joinToString(" • ") + " — " + nameHint
}

fun WeightLibraryState.weightActivityRowsFor(date: LocalDate, displayUnit: BodyWeightUnit): List<WeightActivityRow> {
    val log = logFor(date) ?: return emptyList()
    val sorted = log.workouts.sortedWith(
        compareBy<WeightWorkoutSession> { it.startedAtEpochSeconds ?: it.finishedAtEpochSeconds ?: 0L }
    )
    return sorted.map { WeightActivityRow(it.dashboardSummaryLine(this, displayUnit)) }
}
