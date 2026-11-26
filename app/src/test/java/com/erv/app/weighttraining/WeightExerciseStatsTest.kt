package com.erv.app.weighttraining

import com.erv.app.data.BodyWeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeightExerciseStatsTest {

    private fun lb(pounds: Double) = poundsToKg(pounds)

    private fun state(vararg days: Pair<String, List<WeightSet>>, exerciseId: String = "bench") =
        WeightLibraryState(
            logs = days.mapIndexed { i, (date, sets) ->
                WeightDayLog(
                    date,
                    listOf(WeightWorkoutSession(id = "w$i", source = WeightWorkoutSource.LIVE, entries = listOf(WeightWorkoutEntry(exerciseId, sets)))),
                )
            },
        )

    @Test
    fun compactSetsLine_joinsSameWeight_andSplitsWarmups() {
        val entry = WeightWorkoutEntry(
            "bench",
            listOf(
                WeightSet(reps = 5, weightKg = lb(135.0), warmup = true),
                WeightSet(reps = 3, weightKg = lb(185.0), warmup = true),
                WeightSet(reps = 5, weightKg = lb(230.0)),
                WeightSet(reps = 5, weightKg = lb(215.0)),
                WeightSet(reps = 5, weightKg = lb(215.0)),
            ),
        )
        assertEquals("230 lb × 5 · 215 lb × 5, 5", entry.compactSetsLine(BodyWeightUnit.LB, warmups = false))
        assertEquals("135 lb × 5 · 185 lb × 3", entry.compactSetsLine(BodyWeightUnit.LB, warmups = true))
        val pullups = WeightWorkoutEntry("p", listOf(WeightSet(reps = 12), WeightSet(reps = 10)))
        assertEquals("12, 10 reps", pullups.compactSetsLine(BodyWeightUnit.LB, warmups = false))
        assertNull(pullups.compactSetsLine(BodyWeightUnit.LB, warmups = true))
        val plank = WeightWorkoutEntry("k", listOf(WeightSet(durationSeconds = 90)))
        assertEquals("1:30 hold", plank.compactSetsLine(BodyWeightUnit.LB, warmups = false))
    }

    @Test
    fun recordValues_readInPounds() {
        fun record(kind: WeightRecordKind, value: Double, reps: Int? = null, weightKg: Double? = null) =
            WeightRecord("bench", kind, value, "w", LocalDate.of(2026, 11, 1), reps = reps, weightKg = weightKg)
        assertEquals("268 lb", record(WeightRecordKind.EST_ONE_RM, lb(268.33)).formatValue(BodyWeightUnit.LB))
        assertEquals("245 lb × 2", record(WeightRecordKind.HEAVIEST, lb(245.0), reps = 2).formatValue(BodyWeightUnit.LB))
        assertEquals("185 lb", record(WeightRecordKind.TEN_REP_MAX, lb(185.0), reps = 10).formatValue(BodyWeightUnit.LB))
        assertEquals("3,375 lb", record(WeightRecordKind.BEST_VOLUME, lb(3375.0)).formatValue(BodyWeightUnit.LB))
        assertEquals("15 @ 135 lb", record(WeightRecordKind.MOST_REPS, 15.0, reps = 15, weightKg = lb(135.0)).formatValue(BodyWeightUnit.LB))
        assertEquals("12 reps", record(WeightRecordKind.MOST_REPS, 12.0, reps = 12).formatValue(BodyWeightUnit.LB))
        assertEquals("1:30", record(WeightRecordKind.LONGEST_HOLD, 90.0).formatValue(BodyWeightUnit.LB))
    }

    @Test
    fun trend_usesOneRmForLoadedLifts_repsForBodyweight() {
        val loaded = state(
            "2026-11-10" to listOf(WeightSet(reps = 5, weightKg = 100.0)),
            "2026-08-01" to listOf(WeightSet(reps = 5, weightKg = 90.0), WeightSet(reps = 20, weightKg = 60.0)),
        )
        val (metric, points) = loaded.exerciseTrend("bench")!!
        assertEquals(WeightTrendMetric.EST_ONE_RM, metric)
        assertEquals(listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 10)), points.map { it.date })
        assertEquals(105.0, points.first().value, 0.01)

        val bodyweight = state("2026-11-01" to listOf(WeightSet(reps = 12)), exerciseId = "pullup")
        assertEquals(WeightTrendMetric.MOST_REPS, bodyweight.exerciseTrend("pullup")!!.first)
        assertNull(bodyweight.exerciseTrend("bench"))
    }
}
