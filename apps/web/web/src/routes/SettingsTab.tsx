import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { BlossomMediaStatus } from "../components/BlossomMediaStatus";
import { FieldLabel } from "../components/FieldLabel";
import { RemoveAccountForm } from "../components/RemoveAccountForm";
import { SyncHealthDashboard } from "../components/SyncHealthDashboard";
import { LocalRelayPicker } from "../components/LocalRelayPicker";
import { ApiError, api, relayHost, type AuthStatus } from "../lib/api";
import { invalidateAppDataCache } from "../lib/appDataCache";
import { useAuth } from "../lib/auth";
import { detectedRelaysFromStatus, isAllowedRelayUrl, RELAY_URL_POLICY } from "../lib/relayUrl";
import { useWeightLoadUnit } from "../lib/weightLoadUnit";

function relayUrlsFromStatus(status: AuthStatus | null): string[] {
  const urls = status?.relay_urls?.map((url) => url.trim()).filter(Boolean) ?? [];
  if (urls.length > 0) return urls;
  const primary = status?.relay_url?.trim();
  return primary ? [primary] : [];
}

function parseRelayUrls(value: string): string[] {
  return value
    .split(/[\n,]+/)
    .map((url) => url.trim())
    .filter(Boolean);
}

export function SettingsTab() {
  const { status, lock, refresh } = useAuth();
  const navigate = useNavigate();
  const [locking, setLocking] = useState(false);
  const [weightLoadUnit, setWeightLoadUnit] = useWeightLoadUnit();
  const [relayText, setRelayText] = useState("");
  const [savingRelays, setSavingRelays] = useState(false);
  const [relayError, setRelayError] = useState<string | null>(null);
  const [relayMessage, setRelayMessage] = useState<string | null>(null);

  const relays = relayUrlsFromStatus(status);

  useEffect(() => {
    let cancelled = false;
    const fallback = relayUrlsFromStatus(status).join("\n");

    if (!status?.unlocked) {
      setRelayText(fallback);
      return;
    }

    void api
      .getRelaySettings()
      .then((settings) => {
        if (!cancelled) {
          setRelayText((settings.relay_urls.length ? settings.relay_urls : [settings.relay_url])
            .filter(Boolean)
            .join("\n"));
        }
      })
      .catch(() => {
        if (!cancelled) setRelayText(fallback);
      });

    return () => {
      cancelled = true;
    };
  }, [status]);

  const onLock = async () => {
    if (locking) return;
    setLocking(true);
    try {
      await lock();
      navigate("/unlock", { replace: true });
    } finally {
      setLocking(false);
    }
  };

  const onSaveRelays = async (event: FormEvent) => {
    event.preventDefault();
    if (savingRelays) return;

    const nextRelays = parseRelayUrls(relayText);
    setRelayError(null);
    setRelayMessage(null);

    if (nextRelays.length === 0) {
      setRelayError("Add at least one relay URL.");
      return;
    }

    const invalid = nextRelays.find((url) => !isAllowedRelayUrl(url));
    if (invalid) {
      setRelayError(`${invalid} is not allowed. ${RELAY_URL_POLICY}`);
      return;
    }

    setSavingRelays(true);
    try {
      const saved = await api.updateRelaySettings({ relay_urls: nextRelays });
      invalidateAppDataCache();
      setRelayText(saved.relay_urls.join("\n"));
      await refresh();
      setRelayMessage("Relay settings saved. Reload library or history below to fetch from the new relay.");
    } catch (err) {
      setRelayError(err instanceof ApiError ? err.message : "Could not save relay settings.");
    } finally {
      setSavingRelays(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-4xl space-y-6">
      <div>
        <h2 className="text-2xl font-bold text-heading">Settings</h2>
        <p className="text-sm text-muted mt-1">
          Account, session, and local key on this StartOS server.
        </p>
      </div>

      <section className="card p-5 space-y-3">
        <h3 className="font-semibold text-heading">Account</h3>
        {status?.npub ? (
          <div>
            <p className="label mb-1">
              <FieldLabel>Public key (npub)</FieldLabel>
            </p>
            <p className="text-sm font-mono break-all text-[var(--erv-on-surface)]">
              {status.npub}
            </p>
          </div>
        ) : null}
      </section>

      <section className="card p-5 space-y-4">
        <div>
          <h3 className="font-semibold text-heading">Relays</h3>
          <p className="text-sm text-muted mt-1">
            Reads merge events from every relay in this list. Publishes are sent to all saved
            relays; the first relay is shown as primary in the status bar.
          </p>
        </div>

        {relays.length > 0 ? (
          <div className="space-y-2">
            <p className="label">
              <FieldLabel>Current relays</FieldLabel>
            </p>
            <div className="space-y-2">
              {relays.map((relay, index) => (
                <div
                  key={`${relay}-${index}`}
                  className="rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3"
                >
                  <p className="text-sm font-mono break-all text-[var(--erv-on-surface)]">
                    {relay}
                  </p>
                  <p className="text-xs text-muted mt-1">
                    {index === 0 ? "Primary · " : ""}
                    {relayHost(relay)}
                  </p>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted">No relay configured.</p>
        )}

        <form className="space-y-3" onSubmit={onSaveRelays}>
          {status ? (
            <LocalRelayPicker
              relays={detectedRelaysFromStatus(status)}
              value={parseRelayUrls(relayText)[0] ?? ""}
              onChange={(url) => {
                const rest = parseRelayUrls(relayText).slice(1);
                setRelayText([url, ...rest].join("\n"));
                setRelayError(null);
                setRelayMessage(null);
              }}
            />
          ) : null}
          <div>
            <label className="label" htmlFor="relay-urls">
              <FieldLabel>Relay URLs</FieldLabel>
            </label>
            <textarea
              id="relay-urls"
              className="input min-h-28 font-mono text-sm"
              autoComplete="off"
              placeholder="wss://relay.example.com"
              value={relayText}
              onChange={(event) => {
                setRelayText(event.target.value);
                setRelayError(null);
                setRelayMessage(null);
              }}
            />
            <p className="text-xs text-muted mt-1">
              Enter one relay per line. {RELAY_URL_POLICY}
            </p>
          </div>

          {relayError ? (
            <p className="text-sm text-error" role="alert">
              {relayError}
            </p>
          ) : null}
          {relayMessage ? (
            <p className="text-sm text-[var(--erv-success)]" role="status">
              {relayMessage}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <button type="submit" className="btn-primary" disabled={savingRelays}>
              {savingRelays ? "Saving…" : "Save Relays"}
            </button>
            <button
              type="button"
              className="btn-ghost"
              onClick={() => {
                setRelayText(relays.join("\n"));
                setRelayError(null);
                setRelayMessage(null);
              }}
              disabled={savingRelays}
            >
              Reset
            </button>
          </div>
        </form>
      </section>

      <SyncHealthDashboard />

      <BlossomMediaStatus />

      <section className="card p-5 space-y-3">
        <h3 className="font-semibold text-heading">Units</h3>
        <p className="text-sm text-muted">
          Units when entering target loads in workout builders and viewing load hints.
          Stored weights still sync in kg.
        </p>
        <div>
          <p className="label mb-2">
            <FieldLabel>Weight training loads</FieldLabel>
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              className={weightLoadUnit === "KG" ? "btn-primary" : "btn-ghost"}
              onClick={() => setWeightLoadUnit("KG")}
            >
              Kilograms
            </button>
            <button
              type="button"
              className={weightLoadUnit === "LB" ? "btn-primary" : "btn-ghost"}
              onClick={() => setWeightLoadUnit("LB")}
            >
              Pounds
            </button>
          </div>
        </div>
      </section>

      <section className="card p-5 space-y-3">
        <h3 className="font-semibold text-heading">Session</h3>
        <p className="text-sm text-muted">
          Lock clears the unlocked session on this browser. Your encrypted key
          stays on the server — unlock again with your passphrase.
        </p>
        <button
          type="button"
          className="btn-ghost"
          onClick={() => void onLock()}
          disabled={locking}
        >
          {locking ? "Locking…" : "Lock"}
        </button>
      </section>

      <section className="card p-5 space-y-3 border-[var(--erv-error)]/40">
        <h3 className="font-semibold text-[var(--erv-error)]">Log out</h3>
        <RemoveAccountForm
          onRemoved={() => navigate("/setup", { replace: true })}
        />
      </section>
    </div>
  );
}
