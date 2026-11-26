package com.erv.app.stretching

import java.util.Locale
import kotlin.math.max

/**
 * Build For Today: a stretch routine picked for what today's training worked, fitted to the time
 * asked for. Pure (no Android) so it is unit tested; the Stretching screen gathers today's
 * training from the weight, cardio, workout and program stores and calls [buildStretchesForToday].
 */

/** Lengths offered, in minutes. */
val BUILD_FOR_TODAY_MINUTES = listOf(5, 10, 15, 20)

/** Where today's worked muscles came from. */
enum class TodayTrainingSource {
    /** Lifting or cardio already logged today (and anything in a workout now running). */
    LOGGED,

    /** Only a workout that is running now. */
    RUNNING,

    /** Nothing done yet, but today's plan has training. */
    PLANNED,

    /** Nothing logged, running or planned: a rest day. */
    REST_DAY,
}

/** Today's training as weight muscle groups (chest, back, legs, …) with how much each was worked. */
data class TodayTraining(
    val source: TodayTrainingSource,
    val muscleGroups: Map<String, Int> = emptyMap(),
) {
    val isRestDay: Boolean get() = source == TodayTrainingSource.REST_DAY || muscleGroups.isEmpty()

    /** The worked groups, most worked first. */
    val rankedGroups: List<String>
        get() = muscleGroups.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
}

/** One piece of today's training: weight exercises (by catalog id) and cardio activities (by name). */
data class TrainingPiece(
    val weightExerciseIds: List<String> = emptyList(),
    val cardioActivities: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = weightExerciseIds.isEmpty() && cardioActivities.isEmpty()
}

/** The weight muscle groups a cardio activity (a [com.erv.app.cardio.CardioBuiltinActivity] name) works. */
fun cardioActivityMuscleGroups(activity: String): List<String> = when (activity.trim().uppercase(Locale.ROOT)) {
    "WALK", "RUN", "SPRINT", "HIKE", "BIKE", "STATIONARY_BIKE", "AIR_BIKE", "ELLIPTICAL", "JUMP_ROPE",
    "JUMPING_JACKS" -> listOf("legs")
    "RUCK" -> listOf("legs", "back", "shoulders")
    "ROWING", "SKI_ERG" -> listOf("back", "legs")
    "SWIM" -> listOf("shoulders", "back")
    "BATTLE_ROPE" -> listOf("shoulders", "core")
    "BURPEES" -> listOf("legs", "chest", "core")
    else -> emptyList()
}

/**
 * Today's training from what was [logged], what is [running], and what is [planned], with
 * [weightGroupOf] giving a weight exercise's muscle group. Logged and running count together; the
 * plan is only used when nothing has been done yet; nothing at all is a rest day.
 */
fun todayTraining(
    logged: TrainingPiece,
    running: TrainingPiece,
    planned: TrainingPiece,
    weightGroupOf: (String) -> String?,
): TodayTraining {
    fun groupsOf(vararg pieces: TrainingPiece): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        for (p in pieces) {
            for (id in p.weightExerciseIds) {
                val g = weightGroupOf(id)?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: continue
                counts[g] = (counts[g] ?: 0) + 1
            }
            for (a in p.cardioActivities) {
                // A cardio session counts like two exercises for the groups it works.
                for (g in cardioActivityMuscleGroups(a)) counts[g] = (counts[g] ?: 0) + 2
            }
        }
        return counts
    }
    val done = groupsOf(logged, running)
    if (done.isNotEmpty()) {
        val source = if (logged.isEmpty) TodayTrainingSource.RUNNING else TodayTrainingSource.LOGGED
        return TodayTraining(source, done)
    }
    val plan = groupsOf(planned)
    if (plan.isNotEmpty()) return TodayTraining(TodayTrainingSource.PLANNED, plan)
    return TodayTraining(TodayTrainingSource.REST_DAY)
}

/** A routine Build For Today made: its stretches, one hold each, and its timing. */
data class BuiltStretchRoutine(
    val name: String,
    val entries: List<StretchCatalogEntry>,
    val holds: List<Int>,
    val gapSeconds: Int = STRETCH_GAP_DEFAULT_SECONDS,
    val settleAndRest: Boolean = false,
) {
    val totalSeconds: Int get() = builtRoutineSeconds(entries, holds, gapSeconds, settleAndRest)

    /** As a saved routine; holds that differ from the most common one are kept per stretch. */
    fun toRoutine(id: String = java.util.UUID.randomUUID().toString()): StretchRoutine {
        val ids = entries.map { it.id }
        val common = holds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 30
        return StretchRoutine(
            id = id,
            name = name,
            stretchIds = ids,
            holdSecondsPerStretch = common,
            holdSecondsByStretchId = cleanedStretchHoldOverrides(ids.zip(holds).toMap(), ids, common),
            gapSeconds = gapSeconds.takeIf { it != STRETCH_GAP_DEFAULT_SECONDS },
            settleAndRest = settleAndRest,
        )
    }
}

/** Prep matches the guided player's: 10 s get-ready, or the minute's settle and rest. */
private const val PREP_SECONDS = 10
private const val SIDE_SWITCH_SECONDS = 5

/** Seconds the guided player will take for these stretches (same rules as the player). */
fun builtRoutineSeconds(
    entries: List<StretchCatalogEntry>,
    holds: List<Int>,
    gapSeconds: Int,
    settleAndRest: Boolean,
): Int {
    if (entries.isEmpty()) return 0
    val settle = settleAndRest && entries.any { it.isRestorative }
    var total = if (settle) STRETCH_SETTLE_SECONDS + STRETCH_CLOSING_REST_SECONDS else PREP_SECONDS
    entries.forEachIndexed { i, e ->
        val hold = holds.getOrNull(i) ?: 30
        total += hold * e.holdSideSegments()
        if (e.requiresBothSides) total += SIDE_SWITCH_SECONDS
        if (i > 0) total += gapSeconds
    }
    return total
}

/** Body order for a flowing routine: top down, then the floor, restorative last. */
private val BODY_ORDER = listOf("neck", "shoulders", "arms", "chest", "back", "core", "glutes", "legs", "restorative", "other")

/** A gentle order for restorative poses: fold in, open up, lie back, savasana last. */
private val RESTORATIVE_ORDER = listOf(
    "builtin_supported_childs_pose",
    "builtin_sphinx",
    "builtin_puppy_pose",
    "builtin_banana_pose",
    "builtin_reclined_butterfly",
    "builtin_happy_baby",
    "builtin_legs_up_wall",
    "builtin_savasana",
)

private const val WORK_MIN_HOLD = 30
private const val WORK_MAX_HOLD = 60
private const val LONG_WORK_MIN_HOLD = 45
private const val LONG_WORK_MAX_HOLD = 90
private const val REST_MIN_HOLD = 45
private const val REST_MAX_HOLD = 300

private fun groupLabel(group: String): String = when (group) {
    "biceps" -> "Biceps"
    "triceps" -> "Triceps"
    else -> group.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/**
 * A routine of about [minutes] (never longer) for [today]: stretches for the worked groups, the
 * most worked first and taking turns so each group gets some, holds stretched to fill the time;
 * on a rest day (or when nothing in [catalog] matches), restorative poses with Settle In and Rest
 * from 10 minutes up. Null when [catalog] has nothing to use.
 */
fun buildStretchesForToday(
    catalog: List<StretchCatalogEntry>,
    today: TodayTraining,
    minutes: Int,
): BuiltStretchRoutine? {
    val budget = minutes.coerceIn(1, 60) * 60
    if (!today.isRestDay) {
        buildForWorkedGroups(catalog, today, budget)?.let { return it }
    }
    return buildRestDay(catalog, budget, minutes)
}

private fun buildForWorkedGroups(
    catalog: List<StretchCatalogEntry>,
    today: TodayTraining,
    budget: Int,
): BuiltStretchRoutine? {
    val groups = today.rankedGroups
    val weight = today.muscleGroups
    // Longer sessions hold each stretch longer rather than adding ever more stretches.
    val long = budget >= 15 * 60
    val minHold = if (long) LONG_WORK_MIN_HOLD else WORK_MIN_HOLD
    val maxHold = if (long) LONG_WORK_MAX_HOLD else WORK_MAX_HOLD
    val index = catalog.withIndex().associate { it.value.id to it.index }
    fun score(e: StretchCatalogEntry) = e.muscleGroups.sumOf { weight[it] ?: 0 }
    val work = catalog.filter { !it.isRestorative && it.muscleGroups.any { g -> g in weight } }
    if (work.isEmpty()) return null
    val queues = groups.associateWith { g ->
        work.filter { g in it.muscleGroups }
            .sortedWith(compareByDescending<StretchCatalogEntry> { score(it) }.thenBy { index[it.id] ?: 0 })
            .toMutableList()
    }

    // From 15 minutes, finish on one restorative pose that suits the day.
    val finisher = if (budget >= 15 * 60) {
        catalog.filter { it.isRestorative && it.muscleGroups.any { g -> g in weight } }
            .maxWithOrNull(compareBy<StretchCatalogEntry> { score(it) }.thenByDescending { index[it.id] ?: 0 })
    } else {
        null
    }
    val finisherHold = finisher?.let { (it.defaultHoldSeconds ?: 90).coerceAtMost(120) }

    val picked = mutableListOf<StretchCatalogEntry>()
    fun fits(candidate: List<StretchCatalogEntry>): Boolean {
        val all = candidate + listOfNotNull(finisher)
        val holds = candidate.map { minHold } + listOfNotNull(finisherHold)
        return builtRoutineSeconds(all, holds, STRETCH_GAP_DEFAULT_SECONDS, false) <= budget
    }
    var progress = true
    while (progress) {
        progress = false
        for (g in groups) {
            val q = queues[g] ?: continue
            q.removeAll { it in picked }
            val next = q.firstOrNull() ?: continue
            if (fits(picked + next)) {
                picked += next
                q.remove(next)
                progress = true
            } else {
                q.remove(next)
                if (q.isNotEmpty()) progress = true
            }
        }
    }
    if (picked.isEmpty()) return null
    val ordered = picked.sortedWith(
        compareBy<StretchCatalogEntry> { BODY_ORDER.indexOf(it.category.lowercase(Locale.ROOT)).let { i -> if (i < 0) BODY_ORDER.size else i } }
            .thenBy { picked.indexOf(it) },
    )
    val entries = ordered + listOfNotNull(finisher)
    val baseHolds = ordered.map { minHold } + listOfNotNull(finisherHold)
    val flexible = ordered.indices.toSet()
    val holds = fillHolds(entries, baseHolds, flexible, budget, false, maxHold)
    val label = groups.take(3).joinToString(", ") { groupLabel(it) }
    return BuiltStretchRoutine(name = "For Today: $label", entries = entries, holds = holds)
}

private fun buildRestDay(catalog: List<StretchCatalogEntry>, budget: Int, minutes: Int): BuiltStretchRoutine? {
    val byId = catalog.associateBy { it.id }
    val ordered = RESTORATIVE_ORDER.mapNotNull { byId[it] } +
        catalog.filter { it.isRestorative && it.id !in RESTORATIVE_ORDER }
    if (ordered.isEmpty()) return null
    val settle = minutes >= 10
    // The closing rest is a savasana, so it is not also a pose when Settle In and Rest plays.
    val pool = if (settle) ordered.filter { it.id != "builtin_savasana" } else ordered
    val baseHold = when {
        minutes <= 5 -> 60
        minutes <= 10 -> 90
        else -> 120
    }
    fun holdFor(e: StretchCatalogEntry) = minOf(e.defaultHoldSeconds ?: baseHold, baseHold)
    val picked = mutableListOf<StretchCatalogEntry>()
    for (e in pool) {
        val next = picked + e
        if (builtRoutineSeconds(next, next.map { holdFor(it) }, STRETCH_GAP_DEFAULT_SECONDS, settle) <= budget) {
            picked += e
        }
    }
    if (picked.isEmpty()) {
        picked += pool.first()
    }
    // Savasana, when it is one of the poses, always comes last.
    val entries = picked.sortedBy { if (it.id == "builtin_savasana") 1 else 0 }
    val holds = fillHolds(entries, entries.map { holdFor(it) }, entries.indices.toSet(), budget, settle, REST_MAX_HOLD)
    return BuiltStretchRoutine(
        name = "Rest Day Restore",
        entries = entries,
        holds = holds,
        settleAndRest = settle,
    )
}

/**
 * Grows the [flexible] holds evenly (in 5 s steps, up to [maxHold]) so the routine fills as much of
 * [budget] as it can without going over; shrinks them toward their minimum if it starts over.
 */
private fun fillHolds(
    entries: List<StretchCatalogEntry>,
    base: List<Int>,
    flexible: Set<Int>,
    budget: Int,
    settle: Boolean,
    maxHold: Int,
): List<Int> {
    val holds = base.toMutableList()
    fun total() = builtRoutineSeconds(entries, holds, STRETCH_GAP_DEFAULT_SECONDS, settle)
    // Shrink first if over (a single long pose in a short session).
    while (total() > budget) {
        val i = flexible.filter { holds[it] > 20 }.maxByOrNull { holds[it] } ?: break
        holds[i] = max(20, holds[i] - 5)
    }
    var grew = true
    while (grew) {
        grew = false
        for (i in flexible.sortedBy { holds[it] }) {
            if (holds[i] + 5 > maxHold) continue
            holds[i] += 5
            if (total() <= budget) {
                grew = true
            } else {
                holds[i] -= 5
            }
        }
    }
    return holds
}

/** A short line for the sheet: what the routine is for. */
fun TodayTraining.describe(): String = when {
    isRestDay -> "Nothing logged or planned today, so this is a gentle restorative session."
    else -> {
        val what = rankedGroups.take(3).joinToString(", ") { groupLabel(it).lowercase(Locale.getDefault()) }
        when (source) {
            TodayTrainingSource.LOGGED -> "For what you worked today: $what."
            TodayTrainingSource.RUNNING -> "For the workout you're doing now: $what."
            TodayTrainingSource.PLANNED -> "For what's planned today: $what."
            TodayTrainingSource.REST_DAY -> ""
        }
    }
}
