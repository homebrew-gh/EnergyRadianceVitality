package com.erv.app.nostr

/**
 * Publishes a kind **30078** encrypted-to-self event with a single **`d`** tag.
 * Shared by silo sync objects and [RelayPublishOutbox]. The signed event is sent only to
 * [dataRelayUrls] (typically [KeyManager.relayUrlsForKind30078Publish]), not social-only relays.
 */
object EncryptedKind30078Publish {

    suspend fun publish(
        relayPool: RelayPool,
        signer: EventSigner,
        dTag: String,
        plaintextPayload: String,
        dataRelayUrls: List<String>,
    ): Boolean {
        val encrypted = signer.encryptToSelf(plaintextPayload)
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 30078,
            tags = listOf(listOf("d", dTag)),
            content = encrypted
        )
        val signed = signer.sign(unsigned)
        return relayPool.publishToRelayUrls(signed, dataRelayUrls)
    }
}
