package com.erv.app

import com.erv.app.nostr.Bech32
import com.erv.app.nostr.Hex
import com.erv.app.nostr.Nip44
import org.junit.Test
import org.junit.Assert.*

class ExampleUnitTest {

    @Test
    fun hex_roundTrip() {
        val bytes = byteArrayOf(0x00, 0x0A, 0xFF.toByte(), 0x7F, 0x80.toByte())
        val hex = Hex.encode(bytes)
        assertEquals("000aff7f80", hex)
        assertArrayEquals(bytes, Hex.decode(hex))
    }

    @Test
    fun hex_emptyInput() {
        assertEquals("", Hex.encode(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), Hex.decode(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hex_oddLength_throws() {
        Hex.decode("abc")
    }

    @Test
    fun bech32_nsec_roundTrip() {
        val privKey = Hex.decode("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val nsec = Bech32.nsecEncode(privKey)
        assertTrue(nsec.startsWith("nsec1"))
        assertArrayEquals(privKey, Bech32.nsecDecode(nsec))
    }

    @Test
    fun bech32_npub_roundTrip() {
        val pubKey = Hex.decode("7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e")
        val npub = Bech32.npubEncode(pubKey)
        assertTrue(npub.startsWith("npub1"))
        assertArrayEquals(pubKey, Bech32.npubDecode(npub))
    }

    @Test(expected = IllegalArgumentException::class)
    fun bech32_wrongPrefix_throws() {
        val privKey = Hex.decode("67dea2ed018072d675f5415ecfaed7d2597555e202d85b3d65ea4e58d2d92ffa")
        val nsec = Bech32.nsecEncode(privKey)
        Bech32.npubDecode(nsec) // should fail: nsec decoded as npub
    }

    @Test
    fun nip44_paddingLength() {
        assertEquals(32, Nip44.calcPaddedLen(1))
        assertEquals(32, Nip44.calcPaddedLen(16))
        assertEquals(32, Nip44.calcPaddedLen(32))
        assertEquals(64, Nip44.calcPaddedLen(33))
        assertEquals(64, Nip44.calcPaddedLen(64))
        assertEquals(96, Nip44.calcPaddedLen(65))
        assertEquals(224, Nip44.calcPaddedLen(200))
        assertEquals(256, Nip44.calcPaddedLen(256))
        assertEquals(320, Nip44.calcPaddedLen(257))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nip44_paddingLength_zeroThrows() {
        Nip44.calcPaddedLen(0)
    }
}
