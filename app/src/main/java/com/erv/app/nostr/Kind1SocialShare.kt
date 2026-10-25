package com.erv.app.nostr

/**
 * Kind **1** workout shares are public plaintext — publish only to [KeyManager.relayUrlsForKind1Publish]
 * (social relays), never to data-only relays used for encrypted kind 30078 backup.
 */
data class Kind1ShareDraft(
    val content: String,
    val tags: List<List<String>>,
)

data class Kind1PublishResult(
    val ok: Boolean,
    val userMessage: String,
)

object Kind1SocialShare {

    suspend fun publish(
        relayPool: RelayPool,
        keyManager: KeyManager,
        signer: EventSigner,
        draft: Kind1ShareDraft,
        successMessage: String = "Shared to your social relays.",
        noSocialRelaysMessage: String =
            "No social relays configured. Open Settings → Relays and enable Social on at least one relay.",
        failureMessage: String = "Failed to share — check social relay connections.",
    ): Kind1PublishResult {
        val dest = keyManager.relayUrlsForKind1Publish()
        if (dest.isEmpty()) {
            return Kind1PublishResult(ok = false, userMessage = noSocialRelaysMessage)
        }
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 1,
            tags = draft.tags,
            content = draft.content,
        )
        val signed = signer.sign(unsigned)
        val ok = relayPool.publishToRelayUrls(signed, dest)
        return Kind1PublishResult(
            ok = ok,
            userMessage = if (ok) successMessage else failureMessage,
        )
    }
}
