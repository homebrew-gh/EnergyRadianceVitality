package com.erv.app.cardio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioTimerPauseTest {

    private val start = 1_764_000_000L

    private val draft = CardioTimerSessionDraft.fromActivitySnapshot(
        CardioActivitySnapshot(
            builtin = CardioBuiltinActivity.RUN,
            customTypeId = null,
            customName = null,
            displayLabel = "Run",
        ),
    ).copy(startEpoch = start)

    @Test
    fun pausesAreLeftOutOfWorkoutTime() {
        val paused = draft.withPaused(start + 600)
        assertTrue(paused.isPaused())
        // Frozen while paused.
        assertEquals(600, paused.activeElapsedSeconds(start + 900))

        val resumed = paused.withResumed(start + 900)
        assertFalse(resumed.isPaused())
        assertEquals(300, resumed.pausedTotalSeconds)
        assertEquals(900, resumed.activeElapsedSeconds(start + 1200))

        val again = resumed.withPaused(start + 1500).withResumed(start + 1560)
        assertEquals(360, again.pausedTotalSeconds)
        assertEquals(1240, again.activeElapsedSeconds(start + 1600))
    }

    @Test
    fun pauseDoesNothingBeforeStartOrTwice() {
        val pending = draft.copy(startEpoch = CARDIO_TIMER_PENDING_START_EPOCH)
        assertFalse(pending.withPaused(start).isPaused())
        assertEquals(0, pending.activeElapsedSeconds(start))

        val paused = draft.withPaused(start + 60)
        assertEquals(paused, paused.withPaused(start + 120))
        assertEquals(draft, draft.withResumed(start + 120))
    }

    @Test
    fun notificationClockSkipsPastPauses() {
        val session = CardioActiveTimerSession.Single(
            draft.withPaused(start + 100).withResumed(start + 160),
        )
        assertEquals(start + 60, session.notificationClockBaseEpochSeconds())
        assertFalse(session.isPaused())
    }

    @Test
    fun pausedTimerSurvivesSaveAndRestore() {
        val saved = CardioSavedLiveSession(CardioActiveTimerSession.Single(draft.withPaused(start + 42)))
        assertEquals(saved, CardioLiveSessionStore.decode(CardioLiveSessionStore.encode(saved)))
    }
}
