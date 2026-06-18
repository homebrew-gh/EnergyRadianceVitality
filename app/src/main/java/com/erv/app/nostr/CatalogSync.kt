package com.erv.app.nostr

import android.content.Context
import com.erv.app.cardio.CardioBuiltinActivity
import com.erv.app.cardio.cardioBuiltinHiitSectionOrder
import com.erv.app.cardio.cardioBuiltinHybridSectionOrder
import com.erv.app.cardio.cardioBuiltinSteadySectionOrder
import com.erv.app.cardio.displayName
import com.erv.app.cardio.offersHiitIntervalTemplate
import com.erv.app.cardio.supportsTreadmillModality
import com.erv.app.stretching.StretchCatalogEntry
import com.erv.app.stretching.StretchCatalogLoader
import com.erv.app.weighttraining.WeightExercise
import com.erv.app.weighttraining.defaultCatalogExercises
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Bump when APK built-in catalog content changes. Used to bootstrap empty relays and to merge
 * app updates into an existing relay copy without overwriting newer web-authored catalogs.
 */
const val ERV_BUILTIN_CATALOG_VERSION = 1

const val WEIGHT_CATALOG_D_TAG = "erv/catalog/weight"
const val STRETCH_CATALOG_D_TAG = "erv/catalog/stretch"
const val CARDIO_CATALOG_D_TAG = "erv/catalog/cardio"

@Serializable
data class WeightCatalogPayload(
    val catalogVersion: Int = ERV_BUILTIN_CATALOG_VERSION,
    val publishedAtEpochSeconds: Long = 0L,
    val exercises: List<WeightExercise> = emptyList(),
)

@Serializable
data class StretchCatalogPayload(
    val catalogVersion: Int = ERV_BUILTIN_CATALOG_VERSION,
    val publishedAtEpochSeconds: Long = 0L,
    val stretches: List<StretchCatalogEntry> = emptyList(),
)

@Serializable
data class CardioCatalogActivity(
    /** [CardioBuiltinActivity] enum name, e.g. `WALK`, `RUN`. */
    val id: String,
    val displayName: String,
    /** UI grouping: `steady`, `hybrid`, or `hiit`. */
    val section: String,
    val offersHiitIntervalTemplate: Boolean = false,
    val supportsTreadmillModality: Boolean = false,
)

@Serializable
data class CardioCatalogPayload(
    val catalogVersion: Int = ERV_BUILTIN_CATALOG_VERSION,
    val publishedAtEpochSeconds: Long = 0L,
    val activities: List<CardioCatalogActivity> = emptyList(),
)

object CatalogSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val catalogDTags: List<String> = listOf(
        WEIGHT_CATALOG_D_TAG,
        STRETCH_CATALOG_D_TAG,
        CARDIO_CATALOG_D_TAG,
    )

    fun bundledWeightCatalogPayload(): WeightCatalogPayload {
        val now = System.currentTimeMillis() / 1000
        val exercises = defaultCatalogExercises().map { it.copy(sessionSummaries = emptyList()) }
        return WeightCatalogPayload(
            catalogVersion = ERV_BUILTIN_CATALOG_VERSION,
            publishedAtEpochSeconds = now,
            exercises = exercises,
        )
    }

    fun bundledStretchCatalogPayload(context: Context): StretchCatalogPayload {
        val now = System.currentTimeMillis() / 1000
        return StretchCatalogPayload(
            catalogVersion = ERV_BUILTIN_CATALOG_VERSION,
            publishedAtEpochSeconds = now,
            stretches = StretchCatalogLoader.load(context),
        )
    }

    fun bundledCardioCatalogPayload(): CardioCatalogPayload {
        val now = System.currentTimeMillis() / 1000
        val activities = buildList {
            for (activity in cardioBuiltinSteadySectionOrder) {
                add(activity.toCatalogEntry("steady"))
            }
            for (activity in cardioBuiltinHybridSectionOrder) {
                add(activity.toCatalogEntry("hybrid"))
            }
            for (activity in cardioBuiltinHiitSectionOrder) {
                add(activity.toCatalogEntry("hiit"))
            }
        }
        return CardioCatalogPayload(
            catalogVersion = ERV_BUILTIN_CATALOG_VERSION,
            publishedAtEpochSeconds = now,
            activities = activities,
        )
    }

    /** @deprecated Use [bundledWeightCatalogPayload]. */
    fun weightCatalogPayload(): WeightCatalogPayload = bundledWeightCatalogPayload()

    /** @deprecated Use [bundledStretchCatalogPayload]. */
    fun stretchCatalogPayload(context: Context): StretchCatalogPayload =
        bundledStretchCatalogPayload(context)

    /** @deprecated Use [bundledCardioCatalogPayload]. */
    fun cardioCatalogPayload(): CardioCatalogPayload = bundledCardioCatalogPayload()

    fun encodeWeightCatalog(payload: WeightCatalogPayload): String =
        json.encodeToString(WeightCatalogPayload.serializer(), payload)

    fun encodeStretchCatalog(payload: StretchCatalogPayload): String =
        json.encodeToString(StretchCatalogPayload.serializer(), payload)

    fun encodeCardioCatalog(payload: CardioCatalogPayload): String =
        json.encodeToString(CardioCatalogPayload.serializer(), payload)

    fun decodeWeightCatalog(raw: String): WeightCatalogPayload? =
        try {
            json.decodeFromString(WeightCatalogPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun decodeStretchCatalog(raw: String): StretchCatalogPayload? =
        try {
            json.decodeFromString(StretchCatalogPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun decodeCardioCatalog(raw: String): CardioCatalogPayload? =
        try {
            json.decodeFromString(CardioCatalogPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * Applies relay catalogs locally, then bootstraps or upgrades relay copies when appropriate.
     *
     * - **Offline / no Nostr:** callers skip this; APK bundled catalogs are used via [CatalogStore].
     * - **Relay missing catalog:** publish bundled bootstrap copy.
     * - **Relay newer than APK (`catalogVersion` > [ERV_BUILTIN_CATALOG_VERSION]):** adopt relay;
     *   do not overwrite (web or another device owns the catalog).
     * - **APK newer than relay:** merge bundled rows into relay and publish upgrade.
     */
    suspend fun syncCatalogs(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        latestByTag: Map<String, NostrEvent>,
        dataRelayUrls: List<String>,
    ) {
        if (dataRelayUrls.isEmpty()) return

        val store = CatalogStore.get(appContext)
        store.hydrateFromDisk()

        val remoteWeight = decodeRelayCatalog(latestByTag[WEIGHT_CATALOG_D_TAG], signer, ::decodeWeightCatalog)
        val remoteStretch = decodeRelayCatalog(latestByTag[STRETCH_CATALOG_D_TAG], signer, ::decodeStretchCatalog)
        val remoteCardio = decodeRelayCatalog(latestByTag[CARDIO_CATALOG_D_TAG], signer, ::decodeCardioCatalog)

        store.applyRelayCatalogs(
            weight = remoteWeight,
            stretch = remoteStretch,
            cardio = remoteCardio,
        )

        val bundledWeight = bundledWeightCatalogPayload()
        val bundledStretch = bundledStretchCatalogPayload(appContext)
        val bundledCardio = bundledCardioCatalogPayload()

        when (catalogPublishAction(remoteWeight?.catalogVersion)) {
            CatalogPublishAction.Bootstrap ->
                publishCatalog(appContext, relayPool, signer, dataRelayUrls, WEIGHT_CATALOG_D_TAG, encodeWeightCatalog(bundledWeight))
            CatalogPublishAction.Upgrade ->
                publishCatalog(
                    appContext,
                    relayPool,
                    signer,
                    dataRelayUrls,
                    WEIGHT_CATALOG_D_TAG,
                    encodeWeightCatalog(CatalogMerge.upgradeWeightCatalog(bundledWeight, remoteWeight!!)),
                )
            CatalogPublishAction.None -> Unit
        }

        when (catalogPublishAction(remoteStretch?.catalogVersion)) {
            CatalogPublishAction.Bootstrap ->
                publishCatalog(appContext, relayPool, signer, dataRelayUrls, STRETCH_CATALOG_D_TAG, encodeStretchCatalog(bundledStretch))
            CatalogPublishAction.Upgrade ->
                publishCatalog(
                    appContext,
                    relayPool,
                    signer,
                    dataRelayUrls,
                    STRETCH_CATALOG_D_TAG,
                    encodeStretchCatalog(CatalogMerge.upgradeStretchCatalog(bundledStretch, remoteStretch!!)),
                )
            CatalogPublishAction.None -> Unit
        }

        when (catalogPublishAction(remoteCardio?.catalogVersion)) {
            CatalogPublishAction.Bootstrap ->
                publishCatalog(appContext, relayPool, signer, dataRelayUrls, CARDIO_CATALOG_D_TAG, encodeCardioCatalog(bundledCardio))
            CatalogPublishAction.Upgrade ->
                publishCatalog(
                    appContext,
                    relayPool,
                    signer,
                    dataRelayUrls,
                    CARDIO_CATALOG_D_TAG,
                    encodeCardioCatalog(CatalogMerge.upgradeCardioCatalog(bundledCardio, remoteCardio!!)),
                )
            CatalogPublishAction.None -> Unit
        }
    }

    /** @deprecated Use [syncCatalogs]. */
    suspend fun maybePublishCatalogs(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        latestByTag: Map<String, NostrEvent>,
        dataRelayUrls: List<String>,
    ) = syncCatalogs(appContext, relayPool, signer, latestByTag, dataRelayUrls)

    private enum class CatalogPublishAction {
        Bootstrap,
        Upgrade,
        None,
    }

    internal fun catalogPublishActionForTest(remoteVersion: Int?): String =
        catalogPublishAction(remoteVersion).name

    private fun catalogPublishAction(remoteVersion: Int?): CatalogPublishAction {
        if (remoteVersion == null) return CatalogPublishAction.Bootstrap
        return when {
            remoteVersion > ERV_BUILTIN_CATALOG_VERSION -> CatalogPublishAction.None
            remoteVersion < ERV_BUILTIN_CATALOG_VERSION -> CatalogPublishAction.Upgrade
            else -> CatalogPublishAction.None
        }
    }

    private suspend fun <T> decodeRelayCatalog(
        remoteEvent: NostrEvent?,
        signer: EventSigner,
        decode: (String) -> T?,
    ): T? {
        if (remoteEvent == null) return null
        val raw = try {
            signer.decryptFromSelf(remoteEvent.content)
        } catch (_: Exception) {
            return null
        }
        return decode(raw)
    }

    private suspend fun publishCatalog(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dataRelayUrls: List<String>,
        dTag: String,
        plaintext: String,
    ) {
        RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            dTag,
            plaintext,
        )
    }

    private fun CardioBuiltinActivity.toCatalogEntry(section: String): CardioCatalogActivity =
        CardioCatalogActivity(
            id = name,
            displayName = displayName(),
            section = section,
            offersHiitIntervalTemplate = offersHiitIntervalTemplate(),
            supportsTreadmillModality = supportsTreadmillModality(),
        )
}
