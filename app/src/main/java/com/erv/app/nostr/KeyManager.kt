package com.erv.app.nostr

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import fr.acinq.secp256k1.Secp256k1

/**
 * Manages Nostr key material (nsec), login state, and relay configuration.
 * Private keys are stored in EncryptedSharedPreferences backed by Android Keystore.
 */
class KeyManager(context: Context) {

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

    var relayUrls: List<String>
        get() {
            migrateRelayUrlIfNeeded()
            return prefs.getStringSet(KEY_RELAY_URLS, emptySet())?.toList() ?: emptyList()
        }
        private set(value) = prefs.edit().putStringSet(KEY_RELAY_URLS, value.toSet()).apply()

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
     * Generate a fresh Nostr key pair and store it.
     */
    fun generateKeys(): String {
        val privKey = secureRandomBytes(32)
        require(Secp256k1.secKeyVerify(privKey)) { "Generated invalid key (astronomically unlikely); try again" }

        val compressed = Secp256k1.pubkeyCreate(privKey)
        val pubHex = Hex.encode(compressed.copyOfRange(1, 33))

        nsecHex = Hex.encode(privKey)
        publicKeyHex = pubHex
        loginMethod = LOGIN_NSEC
        return Bech32.nsecEncode(privKey)
    }

    /**
     * Store login state after connecting to Amber (NIP-55 signer).
     */
    fun loginWithAmber(pubkeyHex: String) {
        nsecHex = null
        publicKeyHex = pubkeyHex
        loginMethod = LOGIN_AMBER
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
            .apply()
    }

    companion object {
        private const val KEY_NSEC = "nsec"
        private const val KEY_PUBKEY = "pubkey"
        private const val KEY_RELAY_URL_LEGACY = "relay_url"
        private const val KEY_RELAY_URLS = "relay_urls"
        private const val KEY_LOGIN_METHOD = "login_method"

        const val LOGIN_NSEC = "nsec"
        const val LOGIN_AMBER = "amber"
    }
}
