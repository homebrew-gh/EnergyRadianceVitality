package com.erv.app.nostr

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

    suspend fun saveToNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        keyManager: KeyManager
    ): Boolean {
        val json = buildJsonObject {
            put("dataRelays", buildJsonArray { keyManager.relayUrls.forEach { add(it) } })
            put("socialRelays", buildJsonArray { keyManager.socialRelayUrls.forEach { add(it) } })
        }.toString()

        val encrypted = signer.encryptToSelf(json)
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 30078,
            tags = listOf(listOf("d", D_TAG)),
            content = encrypted
        )
        val signed = signer.sign(unsigned)
        return relayPool.publish(signed)
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
