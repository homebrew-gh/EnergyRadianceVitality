package com.erv.app.workouts

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val WORKOUTS_LIBRARY_D_TAG = "erv/workouts/library"

@Serializable
internal data class WorkoutLibraryPayload(
    val workouts: List<Workout> = emptyList(),
    val libraryUpdatedAtEpochSeconds: Long = 0L,
)

object WorkoutSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        prettyPrint = false
    }

    suspend fun publishLibrary(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: WorkoutLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val content = json.encodeToString(WorkoutLibraryPayload.serializer(), toPayload(state))
        return publishEvent(appContext, relayPool, signer, WORKOUTS_LIBRARY_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishLibraryIfSignedIn(
        appContext: Context,
        relayPool: RelayPool?,
        signer: EventSigner?,
        state: WorkoutLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        if (relayPool == null || signer == null || dataRelayUrls.isEmpty()) return false
        return publishLibrary(appContext, relayPool, signer, state, dataRelayUrls)
    }

    fun fullOutboxEntries(state: WorkoutLibraryState): List<Pair<String, String>> {
        val payload = toPayload(state)
        return listOf(
            WORKOUTS_LIBRARY_D_TAG to json.encodeToString(WorkoutLibraryPayload.serializer(), payload),
        )
    }

    fun clearOutboxEntries(): List<Pair<String, String>> =
        listOf(
            WORKOUTS_LIBRARY_D_TAG to json.encodeToString(
                WorkoutLibraryPayload.serializer(),
                WorkoutLibraryPayload(),
            ),
        )

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): WorkoutLibraryState {
        val master = latestByTag[WORKOUTS_LIBRARY_D_TAG]
            ?.decryptPayload(signer)
            ?.let(::decodeLibraryPayload)
            ?: return WorkoutLibraryState()
        return WorkoutLibraryState(
            workouts = master.workouts,
            activeRun = null,
            libraryUpdatedAtEpochSeconds = master.libraryUpdatedAtEpochSeconds,
        ).sanitized()
    }

    internal fun encodeLibraryPayloadForTest(state: WorkoutLibraryState): String =
        json.encodeToString(WorkoutLibraryPayload.serializer(), toPayload(state))

    internal fun decodeLibraryPayloadForTest(raw: String): WorkoutLibraryPayload? =
        decodeLibraryPayload(raw)

    private fun toPayload(state: WorkoutLibraryState): WorkoutLibraryPayload {
        val sanitized = state.sanitized()
        return WorkoutLibraryPayload(
            workouts = sanitized.workouts,
            libraryUpdatedAtEpochSeconds = sanitized.libraryUpdatedAtEpochSeconds,
        )
    }

    private suspend fun publishEvent(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        dTag: String,
        plaintext: String,
        dataRelayUrls: List<String>,
    ): Boolean {
        val result = RelayPublishOutbox.get(appContext).enqueueReplaceByDTagAndKickDrain(
            appContext,
            relayPool,
            signer,
            dataRelayUrls,
            dTag,
            plaintext,
        )
        return result.publishedFail == 0
    }

    private fun decodeLibraryPayload(raw: String): WorkoutLibraryPayload? =
        try {
            json.decodeFromString(WorkoutLibraryPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private suspend fun NostrEvent.decryptPayload(signer: EventSigner): String? =
        try {
            signer.decryptFromSelf(content)
        } catch (_: Exception) {
            null
        }
}
