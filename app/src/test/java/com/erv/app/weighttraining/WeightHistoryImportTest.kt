package com.erv.app.weighttraining

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightHistoryImportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun merge_appendsImportedSessions_andForcesSource() {
        val current = WeightLibraryState(
            exercises = defaultCompoundExercises(),
            logs = emptyList()
        )
        val envelope = ErvWeightHistoryImportEnvelope(
            ervWeightHistoryImportVersion = 1,
            exercises = emptyList(),
            dayLogs = listOf(
                WeightDayLog(
                    date = "2024-01-15",
                    workouts = listOf(
                        WeightWorkoutSession(
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry(
                                    exerciseId = "erv-weight-exercise-bench-v1",
                                    sets = listOf(WeightSet(reps = 5, weightKg = 80.0))
                                )
                            )
                        )
                    )
                )
            )
        )
        val outcome = WeightHistoryImport.merge(current, envelope) as WeightImportOutcome.Success
        assertEquals(1, outcome.sessionsImported)
        val day = outcome.newState.logFor(java.time.LocalDate.parse("2024-01-15"))!!
        assertEquals(1, day.workouts.size)
        assertEquals(WeightWorkoutSource.IMPORTED, day.workouts.first().source)
        assertTrue(day.workouts.first().id.isNotEmpty())
    }

    @Test
    fun merge_failsOnUnknownExerciseId() {
        val current = WeightLibraryState(exercises = defaultCompoundExercises(), logs = emptyList())
        val envelope = ErvWeightHistoryImportEnvelope(
            dayLogs = listOf(
                WeightDayLog(
                    date = "2024-01-01",
                    workouts = listOf(
                        WeightWorkoutSession(
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry("not-a-real-id", listOf(WeightSet(5, 1.0)))
                            )
                        )
                    )
                )
            )
        )
        val outcome = WeightHistoryImport.merge(current, envelope) as WeightImportOutcome.Failure
        assertTrue(outcome.messages.any { it.contains("not-a-real-id") })
    }

    @Test
    fun csv_buildsEnvelope_andMerge() {
        val csv = """
            date,session_key,exercise_id,set_index,reps,weight_kg
            2024-02-01,a,erv-weight-exercise-squat-v1,1,5,100
            2024-02-01,a,erv-weight-exercise-squat-v1,2,5,100
        """.trimIndent()
        val (env, err) = WeightCsvHistoryImport.parse(csv)
        assertTrue(err.isEmpty())
        assertTrue(env != null)
        val current = WeightLibraryState(exercises = defaultCompoundExercises(), logs = emptyList())
        val outcome = WeightHistoryImport.merge(current, env!!) as WeightImportOutcome.Success
        assertEquals(1, outcome.sessionsImported)
    }

    @Test
    fun sessionsAddedByImport_listsOnlyAppendedWorkouts() {
        val current = WeightLibraryState(
            exercises = defaultCompoundExercises(),
            logs = listOf(
                WeightDayLog(
                    date = "2024-01-15",
                    workouts = listOf(
                        WeightWorkoutSession(
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry(
                                    "erv-weight-exercise-squat-v1",
                                    listOf(WeightSet(1, 50.0))
                                )
                            )
                        )
                    )
                )
            )
        )
        val envelope = ErvWeightHistoryImportEnvelope(
            dayLogs = listOf(
                WeightDayLog(
                    date = "2024-01-15",
                    workouts = listOf(
                        WeightWorkoutSession(
                            source = WeightWorkoutSource.MANUAL,
                            entries = listOf(
                                WeightWorkoutEntry(
                                    "erv-weight-exercise-bench-v1",
                                    listOf(WeightSet(3, 60.0))
                                )
                            )
                        )
                    )
                )
            )
        )
        val success = WeightHistoryImport.merge(current, envelope) as WeightImportOutcome.Success
        val added = WeightHistoryImport.sessionsAddedByImport(current, success)
        assertEquals(1, added.size)
        assertEquals("2024-01-15", added.first().dateIso)
        assertEquals(WeightWorkoutSource.IMPORTED, added.first().session.source)
    }

    @Test
    fun importedSource_serializesRoundTrip() {
        val session = WeightWorkoutSession(
            source = WeightWorkoutSource.IMPORTED,
            entries = listOf(
                WeightWorkoutEntry("erv-weight-exercise-bench-v1", listOf(WeightSet(3, 50.0)))
            )
        )
        val s = json.encodeToString(WeightWorkoutSession.serializer(), session)
        val back = json.decodeFromString(WeightWorkoutSession.serializer(), s)
        assertEquals(WeightWorkoutSource.IMPORTED, back.source)
    }
}
