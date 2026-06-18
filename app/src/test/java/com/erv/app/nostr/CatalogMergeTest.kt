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
}
