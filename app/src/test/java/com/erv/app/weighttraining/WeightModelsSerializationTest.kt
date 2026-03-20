package com.erv.app.weighttraining

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class WeightModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun weightLibraryState_roundTrip() {
        val routineId = UUID.randomUUID().toString()
        val workoutId = UUID.randomUUID().toString()
        val original = WeightLibraryState(
            exercises = defaultCompoundExercises(),
            routines = listOf(
                WeightRoutine(
                    id = routineId,
                    name = "Push A",
                    exerciseIds = listOf("erv-weight-exercise-bench-v1", "erv-weight-exercise-ohp-v1")
                )
            ),
            logs = listOf(
                WeightDayLog(
                    date = "2026-03-20",
                    workouts = listOf(
                        WeightWorkoutSession(
                            id = workoutId,
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry(
                                    exerciseId = "erv-weight-exercise-bench-v1",
                                    sets = listOf(WeightSet(reps = 5, weightKg = 100.0))
                                )
                            )
                        )
                    )
                )
            )
        )
        val encoded = json.encodeToString(WeightLibraryState.serializer(), original)
        val decoded = json.decodeFromString(WeightLibraryState.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun weightDayLog_unknownKeys_ignored() {
        val raw = """{"date":"2026-01-01","workouts":[],"futureField":true}"""
        val parsed = json.decodeFromString(WeightDayLog.serializer(), raw)
        assertEquals("2026-01-01", parsed.date)
        assertEquals(0, parsed.workouts.size)
    }

    @Test
    fun exercisesGroupedByMuscle_knownOrderThenAlpha() {
        val state = WeightLibraryState(
            exercises = listOf(
                WeightExercise(name = "Z Custom", muscleGroup = "zebra", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
                WeightExercise(name = "Bench", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
                WeightExercise(name = "Antagonist", muscleGroup = "arms", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL)
            )
        )
        val groups = state.exercisesGroupedByMuscle().map { it.first }
        assertEquals(listOf("chest", "arms", "zebra"), groups)
    }

    @Test
    fun toFinishedLiveSession_requiresLoggedSets() {
        val empty = WeightWorkoutDraft(1000L, listOf("erv-weight-exercise-bench-v1"), emptyMap())
        assertNull(empty.toFinishedLiveSession())
        val withSets = WeightWorkoutDraft(
            startedAtEpochSeconds = 1000L,
            exerciseOrder = listOf("erv-weight-exercise-bench-v1"),
            setsByExerciseId = mapOf("erv-weight-exercise-bench-v1" to listOf(WeightSet(reps = 5, weightKg = 60.0))),
            routineId = "r1",
            routineName = "Push"
        )
        val session = withSets.toFinishedLiveSession()
        assertNotNull(session)
        assertEquals(WeightWorkoutSource.LIVE, session!!.source)
        assertEquals("r1", session.routineId)
        assertEquals(1, session.entries.size)
    }

    @Test
    fun buildSessionFromLogEditor_newManual_and_preserveLiveOnEdit() {
        assertNull(
            buildSessionFromLogEditor(
                existing = null,
                exerciseOrder = listOf("e1"),
                setsByExerciseId = mapOf("e1" to listOf(WeightSet(reps = 0, weightKg = null)))
            )
        )
        val manual = buildSessionFromLogEditor(
            existing = null,
            exerciseOrder = listOf("e1"),
            setsByExerciseId = mapOf("e1" to listOf(WeightSet(reps = 8, weightKg = 40.0)))
        )
        assertNotNull(manual)
        assertEquals(WeightWorkoutSource.MANUAL, manual!!.source)
        assertEquals(1, manual.entries.size)

        val live = WeightWorkoutSession(
            id = "keep-id",
            source = WeightWorkoutSource.LIVE,
            startedAtEpochSeconds = 100L,
            finishedAtEpochSeconds = 200L,
            entries = listOf(
                WeightWorkoutEntry("e1", listOf(WeightSet(reps = 5, weightKg = 50.0)))
            )
        )
        val edited = buildSessionFromLogEditor(
            existing = live,
            exerciseOrder = listOf("e1"),
            setsByExerciseId = mapOf("e1" to listOf(WeightSet(reps = 6, weightKg = 52.0)))
        )
        assertNotNull(edited)
        assertEquals("keep-id", edited!!.id)
        assertEquals(WeightWorkoutSource.LIVE, edited.source)
        assertEquals(100L, edited.startedAtEpochSeconds)
        assertEquals(6, edited.entries.first().sets.first().reps)
    }
}
