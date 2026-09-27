package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlossomEndpointsTest {

    @Test
    fun originFromRelayUrl_preservesScheme() {
        assertEquals(
            "https://10.0.0.47:49748",
            BlossomEndpoints.originFromRelayUrl("wss://10.0.0.47:49748"),
        )
        assertEquals(
            "http://haven.startos:3355",
            BlossomEndpoints.originFromRelayUrl("ws://haven.startos:3355"),
        )
        assertNull(BlossomEndpoints.originFromRelayUrl("https://example.com"))
    }

    @Test
    fun normalizePrivateOrigin_keepsHttp() {
        assertEquals(
            "http://192.168.1.20:3355",
            BlossomEndpoints.normalizePrivateOrigin("http://192.168.1.20:3355/"),
        )
        assertEquals(
            "https://blossom.example",
            BlossomEndpoints.normalizePrivateOrigin("blossom.example"),
        )
    }

    @Test
    fun allowsInsecureHttpUpload_onlyPrivateHostWithTrust() {
        assertTrue(
            BlossomEndpoints.allowsInsecureHttpUpload("http://192.168.1.20:3355", trustSelfSignedLanTls = true),
        )
        assertTrue(
            BlossomEndpoints.allowsInsecureHttpUpload("http://haven.startos:3355", trustSelfSignedLanTls = true),
        )
        assertFalse(
            BlossomEndpoints.allowsInsecureHttpUpload("http://192.168.1.20:3355", trustSelfSignedLanTls = false),
        )
        assertFalse(
            BlossomEndpoints.allowsInsecureHttpUpload("http://relay.damus.io", trustSelfSignedLanTls = true),
        )
        assertFalse(
            BlossomEndpoints.allowsInsecureHttpUpload("https://192.168.1.20:3355", trustSelfSignedLanTls = true),
        )
    }

    @Test
    fun resolvePrivateBackupOrigin_usesExplicitHttpBeforeRelay() {
        assertEquals(
            "http://10.0.0.5:3355",
            BlossomEndpoints.resolvePrivateBackupOrigin(
                explicitPrivateOrigin = "http://10.0.0.5:3355",
                dataRelayUrls = listOf("wss://10.0.0.47:49748"),
            ),
        )
        assertEquals(
            "https://10.0.0.47:49748",
            BlossomEndpoints.resolvePrivateBackupOrigin(
                explicitPrivateOrigin = "",
                dataRelayUrls = listOf("wss://10.0.0.47:49748"),
            ),
        )
    }
}
