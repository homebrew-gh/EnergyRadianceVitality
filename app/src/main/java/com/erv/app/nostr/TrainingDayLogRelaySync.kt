package com.erv.app.nostr

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Durable upload path for weight/cardio **day logs** (`erv/weight/YYYY-MM-DD`, `erv/cardio/YYYY-MM-DD`).
 *
 * Always queues plaintext to [RelayPublishOutbox] after a local log mutation, then drains when a
 * [RelayPool] + [EventSigner] are bound (see [bindRelay]). UI layers must not gate on
 * `relayPool != null` before calling — repositories invoke this directly.
 */
object TrainingDayLogRelaySync {

    @Volatile
    private var activeRelayPool: RelayPool? = null

    @Volatile
    private var activeSigner: EventSigner? = null

    @Volatile
    private var activeDataRelayUrls: List<String> = emptyList()

    private val noticeEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Shown when a workout was saved but relay upload is still pending (user does not need to act). */
    val userNotices: SharedFlow<String> = noticeEvents.asSharedFlow()

    fun bindRelay(
        relayPool: RelayPool?,
        signer: EventSigner?,
        dataRelayUrls: List<String>,
    ) {
        activeRelayPool = relayPool
        activeSigner = signer
        activeDataRelayUrls = dataRelayUrls
    }

    /**
     * Queue one training day log and attempt immediate + follow-up drains when relay is ready.
     * No-op for blank plaintext or non day-log tags.
     */
    suspend fun queueTrainingDayLog(
        appContext: Context,
        dTag: String,
        plaintextPayload: String,
    ) {
        if (!isTrainingDayLogDTag(dTag)) return
        if (plaintextPayload.isBlank()) return
        RelayPayloadDigestStore.get(appContext).clearDigests(listOf(dTag))
        RelayPublishOutbox.get(appContext).enqueueReplaceByDTag(dTag, plaintextPayload)
        drainWithFollowUp(appContext)
    }

    suspend fun queueTrainingDayLogEntries(
        appContext: Context,
        replaceDTags: Collection<String>,
        entries: List<Pair<String, String>>,
    ) {
        val filtered = entries.filter { (dTag, plaintext) ->
            isTrainingDayLogDTag(dTag) && plaintext.isNotBlank()
        }
        if (filtered.isEmpty()) return
        val digestTags = replaceDTags + filtered.map { it.first }
        RelayPayloadDigestStore.get(appContext).clearDigests(digestTags)
        RelayPublishOutbox.get(appContext).replaceMany(replaceDTags, filtered)
        drainWithFollowUp(appContext)
    }

    /** Called on app start / relay reconnect — drains any queued training uploads. */
    suspend fun drainPending(appContext: Context) {
        drainWithFollowUp(appContext, userNoticeOnPending = false)
    }

    private suspend fun drainWithFollowUp(
        appContext: Context,
        userNoticeOnPending: Boolean = true,
    ) {
        val pool = activeRelayPool
        val signer = activeSigner
        val urls = activeDataRelayUrls
        if (pool == null || signer == null || urls.isEmpty()) {
            if (userNoticeOnPending) maybeNotifyPending(appContext, signer == null)
            return
        }

        var remaining = Int.MAX_VALUE
        var failed = 0
        repeat(4) { pass ->
            if (pass > 0) delay(2_000L * pass)
            val drain = RelayPublishOutbox.get(appContext).kickDrain(
                relayPool = pool,
                signer = signer,
                dataRelayUrls = urls,
                maxPublishesThisCall = 40,
            )
            remaining = drain.remaining
            failed = drain.publishedFail
            if (remaining == 0 && failed == 0) return
        }
        if (userNoticeOnPending && (remaining > 0 || failed > 0)) {
            maybeNotifyPending(appContext, notSignedIn = false)
        }
    }

    private suspend fun maybeNotifyPending(appContext: Context, notSignedIn: Boolean) {
        val pending = RelayPublishOutbox.get(appContext).pendingCount()
        if (pending <= 0) return
        val message = when {
            notSignedIn ->
                "Workout saved on this device. Sign in to Nostr to sync it to the web app."
            else ->
                "Workout saved. Relay sync will finish automatically in the background."
        }
        noticeEvents.emit(message)
    }
}
