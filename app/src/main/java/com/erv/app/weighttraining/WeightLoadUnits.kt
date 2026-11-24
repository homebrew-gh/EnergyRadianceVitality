package com.erv.app.weighttraining

import com.erv.app.data.BodyWeightUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

fun formatRpeFieldForSets(v: Double): String = formatDecimalForField(v, maxDecimals = 1)

/**
 * Formats a number for a text field or summary: at most [maxDecimals] places, trailing zeros
 * dropped, always with a `.` separator regardless of locale (so it parses back with
 * [parseDecimalInput]). `145.00000001` becomes `145`, `1.25` stays `1.25`.
 */
fun formatDecimalForField(value: Double, maxDecimals: Int = 2): String {
    if (!value.isFinite()) return ""
    return BigDecimal.valueOf(value)
        .setScale(maxDecimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/** Parses typed decimal text, accepting `,` as the separator (some keyboards send it). */
fun parseDecimalInput(raw: String): Double? {
    val t = raw.trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/**
 * True when the text being typed in a numeric field ([draft], e.g. `2.` or `1.25`) already stands
 * for the stored value's text ([stored]), so the field should keep showing the draft instead of
 * replacing it. Both blank or both unparseable (e.g. a lone `.`) also count as a match.
 */
fun numericTextMatches(draft: String, stored: String): Boolean {
    val a = parseDecimalInput(draft)
    val b = parseDecimalInput(stored)
    if (a == null || b == null) return a == null && b == null
    return abs(a - b) < NUMERIC_DRAFT_TOLERANCE
}

/** Half a unit in the last displayed place (2 decimals), plus float slack. */
private const val NUMERIC_DRAFT_TOLERANCE = 0.0051

/** Hold duration as `45s` (under a minute) or `M:SS` (e.g. `1:30`). */
fun formatHoldDuration(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * One line per set; matches the live workout collapsed set summary (reps, load, RPE).
 * Time-based sets (e.g. planks) show the hold duration in place of reps.
 */
fun formatSetSummaryLine(
    set: WeightSet,
    setNumber: Int,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    /** When true (e.g. bodyweight / station exercises), weight is shown as added load. */
    weightIsAddedLoad: Boolean = false
): String = "Set $setNumber: ${formatSetValueLine(set, loadUnit, loadSuffix, weightIsAddedLoad)}"

/** The reps/load/RPE portion of a set summary, without any "Set N" prefix. */
fun formatSetValueLine(
    set: WeightSet,
    loadUnit: BodyWeightUnit,
    loadSuffix: String,
    /** When true (e.g. bodyweight / station exercises), weight is shown as added load. */
    weightIsAddedLoad: Boolean = false
): String {
    val duration = set.durationSeconds?.takeIf { it > 0 }
    val repsPart = when {
        duration != null -> "${formatHoldDuration(duration)} hold"
        set.reps > 0 -> "${set.reps} reps"
        else -> "— reps"
    }
    val weightPart = set.weightKg?.let { w ->
        val num = formatWeightLoadNumber(w, loadUnit)
        if (weightIsAddedLoad) " @ +$num $loadSuffix"
        else " @ $num $loadSuffix"
    }.orEmpty()
    val rpePart = set.rpe?.let { " · RPE ${formatRpeFieldForSets(it)}" }.orEmpty()
    return "$repsPart$weightPart$rpePart"
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
    return formatDecimalForField(v)
}

fun weightLoadUnitSuffix(unit: BodyWeightUnit): String = when (unit) {
    BodyWeightUnit.KG -> "kg"
    BodyWeightUnit.LB -> "lb"
}

/** Parse user input in the chosen unit to stored kg, or `null` if blank/invalid. */
fun parseWeightInputToKg(raw: String, unit: BodyWeightUnit): Double? {
    val v = parseDecimalInput(raw) ?: return null
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
    val exerciseBlocks: List<WeightActivityExerciseBlock>,
    val session: WeightWorkoutSession
)

fun WeightWorkoutSession.resolvedDurationSeconds(): Int? =
    durationSeconds?.takeIf { it > 0 }
        ?: run {
            val a = startedAtEpochSeconds ?: return@run null
            val b = finishedAtEpochSeconds ?: return@run null
            (b - a).toInt().coerceAtLeast(0).takeIf { it > 0 }
        }

fun WeightWorkoutSession.activityHeaderLine(unit: BodyWeightUnit): String {
    val src = when (source) {
        WeightWorkoutSource.LIVE -> "Live"
        WeightWorkoutSource.MANUAL -> "Manual"
        WeightWorkoutSource.IMPORTED -> "Imported"
    }
    val bits = mutableListOf(src)
    val elapsed = resolvedDurationSeconds()?.toLong() ?: 0L
    if (elapsed > 0) {
        val m = (elapsed / 60).toInt()
        val s = (elapsed % 60).toInt()
        bits += "%d:%02d".format(m, s)
    }
    bits += "${totalSetCount()} sets"
    val vol = totalVolumeLoadTimesReps(unit)
    if (vol > 0.5) bits += "~${vol.toInt()} ${weightLoadUnitSuffix(unit)}×reps"
    estimatedKcal?.takeIf { it > 0.5 }?.let { bits += "~${it.toInt()} kcal" }
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
        exerciseBlocks = blocks,
        session = this
    )
}

fun WeightLibraryState.weightActivityRowsFor(date: LocalDate, displayUnit: BodyWeightUnit): List<WeightActivityRow> {
    val log = logFor(date) ?: return emptyList()
    val sorted = log.workouts.sortedWith(
        compareBy<WeightWorkoutSession> { it.startedAtEpochSeconds ?: it.finishedAtEpochSeconds ?: 0L }
    )
    return sorted.map { it.buildActivityRow(this, displayUnit) }
}
