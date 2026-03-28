package com.erv.app.nostr

import com.erv.app.bodytracker.BodyTrackerDayLog
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.BodyTrackerPhoto
import com.erv.app.cardio.CardioActivitySnapshot
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.CardioDayLog
import com.erv.app.cardio.CardioLibraryState
import com.erv.app.cardio.CardioSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun mergeBodyTracker_newerRemoteTombstone_clearsMeasurements() {
        val local = BodyTrackerLibraryState(
            logs = listOf(
                BodyTrackerDayLog(date = "2025-01-01", weightKg = 80.0, updatedAtEpochSeconds = 100L)
            )
        )
        val remote = BodyTrackerLibraryState(
            logs = listOf(
                BodyTrackerDayLog(date = "2025-01-01", updatedAtEpochSeconds = 200L)
            )
        )
        val merged = LibraryStateMerge.mergeBodyTracker(local, remote)
        assertTrue(merged.logs.isEmpty())
    }

    @Test
    fun mergeBodyTracker_remoteNewer_keepsLocalPhotos() {
        val photo = BodyTrackerPhoto(id = "p1", addedAtEpochSeconds = 1L)
        val local = BodyTrackerLibraryState(
            logs = listOf(
                BodyTrackerDayLog(
                    date = "2025-01-01",
                    weightKg = 70.0,
                    updatedAtEpochSeconds = 50L,
                    photos = listOf(photo)
                )
            )
        )
        val remote = BodyTrackerLibraryState(
            logs = listOf(
                BodyTrackerDayLog(
                    date = "2025-01-01",
                    weightKg = 75.0,
                    updatedAtEpochSeconds = 100L,
                    photos = emptyList()
                )
            )
        )
        val merged = LibraryStateMerge.mergeBodyTracker(local, remote)
        val day = merged.logs.single()
        assertEquals(75.0, day.weightKg!!, 0.001)
        assertEquals(listOf(photo), day.photos)
    }
}
