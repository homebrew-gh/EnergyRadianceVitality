package com.erv.app.weighttraining

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
}
