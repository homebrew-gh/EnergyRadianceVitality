package com.erv.app.workouts

/**
 * Advances [WorkoutRunPosition] through segment/item/round order for live run.
 */
object WorkoutRunEngine {

    data class Step(
        val position: WorkoutRunPosition,
        val segment: WorkoutSegment,
        val item: WorkoutItem?,
        val label: String,
        val isComplete: Boolean = false,
    )

    data class WorkoutPendingRest(
        val seconds: Int,
        val label: String,
    )

    fun currentStep(workout: Workout, position: WorkoutRunPosition): Step? {
        if (workout.segments.isEmpty()) {
            return Step(position, emptySegment(), null, "No segments", isComplete = true)
        }
        val segmentIndex = position.segmentIndex.coerceIn(0, workout.segments.lastIndex)
        val segment = workout.segments[segmentIndex]
        if (position.segmentIndex >= workout.segments.size) {
            return Step(position, segment, null, "Workout complete", isComplete = true)
        }
        val weightItems = segment.weightItems()
        return when (segment.kind) {
            WorkoutSegmentKind.CIRCUIT, WorkoutSegmentKind.SUPERSET -> {
                if (weightItems.isEmpty()) {
                    segmentStep(workout, position, segment, null, "Empty ${segment.kind.name.lowercase()}")
                } else {
                    val itemIndex = position.itemIndex.coerceIn(0, weightItems.lastIndex)
                    val item = weightItems[itemIndex]
                    val rounds = segment.effectiveRounds()
                    val round = position.round.coerceIn(1, rounds)
                    val label = buildString {
                        append(segment.title ?: segment.kind.name.replace('_', ' ').lowercase())
                        append(" · Round $round/$rounds")
                        append(" · ${itemIndex + 1}/${weightItems.size}")
                    }
                    Step(position, segment, item, label)
                }
            }
            else -> {
                val itemIndex = position.itemIndex
                if (itemIndex >= segment.items.size) {
                    segmentStep(workout, position, segment, null, segment.title ?: "Segment done")
                } else {
                    val item = segment.items[itemIndex]
                    val total = segment.items.size
                    val progress = if (total > 1) " · ${itemIndex + 1}/$total" else ""
                    val itemLabel = when (item) {
                        is WorkoutItem.Note -> "Note"
                        is WorkoutItem.Rest -> "Rest ${item.durationSeconds}s"
                        is WorkoutItem.Weight -> item.title?.takeIf { it.isNotBlank() } ?: "Lift"
                        is WorkoutItem.Cardio -> item.title?.takeIf { it.isNotBlank() } ?: "Cardio"
                        is WorkoutItem.Mobility -> item.title?.takeIf { it.isNotBlank() } ?: "Mobility"
                    }
                    val label = buildString {
                        append(segment.title ?: segmentKindLabel(segment.kind))
                        append(progress)
                        append(" · $itemLabel")
                    }
                    Step(
                        position = position,
                        segment = segment,
                        item = item,
                        label = label,
                    )
                }
            }
        }
    }

    fun advance(workout: Workout, position: WorkoutRunPosition): WorkoutRunPosition {
        if (workout.segments.isEmpty()) return position
        val segmentIndex = position.segmentIndex.coerceIn(0, workout.segments.lastIndex)
        val segment = workout.segments[segmentIndex]
        val weightItems = segment.weightItems()

        when (segment.kind) {
            WorkoutSegmentKind.CIRCUIT, WorkoutSegmentKind.SUPERSET -> {
                if (weightItems.isEmpty()) return nextSegment(workout, position)
                val rounds = segment.effectiveRounds()
                val nextItemIndex = position.itemIndex + 1
                if (nextItemIndex < weightItems.size) {
                    return position.copy(itemIndex = nextItemIndex)
                }
                val nextRound = position.round + 1
                if (nextRound <= rounds) {
                    return position.copy(itemIndex = 0, round = nextRound)
                }
                return nextSegment(workout, position)
            }
            else -> {
                val nextItemIndex = position.itemIndex + 1
                if (nextItemIndex < segment.items.size) {
                    return position.copy(itemIndex = nextItemIndex)
                }
                return nextSegment(workout, position)
            }
        }
    }

    fun isWorkoutComplete(workout: Workout, position: WorkoutRunPosition): Boolean {
        if (workout.segments.isEmpty()) return true
        return position.segmentIndex >= workout.segments.size
    }

    /**
     * Consecutive [WorkoutItem.Weight] items starting at [position] within a plain (non-circuit)
     * segment. Used to batch a section's lifts into a single live session. Returns an empty list
     * when the current step is not a plain weight item.
     */
    fun consecutiveWeightItemRun(
        workout: Workout,
        position: WorkoutRunPosition,
    ): List<WorkoutItem.Weight> {
        if (workout.segments.isEmpty()) return emptyList()
        val segmentIndex = position.segmentIndex
        if (segmentIndex !in workout.segments.indices) return emptyList()
        val segment = workout.segments[segmentIndex]
        if (segment.kind == WorkoutSegmentKind.CIRCUIT || segment.kind == WorkoutSegmentKind.SUPERSET) {
            return emptyList()
        }
        val items = segment.items
        val start = position.itemIndex
        if (start !in items.indices) return emptyList()
        val run = mutableListOf<WorkoutItem.Weight>()
        var idx = start
        while (idx < items.size) {
            val item = items[idx]
            if (item is WorkoutItem.Weight) {
                run.add(item)
                idx++
            } else {
                break
            }
        }
        return run
    }

    /** Advance [steps] times in sequence (used after a batched section completes). */
    fun advanceBy(workout: Workout, position: WorkoutRunPosition, steps: Int): WorkoutRunPosition {
        var current = position
        repeat(steps.coerceAtLeast(1)) {
            current = advance(workout, current)
        }
        return current
    }

    /**
     * Rest to show after the athlete finishes the item at [position], before [advance].
     * Returns null when the next step should begin immediately.
     */
    fun pendingRestBeforeAdvance(
        workout: Workout,
        position: WorkoutRunPosition,
    ): WorkoutPendingRest? {
        if (workout.segments.isEmpty()) return null
        val segmentIndex = position.segmentIndex
        if (segmentIndex >= workout.segments.size) return null
        val segment = workout.segments[segmentIndex]
        val weightItems = segment.weightItems()

        when (segment.kind) {
            WorkoutSegmentKind.CIRCUIT, WorkoutSegmentKind.SUPERSET -> {
                if (weightItems.isNotEmpty()) {
                    val itemIndex = position.itemIndex.coerceIn(0, weightItems.lastIndex)
                    val rounds = segment.effectiveRounds()
                    val isLastItemInRound = itemIndex >= weightItems.lastIndex
                    if (!isLastItemInRound) {
                        val seconds = segment.restPolicy.restBetweenItemsSeconds
                        if (seconds > 0) {
                            return WorkoutPendingRest(
                                seconds = seconds,
                                label = "Rest · next exercise",
                            )
                        }
                    } else if (position.round < rounds) {
                        val seconds = segment.restPolicy.restAfterRoundSeconds
                        if (seconds > 0) {
                            return WorkoutPendingRest(
                                seconds = seconds,
                                label = "Rest · round ${position.round + 1}",
                            )
                        }
                    }
                }
            }
            else -> Unit
        }

        if (segment.kind != WorkoutSegmentKind.CIRCUIT && segment.kind != WorkoutSegmentKind.SUPERSET) {
            val items = segment.items
            val itemIndex = position.itemIndex
            if (itemIndex in items.indices) {
                val current = items[itemIndex]
                if (current is WorkoutItem.Weight) {
                    val restAfter = current.prescription.restAfterExerciseSeconds ?: 0
                    if (restAfter > 0 && itemIndex + 1 < items.size) {
                        return WorkoutPendingRest(
                            seconds = restAfter,
                            label = "Rest · next exercise",
                        )
                    }
                }
            }
        }

        val nextPosition = advance(workout, position)
        if (nextPosition.segmentIndex > segmentIndex) {
            val seconds = segment.restAfterSeconds ?: 0
            if (seconds > 0) {
                return WorkoutPendingRest(
                    seconds = seconds,
                    label = "Rest · next segment",
                )
            }
        }
        return null
    }

    /** Passive rest item inside a segment storyboard. */
    fun restDurationForItem(item: WorkoutItem?): Int? =
        (item as? WorkoutItem.Rest)?.durationSeconds?.takeIf { it > 0 }

    private fun nextSegment(workout: Workout, position: WorkoutRunPosition): WorkoutRunPosition =
        WorkoutRunPosition(
            segmentIndex = position.segmentIndex + 1,
            itemIndex = 0,
            round = 1,
        )

    private fun segmentStep(
        workout: Workout,
        position: WorkoutRunPosition,
        segment: WorkoutSegment,
        item: WorkoutItem?,
        label: String,
    ): Step {
        val atEnd = position.itemIndex >= segment.items.size && segment.weightItems().isEmpty()
        return if (atEnd && position.segmentIndex + 1 >= workout.segments.size) {
            Step(position.copy(segmentIndex = workout.segments.size), segment, item, "Workout complete", isComplete = true)
        } else {
            Step(position, segment, item, label)
        }
    }

    private fun emptySegment() = WorkoutSegment(kind = WorkoutSegmentKind.STRAIGHT_SETS)

    private fun segmentKindLabel(kind: WorkoutSegmentKind): String = when (kind) {
        WorkoutSegmentKind.STRAIGHT_SETS -> "Straight sets"
        WorkoutSegmentKind.COMPOSITE -> "Flow block"
        WorkoutSegmentKind.CARDIO -> "Cardio"
        WorkoutSegmentKind.INTERVAL -> "Intervals"
        WorkoutSegmentKind.MOBILITY -> "Mobility"
        else -> kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
    }
}
