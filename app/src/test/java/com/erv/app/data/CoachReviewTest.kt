package com.erv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachReviewTest {

    /** Shape the web companion publishes (`coachReviewSync.ts`), plus a field Android doesn't know. */
    private val webPayload = """
        {
          "ervCoachReviewVersion": 1,
          "generatedAtEpochSeconds": 1795608000,
          "periodLabel": "Last 4 weeks",
          "periodStart": "2026-10-29",
          "periodEnd": "2026-11-25",
          "weightUnit": "lb",
          "model": "test-model",
          "promptVersion": "3",
          "futureField": true,
          "review": {
            "headline": "Hold bench, add rows.",
            "status": { "consistency": "on_track", "progress": "flat", "recovery": "ok" },
            "lifts": [{ "name": "Bench Press", "trend": "flat", "next": "185 × 8, 8, 8", "why": "Reps fell." }],
            "nextWeek": [{ "action": "Add a row set.", "why": "Pulling is low." }],
            "wins": [],
            "watchOuts": [],
            "bodyWeight": null,
            "confidence": "medium",
            "missingData": null
          },
          "liftTargets": [
            { "exerciseId": "erv-weight-exercise-bench-v1", "name": "Bench Press", "trend": "flat", "next": "185 × 8, 8, 8", "why": "Reps fell." },
            { "exerciseId": "squat", "name": "Back Squat", "trend": "up", "next": "235 lb × 5", "why": "" }
          ]
        }
    """.trimIndent()

    @Test
    fun decodesWebPayloadAndIgnoresUnknownFields() {
        val payload = decodeCoachReview(webPayload)!!
        assertEquals("Hold bench, add rows.", payload.review.headline)
        assertEquals("on_track", payload.review.status.consistency)
        assertEquals(2, payload.liftTargets.size)
        assertNull(payload.review.bodyWeight)
    }

    @Test
    fun rejectsBlankOrBrokenPayloads() {
        assertNull(decodeCoachReview(null))
        assertNull(decodeCoachReview("not json"))
        assertNull(decodeCoachReview("""{"review":{"headline":""}}"""))
    }

    @Test
    fun coachLineAddsTheUnitOnlyWhenMissing() {
        val payload = decodeCoachReview(webPayload)!!
        val now = payload.generatedAtEpochSeconds + 86_400
        assertEquals("Coach: 185 × 8, 8, 8 lb", payload.coachLineFor("erv-weight-exercise-bench-v1", now))
        assertEquals("Coach: 235 lb × 5", payload.coachLineFor("squat", now))
        assertNull(payload.coachLineFor("deadlift", now))
    }

    @Test
    fun staleReviewStopsAddingCoachLines() {
        val payload = decodeCoachReview(webPayload)!!
        val later = payload.generatedAtEpochSeconds + (COACH_REVIEW_STALE_DAYS + 1) * 86_400
        assertTrue(payload.isStale(later))
        assertNull(payload.coachLineFor("erv-weight-exercise-bench-v1", later))
    }

    @Test
    fun roundTripsThroughLocalStorage() {
        val payload = decodeCoachReview(webPayload)!!
        assertEquals(payload, decodeCoachReview(encodeCoachReview(payload)))
    }

    @Test
    fun statusLabelsReadPlainly() {
        assertEquals("On track", coachStatusLabel("on_track"))
        assertEquals("OK", coachStatusLabel("ok"))
        assertEquals("Slipping", coachStatusLabel("slipping"))
    }
}
