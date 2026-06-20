import { FieldLabel } from "./FieldLabel";
import { useState } from "react";
import { ApiError } from "../lib/api";
import { useAuth } from "../lib/auth";
import { SecretInput } from "./SecretInput";

type RemoveAccountFormProps = {
  onRemoved: () => void;
  compact?: boolean;
};

export function RemoveAccountForm({ onRemoved, compact }: RemoveAccountFormProps) {
  const { wipe } = useAuth();
  const [passphrase, setPassphrase] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await wipe({ passphrase, confirmation });
      onRemoved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not remove account.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className={compact ? "space-y-3" : "space-y-4"} onSubmit={onSubmit}>
      {!compact ? (
        <p className="text-sm text-muted">
          Removes the encrypted nsec from this StartOS server so you can set up a
          different key. Your Nostr relay data is unchanged.
        </p>
      ) : null}
      <div>
        <label className="label" htmlFor="remove-pass">
          <FieldLabel>Passphrase</FieldLabel>
        </label>
        <SecretInput
          id="remove-pass"
          autoComplete="current-password"
          value={passphrase}
          onChange={(e) => setPassphrase(e.target.value)}
        />
      </div>
      <div>
        <label className="label" htmlFor="remove-confirm">
          <FieldLabel>Type DELETE to confirm</FieldLabel>
        </label>
        <input
          id="remove-confirm"
          className="input"
          autoComplete="off"
          value={confirmation}
          onChange={(e) => setConfirmation(e.target.value)}
        />
      </div>
      {error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}
      <button
        type="submit"
        className="btn-ghost border-[var(--erv-error)] text-[var(--erv-error)] w-full sm:w-auto"
        disabled={submitting || confirmation !== "DELETE" || !passphrase}
      >
        {submitting ? "Removing…" : "Log out and remove key"}
      </button>
    </form>
  );
}
