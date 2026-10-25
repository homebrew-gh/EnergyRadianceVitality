package com.erv.app.nostr

import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.defaultCatalogExercises

/**
 * Merges APK-shipped catalogs with optional relay-synced catalogs.
 * Bundled catalogs always remain the offline fallback; relay entries win by id when present.
 */
object CatalogMerge {

    fun effectiveWeightExercises(relay: WeightCatalogPayload?): List<WeightExercise> =
        mergeById(defaultCatalogExercises(), relay?.exercises.orEmpty()) { it.id }
            .map { it.copy(sessionSummaries = emptyList()) }

    /**
     * Relay entries win by id, but a relay copy of a bundled stretch that has a blank name or
     * procedure inherits the bundled text so the guided player never shows/speaks an empty stretch.
     */
    fun effectiveStretchCatalog(
        bundled: List<StretchCatalogEntry>,
        relay: StretchCatalogPayload?,
    ): List<StretchCatalogEntry> {
        val bundledById = bundled.associateBy { it.id }
        val relayFilled = relay?.stretches.orEmpty().map { entry ->
            val base = bundledById[entry.id] ?: return@map entry
            entry.copy(
                name = entry.name.ifBlank { base.name },
                procedure = entry.procedure.ifBlank { base.procedure },
            )
        }
        return mergeById(bundled, relayFilled) { it.id }
    }

    fun effectiveCardioCatalog(
        bundled: CardioCatalogPayload,
        relay: CardioCatalogPayload?,
    ): List<CardioCatalogActivity> =
        mergeById(bundled.activities, relay?.activities.orEmpty()) { it.id }

    /** App update: merge new bundled rows into relay without dropping relay-only custom rows. */
    fun upgradeWeightCatalog(
        bundled: WeightCatalogPayload,
        relay: WeightCatalogPayload,
    ): WeightCatalogPayload {
        val merged = mergeById(relay.exercises, bundled.exercises) { it.id }
        return bundled.copy(
            catalogVersion = ERV_BUILTIN_CATALOG_VERSION,
            publishedAtEpochSeconds = System.currentTimeMillis() / 1000,
            exercises = merged,
        )
    }

    fun upgradeStretchCatalog(
        bundled: StretchCatalogPayload,
        relay: StretchCatalogPayload,
    ): StretchCatalogPayload {
        val merged = mergeById(relay.stretches, bundled.stretches) { it.id }
        return bundled.copy(
            catalogVersion = ERV_BUILTIN_CATALOG_VERSION,
            publishedAtEpochSeconds = System.currentTimeMillis() / 1000,
            stretches = merged,
        )
    }

    fun upgradeCardioCatalog(
        bundled: CardioCatalogPayload,
        relay: CardioCatalogPayload,
    ): CardioCatalogPayload {
        val merged = mergeById(relay.activities, bundled.activities) { it.id }
        return bundled.copy(
            catalogVersion = ERV_BUILTIN_CATALOG_VERSION,
            publishedAtEpochSeconds = System.currentTimeMillis() / 1000,
            activities = merged,
        )
    }

    private fun <T> mergeById(bundled: List<T>, relay: List<T>, id: (T) -> String): List<T> {
        val byId = LinkedHashMap<String, T>()
        bundled.forEach { byId[id(it)] = it }
        relay.forEach { byId[id(it)] = it }
        return byId.values.toList()
    }
}
