package com.erv.app.nostr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.relayPayloadDigestDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "erv_relay_payload_digests_v1"
)

/**
 * SHA-256 (hex) of canonical kind-30078 **plaintext** JSON per `d` tag. Updated after a successful
 * relay publish and when a post-fetch merge shows merged plaintext equals remote (no upload needed).
 */
class RelayPayloadDigestStore private constructor(private val appContext: Context) {

    companion object {
        @Volatile
        private var instance: RelayPayloadDigestStore? = null

        fun get(context: Context): RelayPayloadDigestStore {
            return instance ?: synchronized(this) {
                instance ?: RelayPayloadDigestStore(context.applicationContext).also { instance = it }
            }
        }

        /**
         * When merged local state serializes to the same plaintext the relay already has for that tag,
         * record the digest so [RelayPublishOutbox] skips redundant enqueues.
         */
        suspend fun reconcileIdenticalRemoteMerged(
            appContext: Context,
            remotePairs: List<Pair<String, String>>,
            mergedPairs: List<Pair<String, String>>,
        ) {
            if (remotePairs.isEmpty() || mergedPairs.isEmpty()) return
            val remote = remotePairs.toMap()
            val merged = mergedPairs.toMap()
            val store = get(appContext)
            for ((tag, remotePlain) in remote) {
                val m = merged[tag] ?: continue
                if (remotePlain == m) {
                    store.putDigest(tag, sha256HexUtf8(m))
                }
            }
        }
    }

    private object Keys {
        val MAP_JSON = stringPreferencesKey("digest_map_v1")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun getDigestHex(dTag: String): String? = loadMap()[dTag]

    suspend fun putDigest(dTag: String, sha256Hex: String) {
        appContext.relayPayloadDigestDataStore.edit { prefs ->
            val cur = decodeMap(prefs[Keys.MAP_JSON]).toMutableMap()
            cur[dTag] = sha256Hex
            prefs[Keys.MAP_JSON] = json.encodeToString(DigestMap.serializer(), DigestMap(cur))
        }
    }

    suspend fun recordPublishedPlaintext(dTag: String, plaintext: String) {
        putDigest(dTag, sha256HexUtf8(plaintext))
    }

    suspend fun clear() {
        appContext.relayPayloadDigestDataStore.edit { prefs ->
            prefs.remove(Keys.MAP_JSON)
        }
    }

    private suspend fun loadMap(): Map<String, String> =
        appContext.relayPayloadDigestDataStore.data.map { prefs ->
            decodeMap(prefs[Keys.MAP_JSON])
        }.first()

    private fun decodeMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(DigestMap.serializer(), raw).entries
        } catch (_: SerializationException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    @Serializable
    private data class DigestMap(val entries: Map<String, String> = emptyMap())
}
