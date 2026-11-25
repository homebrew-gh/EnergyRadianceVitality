package com.erv.app.workouts

import com.erv.app.cardio.CardioPaceGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutPaceGoalTest {

    // 10 miles in 45 minutes.
    private val goal = CardioPaceGoal(distanceMeters = 16_093.44, seconds = 45 * 60)

    @Test
    fun onPace_atTheGoalPace() {
        val status = WorkoutPaceGoals.status(goal, elapsedSeconds = 900, coveredMeters = 16_093.44 / 3)
        assertEquals(PaceGoalState.ON_PACE, status.state)
        assertEquals(0, status.aheadSeconds)
        assertEquals(1f / 3, status.youFraction, 0.001f)
        assertEquals(1f / 3, status.goalFraction, 0.001f)
        assertEquals(2700, status.projectedFinishSeconds)
    }

    @Test
    fun ahead_whenFurtherThanTheGoal() {
        // Half way at 20 minutes: 2:30 ahead.
        val status = WorkoutPaceGoals.status(goal, elapsedSeconds = 1200, coveredMeters = 16_093.44 / 2)
        assertEquals(PaceGoalState.AHEAD, status.state)
        assertEquals(150, status.aheadSeconds)
        assertEquals(2400, status.projectedFinishSeconds)
    }

    @Test
    fun behind_whenShortOfTheGoal() {
        val status = WorkoutPaceGoals.status(goal, elapsedSeconds = 1350, coveredMeters = 16_093.44 * 0.4)
        assertEquals(PaceGoalState.BEHIND, status.state)
        assertEquals(-270, status.aheadSeconds)
        assertEquals(true, status.aheadMeters < 0)
    }

    @Test
    fun noDistanceYet_hasNoProjection() {
        val status = WorkoutPaceGoals.status(goal, elapsedSeconds = 5, coveredMeters = 0.0)
        assertEquals(PaceGoalState.ON_PACE, status.state)
        assertNull(status.projectedFinishSeconds)
    }

    @Test
    fun goalFor_prefersStandaloneThenBuilder() {
        val builder = WorkoutCardioPrescription(activity = "BIKE", targetMinutes = 30, targetDistanceMeters = 10_000.0)
        assertEquals(CardioPaceGoal(10_000.0, 1800), WorkoutPaceGoals.goalFor(builder, null))
        assertEquals(goal, WorkoutPaceGoals.goalFor(builder, goal))
        assertNull(WorkoutPaceGoals.goalFor(WorkoutCardioPrescription(activity = "BIKE", targetMinutes = 30), null))
    }
}
