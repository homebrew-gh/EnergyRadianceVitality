package com.erv.app.nostr

import com.erv.app.nostr.Nip96Uploader.normalizeMediaServerOrigin

/**
 * Blossom often lives on the same host as a personal outbox relay (Haven).
 * Users can still override this with an explicit Blossom origin in Settings.
 */
object BlossomEndpoints {
    fun originFromRelayUrl(relayUrl: String): String? {
        val trimmed = relayUrl.trim()
        val rest = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> trimmed.drop(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> trimmed.drop(5)
            else -> return null
        }
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        if (authority.isEmpty()) return null
        return "https://$authority"
    }

    fun resolvePrivateBackupOrigin(
        explicitPrivateOrigin: String,
        dataRelayUrls: List<String>,
    ): String? {
        val explicit = normalizeMediaServerOrigin(explicitPrivateOrigin)
        if (explicit.isNotBlank()) return explicit
        return dataRelayUrls.firstNotNullOfOrNull(::originFromRelayUrl)
    }
}
