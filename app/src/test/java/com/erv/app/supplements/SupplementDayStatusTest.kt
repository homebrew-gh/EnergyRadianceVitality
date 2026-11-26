package com.erv.app.supplements

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SupplementDayStatusTest {

    private val day = LocalDate.of(2026, 11, 26)
    private val fish = SupplementEntry(id = "fish", name = "Fish Oil", dosagePlan = SupplementDosagePlan(form = SupplementForm.SOFTGEL, amount = 2.0))
    private val creatine = SupplementEntry(id = "creatine", name = "Creatine", dosagePlan = SupplementDosagePlan(form = SupplementForm.POWDER, amount = 5.0, unit = SupplementUnit.G))
    private val zinc = SupplementEntry(id = "zinc", name = "Zinc")
    private val morning = SupplementRoutine(
        id = "am",
        name = "Breakfast",
        steps = listOf(
            SupplementRoutineStep(supplementId = "fish"),
            SupplementRoutineStep(supplementId = "creatine"),
            SupplementRoutineStep(supplementId = "zinc", paused = true),
            SupplementRoutineStep(supplementId = "gone"),
        )
    )

    private fun intake(id: String, at: Long?) = SupplementIntake(supplementId = id, supplementName = id, takenAtEpochSeconds = at, id = "i-$id-$at")

    private fun state(log: SupplementDayLog?) = SupplementLibraryState(
        supplements = listOf(fish, creatine, zinc),
        routines = listOf(morning),
        logs = listOfNotNull(log)
    )

    @Test
    fun activeStepsSkipPausedAndDeletedSupplements() {
        assertEquals(listOf("fish", "creatine"), state(null).activeSteps(morning).map { it.supplementId })
    }

    @Test
    fun routineStatusCountsOnlyStepsLoggedThroughThatRoutine() {
        val log = SupplementDayLog(
            date = day.toString(),
            routineRuns = listOf(
                SupplementRoutineRun(id = "r1", routineId = "am", routineName = "Breakfast", takenAtEpochSeconds = 100, stepIntakes = listOf(intake("fish", null))),
                SupplementRoutineRun(id = "r2", routineId = "other", routineName = "Other", takenAtEpochSeconds = 200, stepIntakes = listOf(intake("creatine", 200))),
            ),
            adHocIntakes = listOf(intake("creatine", 300))
        )
        val status = state(log).routineDayStatus(morning, day)
        assertEquals(2, status.activeStepCount)
        assertEquals(1, status.takenStepCount)
        assertEquals(100L, status.lastLoggedAtEpochSeconds)
        assertFalse(status.isComplete)
        assertEquals(mapOf("fish" to 100L), state(log).routineStepsTakenOn("am", day))
    }

    @Test
    fun routineCompleteWhenEveryActiveStepIsTaken() {
        val log = SupplementDayLog(
            date = day.toString(),
            routineRuns = listOf(
                SupplementRoutineRun(id = "r1", routineId = "am", routineName = "Breakfast", takenAtEpochSeconds = 100, stepIntakes = listOf(intake("fish", 100))),
                SupplementRoutineRun(id = "r2", routineId = "am", routineName = "Breakfast", takenAtEpochSeconds = 400, stepIntakes = listOf(intake("creatine", 400))),
            )
        )
        val status = state(log).routineDayStatus(morning, day)
        assertTrue(status.isComplete)
        assertEquals(400L, status.lastLoggedAtEpochSeconds)
    }

    @Test
    fun takenStatusCountsRoutineAndAdHocIntakes() {
        val log = SupplementDayLog(
            date = day.toString(),
            routineRuns = listOf(
                SupplementRoutineRun(id = "r1", routineId = "am", routineName = "Breakfast", takenAtEpochSeconds = 100, stepIntakes = listOf(intake("creatine", null)))
            ),
            adHocIntakes = listOf(intake("creatine", 500))
        )
        val status = state(log).takenStatus("creatine", day)
        assertEquals(2, status.count)
        assertEquals(500L, status.lastTakenAtEpochSeconds)
        assertEquals(0, state(log).takenStatus("fish", day).count)
        assertNull(state(null).takenStatus("fish", day).lastTakenAtEpochSeconds)
    }

    @Test
    fun countedFormsUsePieceNouns() {
        assertEquals("2 softgels", fish.dosagePlan.amountText(2.0))
        assertEquals("1 gummy", SupplementDosagePlan(form = SupplementForm.GUMMY).amountText(1.0))
        assertEquals("3 gummies", SupplementDosagePlan(form = SupplementForm.GUMMY).amountText(3.0))
        assertEquals("5 g", creatine.dosagePlan.amountText(5.0))
        assertEquals("Softgel • 2 softgels per serving", fish.dosagePlan.summary())
        assertTrue(SupplementForm.TABLET.isCounted())
        assertFalse(SupplementForm.LIQUID.isCounted())
    }

    @Test
    fun oldFormNamesStillDecode() {
        val json = Json { ignoreUnknownKeys = true }
        val plan = json.decodeFromString(SupplementDosagePlan.serializer(), """{"form":"CAPSULE","amount":1.0}""")
        assertEquals(SupplementForm.CAPSULE, plan.form)
        assertEquals("1 capsule", plan.amountText(1.0))
    }
}
