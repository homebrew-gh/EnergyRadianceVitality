package com.erv.app.workouts

import com.erv.app.weighttraining.WeightHiitBlockLog
import com.erv.app.weighttraining.WeightSet
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutCircuitRun
import com.erv.app.weighttraining.isLogged
import com.erv.app.weighttraining.setLoggingStyle
import com.erv.app.weighttraining.WeightSetLoggingStyle
import kotlinx.serialization.Serializable

@Serializable
data class WeightCircuitSlot(
    val workoutItemId: String,
    val exerciseId: String,
    val prescription: WorkoutWeightPrescription = WorkoutWeightPrescription(),
)

fun WorkoutSegment.toCircuitSlots(): List<WeightCircuitSlot> =
    weightItems().map { item ->
        WeightCircuitSlot(
            workoutItemId = item.id,
            exerciseId = item.exerciseId,
            prescription = item.prescription,
        )
    }

fun circuitSlotKey(round: Int, slotIndex: Int): String = "$round-$slotIndex"

fun WeightWorkoutCircuitRun.currentSlot(): WeightCircuitSlot? =
    slots.getOrNull(currentSlotIndex)

fun WeightWorkoutCircuitRun.isCurrentSlotLogged(
    setsByExerciseId: Map<String, List<WeightSet>>,
    hiitBlocksByExerciseId: Map<String, WeightHiitBlockLog>,
): Boolean {
    val slot = currentSlot() ?: return false
    if (hiitBlocksByExerciseId[slot.exerciseId] != null) return true
    val roundSet = setsByExerciseId[slot.exerciseId].orEmpty().getOrNull(currentRound - 1)
    return roundSet?.isLogged() == true
}

fun WeightWorkoutCircuitRun.pendingRestBeforeAdvance(): Int? {
    if (isComplete || slots.isEmpty()) return null
    val isLastItemInRound = currentSlotIndex >= slots.lastIndex
    if (!isLastItemInRound) {
        return restBetweenItemsSeconds.takeIf { it > 0 }
    }
    if (currentRound < rounds) {
        return restAfterRoundSeconds.takeIf { it > 0 }
    }
    return null
}

fun WeightWorkoutCircuitRun.advanceAfterSlot(): WeightWorkoutCircuitRun {
    if (slots.isEmpty()) return copy(isComplete = true)
    val isLastItemInRound = currentSlotIndex >= slots.lastIndex
    if (!isLastItemInRound) {
        return copy(
            currentSlotIndex = currentSlotIndex + 1,
            lastAcknowledgedSlotKey = circuitSlotKey(currentRound, currentSlotIndex),
            pendingRestSeconds = null,
        )
    }
    if (currentRound < rounds) {
        return copy(
            currentRound = currentRound + 1,
            currentSlotIndex = 0,
            lastAcknowledgedSlotKey = circuitSlotKey(currentRound, currentSlotIndex),
            pendingRestSeconds = null,
        )
    }
    return copy(
        isComplete = true,
        lastAcknowledgedSlotKey = circuitSlotKey(currentRound, currentSlotIndex),
        pendingRestSeconds = null,
    )
}

fun WeightWorkoutCircuitRun.toWorkoutRunPosition(segmentIndex: Int): WorkoutRunPosition =
    WorkoutRunPosition(
        segmentIndex = segmentIndex,
        itemIndex = currentSlotIndex,
        round = currentRound,
    )

fun WorkoutSegment.buildCircuitSetsSeed(library: WeightLibraryState): Map<String, List<WeightSet>> {
    val roundsCount = effectiveRounds()
    return toCircuitSlots().associate { slot ->
        val exercise = library.exerciseById(slot.exerciseId)
        val template = slot.prescription.resolvedSets(
            loggingStyle = exercise?.setLoggingStyle() ?: WeightSetLoggingStyle.REPS,
        )
        val rowTemplate = template.firstOrNull() ?: WeightSet(reps = 0)
        slot.exerciseId to List(roundsCount) { rowTemplate.copy() }
    }
}

fun buildWorkoutCircuitRun(
    segment: WorkoutSegment,
    workoutId: String,
    segmentIndex: Int,
    initialRound: Int = 1,
    initialSlotIndex: Int = 0,
): WeightWorkoutCircuitRun? {
    val slots = segment.toCircuitSlots()
    if (slots.isEmpty()) return null
    val roundsCount = segment.effectiveRounds()
    return WeightWorkoutCircuitRun(
        workoutId = workoutId,
        segmentId = segment.id,
        segmentTitle = segment.title,
        segmentIndex = segmentIndex,
        rounds = roundsCount,
        restBetweenItemsSeconds = segment.restPolicy.restBetweenItemsSeconds,
        restAfterRoundSeconds = segment.restPolicy.restAfterRoundSeconds,
        slots = slots,
        currentRound = initialRound.coerceIn(1, roundsCount),
        currentSlotIndex = initialSlotIndex.coerceIn(0, slots.lastIndex),
    )
}

/** Position at the last slot of the final round — [WorkoutRunEngine.advance] moves to the next segment. */
fun WorkoutSegment.finalCircuitRunPosition(segmentIndex: Int): WorkoutRunPosition =
    WorkoutRunPosition(
        segmentIndex = segmentIndex,
        itemIndex = (weightItems().size - 1).coerceAtLeast(0),
        round = effectiveRounds(),
    )
