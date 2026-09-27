package com.erv.app.nostr

/**
 * Pure relay-role routing for pool connection and publish targets.
 * [KeyManager] delegates here so unit tests can lock privacy rules without Android storage.
 */
internal object RelayPublishTargets {

    fun activeRelayUrls(dataRelays: List<String>, socialRelays: List<String>): List<String> =
        (dataRelays + socialRelays).distinct()

    fun relayUrlsForKind30078Publish(dataRelays: List<String>, socialRelays: List<String>): List<String> {
        if (dataRelays.isNotEmpty()) return dataRelays
        return activeRelayUrls(dataRelays, socialRelays)
    }

    fun relayUrlsForKind1Publish(socialRelays: List<String>): List<String> = socialRelays

    fun shouldAttemptRelayNetworkImport(poolUrls: List<String>): Boolean = poolUrls.isNotEmpty()
}
