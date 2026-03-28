package com.erv.app.nostr

import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * [NIP-01](https://nips.nostr.com/01) profile metadata (kind **0**).
 */
data class ProfileMetadata(
    val name: String = "",
    val displayName: String = "",
    val about: String = "",
    val picture: String = "",
    val nip05: String = "",
    val lud16: String = "",
) {
    fun primaryLabel(): String = displayName.ifBlank { name }

    fun hasPublicTextOrPicture(): Boolean =
        name.isNotBlank() || displayName.isNotBlank() || about.isNotBlank() || picture.isNotBlank()

    /** Single display-name field maps to both `name` and `display_name` for broad client support. */
    fun withUnifiedDisplayName(label: String): ProfileMetadata {
        val t = label.trim()
        return copy(name = t, displayName = t)
    }

    fun toKind0ContentJson(): String {
        val o = buildJsonObject {
            if (name.isNotBlank()) put("name", name)
            if (displayName.isNotBlank()) put("display_name", displayName)
            if (about.isNotBlank()) put("about", about)
            if (picture.isNotBlank()) put("picture", picture.trim())
            if (nip05.isNotBlank()) put("nip05", nip05.trim())
            if (lud16.isNotBlank()) put("lud16", lud16.trim())
        }
        return o.toString()
    }
}

object Nip01Metadata {

    fun parseFromKind0Event(event: NostrEvent): ProfileMetadata? {
        if (event.kind != 0) return null
        return parseFromContent(event.content)
    }

    fun parseFromContent(content: String): ProfileMetadata? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ProfileMetadata()
        return try {
            val obj = Json.parseToJsonElement(trimmed).jsonObject
            fun s(key: String) = obj[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            ProfileMetadata(
                name = s("name"),
                displayName = s("display_name"),
                about = s("about"),
                picture = s("picture"),
                nip05 = s("nip05"),
                lud16 = s("lud16"),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Latest kind **0** for [pubkeyHex] by [NostrEvent.createdAt].
     */
    suspend fun fetchLatestFromNetwork(
        relayPool: RelayPool,
        pubkeyHex: String,
        timeoutMs: Long = 8000
    ): ProfileMetadata? = coroutineScope {
        val subId = "erv-kind0-${System.currentTimeMillis()}"
        val events = mutableListOf<NostrEvent>()
        val job = launch {
            relayPool.events.collect { (id, ev) ->
                if (id == subId && ev.kind == 0 && ev.pubkey == pubkeyHex) events.add(ev)
            }
        }
        delay(50)
        relayPool.subscribe(
            subId,
            NostrFilter(
                kinds = listOf(0),
                authors = listOf(pubkeyHex),
                limit = 20
            )
        )
        delay(timeoutMs)
        job.cancel()
        relayPool.unsubscribe(subId)
        val latest = events.maxByOrNull { it.createdAt } ?: return@coroutineScope null
        parseFromContent(latest.content)
    }

    suspend fun publish(
        relayPool: RelayPool,
        signer: EventSigner,
        meta: ProfileMetadata,
        relayUrls: Collection<String>
    ): Boolean {
        val unsigned = UnsignedEvent(
            pubkey = signer.publicKey,
            createdAt = System.currentTimeMillis() / 1000,
            kind = 0,
            tags = emptyList(),
            content = meta.toKind0ContentJson()
        )
        val signed = signer.sign(unsigned)
        return relayPool.publishToRelayUrls(signed, relayUrls)
    }
}
