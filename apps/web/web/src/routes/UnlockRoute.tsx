import { FieldLabel } from "../components/FieldLabel";
import { useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AuthCard } from "../components/AuthCard";
import { RemoveAccountForm } from "../components/RemoveAccountForm";
import { SecretInput } from "../components/SecretInput";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";

export function UnlockRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const [passphrase, setPassphrase] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showLogout, setShowLogout] = useState(false);

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
          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : null}
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting ? "Unlocking…" : "Unlock"}
          </button>
        </form>
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
