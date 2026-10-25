package com.erv.app.nostr

import com.erv.app.workouts.WORKOUTS_LIBRARY_D_TAG
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Why a relay refused a kind-30078 publish. Relays prefix `OK false` reasons with NIP-01
 * machine-readable codes (`rate-limited:`, `auth-required:`, `restricted:`, …).
 */
enum class RelayRejectionKind {
    /** Relay is out of event budget for this client. Every queued item would fail the same way. */
    RATE_LIMITED,

    /** Relay wants NIP-42 auth or refused it. Relay-wide until settings change. */
    AUTH,

    /** Relay refuses this pubkey (not whitelisted, banned, …). Relay-wide until settings change. */
    RESTRICTED,

    /** No data relay socket is open. */
    OFFLINE,

    /** This one event was refused (`invalid:`, `blocked:`, `pow:`). */
    INVALID,

    /** Timeout, relay error, or unknown reason. */
    TRANSIENT,
}

internal fun classifyRelayRejection(message: String): RelayRejectionKind {
    val m = message.trim().lowercase()
    return when {
        m.startsWith("rate-limited") -> RelayRejectionKind.RATE_LIMITED
        m.startsWith("auth-required") || m.contains("requires authentication") -> RelayRejectionKind.AUTH
        m.startsWith("restricted") -> RelayRejectionKind.RESTRICTED
        m.startsWith("invalid") || m.startsWith("blocked") || m.startsWith("pow") -> RelayRejectionKind.INVALID
        else -> RelayRejectionKind.TRANSIENT
    }
}

/**
 * One kind for a publish that no relay accepted. Relay-wide causes win over per-event ones so the
 * outbox pauses instead of burning a retry on every queued item.
 */
internal fun aggregateRejectionKind(results: List<ClientPublishResult>): RelayRejectionKind {
    val rejected = results.mapNotNull { (it as? ClientPublishResult.Rejected)?.message }
        .map(::classifyRelayRejection)
    return when {
        RelayRejectionKind.RATE_LIMITED in rejected -> RelayRejectionKind.RATE_LIMITED
        RelayRejectionKind.AUTH in rejected -> RelayRejectionKind.AUTH
        RelayRejectionKind.RESTRICTED in rejected -> RelayRejectionKind.RESTRICTED
        RelayRejectionKind.INVALID in rejected -> RelayRejectionKind.INVALID
        results.isNotEmpty() && results.all {
            it is ClientPublishResult.NoSocket || it is ClientPublishResult.SendFailed
        } -> RelayRejectionKind.OFFLINE
        else -> RelayRejectionKind.TRANSIENT
    }
}

/** Queue-wide pause after the relay rate-limits: 1, 5, 15, 30, then 60 minutes. */
internal fun rateLimitCooldownMs(strikes: Int): Long {
    val minutes = when (strikes.coerceAtLeast(1)) {
        1 -> 1L
        2 -> 5L
        3 -> 15L
        4 -> 30L
        else -> 60L
    }
    return minutes * 60_000L
}

internal const val RELAY_REFUSED_PAUSE_MS = 15L * 60_000L
internal const val RELAY_OFFLINE_PAUSE_MS = 30_000L

private const val RECENT_DAY_LOG_DAYS = 14L
private val DATE_SEGMENT = Regex("""(?:^|/)(\d{4}-\d{2}-\d{2})(?:/|$)""")

internal fun dTagEpochDay(dTag: String): Long? {
    val date = DATE_SEGMENT.find(dTag)?.groupValues?.get(1) ?: return null
    return try {
        LocalDate.parse(date).toEpochDay()
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Send priority when the relay budget is tight: libraries first (what the other device needs to
 * work at all), the workout library index after its shards (readers drop listed ids whose shard is
 * missing), then the last two weeks of day logs, then older history.
 */
internal fun outboxSendRank(dTag: String, todayEpochDay: Long): Int {
    val day = dTagEpochDay(dTag)
    return when {
        day != null && todayEpochDay - day <= RECENT_DAY_LOG_DAYS -> 2
        day != null -> 3
        dTag == WORKOUTS_LIBRARY_D_TAG -> 1
        else -> 0
    }
}

/** Rank, then newest day first within day logs, then enqueue order. */
internal fun <T> outboxSendComparator(
    todayEpochDay: Long,
    dTagOf: (T) -> String,
    createdAtOf: (T) -> Long,
): Comparator<T> =
    compareBy<T>({ outboxSendRank(dTagOf(it), todayEpochDay) })
        .thenByDescending { dTagEpochDay(dTagOf(it)) ?: Long.MIN_VALUE }
        .thenBy { createdAtOf(it) }
