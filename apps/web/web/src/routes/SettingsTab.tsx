import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { RemoveAccountForm } from "../components/RemoveAccountForm";
import { relayHost } from "../lib/api";
import { useAuth } from "../lib/auth";

export function SettingsTab() {
  const { status, lock } = useAuth();
  const navigate = useNavigate();
  const [locking, setLocking] = useState(false);

  const relay =
    status?.relay_url?.trim() ||
    status?.relay_urls?.[0]?.trim() ||
    status?.relay_prefill_url?.trim() ||
    null;

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

  return (
    <div className="mx-auto w-full max-w-lg space-y-6">
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
            <p className="label mb-1">Public key (npub)</p>
            <p className="text-sm font-mono break-all text-[var(--erv-on-surface)]">
              {status.npub}
            </p>
          </div>
        ) : null}
        {relay ? (
          <div>
            <p className="label mb-1">Relay</p>
            <p className="text-sm font-mono break-all text-[var(--erv-on-surface)]">
              {relay}
            </p>
            <p className="text-xs text-muted mt-1">{relayHost(relay)}</p>
          </div>
        ) : (
          <p className="text-sm text-muted">No relay configured.</p>
        )}
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
