package com.erv.app.workouts

import com.erv.app.weighttraining.WeightSet
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The live workout as one list of steps, down to single sets, rests, cardio legs and stretch holds.
 *
 * [WorkoutRunEngine] walks segments and items and hands each one to a separate silo screen. This
 * plan goes one level further so a single session screen can run the whole workout: the screen
 * shows [WorkoutSessionEngine.currentStep] and sends a [WorkoutSessionEvent] when the athlete acts.
 * Everything here is pure, so the order and rests are unit tested against the shared examples.
 */
sealed class WorkoutSessionStep {
    abstract val segmentIndex: Int
    abstract val segmentId: String

    /** The storyboard item this step belongs to; null for the rest after a whole section. */
    abstract val itemId: String?

    /** 1-based round: the circuit or superset round, otherwise 1. */
    abstract val round: Int

    /** One set of one exercise. In a superset or circuit each round is one set of each exercise. */
    data class LiftSet(
        override val segmentIndex: Int,
        override val segmentId: String,
        override val itemId: String,
        override val round: Int,
        val exerciseId: String,
        /** 0-based set number within this item (the round minus one in a superset or circuit). */
        val setIndex: Int,
        val setCount: Int,
        /** Targets only (reps 0, no logged weight), as [WorkoutWeightPrescription.resolvedSets] seeds them. */
        val target: WeightSet,
        /** True in a superset or circuit, where [setCount] is the number of rounds. */
        val inRounds: Boolean = false,
    ) : WorkoutSessionStep()

    data class Rest(
        override val segmentIndex: Int,
        override val segmentId: String,
        override val itemId: String?,
        override val round: Int,
        val seconds: Int,
        val kind: WorkoutSessionRestKind,
    ) : WorkoutSessionStep()

    /**
     * One timed leg of a cardio item: the whole of a steady effort, or one work or recovery leg of
     * an interval item. [targetSeconds] is null for an open-ended effort the athlete stops.
     */
    data class CardioLeg(
        override val segmentIndex: Int,
        override val segmentId: String,
        override val itemId: String,
        override val round: Int,
        val activity: String,
        val legIndex: Int,
        val legCount: Int,
        val targetSeconds: Int?,
        val isRecovery: Boolean,
        val label: String? = null,
        val hrTargetBpm: Int? = null,
        /** Set when the item runs a saved cardio routine, which the screen resolves itself. */
        val cardioRoutineId: String? = null,
    ) : WorkoutSessionStep()

    /** One stretch hold; a per-side stretch is two holds, left then right. */
    data class StretchHold(
        override val segmentIndex: Int,
        override val segmentId: String,
        override val itemId: String,
        override val round: Int,
        val catalogId: String,
        val holdSeconds: Int,
        val side: WorkoutStretchSide? = null,
    ) : WorkoutSessionStep()

    data class Note(
        override val segmentIndex: Int,
        override val segmentId: String,
        override val itemId: String,
        override val round: Int,
        val text: String,
    ) : WorkoutSessionStep()
}

enum class WorkoutSessionRestKind {
    /** Between two sets of the same exercise. */
    BETWEEN_SETS,

    /** After an exercise, before the next one in the same section. */
    BETWEEN_EXERCISES,

    /** After the last exercise of a superset or circuit round, before the next round. */
    AFTER_ROUND,

    /** After a whole section, before the next section. */
    AFTER_SECTION,

    /** A rest item the builder placed in the storyboard. */
    PLANNED,
    // A recovery between interval legs is a WorkoutSessionStep.CardioLeg, not a rest.
}

enum class WorkoutStretchSide { LEFT, RIGHT }

/** How the rests between sets and exercises run (the rest timer setting on the session screen). */
@Serializable
enum class WorkoutSessionRestMode {
    /** No rests between sets or exercises; rests the builder placed and section rests stay. */
    @SerialName("off") OFF,

    /** Each rest counts down as soon as it comes on screen. */
    @SerialName("auto") AUTO,

    /** Each rest between sets or exercises waits for Start Rest. */
    @SerialName("manual") MANUAL,
}

/**
 * The athlete's rest timer setting for one run. [defaultSeconds] fills the rest between sets and
 * after an exercise where the workout gives none (0 leaves those without a rest).
 */
@Serializable
data class WorkoutSessionRestOptions(
    val mode: WorkoutSessionRestMode = WorkoutSessionRestMode.AUTO,
    val defaultSeconds: Int = 0,
)

/** A rest the rest timer setting applies to: between sets, between exercises, or between rounds. */
fun WorkoutSessionStep.isTimerRest(): Boolean =
    this is WorkoutSessionStep.Rest && (
        kind == WorkoutSessionRestKind.BETWEEN_SETS ||
            kind == WorkoutSessionRestKind.BETWEEN_EXERCISES ||
            kind == WorkoutSessionRestKind.AFTER_ROUND
        )

/** The steps of one workout, built once from the run's workout snapshot. */
data class WorkoutSessionPlan(
    val workoutId: String,
    val steps: List<WorkoutSessionStep>,
) {
    val size: Int get() = steps.size

    companion object {
        fun from(workout: Workout, rest: WorkoutSessionRestOptions? = null): WorkoutSessionPlan {
            val steps = buildWorkoutSessionSteps(workout, rest?.defaultSeconds?.coerceAtLeast(0) ?: 0)
            return WorkoutSessionPlan(
                workout.id,
                if (rest?.mode == WorkoutSessionRestMode.OFF) steps.filterNot { it.isTimerRest() } else steps,
            )
        }
    }
}

@Serializable
enum class WorkoutStepOutcome {
    @SerialName("done") DONE,
    @SerialName("skipped") SKIPPED,
}

/** What happened to one step. [set] holds a logged lift; [elapsedSeconds] a timed step's actual time. */
@Serializable
data class WorkoutStepRecord(
    val stepIndex: Int,
    val outcome: WorkoutStepOutcome,
    val set: WeightSet? = null,
    val elapsedSeconds: Int? = null,
    /** When the step was finished or skipped; the saved run stamps it (see [withSessionEvent]). */
    val atEpochSeconds: Long? = null,
)

/**
 * Progress through a [WorkoutSessionPlan]: the step on screen and a record per finished or skipped
 * step. Serializable so the run can be saved and resumed; the plan itself is rebuilt from the
 * workout snapshot.
 */
@Serializable
data class WorkoutSessionState(
    val stepIndex: Int = 0,
    val records: List<WorkoutStepRecord> = emptyList(),
) {
    fun recordFor(stepIndex: Int): WorkoutStepRecord? = records.firstOrNull { it.stepIndex == stepIndex }
}

sealed class WorkoutSessionEvent {
    /** The current lift set was done with these reps, load or hold time. */
    data class SetLogged(val set: WeightSet) : WorkoutSessionEvent()

    /** The current cardio leg, stretch hold or rest finished after [elapsedSeconds]. */
    data class TimedStepDone(val elapsedSeconds: Int) : WorkoutSessionEvent()

    /** The current rest ran out or was cut short, or the current note was read. */
    data object StepDone : WorkoutSessionEvent()

    /** Skip only the current step. */
    data object SkipStep : WorkoutSessionEvent()

    /** Skip every step left of the current item, in every round still to come. */
    data object SkipItem : WorkoutSessionEvent()

    /** End the current section early: skip what is left of it, but keep the rest after it. */
    data object EndSection : WorkoutSessionEvent()

    /** Correct a lift set already logged. Does not move the session. */
    data class EditSet(val stepIndex: Int, val set: WeightSet) : WorkoutSessionEvent()

    /**
     * Another screen logged this whole item (a cardio or stretch section, until the session
     * screen draws those itself): every step of it still open is done.
     */
    data class ItemLoggedElsewhere(val segmentId: String, val itemId: String) : WorkoutSessionEvent()

    /** End the whole workout now: every step still open is skipped. */
    data object EndWorkout : WorkoutSessionEvent()
}

object WorkoutSessionEngine {

    fun currentStep(plan: WorkoutSessionPlan, state: WorkoutSessionState): WorkoutSessionStep? =
        plan.steps.getOrNull(state.stepIndex)

    fun isComplete(plan: WorkoutSessionPlan, state: WorkoutSessionState): Boolean =
        state.stepIndex >= plan.size

    /**
     * The state after [event]. An event that does not fit the current step (a logged set while a
     * rest is on screen, say) leaves the state unchanged, so a late tap can't log the wrong step.
     */
    fun reduce(
        plan: WorkoutSessionPlan,
        state: WorkoutSessionState,
        event: WorkoutSessionEvent,
    ): WorkoutSessionState {
        if (event is WorkoutSessionEvent.EditSet) return editSet(plan, state, event)
        val step = currentStep(plan, state) ?: return state
        val index = state.stepIndex
        return when (event) {
            is WorkoutSessionEvent.SetLogged ->
                if (step is WorkoutSessionStep.LiftSet) {
                    state.finish(plan, WorkoutStepRecord(index, WorkoutStepOutcome.DONE, set = event.set))
                } else {
                    state
                }
            is WorkoutSessionEvent.TimedStepDone ->
                if (step is WorkoutSessionStep.CardioLeg || step is WorkoutSessionStep.StretchHold ||
                    step is WorkoutSessionStep.Rest
                ) {
                    state.finish(
                        plan,
                        WorkoutStepRecord(index, WorkoutStepOutcome.DONE, elapsedSeconds = event.elapsedSeconds.coerceAtLeast(0)),
                    )
                } else {
                    state
                }
            WorkoutSessionEvent.StepDone ->
                if (step is WorkoutSessionStep.Rest || step is WorkoutSessionStep.Note) {
                    state.finish(plan, WorkoutStepRecord(index, WorkoutStepOutcome.DONE))
                } else {
                    state
                }
            WorkoutSessionEvent.SkipStep ->
                state.finish(plan, WorkoutStepRecord(index, WorkoutStepOutcome.SKIPPED))
            WorkoutSessionEvent.SkipItem -> {
                val itemId = step.itemId
                if (itemId == null) {
                    state.finish(plan, WorkoutStepRecord(index, WorkoutStepOutcome.SKIPPED))
                } else {
                    state.markWhere(plan, WorkoutStepOutcome.SKIPPED) {
                        it.segmentIndex == step.segmentIndex && it.itemId == itemId
                    }
                }
            }
            WorkoutSessionEvent.EndSection ->
                state.markWhere(plan, WorkoutStepOutcome.SKIPPED) {
                    it.segmentIndex == step.segmentIndex &&
                        !(it is WorkoutSessionStep.Rest && it.kind == WorkoutSessionRestKind.AFTER_SECTION)
                }
            is WorkoutSessionEvent.ItemLoggedElsewhere ->
                state.markWhere(plan, WorkoutStepOutcome.DONE) {
                    it.segmentId == event.segmentId && it.itemId == event.itemId
                }
            WorkoutSessionEvent.EndWorkout -> state.markWhere(plan, WorkoutStepOutcome.SKIPPED) { true }
            is WorkoutSessionEvent.EditSet -> state
        }
    }

    /** Lift sets logged for one item, in the order they were done. */
    fun loggedSets(
        plan: WorkoutSessionPlan,
        state: WorkoutSessionState,
        segmentId: String,
        itemId: String,
    ): List<WeightSet> =
        state.records
            .sortedBy { it.stepIndex }
            .mapNotNull { record ->
                val step = plan.steps.getOrNull(record.stepIndex) as? WorkoutSessionStep.LiftSet
                    ?: return@mapNotNull null
                if (step.segmentId != segmentId || step.itemId != itemId) return@mapNotNull null
                record.set?.takeIf { record.outcome == WorkoutStepOutcome.DONE }
            }

    /** True when every step of [segmentIndex] is done or skipped. */
    fun isSectionFinished(plan: WorkoutSessionPlan, state: WorkoutSessionState, segmentIndex: Int): Boolean =
        plan.steps.indices
            .filter { plan.steps[it].segmentIndex == segmentIndex }
            .all { state.recordFor(it) != null }

    private fun editSet(
        plan: WorkoutSessionPlan,
        state: WorkoutSessionState,
        event: WorkoutSessionEvent.EditSet,
    ): WorkoutSessionState {
        if (plan.steps.getOrNull(event.stepIndex) !is WorkoutSessionStep.LiftSet) return state
        val existing = state.recordFor(event.stepIndex) ?: return state
        if (existing.outcome != WorkoutStepOutcome.DONE) return state
        return state.copy(
            records = state.records.map { if (it.stepIndex == event.stepIndex) it.copy(set = event.set) else it },
        )
    }

    private fun WorkoutSessionState.finish(
        plan: WorkoutSessionPlan,
        record: WorkoutStepRecord,
    ): WorkoutSessionState {
        val updated = copy(records = records.filterNot { it.stepIndex == record.stepIndex } + record)
        return updated.copy(stepIndex = updated.nextOpenStep(plan, after = record.stepIndex))
    }

    private fun WorkoutSessionState.markWhere(
        plan: WorkoutSessionPlan,
        outcome: WorkoutStepOutcome,
        predicate: (WorkoutSessionStep) -> Boolean,
    ): WorkoutSessionState {
        val start = stepIndex
        val marked = (start until plan.size)
            .filter { recordFor(it) == null && predicate(plan.steps[it]) }
            .map { WorkoutStepRecord(it, outcome) }
        if (marked.isEmpty()) return this
        val updated = copy(records = records + marked)
        return updated.copy(stepIndex = updated.nextOpenStep(plan, after = start - 1))
    }

    private fun WorkoutSessionState.nextOpenStep(plan: WorkoutSessionPlan, after: Int): Int {
        var next = after + 1
        while (next < plan.size && recordFor(next) != null) next++
        return next
    }
}

private const val DEFAULT_STRETCH_HOLD_SECONDS = 30

private fun buildWorkoutSessionSteps(
    workout: Workout,
    defaultRestSeconds: Int,
): List<WorkoutSessionStep> = buildList<WorkoutSessionStep> {
    workout.segments.forEachIndexed { segmentIndex, segment ->
        when (segment.kind) {
            WorkoutSegmentKind.SUPERSET, WorkoutSegmentKind.CIRCUIT ->
                addRoundSteps(segmentIndex, segment)
            else -> segment.items.forEachIndexed { itemIndex, item ->
                val hasNextItem = itemIndex < segment.items.lastIndex
                when (item) {
                    is WorkoutItem.Weight -> addStraightSets(
                        segmentIndex, segment, item, hasNextItem,
                        defaultRestBetweenSets = defaultRestSeconds,
                        // A rest the builder placed next already covers the gap.
                        defaultRestAfter = if (segment.items.getOrNull(itemIndex + 1) is WorkoutItem.Rest) 0 else defaultRestSeconds,
                    )
                    is WorkoutItem.Rest -> if (item.durationSeconds > 0) {
                        add(
                            WorkoutSessionStep.Rest(
                                segmentIndex, segment.id, item.id, 1, item.durationSeconds,
                                WorkoutSessionRestKind.PLANNED,
                            ),
                        )
                    }
                    is WorkoutItem.Note -> add(WorkoutSessionStep.Note(segmentIndex, segment.id, item.id, 1, item.text))
                    is WorkoutItem.Cardio -> addCardioLegs(segmentIndex, segment, item)
                    is WorkoutItem.Mobility -> addStretchHolds(segmentIndex, segment, item)
                }
            }
        }
        val restAfter = segment.restAfterSeconds ?: 0
        if (restAfter > 0 && segmentIndex < workout.segments.lastIndex) {
            add(
                WorkoutSessionStep.Rest(
                    segmentIndex, segment.id, null, segment.effectiveRounds(), restAfter,
                    WorkoutSessionRestKind.AFTER_SECTION,
                ),
            )
        }
    }
}

private fun MutableList<WorkoutSessionStep>.addStraightSets(
    segmentIndex: Int,
    segment: WorkoutSegment,
    item: WorkoutItem.Weight,
    hasNextItem: Boolean,
    defaultRestBetweenSets: Int,
    defaultRestAfter: Int,
) {
    val sets = item.prescription.resolvedSets()
    // The rest timer's default length stands in where the workout gives no rest.
    val restBetweenSets = item.prescription.restBetweenSetsSeconds?.takeIf { it > 0 } ?: defaultRestBetweenSets
    sets.forEachIndexed { setIndex, target ->
        add(
            WorkoutSessionStep.LiftSet(
                segmentIndex, segment.id, item.id, 1, item.exerciseId, setIndex, sets.size, target,
            ),
        )
        if (setIndex < sets.lastIndex && restBetweenSets > 0) {
            add(
                WorkoutSessionStep.Rest(
                    segmentIndex, segment.id, item.id, 1, restBetweenSets, WorkoutSessionRestKind.BETWEEN_SETS,
                ),
            )
        }
    }
    val restAfter = item.prescription.restAfterExerciseSeconds?.takeIf { it > 0 } ?: defaultRestAfter
    if (hasNextItem && restAfter > 0) {
        add(
            WorkoutSessionStep.Rest(
                segmentIndex, segment.id, item.id, 1, restAfter, WorkoutSessionRestKind.BETWEEN_EXERCISES,
            ),
        )
    }
}

/**
 * Superset and circuit: one set of each exercise per round. Like [WorkoutRunEngine], only the
 * weight items take part; the builder adds nothing else to these sections.
 */
private fun MutableList<WorkoutSessionStep>.addRoundSteps(segmentIndex: Int, segment: WorkoutSegment) {
    val lifts = segment.weightItems()
    if (lifts.isEmpty()) return
    val rounds = segment.effectiveRounds()
    val targets = lifts.associate { it.id to it.prescription.resolvedSets() }
    for (round in 1..rounds) {
        lifts.forEachIndexed { liftIndex, item ->
            val sets = targets.getValue(item.id)
            val target = sets.getOrNull(round - 1) ?: sets.lastOrNull() ?: WeightSet()
            add(
                WorkoutSessionStep.LiftSet(
                    segmentIndex, segment.id, item.id, round, item.exerciseId, round - 1, rounds, target,
                    inRounds = true,
                ),
            )
            val lastInRound = liftIndex == lifts.lastIndex
            val rest = when {
                !lastInRound -> segment.restPolicy.restBetweenItemsSeconds
                round < rounds -> segment.restPolicy.restAfterRoundSeconds
                else -> 0
            }
            if (rest > 0) {
                add(
                    WorkoutSessionStep.Rest(
                        segmentIndex, segment.id, item.id, round, rest,
                        if (lastInRound) WorkoutSessionRestKind.AFTER_ROUND else WorkoutSessionRestKind.BETWEEN_EXERCISES,
                    ),
                )
            }
        }
    }
}

/** Same legs as [resolveCardioLaunch]: recovery between work legs, none after the last. */
private fun MutableList<WorkoutSessionStep>.addCardioLegs(
    segmentIndex: Int,
    segment: WorkoutSegment,
    item: WorkoutItem.Cardio,
) {
    val cardio = item.cardio
    data class Leg(val seconds: Int?, val recovery: Boolean, val label: String? = null, val hr: Int? = null)
    val routineId = cardio.cardioRoutineId?.takeIf { it.isNotBlank() }
    val legs: List<Leg> = when {
        routineId != null -> listOf(Leg(null, false))
        cardio.mode.equals("sprint_intervals", ignoreCase = true) -> {
            val rounds = cardio.rounds?.coerceAtLeast(1) ?: 1
            val work = cardio.workSeconds?.coerceAtLeast(1)
            val rest = cardio.restSeconds?.coerceAtLeast(0) ?: 0
            buildList {
                repeat(rounds) { round ->
                    add(Leg(work, false, hr = cardio.hrTargetBpm))
                    if (rest > 0 && round < rounds - 1) add(Leg(rest, true))
                }
            }
        }
        cardio.mode.equals("interval_template", ignoreCase = true) -> {
            val template = cardio.legs.filter { it.workSeconds > 0 }
            val outer = cardio.outerRounds?.coerceAtLeast(1) ?: 1
            buildList {
                repeat(outer) { outerIndex ->
                    template.forEachIndexed { legIndex, leg ->
                        add(Leg(leg.workSeconds, false, leg.label, leg.hrTargetBpm))
                        val last = legIndex == template.lastIndex && outerIndex == outer - 1
                        if (leg.restSeconds > 0 && !last) add(Leg(leg.restSeconds, true))
                    }
                }
            }
        }
        else -> listOf(Leg(cardio.targetMinutes?.takeIf { it > 0 }?.let { it * 60 }, false, hr = cardio.hrTargetBpm))
    }
    legs.forEachIndexed { legIndex, leg ->
        add(
            WorkoutSessionStep.CardioLeg(
                segmentIndex = segmentIndex,
                segmentId = segment.id,
                itemId = item.id,
                round = 1,
                activity = cardio.activity,
                legIndex = legIndex,
                legCount = legs.size,
                targetSeconds = leg.seconds,
                isRecovery = leg.recovery,
                label = leg.label,
                hrTargetBpm = leg.hr,
                cardioRoutineId = routineId,
            ),
        )
    }
}

private fun MutableList<WorkoutSessionStep>.addStretchHolds(
    segmentIndex: Int,
    segment: WorkoutSegment,
    item: WorkoutItem.Mobility,
) {
    val catalogId = item.mobility.catalogId.takeIf { it.isNotBlank() } ?: return
    val perSide = item.mobility.holdSecondsPerSide?.takeIf { it > 0 }
    if (perSide != null) {
        val seconds = perSide.coerceIn(5, 300)
        for (side in WorkoutStretchSide.entries) {
            add(WorkoutSessionStep.StretchHold(segmentIndex, segment.id, item.id, 1, catalogId, seconds, side))
        }
    } else {
        val seconds = (item.mobility.holdSeconds?.takeIf { it > 0 } ?: DEFAULT_STRETCH_HOLD_SECONDS).coerceIn(5, 300)
        add(WorkoutSessionStep.StretchHold(segmentIndex, segment.id, item.id, 1, catalogId, seconds))
    }
}
