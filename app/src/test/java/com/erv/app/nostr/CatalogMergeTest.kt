package com.erv.app.nostr

import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.weighttraining.WeightExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogMergeTest {

    @Test
    fun effectiveWeightExercises_prefersRelayById() {
        val bundled = CatalogSync.bundledWeightCatalogPayload().exercises
        val bench = bundled.first { it.id == "erv-weight-exercise-bench-v1" }
        val relay = WeightCatalogPayload(
            catalogVersion = 2,
            exercises = listOf(bench.copy(name = "Relay Bench")),
        )
        val effective = CatalogMerge.effectiveWeightExercises(relay)
        assertTrue(effective.any { it.id == bench.id && it.name == "Relay Bench" })
        assertTrue(effective.size >= bundled.size)
    }

    @Test
    fun effectiveWeightExercises_usesBundledWhenRelayMissing() {
        val bundledCount = CatalogSync.bundledWeightCatalogPayload().exercises.size
        assertEquals(bundledCount, CatalogMerge.effectiveWeightExercises(null).size)
    }

    @Test
    fun upgradeWeightCatalog_preservesRelayOnlyCustomRows() {
        val bundled = CatalogSync.bundledWeightCatalogPayload()
        val custom = WeightExercise(
            id = "custom-web-lift",
            name = "Web Custom",
            muscleGroup = "chest",
            pushOrPull = com.erv.app.weighttraining.WeightPushPull.PUSH,
            equipment = com.erv.app.weighttraining.WeightEquipment.DUMBBELL,
        )
        val relay = bundled.copy(catalogVersion = 2, exercises = bundled.exercises + custom)
        val upgraded = CatalogMerge.upgradeWeightCatalog(bundled, relay)
        assertTrue(upgraded.exercises.any { it.id == custom.id })
        assertEquals(ERV_BUILTIN_CATALOG_VERSION, upgraded.catalogVersion)
    }

    @Test
    fun effectiveStretchCatalog_mergesBundledAndRelay() {
        val bundled = listOf(
            StretchCatalogEntry(id = "builtin_a", name = "A"),
            StretchCatalogEntry(id = "builtin_b", name = "B"),
        )
        val relay = StretchCatalogPayload(
            catalogVersion = 2,
            stretches = listOf(
                StretchCatalogEntry(id = "builtin_a", name = "A Relay"),
                StretchCatalogEntry(id = "custom_c", name = "C"),
            ),
        )
        val effective = CatalogMerge.effectiveStretchCatalog(bundled, relay)
        assertEquals("A Relay", effective.first { it.id == "builtin_a" }.name)
        assertTrue(effective.any { it.id == "custom_c" })
        assertTrue(effective.any { it.id == "builtin_b" })
    }

    @Test
    fun effectiveStretchCatalog_fillsBlankRelayTextFromBundled() {
        val bundled = listOf(
            StretchCatalogEntry(id = "builtin_a", name = "Hamstring stretch", procedure = "Reach for your toes."),
        )
        val relay = StretchCatalogPayload(
            catalogVersion = 2,
            stretches = listOf(
                StretchCatalogEntry(id = "builtin_a", name = "", procedure = "  ", requiresBothSides = true),
                StretchCatalogEntry(id = "custom_c", name = "", procedure = ""),
            ),
        )
        val effective = CatalogMerge.effectiveStretchCatalog(bundled, relay)
        val a = effective.first { it.id == "builtin_a" }
        assertEquals("Hamstring stretch", a.name)
        assertEquals("Reach for your toes.", a.procedure)
        assertTrue(a.requiresBothSides)
        // Relay-only entries have no bundled fallback and are passed through unchanged.
        assertEquals("", effective.first { it.id == "custom_c" }.name)
    }

    private val bundledRoot = com.erv.app.stretching.StretchCatalogRoot(
        catalogRevision = 2,
        addedByRevision = mapOf("2" to listOf("new_a", "new_b")),
        stretches = listOf(
            StretchCatalogEntry(id = "old_a", name = "Old A", muscleGroups = listOf("legs")),
            StretchCatalogEntry(id = "old_b", name = "Old B", muscleGroups = listOf("back")),
            StretchCatalogEntry(id = "new_a", name = "New A", muscleGroups = listOf("chest")),
            StretchCatalogEntry(id = "new_b", name = "New B", category = "restorative", defaultHoldSeconds = 120),
        ),
    )

    @Test
    fun offerNewBuiltinStretches_addsOnlyNewRevisionRowsAndKeepsWebEdits() {
        // The web renamed old_a, deleted old_b, and added a custom stretch.
        val relay = StretchCatalogPayload(
            catalogVersion = 4,
            stretches = listOf(
                StretchCatalogEntry(id = "old_a", name = "My Hamstring"),
                StretchCatalogEntry(id = "custom", name = "Custom"),
            ),
        )
        val out = CatalogMerge.offerNewBuiltinStretches(relay, bundledRoot, offeredRevision = 1, nowEpochSeconds = 99)!!
        assertEquals(listOf("old_a", "custom", "new_a", "new_b"), out.stretches.map { it.id })
        assertEquals(4, out.catalogVersion)
        assertEquals(99L, out.publishedAtEpochSeconds)
        val oldA = out.stretches.first { it.id == "old_a" }
        assertEquals("My Hamstring", oldA.name)
        // Tags fill in where the relay copy had none.
        assertEquals(listOf("legs"), oldA.muscleGroups)
        assertEquals(120, out.stretches.first { it.id == "new_b" }.defaultHoldSeconds)
    }

    @Test
    fun offerNewBuiltinStretches_onlyOnce() {
        val relay = StretchCatalogPayload(stretches = listOf(StretchCatalogEntry(id = "old_a", name = "Old A")))
        assertEquals(null, CatalogMerge.offerNewBuiltinStretches(relay, bundledRoot, offeredRevision = 2, nowEpochSeconds = 1))
    }

    @Test
    fun offerNewBuiltinStretches_nullWhenNothingChanges() {
        val relay = StretchCatalogPayload(stretches = bundledRoot.stretches)
        assertEquals(null, CatalogMerge.offerNewBuiltinStretches(relay, bundledRoot, offeredRevision = 1, nowEpochSeconds = 1))
    }

    @Test
    fun effectiveStretchCatalog_fillsMissingTagsFromBundled() {
        val effective = CatalogMerge.effectiveStretchCatalog(
            bundledRoot.stretches,
            StretchCatalogPayload(stretches = listOf(StretchCatalogEntry(id = "new_b", name = "Edited"))),
        )
        val b = effective.first { it.id == "new_b" }
        assertEquals("Edited", b.name)
        assertEquals(120, b.defaultHoldSeconds)
    }
}
