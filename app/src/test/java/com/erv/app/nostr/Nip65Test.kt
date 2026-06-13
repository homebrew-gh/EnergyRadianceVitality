package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Test

class Nip65Test {

    @Test
    fun parseRelayListFromEvent_deduplicatesCanonicalRelayUrls() {
        val event = NostrEvent(
            id = "id",
            pubkey = "pubkey",
            createdAt = 1L,
            kind = 10002,
            tags = listOf(
                listOf("r", "wss://relay.example.com/"),
                listOf("r", "WSS://Relay.Example.com", "read"),
                listOf("r", "https://not-a-relay.example.com"),
            ),
            content = "",
            sig = "sig",
        )

        assertEquals(listOf("wss://relay.example.com"), Nip65.parseRelayListFromEvent(event))
    }
}
