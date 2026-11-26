package com.erv.app.nostr

import android.content.Context

/**
 * Shared publish and decrypt helpers for the encrypt-to-self kind 30078 sync files
 * (heat/cold, light therapy, supplements, body tracker).
 */

/**
 * Queues [plaintext] as the replaceable event for [dTag] and drains the outbox.
 * Returns true when no relay reported a failed publish.
 */
suspend fun publishSelfKind30078(
    appContext: Context,
    relayPool: RelayPool,
    signer: EventSigner,
    dTag: String,
    plaintext: String,
    dataRelayUrls: List<String>,
): Boolean {
    val r = RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
        appContext,
        relayPool,
        signer,
        dataRelayUrls,
        dTag,
        plaintext,
    )
    return r.publishedFail == 0
}

/** Decrypts an event this account encrypted to itself, or null when it can't be read. */
suspend fun NostrEvent.decryptSelfPayloadOrNull(signer: EventSigner): String? =
    try {
        signer.decryptFromSelf(content)
    } catch (_: Exception) {
        null
    }
