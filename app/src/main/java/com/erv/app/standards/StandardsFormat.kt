package com.erv.app.standards

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Display text for standard values, targets and gaps, in the athlete's units ([metric] = kg / m / cm).
 * A port of the display half of `apps/web/web/src/lib/standardsFormat.ts`.
 */

private const val INCH_CM = 2.54
private const val FOOT_M = 0.3048
private const val KG_TO_LB = 2.2046226218

fun standardTierLabel(tier: StandardTier?): String = tier?.label ?: "Below Base"

fun formatStandardClock(totalSeconds: Double): String {
    val s = max(0, totalSeconds.roundToInt())
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

private fun trim2(n: Double): String {
    val r = (n * 100).roundToInt() / 100.0
    return if (r == floor(r)) r.toInt().toString() else r.toString()
}

private fun load(kg: Double, metric: Boolean): String =
    if (metric) "${kg.roundToInt()} kg" else "${(kg * KG_TO_LB).roundToInt()} lb"

private fun reps(n: Double): String {
    val r = n.roundToInt()
    return "$r ${if (r == 1) "rep" else "reps"}"
}

/** A value or target in its own unit, e.g. "1.5x BW", "12 reps", "3:15", "Height + 12 in". */
fun formatStandardValue(event: StandardEvent, value: Double, metric: Boolean): String {
    val m = event.measure
    return when (m.kind) {
        StandardMeasureKind.LIFT_MAX_BW -> "${trim2(value)}x BW"
        StandardMeasureKind.LIFT_MAX_LOAD -> load(value, metric)
        StandardMeasureKind.MAX_REPS, StandardMeasureKind.REPS_AT_BW -> reps(value)
        StandardMeasureKind.LONGEST_HOLD, StandardMeasureKind.FASTEST -> formatStandardClock(value)
        else -> when (m.unit) {
            StandardManualUnit.REPS -> "${value.roundToInt()} reps"
            StandardManualUnit.METERS ->
                if (metric) "${value.roundToInt()} m" else "${(value / FOOT_M).roundToInt()} ft"
            StandardManualUnit.CM_OVER_HEIGHT -> {
                val shown = if (metric) value.roundToInt() else (value / INCH_CM).roundToInt()
                val suffix = if (metric) "cm" else "in"
                if (shown == 0) "Your height" else "Height ${if (shown > 0) "+" else "−"} ${abs(shown)} $suffix"
            }
            else -> formatStandardClock(value)
        }
    }
}

/** "0.1x BW to Strong (about 20 lb)", "3 reps to Elite", "0:12 faster to Strong"; null at Elite. */
fun formatStandardGap(result: StandardEventResult, metric: Boolean, latestBodyWeightKg: Double?): String? {
    val gap = result.gapToNext ?: return null
    val next = result.nextTier ?: return null
    if (result.value == null) return null
    val to = next.label
    val m = result.event.measure
    return when (m.kind) {
        StandardMeasureKind.LIFT_MAX_BW -> {
            val extra = latestBodyWeightKg?.let { " (about ${load(gap * it, metric)})" } ?: ""
            "${trim2(max(gap, 0.01))}x BW to $to$extra"
        }
        StandardMeasureKind.LIFT_MAX_LOAD -> "${load(gap, metric)} to $to"
        StandardMeasureKind.MAX_REPS, StandardMeasureKind.REPS_AT_BW -> {
            val n = max(1, ceil(gap - 1e-6).toInt())
            "$n ${if (n == 1) "rep" else "reps"} to $to"
        }
        StandardMeasureKind.LONGEST_HOLD -> "${formatStandardClock(max(1.0, gap))} longer to $to"
        StandardMeasureKind.FASTEST -> "${formatStandardClock(max(1.0, gap))} faster to $to"
        else -> when (m.unit) {
            StandardManualUnit.CM_OVER_HEIGHT -> {
                val n = if (metric) ceil(gap).toInt() else ceil(gap / INCH_CM).toInt()
                "$n ${if (metric) "cm" else "in"} to $to"
            }
            StandardManualUnit.METERS ->
                if (metric) "${ceil(gap).toInt()} m to $to" else "${ceil(gap / FOOT_M).toInt()} ft to $to"
            StandardManualUnit.REPS -> "${ceil(gap).toInt()} to $to"
            else -> "${formatStandardClock(max(1.0, gap))} ${if (result.event.lowerIsBetter) "faster" else "longer"} to $to"
        }
    }
}

/** Why an event has no value yet. */
fun standardMissingLabel(result: StandardEventResult): String? = when (result.missing) {
    StandardMissing.BODYWEIGHT -> "Needs a weigh-in"
    StandardMissing.HEIGHT -> "Add your height on the web Profile page"
    StandardMissing.RESULT ->
        if (result.event.measure.kind == StandardMeasureKind.MANUAL) "Log a test result on the web" else "Nothing logged yet"
    null -> null
}

/** "Base 28:00 · Strong 24:00 · Elite 20:00" for an event with nothing scored. */
fun formatStandardTargets(result: StandardEventResult, metric: Boolean): String =
    StandardTier.entries.joinToString(" · ") { "${it.label} ${formatStandardValue(result.event, result.tiers[it], metric)}" }
