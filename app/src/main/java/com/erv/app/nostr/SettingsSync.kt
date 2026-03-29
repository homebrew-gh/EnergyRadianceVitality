package com.erv.app.nostr

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*

/**
 * Persists and restores relay configuration as an encrypted kind 30078
 * replaceable event with d-tag "erv/settings". This lets settings survive
 * across devices/reinstalls.
 */
object SettingsSync {

    private const val D_TAG = "erv/settings"

    data class RelayConfig(
        val dataRelays: List<String>,
        val socialRelays: List<String>
    )

    fun plaintextFor(
        dataRelays: List<String>,
        socialRelays: List<String>,
    ): Pair<String, String> {
        val json = buildJsonObject {
            put("dataRelays", buildJsonArray { dataRelays.forEach { add(it) } })
            put("socialRelays", buildJsonArray { socialRelays.forEach { add(it) } })
        }.toString()
        return D_TAG to json
    }

    suspend fun saveToNetwork(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        keyManager: KeyManager
    ): Boolean {
        val (_, json) = plaintextFor(keyManager.relayUrls, keyManager.socialRelayUrls)

        val r = RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
            appContext,
            relayPool,
            signer,
            keyManager.relayUrlsForKind30078Publish(),
            D_TAG,
            json,
        )
        return r.publishedFail == 0
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000
    ): RelayConfig? = coroutineScope {
        val subId = "erv-settings-${System.currentTimeMillis()}"
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(30078),
                authors = listOf(pubkeyHex),
                dTags = listOf(D_TAG),
                limit = 5
            )
        )

        val events = mutableListOf<NostrEvent>()
        val job = launch {
            relayPool.events.collect { (id, ev) ->
                if (id == subId && ev.kind == 30078) events.add(ev)
            }
        }
        delay(timeoutMs)
        job.cancel()
        relayPool.unsubscribe(subId)

        val latest = events.maxByOrNull { it.createdAt } ?: return@coroutineScope null
        try {
            val decrypted = signer.decryptFromSelf(latest.content)
            val obj = Json.parseToJsonElement(decrypted).jsonObject
            RelayConfig(
                dataRelays = obj["dataRelays"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                socialRelays = obj["socialRelays"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun applyToKeyManager(config: RelayConfig, keyManager: KeyManager) {
        config.dataRelays.forEach { keyManager.addRelay(it) }
        config.socialRelays.forEach { keyManager.addSocialRelay(it) }
    }
}
