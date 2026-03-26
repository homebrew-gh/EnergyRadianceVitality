package com.erv.app.nostr

import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioSession
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStateMergeTest {

    @Test
    fun mergeCardio_sameSessionId_prefersNewerLoggedAt() {
        val act = CardioActivitySnapshot(
            builtin = CardioBuiltinActivity.RUN,
            customTypeId = null,
            customName = null,
            displayLabel = "Run"
        )
        val older = CardioSession(
            id = "s1",
            activity = act,
            durationMinutes = 30,
            loggedAtEpochSeconds = 100L
        )
        val newer = CardioSession(
            id = "s1",
            activity = act,
            durationMinutes = 45,
            loggedAtEpochSeconds = 200L
        )
        val local = CardioLibraryState(logs = listOf(CardioDayLog(date = "2025-01-01", sessions = listOf(newer))))
        val remote = CardioLibraryState(logs = listOf(CardioDayLog(date = "2025-01-01", sessions = listOf(older))))
        val merged = LibraryStateMerge.mergeCardio(local, remote)
        val day = merged.logs.first { it.date == "2025-01-01" }
        assertEquals(45, day.sessions.single().durationMinutes)
    }
}
