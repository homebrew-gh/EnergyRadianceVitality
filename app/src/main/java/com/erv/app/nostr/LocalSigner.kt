package com.erv.app.nostr

import fr.acinq.secp256k1.Secp256k1

/**
 * Signs events and handles NIP-44 encryption using a local nsec (private key on device).
 * Key material never leaves the process.
 */
class LocalSigner(private val privateKey: ByteArray) : EventSigner {

    override val publicKey: String

    init {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        val compressed = Secp256k1.pubkeyCreate(privateKey)
        publicKey = Hex.encode(compressed.copyOfRange(1, 33))
    }

    override suspend fun sign(event: UnsignedEvent): NostrEvent {
        require(event.pubkey == publicKey) {
            "Event pubkey does not match signer pubkey"
        }
        val id = event.computeId()
        val idBytes = Hex.decode(id)
        val auxRand = secureRandomBytes(32)
        val sig = Secp256k1.signSchnorr(idBytes, privateKey, auxRand)
        return NostrEvent(
            id = id,
            pubkey = event.pubkey,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content,
            sig = Hex.encode(sig)
        )
    }

    override suspend fun nip44Encrypt(plaintext: String, peerPubkeyHex: String): String {
        val conversationKey = Nip44.getConversationKey(privateKey, Hex.decode(peerPubkeyHex))
        return Nip44.encrypt(plaintext, conversationKey)
    }

    override suspend fun nip44Decrypt(ciphertext: String, peerPubkeyHex: String): String {
        val conversationKey = Nip44.getConversationKey(privateKey, Hex.decode(peerPubkeyHex))
        return Nip44.decrypt(ciphertext, conversationKey)
    }
}
