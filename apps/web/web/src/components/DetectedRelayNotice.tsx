import type { AuthStatus } from "../lib/api";
import { relayPrefillFromStatus } from "../lib/relayUrl";

export function DetectedRelayNotice({ status }: { status: AuthStatus }) {
  const prefill = relayPrefillFromStatus(status);
  if (!prefill) return null;
  const label = status.detected_relay_label?.trim() || "local relay";

  return (
    <div
      className="rounded-lg border border-[var(--erv-outline)]/30 bg-[var(--erv-primary-container)]/40 p-3 text-sm space-y-1"
      role="status"
    >
      <p className="text-[var(--erv-on-surface)]">
        Found <span className="font-semibold">{label}</span> on this StartOS
        server. The relay URL below was filled in automatically — change it if
        your Android app uses a different relay.
      </p>
      <p className="text-muted text-xs font-mono break-all">{prefill}</p>
    </div>
  );
}

export function hasDetectedRelay(status: AuthStatus | null | undefined): boolean {
  return Boolean(status && relayPrefillFromStatus(status));
}
