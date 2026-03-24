package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPublishOutboxBackoffTest {

    @Test
    fun backoffDelay_firstFailure_isTwoSeconds() {
        assertEquals(2000L, RelayPublishOutbox.backoffDelayMsAfterFailure(1))
    }

    @Test
    fun backoffDelay_cappedAtTenMinutes() {
        assertTrue(RelayPublishOutbox.backoffDelayMsAfterFailure(50) <= 600_000L)
    }
}
