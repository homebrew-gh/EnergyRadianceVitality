package com.erv.app.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardioLiveSessionStoreTest {

    private val run = CardioActivitySnapshot(
        builtin = CardioBuiltinActivity.RUCK,
        customTypeId = null,
        customName = null,
        displayLabel = "Ruck",
    )

    @Test
    fun singleTimerWithSplitsRoundTrips() {
        val draft = CardioTimerSessionDraft.fromQuickSnapshot(
            activity = run,
            modality = CardioModality.OUTDOOR,
            treadmill = null,
            title = "Morning ruck",
            timerStyle = CardioTimerStyle.CountDown(45 * 60),
            ruckLoadKg = 13.6,
        ).copy(
            startEpoch = 1_764_000_000L,
            splits = listOf(
                CardioWorkoutSplit(
                    orderIndex = 0,
                    elapsedSeconds = 900,
                    segmentElapsedSeconds = 900,
                    totalDistanceMeters = 1609.344,
                    segmentDistanceMeters = 1609.344,
                ),
            ),
        )
        val saved = CardioSavedLiveSession(CardioActiveTimerSession.Single(draft))

        val back = CardioLiveSessionStore.decode(CardioLiveSessionStore.encode(saved))

        assertEquals(saved, back)
    }

    @Test
    fun everyTimerStyleRoundTrips() {
        listOf(
            CardioTimerStyle.CountUp,
            CardioTimerStyle.CountDown(600),
            CardioTimerStyle.CountDownDistance(400.0),
        ).forEach { style ->
            val draft = CardioTimerSessionDraft.fromActivitySnapshot(run).copy(timerStyle = style)
            val saved = CardioSavedLiveSession(CardioActiveTimerSession.Single(draft), suppressNotification = true)
            assertEquals(saved, CardioLiveSessionStore.decode(CardioLiveSessionStore.encode(saved)))
        }
    }

    @Test
    fun multiLegTimerRoundTrips() {
        val state = CardioMultiLegTimerState.fromIntervalTemplate(
            workActivity = run,
            workModality = CardioModality.OUTDOOR,
            workTreadmill = null,
            routineName = "Hill repeats",
            rounds = 3,
            workMinutes = 4,
            restMinutes = 2,
        ).copy(currentLegIndex = 2, workoutStartEpoch = 1_764_000_000L, legStartedEpoch = 1_764_000_360L)
        val saved = CardioSavedLiveSession(CardioActiveTimerSession.Multi(state))

        assertEquals(saved, CardioLiveSessionStore.decode(CardioLiveSessionStore.encode(saved)))
    }

    @Test
    fun unreadableTextGivesNull() {
        assertNull(CardioLiveSessionStore.decode("{not json"))
        assertNull(CardioLiveSessionStore.decode("{\"session\":{\"type\":\"unknown\"}}"))
    }

    @Test
    fun gpsPointsRoundTripAndSkipHalfWrittenLines() {
        val points = listOf(
            CardioGpsPoint(lat = 40.7128, lon = -74.006, epochSeconds = 1_764_000_010L, altitudeMeters = 12.5),
            CardioGpsPoint(lat = 40.7130, lon = -74.0058, epochSeconds = 1_764_000_013L, altitudeMeters = null),
        )
        val lines = points.joinToString("") { CardioGpsRecordingHub.encodePoint(it) }.lines()
            .filter { it.isNotEmpty() } + "40.71,-74.0"

        assertEquals(points, CardioGpsRecordingHub.decodePoints(lines))
    }
}
