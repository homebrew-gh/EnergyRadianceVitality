package com.erv.app.nostr

import com.erv.app.bodytracker.BodyTrackerDayLog
import com.erv.app.bodytracker.BodyTrackerLibraryState
import com.erv.app.bodytracker.BodyTrackerPhoto
import com.erv.app.programs.FitnessProgram
import com.erv.app.programs.ProgramBlockKind
import com.erv.app.programs.ProgramDayBlock
import com.erv.app.programs.ProgramsLibraryState
import com.erv.app.programs.ProgramWeekDay
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
    fun mergeCardio_doesNotAdoptRemoteEmptyDayWhenLocalMissing() {
        val remote = CardioLibraryState(
            logs = listOf(CardioDayLog(date = "2025-01-02", sessions = emptyList()))
        )
        val merged = LibraryStateMerge.mergeCardio(CardioLibraryState(), remote)
        assertTrue(merged.logs.none { it.date == "2025-01-02" })
    }

    @Test
    fun mergeCardio_localSessionsSurviveRemoteEmptyDay() {
        val act = CardioActivitySnapshot(
            builtin = CardioBuiltinActivity.STATIONARY_BIKE,
            customTypeId = null,
            customName = null,
            displayLabel = "Stationary Bike"
        )
        val session = CardioSession(
            id = "s1",
            activity = act,
            durationMinutes = 35,
            loggedAtEpochSeconds = 100L
        )
        val local = CardioLibraryState(logs = listOf(CardioDayLog(date = "2025-01-03", sessions = listOf(session))))
        val remote = CardioLibraryState(logs = listOf(CardioDayLog(date = "2025-01-03", sessions = emptyList())))
        val merged = LibraryStateMerge.mergeCardio(local, remote)
        assertEquals(35, merged.logs.single().sessions.single().durationMinutes)
    }

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

    @Test
    fun mergePrograms_keepsRemoteProgramWhenLocalMasterIsNewer() {
        val localProgram = FitnessProgram(
            id = "local-plan",
            name = "Phone plan",
            lastModifiedEpochSeconds = 50L,
        )
        val remoteProgram = FitnessProgram(
            id = "web-plan",
            name = "Web plan",
            weeklySchedule = listOf(
                ProgramWeekDay(
                    dayOfWeek = 1,
                    blocks = listOf(
                        ProgramDayBlock(
                            id = "block-1",
                            kind = ProgramBlockKind.WORKOUT,
                            workoutId = "workout-1",
                            title = "Push day",
                        ),
                    ),
                ),
            ),
            lastModifiedEpochSeconds = 200L,
        )
        val local = ProgramsLibraryState(
            programs = listOf(localProgram),
            activeProgramId = localProgram.id,
            masterUpdatedAtEpochSeconds = 300L,
        )
        val remote = ProgramsLibraryState(
            programs = listOf(remoteProgram),
            activeProgramId = remoteProgram.id,
            masterUpdatedAtEpochSeconds = 100L,
        )

        val merged = LibraryStateMerge.mergePrograms(local, remote)

        assertEquals(setOf("local-plan", "web-plan"), merged.programs.map { it.id }.toSet())
        assertEquals("Web plan", merged.programById("web-plan")?.name)
        assertEquals("workout-1", merged.programById("web-plan")?.weeklySchedule?.single()?.blocks?.single()?.workoutId)
        assertEquals("local-plan", merged.activeProgramId)
    }
}
