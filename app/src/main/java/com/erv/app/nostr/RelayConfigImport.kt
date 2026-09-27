package com.erv.app.nostr

import com.erv.app.data.UserPreferences
import com.erv.app.data.WorkoutMediaUploadBackend
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

enum class RelayConfigImportResult {
    /** Pool has no connected relay within the wait window. */
    NOT_CONNECTED,
    /** Encrypted `erv/settings` was found and applied. */
    SETTINGS_APPLIED,
    /** Connected, but no `erv/settings` event was found (NIP-65 / Blossom may still have merged). */
    NOTHING_FOUND,
}

object RelayConfigImport {

    /**
     * Import relay roles from encrypted `erv/settings` and optional NIP-65 / Blossom metadata.
     */
    suspend fun fetchAndApply(
        keyManager: KeyManager,
        signer: EventSigner,
        userPreferences: UserPreferences,
        pool: RelayPool,
        connectTimeoutMs: Long = 15_000,
    ): RelayConfigImportResult {
        if (!RelayPublishTargets.shouldAttemptRelayNetworkImport(keyManager.relayUrlsForPool())) {
            return RelayConfigImportResult.NOT_CONNECTED
        }
        if (!pool.awaitAtLeastOneConnected(timeoutMs = connectTimeoutMs)) {
            return RelayConfigImportResult.NOT_CONNECTED
        }

        val pubkey = keyManager.publicKeyHex ?: return RelayConfigImportResult.NOT_CONNECTED
        val (nip65Urls, blossomUrls) = coroutineScope {
            val nip65 = async { Nip65.fetchRelayListFromNetwork(pool, pubkey, timeoutMs = 8000) }
            val nipB7 = async { NipB7.fetchBlossomServersFromNetwork(pool, pubkey, timeoutMs = 8000) }
            nip65.await() to nipB7.await()
        }
        nip65Urls.forEach { keyManager.addSocialRelay(it) }
        applyImportedBlossomServersFromProfile(userPreferences, blossomUrls)

        pool.setRelays(keyManager.relayUrlsForPool())
        delay(1500)

        val config = SettingsSync.fetchFromNetwork(pool, signer, pubkey, timeoutMs = 5000)
        if (config != null) {
            SettingsSync.applyToKeyManager(config, keyManager)
            return RelayConfigImportResult.SETTINGS_APPLIED
        }
        return RelayConfigImportResult.NOTHING_FOUND
    }

    private suspend fun applyImportedBlossomServersFromProfile(
        userPreferences: UserPreferences,
        blossomUrls: List<String>,
    ) {
        if (userPreferences.peekBlossomPublicServerOrigin().isNotBlank()) return
        val first = blossomUrls.firstOrNull() ?: return
        val normalized = Nip96Uploader.normalizeMediaServerOrigin(first)
        if (normalized.isEmpty()) return
        userPreferences.setBlossomPublicServerOrigin(normalized)
        userPreferences.setWorkoutMediaUploadBackend(WorkoutMediaUploadBackend.BLOSSOM)
    }
}
