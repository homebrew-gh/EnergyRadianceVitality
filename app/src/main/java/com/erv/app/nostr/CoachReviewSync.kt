package com.erv.app.nostr

import com.erv.app.data.CoachReviewSyncPayload
import com.erv.app.data.decodeCoachReview

/**
 * Reads the latest AI coach review the web companion publishes as kind **30078** with `d` tag
 * **erv/coach/review**. Android never publishes this tag.
 */
object CoachReviewSync {

    const val D_TAG = "erv/coach/review"

    suspend fun fromLatestEvent(latest: NostrEvent, signer: EventSigner): CoachReviewSyncPayload? {
        return try {
            decodeCoachReview(signer.decryptFromSelf(latest.content))
        } catch (_: Exception) {
            null
        }
    }
}
