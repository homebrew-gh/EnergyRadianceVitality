package com.erv.app.weighttraining

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightRecordsTest {

    private val bench = "bench"

    private fun workout(id: String, vararg sets: WeightSet, exerciseId: String = bench) =
        WeightWorkoutSession(
            id = id,
            source = WeightWorkoutSource.LIVE,
            entries = listOf(WeightWorkoutEntry(exerciseId, sets.toList())),
        )

    private fun dated(day: Int, workout: WeightWorkoutSession) =
        DatedWeightWorkout(LocalDate.of(2026, 11, day), workout)

    @Test
    fun estimatedOneRm_isEpley_singleIsItsWeight_highRepsGiveNone() {
        assertEquals(116.67, estimatedOneRmKg(100.0, 5)!!, 0.01)
        assertEquals(100.0, estimatedOneRmKg(100.0, 1)!!, 0.001)
        assertEquals(140.0, estimatedOneRmKg(105.0, 10)!!, 0.01)
        assertNull(estimatedOneRmKg(60.0, 20))
    }

    @Test
    fun warmups_areLeftOutOfRecordsVolumeAndBuckets() {
        val w = workout(
            "w1",
            WeightSet(reps = 10, weightKg = 40.0, warmup = true),
            WeightSet(reps = 5, weightKg = 60.0, warmup = true),
            WeightSet(reps = 5, weightKg = 100.0),
        )
        val records = WeightRecords.recordsOf(w, LocalDate.of(2026, 11, 1), bench).associateBy { it.kind }
        assertEquals(100.0, records.getValue(WeightRecordKind.HEAVIEST).value, 0.001)
        assertEquals(500.0, records.getValue(WeightRecordKind.BEST_VOLUME).value, 0.001)
        assertEquals(5.0, records.getValue(WeightRecordKind.MOST_REPS).value, 0.001)
        assertNull(records[WeightRecordKind.TEN_REP_MAX])
        assertEquals(500.0, w.entries.single().volumeKgForEntry(), 0.001)
        assertEquals(1, w.entries.single().workingSetCount())

        val state = WeightLibraryState(logs = listOf(WeightDayLog("2026-11-01", listOf(w))))
        val buckets = state.maxWeightByRepBucketKg(bench)
        assertNull(buckets[9].second)
        assertEquals(100.0, buckets[4].second!!, 0.001)
    }

    @Test
    fun best_keepsEachKindWithItsDateAndSet() {
        val all = listOf(
            dated(1, workout("a", WeightSet(reps = 5, weightKg = 100.0), WeightSet(reps = 10, weightKg = 80.0))),
            dated(8, workout("b", WeightSet(reps = 2, weightKg = 110.0))),
            dated(15, workout("c", WeightSet(reps = 12, weightKg = 82.0))),
        )
        val best = WeightRecords.best(all, bench).associateBy { it.kind }
        assertEquals(110.0, best.getValue(WeightRecordKind.HEAVIEST).value, 0.001)
        assertEquals("b", best.getValue(WeightRecordKind.HEAVIEST).workoutId)
        assertEquals(82.0, best.getValue(WeightRecordKind.TEN_REP_MAX).value, 0.001)
        assertEquals(12, best.getValue(WeightRecordKind.TEN_REP_MAX).reps)
        // 82 x 12 = 114.8, 110 x 2 = 117.3, 100 x 5 = 116.7
        assertEquals("b", best.getValue(WeightRecordKind.EST_ONE_RM).workoutId)
        assertEquals(LocalDate.of(2026, 11, 1), best.getValue(WeightRecordKind.BEST_VOLUME).date)
    }

    @Test
    fun newRecords_onlyWhenBeatingAnEarlierOne() {
        val first = workout("a", WeightSet(reps = 5, weightKg = 100.0))
        val firstOnly = WeightRecords.newRecords(listOf(dated(1, first)), first, LocalDate.of(2026, 11, 1))
        assertTrue(firstOnly.isEmpty())

        val next = workout("b", WeightSet(reps = 5, weightKg = 105.0), WeightSet(reps = 10, weightKg = 70.0))
        val found = WeightRecords.newRecords(
            listOf(dated(1, first), dated(8, next)),
            next,
            LocalDate.of(2026, 11, 8),
        ).associateBy { it.record.kind }
        assertEquals(105.0, found.getValue(WeightRecordKind.HEAVIEST).record.value, 0.001)
        assertEquals(100.0, found.getValue(WeightRecordKind.HEAVIEST).previous.value, 0.001)
        assertTrue(WeightRecordKind.EST_ONE_RM in found)
        assertTrue(WeightRecordKind.MOST_REPS in found)
        // A first set of 10 has nothing to beat.
        assertTrue(WeightRecordKind.TEN_REP_MAX !in found)
    }

    @Test
    fun heaviest_tieGoesToMoreReps_holdsAndBodyweightCount() {
        val all = listOf(
            dated(1, workout("a", WeightSet(reps = 3, weightKg = 100.0))),
            dated(2, workout("b", WeightSet(reps = 5, weightKg = 100.0))),
            dated(3, workout("p", WeightSet(reps = 12), exerciseId = "pullup")),
            dated(4, workout("k", WeightSet(durationSeconds = 90), exerciseId = "plank")),
        )
        assertEquals("b", WeightRecords.best(all, bench).first { it.kind == WeightRecordKind.HEAVIEST }.workoutId)
        val pullups = WeightRecords.best(all, "pullup")
        assertEquals(listOf(WeightRecordKind.MOST_REPS), pullups.map { it.kind })
        val plank = WeightRecords.best(all, "plank")
        assertEquals(90.0, plank.single { it.kind == WeightRecordKind.LONGEST_HOLD }.value, 0.001)
    }

    @Test
    fun exerciseDoneTwiceInOneWorkout_countsBothEntries() {
        val w = WeightWorkoutSession(
            id = "w",
            source = WeightWorkoutSource.LIVE,
            entries = listOf(
                WeightWorkoutEntry(bench, listOf(WeightSet(reps = 5, weightKg = 100.0))),
                WeightWorkoutEntry("row", listOf(WeightSet(reps = 8, weightKg = 60.0))),
                WeightWorkoutEntry(bench, listOf(WeightSet(reps = 3, weightKg = 120.0))),
            ),
        )
        val state = WeightLibraryState(logs = listOf(WeightDayLog("2026-11-01", listOf(w))))
        val row = state.historyForExercise(bench).single()
        assertEquals(2, row.entry.sets.size)
        assertEquals(120.0, state.maxWeightByRepBucketKg(bench)[2].second!!, 0.001)
        val heaviest = WeightRecords.recordsOf(w, LocalDate.of(2026, 11, 1), bench)
            .first { it.kind == WeightRecordKind.HEAVIEST }
        assertEquals(120.0, heaviest.value, 0.001)
    }

    @Test
    fun warmupFlag_isOmittedFromJsonUnlessSet() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val plain = json.encodeToString(WeightSet.serializer(), WeightSet(reps = 5, weightKg = 100.0))
        assertTrue("warmup" !in plain)
        val warm = json.encodeToString(WeightSet.serializer(), WeightSet(reps = 5, weightKg = 60.0, warmup = true))
        assertTrue("\"warmup\":true" in warm)
        val back = json.decodeFromString(WeightSet.serializer(), warm)
        assertTrue(back.isWarmup())
    }
}
