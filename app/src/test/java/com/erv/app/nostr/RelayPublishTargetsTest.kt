package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayPublishTargetsTest {

    @Test
    fun activeRelayUrls_emptyWhenNoRelaysConfigured() {
        assertEquals(emptyList<String>(), RelayPublishTargets.activeRelayUrls(emptyList(), emptyList()))
    }

    @Test
    fun activeRelayUrls_deduplicatesDataAndSocial() {
        val url = "wss://relay.example"
        assertEquals(
            listOf(url),
            RelayPublishTargets.activeRelayUrls(listOf(url), listOf(url)),
        )
    }

    @Test
    fun kind30078_usesDataRelaysOnlyWhenPresent() {
        val data = "wss://data.example"
        val social = "wss://social.example"
        assertEquals(
            listOf(data),
            RelayPublishTargets.relayUrlsForKind30078Publish(listOf(data), listOf(social)),
        )
    }

    @Test
    fun kind30078_fallsBackToPoolWhenNoDataRelays() {
        val social = "wss://social.example"
        assertEquals(
            listOf(social),
            RelayPublishTargets.relayUrlsForKind30078Publish(emptyList(), listOf(social)),
        )
    }

    @Test
    fun kind1_neverIncludesDataOnlyRelays() {
        val data = "wss://data.example"
        val social = "wss://social.example"
        assertEquals(listOf(social), RelayPublishTargets.relayUrlsForKind1Publish(listOf(social)))
        assertEquals(emptyList<String>(), RelayPublishTargets.relayUrlsForKind1Publish(emptyList()))
        assertFalse(RelayPublishTargets.relayUrlsForKind1Publish(listOf(social)).contains(data))
    }

    @Test
    fun shouldAttemptRelayNetworkImport_falseWhenPoolEmpty() {
        assertFalse(RelayPublishTargets.shouldAttemptRelayNetworkImport(emptyList()))
        assertTrue(RelayPublishTargets.shouldAttemptRelayNetworkImport(listOf("wss://relay.example")))
    }
}
