package com.erv.app.standards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors `apps/web/web/src/lib/standards.test.ts` so the phone and web score the same. */
class StandardsEngineTest {

    private val lb = 0.45359237
    private val mile = 1609.344

    private val builtIns: List<FitnessStandard> by lazy {
        val file = listOf(
            File("src/main/assets/standards/built_in_standards.json"),
            File("app/src/main/assets/standards/built_in_standards.json"),
        ).first { it.exists() }
        decodeBuiltInStandards(file.readText())
    }

    private fun standard(id: String) = builtIns.first { it.id == id }
    private fun event(standardId: String, eventId: String) = standard(standardId).events.first { it.id == eventId }

    private fun inputs(
        liftSets: List<StandardLiftSet> = emptyList(),
        cardio: List<StandardCardioSession> = emptyList(),
        bodyWeights: List<StandardBodyWeight> = listOf(StandardBodyWeight("2026-01-01", 200 * lb)),
        profile: StandardProfile = StandardProfile(ageYears = 40, sex = "male", heightCm = 180.0),
        testResults: List<StoredStandardResult> = emptyList(),
    ) = StandardInputs(
        liftSets = liftSets,
        cardioSessions = cardio,
        bodyWeights = bodyWeights,
        profile = profile,
        exerciseNames = mapOf(
            "erv-weight-exercise-deadlift-v1" to "Deadlift",
            "custom-trap" to "Trap Bar Deadlift",
        ),
        testResults = testResults,
    )

    private fun set(exerciseId: String, reps: Int, weightLb: Double?, seconds: Int? = null, date: String = "2026-05-01") =
        StandardLiftSet(date, exerciseId, reps, weightLb?.let { it * lb }, seconds)

    private fun run(meters: Double, minutes: Double, group: String = "RUN", label: String = "Run", packKg: Double? = null) =
        StandardCardioSession("2026-05-01", group, label, meters, minutes * 60, packKg)

    @Test
    fun builtInsLoad() {
        assertEquals(listOf("erv-general-v1", "seven-event-v1", "army-aft-2025", "ruck-12mi-v1"), builtIns.map { it.id })
        assertTrue(builtIns.all { it.events.isNotEmpty() })
    }

    @Test
    fun scoringAlongTheRings() {
        val tiers = StandardTierValues(10.0, 15.0, 20.0)
        assertEquals(0.5, standardScore(5.0, tiers, false), 1e-6)
        assertEquals(1.5, standardScore(12.5, tiers, false), 1e-6)
        assertEquals(3.0, standardScore(25.0, tiers, false), 1e-6)
        assertNull(tierOf(9.0, tiers, false))
        assertEquals(StandardTier.STRONG, tierOf(15.0, tiers, false))
        val runTiers = StandardTierValues(540.0, 450.0, 390.0)
        assertEquals(1.5, standardScore(495.0, runTiers, true), 1e-6)
        assertEquals(StandardTier.STRONG, tierOf(450.0, runTiers, true))
    }

    @Test
    fun bodyWeightOnUsesLastBeforeElseFirstAfter() {
        val entries = listOf(StandardBodyWeight("2026-02-01", 90.0), StandardBodyWeight("2026-03-01", 88.0))
        assertEquals(90.0, bodyWeightOn(entries, "2026-02-15")!!, 0.0)
        assertEquals(88.0, bodyWeightOn(entries, "2026-03-01")!!, 0.0)
        assertEquals(90.0, bodyWeightOn(entries, "2026-01-01")!!, 0.0)
        assertNull(bodyWeightOn(emptyList(), "2026-01-01"))
    }

    @Test
    fun estimatedOneRepMaxAgainstBodyweight() {
        val r = evaluateEvent(
            "erv-general-v1",
            event("erv-general-v1", "squat"),
            inputs(liftSets = listOf(set("erv-weight-exercise-squat-v1", 5, 255.0))),
        )
        assertEquals(1.4875, r.value!!, 1e-3)
        assertEquals(StandardValueSource.ESTIMATED, r.source)
        assertEquals(StandardTier.BASE, r.tier)
        assertEquals(StandardTier.STRONG, r.nextTier)
        assertEquals(0.0125, r.gapToNext!!, 1e-3)
    }

    @Test
    fun needsBodyweightForRatioEvents() {
        val r = evaluateEvent(
            "erv-general-v1",
            event("erv-general-v1", "squat"),
            inputs(liftSets = listOf(set("erv-weight-exercise-squat-v1", 5, 220.0)), bodyWeights = emptyList()),
        )
        assertNull(r.value)
        assertEquals(StandardMissing.BODYWEIGHT, r.missing)
    }

    @Test
    fun trapBarByNameElseDeadliftFallback() {
        val trap = event("seven-event-v1", "trap-bar-5")
        val byName = evaluateEvent("seven-event-v1", trap, inputs(liftSets = listOf(set("custom-trap", 5, 350.0))))
        assertEquals(1.75, byName.value!!, 1e-3)
        assertEquals(StandardValueSource.LOGGED, byName.source)
        assertNull(byName.note)
        val fallback = evaluateEvent(
            "seven-event-v1",
            trap,
            inputs(liftSets = listOf(set("erv-weight-exercise-deadlift-v1", 5, 300.0))),
        )
        assertEquals(1.5, fallback.value!!, 1e-3)
        assertEquals("Deadlift used in its place", fallback.note)
    }

    @Test
    fun benchRepsAtBodyweightLoggedOrEstimated() {
        val ev = event("seven-event-v1", "bench-bw-reps")
        val logged = evaluateEvent("seven-event-v1", ev, inputs(liftSets = listOf(set("erv-weight-exercise-bench-v1", 12, 200.0))))
        assertEquals(12.0, logged.value!!, 0.0)
        assertEquals(StandardValueSource.LOGGED, logged.source)
        val est = evaluateEvent("seven-event-v1", ev, inputs(liftSets = listOf(set("erv-weight-exercise-bench-v1", 5, 240.0))))
        assertEquals(12.0, est.value!!, 0.0)
        assertEquals(StandardValueSource.ESTIMATED, est.source)
    }

    @Test
    fun armyTargetsByAgeBand() {
        val dl = event("army-aft-2025", "deadlift-3rm")
        assertEquals(140 * lb, tiersFor(dl, StandardProfile(ageYears = 40)).base, 1e-3)
        assertEquals(150 * lb, tiersFor(dl, StandardProfile(ageYears = 20)).base, 1e-3)
        assertEquals(150 * lb, tiersFor(dl, StandardProfile()).base, 1e-3)
    }

    @Test
    fun runsExactOrEstimatedFromPace() {
        val m = event("erv-general-v1", "mile")
        val exact = evaluateEvent("erv-general-v1", m, inputs(cardio = listOf(run(mile, 8.0))))
        assertEquals(480.0, exact.value!!, 0.5)
        assertEquals(StandardValueSource.LOGGED, exact.source)
        val long = evaluateEvent("erv-general-v1", m, inputs(cardio = listOf(run(5 * mile, 45.0))))
        assertEquals(540.0, long.value!!, 0.5)
        assertEquals(StandardValueSource.ESTIMATED, long.source)
        assertEquals("Average pace of a 5.0 mi run", long.note)
    }

    @Test
    fun ruckNeedsPackWeight() {
        val ruck = standard("ruck-12mi-v1").events.first()
        val light = evaluateEvent("ruck-12mi-v1", ruck, inputs(cardio = listOf(run(12 * mile, 170.0, "RUCK", "Ruck", 20 * lb))))
        assertNull(light.value)
        val heavy = evaluateEvent("ruck-12mi-v1", ruck, inputs(cardio = listOf(run(12 * mile, 170.0, "RUCK", "Ruck", 35 * lb))))
        assertEquals(170 * 60.0, heavy.value!!, 0.5)
        assertEquals(StandardTier.BASE, heavy.tier)
    }

    @Test
    fun broadJumpAgainstHeight() {
        val jump = event("seven-event-v1", "broad-jump")
        val results = listOf(StoredStandardResult("r1", "seven-event-v1", "broad-jump", 215.0, "2026-05-01"))
        val r = evaluateEvent("seven-event-v1", jump, inputs(testResults = results))
        assertEquals(35.0, r.value!!, 1e-3)
        assertEquals(StandardValueSource.TESTED, r.source)
        assertEquals(StandardTier.STRONG, r.tier)
        val noHeight = evaluateEvent("seven-event-v1", jump, inputs(testResults = results, profile = StandardProfile(ageYears = 40)))
        assertNull(noHeight.value)
        assertEquals(StandardMissing.HEIGHT, noHeight.missing)
    }

    @Test
    fun testedWinsATie() {
        val r = evaluateEvent(
            "erv-general-v1",
            event("erv-general-v1", "plank"),
            inputs(
                liftSets = listOf(set("erv-weight-exercise-bw-plank-v1", 0, null, seconds = 150)),
                testResults = listOf(StoredStandardResult("r1", "erv-general-v1", "plank", 150.0, "2026-06-01")),
            ),
        )
        assertEquals(StandardValueSource.TESTED, r.source)
        assertEquals("2026-06-01", r.date)
    }

    @Test
    fun overallIsTheLowestScoredTier() {
        val result = evaluateStandard(
            standard("erv-general-v1"),
            inputs(
                liftSets = listOf(
                    set("erv-weight-exercise-squat-v1", 1, 300.0),
                    set("erv-weight-exercise-bench-v1", 1, 220.0),
                ),
            ),
        )
        assertEquals(2, result.scoredCount)
        assertEquals(StandardTier.BASE, result.overallTier)
        assertEquals(1, result.tierCounts[StandardTier.STRONG])
    }

    @Test
    fun decodesWebResultsAndCustomStandards() {
        val results = decodeStandardResults(
            """{"ervStandardResultsVersion":1,"results":[
              {"id":"a","standardId":"s","eventId":"e","value":100,"date":"2026-05-01","lastModifiedEpochSeconds":10},
              {"id":"b","standardId":"s","eventId":"e","value":0,"date":"2026-05-01"},
              {"id":"c","standardId":"s","eventId":"e","value":5,"date":"2026-05-02","lastModifiedEpochSeconds":5}
            ],"deletedResults":{"c":9},"lastModifiedEpochSeconds":12,"extra":true}""",
        )!!
        assertEquals(listOf("a"), results.results.map { it.id })
        assertEquals(12L, results.lastModifiedEpochSeconds)

        val custom = decodeCustomStandards(
            """{"ervCustomStandardsVersion":1,"standards":[
              {"id":"custom-1","name":"Mine","description":"","lastModifiedEpochSeconds":5,"events":[
                {"id":"e1","name":"5K Run","measure":{"kind":"FASTEST","meters":5000,"cardioGroups":["RUN"]},
                 "lowerIsBetter":true,"tiers":{"BASE":1680,"STRONG":1440,"ELITE":1200}}]},
              {"id":"not-custom","name":"Bad","events":[]},
              {"id":"custom-2","name":"Gone","lastModifiedEpochSeconds":1,"events":[
                {"id":"e","name":"x","measure":{"kind":"MAX_REPS"},"tiers":{"BASE":1,"STRONG":2,"ELITE":3}}]}
            ],"deletedStandards":{"custom-2":3},"lastModifiedEpochSeconds":7}""",
        )!!
        assertEquals(listOf("custom-1"), custom.standards.map { it.id })
        val ev = custom.standards.first().events.first()
        assertEquals(5000.0, ev.measure.meters!!, 0.0)
        assertTrue(ev.lowerIsBetter)
        assertNull(decodeStandardResults("not json"))
    }

    @Test
    fun formatsValuesAndGaps() {
        val squat = event("erv-general-v1", "squat")
        assertEquals("1.5x BW", formatStandardValue(squat, 1.5, metric = false))
        assertEquals("8:00", formatStandardClock(480.0))
        assertEquals("1:02:15", formatStandardClock(3735.0))
        val r = evaluateEvent("erv-general-v1", squat, inputs(liftSets = listOf(set("erv-weight-exercise-squat-v1", 1, 250.0))))
        assertEquals("0.25x BW to Strong (about 50 lb)", formatStandardGap(r, metric = false, latestBodyWeightKg = 200 * lb))
    }

    @Test
    fun followedStandardsPutMainFirst() {
        val profile = com.erv.app.data.TrainingProfileNostrPayload(
            followedStandardIds = listOf("erv-general-v1", "custom-x", "gone"),
            mainStandardId = "custom-x",
        )
        val custom = listOf(FitnessStandard("custom-x", "Mine", events = standard("ruck-12mi-v1").events))
        assertEquals(listOf("custom-x", "erv-general-v1"), followedStandards(profile, builtIns, custom).map { it.id })
    }
}
