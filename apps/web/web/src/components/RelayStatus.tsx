import { useCallback, useEffect, useRef, useState } from "react";
import { api, relayHost, type OutboxStatus } from "../lib/api";
import { useAuth } from "../lib/auth";

const POLL_MS = 60_000;

type Conn = { connected: boolean; message?: string | null };

type Tone = "ok" | "warn" | "error" | "idle";

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

export function RelayStatus() {
  const { status } = useAuth();
  const [conn, setConn] = useState<Conn | null>(null);
  const [outbox, setOutbox] = useState<OutboxStatus | null>(null);
  const [checking, setChecking] = useState(false);
  const [retrying, setRetrying] = useState(false);
  const timer = useRef<number | null>(null);

  const poll = useCallback(async () => {
    setChecking(true);
    try {
      const [c, o] = await Promise.allSettled([
        api.relayConnection(),
        api.outboxStatus(),
      ]);
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

  const onRetry = async () => {
    setRetrying(true);
    try {
      const next = await api.outboxRetry();
      setOutbox(next);
      await poll();
    } finally {
      setRetrying(false);
    }
  };

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

  const host = relayHost(status?.relay_url ?? status?.relay_urls?.[0]);

  return (
    <div className="border-b border-[var(--erv-outline)]/30 bg-[var(--erv-surface)]">
      <div className="max-w-3xl mx-auto w-full px-4 py-2 flex items-center gap-2 text-xs">
        <span
          aria-hidden
          className="inline-block h-2.5 w-2.5 rounded-full shrink-0"
          style={{
            backgroundColor: dotColor(tone),
            boxShadow: `0 0 0 3px color-mix(in srgb, ${dotColor(tone)} 25%, transparent)`,
          }}
        />
        <span className="font-medium" style={{ color: dotColor(tone) }}>
          {label}
        </span>
        <span className="text-muted">·</span>
        <span className="text-muted font-mono truncate" title={status?.relay_url ?? undefined}>
          {host}
        </span>

        <div className="ml-auto flex items-center gap-2">
          {failed > 0 ? (
            <button
              type="button"
              className="btn-ghost !py-1 !px-2 text-xs"
              onClick={() => void onRetry()}
              disabled={retrying}
            >
              {retrying ? "Retrying…" : "Retry"}
            </button>
          ) : null}
          <button
            type="button"
            className="text-muted hover:text-heading disabled:opacity-50"
            onClick={() => void poll()}
            disabled={checking}
            aria-label="Refresh relay status"
            title="Refresh relay status"
          >
            {checking ? "…" : "↻"}
          </button>
        </div>
      </div>

      {tone === "error" && conn?.message ? (
        <div className="max-w-3xl mx-auto w-full px-4 pb-2 -mt-1">
          <p className="text-xs text-error truncate" title={conn.message}>
            {conn.message}
          </p>
        </div>
      ) : null}
    </div>
  );
}
