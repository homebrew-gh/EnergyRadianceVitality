package com.erv.app.workouts

import com.erv.app.cardio.CardioLibraryState
import com.erv.app.stretching.StretchDayLog
import com.erv.app.stretching.StretchLibraryState
import com.erv.app.stretching.StretchSession
import com.erv.app.weighttraining.WeightDayLog
import com.erv.app.weighttraining.WeightLibraryState
import com.erv.app.weighttraining.WeightWorkoutSession
import com.erv.app.weighttraining.WeightWorkoutSource
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDayActivityTest {

    private val day = LocalDate.of(2026, 11, 24)

    private fun link(workoutId: String) = WorkoutSessionLink(
        sessionId = "run-1",
        workoutId = workoutId,
        segmentId = "seg",
        itemId = "item",
        displayRef = "WR-1",
    )

    @Test
    fun empty_day_has_no_activity() {
        val activity = trainingDayActivity(day, WeightLibraryState(), CardioLibraryState(), StretchLibraryState())
        assertFalse(activity.trained)
        assertTrue(activity.loggedWorkoutIds.isEmpty())
    }

    @Test
    fun linked_sessions_mark_their_workout_on_that_day_only() {
        val weight = WeightLibraryState(
            logs = listOf(
                WeightDayLog(
                    date = day.toString(),
                    workouts = listOf(
                        WeightWorkoutSession(source = WeightWorkoutSource.LIVE, workoutLink = link("w-push")),
                    ),
                ),
                WeightDayLog(
                    date = day.minusDays(1).toString(),
                    workouts = listOf(
                        WeightWorkoutSession(source = WeightWorkoutSource.LIVE, workoutLink = link("w-pull")),
                    ),
                ),
            ),
        )
        val stretch = StretchLibraryState(
            logs = listOf(
                StretchDayLog(
                    date = day.toString(),
                    sessions = listOf(StretchSession(workoutLink = link("w-mobility"))),
                ),
            ),
        )
        val activity = trainingDayActivity(day, weight, CardioLibraryState(), stretch)
        assertTrue(activity.trained)
        assertEquals(setOf("w-push", "w-mobility"), activity.loggedWorkoutIds)
    }

    @Test
    fun unlinked_session_counts_as_trained_without_a_workout() {
        val weight = WeightLibraryState(
            logs = listOf(
                WeightDayLog(
                    date = day.toString(),
                    workouts = listOf(WeightWorkoutSession(source = WeightWorkoutSource.MANUAL)),
                ),
            ),
        )
        val activity = trainingDayActivity(day, weight, CardioLibraryState(), StretchLibraryState())
        assertTrue(activity.trained)
        assertTrue(activity.loggedWorkoutIds.isEmpty())
    }

    @Test
    fun fresh_run_with_nothing_logged_can_be_left_quietly() {
        val workout = Workout(id = "w", name = "Push")
        val run = WorkoutActiveRun(workoutId = "w", workoutSnapshot = workout, startedAtEpochSeconds = 1_000L)
        assertTrue(run.isFreshWithNothingLogged(nowEpochSeconds = 1_030L))
        assertFalse(run.isFreshWithNothingLogged(nowEpochSeconds = 1_000L + WORKOUT_RUN_QUICK_EXIT_SECONDS))
        assertFalse(
            run.copy(lastLaunchedSegmentId = "seg").isFreshWithNothingLogged(nowEpochSeconds = 1_010L),
        )
        assertFalse(
            run.copy(completedSegmentIds = listOf("seg")).isFreshWithNothingLogged(nowEpochSeconds = 1_010L),
        )
    }
}
