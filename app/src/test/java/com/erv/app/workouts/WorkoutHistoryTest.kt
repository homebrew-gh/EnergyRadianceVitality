package com.erv.app.workouts

import com.erv.app.cardio.CardioActivityRow
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioHrScaffolding
import com.erv.app.cardio.CardioSession
import com.erv.app.stretching.StretchActivityRow
import com.erv.app.stretching.StretchSession
import com.erv.app.weighttraining.WeightActivityRow
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A mixed workout's silo logs come back together as one history entry. */
class WorkoutHistoryTest {

    private fun link(sessionId: String, workoutId: String = "w-mixed", hr: CardioHrScaffolding? = null) =
        WorkoutSessionLink(
            sessionId = sessionId,
            workoutId = workoutId,
            segmentId = "seg",
            itemId = "item",
            displayRef = "WR-1",
            sessionHeartRate = hr,
        )

    private fun lifting(start: Long, end: Long, link: WorkoutSessionLink?) = WeightActivityRow(
        headerLine = "Live",
        exerciseBlocks = emptyList(),
        session = WeightWorkoutSession(
            id = "lift-$start",
            source = WeightWorkoutSource.LIVE,
            startedAtEpochSeconds = start,
            finishedAtEpochSeconds = end,
            workoutLink = link,
        ),
    )

    private fun cardio(start: Long, minutes: Int, link: WorkoutSessionLink?) = CardioActivityRow(
        summaryLine = "Bike • $minutes min",
        session = CardioSession(
            id = "cardio-$start",
            activity = CardioActivitySnapshot(builtin = CardioBuiltinActivity.BIKE, displayLabel = "Bike"),
            durationMinutes = minutes,
            startEpochSeconds = start,
            endEpochSeconds = start + minutes * 60L,
            loggedAtEpochSeconds = start + minutes * 60L,
            workoutLink = link,
        ),
    )

    private fun stretch(loggedAt: Long, minutes: Int, link: WorkoutSessionLink?) = StretchActivityRow(
        summaryLine = "Stretch session • $minutes min",
        session = StretchSession(id = "stretch-$loggedAt", totalMinutes = minutes, loggedAtEpochSeconds = loggedAt, workoutLink = link),
    )

    @Test
    fun a_mixed_workout_is_one_entry_with_its_sections_in_order() {
        val run = link("run-1", hr = CardioHrScaffolding(avgBpm = 131))
        val warmUp = cardio(1_000, 10, run)
        val lifts = lifting(1_700, 3_500, run)
        val coolDown = stretch(4_000, 5, run)
        val solo = lifting(10_000, 11_000, null)
        val day = groupDayActivityByWorkout(
            cardio = listOf(warmUp),
            weight = listOf(lifts, solo),
            stretch = listOf(coolDown),
        ) { id -> if (id == "w-mixed") "Full Body" else null }

        val entry = day.workouts.single()
        assertEquals("Full Body", entry.title)
        assertEquals(
            listOf("cardio-1000", "lift-1700", "stretch-4000"),
            entry.sections.map {
                when (it) {
                    is WorkoutHistorySection.Lifting -> it.row.session.id
                    is WorkoutHistorySection.Cardio -> it.row.session.id
                    is WorkoutHistorySection.Stretching -> it.row.session.id
                }
            },
        )
        // 1000 to 4000 is 50 minutes.
        assertEquals("3 sections · 50 min · avg 131 bpm", entry.headerLine)
        // Nothing from the workout is listed again under its silo; the standalone lift still is.
        assertTrue(day.cardio.isEmpty())
        assertTrue(day.stretch.isEmpty())
        assertEquals(listOf(solo), day.weight)
    }

    @Test
    fun two_runs_of_the_same_workout_stay_separate() {
        val morning = lifting(1_000, 2_000, link("run-am"))
        val evening = lifting(50_000, 51_200, link("run-pm"))
        val day = groupDayActivityByWorkout(emptyList(), listOf(evening, morning), emptyList()) { null }
        assertEquals(listOf("run-am", "run-pm"), day.workouts.map { it.sessionId })
        // A workout that has since been deleted still gets a name.
        assertEquals("Workout", day.workouts.first().title)
        assertEquals("1 section · 16 min", day.workouts.first().headerLine)
    }
}
