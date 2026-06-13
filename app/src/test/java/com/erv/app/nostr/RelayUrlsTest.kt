package com.erv.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayUrlsTest {

    @Test
    fun normalize_trimsRootTrailingSlashAndLowercasesHost() {
        assertEquals(
            "wss://relay.example.com",
            RelayUrls.normalize(" WSS://Relay.Example.com/ ")
        )
    }

    @Test
    fun normalize_addsDefaultSchemeForManualEntry() {
        assertEquals(
            "wss://relay.example.com",
            RelayUrls.normalize("relay.example.com/", addDefaultScheme = true)
        )
    }

    @Test
    fun normalize_rejectsMissingSchemeWhenDefaultNotRequested() {
        assertNull(RelayUrls.normalize("relay.example.com"))
    }
}
