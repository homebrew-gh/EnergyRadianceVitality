package com.erv.app.nostr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanTlsTest {

    @Test
    fun shouldTrustSelfSigned_onlyWhenUserEnabledAndTlsUrl() {
        assertFalse(LanTls.shouldTrustSelfSigned("wss://relay.example.com", userEnabled = false))
        assertFalse(LanTls.shouldTrustSelfSigned("ws://10.0.0.5", userEnabled = true))
        assertTrue(LanTls.shouldTrustSelfSigned("wss://10.0.0.5", userEnabled = true))
        assertTrue(LanTls.shouldTrustSelfSigned("https://start.local", userEnabled = true))
    }

    @Test
    fun isLanOrPrivateHost_detectsCommonPrivateAddresses() {
        assertTrue(LanTls.isLanOrPrivateHost("localhost"))
        assertTrue(LanTls.isLanOrPrivateHost("192.168.1.10"))
        assertTrue(LanTls.isLanOrPrivateHost("10.0.0.5"))
        assertTrue(LanTls.isLanOrPrivateHost("server.startos"))
        assertFalse(LanTls.isLanOrPrivateHost("relay.damus.io"))
    }
}
