package com.erv.app.programs

import android.content.Context
import com.erv.app.nostr.EventSigner
import com.erv.app.nostr.dTagOrNull
import com.erv.app.nostr.fetchLatestKind30078ByDTag
import com.erv.app.nostr.NostrEvent
import com.erv.app.nostr.RelayPool
import com.erv.app.nostr.RelayPublishOutbox
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.LocalDate

private const val PROGRAMS_MASTER_D_TAG = "erv/programs/master"
private const val PROGRAMS_PROGRESS_PREFIX = "erv/programs/progress/"

@Serializable
private data class ProgramMasterPayload(
    val programs: List<FitnessProgram> = emptyList(),
    val activeProgramId: String? = null,
    val strategy: ProgramStrategy = ProgramStrategy(),
    val masterUpdatedAtEpochSeconds: Long = 0L
)

@Serializable
private data class ProgramDailyProgressPayload(
    val date: String,
    val completionState: Map<String, ProgramCompletionMark> = emptyMap()
)

object ProgramSync {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun publishAll(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: ProgramsLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val outbox = RelayPublishOutbox.get(appContext)
        outbox.enqueueAllDigestsAware(appContext, fullOutboxEntries(state))
        val result = outbox.kickDrain(relayPool, signer, dataRelayUrls)
        return result.publishedFail == 0 && result.remaining == 0
    }

    fun fullOutboxEntries(state: ProgramsLibraryState): List<Pair<String, String>> {
        val normalized = state.sanitized()
        val pairs = mutableListOf<Pair<String, String>>()
        val masterPayload = ProgramMasterPayload(
            programs = normalized.programs,
            activeProgramId = normalized.activeProgramId,
            strategy = normalized.strategy,
            masterUpdatedAtEpochSeconds = normalized.masterUpdatedAtEpochSeconds
        )
        pairs += PROGRAMS_MASTER_D_TAG to json.encodeToString(
            ProgramMasterPayload.serializer(),
            masterPayload
        )
        val completion = normalized.completionState
        val dates = completion.keys.mapNotNull(::programCompletionDateString).distinct().sorted()
        for (date in dates) {
            val payload = ProgramDailyProgressPayload(
                date = date,
                completionState = completion.filterKeys { key ->
                    programCompletionDateString(key) == date
                }
            )
            pairs += dailyProgressTag(date) to json.encodeToString(
                ProgramDailyProgressPayload.serializer(),
                payload
            )
        }
        return pairs
    }

    fun clearOutboxEntries(state: ProgramsLibraryState): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        pairs += PROGRAMS_MASTER_D_TAG to json.encodeToString(
            ProgramMasterPayload.serializer(),
            ProgramMasterPayload()
        )
        val dates = state.sanitized().completionState.keys.mapNotNull(::programCompletionDateString).distinct().sorted()
        for (date in dates) {
            pairs += dailyProgressTag(date) to json.encodeToString(
                ProgramDailyProgressPayload.serializer(),
                ProgramDailyProgressPayload(date = date)
            )
        }
        return pairs
    }

    suspend fun publishMaster(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: ProgramsLibraryState,
        dataRelayUrls: List<String>,
    ): Boolean {
        val normalized = state.sanitized()
        val payload = ProgramMasterPayload(
            programs = normalized.programs,
            activeProgramId = normalized.activeProgramId,
            strategy = normalized.strategy,
            masterUpdatedAtEpochSeconds = normalized.masterUpdatedAtEpochSeconds
        )
        val content = json.encodeToString(ProgramMasterPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, PROGRAMS_MASTER_D_TAG, content, dataRelayUrls)
    }

    suspend fun publishDailyProgress(
        appContext: Context,
        relayPool: RelayPool,
        signer: EventSigner,
        state: ProgramsLibraryState,
        date: LocalDate,
        dataRelayUrls: List<String>,
    ): Boolean {
        val dateIso = date.toString()
        val completion = state.sanitized().completionState.filterKeys { key ->
            programCompletionDateString(key) == dateIso
        }
        val payload = ProgramDailyProgressPayload(
            date = dateIso,
            completionState = completion
        )
        val content = json.encodeToString(ProgramDailyProgressPayload.serializer(), payload)
        return publishEvent(appContext, relayPool, signer, dailyProgressTag(dateIso), content, dataRelayUrls)
    }

    suspend fun fetchFromNetwork(
        relayPool: RelayPool,
        signer: EventSigner,
        pubkeyHex: String,
        timeoutMs: Long = 6000,
    ): ProgramsLibraryState? {
        val latestByTag = fetchLatestKind30078ByDTag(relayPool, pubkeyHex, timeoutMs)
        if (latestByTag.isEmpty()) return null
        return fromLatestByTag(latestByTag, signer)
    }

    suspend fun fromLatestByTag(
        latestByTag: Map<String, NostrEvent>,
        signer: EventSigner,
    ): ProgramsLibraryState? {
        val master = latestByTag[PROGRAMS_MASTER_D_TAG]
            ?.decryptPayload(signer)
            ?.let(::decodeMaster)

        val progress = latestByTag
            .filterKeys { it.startsWith(PROGRAMS_PROGRESS_PREFIX) }
            .mapNotNull { (dTag, event) ->
                val raw = event.decryptPayload(signer) ?: return@mapNotNull null
                decodeDailyProgress(raw, dTag.removePrefix(PROGRAMS_PROGRESS_PREFIX))
            }

        if (master == null && progress.isEmpty()) return null

        return ProgramsLibraryState(
            programs = master?.programs ?: emptyList(),
            activeProgramId = master?.activeProgramId,
            strategy = master?.strategy ?: ProgramStrategy(),
            masterUpdatedAtEpochSeconds = master?.masterUpdatedAtEpochSeconds ?: 0L,
            completionState = progress
                .flatMap { it.completionState.entries }
                .associate { it.toPair() }
        ).sanitized()
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

    private fun decodeMaster(raw: String): ProgramMasterPayload? =
        try {
            json.decodeFromString(ProgramMasterPayload.serializer(), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun decodeDailyProgress(raw: String, date: String): ProgramDailyProgressPayload? =
        try {
            val parsed = json.decodeFromString(ProgramDailyProgressPayload.serializer(), raw)
            if (parsed.date.isBlank()) parsed.copy(date = date) else parsed
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

    private fun dailyProgressTag(date: String): String = "$PROGRAMS_PROGRESS_PREFIX$date"
}
