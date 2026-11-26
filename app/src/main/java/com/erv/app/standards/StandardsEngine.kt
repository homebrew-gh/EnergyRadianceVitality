package com.erv.app.standards

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Scores a fitness standard from lift sets, cardio sessions, weigh-ins and logged test results.
 * A port of `apps/web/web/src/lib/standards.ts`; keep the two in step so the phone and the web
 * show the same tiers.
 */

data class StandardProfile(
    val ageYears: Int? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
)

/** One working set (warm-ups and interval blocks already left out). */
data class StandardLiftSet(
    val date: String,
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double?,
    val durationSeconds: Int?,
)

/** One single-activity cardio session in a record group (RUN, RUCK, ROW, ...). */
data class StandardCardioSession(
    val date: String,
    val group: String,
    val groupLabel: String,
    val distanceMeters: Double,
    val seconds: Double,
    val packKg: Double?,
)

data class StandardBodyWeight(val date: String, val weightKg: Double)

data class StandardInputs(
    val liftSets: List<StandardLiftSet> = emptyList(),
    val cardioSessions: List<StandardCardioSession> = emptyList(),
    val bodyWeights: List<StandardBodyWeight> = emptyList(),
    val profile: StandardProfile = StandardProfile(),
    /** Exercise id to name, for matching custom exercises by name. */
    val exerciseNames: Map<String, String> = emptyMap(),
    val testResults: List<StoredStandardResult> = emptyList(),
)

enum class StandardValueSource(val label: String) {
    ESTIMATED("Est."),
    LOGGED("Logged"),
    TESTED("Tested"),
}

enum class StandardMissing { BODYWEIGHT, HEIGHT, RESULT }

data class StandardEventResult(
    val event: StandardEvent,
    val tiers: StandardTierValues,
    val value: Double?,
    val source: StandardValueSource?,
    val date: String?,
    val note: String?,
    val missing: StandardMissing?,
    val tier: StandardTier?,
    /** 0 to 3 along the rings: 1 = Base, 2 = Strong, 3 = Elite. */
    val score: Double,
    val nextTier: StandardTier?,
    /** How far from the next tier, in the canonical unit (always positive). */
    val gapToNext: Double?,
)

data class StandardResult(
    val standard: FitnessStandard,
    val events: List<StandardEventResult>,
    /** The highest tier every scored event meets; null when one is below Base or none are scored. */
    val overallTier: StandardTier?,
    val scoredCount: Int,
    val tierCounts: Map<StandardTier, Int>,
)

private const val EPS = 1e-6
private const val EST_ONE_RM_MAX_REPS = 12
private const val MILE = 1609.344

/** Epley, the same as the web app: a single is its own weight, long sets give no estimate. */
internal fun epleyOneRmKg(weightKg: Double, reps: Int): Double? {
    if (reps <= 0 || weightKg <= 0.0 || reps > EST_ONE_RM_MAX_REPS) return null
    if (reps == 1) return weightKg
    return weightKg * (1.0 + reps / 30.0)
}

/** Variant targets for the profile, or the event's own targets. */
fun tiersFor(event: StandardEvent, profile: StandardProfile): StandardTierValues {
    for (v in event.variants) {
        if (v.sex != null && v.sex != profile.sex) continue
        if (v.minAge != null && (profile.ageYears == null || profile.ageYears < v.minAge)) continue
        if (v.maxAge != null && (profile.ageYears == null || profile.ageYears > v.maxAge)) continue
        return v.tiers
    }
    return event.tiers
}

private fun meets(value: Double, target: Double, lowerIsBetter: Boolean): Boolean =
    if (lowerIsBetter) value <= target + EPS else value >= target - EPS

fun standardScore(value: Double, tiers: StandardTierValues, lowerIsBetter: Boolean): Double {
    val steps = listOf(tiers.base, tiers.strong, tiers.elite)
    if (!lowerIsBetter) {
        if (value < tiers.base) return max(0.0, value / tiers.base)
        for (i in 0 until 2) {
            val lo = steps[i]
            val hi = steps[i + 1]
            if (value < hi) return i + 1 + (value - lo) / (hi - lo)
        }
        return 3.0
    }
    if (value > tiers.base) return max(0.0, tiers.base / value)
    for (i in 0 until 2) {
        val lo = steps[i]
        val hi = steps[i + 1]
        if (value > hi) return i + 1 + (lo - value) / (lo - hi)
    }
    return 3.0
}

fun tierOf(value: Double, tiers: StandardTierValues, lowerIsBetter: Boolean): StandardTier? {
    var tier: StandardTier? = null
    for (t in StandardTier.entries) if (meets(value, tiers[t], lowerIsBetter)) tier = t
    return tier
}

/** Bodyweight on [date]: the last weigh-in on or before it, else the first one after. */
fun bodyWeightOn(entries: List<StandardBodyWeight>, date: String): Double? {
    val before = entries.filter { it.date <= date }.maxByOrNull { it.date }
    if (before != null) return before.weightKg
    return entries.minByOrNull { it.date }?.weightKg
}

private data class Candidate(val value: Double, val source: StandardValueSource, val date: String, val note: String?)

private fun rank(s: StandardValueSource): Int = when (s) {
    StandardValueSource.TESTED -> 2
    StandardValueSource.LOGGED -> 1
    StandardValueSource.ESTIMATED -> 0
}

private fun better(a: Candidate, b: Candidate?, lowerIsBetter: Boolean): Boolean {
    if (b == null) return true
    if (abs(a.value - b.value) < EPS) return rank(a.source) > rank(b.source)
    return if (lowerIsBetter) a.value < b.value else a.value > b.value
}

private fun matchingExerciseIds(event: StandardEvent, inputs: StandardInputs): Set<String> {
    val ids = event.exerciseIds.toMutableSet()
    val pattern = event.exerciseNamePattern
    if (!pattern.isNullOrBlank()) {
        val re = try {
            Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            null
        }
        if (re != null) inputs.exerciseNames.forEach { (id, name) -> if (re.containsMatchIn(name)) ids += id }
    }
    return ids
}

private data class Load(val kg: Double, val estimated: Boolean)

/** Loads one set is good for at [reps] reps: the set itself, and an Epley estimate when it says more. */
private fun loadsForReps(set: StandardLiftSet, reps: Int): List<Load> {
    val kg = set.weightKg ?: 0.0
    if (kg <= 0.0 || set.reps <= 0) return emptyList()
    val out = mutableListOf<Load>()
    if (set.reps >= reps) out += Load(kg, false)
    if (set.reps != reps) {
        val e1rm = epleyOneRmKg(kg, set.reps)
        if (e1rm != null) {
            val kgAtReps = if (reps == 1) e1rm else e1rm / (1 + reps / 30.0)
            if (kgAtReps > kg || set.reps < reps) out += Load(kgAtReps, true)
        }
    }
    return out
}

private fun distanceLabel(meters: Double): String {
    val miles = meters / MILE
    return if (miles >= 0.95) "${String.format(Locale.US, "%.1f", miles)} mi" else "${meters.roundToInt()} m"
}

private fun liftCandidates(event: StandardEvent, inputs: StandardInputs): Pair<List<Candidate>, StandardMissing?> {
    val m = event.measure
    var ids = matchingExerciseIds(event, inputs)
    var sets = inputs.liftSets.filter { it.exerciseId in ids }
    var fallbackNote: String? = null
    if (sets.isEmpty() && event.fallbackExerciseIds.isNotEmpty()) {
        ids = event.fallbackExerciseIds.toSet()
        sets = inputs.liftSets.filter { it.exerciseId in ids }
        if (sets.isNotEmpty()) {
            val name = inputs.exerciseNames[event.fallbackExerciseIds.first()] ?: "A related lift"
            fallbackNote = "$name used in its place"
        }
    }
    val out = mutableListOf<Candidate>()
    var neededBodyweight = false
    for (set in sets) {
        val kg = set.weightKg ?: 0.0
        when (m.kind) {
            StandardMeasureKind.LIFT_MAX_BW, StandardMeasureKind.LIFT_MAX_LOAD -> {
                val loads = loadsForReps(set, m.reps ?: 1)
                if (loads.isEmpty()) continue
                var divisor = 1.0
                if (m.kind == StandardMeasureKind.LIFT_MAX_BW) {
                    val bw = bodyWeightOn(inputs.bodyWeights, set.date)
                    if (bw == null) {
                        neededBodyweight = true
                        continue
                    }
                    divisor = bw
                }
                loads.forEach { load ->
                    out += Candidate(
                        load.kg / divisor,
                        if (load.estimated) StandardValueSource.ESTIMATED else StandardValueSource.LOGGED,
                        set.date,
                        fallbackNote,
                    )
                }
            }
            StandardMeasureKind.REPS_AT_BW -> {
                if (set.reps <= 0 || kg <= 0.0) continue
                val bw = bodyWeightOn(inputs.bodyWeights, set.date)
                if (bw == null) {
                    neededBodyweight = true
                    continue
                }
                if (kg >= bw * 0.99) out += Candidate(set.reps.toDouble(), StandardValueSource.LOGGED, set.date, fallbackNote)
                if (set.reps <= EST_ONE_RM_MAX_REPS) {
                    val e1rm = epleyOneRmKg(kg, set.reps)
                    if (e1rm != null && e1rm > bw) {
                        val reps = floor(30 * (e1rm / bw - 1))
                        if (reps >= 1) out += Candidate(reps, StandardValueSource.ESTIMATED, set.date, fallbackNote)
                    }
                }
            }
            StandardMeasureKind.MAX_REPS ->
                if (set.reps > 0) out += Candidate(set.reps.toDouble(), StandardValueSource.LOGGED, set.date, fallbackNote)
            StandardMeasureKind.LONGEST_HOLD -> {
                val seconds = set.durationSeconds ?: 0
                if (seconds > 0) out += Candidate(seconds.toDouble(), StandardValueSource.LOGGED, set.date, fallbackNote)
            }
        }
    }
    return out to (if (out.isEmpty() && neededBodyweight) StandardMissing.BODYWEIGHT else null)
}

private fun cardioCandidates(m: StandardMeasure, inputs: StandardInputs): List<Candidate> {
    val meters = m.meters ?: return emptyList()
    val out = mutableListOf<Candidate>()
    for (s in inputs.cardioSessions) {
        if (s.group !in m.cardioGroups) continue
        if (m.minPackKg != null && (s.packKg ?: 0.0) + 0.25 < m.minPackKg) continue
        if (s.distanceMeters + 0.5 < meters || s.seconds <= 0) continue
        val scaled = s.distanceMeters > meters * 1.05
        out += Candidate(
            s.seconds * meters / s.distanceMeters,
            if (scaled) StandardValueSource.ESTIMATED else StandardValueSource.LOGGED,
            s.date,
            if (scaled) "Average pace of a ${distanceLabel(s.distanceMeters)} ${s.groupLabel.lowercase()}" else null,
        )
    }
    return out
}

private fun testCandidates(
    standardId: String,
    event: StandardEvent,
    inputs: StandardInputs,
): Pair<List<Candidate>, StandardMissing?> {
    val out = mutableListOf<Candidate>()
    var missing: StandardMissing? = null
    for (r in inputs.testResults) {
        if (r.standardId != standardId || r.eventId != event.id || r.value <= 0) continue
        var value = r.value
        if (event.measure.kind == StandardMeasureKind.LIFT_MAX_BW) {
            val bw = bodyWeightOn(inputs.bodyWeights, r.date)
            if (bw == null) {
                missing = StandardMissing.BODYWEIGHT
                continue
            }
            value = r.value / bw
        }
        if (event.measure.kind == StandardMeasureKind.MANUAL && event.measure.unit == StandardManualUnit.CM_OVER_HEIGHT) {
            val h = inputs.profile.heightCm
            if (h == null || h <= 0) {
                missing = StandardMissing.HEIGHT
                continue
            }
            value = r.value - h
        }
        out += Candidate(value, StandardValueSource.TESTED, r.date, null)
    }
    return out to missing
}

fun evaluateEvent(standardId: String, event: StandardEvent, inputs: StandardInputs): StandardEventResult {
    val tiers = tiersFor(event, inputs.profile)
    val (tested, testMissing) = testCandidates(standardId, event, inputs)
    val candidates = tested.toMutableList()
    var missing = testMissing
    when (event.measure.kind) {
        StandardMeasureKind.FASTEST -> candidates += cardioCandidates(event.measure, inputs)
        StandardMeasureKind.MANUAL -> Unit
        else -> {
            val (lifts, liftMissing) = liftCandidates(event, inputs)
            candidates += lifts
            missing = missing ?: liftMissing
        }
    }
    var best: Candidate? = null
    for (c in candidates) if (better(c, best, event.lowerIsBetter)) best = c
    val b = best
    if (b == null) {
        return StandardEventResult(
            event = event,
            tiers = tiers,
            value = null,
            source = null,
            date = null,
            note = null,
            missing = missing ?: StandardMissing.RESULT,
            tier = null,
            score = 0.0,
            nextTier = StandardTier.BASE,
            gapToNext = null,
        )
    }
    val tier = tierOf(b.value, tiers, event.lowerIsBetter)
    val nextTier = when (tier) {
        null -> StandardTier.BASE
        StandardTier.BASE -> StandardTier.STRONG
        StandardTier.STRONG -> StandardTier.ELITE
        StandardTier.ELITE -> null
    }
    return StandardEventResult(
        event = event,
        tiers = tiers,
        value = b.value,
        source = b.source,
        date = b.date,
        note = b.note,
        missing = null,
        tier = tier,
        score = min(3.0, standardScore(b.value, tiers, event.lowerIsBetter)),
        nextTier = nextTier,
        gapToNext = nextTier?.let { abs(tiers[it] - b.value) },
    )
}

fun evaluateStandard(standard: FitnessStandard, inputs: StandardInputs): StandardResult {
    val events = standard.events.map { evaluateEvent(standard.id, it, inputs) }
    val counts = StandardTier.entries.associateWith { 0 }.toMutableMap()
    var overall = 3
    var scored = 0
    for (r in events) {
        if (r.value == null) continue
        scored++
        r.tier?.let { counts[it] = (counts[it] ?: 0) + 1 }
        overall = min(overall, r.tier?.let { it.ordinal + 1 } ?: 0)
    }
    return StandardResult(
        standard = standard,
        events = events,
        overallTier = if (scored > 0 && overall > 0) StandardTier.entries[overall - 1] else null,
        scoredCount = scored,
        tierCounts = counts,
    )
}
