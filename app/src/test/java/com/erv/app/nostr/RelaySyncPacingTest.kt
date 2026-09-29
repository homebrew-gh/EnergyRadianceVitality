package com.erv.app.nostr

import com.erv.app.workouts.WORKOUTS_LIBRARY_D_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelaySyncPacingTest {

    private val relayKey = relayPauseKey(listOf("wss://haven.local"))
    private val now = 1_000_000L

    @Test
    fun classify_nip01Prefixes() {
        assertEquals(RelayRejectionKind.RATE_LIMITED, classifyRelayRejection("rate-limited: slow down, please"))
        assertEquals(RelayRejectionKind.AUTH, classifyRelayRejection("auth-required: you must be authenticated"))
        assertEquals(
            RelayRejectionKind.AUTH,
            classifyRelayRejection("Relay requires authentication; publish retry was exhausted"),
        )
        assertEquals(RelayRejectionKind.RESTRICTED, classifyRelayRejection("restricted: you must be whitelisted"))
        assertEquals(RelayRejectionKind.INVALID, classifyRelayRejection("invalid: bad signature"))
        assertEquals(RelayRejectionKind.TRANSIENT, classifyRelayRejection("error: could not save"))
    }

    @Test
    fun aggregate_relayWideCauseWins() {
        assertEquals(
            RelayRejectionKind.RATE_LIMITED,
            aggregateRejectionKind(
                listOf(ClientPublishResult.TimedOut, ClientPublishResult.Rejected("rate-limited: slow down")),
            ),
        )
        assertEquals(RelayRejectionKind.OFFLINE, aggregateRejectionKind(listOf(ClientPublishResult.NoSocket)))
        assertEquals(RelayRejectionKind.TRANSIENT, aggregateRejectionKind(listOf(ClientPublishResult.TimedOut)))
    }

    @Test
    fun rateLimitCooldown_laddersToAnHour() {
        assertEquals(listOf(1L, 5L, 15L, 30L, 60L, 60L), (1..6).map { rateLimitCooldownMs(it) / 60_000L })
    }

    @Test
    fun pause_rateLimitEscalatesAndSuccessEasesOffOneStrike() {
        val first = nextOutboxPause(null, RelayRejectionKind.RATE_LIMITED, "rate-limited", relayKey, now)!!
        assertEquals(1, first.strikes)
        assertEquals(now + 60_000L, first.untilEpochMs)

        val second = nextOutboxPause(first, RelayRejectionKind.RATE_LIMITED, "rate-limited", relayKey, now)!!
        assertEquals(2, second.strikes)
        assertEquals(now + 5 * 60_000L, second.untilEpochMs)

        val eased = nextOutboxPause(second, null, "", relayKey, now)!!
        assertEquals(0L, eased.untilEpochMs)
        assertEquals(1, eased.strikes)
        // Further successes in the same batch do not keep decrementing.
        assertEquals(eased, nextOutboxPause(eased, null, "", relayKey, now))

        val again = nextOutboxPause(eased, RelayRejectionKind.RATE_LIMITED, "rate-limited", relayKey, now)!!
        assertEquals(2, again.strikes)

        assertNull(nextOutboxPause(first, null, "", relayKey, now))
    }

    @Test
    fun pause_perEventFailureLeavesPauseAlone() {
        val paused = nextOutboxPause(null, RelayRejectionKind.RATE_LIMITED, "rate-limited", relayKey, now)
        assertEquals(paused, nextOutboxPause(paused, RelayRejectionKind.INVALID, "invalid", relayKey, now))
        assertNull(nextOutboxPause(null, RelayRejectionKind.TRANSIENT, "timeout", relayKey, now))
    }

    @Test
    fun pause_offlineAndRefusedKeepStrikes() {
        val paused = nextOutboxPause(null, RelayRejectionKind.RATE_LIMITED, "rate-limited", relayKey, now)
        val offline = nextOutboxPause(paused, RelayRejectionKind.OFFLINE, "No connected data relays", relayKey, now)!!
        assertEquals(1, offline.strikes)
        assertEquals(now + RELAY_OFFLINE_PAUSE_MS, offline.untilEpochMs)
        val refused = nextOutboxPause(null, RelayRejectionKind.RESTRICTED, "restricted", relayKey, now)!!
        assertEquals(now + RELAY_REFUSED_PAUSE_MS, refused.untilEpochMs)
    }

    @Test
    fun sendOrder_librariesThenIndexThenRecentNewestFirstThenHistory() {
        val today = LocalDate.parse("2026-09-29").toEpochDay()
        fun item(dTag: String, createdAt: Long, nextAttempt: Long = 0L) =
            RelayPublishOutbox.OutboxItem(
                id = dTag,
                dTag = dTag,
                plaintextPayload = "{}",
                createdAtEpochMs = createdAt,
                nextAttemptAtEpochMs = nextAttempt,
            )
        val queue = listOf(
            item("erv/weight/2025-01-02", 1),
            item(WORKOUTS_LIBRARY_D_TAG, 2),
            item("erv/cardio/2026-09-20", 3),
            item("erv/weight/2026-09-28/session/abc", 4),
            item("erv/workouts/library/workout/w1", 5),
            item("erv/weight/routines", 6),
            item("erv/supplements/list", 7, nextAttempt = now + 1),
        )
        val order = mutableListOf<String>()
        var remaining = queue
        while (true) {
            val next = pickNextDueOutboxItem(remaining, now, today) ?: break
            order += next.dTag
            remaining = remaining - next
        }
        assertEquals(
            listOf(
                "erv/workouts/library/workout/w1",
                "erv/weight/routines",
                WORKOUTS_LIBRARY_D_TAG,
                "erv/weight/2026-09-28/session/abc",
                "erv/cardio/2026-09-20",
                "erv/weight/2025-01-02",
            ),
            order,
        )
    }

    @Test
    fun drainLoopWait() {
        fun result(empty: Boolean = false, ok: Int = 0, retry: Long? = null) =
            RelayPublishOutbox.KickDrainResult(
                remaining = if (empty) 0 else 3,
                publishedOk = ok,
                publishedFail = 0,
                stoppedBecauseQueueEmpty = empty,
                retryAfterMs = retry,
            )
        assertEquals(0L, drainLoopWaitMs(result(empty = true)))
        assertEquals(60_000L, drainLoopWaitMs(result(retry = 60_000L)))
        assertEquals(1_000L, drainLoopWaitMs(result(retry = 10L)))
        assertEquals(0L, drainLoopWaitMs(result(ok = 5)))
        assertEquals(30_000L, drainLoopWaitMs(null))
    }

    @Test
    fun diff_onlyUploadsMissingOrDifferent() {
        val local = listOf("a" to "same", "b" to "local-new", "c" to "only-local")
        val relay = mapOf("a" to "same", "b" to "relay-old")

        val verified = diffLocalAgainstRelay(local, relay, relayAbsenceVerified = true)
        assertEquals(listOf("a"), verified.matched.map { it.first })
        assertEquals(listOf("b", "c"), verified.toUpload.map { it.first })

        val unverified = diffLocalAgainstRelay(local, relay, relayAbsenceVerified = false)
        assertEquals(listOf("b"), unverified.toUpload.map { it.first })
        assertEquals(listOf("c"), unverified.unverifiedTags)
    }

    @Test
    fun diff_undecryptableRelayCopyIsReplaced() {
        val diff = diffLocalAgainstRelay(listOf("a" to "x"), mapOf("a" to null), relayAbsenceVerified = false)
        assertEquals(listOf("a"), diff.toUpload.map { it.first })
    }
}
