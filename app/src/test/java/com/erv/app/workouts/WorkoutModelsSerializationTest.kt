package com.erv.app.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutModelsSerializationTest {

    @Test
    fun roundTrip_timed_prep_seconds_on_prescription() {
        val workout = Workout(
            name = "Carries",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(
                            exerciseId = "erv-weight-exercise-db-farmers-carry-v1",
                            prescription = WorkoutWeightPrescription(
                                mode = WorkoutWeightPrescriptionMode.TIME_BASED,
                                setCount = 3,
                                durationSeconds = 45,
                                timedPrepSeconds = 10,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val encoded = encodeWorkoutImportEnvelope(WorkoutImportEnvelope(workouts = listOf(workout)))
        val decoded = decodeWorkoutImportEnvelope(encoded).getOrThrow()
        val prescription = decoded.workouts.first().segments.first().weightItems().single().prescription
        assertEquals(10, prescription.timedPrepSeconds)
        assertEquals(45, prescription.durationSeconds)
    }

    @Test
    fun roundTrip_circuit_segment_with_weight_items() {
        val workout = Workout(
            name = "Push + core",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.CIRCUIT,
                    title = "Accessory circuit",
                    rounds = 3,
                    restPolicy = WorkoutRestPolicy(
                        restBetweenItemsSeconds = 0,
                        restAfterRoundSeconds = 90,
                    ),
                    items = listOf(
                        WorkoutItem.Weight(
                            exerciseId = "erv-weight-exercise-bench-v1",
                            prescription = WorkoutWeightPrescription(
                                setCount = 1,
                                repRangeMin = 12,
                                repRangeMax = 12,
                            ),
                        ),
                        WorkoutItem.Weight(
                            exerciseId = "erv-weight-exercise-bw-plank-v1",
                            prescription = WorkoutWeightPrescription(
                                mode = WorkoutWeightPrescriptionMode.TIME_BASED,
                                durationSeconds = 45,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val envelope = WorkoutImportEnvelope(workouts = listOf(workout))
        val encoded = encodeWorkoutImportEnvelope(envelope)
        val decoded = decodeWorkoutImportEnvelope(encoded).getOrThrow()
        assertEquals(1, decoded.workouts.size)
        val segment = decoded.workouts.first().segments.first()
        assertEquals(WorkoutSegmentKind.CIRCUIT, segment.kind)
        assertEquals(3, segment.rounds)
        assertEquals(2, segment.weightItems().size)
        assertTrue(segment.weightItems()[1].prescription.durationSeconds == 45)
    }

    @Test
    fun roundTrip_straight_sets_with_rest_and_note_items() {
        val workout = Workout(
            name = "Upper",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.STRAIGHT_SETS,
                    items = listOf(
                        WorkoutItem.Weight(
                            exerciseId = "erv-weight-exercise-bench-v1",
                            prescription = WorkoutWeightPrescription(
                                setCount = 3,
                                repRangeMin = 8,
                                repRangeMax = 12,
                                targetRir = 2,
                                restBetweenSetsSeconds = 90,
                                restAfterExerciseSeconds = 120,
                            ),
                        ),
                        WorkoutItem.Rest(durationSeconds = 60),
                        WorkoutItem.Note(text = "Focus on tempo"),
                    ),
                ),
            ),
        )
        val encoded = encodeWorkoutImportEnvelope(WorkoutImportEnvelope(workouts = listOf(workout)))
        val decoded = decodeWorkoutImportEnvelope(encoded).getOrThrow().workouts.first()
        val item = decoded.segments.first().items.first() as WorkoutItem.Weight
        assertEquals(120, item.prescription.restAfterExerciseSeconds)
        assertTrue(decoded.segments.first().items[1] is WorkoutItem.Rest)
    }

    @Test
    fun roundTrip_composite_flow_with_cardio_and_mobility() {
        val workout = Workout(
            name = "Full session",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.COMPOSITE,
                    title = "Warm-up",
                    items = listOf(
                        WorkoutItem.Cardio(
                            cardio = WorkoutCardioPrescription(
                                activity = "STATIONARY_BIKE",
                                targetMinutes = 10,
                                hrTargetBpm = 115,
                            ),
                        ),
                        WorkoutItem.Mobility(
                            mobility = WorkoutMobilityPrescription(
                                catalogId = "builtin_hamstring_stretch",
                                holdSeconds = 45,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val encoded = encodeWorkoutImportEnvelope(WorkoutImportEnvelope(workouts = listOf(workout)))
        val decoded = decodeWorkoutImportEnvelope(encoded).getOrThrow().workouts.first()
        val items = decoded.segments.first().items
        assertTrue(items[0] is WorkoutItem.Cardio)
        assertTrue(items[1] is WorkoutItem.Mobility)
    }

    @Test
    fun roundTrip_interval_cardio_prescription() {
        val workout = Workout(
            name = "HIIT day",
            segments = listOf(
                WorkoutSegment(
                    kind = WorkoutSegmentKind.INTERVAL,
                    items = listOf(
                        WorkoutItem.Cardio(
                            cardio = WorkoutCardioPrescription(
                                activity = "STATIONARY_BIKE",
                                mode = "sprint_intervals",
                                rounds = 8,
                                workSeconds = 45,
                                restSeconds = 30,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val encoded = encodeWorkoutImportEnvelope(WorkoutImportEnvelope(workouts = listOf(workout)))
        val decoded = decodeWorkoutImportEnvelope(encoded).getOrThrow().workouts.first()
        val cardio = decoded.segments.first().items.first() as WorkoutItem.Cardio
        assertEquals("sprint_intervals", cardio.cardio.mode)
        assertEquals(8, cardio.cardio.rounds)
        assertEquals(45, cardio.cardio.workSeconds)
    }

    @Test
    fun mergeWorkoutImport_replaces_by_id() {
        val original = Workout(id = "w1", name = "Old")
        val updated = Workout(id = "w1", name = "New")
        val merged = mergeWorkoutImport(
            WorkoutLibraryState(workouts = listOf(original)),
            WorkoutImportEnvelope(workouts = listOf(updated)),
        )
        assertEquals("New", merged.workouts.single().name)
    }
}
