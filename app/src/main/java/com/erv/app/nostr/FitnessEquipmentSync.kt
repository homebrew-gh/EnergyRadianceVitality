package com.erv.app.nostr

import com.erv.app.data.FitnessEquipmentNostrPayload
import com.erv.app.data.OwnedEquipmentItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Persists gym access and owned equipment as an encrypted kind **30078** replaceable event
 * with `d` tag **erv/equipment** (same kind as [SettingsSync], distinct `d` tag).
 */
object FitnessEquipmentSync {

    private const val D_TAG = "erv/equipment"
    private const val KIND = 30078

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun saveToNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        gymMembership: Boolean,
        equipment: List<OwnedEquipmentItem>,
    ): Boolean {
        val payload = FitnessEquipmentNostrPayload(gymMembership = gymMembership, equipment = equipment)
        val plaintext = json.encodeToString(FitnessEquipmentNostrPayload.serializer(), payload)
        val encrypted = signer.encryptToSelf(plaintext)
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = KIND,
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
        timeoutMs: Long = 6000,
    ): FitnessEquipmentNostrPayload? = coroutineScope {
        val subId = "erv-equipment-${System.currentTimeMillis()}"
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(KIND),
                authors = listOf(pubkeyHex),
                dTags = listOf(D_TAG),
                limit = 5
            )
        )

        val events = mutableListOf<NostrEvent>()
        val job = launch {
            relayPool.events.collect { (id, ev) ->
                if (id == subId && ev.kind == KIND) events.add(ev)
            }
        }
        delay(timeoutMs)
        job.cancel()
        relayPool.unsubscribe(subId)

        val latest = events.maxByOrNull { it.createdAt } ?: return@coroutineScope null
        try {
            val decrypted = signer.decryptFromSelf(latest.content)
            json.decodeFromString(FitnessEquipmentNostrPayload.serializer(), decrypted)
        } catch (_: Exception) {
            null
        }
    }
}
