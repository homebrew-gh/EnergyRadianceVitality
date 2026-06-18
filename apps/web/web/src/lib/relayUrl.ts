import type { AuthStatus } from "./api";

export const RELAY_URL_POLICY =
  "Use wss:// for remote relays. On StartOS, ERV auto-detects Nostr RS Relay or Haven when installed (ws://…startos).";

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
