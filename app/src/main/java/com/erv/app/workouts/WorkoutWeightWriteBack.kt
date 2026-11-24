package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightWorkoutSession
import kotlin.math.abs

/**
 * "Save today's weights": after a run, the weights the athlete actually lifted can become the
 * workout's targets for next time.
 *
 * Each change is matched to a storyboard item by segment and item id, so it can be applied to the
 * library copy of the workout even if that copy was edited while the run was going.
 */
data class WorkoutTargetWeightChange(
    val segmentId: String,
    val itemId: String,
    val exerciseId: String,
    /** The sets logged today for this item, in order (unlogged sets included, so indexes line up). */
    val loggedSets: List<WeightSet>,
    /** Targets before and after, one per set when the item has per-set targets, otherwise one value. */
    val beforeKg: List<Double?>,
    val afterKg: List<Double?>,
)

private const val WEIGHT_EPSILON_KG = 0.01

private fun WeightSet.loggedWeightKg(): Double? {
    val kg = weightKg?.takeIf { it > 0 } ?: return null
    val didWork = reps > 0 || (durationSeconds ?: 0) > 0 || (repsPerSide ?: 0) > 0
    return if (didWork) kg else null
}

private fun sameKg(a: Double?, b: Double?): Boolean =
    if (a == null || b == null) a == b else abs(a - b) < WEIGHT_EPSILON_KG

private fun WeightSet.targetKg(): Double? =
    targetWeightKg?.takeIf { it > 0 } ?: weightKg?.takeIf { it > 0 }

/**
 * The prescription with today's [loggedSets] as its weight targets, or null when nothing would
 * change (no weight was logged, or it matches the targets already).
 *
 * - Per-set targets: each set takes the weight logged for that set; sets not logged keep theirs.
 * - One target for every set: it becomes the heaviest weight lifted today.
 */
fun WorkoutWeightPrescription.withLoggedWeights(loggedSets: List<WeightSet>): WorkoutWeightPrescription? {
    if (sets.isNotEmpty()) {
        var changed = false
        val updated = sets.mapIndexed { index, set ->
            val logged = loggedSets.getOrNull(index)?.loggedWeightKg() ?: return@mapIndexed set
            if (sameKg(set.targetKg(), logged)) return@mapIndexed set
            changed = true
            set.copy(targetWeightKg = logged)
        }
        return if (changed) copy(sets = updated) else null
    }
    val heaviest = loggedSets.mapNotNull { it.loggedWeightKg() }.maxOrNull() ?: return null
    if (sameKg(effectiveTargetWeightKg(), heaviest)) return null
    return copy(targetWeightKg = heaviest)
}

private fun WorkoutWeightPrescription.targetWeightsKg(): List<Double?> =
    if (sets.isNotEmpty()) sets.map { it.targetKg() } else listOf(effectiveTargetWeightKg())

private fun WorkoutSegment.isRoundBased(): Boolean =
    kind == WorkoutSegmentKind.SUPERSET || kind == WorkoutSegmentKind.CIRCUIT

/** How many logged sets this item used: one per round in a superset or circuit. */
private fun WorkoutItem.Weight.plannedSetCount(segment: WorkoutSegment): Int =
    if (segment.isRoundBased()) {
        segment.effectiveRounds()
    } else {
        prescription.sets.size.takeIf { it > 0 } ?: prescription.setCount?.coerceAtLeast(1) ?: 1
    }

/**
 * The target changes today's run suggests, in storyboard order.
 *
 * [sessionsBySegmentId] holds the weight sessions logged for each segment, in the order they were
 * logged. When the same exercise appears more than once in a segment, the live session appends its
 * later sets to the first entry (see [buildWorkoutWeightBatch]), so each item takes its share of
 * that exercise's sets in turn. Items in [skippedItemKeys] (segment id to item id) logged nothing
 * and take no sets. An item whose exercise was swapped for an alternative today is left alone.
 */
fun proposeWorkoutTargetWeightChanges(
    workout: Workout,
    sessionsBySegmentId: Map<String, List<WeightWorkoutSession>>,
    skippedItemKeys: Set<Pair<String, String>> = emptySet(),
): List<WorkoutTargetWeightChange> {
    val changes = mutableListOf<WorkoutTargetWeightChange>()
    workout.segments.forEach { segment ->
        val sessions = sessionsBySegmentId[segment.id].orEmpty()
        if (sessions.isEmpty()) return@forEach
        val setsByExercise = mutableMapOf<String, List<WeightSet>>()
        sessions.forEach { session ->
            session.entries.forEach { entry ->
                if (entry.hiitBlock != null) return@forEach
                setsByExercise[entry.exerciseId] = setsByExercise[entry.exerciseId].orEmpty() + entry.sets
            }
        }
        val usedByExercise = mutableMapOf<String, Int>()
        segment.items.filterIsInstance<WorkoutItem.Weight>().forEach { item ->
            if ((segment.id to item.id) in skippedItemKeys) return@forEach
            val all = setsByExercise[item.exerciseId] ?: return@forEach
            val start = usedByExercise[item.exerciseId] ?: 0
            val count = item.plannedSetCount(segment)
            usedByExercise[item.exerciseId] = start + count
            val logged = all.drop(start).take(count)
            if (logged.isEmpty()) return@forEach
            val updated = item.prescription.withLoggedWeights(logged) ?: return@forEach
            changes.add(
                WorkoutTargetWeightChange(
                    segmentId = segment.id,
                    itemId = item.id,
                    exerciseId = item.exerciseId,
                    loggedSets = logged,
                    beforeKg = item.prescription.targetWeightsKg(),
                    afterKg = updated.targetWeightsKg(),
                ),
            )
        }
    }
    return changes
}

/**
 * This workout with [changes] applied to the items that still exist with the same exercise.
 * Returns the workout unchanged when none of them apply any more.
 */
fun Workout.withTodaysWeights(changes: List<WorkoutTargetWeightChange>): Workout {
    if (changes.isEmpty()) return this
    val byKey = changes.associateBy { it.segmentId to it.itemId }
    var changed = false
    val updatedSegments = segments.map { segment ->
        val updatedItems = segment.items.map { item ->
            val change = byKey[segment.id to item.id]
            if (item !is WorkoutItem.Weight || change == null || change.exerciseId != item.exerciseId) {
                return@map item
            }
            val updated = item.prescription.withLoggedWeights(change.loggedSets) ?: return@map item
            changed = true
            item.copy(prescription = updated)
        }
        segment.copy(items = updatedItems)
    }
    return if (changed) copy(segments = updatedSegments) else this
}
