package com.erv.app.nostr

import java.net.URI

object RelayUrls {
    private const val DEFAULT_SCHEME = "wss://"

    fun normalize(input: String, addDefaultScheme: Boolean = false): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith("wss://", ignoreCase = true) || trimmed.startsWith("ws://", ignoreCase = true) -> trimmed
            addDefaultScheme -> "$DEFAULT_SCHEME$trimmed"
            else -> return null
        }

        return try {
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "wss" && scheme != "ws") return null
            val host = uri.host?.lowercase() ?: return null
            val authority = buildString {
                append(host)
                if (uri.port != -1) append(":").append(uri.port)
            }
            val path = uri.rawPath.orEmpty().trimEnd('/')
            val normalizedPath = if (path.isEmpty()) "" else path
            val query = uri.rawQuery?.let { "?$it" }.orEmpty()
            "$scheme://$authority$normalizedPath$query"
        } catch (_: Exception) {
            null
        }
    }
}
