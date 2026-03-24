package com.erv.app.weighttraining

import com.erv.app.data.BodyWeightUnit
import java.time.LocalDate

fun formatRpeFieldForSets(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else String.format("%.1f", v)

/**
 * One line per set; matches the live workout collapsed set summary (reps, load, RPE).
 */
fun formatSetSummaryLine(
    set: WeightSet,
    setNumber: Int,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    /** When true (e.g. bodyweight / station exercises), weight is shown as added load. */
    weightIsAddedLoad: Boolean = false
): String {
    val repsPart = if (set.reps > 0) "${set.reps} reps" else "— reps"
    val weightPart = set.weightKg?.let { w ->
        val num = formatWeightLoadNumber(w, loadUnit)
        if (weightIsAddedLoad) " @ +$num $loadSuffix"
        else " @ $num $loadSuffix"
    }.orEmpty()
    val rpePart = set.rpe?.let { " · RPE ${formatRpeFieldForSets(it)}" }.orEmpty()
    return "Set $setNumber: $repsPart$weightPart$rpePart"
}

/** One-line summary for a completed interval block (live or manual log). */
fun formatHiitBlockSummaryLine(
    block: WeightHiitBlockLog,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    weightIsAddedLoad: Boolean = false
): String {
    val w = block.weightKg?.let { kg ->
        val num = formatWeightLoadNumber(kg, loadUnit)
        if (weightIsAddedLoad) " @ +$num $loadSuffix" else " @ $num $loadSuffix"
    }.orEmpty()
    val rpePart = block.rpe?.let { " · RPE ${formatRpeFieldForSets(it)}" }.orEmpty()
    return "${block.intervals}× ${block.workSeconds}s work / ${block.restSeconds}s rest$w$rpePart"
}

fun WeightWorkoutSession.totalSetCount(): Int =
    entries.sumOf { e ->
        e.hiitBlock?.intervals ?: e.sets.size
    }

fun WeightWorkoutSession.totalVolumeKg(): Double =
    entries.sumOf { e ->
        e.hiitBlock?.let { b -> (b.weightKg ?: 0.0) * b.intervals }
            ?: e.sets.sumOf { s -> (s.weightKg ?: 0.0) * s.reps }
    }

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
    entries.sumOf { e -> e.totalVolumeLoadTimesReps(unit) }

fun WeightWorkoutEntry.totalVolumeLoadTimesReps(unit: BodyWeightUnit): Double {
    hiitBlock?.let { b ->
        val kg = b.weightKg ?: 0.0
        val per = when (unit) {
            BodyWeightUnit.KG -> kg
            BodyWeightUnit.LB -> kgToPounds(kg)
        }
        return per * b.intervals
    }
    return sets.sumOf { s ->
        val kg = s.weightKg ?: 0.0
        val perRep = when (unit) {
            BodyWeightUnit.KG -> kg
            BodyWeightUnit.LB -> kgToPounds(kg)
        }
        perRep * s.reps
    }
}

data class WeightActivityExerciseBlock(
    /** Exercise name and equipment, e.g. `Bench press · Barbell`. */
    val titleLine: String,
    val setLines: List<String>
)

data class WeightActivityRow(
    /** Session summary: source, duration, set count, volume. */
    val headerLine: String,
    val exerciseBlocks: List<WeightActivityExerciseBlock>
)

private fun WeightWorkoutSession.elapsedSecondsForSummary(): Long {
    val a = startedAtEpochSeconds ?: return 0L
    val b = finishedAtEpochSeconds ?: return 0L
    return (b - a).coerceAtLeast(0L)
}

fun WeightWorkoutSession.activityHeaderLine(unit: BodyWeightUnit): String {
    val src = when (source) {
        WeightWorkoutSource.LIVE -> "Live"
        WeightWorkoutSource.MANUAL -> "Manual"
        WeightWorkoutSource.IMPORTED -> "Imported"
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
    return bits.joinToString(" • ")
}

fun WeightWorkoutSession.buildActivityRow(library: WeightLibraryState, unit: BodyWeightUnit): WeightActivityRow {
    val loadSuffix = weightLoadUnitSuffix(unit)
    val blocks = entries.map { entry ->
        val ex = library.exerciseById(entry.exerciseId)
        val name = ex?.name ?: "Exercise"
        val equipSuffix = ex?.equipment?.displayLabel()?.let { " · $it" }.orEmpty()
        val titleLine = "$name$equipSuffix"
        val weightIsAddedLoad = ex?.equipment == WeightEquipment.OTHER
        val setLines = entry.hiitBlock?.let { b ->
            listOf(formatHiitBlockSummaryLine(b, unit, loadSuffix, weightIsAddedLoad))
        } ?: entry.sets.mapIndexed { idx, set ->
            formatSetSummaryLine(set, idx + 1, unit, loadSuffix, weightIsAddedLoad)
        }
        WeightActivityExerciseBlock(titleLine = titleLine, setLines = setLines)
    }
    return WeightActivityRow(
        headerLine = activityHeaderLine(unit),
        exerciseBlocks = blocks
    )
}

fun WeightLibraryState.weightActivityRowsFor(date: LocalDate, displayUnit: BodyWeightUnit): List<WeightActivityRow> {
    val log = logFor(date) ?: return emptyList()
    val sorted = log.workouts.sortedWith(
        compareBy<WeightWorkoutSession> { it.startedAtEpochSeconds ?: it.finishedAtEpochSeconds ?: 0L }
    )
    return sorted.map { it.buildActivityRow(this, displayUnit) }
}
