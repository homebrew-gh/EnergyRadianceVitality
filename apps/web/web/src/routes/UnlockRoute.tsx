import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { PassphraseFields } from "../components/PassphraseFields";
import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AuthCard } from "../components/AuthCard";
import { RemoveAccountForm } from "../components/RemoveAccountForm";
import { SecretInput } from "../components/SecretInput";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { MIN_PASSPHRASE_LENGTH } from "../lib/passphrase";

export function UnlockRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const [passphrase, setPassphrase] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showLogout, setShowLogout] = useState(false);
  const [showRecover, setShowRecover] = useState(false);
  const [recoverNsec, setRecoverNsec] = useState("");
  const [recoverPass, setRecoverPass] = useState("");
  const [recoverConfirm, setRecoverConfirm] = useState("");

  if (loading) return null;
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (status.unlocked) return <Navigate to="/app" replace />;

  const onSubmit = async (e: React.FormEvent) => {
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

  const onRecover = async (nextPassphrase: string | undefined) => {
    setError(null);
    if (!recoverNsec.startsWith("nsec1")) {
      setError("Secret key must be an nsec1… string (same key as your Android app).");
      return;
    }
    if (nextPassphrase !== undefined) {
      if (nextPassphrase.length < MIN_PASSPHRASE_LENGTH) {
        setError(`Passphrase must be at least ${MIN_PASSPHRASE_LENGTH} characters.`);
        return;
      }
      if (nextPassphrase !== recoverConfirm) {
        setError("Passphrases do not match.");
        return;
      }
    }
    setSubmitting(true);
    try {
      await api.authRecover({ nsec: recoverNsec, passphrase: nextPassphrase });
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
        <form className="space-y-4" onSubmit={onSubmit}>
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
          {error && !showRecover ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : null}
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting ? "Unlocking…" : "Unlock"}
          </button>
        </form>
      </AuthCard>

      <div className="w-full max-w-md card p-5 space-y-3">
        {showRecover ? (
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              void onRecover(recoverPass);
            }}
          >
            <SectionHeader>I forgot — paste nsec</SectionHeader>
            <p className="text-sm text-muted">
              Paste the same nsec as this companion. You can set a new passphrase
              or continue without one.
            </p>
            <div>
              <label className="label" htmlFor="recover-nsec">
                <FieldLabel>Nostr secret key (nsec)</FieldLabel>
              </label>
              <SecretInput
                id="recover-nsec"
                autoComplete="off"
                placeholder="nsec1…"
                value={recoverNsec}
                onChange={(e) => setRecoverNsec(e.target.value.trim())}
              />
            </div>
            <PassphraseFields
              idPrefix="recover"
              passphrase={recoverPass}
              confirm={recoverConfirm}
              onPassphrase={setRecoverPass}
              onConfirm={setRecoverConfirm}
            />
            {error && showRecover ? (
              <p className="text-sm text-error" role="alert">
                {error}
              </p>
            ) : null}
            <button type="submit" className="btn-primary w-full" disabled={submitting}>
              {submitting ? "Recovering…" : "Recover and set passphrase"}
            </button>
            <button
              type="button"
              className="btn-ghost w-full"
              disabled={submitting}
              onClick={() => void onRecover(undefined)}
            >
              Recover without passphrase
            </button>
            <button
              type="button"
              className="btn-ghost text-sm w-full"
              onClick={() => {
                setShowRecover(false);
                setError(null);
              }}
            >
              Cancel
            </button>
          </form>
        ) : (
          <div className="space-y-2 text-center">
            <p className="text-sm text-muted">Forgot your passphrase?</p>
            <button
              type="button"
              className="btn-ghost text-sm"
              onClick={() => {
                setShowRecover(true);
                setError(null);
              }}
            >
              I forgot — paste nsec
            </button>
          </div>
        )}
      </div>

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
