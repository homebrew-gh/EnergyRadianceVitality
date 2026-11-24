package com.erv.app.weighttraining

import com.erv.app.data.BarbellOwnership
import com.erv.app.data.BodyWeightUnit
import com.erv.app.data.EquipmentCatalogKind
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.data.StandardBarbellType
import com.erv.app.data.kgToLb
import com.erv.app.data.lbToKg
import kotlin.math.roundToLong

/*
 * Plate calculator: which plates to put on a bar for a target weight, using the bars and plates
 * stored in `erv/equipment`.
 *
 * Keep this in step with the web copy (`apps/web/web/src/lib/plateCalculator.ts`). Both are checked
 * against `shared/test-cases/plate_calculator.json`, so the phone and the builder always show the
 * same plates.
 *
 * All arithmetic runs in the unit you lift in, in whole hundredths. Weights are stored in kg, and a
 * 45 lb plate is 20.4117 kg, so matching on stored kg would miss.
 */

/** How plates go on: both sleeves, one end (landmine, T-bar row), or not loaded at all (rollout). */
enum class PlateLoadingStyle { BOTH_ENDS, ONE_END, NONE }

/** Plates of one weight, in the unit you lift in. A pair is one plate for each sleeve. */
data class PlateStack(val weight: Double, val pairCount: Int)

/** A loadable total and its plates, per side (or on the loaded end), heaviest first. */
data class PlateLoadOption(val total: Double, val plates: List<Double>)

data class PlateLoadResult(
    /** Plates that make the target exactly, or null when it cannot be loaded. */
    val exact: PlateLoadOption?,
    /** Nearest loadable total below the target. */
    val below: PlateLoadOption?,
    /** Nearest loadable total above the target. */
    val above: PlateLoadOption?,
)

private val EMPTY_RESULT = PlateLoadResult(null, null, null)

private fun toHundredths(v: Double): Long = (v * 100).roundToLong()
private fun fromHundredths(v: Long): Double = v / 100.0

private class Combo(val counts: IntArray, val plateCount: Int) {
    /** Fewer plates wins; on a tie, more of the heavier plates wins. */
    fun isBetterThan(other: Combo): Boolean {
        if (plateCount != other.plateCount) return plateCount < other.plateCount
        for (i in counts.indices) {
            if (counts[i] != other.counts[i]) return counts[i] > other.counts[i]
        }
        return false
    }
}

/**
 * Bounded search over every combination of the plates you own (not largest-first, which gets
 * stuck on a real inventory). Prefers an exact match, then the fewest plates.
 */
fun calculatePlateLoad(
    target: Double,
    bar: Double,
    plates: List<PlateStack>,
    loading: PlateLoadingStyle = PlateLoadingStyle.BOTH_ENDS,
): PlateLoadResult {
    if (loading == PlateLoadingStyle.NONE || !target.isFinite() || target <= 0) return EMPTY_RESULT

    val merged = linkedMapOf<Long, Int>()
    for (stack in plates) {
        val w = toHundredths(stack.weight)
        if (w <= 0 || stack.pairCount <= 0) continue
        merged[w] = (merged[w] ?: 0) + stack.pairCount
    }
    val weights = merged.keys.sortedDescending()
    val perPair = if (loading == PlateLoadingStyle.ONE_END) 2 else 1
    val maxCounts = weights.map { perPair * merged.getValue(it) }
    val sides = if (loading == PlateLoadingStyle.ONE_END) 1 else 2

    var reachable = hashMapOf(0L to Combo(IntArray(weights.size), 0))
    weights.forEachIndexed { i, w ->
        val next = HashMap<Long, Combo>()
        for ((sum, combo) in reachable) {
            for (k in 0..maxCounts[i]) {
                val counts = combo.counts.copyOf()
                counts[i] = k
                val candidate = Combo(counts, combo.plateCount + k)
                val key = sum + k * w
                val existing = next[key]
                if (existing == null || candidate.isBetterThan(existing)) next[key] = candidate
            }
        }
        reachable = next
    }

    val barH = toHundredths(bar)
    val targetH = toHundredths(target)
    fun option(sum: Long, combo: Combo) = PlateLoadOption(
        total = fromHundredths(barH + sides * sum),
        plates = weights.flatMapIndexed { i, w -> List(combo.counts[i]) { fromHundredths(w) } },
    )

    var exact: PlateLoadOption? = null
    var belowSum: Long? = null
    var aboveSum: Long? = null
    for ((sum, combo) in reachable) {
        val total = barH + sides * sum
        when {
            total == targetH -> exact = option(sum, combo)
            total < targetH -> if (belowSum == null || sum > belowSum) belowSum = sum
            else -> if (aboveSum == null || sum < aboveSum) aboveSum = sum
        }
    }
    return PlateLoadResult(
        exact = exact,
        below = belowSum?.let { option(it, reachable.getValue(it)) },
        above = aboveSum?.let { option(it, reachable.getValue(it)) },
    )
}

// --- Equipment ---------------------------------------------------------------------------------

/** A bar you can pick in the calculator, with its weight in the unit you lift in. */
data class PlateCalculatorBar(
    val id: String,
    val label: String,
    val weight: Double,
    val barType: StandardBarbellType,
)

/** A stored kg weight in the unit you lift in, to the hundredth (so 20.4117 kg reads 45 lb). */
fun nominalWeight(kg: Double, unit: BodyWeightUnit): Double =
    fromHundredths(toHundredths(if (unit == BodyWeightUnit.LB) kgToLb(kg) else kg))

/** The unit you lift in back to stored kg. */
fun nominalWeightToKg(weight: Double, unit: BodyWeightUnit): Double =
    if (unit == BodyWeightUnit.LB) lbToKg(weight) else weight

/**
 * What lifters count each bar as. An Olympic bar is stored as 20 kg (44.1 lb) but is counted as
 * 45 lb, so pound answers use these nominal values (matching the bar labels) instead of converting.
 */
fun BarbellOwnership.nominalBarWeight(unit: BodyWeightUnit): Double {
    val lb = unit == BodyWeightUnit.LB
    return when (barType) {
        StandardBarbellType.OLYMPIC_MENS -> if (lb) 45.0 else 20.0
        StandardBarbellType.OLYMPIC_WOMENS -> if (lb) 33.0 else 15.0
        StandardBarbellType.TRAINING_10KG -> if (lb) 25.0 else 10.0
        StandardBarbellType.EZ_CURL -> if (lb) 18.0 else 8.0
        StandardBarbellType.TRAP_HEX_20 -> if (lb) 45.0 else 20.0
        StandardBarbellType.TRAP_HEX_25 -> if (lb) 55.0 else 25.0
        StandardBarbellType.OPEN_TRAP_BAR -> if (lb) 55.0 else 25.0
        StandardBarbellType.STANDARD_1INCH -> if (lb) 15.0 else 7.0
        StandardBarbellType.CUSTOM ->
            customWeightKg?.takeIf { it > 0 }?.let { nominalWeight(it, unit) } ?: if (lb) 45.0 else 20.0
    }
}

private fun StandardBarbellType.shortLabel(): String = when (this) {
    StandardBarbellType.OLYMPIC_MENS -> "Olympic bar"
    StandardBarbellType.OLYMPIC_WOMENS -> "Women's Olympic bar"
    StandardBarbellType.TRAINING_10KG -> "Training bar"
    StandardBarbellType.EZ_CURL -> "EZ curl bar"
    StandardBarbellType.TRAP_HEX_20, StandardBarbellType.TRAP_HEX_25 -> "Trap bar"
    StandardBarbellType.OPEN_TRAP_BAR -> "Open trap bar"
    StandardBarbellType.STANDARD_1INCH -> "Standard 1\" bar"
    StandardBarbellType.CUSTOM -> "Custom bar"
}

private fun formatAmount(v: Double): String = formatDecimalForField(v)

data class PlateCalculatorBars(val bars: List<PlateCalculatorBar>, val assumed: Boolean)

data class PlateCalculatorPlates(val plates: List<PlateStack>, val assumed: Boolean)

/** Bars from your equipment. When you have none, a standard Olympic bar is assumed. */
fun plateCalculatorBars(equipment: List<OwnedEquipmentItem>, unit: BodyWeightUnit): PlateCalculatorBars {
    val suffix = weightLoadUnitSuffix(unit)
    val bars = equipment.mapNotNull { item ->
        val b = item.barbell
        if (item.catalogKind != EquipmentCatalogKind.BARBELL || b == null) return@mapNotNull null
        val weight = b.nominalBarWeight(unit)
        val name = if (b.barType == StandardBarbellType.CUSTOM) {
            b.customName?.trim()?.takeIf { it.isNotEmpty() } ?: "Custom bar"
        } else {
            b.barType.shortLabel()
        }
        PlateCalculatorBar(item.id, "$name (${formatAmount(weight)} $suffix)", weight, b.barType)
    }
    if (bars.isNotEmpty()) return PlateCalculatorBars(bars, assumed = false)
    val weight = if (unit == BodyWeightUnit.LB) 45.0 else 20.0
    return PlateCalculatorBars(
        listOf(
            PlateCalculatorBar(
                "standard-olympic",
                "Olympic bar (${formatAmount(weight)} $suffix)",
                weight,
                StandardBarbellType.OLYMPIC_MENS,
            ),
        ),
        assumed = true,
    )
}

/** A typical gym's plates, used when you have not entered any. */
private val STANDARD_PLATES_LB = listOf(
    PlateStack(45.0, 8), PlateStack(35.0, 2), PlateStack(25.0, 2),
    PlateStack(10.0, 2), PlateStack(5.0, 2), PlateStack(2.5, 2),
)

private val STANDARD_PLATES_KG = listOf(
    PlateStack(25.0, 8), PlateStack(20.0, 2), PlateStack(15.0, 2), PlateStack(10.0, 2),
    PlateStack(5.0, 2), PlateStack(2.5, 2), PlateStack(1.25, 2),
)

/** Plates from your equipment, merged across entries. When you have none, a standard set is assumed. */
fun plateCalculatorPlates(equipment: List<OwnedEquipmentItem>, unit: BodyWeightUnit): PlateCalculatorPlates {
    val merged = linkedMapOf<Double, Int>()
    for (item in equipment) {
        val p = item.plates
        if (item.catalogKind != EquipmentCatalogKind.PLATES || p == null) continue
        for (entry in p.resolvedPlatePairs()) {
            if (entry.weightKg <= 0) continue
            val w = nominalWeight(entry.weightKg, unit)
            merged[w] = (merged[w] ?: 0) + entry.pairCount
        }
    }
    if (merged.isNotEmpty()) {
        return PlateCalculatorPlates(
            merged.map { (weight, pairs) -> PlateStack(weight, pairs) }.sortedByDescending { it.weight },
            assumed = false,
        )
    }
    return PlateCalculatorPlates(
        if (unit == BodyWeightUnit.LB) STANDARD_PLATES_LB else STANDARD_PLATES_KG,
        assumed = true,
    )
}

// --- Exercises ---------------------------------------------------------------------------------

private val T_BAR = Regex("""\bt-?bar\b""")
private val EZ_BAR = Regex("""\bez\b|ez-bar|skull crusher""")
private val TRAP_BAR = Regex("""trap bar|hex bar|trap-bar|hex-bar""")
private val TRAP_TYPES = setOf(
    StandardBarbellType.OPEN_TRAP_BAR,
    StandardBarbellType.TRAP_HEX_25,
    StandardBarbellType.TRAP_HEX_20,
)

/** How a barbell exercise is loaded, or null when it is not a barbell exercise. */
fun WeightExercise.plateLoading(): PlateLoadingStyle? {
    if (equipment != WeightEquipment.BARBELL) return null
    val key = "$name $id".lowercase()
    return when {
        "rollout" in key -> PlateLoadingStyle.NONE
        "landmine" in key || T_BAR.containsMatchIn(key) || "tbar" in key -> PlateLoadingStyle.ONE_END
        else -> PlateLoadingStyle.BOTH_ENDS
    }
}

/**
 * The bar to start with: the one you last used for this exercise if you still own it, an EZ bar for
 * curls and skull crushers named for it, a trap bar for trap or hex bar lifts, otherwise an Olympic bar.
 */
fun defaultBarForExercise(
    exerciseName: String?,
    bars: List<PlateCalculatorBar>,
    rememberedBarId: String? = null,
): PlateCalculatorBar? {
    if (bars.isEmpty()) return null
    rememberedBarId?.let { id -> bars.firstOrNull { it.id == id }?.let { return it } }
    val name = exerciseName.orEmpty().lowercase()
    if (EZ_BAR.containsMatchIn(name)) {
        bars.firstOrNull { it.barType == StandardBarbellType.EZ_CURL }?.let { return it }
    }
    if (TRAP_BAR.containsMatchIn(name)) {
        TRAP_TYPES.firstNotNullOfOrNull { type -> bars.firstOrNull { it.barType == type } }?.let { return it }
    }
    return bars.firstOrNull { it.barType == StandardBarbellType.OLYMPIC_MENS }
        ?: bars.firstOrNull { it.barType != StandardBarbellType.EZ_CURL && it.barType !in TRAP_TYPES }
        ?: bars.first()
}

/** "Per side: 45 · 25 · 5", "On the end: 25" for one-end loading, or "Empty bar". */
fun formatPlateList(plates: List<Double>, loading: PlateLoadingStyle): String {
    if (plates.isEmpty()) return "Empty bar"
    val list = plates.joinToString(" · ") { formatAmount(it) }
    return if (loading == PlateLoadingStyle.ONE_END) "On the end: $list" else "Per side: $list"
}
