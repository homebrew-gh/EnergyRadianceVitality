package com.erv.app.nostr

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import fr.acinq.secp256k1.Secp256k1

/**
 * Manages Nostr key material (nsec), login state, and relay configuration.
 * Private keys are stored in EncryptedSharedPreferences backed by Android Keystore.
 */
class KeyManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "erv_secure_prefs",
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // --- Stored values ---

    var nsecHex: String?
        get() = prefs.getString(KEY_NSEC, null)
        private set(value) = prefs.edit().putString(KEY_NSEC, value).apply()

    var publicKeyHex: String?
        get() = prefs.getString(KEY_PUBKEY, null)
        private set(value) = prefs.edit().putString(KEY_PUBKEY, value).apply()

    /** Relays used for encrypted activity data (kind 30078). */
    var relayUrls: List<String>
        get() {
            migrateRelayUrlIfNeeded()
            return prefs.getStringSet(KEY_RELAY_URLS, emptySet())?.toList() ?: emptyList()
        }
        private set(value) = prefs.edit().putStringSet(KEY_RELAY_URLS, value.toSet()).apply()

    /** Relays used for public social posts (e.g. kind 1 workout summaries). */
    var socialRelayUrls: List<String>
        get() = prefs.getStringSet(KEY_SOCIAL_RELAY_URLS, emptySet())?.toList() ?: emptyList()
        private set(value) = prefs.edit().putStringSet(KEY_SOCIAL_RELAY_URLS, value.toSet()).apply()

    fun addRelay(url: String) {
        val current = relayUrls.toMutableList()
        if (url !in current) {
            current.add(url)
            relayUrls = current
        }
    }

    fun removeRelay(url: String) {
        relayUrls = relayUrls.filter { it != url }
    }

    fun addSocialRelay(url: String) {
        val current = socialRelayUrls.toMutableList()
        if (url !in current) {
            current.add(url)
            socialRelayUrls = current
        }
    }

    fun removeSocialRelay(url: String) {
        socialRelayUrls = socialRelayUrls.filter { it != url }
    }

    fun removeRelayCompletely(url: String) {
        removeRelay(url)
        removeSocialRelay(url)
    }

    /** All relays (data + social), deduplicated. */
    fun allRelayUrls(): List<String> = (relayUrls + socialRelayUrls).distinct()

    /**
     * Relays to open on the [RelayPool]. Uses only what the user has saved when non-empty.
     * When empty (e.g. Amber before NIP-65), returns [DEFAULT_RELAYS] for connectivity only —
     * nothing is written to preferences.
     */
    fun relayUrlsForPool(): List<String> {
        val stored = allRelayUrls()
        return if (stored.isEmpty()) DEFAULT_RELAYS else stored
    }

    fun isDataRelay(url: String): Boolean = url in relayUrls
    fun isSocialRelay(url: String): Boolean = url in socialRelayUrls

    private fun migrateRelayUrlIfNeeded() {
        val legacy = prefs.getString(KEY_RELAY_URL_LEGACY, null)
        if (legacy != null) {
            val existing = prefs.getStringSet(KEY_RELAY_URLS, emptySet()) ?: emptySet()
            prefs.edit()
                .putStringSet(KEY_RELAY_URLS, existing + legacy)
                .remove(KEY_RELAY_URL_LEGACY)
                .apply()
        }
    }

    var loginMethod: String?
        get() = prefs.getString(KEY_LOGIN_METHOD, null)
        private set(value) = prefs.edit().putString(KEY_LOGIN_METHOD, value).apply()

    val isLoggedIn: Boolean
        get() = publicKeyHex != null

    val npub: String?
        get() = publicKeyHex?.let { Bech32.npubEncode(Hex.decode(it)) }

    // --- Login flows ---

    /**
     * Import an nsec (bech32 or raw hex) and derive the public key.
     */
    fun loginWithNsec(nsecInput: String) {
        val privKey = if (nsecInput.startsWith("nsec")) {
            Bech32.nsecDecode(nsecInput)
        } else {
            Hex.decode(nsecInput)
        }
        require(privKey.size == 32) { "Invalid private key length" }
        require(Secp256k1.secKeyVerify(privKey)) { "Invalid private key" }

        val compressed = Secp256k1.pubkeyCreate(privKey)
        val pubHex = Hex.encode(compressed.copyOfRange(1, 33))

        nsecHex = Hex.encode(privKey)
        publicKeyHex = pubHex
        loginMethod = LOGIN_NSEC
    }

    /**
     * Generate a fresh Nostr key pair, store it, and persist [DEFAULT_RELAYS] so the user
     * can publish encrypted data immediately. Imported nsec ([loginWithNsec]) does not do this;
     * it relies on NIP-65 / settings, then [populateDefaultRelaysIfStillEmpty].
     */
    fun generateKeys(): String {
        val privKey = secureRandomBytes(32)
        require(Secp256k1.secKeyVerify(privKey)) { "Generated invalid key (astronomically unlikely); try again" }

        val compressed = Secp256k1.pubkeyCreate(privKey)
        val pubHex = Hex.encode(compressed.copyOfRange(1, 33))

        nsecHex = Hex.encode(privKey)
        publicKeyHex = pubHex
        loginMethod = LOGIN_NSEC
        populateDefaultRelays()
        return Bech32.nsecEncode(privKey)
    }

    /**
     * After NIP-65 / settings fetch during post-login: persist [DEFAULT_RELAYS] only if nothing was loaded.
     */
    fun populateDefaultRelaysIfStillEmpty() {
        if (allRelayUrls().isEmpty()) populateDefaultRelays()
    }

    private fun populateDefaultRelays() {
        DEFAULT_RELAYS.forEach { url ->
            addRelay(url)
            addSocialRelay(url)
        }
    }

    /**
     * Package name of the NIP-55 signer app (e.g. Amber), saved after initial login.
     * If missing (pre-existing logins), discovered from the package manager on first access.
     */
    val amberPackageName: String?
        get() {
            prefs.getString(KEY_AMBER_PACKAGE, null)?.let { return it }
            if (loginMethod != LOGIN_AMBER) return null
            val discovered = discoverSignerPackage() ?: return null
            prefs.edit().putString(KEY_AMBER_PACKAGE, discovered).apply()
            return discovered
        }

    private fun discoverSignerPackage(): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
        return appContext.packageManager.queryIntentActivities(intent, 0)
            .firstOrNull()?.activityInfo?.packageName
    }

    /**
     * Store login state after connecting to Amber (NIP-55 signer).
     */
    fun loginWithAmber(pubkeyHex: String, packageName: String) {
        nsecHex = null
        publicKeyHex = pubkeyHex
        loginMethod = LOGIN_AMBER
        prefs.edit().putString(KEY_AMBER_PACKAGE, packageName).apply()
    }

    fun createLocalSigner(): LocalSigner? {
        val hex = nsecHex ?: return null
        return LocalSigner(Hex.decode(hex))
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_NSEC)
            .remove(KEY_PUBKEY)
            .remove(KEY_LOGIN_METHOD)
            .remove(KEY_RELAY_URLS)
            .remove(KEY_SOCIAL_RELAY_URLS)
            .remove(KEY_AMBER_PACKAGE)
            .apply()
    }

    companion object {
        private const val KEY_NSEC = "nsec"
        private const val KEY_PUBKEY = "pubkey"
        private const val KEY_RELAY_URL_LEGACY = "relay_url"
        private const val KEY_RELAY_URLS = "relay_urls"
        private const val KEY_SOCIAL_RELAY_URLS = "social_relay_urls"
        private const val KEY_LOGIN_METHOD = "login_method"
        private const val KEY_AMBER_PACKAGE = "amber_package"

        const val LOGIN_NSEC = "nsec"
        const val LOGIN_AMBER = "amber"

        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://nos.lol",
            "wss://relay.nostr.band"
        )
    }
}
