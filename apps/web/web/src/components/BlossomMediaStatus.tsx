import { useEffect, useState } from "react";
import { SectionHeader } from "./FieldLabel";
import { api, type BlossomStatus } from "../lib/api";

type LoadState =
  | { kind: "loading" }
  | { kind: "ready"; status: BlossomStatus }
  | { kind: "error"; message: string };

export function BlossomMediaStatus() {
  const [state, setState] = useState<LoadState>({ kind: "loading" });
  const [refreshing, setRefreshing] = useState(false);

  const load = async () => {
    setRefreshing(true);
    try {
      const status = await api.blossomStatus();
      setState({ kind: "ready", status });
    } catch (err) {
      setState({
        kind: "error",
        message: err instanceof Error ? err.message : "Blossom status check failed.",
      });
    } finally {
      setRefreshing(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const ready = state.kind === "ready" ? state.status : null;
  const verified = ready?.available === true && ready.auth_verified === true;
  const detected = ready?.available === true && ready.auth_verified !== true;

  return (
    <section className="card p-5 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <SectionHeader>Media Backup</SectionHeader>
          <p className="text-sm text-muted mt-1">
            ERV checks whether your configured relay exposes Blossom storage and
            whether upload auth works for your key. Back up photos from Android;
            this companion fetches and decrypts them in the Media tab.
          </p>
        </div>
        <button
          type="button"
          className="btn-ghost shrink-0"
          onClick={() => void load()}
          disabled={refreshing}
        >
          {refreshing ? "Checking…" : "Refresh"}
        </button>
      </div>

      <div
        className={[
          "rounded-2xl border p-4",
          verified
            ? "border-[var(--erv-success)]/40 bg-[var(--erv-success)]/10"
            : detected
              ? "border-[var(--erv-secondary)]/40 bg-[var(--erv-secondary-container)]/30"
              : "border-[var(--erv-outline)]/50 bg-[var(--erv-surface-muted)]",
        ].join(" ")}
      >
        <p
          className="font-semibold"
          style={{
            color: verified
              ? "var(--erv-success)"
              : detected
                ? "var(--erv-on-secondary-container)"
                : "var(--erv-on-surface)",
          }}
        >
          {state.kind === "loading"
            ? "Checking Blossom…"
            : verified
              ? "Ready For Media Backup"
              : detected
                ? "Endpoint Detected"
                : "Relay Sync Only"}
        </p>
        <p className="text-sm text-muted mt-1">
          {state.kind === "loading"
            ? "Probing the media endpoint derived from your relay URL."
            : state.kind === "error"
              ? state.message
              : state.status.message}
        </p>
        {ready?.origin ? (
          <p className="text-xs font-mono break-all text-muted mt-3">
            {ready.origin}
          </p>
        ) : null}
      </div>

      {verified ? (
        <p className="text-xs text-muted">
          On Android, open Body Tracker or cardio route backup to publish encrypted
          blobs and the `erv/media/library` manifest. Refresh the Media tab here
          after backup completes.
        </p>
      ) : detected ? (
        <p className="text-xs text-muted">
          The Blossom HTTP endpoint responded, but upload auth was not verified.
          Confirm this companion uses the same nsec as your phone and that Haven
          is running on the relay host.
        </p>
      ) : (
        <p className="text-xs text-muted">
          Install Haven on StartOS for local relay sync and Blossom media storage,
          or use a relay bundle that includes Blossom on the same host.
        </p>
      )}
    </section>
  );
}
