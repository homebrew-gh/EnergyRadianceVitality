package com.erv.app.stretching

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StretchCatalogAssetTest {

    private val root: StretchCatalogRoot by lazy {
        val file = listOf(
            File("src/main/assets/stretch_catalog.json"),
            File("app/src/main/assets/stretch_catalog.json"),
        ).first { it.exists() }
        Json { ignoreUnknownKeys = true }.decodeFromString(StretchCatalogRoot.serializer(), file.readText())
    }

    /** The muscle groups weight exercises use, so stretches can be matched to a workout. */
    private val weightMuscleGroups = setOf("chest", "back", "shoulders", "biceps", "triceps", "legs", "core", "neck")

    @Test
    fun idsAreUniqueAndEveryStretchHasTextAndTags() {
        val ids = root.stretches.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (s in root.stretches) {
            assertTrue("${s.id} name", s.name.isNotBlank())
            assertTrue("${s.id} procedure", s.procedure.isNotBlank())
            assertTrue("${s.id} groups ${s.muscleGroups}", weightMuscleGroups.containsAll(s.muscleGroups))
            if (s.id != "builtin_savasana") assertTrue("${s.id} has no groups", s.muscleGroups.isNotEmpty())
        }
    }

    @Test
    fun revisionListsOnlyRealIds() {
        val ids = root.stretches.map { it.id }.toSet()
        assertTrue(root.catalogRevision >= 2)
        root.addedByRevision.forEach { (rev, added) ->
            assertTrue(rev.toInt() in 2..root.catalogRevision)
            assertTrue(ids.containsAll(added))
        }
    }

    @Test
    fun restorativePosesHaveLongerHolds() {
        val restorative = root.stretches.filter { it.isRestorative }
        assertEquals(8, restorative.size)
        for (r in restorative) {
            val hold = r.defaultHoldSeconds ?: 0
            assertTrue("${r.id} hold $hold", hold in 60..STRETCH_HOLD_MAX_SECONDS)
        }
    }

    @Test
    fun settleAndRestNeedsARestorativePose() {
        val child = root.stretches.first { it.id == "builtin_childs_pose" }
        val sav = root.stretches.first { it.id == "builtin_savasana" }
        val r = StretchRoutine(name = "R", settleAndRest = true)
        assertEquals(false, r.playsSettleAndRest(listOf(child)))
        assertEquals(true, r.playsSettleAndRest(listOf(child, sav)))
        assertEquals(false, r.copy(settleAndRest = false).playsSettleAndRest(listOf(sav)))
    }
}
