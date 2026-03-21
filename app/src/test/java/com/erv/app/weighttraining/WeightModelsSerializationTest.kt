package com.erv.app.weighttraining

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
    fun exerciseIdsUsedInAnyLog_collectsDistinct() {
        val state = WeightLibraryState(
            exercises = defaultCompoundExercises(),
            logs = listOf(
                WeightDayLog(
                    date = "2026-01-01",
                    workouts = listOf(
                        WeightWorkoutSession(
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry("e1", emptyList()),
                                WeightWorkoutEntry("e2", emptyList())
                            )
                        )
                    )
                )
            )
        )
        assertEquals(setOf("e1", "e2"), state.exerciseIdsUsedInAnyLog())
    }

    @Test
    fun defaultCatalogExercises_uniqueIds_and_includesCompounds() {
        val catalog = defaultCatalogExercises()
        assertTrue(catalog.size >= 80)
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bench-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-squat-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bw-pullup-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bw-chinup-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bw-dip-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bb-ezbar-curl-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bw-hanging-leg-raise-v1" })
        assertTrue(catalog.any { it.id == "erv-weight-exercise-bw-ab-wheel-rollout-v1" })
    }

    @Test
    fun exercisesGroupedByMuscle_knownOrderThenAlpha() {
        val list = listOf(
            WeightExercise(name = "Z Custom", muscleGroup = "zebra", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.DUMBBELL),
            WeightExercise(name = "Bench", muscleGroup = "chest", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.BARBELL),
            WeightExercise(name = "Curl", muscleGroup = "biceps", pushOrPull = WeightPushPull.PULL, equipment = WeightEquipment.DUMBBELL),
            WeightExercise(name = "Pushdown", muscleGroup = "triceps", pushOrPull = WeightPushPull.PUSH, equipment = WeightEquipment.MACHINE)
        )
        val state = WeightLibraryState(exercises = list)
        val groups = state.exercisesGroupedByMuscle().map { it.first }
        assertEquals(listOf("chest", "biceps", "triceps", "zebra"), groups)
        assertEquals(groups, groupExercisesByMuscle(list).map { it.first })
    }

    @Test
    fun groupExercisesByMuscle_emptyList() {
        assertTrue(groupExercisesByMuscle(emptyList()).isEmpty())
    }

    @Test
    fun withMigratedArmsMuscleGroup_mapsBuiltinCatalogIds_only() {
        val curl = WeightExercise(
            id = "erv-weight-exercise-bb-barbell-curl-v1",
            name = "Barbell Curl",
            muscleGroup = "arms",
            pushOrPull = WeightPushPull.PULL,
            equipment = WeightEquipment.BARBELL
        )
        assertEquals("biceps", curl.withMigratedArmsMuscleGroup().muscleGroup)
        val pushdown = WeightExercise(
            id = "erv-weight-exercise-m-tricep-pushdown-v1",
            name = "Tricep Pushdown",
            muscleGroup = "arms",
            pushOrPull = WeightPushPull.PUSH,
            equipment = WeightEquipment.MACHINE
        )
        assertEquals("triceps", pushdown.withMigratedArmsMuscleGroup().muscleGroup)
        val customStillArms = WeightExercise(
            id = "custom-arm-day",
            name = "Custom",
            muscleGroup = "arms",
            pushOrPull = WeightPushPull.PULL,
            equipment = WeightEquipment.OTHER
        )
        assertEquals("arms", customStillArms.withMigratedArmsMuscleGroup().muscleGroup)
    }

    @Test
    fun estimatedOneRmKg_100kg5reps() {
        val e = estimatedOneRmKg(100.0, 5)
        assertNotNull(e)
        assertEquals(116.67, e!!, 0.05)
    }

    @Test
    fun withRebuiltExerciseSessionSummaries_populatesFromLogs() {
        val benchId = "erv-weight-exercise-bench-v1"
        val state = WeightLibraryState(
            exercises = defaultCompoundExercises(),
            logs = listOf(
                WeightDayLog(
                    date = "2026-03-20",
                    workouts = listOf(
                        WeightWorkoutSession(
                            id = "w1",
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry(benchId, listOf(WeightSet(reps = 5, weightKg = 100.0)))
                            )
                        )
                    )
                )
            )
        )
        val rebuilt = state.withRebuiltExerciseSessionSummaries()
        val bench = rebuilt.exercises.first { it.id == benchId }
        assertEquals(1, bench.sessionSummaries.size)
        val s = bench.sessionSummaries.first()
        assertEquals("2026-03-20", s.date)
        assertEquals("w1", s.workoutId)
        assertEquals(500.0, s.volumeKg, 0.001)
        assertNotNull(s.bestEstOneRmKg)
        assertEquals(1, s.workingSetCount)
    }

    @Test
    fun weightExercise_sessionSummaries_roundTrip() {
        val ex = WeightExercise(
            name = "Test",
            muscleGroup = "chest",
            pushOrPull = WeightPushPull.PUSH,
            equipment = WeightEquipment.BARBELL,
            sessionSummaries = listOf(
                WeightExerciseSessionSummary(
                    date = "2026-01-01",
                    workoutId = "wid",
                    volumeKg = 300.0,
                    bestEstOneRmKg = 110.0,
                    workingSetCount = 3
                )
            )
        )
        val encoded = json.encodeToString(WeightExercise.serializer(), ex)
        val decoded = json.decodeFromString(WeightExercise.serializer(), encoded)
        assertEquals(ex, decoded)
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

    @Test
    fun historyForExercise_sortsNewestFirst_and_filters() {
        val bench = "erv-weight-exercise-bench-v1"
        val squat = "erv-weight-exercise-squat-v1"
        val w1 = WeightWorkoutSession(
            id = "w1",
            source = WeightWorkoutSource.MANUAL,
            entries = listOf(WeightWorkoutEntry(bench, listOf(WeightSet(5, 100.0))))
        )
        val w2 = WeightWorkoutSession(
            id = "w2",
            source = WeightWorkoutSource.LIVE,
            startedAtEpochSeconds = 500L,
            finishedAtEpochSeconds = 600L,
            entries = listOf(
                WeightWorkoutEntry(squat, listOf(WeightSet(3, 60.0))),
                WeightWorkoutEntry(bench, listOf(WeightSet(3, 105.0)))
            )
        )
        val state = WeightLibraryState(
            logs = listOf(
                WeightDayLog("2026-03-18", listOf(w1)),
                WeightDayLog("2026-03-20", listOf(w2))
            )
        )
        val h = state.historyForExercise(bench)
        assertEquals(2, h.size)
        assertEquals(LocalDate.parse("2026-03-20"), h[0].logDate)
        assertEquals("w2", h[0].workout.id)
        assertEquals(105.0, h[0].entry.sets.first().weightKg!!, 0.001)
        assertEquals(LocalDate.parse("2026-03-18"), h[1].logDate)
        assertEquals(1, state.historyForExercise(squat).size)
    }
}
