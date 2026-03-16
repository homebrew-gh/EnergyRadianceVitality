package com.erv.app.nostr

import java.security.MessageDigest
import java.security.SecureRandom

object Hex {
    fun encode(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun decode(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character at position ${i * 2}" }
            ((hi shl 4) or lo).toByte()
        }
    }
}

fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }

/**
 * Bech32 encoding/decoding for Nostr key formats (nsec1..., npub1...).
 * Implements BIP-173 Bech32 (not Bech32m).
 */
object Bech32 {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private val CHARSET_REV = IntArray(128) { -1 }.also { arr ->
        CHARSET.forEachIndexed { i, c -> arr[c.code] = i }
    }

    fun nsecEncode(privKey: ByteArray): String {
        require(privKey.size == 32)
        return encode("nsec", privKey)
    }

    fun npubEncode(pubKey: ByteArray): String {
        require(pubKey.size == 32)
        return encode("npub", pubKey)
    }

    fun nsecDecode(nsec: String): ByteArray {
        val (hrp, data) = decode(nsec)
        require(hrp == "nsec") { "Expected nsec, got $hrp" }
        require(data.size == 32) { "Invalid private key length: ${data.size}" }
        return data
    }

    fun npubDecode(npub: String): ByteArray {
        val (hrp, data) = decode(npub)
        require(hrp == "npub") { "Expected npub, got $hrp" }
        require(data.size == 32) { "Invalid public key length: ${data.size}" }
        return data
    }

    fun encode(hrp: String, data: ByteArray): String {
        val data5 = convertBits(data.map { it.toInt() and 0xFF }, 8, 5, pad = true)
        val checksum = createChecksum(hrp, data5)
        return buildString {
            append(hrp)
            append('1')
            for (d in data5) append(CHARSET[d])
            for (c in checksum) append(CHARSET[c])
        }
    }

    fun decode(bech32: String): Pair<String, ByteArray> {
        val lower = bech32.lowercase()
        val pos = lower.lastIndexOf('1')
        require(pos >= 1 && pos + 7 <= lower.length) { "Invalid bech32 string" }
        val hrp = lower.substring(0, pos)
        val data5 = (pos + 1 until lower.length).map { i ->
            val c = lower[i].code
            require(c < 128) { "Invalid character" }
            val v = CHARSET_REV[c]
            require(v >= 0) { "Invalid bech32 character" }
            v
        }
        require(verifyChecksum(hrp, data5)) { "Invalid bech32 checksum" }
        val payload5 = data5.subList(0, data5.size - 6)
        val bytes8 = convertBits(payload5, 5, 8, pad = false)
        return hrp to ByteArray(bytes8.size) { bytes8[it].toByte() }
    }

    private fun polymod(values: List<Int>): Int {
        val gen = intArrayOf(0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3)
        var chk = 1
        for (v in values) {
            val b = chk ushr 25
            chk = ((chk and 0x1ffffff) shl 5) xor v
            for (i in 0..4) {
                if ((b ushr i) and 1 == 1) chk = chk xor gen[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): List<Int> {
        val result = mutableListOf<Int>()
        for (c in hrp) result.add(c.code ushr 5)
        result.add(0)
        for (c in hrp) result.add(c.code and 31)
        return result
    }

    private fun verifyChecksum(hrp: String, data: List<Int>): Boolean =
        polymod(hrpExpand(hrp) + data) == 1

    private fun createChecksum(hrp: String, data: List<Int>): List<Int> {
        val values = hrpExpand(hrp) + data + listOf(0, 0, 0, 0, 0, 0)
        val pm = polymod(values) xor 1
        return (0..5).map { (pm ushr (5 * (5 - it))) and 31 }
    }

    private fun convertBits(data: List<Int>, fromBits: Int, toBits: Int, pad: Boolean): List<Int> {
        var acc = 0
        var bits = 0
        val maxv = (1 shl toBits) - 1
        val result = mutableListOf<Int>()
        for (value in data) {
            require(value in 0 until (1 shl fromBits)) { "Value out of range" }
            acc = (acc shl fromBits) or value
            bits += fromBits
            while (bits >= toBits) {
                bits -= toBits
                result.add((acc ushr bits) and maxv)
            }
        }
        if (pad) {
            if (bits > 0) result.add((acc shl (toBits - bits)) and maxv)
        } else {
            require(bits < fromBits) { "Invalid padding" }
            require((acc shl (toBits - bits)) and maxv == 0) { "Non-zero padding bits" }
        }
        return result
    }
}
