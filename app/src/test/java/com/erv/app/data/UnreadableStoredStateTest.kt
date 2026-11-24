package com.erv.app.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnreadableStoredStateTest {

    private val stateKey = stringPreferencesKey("weight_library_state")

    private fun backups(prefs: androidx.datastore.preferences.core.Preferences): Map<String, Any> =
        prefs.asMap()
            .filterKeys { it.name.startsWith(stateKey.name + UNREADABLE_STATE_BACKUP_SUFFIX) }
            .mapKeys { it.key.name }

    @Test
    fun keeps_a_copy_of_unreadable_text() {
        val prefs = mutablePreferencesOf(stateKey to "{broken")
        val backupKey = prefs.preserveUnreadableState(stateKey, "{broken", nowEpochSeconds = 100L)

        assertEquals("weight_library_state__unreadable_100", backupKey?.name)
        assertEquals(mapOf("weight_library_state__unreadable_100" to "{broken"), backups(prefs))
    }

    @Test
    fun skips_missing_or_blank_text() {
        val prefs = mutablePreferencesOf()
        assertNull(prefs.preserveUnreadableState(stateKey, null, nowEpochSeconds = 100L))
        assertNull(prefs.preserveUnreadableState(stateKey, "   ", nowEpochSeconds = 100L))
        assertEquals(emptyMap<String, Any>(), backups(prefs))
    }

    @Test
    fun does_not_store_the_same_text_twice() {
        val prefs = mutablePreferencesOf()
        prefs.preserveUnreadableState(stateKey, "{broken", nowEpochSeconds = 100L)
        assertNull(prefs.preserveUnreadableState(stateKey, "{broken", nowEpochSeconds = 200L))
        assertEquals(1, backups(prefs).size)
    }

    @Test
    fun keeps_distinct_copies_written_in_the_same_second() {
        val prefs = mutablePreferencesOf()
        prefs.preserveUnreadableState(stateKey, "{first", nowEpochSeconds = 100L)
        val second = prefs.preserveUnreadableState(stateKey, "{second", nowEpochSeconds = 100L)

        assertEquals("weight_library_state__unreadable_100_1", second?.name)
        assertEquals(
            mapOf(
                "weight_library_state__unreadable_100" to "{first",
                "weight_library_state__unreadable_100_1" to "{second",
            ),
            backups(prefs),
        )
    }
}
