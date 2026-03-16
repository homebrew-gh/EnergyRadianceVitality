package com.erv.app.nostr

/**
 * Abstraction over Nostr event signing and NIP-44 encryption.
 * Implementations: [LocalSigner] (nsec on device) and [AmberSigner] (NIP-55 remote signer).
 */
interface EventSigner {
    val publicKey: String

    suspend fun sign(event: UnsignedEvent): NostrEvent

    suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String

    suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String

    suspend fun encryptToSelf(plaintext: String): String =
        nip44Encrypt(plaintext, publicKey)

    suspend fun decryptFromSelf(ciphertext: String): String =
        nip44Decrypt(ciphertext, publicKey)
}
