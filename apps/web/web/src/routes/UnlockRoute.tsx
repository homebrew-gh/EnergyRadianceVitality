import { FieldLabel } from "../components/FieldLabel";
import { useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import type { UnlockLocationState } from "../lib/sessionWatcher";
import { AuthCard } from "../components/AuthCard";
import { RemoveAccountForm } from "../components/RemoveAccountForm";
import { SecretInput } from "../components/SecretInput";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";

type UnlockMode = "passphrase" | "nsec";

export function UnlockRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const sessionExpired =
    (location.state as UnlockLocationState | null)?.reason === "session-expired";
  const [mode, setMode] = useState<UnlockMode>("passphrase");
  const [passphrase, setPassphrase] = useState("");
  const [nsec, setNsec] = useState("");
  const [newPassphrase, setNewPassphrase] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showLogout, setShowLogout] = useState(false);

  if (loading) return null;
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (status.unlocked) return <Navigate to="/app" replace />;

  const switchMode = (next: UnlockMode) => {
    setMode(next);
    setError(null);
  };

  const onPassphraseSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.authUnlock({ passphrase });
      await refresh();
      navigate("/app", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unlock failed.");
    } finally {
      setSubmitting(false);
    }
  };

  const onRecoverSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!nsec.startsWith("nsec1")) {
      setError("Secret key must be an nsec1… string (same key as this account).");
      return;
    }
    if (newPassphrase.length < 8) {
      setError("New passphrase must be at least 8 characters.");
      return;
    }
    if (newPassphrase !== confirm) {
      setError("Passphrases do not match.");
      return;
    }
    setSubmitting(true);
    try {
      await api.authRecover({ nsec, new_passphrase: newPassphrase });
      await refresh();
      navigate("/app", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Recovery failed.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-full flex flex-col items-center justify-center p-4 gap-4">
      <AuthCard
        title="Unlock ERV"
        subtitle={status.npub ? `Signed in as ${status.npub.slice(0, 16)}…` : undefined}
      >
        {sessionExpired ? (
          <p className="text-sm text-muted mb-4" role="status">
            Your session expired after a period of inactivity. Enter your passphrase to
            continue, or unlock with this account's private key.
          </p>
        ) : null}

        {mode === "passphrase" ? (
          <form className="space-y-4" onSubmit={onPassphraseSubmit}>
            <div>
              <label className="label" htmlFor="pass">
                <FieldLabel>Passphrase</FieldLabel>
              </label>
              <SecretInput
                id="pass"
                autoComplete="current-password"
                value={passphrase}
                onChange={(e) => setPassphrase(e.target.value)}
              />
            </div>
            {error ? (
              <p className="text-sm text-error" role="alert">
                {error}
              </p>
            ) : null}
            <button type="submit" className="btn-primary w-full" disabled={submitting}>
              {submitting ? "Unlocking…" : "Unlock"}
            </button>
            <button
              type="button"
              className="btn-ghost w-full text-sm"
              onClick={() => switchMode("nsec")}
            >
              Can't remember your passphrase?
            </button>
          </form>
        ) : (
          <form className="space-y-4" onSubmit={onRecoverSubmit}>
            <p className="text-sm text-muted">
              If you still have this account's nsec, you can unlock without the old
              passphrase and choose a new one. The key must match the npub on this
              server.
            </p>
            <div>
              <label className="label" htmlFor="recover-nsec">
                <FieldLabel>Nostr secret key (nsec)</FieldLabel>
              </label>
              <SecretInput
                id="recover-nsec"
                autoComplete="off"
                placeholder="nsec1…"
                value={nsec}
                onChange={(e) => setNsec(e.target.value.trim())}
              />
            </div>
            <div>
              <label className="label" htmlFor="recover-pass">
                <FieldLabel>New passphrase</FieldLabel>
              </label>
              <SecretInput
                id="recover-pass"
                autoComplete="new-password"
                value={newPassphrase}
                onChange={(e) => setNewPassphrase(e.target.value)}
              />
            </div>
            <div>
              <label className="label" htmlFor="recover-confirm">
                <FieldLabel>Confirm new passphrase</FieldLabel>
              </label>
              <SecretInput
                id="recover-confirm"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </div>
            {error ? (
              <p className="text-sm text-error" role="alert">
                {error}
              </p>
            ) : null}
            <button type="submit" className="btn-primary w-full" disabled={submitting}>
              {submitting ? "Unlocking…" : "Unlock and set new passphrase"}
            </button>
            <button
              type="button"
              className="btn-ghost w-full text-sm"
              onClick={() => switchMode("passphrase")}
            >
              Back to passphrase
            </button>
          </form>
        )}
      </AuthCard>

      <div className="w-full max-w-md card p-5 border-[var(--erv-error)]/30">
        {showLogout ? (
          <div className="space-y-3">
            <h2 className="font-semibold text-[var(--erv-error)]">
              Log out and switch account
            </h2>
            <RemoveAccountForm
              compact
              onRemoved={() => navigate("/setup", { replace: true })}
            />
            <button
              type="button"
              className="btn-ghost text-sm w-full"
              onClick={() => setShowLogout(false)}
            >
              Cancel
            </button>
          </div>
        ) : (
          <div className="space-y-2 text-center">
            <p className="text-sm text-muted">
              Need to use a different nsec on this server?
            </p>
            <button
              type="button"
              className="btn-ghost text-sm border-[var(--erv-error)]/50 text-[var(--erv-error)]"
              onClick={() => setShowLogout(true)}
            >
              Log out and remove key
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
