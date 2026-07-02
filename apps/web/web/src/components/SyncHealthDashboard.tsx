import { useCallback, useEffect, useState } from "react";
import { SectionHeader } from "./FieldLabel";
import { api, relayHost, type OutboxStatus } from "../lib/api";
import { useAuth } from "../lib/auth";
import { hasRelayConfigured } from "../lib/relayUrl";
import { useTraining } from "../lib/trainingData";
import { useTrainingHistory } from "../lib/trainingHistoryData";

type RelayConnectionState = {
  connected: boolean;
  message?: string | null;
};

type HealthTone = "ok" | "warn" | "error" | "idle";

function toneClasses(tone: HealthTone): string {
  switch (tone) {
    case "ok":
      return "border-green-200 bg-green-50 text-green-800";
    case "warn":
      return "border-amber-200 bg-amber-50 text-amber-800";
    case "error":
      return "border-red-200 bg-red-50 text-red-800";
    default:
      return "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] text-muted";
  }
}

function formatLoadedAt(value: number | null): string {
  if (value == null) return "Not loaded yet";
  return new Date(value).toLocaleString();
}

export function SyncHealthDashboard() {
  const { status, refresh } = useAuth();
  const training = useTraining();
  const history = useTrainingHistory();
  const [conn, setConn] = useState<RelayConnectionState | null>(null);
  const [outbox, setOutbox] = useState<OutboxStatus | null>(null);
  const [checking, setChecking] = useState(false);
  const [retrying, setRetrying] = useState(false);

  const relay =
    status?.relay_url?.trim() ||
    status?.relay_urls?.[0]?.trim() ||
    status?.detected_relay_url?.trim() ||
    null;
  const relayConfigured = hasRelayConfigured(status);

  const poll = useCallback(async () => {
    setChecking(true);
    try {
      const [connectionResult, outboxResult] = await Promise.allSettled([
        api.relayConnection(),
        api.outboxStatus(),
      ]);
      if (connectionResult.status === "fulfilled") {
        setConn(connectionResult.value);
      } else {
        setConn({ connected: false, message: "Could not check relay connection." });
      }
      if (outboxResult.status === "fulfilled") {
        setOutbox(outboxResult.value);
      }
    } finally {
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    void poll();
  }, [poll]);

  const retryFailed = async () => {
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
  const relayTone: HealthTone = !relayConfigured
    ? "error"
    : conn == null
      ? "idle"
      : conn.connected
        ? "ok"
        : "error";
  const outboxTone: HealthTone = failed > 0 ? "error" : pending > 0 ? "warn" : outbox ? "ok" : "idle";
  const libraryTone: HealthTone = training.error ? "error" : training.loading ? "idle" : "ok";
  const historyTone: HealthTone = history.error
    ? "error"
    : history.relayFetchTruncated || history.decryptErrors.length > 0 || history.emptyDayLogs.length > 0
      ? "warn"
      : history.loading
        ? "idle"
        : "ok";
  const identityTone: HealthTone = status?.npub && relayConfigured ? "ok" : "warn";

  return (
    <section className="card overflow-hidden">
      <div className="border-b border-[var(--erv-outline-variant)] bg-[var(--erv-surface-variant)]/35 p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <span className="sun-chip">Relay Trust</span>
            <h3 className="mt-2 text-xl font-semibold text-heading">Sync Health Dashboard</h3>
            <p className="mt-1 text-sm text-muted">
              Plain-English checks for account identity, relay reachability, publish queue, and
              the data web last fetched from your relay.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="btn-ghost text-sm"
              onClick={() => void refresh()}
            >
              Refresh Account
            </button>
            <button
              type="button"
              className="btn-primary text-sm"
              onClick={() => void poll()}
              disabled={checking}
            >
              {checking ? "Checking..." : "Check Relay"}
            </button>
          </div>
        </div>
      </div>

      <div className="space-y-4 p-5">
        <div className="grid gap-3 md:grid-cols-2">
          <HealthCard
            tone={identityTone}
            title="Account Pairing"
            value={status?.npub ? "Unlocked" : "Missing Public Key"}
            detail={
              status?.npub
                ? `npub ${status.npub.slice(0, 18)}... must match Android for decrypt-to-self sync.`
                : "Unlock or set up this web companion before syncing."
            }
          />
          <HealthCard
            tone={relayTone}
            title="Relay Connection"
            value={
              !relayConfigured
                ? "No Relay Configured"
                : conn == null
                  ? "Checking"
                  : conn.connected
                    ? "Reachable"
                    : "Unreachable"
            }
            detail={conn?.message || relayHost(relay)}
          />
          <HealthCard
            tone={outboxTone}
            title="Publish Queue"
            value={
              failed > 0
                ? `${failed} Failed`
                : pending > 0
                  ? `${pending} Pending`
                  : outbox
                    ? "Clear"
                    : "Checking"
            }
            detail={
              failed > 0
                ? outbox?.failed_items[0]?.error ?? "A publish failed. Retry after checking relay."
                : pending > 0
                  ? "Web has changes waiting to reach the relay."
                  : "No queued publish work."
            }
          />
          <HealthCard
            tone={libraryTone}
            title="Library Fetch"
            value={training.loading ? "Loading" : training.error ? "Needs Attention" : "Loaded"}
            detail={
              training.error ??
              `${training.workouts.length} workouts, ${training.routines.length} weight routines, ${training.stretchRoutines.length} stretch routines, ${training.cardioRoutines.length} cardio routines. Last loaded ${formatLoadedAt(training.lastLoadedAt)}.`
            }
          />
          <HealthCard
            tone={historyTone}
            title="Training History Fetch"
            value={history.loading ? "Loading" : history.error ? "Needs Attention" : "Loaded"}
            detail={
              history.error ??
              `${history.weightLogs.length} strength day logs and ${history.cardioLogs.length} cardio day logs parsed. Last loaded ${formatLoadedAt(history.lastLoadedAt)}.`
            }
          />
          <HealthCard
            tone={history.relayMeta?.relay_fetch_possibly_truncated ? "warn" : history.relayMeta ? "ok" : "idle"}
            title="Relay Event Window"
            value={
              history.relayMeta
                ? `${history.relayMeta.relay_events_fetched}/${history.relayMeta.relay_fetch_limit} Events`
                : "No Fetch Metadata"
            }
            detail={
              history.relayMeta?.relay_fetch_possibly_truncated
                ? "Relay hit the fetch limit; older day logs may be hidden."
                : history.relayMeta
                  ? `Queried ${history.relayMeta.relay_urls_queried.map(relayHost).join(", ")}.`
                  : "Reload history to see relay fetch coverage."
            }
          />
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => void training.reload(true)}
            disabled={training.loading}
          >
            {training.loading ? "Reloading Library..." : "Reload Library"}
          </button>
          <button
            type="button"
            className="btn-ghost text-sm"
            onClick={() => void history.reload(true)}
            disabled={history.loading}
          >
            {history.loading ? "Reloading History..." : "Reload History"}
          </button>
          {failed > 0 ? (
            <button
              type="button"
              className="btn-primary text-sm"
              onClick={() => void retryFailed()}
              disabled={retrying}
            >
              {retrying ? "Retrying..." : "Retry Failed Publishes"}
            </button>
          ) : null}
        </div>

        {(history.decryptErrors.length > 0 || history.emptyDayLogs.length > 0) ? (
          <div className="rounded-card border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
            <SectionHeader className="text-xs font-semibold text-amber-900">
              History warnings
            </SectionHeader>
            {history.decryptErrors.length > 0 ? (
              <p className="mt-1">
                {history.decryptErrors.length} day log
                {history.decryptErrors.length === 1 ? "" : "s"} could not be decrypted.
              </p>
            ) : null}
            {history.emptyDayLogs.length > 0 ? (
              <p className="mt-1">
                {history.emptyDayLogs.length} day log
                {history.emptyDayLogs.length === 1 ? "" : "s"} decrypted but contained no readable sessions.
              </p>
            ) : null}
          </div>
        ) : null}

        <p className="text-xs text-muted">
          Web publishes workout libraries and routines. Android remains the source of live workout
          logs; after logging on Android, sync there first, then reload history here.
        </p>
      </div>
    </section>
  );
}

function HealthCard({
  tone,
  title,
  value,
  detail,
}: {
  tone: HealthTone;
  title: string;
  value: string;
  detail: string;
}) {
  return (
    <div className={`rounded-card border p-4 ${toneClasses(tone)}`}>
      <p className="text-xs font-semibold opacity-80">{title}</p>
      <p className="mt-1 text-lg font-bold">{value}</p>
      <p className="mt-1 text-xs opacity-85">{detail}</p>
    </div>
  );
}
