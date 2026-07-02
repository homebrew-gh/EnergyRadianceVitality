import { SectionHeader } from "./FieldLabel";
import type { DetectedRelayOption } from "../lib/relayUrl";
import { relayConnectUrl } from "../lib/relayUrl";

type LocalRelayPickerProps = {
  relays: DetectedRelayOption[];
  value: string;
  onChange: (url: string) => void;
};

export function LocalRelayPicker({ relays, value, onChange }: LocalRelayPickerProps) {
  if (relays.length < 2) return null;

  return (
    <div className="space-y-3">
      <SectionHeader>Local Relay</SectionHeader>
      <p className="text-sm text-muted">
        Multiple relays are installed on this StartOS server. Choose which one ERV should use.
        Haven includes Blossom media storage; Nostr RS Relay is the marketplace default.
      </p>
      <div className="space-y-2" role="radiogroup" aria-label="Local relay">
        {relays.map((relay) => {
          const connectUrl = relayConnectUrl(relay);
          const selected = value.trim() === connectUrl.trim();
          return (
            <label
              key={relay.internal}
              className={`flex cursor-pointer gap-3 rounded-card border p-3 transition-colors ${
                selected
                  ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)]/40"
                  : "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]"
              }`}
            >
              <input
                type="radio"
                name="local-relay"
                className="mt-1"
                checked={selected}
                onChange={() => onChange(connectUrl)}
              />
              <span className="min-w-0 space-y-1">
                <span className="block font-medium text-[var(--erv-on-surface)]">
                  {relay.label}
                </span>
                <span className="block font-mono text-xs break-all text-muted">
                  {connectUrl}
                </span>
              </span>
            </label>
          );
        })}
      </div>
      <p className="text-xs text-muted">
        Advanced: enter <span className="font-mono">ws://&lt;package&gt;.startos:&lt;port&gt;</span>{" "}
        directly below to force a specific internal relay.
      </p>
    </div>
  );
}
