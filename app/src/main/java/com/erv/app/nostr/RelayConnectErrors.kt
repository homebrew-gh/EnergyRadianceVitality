package com.erv.app.nostr

object RelayConnectErrors {

    fun format(t: Throwable, trustSelfSignedLanTls: Boolean): String {
        val msg = t.message?.takeIf { it.isNotBlank() } ?: "Connection failed"
        if (trustSelfSignedLanTls || !looksLikeCertFailure(t)) return msg
        return "$msg. Enable “Self-signed LAN certificate” in Settings → Relays for Start9/LAN relays."
    }

    private fun looksLikeCertFailure(t: Throwable): Boolean {
        val messages = sequence {
            yield(t.message)
            yield(t.cause?.message)
        }.filterNotNull()
        return messages.any { m ->
            m.contains("CertPathValidator", ignoreCase = true) ||
                m.contains("trust anchor", ignoreCase = true) ||
                m.contains("CertificateException", ignoreCase = true)
        }
    }
}
