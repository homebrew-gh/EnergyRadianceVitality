package com.erv.app.nostr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.StretchCatalogLoader
import com.erv.app.weighttraining.WeightExercise
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.catalogStoreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "erv_relay_catalogs",
)

@Serializable
private data class StoredRelayCatalogs(
    val weight: WeightCatalogPayload? = null,
    val stretch: StretchCatalogPayload? = null,
    val cardio: CardioCatalogPayload? = null,
)

/**
 * Caches relay-synced catalogs locally. APK bundled catalogs remain the offline fallback;
 * when a relay copy exists it becomes the effective catalog for Nostr/web users.
 */
class CatalogStore private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Volatile
    private var cache: StoredRelayCatalogs = StoredRelayCatalogs()

    private object Keys {
        val RELAY_CATALOGS = stringPreferencesKey("relay_catalogs_json")
    }

    suspend fun hydrateFromDisk() {
        val raw = appContext.catalogStoreDataStore.data.first()[Keys.RELAY_CATALOGS]
        cache = decodeStored(raw)
    }

    suspend fun applyRelayCatalogs(
        weight: WeightCatalogPayload?,
        stretch: StretchCatalogPayload?,
        cardio: CardioCatalogPayload?,
    ) {
        cache = StoredRelayCatalogs(
            weight = weight,
            stretch = stretch,
            cardio = cardio,
        )
        appContext.catalogStoreDataStore.edit { prefs ->
            prefs[Keys.RELAY_CATALOGS] = json.encodeToString(StoredRelayCatalogs.serializer(), cache)
        }
    }

    suspend fun clearRelayCatalogs() {
        applyRelayCatalogs(weight = null, stretch = null, cardio = null)
    }

    fun relayWeightCatalog(): WeightCatalogPayload? = cache.weight

    fun relayStretchCatalog(): StretchCatalogPayload? = cache.stretch

    fun relayCardioCatalog(): CardioCatalogPayload? = cache.cardio

    fun effectiveWeightExercises(): List<WeightExercise> =
        CatalogMerge.effectiveWeightExercises(cache.weight)

    fun effectiveStretchCatalog(): List<StretchCatalogEntry> =
        CatalogMerge.effectiveStretchCatalog(
            StretchCatalogLoader.load(appContext),
            cache.stretch,
        )

    fun effectiveCardioCatalog(): List<CardioCatalogActivity> =
        CatalogMerge.effectiveCardioCatalog(
            CatalogSync.cardioCatalogPayload(),
            cache.cardio,
        )

    /** Blocking read for repository decode paths; uses in-memory cache after [hydrateFromDisk]. */
    fun effectiveWeightExercisesBlocking(): List<WeightExercise> = effectiveWeightExercises()

    fun ensureHydratedBlocking() {
        if (cache == StoredRelayCatalogs()) {
            runBlocking { hydrateFromDisk() }
        }
    }

    private fun decodeStored(raw: String?): StoredRelayCatalogs {
        if (raw.isNullOrBlank()) return StoredRelayCatalogs()
        return try {
            json.decodeFromString(StoredRelayCatalogs.serializer(), raw)
        } catch (_: SerializationException) {
            StoredRelayCatalogs()
        } catch (_: IllegalArgumentException) {
            StoredRelayCatalogs()
        }
    }

    companion object {
        @Volatile
        private var instance: CatalogStore? = null

        fun get(context: Context): CatalogStore =
            instance ?: synchronized(this) {
                instance ?: CatalogStore(context.applicationContext).also { instance = it }
            }
    }
}
