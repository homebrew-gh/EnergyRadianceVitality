import type { AuthStatus } from "../lib/api";
import {
  detectedRelaysFromStatus,
  relayConnectUrl,
  relayPrefillFromStatus,
} from "../lib/relayUrl";

export function DetectedRelayNotice({ status }: { status: AuthStatus }) {
  const relays = detectedRelaysFromStatus(status);
  const prefill = relayPrefillFromStatus(status);
  if (!prefill && relays.length === 0) return null;

  if (relays.length > 1) {
    return (
      <div
        className="rounded-lg border border-[var(--erv-outline)]/30 bg-[var(--erv-primary-container)]/40 p-3 text-sm space-y-1"
        role="status"
      >
        <p className="text-[var(--erv-on-surface)]">
          Found {relays.map((relay) => relay.label).join(" and ")} on this StartOS server.
          Choose one below. Your Android app must use the same relay URL.
        </p>
      </div>
    );
  }

  const label = status.detected_relay_label?.trim() || relays[0]?.label || "local relay";
  const displayUrl = relays[0] ? relayConnectUrl(relays[0]) : prefill;

  return (
    <div
      className="rounded-lg border border-[var(--erv-outline)]/30 bg-[var(--erv-primary-container)]/40 p-3 text-sm space-y-1"
      role="status"
    >
      <p className="text-[var(--erv-on-surface)]">
        Found <span className="font-semibold">{label}</span> on this StartOS server. The relay
        URL below was filled in automatically — change it if your Android app uses a different
        relay.
      </p>
      {displayUrl ? (
        <p className="text-muted text-xs font-mono break-all">{displayUrl}</p>
      ) : null}
    </div>
  );
}

export function hasDetectedRelay(status: AuthStatus | null | undefined): boolean {
  return Boolean(
    status &&
      (relayPrefillFromStatus(status) || detectedRelaysFromStatus(status).length > 0),
  );
}
