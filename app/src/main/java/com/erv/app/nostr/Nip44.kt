package com.erv.app.nostr

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * NIP-44 v2 encryption/decryption.
 * Uses secp256k1 ECDH (via BouncyCastle EC math) + HKDF-SHA256 + ChaCha20-Poly1305.
 */
object Nip44 {
    private const val VERSION: Byte = 2
    private val bcProvider = BouncyCastleProvider()

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(bcProvider)
        }
    }

    /**
     * Derive the NIP-44 conversation key from a private key and a peer's public key.
     * For encrypt-to-self, pass your own 32-byte x-only pubkey as [publicKey].
     */
    fun getConversationKey(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val sharedX = computeSharedSecret(privateKey, publicKey)
        val prk = hkdfExtract(salt = "nip44-v2".toByteArray(Charsets.UTF_8), ikm = sharedX)
        return hkdfExpand(prk, info = byteArrayOf(), length = 32)
    }

    fun encrypt(plaintext: String, conversationKey: ByteArray): String {
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        require(plaintextBytes.size in 1..65535) { "Plaintext must be 1-65535 bytes" }

        val nonce = secureRandomBytes(32)
        val keys = deriveMessageKeys(conversationKey, nonce)
        val padded = pad(plaintextBytes)

        val cipher = Cipher.getInstance("ChaCha20-Poly1305", bcProvider)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keys.chachaKey, "ChaCha20"),
            IvParameterSpec(keys.chachaNonce)
        )
        val ciphertext = cipher.doFinal(padded)

        val mac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        val payload = byteArrayOf(VERSION) + nonce + ciphertext + mac
        return Base64.getEncoder().encodeToString(payload)
    }

    fun decrypt(payload: String, conversationKey: ByteArray): String {
        val raw = Base64.getDecoder().decode(payload)
        require(raw.isNotEmpty() && raw[0] == VERSION) { "Unsupported NIP-44 version" }
        require(raw.size >= 99) { "Payload too short" }

        val nonce = raw.copyOfRange(1, 33)
        val ciphertext = raw.copyOfRange(33, raw.size - 32)
        val mac = raw.copyOfRange(raw.size - 32, raw.size)

        val keys = deriveMessageKeys(conversationKey, nonce)

        val expectedMac = hmacSha256(keys.hmacKey, nonce + ciphertext)
        require(expectedMac.contentEquals(mac)) { "Invalid MAC" }

        val cipher = Cipher.getInstance("ChaCha20-Poly1305", bcProvider)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keys.chachaKey, "ChaCha20"),
            IvParameterSpec(keys.chachaNonce)
        )
        val padded = cipher.doFinal(ciphertext)
        return String(unpad(padded), Charsets.UTF_8)
    }

    // --- EC / shared secret ---

    /**
     * Raw secp256k1 ECDH: multiply the peer's public point by our private scalar,
     * return the 32-byte x-coordinate of the resulting point.
     */
    private fun computeSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        val spec = ECNamedCurveTable.getParameterSpec("secp256k1")
        val compressed = if (publicKey.size == 32) {
            byteArrayOf(0x02) + publicKey
        } else {
            publicKey
        }
        val pubPoint = spec.curve.decodePoint(compressed)
        val sharedPoint = pubPoint.multiply(BigInteger(1, privateKey)).normalize()
        val xBytes = sharedPoint.affineXCoord.encoded
        val result = ByteArray(32)
        xBytes.copyInto(result, destinationOffset = 32 - xBytes.size)
        return result
    }

    // --- Message key derivation ---

    private class MessageKeys(
        val chachaKey: ByteArray,
        val chachaNonce: ByteArray,
        val hmacKey: ByteArray,
    )

    private fun deriveMessageKeys(conversationKey: ByteArray, nonce: ByteArray): MessageKeys {
        val prk = hkdfExtract(salt = nonce, ikm = conversationKey)
        val okm = hkdfExpand(prk, info = byteArrayOf(), length = 76)
        return MessageKeys(
            chachaKey = okm.copyOfRange(0, 32),
            chachaNonce = okm.copyOfRange(32, 44),
            hmacKey = okm.copyOfRange(44, 76),
        )
    }

    // --- HKDF-SHA256 (RFC 5869) ---

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        val output = ByteArray(n * hashLen)
        var prev = byteArrayOf()
        for (i in 1..n) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(prev)
            mac.update(info)
            mac.update(byteArrayOf(i.toByte()))
            prev = mac.doFinal()
            prev.copyInto(output, (i - 1) * hashLen)
        }
        return output.copyOfRange(0, length)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // --- NIP-44 padding ---

    internal fun calcPaddedLen(unpaddedLen: Int): Int {
        require(unpaddedLen > 0) { "Length must be positive" }
        if (unpaddedLen <= 32) return 32
        val nextPower = Integer.highestOneBit(unpaddedLen - 1) shl 1
        val chunk = if (nextPower <= 256) 32 else nextPower / 8
        return ((unpaddedLen + chunk - 1) / chunk) * chunk
    }

    private fun pad(plaintext: ByteArray): ByteArray {
        val paddedLen = calcPaddedLen(plaintext.size)
        val result = ByteArray(2 + paddedLen)
        result[0] = (plaintext.size shr 8).toByte()
        result[1] = (plaintext.size and 0xFF).toByte()
        plaintext.copyInto(result, 2)
        return result
    }

    private fun unpad(padded: ByteArray): ByteArray {
        require(padded.size >= 2 + 32) { "Padded data too short" }
        val unpaddedLen = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        require(unpaddedLen in 1..(padded.size - 2)) { "Invalid unpadded length" }
        val expectedTotal = 2 + calcPaddedLen(unpaddedLen)
        require(padded.size == expectedTotal) { "Unexpected padded size" }
        for (i in (2 + unpaddedLen) until padded.size) {
            require(padded[i] == 0.toByte()) { "Non-zero padding byte" }
        }
        return padded.copyOfRange(2, 2 + unpaddedLen)
    }
}
