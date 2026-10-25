package com.erv.app.nostr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

private const val DRAIN_LOOP_IDLE_RETRY_MS = 30_000L
private const val DRAIN_LOOP_MAX_WAIT_MS = 60L * 60_000L

/**
 * Keeps the relay outbox draining while the app runs. [RelayPublishOutbox.kickDrain] never sleeps
 * (it returns [RelayPublishOutbox.KickDrainResult.retryAfterMs] instead), so without this loop a
 * paused or backed-off queue only moved when some unrelated publish happened to kick it.
 * [wakeSignals] (resume, data relay reconnect) cut a wait short; the outbox still honours its own
 * rate-limit pause.
 */
suspend fun runRelayOutboxDrainLoop(
    outbox: RelayPublishOutbox,
    relayPool: RelayPool,
    signer: EventSigner,
    dataRelayUrls: () -> List<String>,
    wakeSignals: Flow<Unit>,
) {
    while (currentCoroutineContext().isActive) {
        outbox.pendingCountFlow().first { it > 0 }
        val result = try {
            outbox.kickDrain(relayPool, signer, dataRelayUrls())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(ERV_SYNC_LOG_TAG, "outbox drain loop: kickDrain failed", e)
            null
        }
        val waitMs = drainLoopWaitMs(result)
        if (waitMs > 0) {
            withTimeoutOrNull(waitMs) { wakeSignals.first() }
        }
    }
}

internal fun drainLoopWaitMs(result: RelayPublishOutbox.KickDrainResult?): Long = when {
    result == null -> DRAIN_LOOP_IDLE_RETRY_MS
    result.stoppedBecauseQueueEmpty -> 0L
    result.retryAfterMs != null -> result.retryAfterMs.coerceIn(1_000L, DRAIN_LOOP_MAX_WAIT_MS)
    result.publishedOk > 0 -> 0L
    else -> DRAIN_LOOP_IDLE_RETRY_MS
}
