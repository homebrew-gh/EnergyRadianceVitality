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
  const available = ready?.available === true;

  return (
    <section className="card p-5 space-y-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <SectionHeader>Media Backup</SectionHeader>
          <p className="text-sm text-muted mt-1">
            ERV checks whether your configured relay also exposes Blossom media
            storage for image backup.
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
          available
            ? "border-[var(--erv-success)]/40 bg-[var(--erv-success)]/10"
            : "border-[var(--erv-outline)]/50 bg-[var(--erv-surface-muted)]",
        ].join(" ")}
      >
        <p
          className="font-semibold"
          style={{
            color: available ? "var(--erv-success)" : "var(--erv-on-surface)",
          }}
        >
          {state.kind === "loading"
            ? "Checking Blossom…"
            : available
              ? "Blossom Media Storage Available"
              : "Relay Sync Only"}
        </p>
        <p className="text-sm text-muted mt-1">
          {state.kind === "loading"
            ? "Deriving the media endpoint from your relay URL."
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

      <p className="text-xs text-muted">
        This is a detection-only first step. Image upload, encrypted media
        manifests, and gallery restore will build on this status.
      </p>
    </section>
  );
}
