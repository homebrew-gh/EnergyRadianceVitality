package com.erv.app.nostr

import com.erv.app.cardio.CardioHrSample
import com.erv.app.cardio.CardioHrScaffolding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionMediaBackupTest {

    @Test
    fun heartRateForMediaBackup_prefersLongerWholeRunTrace() {
        val section = hr(2)
        val wholeRun = hr(4)
        assertEquals(wholeRun, heartRateForMediaBackup(section, wholeRun))
    }

    @Test
    fun heartRateForMediaBackup_keepsRicherSectionTrace() {
        val section = hr(5)
        val wholeRun = hr(2)
        assertEquals(section, heartRateForMediaBackup(section, wholeRun))
    }

    @Test
    fun heartRateForMediaBackup_skipsSingleSample() {
        assertNull(heartRateForMediaBackup(hr(1), null))
    }

    private fun hr(sampleCount: Int): CardioHrScaffolding =
        CardioHrScaffolding(
            avgBpm = 120,
            maxBpm = 150,
            minBpm = 90,
            samples = List(sampleCount) { index ->
                CardioHrSample(epochSeconds = index.toLong(), bpm = 100 + index)
            },
        )
}
