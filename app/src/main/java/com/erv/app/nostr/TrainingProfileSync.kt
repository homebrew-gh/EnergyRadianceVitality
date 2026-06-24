package com.erv.app.nostr

import android.content.Context
import com.erv.app.data.TrainingProfileNostrPayload
import com.erv.app.data.encodeTrainingProfile
import com.erv.app.data.isBlank
import kotlinx.serialization.json.Json

/**
 * Persists athlete training profile as an encrypted kind **30078** replaceable event
 * with `d` tag **erv/training-profile**.
 */
object TrainingProfileSync {

    const val D_TAG = "erv/training-profile"
    private const val KIND = 30078

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun plaintextFor(profile: TrainingProfileNostrPayload): Pair<String, String> =
        D_TAG to encodeTrainingProfile(profile)

    suspend fun saveToNetwork(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        profile: TrainingProfileNostrPayload,
        dataRelayUrls: List<String>,
    ): Boolean {
        val (_, plaintext) = plaintextFor(profile)
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
    ): TrainingProfileNostrPayload? {
        val latest = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)[D_TAG] ?: return null
        return fromLatestEvent(latest, signer)
    }

    suspend fun fromLatestEvent(latest: NostrEvent, signer: EventSigner): TrainingProfileNostrPayload? {
        return try {
            val decrypted = signer.decryptFromSelf(latest.content)
            json.decodeFromString(TrainingProfileNostrPayload.serializer(), decrypted)
        } catch (_: Exception) {
            null
        }
    }

    fun shouldPublish(profile: TrainingProfileNostrPayload): Boolean = !profile.isBlank()
}
