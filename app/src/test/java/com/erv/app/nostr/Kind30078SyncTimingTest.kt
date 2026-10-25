package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Kind30078SyncTimingTest {

    @Test
    fun recentWindow_isTwentyOneDays() {
        val now = 1_700_000_000L
        assertEquals(now - 21L * 86_400L, recentFetchSinceEpochSeconds(now))
    }

    @Test
    fun recentWindow_doesNotGoNegative() {
        assertEquals(0L, recentFetchSinceEpochSeconds(10L))
    }

    @Test
    fun liveSince_allowsTwoMinutesOfClockSkew() {
        val now = 1_700_000_000L
        assertEquals(now - 120L, liveSubscriptionSinceEpochSeconds(now))
    }

    @Test
    fun liveTags_workoutsAndCatalogsUseFullPull() {
        assertTrue(liveTagNeedsFullPull("erv/workouts/library"))
        assertTrue(liveTagNeedsFullPull("erv/workouts/library/workout/w1/segment/s1"))
        assertTrue(liveTagNeedsFullPull("erv/catalog/weight"))
        assertFalse(liveTagNeedsFullPull("erv/supplements/2026-09-28"))
        assertFalse(liveTagNeedsFullPull("erv/light/2026-09-28"))
        assertFalse(liveTagNeedsFullPull("erv/weight/2026-09-28"))
    }

    @Test
    fun eose_requiresEveryConnectedRelay() {
        assertTrue(allConnectedRelaysReachedEose(setOf("wss://relay.home"), setOf("wss://relay.home")))
        assertFalse(
            allConnectedRelaysReachedEose(
                setOf("wss://a", "wss://b"),
                setOf("wss://a"),
            ),
        )
    }

    @Test
    fun eose_emptyConnectionIsNotComplete() {
        assertFalse(allConnectedRelaysReachedEose(emptySet(), emptySet()))
    }
}
