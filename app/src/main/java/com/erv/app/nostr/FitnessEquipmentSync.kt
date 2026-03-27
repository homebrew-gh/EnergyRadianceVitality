package com.erv.app.nostr

import android.content.Context
import com.erv.app.data.FitnessEquipmentNostrPayload
import com.erv.app.data.OwnedEquipmentItem
import com.erv.app.nostr.dTagOrNull
import com.erv.app.nostr.fetchLatestKind30078ByDTag
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

    fun plaintextFor(gymMembership: Boolean, equipment: List<OwnedEquipmentItem>): Pair<String, String> {
        val payload = FitnessEquipmentNostrPayload(gymMembership = gymMembership, equipment = equipment)
        return D_TAG to json.encodeToString(FitnessEquipmentNostrPayload.serializer(), payload)
    }

    suspend fun saveToNetwork(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        gymMembership: Boolean,
        equipment: List<OwnedEquipmentItem>,
        dataRelayUrls: List<String>,
    ): Boolean {
        val (_, plaintext) = plaintextFor(gymMembership, equipment)
        val r = RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            D_TAG,
            plaintext,
        )
        return r.publishedFail == 0
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000,
    ): FitnessEquipmentNostrPayload? {
        val latest = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)[D_TAG] ?: return null
        return fromLatestEvent(latest, signer)
    }

    suspend fun fromLatestEvent(latest: NostrEvent, signer: EventSigner): FitnessEquipmentNostrPayload? {
        return try {
            val decrypted = signer.decryptFromSelf(latest.content)
            json.decodeFromString(FitnessEquipmentNostrPayload.serializer(), decrypted)
        } catch (_: Exception) {
            null
        }
    }
}
