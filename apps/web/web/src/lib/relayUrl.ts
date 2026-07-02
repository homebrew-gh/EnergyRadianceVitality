import type { AuthStatus, DetectedRelayOption } from "./api";

export type { DetectedRelayOption };

export const RELAY_URL_POLICY =
  "Use wss:// for remote relays. On StartOS, pick a detected local relay or enter ws://<package>.startos for an explicit internal relay.";

export function relayConnectUrl(relay: DetectedRelayOption): string {
  return relay.suggested?.trim() || relay.internal;
}

export function detectedRelaysFromStatus(status: AuthStatus | null): DetectedRelayOption[] {
  return status?.detected_relays ?? [];
}

export function isAllowedRelayUrl(url: string): boolean {
  const trimmed = url.trim();
  if (trimmed.startsWith("wss://")) return true;
  if (!trimmed.startsWith("ws://")) return false;
  try {
    const parsed = new URL(trimmed);
    const host = parsed.hostname;
    if (host.endsWith(".startos")) return true;
    return host === "127.0.0.1" || host === "localhost" || host === "::1";
  } catch {
    return false;
  }
}

export function relayPrefillFromStatus(status: AuthStatus | null): string | null {
  if (!status) return null;
  return status.relay_prefill_url ?? status.suggested_relay_url ?? null;
}

export function hasRelayConfigured(status: AuthStatus | null): boolean {
  if (!status) return false;
  if ((status.relay_urls?.length ?? 0) > 0) return true;
  if (status.relay_url?.trim()) return true;
  if (status.detected_relay_url?.trim()) return true;
  return false;
}
