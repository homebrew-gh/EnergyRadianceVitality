package com.erv.app.nostr

/**
 * Blossom often lives on the same host as a personal outbox relay (Haven).
 * Users can still override this with an explicit Blossom origin in Settings.
 *
 * Scheme follows the relay, matching the companion: `wss://` → `https://`, `ws://` → `http://`.
 * Haven on StartOS is HTTP inside the container and HTTPS on the LAN address the phone uses.
 */
object BlossomEndpoints {
    fun originFromRelayUrl(relayUrl: String): String? {
        val trimmed = relayUrl.trim()
        val (scheme, rest) = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> "https" to trimmed.drop(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "http" to trimmed.drop(5)
            else -> return null
        }
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        if (authority.isEmpty()) return null
        return "$scheme://$authority"
    }

    /**
     * Keeps an explicit `http://` origin. Public share uploads still force HTTPS via
     * [Nip96Uploader.normalizeMediaServerOrigin].
     */
    fun normalizePrivateOrigin(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> "https://${trimmed.trimStart('/')}"
        }
        return withScheme.trimEnd('/')
    }

    /** Cleartext Blossom only for a private host, and only after the user opts into LAN trust. */
    fun allowsInsecureHttpUpload(origin: String, trustSelfSignedLanTls: Boolean): Boolean {
        if (!origin.trim().startsWith("http://", ignoreCase = true)) return false
        if (!trustSelfSignedLanTls) return false
        val host = hostFromOrigin(origin) ?: return false
        return LanTls.isLanOrPrivateHost(host)
    }

    private fun hostFromOrigin(origin: String): String? {
        val authority = origin.trim()
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfter('@')
            .trim()
        if (authority.isEmpty()) return null
        val host = if (authority.startsWith("[")) {
            authority.substringAfter("[").substringBefore("]")
        } else {
            authority.substringBefore(':')
        }
        return host.takeIf { it.isNotEmpty() }
    }

    fun resolvePrivateBackupOrigin(
        explicitPrivateOrigin: String,
        dataRelayUrls: List<String>,
    ): String? {
        val explicit = normalizePrivateOrigin(explicitPrivateOrigin)
        if (explicit.isNotBlank()) return explicit
        return dataRelayUrls.firstNotNullOfOrNull(::originFromRelayUrl)
    }
}
