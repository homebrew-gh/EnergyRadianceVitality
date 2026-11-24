import { FieldLabel, SectionHeader } from "../components/FieldLabel";
import { PassphraseFields } from "../components/PassphraseFields";
import { useEffect, useRef, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AuthCard } from "../components/AuthCard";
import { DetectedRelayNotice } from "../components/DetectedRelayNotice";
import { LocalRelayPicker } from "../components/LocalRelayPicker";
import { SecretInput } from "../components/SecretInput";
import { ApiError, api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { MIN_PASSPHRASE_LENGTH } from "../lib/passphrase";
import {
  detectedRelaysFromStatus,
  isAllowedRelayUrl,
  relayPrefillFromStatus,
  RELAY_URL_POLICY,
} from "../lib/relayUrl";

export function SetupRoute() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const prefilled = useRef(false);

  const [nsec, setNsec] = useState("");
  const [passphrase, setPassphrase] = useState("");
  const [confirm, setConfirm] = useState("");
  const [relayUrl, setRelayUrl] = useState("wss://");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (prefilled.current) return;
    const prefill = relayPrefillFromStatus(status);
    if (prefill) {
      setRelayUrl(prefill);
      prefilled.current = true;
    }
  }, [status]);

  if (loading) return null;
  if (status?.has_state) {
    return <Navigate to={status.unlocked ? "/app" : "/unlock"} replace />;
  }

  const validateCommon = (): string | null => {
    if (!nsec.startsWith("nsec1")) {
      return "Secret key must be an nsec1… string (same key as your Android app).";
    }
    if (!isAllowedRelayUrl(relayUrl)) {
      return RELAY_URL_POLICY;
    }
    return null;
  };

  const save = async (nextPassphrase: string | undefined) => {
    setError(null);
    const commonError = validateCommon();
    if (commonError) {
      setError(commonError);
      return;
    }
    if (nextPassphrase !== undefined) {
      if (nextPassphrase.length < MIN_PASSPHRASE_LENGTH) {
        setError(`Passphrase must be at least ${MIN_PASSPHRASE_LENGTH} characters.`);
        return;
      }
      if (nextPassphrase !== confirm) {
        setError("Passphrases do not match.");
        return;
      }
    }

    setSubmitting(true);
    try {
      await api.authSetup({
        nsec,
        passphrase: nextPassphrase,
        relay_url: relayUrl,
      });
      await refresh();
      navigate("/app", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Setup failed.");
    } finally {
      setSubmitting(false);
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await save(passphrase);
  };

  return (
    <AuthCard
      title="Set up ERV"
      subtitle="Use the same nsec and relay as your Android app. A passphrase is optional — it encrypts the key on this server and is required after idle or restart."
    >
      <form className="space-y-4" onSubmit={onSubmit}>
        {status ? <DetectedRelayNotice status={status} /> : null}
        {status ? (
          <LocalRelayPicker
            relays={detectedRelaysFromStatus(status)}
            value={relayUrl}
            onChange={setRelayUrl}
          />
        ) : null}
        <div>
          <label className="label" htmlFor="nsec">
            <FieldLabel>Nostr secret key (nsec)</FieldLabel>
          </label>
          <SecretInput
            id="nsec"
            autoComplete="off"
            placeholder="nsec1…"
            value={nsec}
            onChange={(e) => setNsec(e.target.value.trim())}
          />
        </div>
        <div>
          <label className="label" htmlFor="relay">
            <FieldLabel>Relay URL</FieldLabel>
          </label>
          <input
            id="relay"
            className="input font-mono text-sm"
            type="url"
            autoComplete="off"
            placeholder="wss://relay.example.com"
            value={relayUrl}
            onChange={(e) => setRelayUrl(e.target.value.trim())}
          />
          <p className="text-xs text-muted mt-1">{RELAY_URL_POLICY}</p>
        </div>
        <div className="space-y-2">
          <SectionHeader>Passphrase (optional)</SectionHeader>
          <p className="text-xs text-muted">
            Skip to store the nsec unencrypted on this server. The Web UI then
            stays unlocked while the service is running.
          </p>
          <PassphraseFields
            idPrefix="setup"
            passphrase={passphrase}
            confirm={confirm}
            onPassphrase={setPassphrase}
            onConfirm={setConfirm}
          />
        </div>
        {error ? (
          <p className="text-sm text-error" role="alert">
            {error}
          </p>
        ) : null}
        <div className="flex flex-col gap-2">
          <button type="submit" className="btn-primary w-full" disabled={submitting}>
            {submitting ? "Saving…" : "Save and unlock"}
          </button>
          <button
            type="button"
            className="btn-ghost w-full"
            disabled={submitting}
            onClick={() => void save(undefined)}
          >
            Skip passphrase
          </button>
        </div>
      </form>
    </AuthCard>
  );
}
