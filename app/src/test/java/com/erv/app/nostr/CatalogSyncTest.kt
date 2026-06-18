package com.erv.app.nostr

import com.erv.app.weighttraining.defaultCatalogExercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncTest {

    @Test
    fun bundledWeightCatalogPayload_includesFullBuiltinCatalog() {
        val payload = CatalogSync.bundledWeightCatalogPayload()
        assertEquals(ERV_BUILTIN_CATALOG_VERSION, payload.catalogVersion)
        assertTrue(payload.publishedAtEpochSeconds > 0)
        assertTrue(payload.exercises.size >= 80)
        assertEquals(defaultCatalogExercises().size, payload.exercises.size)
        assertTrue(payload.exercises.all { it.sessionSummaries.isEmpty() })
        assertTrue(payload.exercises.any { it.id == "erv-weight-exercise-bench-v1" })
    }

    @Test
    fun cardioCatalogPayload_includesUserSelectableActivities() {
        val payload = CatalogSync.cardioCatalogPayload()
        assertEquals(ERV_BUILTIN_CATALOG_VERSION, payload.catalogVersion)
        assertTrue(payload.activities.isNotEmpty())
        assertTrue(payload.activities.any { it.id == "WALK" })
        assertTrue(payload.activities.any { it.id == "RUN" })
        assertTrue(payload.activities.none { it.id == "ACTIVE_RECOVERY" })
        assertTrue(payload.activities.all { it.displayName.isNotBlank() })
    }

    @Test
    fun weightCatalog_roundTripsThroughJson() {
        val encoded = CatalogSync.encodeWeightCatalog(CatalogSync.weightCatalogPayload())
        val decoded = CatalogSync.decodeWeightCatalog(encoded)
        requireNotNull(decoded)
        assertEquals(ERV_BUILTIN_CATALOG_VERSION, decoded.catalogVersion)
        assertTrue(decoded.exercises.isNotEmpty())
    }

    @Test
    fun cardioCatalog_roundTripsThroughJson() {
        val encoded = CatalogSync.encodeCardioCatalog(CatalogSync.cardioCatalogPayload())
        val decoded = CatalogSync.decodeCardioCatalog(encoded)
        requireNotNull(decoded)
        assertEquals(ERV_BUILTIN_CATALOG_VERSION, decoded.catalogVersion)
        assertTrue(decoded.activities.isNotEmpty())
    }

    @Test
    fun catalogPublishAction_respectsRelayNewerThanBundled() {
        assertEquals("None", CatalogSync.catalogPublishActionForTest(2))
        assertEquals("Bootstrap", CatalogSync.catalogPublishActionForTest(null))
        assertEquals("Upgrade", CatalogSync.catalogPublishActionForTest(0))
        assertEquals("None", CatalogSync.catalogPublishActionForTest(ERV_BUILTIN_CATALOG_VERSION))
    }

    @Test
    fun catalogDTags_useErvCatalogNamespace() {
        assertTrue(CatalogSync.catalogDTags.all { it.startsWith("erv/catalog/") })
        assertEquals(3, CatalogSync.catalogDTags.size)
    }
}
