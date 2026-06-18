export type AuthStatus = {
  has_state: boolean;
  unlocked: boolean;
  npub?: string | null;
  relay_url?: string | null;
  relay_urls?: string[];
  detected_relay_url?: string | null;
  detected_relay_label?: string | null;
  suggested_relay_url?: string | null;
  relay_prefill_url?: string | null;
};

export type AppDataRecord = {
  event_id: string;
  d_tag?: string | null;
  ciphertext: string;
  plaintext?: string | null;
  decrypt_error?: string | null;
};

export type OutboxFailedItem = {
  id: number;
  label: string;
  error: string;
};

export type OutboxStatus = {
  pending: number;
  failed: number;
  failed_items: OutboxFailedItem[];
};

export type SetupBody = {
  nsec: string;
  passphrase: string;
  relay_url?: string;
};

export type UnlockBody = {
  passphrase: string;
};

export type WipeBody = {
  passphrase: string;
  confirmation: string;
};

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

async function request<T>(
  path: string,
  init?: RequestInit & { json?: unknown },
): Promise<T> {
  const headers = new Headers(init?.headers);
  let body: BodyInit | undefined = init?.body ?? undefined;
  if (init?.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.json);
  }
  headers.set("Accept", "application/json");
  const res = await fetch(path, {
    ...init,
    headers,
    body,
    credentials: "same-origin",
  });
  const text = await res.text();
  let parsed: unknown = undefined;
  if (text.length > 0) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = { error: text };
    }
  }
  if (!res.ok) {
    const msg =
      (parsed as { error?: string } | undefined)?.error ??
      `${res.status} ${res.statusText}`;
    if (res.status === 401 && unauthorizedHandler) unauthorizedHandler();
    throw new ApiError(res.status, msg);
  }
  return parsed as T;
}

export const api = {
  health: () => request<{ ok: boolean }>("/api/health"),
  authStatus: () => request<AuthStatus>("/api/auth/status"),
  authSetup: (body: SetupBody) =>
    request<AuthStatus>("/api/auth/setup", { method: "POST", json: body }),
  authUnlock: (body: UnlockBody) =>
    request<AuthStatus>("/api/auth/unlock", { method: "POST", json: body }),
  authLock: () =>
    request<{ ok: boolean }>("/api/auth/lock", { method: "POST" }),
  authWipe: (body: WipeBody) =>
    request<{ ok: boolean }>("/api/auth/wipe", { method: "POST", json: body }),
  listAppData: () => request<AppDataRecord[]>("/api/nostr/app-data"),
  publishAppData: (body: { d_tag: string; plaintext: string }) =>
    request<{ event_id: string }>("/api/nostr/app-data", {
      method: "POST",
      json: body,
    }),
  relayConnection: () =>
    request<{ connected: boolean; message?: string | null }>(
      "/api/nostr/connection",
    ),
  outboxStatus: () => request<OutboxStatus>("/api/nostr/outbox"),
  outboxRetry: () =>
    request<OutboxStatus>("/api/nostr/outbox/retry", { method: "POST" }),
  outboxClear: () =>
    request<OutboxStatus>("/api/nostr/outbox/clear", { method: "POST" }),
};

export function relayHost(url: string | null | undefined): string {
  if (!url) return "no relay";
  try {
    return new URL(url).host || url;
  } catch {
    return url;
  }
}
