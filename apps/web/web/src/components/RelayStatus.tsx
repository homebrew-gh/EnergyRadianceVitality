import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import clsx from "clsx";
import { api, relayHost, type OutboxStatus } from "../lib/api";
import { useAuth } from "../lib/auth";

const POLL_MS = 60_000;

type Conn = { connected: boolean; message?: string | null };

type Tone = "ok" | "warn" | "error" | "idle";

type RelayStatusValue = {
  tone: Tone;
  label: string;
  host: string;
  relayUrl: string | undefined;
  message: string | null;
  failed: number;
  checking: boolean;
  retrying: boolean;
  refresh: () => void;
  retry: () => void;
};

const RelayStatusContext = createContext<RelayStatusValue | null>(null);

function dotColor(tone: Tone): string {
  switch (tone) {
    case "ok":
      return "var(--erv-success)";
    case "warn":
      return "var(--erv-secondary)";
    case "error":
      return "var(--erv-error)";
    default:
      return "var(--erv-outline)";
  }
}

/** Polls the relay connection and publish queue once for the whole app. */
export function RelayStatusProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const [conn, setConn] = useState<Conn | null>(null);
  const [outbox, setOutbox] = useState<OutboxStatus | null>(null);
  const [checking, setChecking] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const timer = useRef<number | null>(null);

  const poll = useCallback(async () => {
    setChecking(true);
    try {
      const [c, o] = await Promise.allSettled([api.relayConnection(), api.outboxStatus()]);
      if (c.status === "fulfilled") setConn(c.value);
      else setConn({ connected: false, message: "status check failed" });
      if (o.status === "fulfilled") setOutbox(o.value);
    } finally {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    void poll();
    timer.current = window.setInterval(() => void poll(), POLL_MS);
    return () => {
      if (timer.current) window.clearInterval(timer.current);
    };
  }, [poll]);

  const retry = useCallback(async () => {
    setRetrying(true);
    try {
      const next = await api.outboxRetry();
      setOutbox(next);
      await poll();
    } finally {
      setRetrying(false);
    }
  }, [poll]);

  const pending = outbox?.pending ?? 0;
  const failed = outbox?.failed ?? 0;

  let tone: Tone = "idle";
  let label = "Checking relay…";
  if (conn) {
    if (!conn.connected) {
      tone = "error";
      label = "Relay unreachable";
    } else if (failed > 0) {
      tone = "error";
      label = `${failed} failed to publish`;
    } else if (pending > 0) {
      tone = "warn";
      label = `Publishing ${pending}…`;
    } else {
      tone = "ok";
      label = "Synced to relay";
    }
  }

  const relayUrl = status?.relay_url ?? status?.relay_urls?.[0] ?? undefined;

  const value: RelayStatusValue = {
    tone,
    label,
    host: relayHost(relayUrl),
    relayUrl,
    message: tone === "error" ? conn?.message ?? null : null,
    failed,
    checking,
    retrying,
    refresh: () => void poll(),
    retry: () => void retry(),
  };

  return <RelayStatusContext.Provider value={value}>{children}</RelayStatusContext.Provider>;
}

function useRelayStatus(): RelayStatusValue {
  const value = useContext(RelayStatusContext);
  if (!value) throw new Error("useRelayStatus must be used inside RelayStatusProvider");
  return value;
}

function Dot({ tone, onDark = false }: { tone: Tone; onDark?: boolean }) {
  return (
    <span
      aria-hidden
      className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
      style={{
        backgroundColor: dotColor(tone),
        // On the red sidebar a white ring keeps a red (error) dot visible.
        boxShadow: onDark
          ? "0 0 0 2px rgba(255, 255, 255, 0.9)"
          : `0 0 0 3px color-mix(in srgb, ${dotColor(tone)} 25%, transparent)`,
      }}
    />
  );
}

/** A small dot and label for the sidebar. Click it to check the relay again. */
export function RelaySidebarStatus({ collapsed }: { collapsed: boolean }) {
  const { tone, label, host, checking, refresh } = useRelayStatus();
  const title = `${label} · ${host}. Click to check again.`;
  return (
    <button
      type="button"
      className={clsx(
        "flex w-full items-center gap-3 rounded-xl py-2 text-left text-xs text-white/82 transition hover:bg-white/10 hover:text-white disabled:opacity-60",
        collapsed ? "justify-center px-2" : "px-3",
      )}
      onClick={refresh}
      disabled={checking}
      title={title}
      aria-label={title}
    >
      <span className="flex w-5 shrink-0 justify-center">
        <Dot tone={tone} onDark />
      </span>
      {collapsed ? null : <span className="truncate">{checking && tone === "idle" ? "Checking relay…" : label}</span>}
    </button>
  );
}

/** A bar across the page, shown only when the relay is down or a publish failed. */
export function RelayStatusBanner() {
  const { tone, label, host, relayUrl, message, failed, checking, retrying, refresh, retry } = useRelayStatus();
  if (tone !== "error") return null;

  return (
    <div className="border-b border-[var(--erv-error)]/30 bg-[var(--erv-surface)]" role="alert">
      <div className="flex w-full items-center gap-2 px-4 py-2 text-xs md:px-6">
        <Dot tone={tone} />
        <span className="font-medium" style={{ color: dotColor(tone) }}>
          {label}
        </span>
        <span className="text-muted">·</span>
        <span className="truncate font-mono text-muted" title={relayUrl}>
          {host}
        </span>
        {message ? (
          <span className="hidden truncate text-error sm:inline" title={message}>
            · {message}
          </span>
        ) : null}

        <div className="ml-auto flex items-center gap-2">
          {failed > 0 ? (
            <button
              type="button"
              className="btn-ghost !px-2 !py-1 text-xs"
              onClick={retry}
              disabled={retrying}
            >
              {retrying ? "Retrying…" : "Retry"}
            </button>
          ) : null}
          <button
            type="button"
            className="text-muted hover:text-heading disabled:opacity-50"
            onClick={refresh}
            disabled={checking}
            aria-label="Refresh relay status"
            title="Refresh relay status"
          >
            {checking ? "…" : "↻"}
          </button>
        </div>
      </div>
    </div>
  );
}
