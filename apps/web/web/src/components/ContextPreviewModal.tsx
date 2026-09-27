import { useEffect, useId } from "react";
import { FieldLabel, SectionHeader } from "./FieldLabel";

type ContextPreviewModalProps = {
  json: string;
  byteLength: number;
  modelId: string;
  providerLabel: string;
  onConfirm: () => void;
  onClose: () => void;
};

export function ContextPreviewModal({
  json,
  byteLength,
  modelId,
  providerLabel,
  onConfirm,
  onClose,
}: ContextPreviewModalProps) {
  const titleId = useId();
  const tokens = Math.max(1, Math.ceil(byteLength / 4));

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="card max-h-[90vh] w-full max-w-3xl space-y-4 overflow-y-auto p-4">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h3 id={titleId} className="text-lg font-semibold text-heading">
              Review what leaves this server
            </h3>
            <p className="mt-1 text-sm text-muted">
              Prompts are processed off this server inside Maple&apos;s encrypted enclave and billed
              to your Maple account. ERV sends the aggregates below, not raw day logs.
            </p>
          </div>
          <button type="button" className="btn-ghost px-2 py-1 text-sm" onClick={onClose}>
            Close
          </button>
        </div>
        <dl className="grid gap-3 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-muted">
              <FieldLabel>Provider</FieldLabel>
            </dt>
            <dd className="mt-1 text-heading">{providerLabel}</dd>
          </div>
          <div>
            <dt className="text-muted">
              <FieldLabel>Analysis model</FieldLabel>
            </dt>
            <dd className="mt-1 break-all font-mono text-xs text-heading">{modelId || "Not chosen"}</dd>
          </div>
          <div>
            <dt className="text-muted">
              <FieldLabel>Context size</FieldLabel>
            </dt>
            <dd className="mt-1 text-heading">
              {byteLength.toLocaleString()} bytes · about {tokens.toLocaleString()} tokens
            </dd>
          </div>
        </dl>
        <div className="space-y-1">
          <SectionHeader>Context JSON</SectionHeader>
          <pre className="max-h-80 overflow-auto rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3 font-mono text-xs text-heading whitespace-pre-wrap">
            {json}
          </pre>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className="btn-primary" onClick={onConfirm}>
            Send to coach
          </button>
          <button type="button" className="btn-ghost" onClick={onClose}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
